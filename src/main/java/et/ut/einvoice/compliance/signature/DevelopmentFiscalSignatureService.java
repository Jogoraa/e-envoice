package et.ut.einvoice.compliance.signature;

import et.ut.einvoice.compliance.crypto.DigitalSignatureProvider;
import et.ut.einvoice.compliance.crypto.SigningIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Non-production development & test implementation of FiscalSignatureService.
 * Activated only when not running in production profile.
 */
@Service
@Profile("!prod & !production")
@ConditionalOnProperty(name = "mor.crypto.provider", havingValue = "software", matchIfMissing = true)
public class DevelopmentFiscalSignatureService implements FiscalSignatureService {

    private static final Logger log = LoggerFactory.getLogger(DevelopmentFiscalSignatureService.class);

    private final DigitalSignatureProvider signatureProvider;
    private final Map<UUID, KeyPair> tenantKeyCache = new ConcurrentHashMap<>();

    public DevelopmentFiscalSignatureService(DigitalSignatureProvider signatureProvider) {
        this.signatureProvider = signatureProvider;
        log.info("[DEV-CRYPTO] Initialized DevelopmentFiscalSignatureService (Non-production mode).");
    }

    @Override
    public FiscalSignatureResponse signFiscalDocument(UUID tenantId, String canonicalPayload, String certificateRef) {
        KeyPair kp = tenantKeyCache.computeIfAbsent(tenantId, this::generateTenantKeyPair);
        String digest = signatureProvider.computeSha256Hash(canonicalPayload);
        byte[] digestBytes = digest.getBytes(StandardCharsets.UTF_8);

        SigningIdentity identity = SigningIdentity.ofSoftwareKey(
                "DEV-TENANT-" + tenantId,
                kp.getPrivate(),
                kp.getPublic()
        );

        String signature = signatureProvider.sign(digestBytes, identity);
        String certRef = certificateRef != null ? certificateRef : "INSA-DEV-CERT-" + tenantId.toString().substring(0, 8).toUpperCase();

        CertificateMetadata metadata = new CertificateMetadata(
                "SERIAL-DEV-" + tenantId.hashCode(),
                "CN=INSA Development CA, O=Federal Democratic Republic of Ethiopia, C=ET",
                "CN=Tenant-" + tenantId + ", O=Taxpayer Business, C=ET",
                Instant.now().minusSeconds(86400 * 30),
                Instant.now().plusSeconds(86400 * 365)
        );

        return new FiscalSignatureResponse(
                "SHA256withRSA",
                certRef,
                signature,
                digest,
                metadata,
                Instant.now()
        );
    }

    @Override
    public boolean verifyFiscalSignature(UUID tenantId, String canonicalPayload, String base64Signature, String certificateRef) {
        KeyPair kp = tenantKeyCache.get(tenantId);
        if (kp == null) {
            log.warn("No public key cached for tenant {} during verification", tenantId);
            return false;
        }
        String digest = signatureProvider.computeSha256Hash(canonicalPayload);
        byte[] digestBytes = digest.getBytes(StandardCharsets.UTF_8);

        SigningIdentity identity = SigningIdentity.ofSoftwareKey(
                "DEV-TENANT-" + tenantId,
                null,
                kp.getPublic()
        );

        return signatureProvider.verify(digestBytes, base64Signature, identity);
    }

    @Override
    public boolean isHsmBacked() {
        return false;
    }

    @Override
    public String getProviderName() {
        return "Development Software Provider (INSA Mock / Non-Production)";
    }

    private KeyPair generateTenantKeyPair(UUID tenantId) {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048, new SecureRandom());
            return kpg.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("RSA KeyPair generation failed", e);
        }
    }
}
