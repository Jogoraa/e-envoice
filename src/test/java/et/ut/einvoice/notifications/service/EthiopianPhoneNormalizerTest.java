package et.ut.einvoice.notifications.service;

import et.ut.einvoice.platform.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class EthiopianPhoneNormalizerTest {

    @Test
    @DisplayName("Normalizes valid Ethio Telecom mobile numbers across all format permutations")
    void normalizeEthioTelecomNumbers() {
        assertEquals("251911234567", EthiopianPhoneNormalizer.normalize("+251911234567"));
        assertEquals("251911234567", EthiopianPhoneNormalizer.normalize("251911234567"));
        assertEquals("251911234567", EthiopianPhoneNormalizer.normalize("0911234567"));
        assertEquals("251911234567", EthiopianPhoneNormalizer.normalize("911234567"));
        assertEquals("251911234567", EthiopianPhoneNormalizer.normalize("+251 911 23 45 67"));
        assertEquals("251911234567", EthiopianPhoneNormalizer.normalize("0911-23-45-67"));
        assertEquals("251911234567", EthiopianPhoneNormalizer.normalize("(0911) 234567"));
    }

    @Test
    @DisplayName("Normalizes valid Safaricom Ethiopia mobile numbers across all format permutations")
    void normalizeSafaricomNumbers() {
        assertEquals("251712345678", EthiopianPhoneNormalizer.normalize("+251712345678"));
        assertEquals("251712345678", EthiopianPhoneNormalizer.normalize("251712345678"));
        assertEquals("251712345678", EthiopianPhoneNormalizer.normalize("0712345678"));
        assertEquals("251712345678", EthiopianPhoneNormalizer.normalize("712345678"));
        assertEquals("251712345678", EthiopianPhoneNormalizer.normalize("+251 712 34 56 78"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0115512345",     // Addis Ababa fixed landline
            "+251115512345",  // E.164 Addis landline
            "0221112345",     // Adama fixed landline
            "0331112345",     // Dessie landline
            "0462201234"      // Hawassa landline
    })
    @DisplayName("Rejects Ethiopian landlines fail-closed because fixed lines cannot receive SMS")
    void rejectLandlines(String landline) {
        BusinessException ex = assertThrows(BusinessException.class, () -> EthiopianPhoneNormalizer.normalize(landline));
        assertEquals("INVALID_PHONE_NUMBER", ex.getCode());
        assertFalse(EthiopianPhoneNormalizer.isValid(landline));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "123",
            "911",
            "09112345",       // Too short (8 digits)
            "0911234567890",  // Too long
            "0911abc567",     // Non-digits
            "+12025550123",   // US number
            "+447911123456"   // UK number
    })
    @DisplayName("Rejects malformed, incomplete, or international phone numbers fail-closed")
    void rejectInvalidFormats(String invalidNumber) {
        assertThrows(BusinessException.class, () -> EthiopianPhoneNormalizer.normalize(invalidNumber));
        assertFalse(EthiopianPhoneNormalizer.isValid(invalidNumber));
    }
}
