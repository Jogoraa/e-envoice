package et.ut.einvoice.platform.identity.service;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * Standard RFC 6238 TOTP and RFC 4648 Base32 implementation.
 * Zero external library dependencies, natively utilizes Java javax.crypto.Mac.
 */
@Service
public class TotpService {

    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int TIME_STEP_SECONDS = 30;
    private static final int DIGITS = 6;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generates a 160-bit (20-byte) cryptographically secure Base32 secret.
     */
    public String generateSecret() {
        byte[] buffer = new byte[20];
        secureRandom.nextBytes(buffer);
        return encodeBase32(buffer);
    }

    /**
     * Generates standard otpauth URI for QR code integration.
     */
    public String getOtpAuthUri(String secret, String accountName, String issuer) {
        String cleanIssuer = issuer != null ? issuer.trim() : "UT-Invoice";
        return String.format(
                "otpauth://totp/%s:%s?secret=%s&issuer=%s&algorithm=SHA1&digits=6&period=30",
                cleanIssuer,
                accountName,
                secret,
                cleanIssuer
        );
    }

    /**
     * Validates a TOTP code within a +/- 1 step window (90s window) to tolerate clock drift.
     */
    public boolean verifyCode(String secret, String code) {
        if (secret == null || code == null || code.trim().length() != DIGITS) {
            return false;
        }
        try {
            int parsedCode = Integer.parseInt(code.trim());
            byte[] key = decodeBase32(secret);
            long currentWindow = System.currentTimeMillis() / 1000L / TIME_STEP_SECONDS;

            for (int i = -1; i <= 1; i++) {
                long window = currentWindow + i;
                int expectedCode = generateTotpForWindow(key, window);
                if (expectedCode == parsedCode) {
                    return true;
                }
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    private int generateTotpForWindow(byte[] key, long window) throws Exception {
        byte[] data = ByteBuffer.allocate(8).putLong(window).array();
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(key, "HmacSHA1"));
        byte[] hash = mac.doFinal(data);

        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);

        return binary % (int) Math.pow(10, DIGITS);
    }

    public static String encodeBase32(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int nextChar = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            nextChar = (nextChar << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                bitsLeft -= 5;
                sb.append(BASE32_ALPHABET.charAt((nextChar >> bitsLeft) & 0x1F));
            }
        }
        if (bitsLeft > 0) {
            sb.append(BASE32_ALPHABET.charAt((nextChar << (5 - bitsLeft)) & 0x1F));
        }
        return sb.toString();
    }

    public static byte[] decodeBase32(String base32) {
        String clean = base32.toUpperCase(Locale.ROOT).replaceAll("[^A-Z2-7]", "");
        byte[] result = new byte[clean.length() * 5 / 8];
        int buffer = 0;
        int bitsLeft = 0;
        int count = 0;
        for (char c : clean.toCharArray()) {
            int val = BASE32_ALPHABET.indexOf(c);
            if (val < 0) continue;
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                bitsLeft -= 8;
                result[count++] = (byte) ((buffer >> bitsLeft) & 0xFF);
            }
        }
        return result;
    }
}
