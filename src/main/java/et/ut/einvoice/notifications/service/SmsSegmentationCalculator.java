package et.ut.einvoice.notifications.service;

/**
 * Calculates message encoding, technical telecom SMS segmentation (3GPP TS 23.040 / SMPP standard UDH),
 * and decoupled provider commercial billing units.
 */
public class SmsSegmentationCalculator {

    public enum EncodingType {
        GSM_7,
        UCS_2
    }

    /**
     * Configurable commercial billing rules allowing custom operator or gateway billing increments.
     */
    public record CommercialBillingRules(
            int latinCharsPerUnit,
            int unicodeCharsPerUnit
    ) {
        /**
         * Common Ethiopian SMS aggregator commercial model (e.g. 159 Latin / 69 Ethiopic characters).
         */
        public static final CommercialBillingRules AGGREGATOR_DEFAULT = new CommercialBillingRules(159, 69);

        /**
         * Standard carrier single-PDU baseline (160 GSM / 70 UCS-2).
         */
        public static final CommercialBillingRules STANDARD_PDU = new CommercialBillingRules(160, 70);
    }

    public record SegmentationResult(
            int characterCount,
            EncodingType encoding,
            int technicalSegments,
            int commercialBillingUnits
    ) {}

    public static SegmentationResult calculate(String text) {
        return calculate(text, CommercialBillingRules.AGGREGATOR_DEFAULT);
    }

    public static SegmentationResult calculate(String text, CommercialBillingRules billingRules) {
        if (text == null || text.isEmpty()) {
            return new SegmentationResult(0, EncodingType.GSM_7, 0, 0);
        }

        CommercialBillingRules rules = billingRules != null ? billingRules : CommercialBillingRules.AGGREGATOR_DEFAULT;
        boolean isUnicode = containsUnicodeCharacters(text);
        int length = text.length();

        if (isUnicode) {
            // Technical UCS-2 standard (3GPP TS 23.040):
            // Single PDU: up to 70 characters (140 octets)
            // Concatenated multipart PDU: 67 characters per segment (due to 6-byte User Data Header)
            int technicalSegments = length <= 70 ? 1 : (int) Math.ceil((double) length / 67.0);

            // Provider Commercial Billing Units:
            int commercialUnits = (int) Math.ceil((double) length / (double) rules.unicodeCharsPerUnit());
            return new SegmentationResult(length, EncodingType.UCS_2, technicalSegments, commercialUnits);
        } else {
            // Technical GSM-7 standard:
            // Single PDU: up to 160 characters (140 octets with 7-bit packing)
            // Concatenated multipart PDU: 153 characters per segment (due to 6-byte User Data Header)
            int technicalSegments = length <= 160 ? 1 : (int) Math.ceil((double) length / 153.0);

            // Provider Commercial Billing Units:
            int commercialUnits = (int) Math.ceil((double) length / (double) rules.latinCharsPerUnit());
            return new SegmentationResult(length, EncodingType.GSM_7, technicalSegments, commercialUnits);
        }
    }

    public static int calculateTechnicalSegments(String text) {
        return calculate(text).technicalSegments();
    }

    public static int calculateCommercialUnits(String text, CommercialBillingRules rules) {
        return calculate(text, rules).commercialBillingUnits();
    }

    public static boolean containsUnicodeCharacters(String text) {
        if (text == null) return false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            // If character is outside standard 7-bit ASCII/GSM range (such as Ethiopic script U+1200 to U+137F)
            if (c > 127) {
                return true;
            }
        }
        return false;
    }
}
