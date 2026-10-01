package et.ut.einvoice.compliance.crypto;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;

/**
 * Immutable cryptographic signing identity abstraction.
 * Encapsulates key identifiers, hardware token aliases, or key pairs without
 * exposing infrastructure details to business services.
 */
public record SigningIdentity(
        String identityId,
        String keyAlias,
        String certificateSerialNumber,
        String issuerDn,
        PrivateKey privateKey,
        PublicKey publicKey,
        X509Certificate certificate
) {
    public static SigningIdentity ofHardwareAlias(String keyAlias) {
        return new SigningIdentity(keyAlias, keyAlias, null, null, null, null, null);
    }

    public static SigningIdentity ofSoftwareKey(String keyAlias, PrivateKey privateKey, PublicKey publicKey) {
        return new SigningIdentity(keyAlias, keyAlias, "SW-DEV-SERIAL", "CN=UT-DEV-CA", privateKey, publicKey, null);
    }
}
