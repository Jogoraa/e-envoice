package et.ut.einvoice.notifications.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class InvoiceNotificationTemplateServiceTest {

    private final InvoiceNotificationTemplateService templateService =
            new InvoiceNotificationTemplateService("https://invoice.utsolutionsplc.com");

    @Test
    @DisplayName("Generates 256-bit cryptographically secure Base62 verification token with maximum length 43")
    void testVerificationTokenGeneration() {
        Set<String> generatedTokens = new HashSet<>();
        for (int i = 0; i < 5000; i++) {
            String token = templateService.generateVerificationToken();
            assertNotNull(token);
            assertFalse(token.isBlank());
            assertTrue(token.length() <= 43, "Token length exceeds 43 characters: " + token.length());
            assertTrue(token.matches("^[0-9A-Za-z]+$"), "Token contains non-Base62 characters: " + token);
            assertTrue(generatedTokens.add(token), "Duplicate token generated: " + token);
        }
    }

    @Test
    @DisplayName("Renders English template with unmasked authoritative IRN and zero PII")
    void testEnglishTemplateRendering() {
        String token = templateService.generateVerificationToken();
        var rendered = templateService.renderRegistrationNotification(
                "INV-2026-00123",
                "UT Solutions PLC",
                new BigDecimal("12500.00"),
                "IRN-EIRS-2026-09BC6B7E-BB30-4C47",
                token,
                "en"
        );

        assertEquals("INVOICE_REGISTRATION_EN", rendered.templateId());
        assertEquals("v1.0-en", rendered.templateVersion());
        assertTrue(rendered.messageText().contains("Tax Invoice INV-2026-00123 from UT Solutions PLC. Total: ETB 12500.00."));
        assertTrue(rendered.messageText().contains("IRN: IRN-EIRS-2026-09BC6B7E-BB30-4C47"));
        assertTrue(rendered.messageText().contains("https://invoice.utsolutionsplc.com/v/" + token));
        assertNotNull(rendered.messageHash());
        assertEquals(64, rendered.messageHash().length());
    }

    @Test
    @DisplayName("Renders Amharic template with unmasked authoritative IRN and Ethiopic script")
    void testAmharicTemplateRendering() {
        String token = templateService.generateVerificationToken();
        var rendered = templateService.renderRegistrationNotification(
                "INV-2026-00123",
                "ዩቲ ሶሉሽንስ ኃ.የተ.የግ.ማ",
                new BigDecimal("12500.00"),
                "IRN-EIRS-2026-09BC6B7E-BB30-4C47",
                token,
                "am"
        );

        assertEquals("INVOICE_REGISTRATION_AM", rendered.templateId());
        assertEquals("v1.0-am", rendered.templateVersion());
        assertTrue(rendered.messageText().contains("የሽያጭ ደረሰኝ INV-2026-00123 ከዩቲ ሶሉሽንስ ኃ.የተ.የግ.ማ። ጠቅላላ፡ 12500.00 ብር።"));
        assertTrue(rendered.messageText().contains("IRN: IRN-EIRS-2026-09BC6B7E-BB30-4C47"));
        assertTrue(rendered.messageText().contains("https://invoice.utsolutionsplc.com/v/" + token));
        assertNotNull(rendered.messageHash());
    }
}
