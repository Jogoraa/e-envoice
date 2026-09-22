package et.ut.einvoice.platform.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/**
 * Hardened JWT Token Engine.
 * Enforces algorithm allowlisting (strictly HS256), cryptographic signature verification,
 * expiration with clock-skew policy, issuer and audience verification, and tenant binding.
 * Supports three security contexts: Normal Tenant, SaaS Master, and Delegated Tenant Support.
 * Never logs raw JWT tokens.
 */
@Service
public class JwtTokenService {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenService.class);
    private static final String ALLOWED_ALGORITHM = "HS256";
    private static final long CLOCK_SKEW_SECONDS = 60;

    public static final String AUDIENCE_TENANT = "ut-invoice-tenant";
    public static final String AUDIENCE_MASTER = "ut-invoice-master";
    public static final String AUDIENCE_LEGACY = "ut-einvoice-api";

    private final byte[] secretKeyBytes;
    private final String expectedIssuer;
    private final String defaultTenantAudience;
    private final ObjectMapper objectMapper;

    public JwtTokenService(
            @Value("${platform.security.jwt.secret:default-dev-ut-einvoice-platform-jwt-secret-key-at-least-256-bits-long}") String secret,
            @Value("${platform.security.jwt.issuer:ut-einvoice-platform}") String expectedIssuer,
            @Value("${platform.security.jwt.audience:ut-invoice-tenant}") String defaultTenantAudience,
            ObjectMapper objectMapper
    ) {
        this.secretKeyBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.expectedIssuer = expectedIssuer;
        this.defaultTenantAudience = (defaultTenantAudience != null && !defaultTenantAudience.isBlank())
                ? defaultTenantAudience
                : AUDIENCE_TENANT;
        this.objectMapper = objectMapper;
    }

    public record ValidatedJwtClaims(
            UUID tenantId,
            String subject,
            String audience,
            Set<String> roles,
            Set<String> scopes,
            Instant expiresAt,
            boolean isDelegated,
            String delegatedBy,
            String accessType,
            String sessionId,
            String reason
    ) {
        // Backwards-compatible convenience constructor
        public ValidatedJwtClaims(UUID tenantId, String subject, Set<String> roles, Set<String> scopes, Instant expiresAt) {
            this(tenantId, subject, AUDIENCE_TENANT, roles, scopes, expiresAt, false, null, null, null, null);
        }

        public boolean isMasterToken() {
            return AUDIENCE_MASTER.equalsIgnoreCase(audience);
        }

        public boolean isTenantToken() {
            return AUDIENCE_TENANT.equalsIgnoreCase(audience) || AUDIENCE_LEGACY.equalsIgnoreCase(audience);
        }
    }

    public String generateToken(UUID tenantId, String subject, Set<String> roles, Set<String> scopes, long ttlSeconds) {
        return generateToken(tenantId, subject, roles, scopes, ttlSeconds, this.defaultTenantAudience);
    }

    public String generateToken(UUID tenantId, String subject, Set<String> roles, Set<String> scopes, long ttlSeconds, String audience) {
        return createJwt(tenantId, subject, roles, scopes, ttlSeconds, audience, Collections.emptyMap());
    }

    public String generateMasterToken(String masterUserId, Set<String> roles, Set<String> scopes, long ttlSeconds) {
        return createJwt(null, masterUserId, roles, scopes, ttlSeconds, AUDIENCE_MASTER, Collections.emptyMap());
    }

    public String generateDelegatedTenantToken(
            String masterUserId,
            UUID targetTenantId,
            String accessType,
            String sessionId,
            String reason,
            Set<String> roles,
            Set<String> scopes,
            long ttlSeconds
    ) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("is_delegated", true);
        extraClaims.put("delegated_by", masterUserId);
        extraClaims.put("access_type", accessType != null ? accessType : "TESTING");
        if (sessionId != null) {
            extraClaims.put("session_id", sessionId);
        }
        if (reason != null) {
            extraClaims.put("reason", reason);
        }
        return createJwt(targetTenantId, masterUserId, roles, scopes, ttlSeconds, AUDIENCE_TENANT, extraClaims);
    }

    private String createJwt(
            UUID tenantId,
            String subject,
            Set<String> roles,
            Set<String> scopes,
            long ttlSeconds,
            String audience,
            Map<String, Object> extraClaims
    ) {
        try {
            Instant now = Instant.now();
            Instant exp = now.plusSeconds(ttlSeconds);

            Map<String, Object> header = Map.of(
                    "alg", ALLOWED_ALGORITHM,
                    "typ", "JWT"
            );
            String headerJson = objectMapper.writeValueAsString(header);
            String headerB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));

            Map<String, Object> claims = new HashMap<>();
            claims.put("iss", expectedIssuer);
            if (audience != null && !audience.isBlank()) {
                claims.put("aud", audience);
            }
            claims.put("sub", subject);
            if (tenantId != null) {
                claims.put("tenant_id", tenantId.toString());
            }
            claims.put("roles", roles != null ? new ArrayList<>(roles) : List.of());
            claims.put("scopes", scopes != null ? new ArrayList<>(scopes) : List.of());
            claims.put("iat", now.getEpochSecond());
            claims.put("nbf", now.minusSeconds(10).getEpochSecond());
            claims.put("exp", exp.getEpochSecond());

            if (extraClaims != null) {
                claims.putAll(extraClaims);
            }

            String payloadJson = objectMapper.writeValueAsString(claims);
            String payloadB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));

            String dataToSign = headerB64 + "." + payloadB64;
            String signatureB64 = computeHmacSha256(dataToSign, secretKeyBytes);

            return dataToSign + "." + signatureB64;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate JWT token", e);
        }
    }

    public Optional<ValidatedJwtClaims> validateAndExtract(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        String[] parts = token.trim().split("\\.");
        if (parts.length != 3) {
            log.warn("Malformed JWT token rejected: invalid part count ({})", parts.length);
            return Optional.empty();
        }

        String headerB64 = parts[0];
        String payloadB64 = parts[1];
        String signatureB64 = parts[2];

        try {
            // 1. Header & Algorithm Allowlisting
            byte[] headerBytes = Base64.getUrlDecoder().decode(headerB64);
            JsonNode headerNode = objectMapper.readTree(headerBytes);
            String alg = headerNode.has("alg") ? headerNode.get("alg").asText() : null;
            if (!ALLOWED_ALGORITHM.equals(alg)) {
                log.warn("JWT algorithm rejected: '{}' (only '{}' permitted)", alg, ALLOWED_ALGORITHM);
                return Optional.empty();
            }

            // 2. Cryptographic Signature Verification
            String dataToSign = headerB64 + "." + payloadB64;
            String expectedSigB64 = computeHmacSha256(dataToSign, secretKeyBytes);
            byte[] expectedSig = expectedSigB64.getBytes(StandardCharsets.UTF_8);
            byte[] providedSig = signatureB64.getBytes(StandardCharsets.UTF_8);
            if (!MessageDigest.isEqual(expectedSig, providedSig)) {
                log.warn("JWT signature verification failed");
                return Optional.empty();
            }

            // 3. Claims Verification
            byte[] payloadBytes = Base64.getUrlDecoder().decode(payloadB64);
            JsonNode payloadNode = objectMapper.readTree(payloadBytes);

            // Issuer verification
            String issuer = payloadNode.has("iss") ? payloadNode.get("iss").asText() : null;
            if (!expectedIssuer.equals(issuer)) {
                log.warn("JWT issuer rejected: '{}' (expected '{}')", issuer, expectedIssuer);
                return Optional.empty();
            }

            // Audience extraction & verification
            String tokenAudience = null;
            if (payloadNode.has("aud")) {
                JsonNode audNode = payloadNode.get("aud");
                if (audNode.isArray() && audNode.size() > 0) {
                    tokenAudience = audNode.get(0).asText();
                } else {
                    tokenAudience = audNode.asText();
                }
            }

            if (tokenAudience != null && !tokenAudience.isBlank()) {
                boolean isAudienceValid = AUDIENCE_TENANT.equalsIgnoreCase(tokenAudience) ||
                        AUDIENCE_MASTER.equalsIgnoreCase(tokenAudience) ||
                        AUDIENCE_LEGACY.equalsIgnoreCase(tokenAudience) ||
                        defaultTenantAudience.equalsIgnoreCase(tokenAudience);
                if (!isAudienceValid) {
                    log.warn("JWT audience rejected: '{}'", tokenAudience);
                    return Optional.empty();
                }
            }

            Instant now = Instant.now();

            // Expiration verification (with clock skew)
            if (payloadNode.has("exp")) {
                long expEpoch = payloadNode.get("exp").asLong();
                Instant exp = Instant.ofEpochSecond(expEpoch);
                if (now.isAfter(exp.plusSeconds(CLOCK_SKEW_SECONDS))) {
                    log.warn("JWT expired at {}", exp);
                    return Optional.empty();
                }
            } else {
                log.warn("JWT rejected: missing 'exp' claim");
                return Optional.empty();
            }

            // Not Before verification (with clock skew)
            if (payloadNode.has("nbf")) {
                long nbfEpoch = payloadNode.get("nbf").asLong();
                Instant nbf = Instant.ofEpochSecond(nbfEpoch);
                if (now.isBefore(nbf.minusSeconds(CLOCK_SKEW_SECONDS))) {
                    log.warn("JWT not yet valid (nbf: {})", nbf);
                    return Optional.empty();
                }
            }

            // Tenant ID binding (Required for tenant/delegated tokens; optional for master tokens)
            String tenantStr = payloadNode.has("tenant_id") ? payloadNode.get("tenant_id").asText() :
                    (payloadNode.has("tid") ? payloadNode.get("tid").asText() : null);

            UUID tenantId = null;
            if (tenantStr != null && !tenantStr.isBlank()) {
                try {
                    tenantId = UUID.fromString(tenantStr);
                } catch (IllegalArgumentException ex) {
                    log.warn("JWT rejected: invalid tenant_id format");
                    return Optional.empty();
                }
            } else if (!AUDIENCE_MASTER.equalsIgnoreCase(tokenAudience)) {
                log.warn("JWT rejected: missing tenant binding on non-master token");
                return Optional.empty();
            }

            String subject = payloadNode.has("sub") ? payloadNode.get("sub").asText() : "USER";

            // Roles
            Set<String> roles = new HashSet<>();
            if (payloadNode.has("roles")) {
                JsonNode rolesNode = payloadNode.get("roles");
                if (rolesNode.isArray()) {
                    rolesNode.forEach(r -> roles.add(r.asText()));
                } else {
                    roles.addAll(Arrays.asList(rolesNode.asText().split("\\s+")));
                }
            }

            // Scopes
            Set<String> scopes = new HashSet<>();
            if (payloadNode.has("scopes")) {
                JsonNode scopesNode = payloadNode.get("scopes");
                if (scopesNode.isArray()) {
                    scopesNode.forEach(s -> scopes.add(s.asText()));
                } else {
                    scopes.addAll(Arrays.asList(scopesNode.asText().split("\\s+")));
                }
            }

            // Delegation Claims
            boolean isDelegated = payloadNode.has("is_delegated") && payloadNode.get("is_delegated").asBoolean();
            String delegatedBy = payloadNode.has("delegated_by") ? payloadNode.get("delegated_by").asText() : null;
            String accessType = payloadNode.has("access_type") ? payloadNode.get("access_type").asText() : null;
            String sessionId = payloadNode.has("session_id") ? payloadNode.get("session_id").asText() : null;
            String reason = payloadNode.has("reason") ? payloadNode.get("reason").asText() : null;

            long expEpoch = payloadNode.get("exp").asLong();
            return Optional.of(new ValidatedJwtClaims(
                    tenantId,
                    subject,
                    tokenAudience,
                    roles,
                    scopes,
                    Instant.ofEpochSecond(expEpoch),
                    isDelegated,
                    delegatedBy,
                    accessType,
                    sessionId,
                    reason
            ));

        } catch (Exception ex) {
            log.warn("JWT parsing/validation exception: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private String computeHmacSha256(String data, byte[] keyBytes) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(keyBytes, "HmacSHA256"));
        byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }
}
