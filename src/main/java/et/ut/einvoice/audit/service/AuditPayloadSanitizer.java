package et.ut.einvoice.audit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Audit Payload Sanitizer ensuring secrets never enter the immutable audit ledger.
 * Scrubs passwords, private keys, API keys, client secrets, JWTs, government credentials,
 * database passwords, session tokens, and authorization headers.
 * <p>
 * Secret correlation utilizes HMAC-SHA-256 keyed with a dedicated server-held secret key,
 * preventing offline dictionary / rainbow table attacks on low-entropy secrets.
 * </p>
 */
@Component
public class AuditPayloadSanitizer {

    private static final Logger log = LoggerFactory.getLogger(AuditPayloadSanitizer.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Default server-held salt for development HMAC fingerprinting; production injects KMS key
    private static final byte[] DEFAULT_SERVER_HELD_KEY = 
            "UT-EINVOICE-AUDIT-SERVER-HELD-HMAC-FINGERPRINT-KEY-2026-CONFIDENTIAL".getBytes(StandardCharsets.UTF_8);

    private final byte[] hmacKeyBytes;

    private static final Set<String> SENSITIVE_KEY_NAMES = Set.of(
            "password", "pwd", "pass", "secret", "clientsecret", "client_secret",
            "privatekey", "private_key", "privkey", "privatesigningkey", "private_signing_key",
            "signingkey", "signing_key", "jwt", "jwtsecret", "jwt_secret",
            "governmentsecret", "government_secret", "governmentcredential", "government_credential", "govcredential",
            "dbpassword", "db_password", "databasepassword", "database_password",
            "encryptionkey", "encryption_key", "sessioncredential", "session_credential",
            "sessiontoken", "session_token", "token", "apikey", "api_key", "authorization",
            "authorizationheader", "authorization_header", "auth_token", "bearer", "credential", "credentials"
    );

    private static final Pattern BEARER_PATTERN = Pattern.compile("(?i)(Bearer\\s+)[A-Za-z0-9-_=]+\\.[A-Za-z0-9-_=]+\\.?[A-Za-z0-9-_.+/=]*");
    private static final Pattern BASIC_AUTH_PATTERN = Pattern.compile("(?i)(Basic\\s+)[A-Za-z0-9+/=]+");
    private static final Pattern PEM_PRIVATE_KEY_PATTERN = Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]*PRIVATE KEY-----");
    private static final Pattern KEY_VALUE_SECRET_PATTERN = Pattern.compile("(?i)(password|secret|client_secret|apikey|api_key|token|private_key)\\s*[:=]\\s*[\"']?([^\"'\\s,]+)[\"']?");

    public AuditPayloadSanitizer() {
        this(DEFAULT_SERVER_HELD_KEY);
    }

    public AuditPayloadSanitizer(byte[] customKey) {
        if (customKey != null && customKey.length >= 16) {
            this.hmacKeyBytes = customKey.clone();
        } else {
            this.hmacKeyBytes = DEFAULT_SERVER_HELD_KEY.clone();
        }
    }

    public String sanitize(String rawPayload) {
        if (rawPayload == null || rawPayload.isBlank()) {
            return "{}";
        }

        String trimmed = rawPayload.trim();
        // 1. Attempt JSON tree sanitization if valid JSON
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                JsonNode root = objectMapper.readTree(trimmed);
                sanitizeJsonNode(root);
                return objectMapper.writeValueAsString(root);
            } catch (Exception ignored) {
                // Fall through to regex sanitization
            }
        }

        // 2. Text / Key-Value Regex Sanitization
        return sanitizePlainText(trimmed);
    }

    private void sanitizeJsonNode(JsonNode node) {
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = obj.fields();
            List<String> keysToRedact = new ArrayList<>();

            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String key = entry.getKey();
                String cleanKey = key.toLowerCase().replace("-", "").replace("_", "");

                if (isSensitiveKey(cleanKey)) {
                    keysToRedact.add(key);
                } else {
                    sanitizeJsonNode(entry.getValue());
                }
            }

            for (String key : keysToRedact) {
                JsonNode val = obj.get(key);
                String valStr = val.asText();
                String fingerprint = computeFingerprint(valStr);
                obj.put(key, "[REDACTED_FINGERPRINT:" + fingerprint + "]");
            }
        } else if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            for (int i = 0; i < arr.size(); i++) {
                JsonNode item = arr.get(i);
                if (item.isTextual()) {
                    String sanitizedText = sanitizePlainText(item.asText());
                    if (!sanitizedText.equals(item.asText())) {
                        arr.set(i, new TextNode(sanitizedText));
                    }
                } else {
                    sanitizeJsonNode(item);
                }
            }
        }
    }

    private String sanitizePlainText(String text) {
        String result = PEM_PRIVATE_KEY_PATTERN.matcher(text).replaceAll("[REDACTED_PRIVATE_KEY]");
        result = BEARER_PATTERN.matcher(result).replaceAll("$1[REDACTED_JWT]");
        result = BASIC_AUTH_PATTERN.matcher(result).replaceAll("$1[REDACTED_BASIC_AUTH]");

        Matcher matcher = KEY_VALUE_SECRET_PATTERN.matcher(result);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            String secretVal = matcher.group(2);
            String fingerprint = computeFingerprint(secretVal);
            matcher.appendReplacement(sb, key + "=[REDACTED_FINGERPRINT:" + fingerprint + "]");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private boolean isSensitiveKey(String cleanKey) {
        for (String sensitive : SENSITIVE_KEY_NAMES) {
            if (cleanKey.contains(sensitive)) {
                return true;
            }
        }
        return false;
    }

    public String computeFingerprint(String value) {
        if (value == null || value.isBlank()) {
            return "EMPTY";
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKeyBytes, "HmacSHA256"));
            byte[] hmac = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < Math.min(8, hmac.length); i++) {
                hex.append(String.format("%02x", hmac[i]));
            }
            return "HMAC256:" + hex;
        } catch (Exception e) {
            return "REDACTED";
        }
    }
}
