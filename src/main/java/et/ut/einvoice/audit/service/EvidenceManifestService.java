package et.ut.einvoice.audit.service;

import et.ut.einvoice.audit.domain.EvidenceManifest;
import et.ut.einvoice.audit.repository.EvidenceManifestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Service for creating and persisting cryptographic evidence manifests
 * for exported evidence packages and fiscal audit archives.
 */
@Service
public class EvidenceManifestService {

    private static final Logger log = LoggerFactory.getLogger(EvidenceManifestService.class);
    public static final String GENESIS_MANIFEST_HASH = "0".repeat(64);

    private final EvidenceManifestRepository manifestRepository;

    public EvidenceManifestService(EvidenceManifestRepository manifestRepository) {
        this.manifestRepository = manifestRepository;
    }

    @Transactional
    public EvidenceManifest createManifest(UUID tenantId, String artifactType, byte[] contentBytes, String signatureMetadata) {
        String contentHash = computeHash(contentBytes);

        List<EvidenceManifest> existing = manifestRepository.findByTenantIdAndArtifactTypeOrderByCreatedAtDesc(tenantId, artifactType);
        String previousHash = existing.isEmpty() ? GENESIS_MANIFEST_HASH : existing.get(0).getContentHash();

        EvidenceManifest manifest = new EvidenceManifest(
                UUID.randomUUID(),
                tenantId,
                artifactType,
                contentHash,
                previousHash,
                "1.0.0-RELEASE",
                1,
                signatureMetadata,
                Instant.now()
        );

        EvidenceManifest saved = manifestRepository.save(manifest);
        log.info("Created evidence manifest {} for tenant {} (type: {}, hash: {})",
                saved.getArtifactId(), tenantId, artifactType, contentHash);
        return saved;
    }

    public static String computeHash(byte[] contentBytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(contentBytes != null ? contentBytes : new byte[0]);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
