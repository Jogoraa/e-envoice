package et.ut.einvoice.platform.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Canonical plain-text input sanitizer for API supplied strings.
 *
 * <p>API fields are not an HTML authoring surface. The OWASP policy therefore
 * permits no HTML elements or attributes. Opaque credentials and signed blobs
 * are excluded: modifying them would invalidate authentication or signatures,
 * and they are never rendered as HTML.</p>
 */
@Component
public class InputSanitizer {

    private static final PolicyFactory PLAIN_TEXT_POLICY = new HtmlPolicyBuilder().toFactory();
    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("[\\p{Cntrl}]");
    private static final Pattern BIDI_CONTROL_CHARACTERS = Pattern.compile("[\\u202A-\\u202E\\u2066-\\u2069]");
    private static final Pattern DANGEROUS_URI_SCHEMES = Pattern.compile("(?i)\\b(?:javascript|vbscript)\\s*:");
    private static final Pattern HTML_DATA_URI_SCHEME = Pattern.compile("(?i)\\bdata\\s*:\\s*text/html");

    private static final Set<String> OPAQUE_FIELD_TOKENS = Set.of(
            "password", "secret", "token", "signature", "hash", "payloadjson",
            "apikey", "keymaterial", "privatekey", "authorization", "otp", "code",
            "username", "email", "mail", "tin", "user"
    );

    /** Sanitizes a displayable/searchable string while preserving null values. */
    public String sanitize(String fieldName, String value) {
        if (value == null || isOpaqueField(fieldName)) {
            return value;
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFC);
        normalized = CONTROL_CHARACTERS.matcher(normalized).replaceAll("");
        normalized = BIDI_CONTROL_CHARACTERS.matcher(normalized).replaceAll("");

        // No request field is permitted to carry executable or renderable HTML.
        String clean = PLAIN_TEXT_POLICY.sanitize(normalized);
        clean = DANGEROUS_URI_SCHEMES.matcher(clean).replaceAll("");
        clean = HTML_DATA_URI_SCHEME.matcher(clean).replaceAll("");
        // Restore characters that the HTML policy entity-escapes in plain-text JSON APIs (e.g. '@' -> '&#64;')
        return org.springframework.web.util.HtmlUtils.htmlUnescape(clean);
    }

    /** Returns a deep-sanitized JSON tree without changing JSON field names or non-text values. */
    public JsonNode sanitizeJson(JsonNode node) {
        return sanitizeJson(node, null);
    }

    private JsonNode sanitizeJson(JsonNode node, String fieldName) {
        if (node == null || node.isNull()) {
            return node;
        }
        if (node.isTextual()) {
            return TextNode.valueOf(sanitize(fieldName, node.textValue()));
        }
        if (node.isArray()) {
            ArrayNode clean = ((ArrayNode) node).arrayNode();
            for (JsonNode item : node) {
                clean.add(sanitizeJson(item, fieldName));
            }
            return clean;
        }
        if (node.isObject()) {
            ObjectNode clean = ((ObjectNode) node).objectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                clean.set(field.getKey(), sanitizeJson(field.getValue(), field.getKey()));
            }
            return clean;
        }
        return node;
    }

    private boolean isOpaqueField(String fieldName) {
        if (fieldName == null || fieldName.isBlank()) {
            return false;
        }
        String normalized = fieldName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return OPAQUE_FIELD_TOKENS.stream().anyMatch(normalized::contains);
    }
}
