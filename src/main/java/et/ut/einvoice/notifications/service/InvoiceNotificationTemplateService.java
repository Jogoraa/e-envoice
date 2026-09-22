package et.ut.einvoice.notifications.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;

/**
 * Renders approved bilingual TRANSACTIONAL_NOTIFICATION_TEMPLATE messages,
 * computes cryptographic SHA-256 integrity hashes, and generates 256-bit Base62 verification tokens.
 */
@Service
public class InvoiceNotificationTemplateService {

    private static final String BASE62_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private final SecureRandom secureRandom = new SecureRandom();

    private final String verificationBaseUrl;

    public InvoiceNotificationTemplateService(
            @Value("${notifications.sms.verification-base-url:https://invoice.utsolutionsplc.com}") String verificationBaseUrl
    ) {
        this.verificationBaseUrl = verificationBaseUrl.replaceAll("/+$", "");
    }

    public record RenderedTemplate(
            String templateId,
            String templateVersion,
            String messageText,
            String messageHash,
            Map<String, Object> parameters
    ) {}

    /**
     * Generates a 256-bit (32 bytes) cryptographically random Base62-encoded verification token.
     * Maximum length is 43 characters.
     */
    public String generateVerificationToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return encodeBase62(randomBytes);
    }

    /**
     * Renders transactional notification message for invoice registration.
     */
    public RenderedTemplate renderRegistrationNotification(
            String documentNumber,
            String sellerName,
            BigDecimal grandTotal,
            String irn,
            String verificationToken,
            String preferredLanguage
    ) {
        String lang = (preferredLanguage != null && preferredLanguage.toLowerCase().startsWith("en")) ? "en" : "am";
        String templateId = "INVOICE_REGISTRATION_" + lang.toUpperCase();
        String templateVersion = "v1.0-" + lang;
        String verificationUrl = verificationBaseUrl + "/v/" + verificationToken;

        String messageText;
        if ("en".equals(lang)) {
            messageText = String.format(
                    "Tax Invoice %s from %s. Total: ETB %s. IRN: %s. Verify: %s",
                    documentNumber,
                    sellerName != null ? sellerName : "Seller",
                    grandTotal != null ? grandTotal.toPlainString() : "0.00",
                    irn,
                    verificationUrl
            );
        } else {
            messageText = String.format(
                    "የሽያጭ ደረሰኝ %s ከ%s። ጠቅላላ፡ %s ብር። IRN: %s። ማረጋገጫ፡ %s",
                    documentNumber,
                    sellerName != null ? sellerName : "ሻጭ",
                    grandTotal != null ? grandTotal.toPlainString() : "0.00",
                    irn,
                    verificationUrl
            );
        }

        String hash = sha256Hex(messageText);
        Map<String, Object> params = Map.of(
                "documentNumber", documentNumber,
                "sellerName", sellerName != null ? sellerName : "",
                "grandTotal", grandTotal != null ? grandTotal.toPlainString() : "0.00",
                "irn", irn,
                "verificationUrl", verificationUrl,
                "language", lang
        );

        return new RenderedTemplate(templateId, templateVersion, messageText, hash, params);
    }

    /**
     * Renders transactional notification message for invoice cancellation.
     */
    public RenderedTemplate renderCancellationNotification(
            String documentNumber,
            String irn,
            String cancellationRef,
            String verificationToken,
            String preferredLanguage
    ) {
        String lang = (preferredLanguage != null && preferredLanguage.toLowerCase().startsWith("en")) ? "en" : "am";
        String templateId = "INVOICE_CANCELLATION_" + lang.toUpperCase();
        String templateVersion = "v1.0-" + lang;
        String verificationUrl = verificationBaseUrl + "/v/" + verificationToken;

        String messageText;
        if ("en".equals(lang)) {
            messageText = String.format(
                    "Notice: Tax Invoice %s (IRN: %s) has been CANCELLED under Ref: %s. Verify: %s",
                    documentNumber, irn, cancellationRef, verificationUrl
            );
        } else {
            messageText = String.format(
                    "ማስታወቂያ፡ የደረሰኝ ቁጥር %s (IRN: %s) በማመሳከሪያ %s ተሰርዟል። ማረጋገጫ፡ %s",
                    documentNumber, irn, cancellationRef, verificationUrl
            );
        }

        String hash = sha256Hex(messageText);
        Map<String, Object> params = Map.of(
                "documentNumber", documentNumber,
                "irn", irn,
                "cancellationRef", cancellationRef,
                "verificationUrl", verificationUrl,
                "language", lang
        );

        return new RenderedTemplate(templateId, templateVersion, messageText, hash, params);
    }

    public static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    private static String encodeBase62(byte[] bytes) {
        java.math.BigInteger value = new java.math.BigInteger(1, bytes);
        java.math.BigInteger base = java.math.BigInteger.valueOf(62);
        StringBuilder sb = new StringBuilder();

        while (value.compareTo(java.math.BigInteger.ZERO) > 0) {
            java.math.BigInteger[] divRem = value.divideAndRemainder(base);
            sb.append(BASE62_ALPHABET.charAt(divRem[1].intValue()));
            value = divRem[0];
        }

        // Preserve leading zero bytes
        for (byte b : bytes) {
            if (b == 0) {
                sb.append(BASE62_ALPHABET.charAt(0));
            } else {
                break;
            }
        }

        return sb.reverse().toString();
    }
}
