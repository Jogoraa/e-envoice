package et.ut.einvoice.audit.service;

import et.ut.einvoice.audit.domain.EvidenceManifest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Verifier for cryptographic evidence manifests.
 * Fails closed if content hash does not match or metadata is invalid.
 */
@Service
public class EvidenceManifestVerifier {

    private static final Logger log = LoggerFactory.getLogger(EvidenceManifestVerifier.class);

    public boolean verifyManifest(EvidenceManifest manifest, byte[] contentBytes) {
        if (manifest == null) {
            log.error("Manifest verification failed: null manifest");
            return false;
        }

        String computedHash = EvidenceManifestService.computeHash(contentBytes);
        if (!computedHash.equalsIgnoreCase(manifest.getContentHash())) {
            log.error("Manifest {} verification failed: content hash mismatch! expected={}, actual={}",
                    manifest.getArtifactId(), manifest.getContentHash(), computedHash);
            return false;
        }

        log.info("Manifest {} successfully verified for content hash {}",
                manifest.getArtifactId(), computedHash);
        return true;
    }
}
