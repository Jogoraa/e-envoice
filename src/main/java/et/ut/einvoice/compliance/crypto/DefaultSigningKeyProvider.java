package et.ut.einvoice.compliance.crypto;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DefaultSigningKeyProvider implements SigningKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(DefaultSigningKeyProvider.class);
    private final Map<UUID, KeyPair> keyStore = new ConcurrentHashMap<>();

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Override
    public PrivateKey getTenantPrivateKey(UUID tenantId) {
        return getOrGenerateKeyPair(tenantId).getPrivate();
    }

    @Override
    public PublicKey getTenantPublicKey(UUID tenantId) {
        return getOrGenerateKeyPair(tenantId).getPublic();
    }

    @Override
    public String getCertificateSerialNumber(UUID tenantId) {
        return "INSA-CERT-" + Math.abs(tenantId.hashCode());
    }

    private KeyPair getOrGenerateKeyPair(UUID tenantId) {
        return keyStore.computeIfAbsent(tenantId, id -> {
            try {
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", BouncyCastleProvider.PROVIDER_NAME);
                generator.initialize(2048);
                KeyPair kp = generator.generateKeyPair();
                log.info("Initialized secure in-memory INSA-compliant RSA-2048 keypair for tenant {}", id);
                return kp;
            } catch (Exception ex) {
                log.error("Failed to generate RSA keypair for tenant {}", id, ex);
                throw new RuntimeException("Key generation failed: " + ex.getMessage(), ex);
            }
        });
    }
}
