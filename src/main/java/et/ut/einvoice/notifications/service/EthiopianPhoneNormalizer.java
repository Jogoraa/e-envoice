package et.ut.einvoice.notifications.service;

import et.ut.einvoice.platform.exception.BusinessException;
import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.Set;

/**
 * Normalizes Ethiopian mobile phone numbers to the canonical E.164-compatible format (2519XXXXXXXX or 2517XXXXXXXX).
 * Establishes valid Ethiopian national mobile numbering plan compliance without asserting telecom carrier ownership.
 * Strictly rejects landlines (e.g. 011...), malformed numbers, and non-mobile prefixes (Fail-Closed).
 */
public class EthiopianPhoneNormalizer {

    /**
     * Default designated Ethiopian mobile subscriber leading digits (09... and 07...).
     */
    public static final Set<Character> DEFAULT_ALLOWED_MOBILE_PREFIXES = Set.of('9', '7');

    private EthiopianPhoneNormalizer() {}

    /**
     * Normalizes a phone number to canonical 12-digit E.164 representation using default mobile prefixes.
     *
     * @param rawPhone Raw input phone number
     * @return 12-digit canonical phone number (e.g. 251911234567, 251712345678)
     * @throws BusinessException if the number is invalid, a fixed landline, or non-mobile
     */
    public static String normalize(String rawPhone) {
        return normalize(rawPhone, DEFAULT_ALLOWED_MOBILE_PREFIXES);
    }

    /**
     * Normalizes a phone number against a configurable set of allowed mobile leading digits.
     *
     * @param rawPhone Raw input phone number
     * @param allowedMobilePrefixes Set of valid mobile subscriber prefix characters (e.g. '9', '7')
     * @return 12-digit canonical E.164 phone number
     */
    public static String normalize(String rawPhone, Set<Character> allowedMobilePrefixes) {
        if (rawPhone == null || rawPhone.isBlank()) {
            throw new BusinessException("INVALID_PHONE_NUMBER", "Phone number cannot be null or blank", HttpStatus.BAD_REQUEST);
        }

        Set<Character> activePrefixes = (allowedMobilePrefixes != null && !allowedMobilePrefixes.isEmpty())
                ? allowedMobilePrefixes
                : DEFAULT_ALLOWED_MOBILE_PREFIXES;

        // Strip whitespaces, dashes, dots, parentheses
        String cleaned = rawPhone.trim().replaceAll("[\\s\\-\\.\\(\\)]", "");

        // Remove leading '+' if present
        if (cleaned.startsWith("+")) {
            cleaned = cleaned.substring(1);
        }

        // Reject any non-digit characters
        if (!cleaned.matches("\\d+")) {
            throw new BusinessException("INVALID_PHONE_NUMBER", "Phone number contains non-digit characters: " + rawPhone, HttpStatus.BAD_REQUEST);
        }

        // Handle variations
        String canonical;
        if (cleaned.startsWith("251")) {
            canonical = cleaned;
        } else if (cleaned.startsWith("0")) {
            canonical = "251" + cleaned.substring(1);
        } else if (cleaned.length() == 9 && activePrefixes.contains(cleaned.charAt(0))) {
            canonical = "251" + cleaned;
        } else {
            throw new BusinessException("INVALID_PHONE_NUMBER", "Unsupported country or national prefix for phone number: " + rawPhone, HttpStatus.BAD_REQUEST);
        }

        // Must be exactly 12 digits: 251 + 9 national digits
        if (canonical.length() != 12) {
            throw new BusinessException("INVALID_PHONE_NUMBER", "Invalid Ethiopian phone number length (expected 12 digits): " + canonical, HttpStatus.BAD_REQUEST);
        }

        // Validate mobile prefix: 4th character must match configured mobile subscriber numbering blocks
        char prefixChar = canonical.charAt(3);
        if (!activePrefixes.contains(prefixChar)) {
            throw new BusinessException("INVALID_PHONE_NUMBER",
                    "Invalid mobile subscriber prefix '0" + prefixChar + "'. Fixed landlines and unallocated ranges cannot receive SMS: " + rawPhone,
                    HttpStatus.BAD_REQUEST);
        }

        return canonical;
    }

    /**
     * Checks if a phone number is valid without throwing an exception.
     */
    public static boolean isValid(String rawPhone) {
        return isValid(rawPhone, DEFAULT_ALLOWED_MOBILE_PREFIXES);
    }

    public static boolean isValid(String rawPhone, Set<Character> allowedMobilePrefixes) {
        try {
            normalize(rawPhone, allowedMobilePrefixes);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
