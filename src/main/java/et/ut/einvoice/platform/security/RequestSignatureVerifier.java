package et.ut.einvoice.platform.security;

import jakarta.servlet.http.HttpServletRequest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;
import java.util.Locale;

/**
 * Verifies HMAC-SHA-256 signatures for mutating HTTP requests.
 *
 * <p>The signed canonical request binds the raw body hash to the HTTP method, URI, query string,
 * and a short-lived Unix timestamp. This prevents an otherwise valid body signature from being
 * replayed against another route or with materially different query parameters.</p>
 */
public class RequestSignatureVerifier {

    public static final String SIGNATURE_HEADER = "X-Request-Signature";
    public static final String TIMESTAMP_HEADER = "X-Request-Timestamp";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int MINIMUM_SECRET_BYTES = 32;

    private final String sharedSecret;
    private final long maxClockSkewSeconds;
    private final Clock clock;

    public RequestSignatureVerifier(String sharedSecret, long maxClockSkewSeconds) {
        this(sharedSecret, maxClockSkewSeconds, Clock.systemUTC());
    }

    RequestSignatureVerifier(String sharedSecret, long maxClockSkewSeconds, Clock clock) {
        this.sharedSecret = sharedSecret;
        this.maxClockSkewSeconds = Math.max(1L, maxClockSkewSeconds);
        this.clock = clock;
    }

    public VerificationResult verify(HttpServletRequest request, byte[] rawBody) {
        if (!hasSecureSharedSecret()) {
            return VerificationResult.NOT_CONFIGURED;
        }

        String timestamp = request.getHeader(TIMESTAMP_HEADER);
        String signature = request.getHeader(SIGNATURE_HEADER);
        if (timestamp == null || timestamp.isBlank() || signature == null || signature.isBlank()) {
            return VerificationResult.MISSING_SIGNATURE;
        }

        String normalizedTimestamp = timestamp.trim();
        long epochSeconds;
        try {
            epochSeconds = Long.parseLong(normalizedTimestamp);
        } catch (NumberFormatException ex) {
            return VerificationResult.INVALID_TIMESTAMP;
        }

        long currentEpochSeconds = clock.instant().getEpochSecond();
        if (Math.abs(currentEpochSeconds - epochSeconds) > maxClockSkewSeconds) {
            return VerificationResult.EXPIRED;
        }

        final byte[] suppliedSignature;
        try {
            suppliedSignature = Base64.getUrlDecoder().decode(signature.trim());
        } catch (IllegalArgumentException ex) {
            return VerificationResult.INVALID_SIGNATURE;
        }

        byte[] expectedSignature = calculateSignature(
                request.getMethod(),
                request.getRequestURI(),
                request.getQueryString(),
                normalizedTimestamp,
                rawBody,
                sharedSecret
        );
        return MessageDigest.isEqual(expectedSignature, suppliedSignature)
                ? VerificationResult.VALID
                : VerificationResult.INVALID_SIGNATURE;
    }

    /**
     * Produces the URL-safe, unpadded header value for the request-signing protocol.
     * Clients must use the exact raw body bytes they transmit.
     */
    public static String sign(
            String method,
            String requestUri,
            String queryString,
            String timestamp,
            byte[] rawBody,
            String sharedSecret
    ) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(calculateSignature(
                method, requestUri, queryString, timestamp, rawBody, sharedSecret
        ));
    }

    private boolean hasSecureSharedSecret() {
        return sharedSecret != null && sharedSecret.getBytes(StandardCharsets.UTF_8).length >= MINIMUM_SECRET_BYTES;
    }

    private static byte[] calculateSignature(
            String method,
            String requestUri,
            String queryString,
            String timestamp,
            byte[] rawBody,
            String sharedSecret
    ) {
        try {
            byte[] body = rawBody == null ? new byte[0] : rawBody;
            String bodyHash = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(body)
            );
            String canonicalRequest = String.join("\n",
                    method.toUpperCase(Locale.ROOT),
                    requestUri,
                    queryString == null ? "" : queryString,
                    timestamp,
                    bodyHash
            );
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(sharedSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return mac.doFinal(canonicalRequest.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to calculate request signature", ex);
        }
    }

    public enum VerificationResult {
        VALID,
        NOT_CONFIGURED,
        MISSING_SIGNATURE,
        INVALID_TIMESTAMP,
        EXPIRED,
        INVALID_SIGNATURE
    }
}
