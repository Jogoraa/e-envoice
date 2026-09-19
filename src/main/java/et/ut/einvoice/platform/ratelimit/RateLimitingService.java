package et.ut.einvoice.platform.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RateLimitingService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingService.class);

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    // In-memory fallback for local testing or when Redis is absent
    private final Map<String, TokenBucket> localBuckets = new ConcurrentHashMap<>();

    public boolean tryAcquire(UUID tenantId, String clientId, int limitPerMinute) {
        String key = "ratelimit:" + tenantId + ":" + (clientId != null ? clientId : "default");
        return tryAcquireInternal(key, limitPerMinute);
    }

    public boolean tryAcquireKey(String key, int limitPerMinute) {
        String rateKey = "ratelimit:" + key;
        return tryAcquireInternal(rateKey, limitPerMinute);
    }

    private boolean tryAcquireInternal(String key, int limitPerMinute) {
        if (redisTemplate != null) {
            try {
                Long current = redisTemplate.opsForValue().increment(key);
                if (current != null && current == 1) {
                    redisTemplate.expire(key, Duration.ofMinutes(1));
                }
                return current != null && current <= limitPerMinute;
            } catch (Exception ex) {
                log.warn("Redis unavailable for rate limiting, falling back to local bucket: {}", ex.getMessage());
            }
        }

        // Local token bucket fallback
        long now = System.currentTimeMillis();
        TokenBucket bucket = localBuckets.computeIfAbsent(key, k -> new TokenBucket(limitPerMinute, now));
        return bucket.tryConsume(limitPerMinute, now);
    }

    private static class TokenBucket {
        private final int capacity;
        private final AtomicInteger tokens;
        private volatile long lastRefillTime;

        TokenBucket(int capacity, long now) {
            this.capacity = capacity;
            this.tokens = new AtomicInteger(capacity);
            this.lastRefillTime = now;
        }

        synchronized boolean tryConsume(int limitPerMinute, long now) {
            if (now - lastRefillTime > 60_000) {
                tokens.set(capacity);
                lastRefillTime = now;
            }
            return tokens.decrementAndGet() >= 0;
        }
    }
}
