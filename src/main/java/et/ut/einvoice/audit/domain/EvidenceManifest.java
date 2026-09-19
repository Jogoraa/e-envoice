package et.ut.einvoice.audit.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Evidence Manifest entity for cryptographically chained archive and
 * evidence export artifact verification.
 */
@Entity
@Table(name = "evidence_manifests", indexes = {
        @Index(name = "idx_evidence_manifests_tenant_type", columnList = "tenant_id, artifact_type")
})
public class EvidenceManifest {

    @Id
    @Column(name = "artifact_id")
    private UUID artifactId;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "artifact_type", nullable = false, length = 64)
    private String artifactType;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "previous_artifact_hash", nullable = false, length = 64)
    private String previousArtifactHash;

    @Column(name = "producer_version", nullable = false, length = 32)
    private String producerVersion = "1.0.0-RELEASE";

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion = 1;

    @Column(name = "signature_metadata", length = 512)
    private String signatureMetadata;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public EvidenceManifest() {}

    public EvidenceManifest(UUID artifactId, UUID tenantId, String artifactType, String contentHash,
                            String previousArtifactHash, String producerVersion, int schemaVersion,
                            String signatureMetadata, Instant createdAt) {
        this.artifactId = artifactId != null ? artifactId : UUID.randomUUID();
        this.tenantId = tenantId;
        this.artifactType = artifactType;
        this.contentHash = contentHash;
        this.previousArtifactHash = previousArtifactHash;
        this.producerVersion = producerVersion != null ? producerVersion : "1.0.0-RELEASE";
        this.schemaVersion = schemaVersion > 0 ? schemaVersion : 1;
        this.signatureMetadata = signatureMetadata;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public UUID getArtifactId() { return artifactId; }
    public UUID getTenantId() { return tenantId; }
    public String getArtifactType() { return artifactType; }
    public String getContentHash() { return contentHash; }
    public String getPreviousArtifactHash() { return previousArtifactHash; }
    public String getProducerVersion() { return producerVersion; }
    public int getSchemaVersion() { return schemaVersion; }
    public String getSignatureMetadata() { return signatureMetadata; }
    public Instant getCreatedAt() { return createdAt; }
}
