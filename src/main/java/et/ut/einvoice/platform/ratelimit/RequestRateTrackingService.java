package et.ut.einvoice.platform.ratelimit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-principal, per-route request-rate tracker.
 *
 * <p>Redis is used when available so limits apply across application instances. The local,
 * bounded fixed-window fallback keeps the guard effective during Redis outages without retaining
 * raw user IDs or client addresses in process memory.</p>
 */
@Service
public class RequestRateTrackingService {

    private static final Logger log = LoggerFactory.getLogger(RequestRateTrackingService.class);
    private static final long WINDOW_MILLIS = 1_000L;
    private static final long REDIS_KEY_TTL_MILLIS = 2_000L;
    private static final long REDIS_RETRY_DELAY_MILLIS = 30_000L;
    private static final int MAX_LOCAL_BUCKETS = 50_000;

    private static final DefaultRedisScript<Long> INCREMENT_WITH_TTL = new DefaultRedisScript<>(
            "local count = redis.call('INCR', KEYS[1]); "
                    + "if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]); end; "
                    + "return count;",
            Long.class
    );

    private final Clock clock;
    private final Map<String, LocalWindowBucket> localBuckets = new ConcurrentHashMap<>();
    private final AtomicLong localOperations = new AtomicLong();

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    private volatile long redisUnavailableUntil;

    public RequestRateTrackingService() {
        this(Clock.systemUTC());
    }

    RequestRateTrackingService(Clock clock) {
        this.clock = clock;
    }

    /**
     * Atomically records one request and returns whether it is within the per-second limit.
     * The actor is hashed before being used in storage or logs. The endpoint is a normalized,
     * bounded route key suitable for aggregate metrics.
     */
    public RateLimitDecision tryAcquire(String actor, String endpoint, String actorType, int requestsPerSecond) {
        int limit = Math.max(1, requestsPerSecond);
        long now = clock.millis();
        long window = now / WINDOW_MILLIS;
        String actorHash = hash(actor);
        String endpointHash = hash(endpoint);
        String bucketKey = "request-rate:v1:" + window + ":" + actorHash + ":" + endpointHash;

        RateLimitDecision decision = tryAcquireRedis(bucketKey, now, limit);
        if (decision == null) {
            decision = tryAcquireLocal(actorHash + ":" + endpointHash, window, now, limit);
        }

        recordMetric(endpoint, actorType, decision.allowed());
        if (!decision.allowed()) {
            log.warn("Request-rate limit exceeded: actorHash={}, endpoint={}, limitPerSecond={}",
                    shortHash(actorHash), endpoint, limit);
        }
        return decision;
    }

    private RateLimitDecision tryAcquireRedis(String bucketKey, long now, int limit) {
        if (redisTemplate == null || now < redisUnavailableUntil) {
            return null;
        }

        try {
            Long count = redisTemplate.execute(INCREMENT_WITH_TTL, List.of(bucketKey), Long.toString(REDIS_KEY_TTL_MILLIS));
            if (count == null) {
                throw new IllegalStateException("Redis did not return a request count");
            }
            return decision(count, limit, now);
        } catch (Exception ex) {
            redisUnavailableUntil = now + REDIS_RETRY_DELAY_MILLIS;
            log.warn("Redis unavailable for request-rate tracking; using bounded local limiter for 30 seconds: {}",
                    ex.getMessage());
            return null;
        }
    }

    private RateLimitDecision tryAcquireLocal(String bucketKey, long window, long now, int limit) {
        LocalWindowBucket bucket = localBuckets.get(bucketKey);
        if (bucket == null && localBuckets.size() >= MAX_LOCAL_BUCKETS) {
            evictExpiredBuckets(window);
            if (localBuckets.size() >= MAX_LOCAL_BUCKETS) {
                // Do not let a key-cardinality attack exhaust heap while the distributed limiter is unavailable.
                return RateLimitDecision.blocked(1L);
            }
        }

        bucket = localBuckets.computeIfAbsent(bucketKey, ignored -> new LocalWindowBucket(window));
        RateLimitDecision decision = bucket.tryAcquire(window, now, limit);
        if (localOperations.incrementAndGet() % 1_024 == 0) {
            evictExpiredBuckets(window);
        }
        return decision;
    }

    private void evictExpiredBuckets(long currentWindow) {
        localBuckets.entrySet().removeIf(entry -> entry.getValue().lastSeenWindow() < currentWindow - 1);
    }

    private static RateLimitDecision decision(long count, int limit, long now) {
        long retryAfter = Math.max(1L, (WINDOW_MILLIS - (now % WINDOW_MILLIS) + 999L) / 1_000L);
        return count <= limit
                ? RateLimitDecision.allowed(Math.max(0, limit - count), retryAfter)
                : RateLimitDecision.blocked(retryAfter);
    }

    private void recordMetric(String endpoint, String actorType, boolean allowed) {
        if (meterRegistry == null) {
            return;
        }
        Counter.builder("platform.request.rate_limit.requests")
                .description("Requests observed by the per-principal endpoint rate limiter")
                .tag("endpoint", endpoint)
                .tag("actor_type", actorType)
                .tag("outcome", allowed ? "allowed" : "blocked")
                .register(meterRegistry)
                .increment();
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", ex);
        }
    }

    private static String shortHash(String hash) {
        return hash.substring(0, 12);
    }

    public record RateLimitDecision(boolean allowed, long remaining, long retryAfterSeconds) {
        static RateLimitDecision allowed(long remaining, long retryAfterSeconds) {
            return new RateLimitDecision(true, remaining, retryAfterSeconds);
        }

        static RateLimitDecision blocked(long retryAfterSeconds) {
            return new RateLimitDecision(false, 0, retryAfterSeconds);
        }
    }

    private static final class LocalWindowBucket {
        private long window;
        private long count;

        private LocalWindowBucket(long window) {
            this.window = window;
        }

        synchronized RateLimitDecision tryAcquire(long requestedWindow, long now, int limit) {
            if (window != requestedWindow) {
                window = requestedWindow;
                count = 0;
            }
            count++;
            return decision(count, limit, now);
        }

        synchronized long lastSeenWindow() {
            return window;
        }
    }
}
