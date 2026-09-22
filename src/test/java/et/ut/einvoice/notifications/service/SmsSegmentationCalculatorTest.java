package et.ut.einvoice.notifications.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SmsSegmentationCalculatorTest {

    @Test
    @DisplayName("Calculates Latin GSM-7 character count and GeezSMS commercial billing units (159 limit)")
    void testLatinSegmentation() {
        String shortLatin = "Tax Invoice INV-001 from Seller PLC. Total: ETB 1500.00. IRN: IRN-12345. Verify: https://invoice.utsolutionsplc.com/v/Abc123";
        var result = SmsSegmentationCalculator.calculate(shortLatin);

        assertEquals(SmsSegmentationCalculator.EncodingType.GSM_7, result.encoding());
        assertTrue(result.characterCount() <= 159);
        assertEquals(1, result.technicalSegments());
        assertEquals(1, result.commercialBillingUnits());

        // Test multi-part Latin
        String longLatin = "A".repeat(170);
        var multiResult = SmsSegmentationCalculator.calculate(longLatin);
        assertEquals(SmsSegmentationCalculator.EncodingType.GSM_7, multiResult.encoding());
        assertEquals(2, multiResult.technicalSegments());
        assertEquals(2, multiResult.commercialBillingUnits());
    }

    @Test
    @DisplayName("Calculates Amharic Ethiopic Unicode count and GeezSMS commercial billing units (69 limit)")
    void testAmharicSegmentation() {
        String shortAmharic = "የሽያጭ ደረሰኝ INV-001 ከሻጭ። ጠቅላላ፡ 1500.00 ብር። ማረጋገጫ፡ https://invoice.utsolutionsplc.com/v/Abc123";
        var result = SmsSegmentationCalculator.calculate(shortAmharic);

        assertEquals(SmsSegmentationCalculator.EncodingType.UCS_2, result.encoding());
        assertTrue(SmsSegmentationCalculator.containsUnicodeCharacters(shortAmharic));
        assertTrue(result.characterCount() <= 100);

        // Standard Amharic single segment test <= 69 chars
        String singleAmharic = "የሽያጭ ደረሰኝ ቁጥር 001። ጠቅላላ 500 ብር።";
        var singleResult = SmsSegmentationCalculator.calculate(singleAmharic);
        assertEquals(1, singleResult.technicalSegments());
        assertEquals(1, singleResult.commercialBillingUnits());
    }
}
