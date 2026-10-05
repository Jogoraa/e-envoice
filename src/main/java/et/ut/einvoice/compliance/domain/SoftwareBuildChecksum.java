package et.ut.einvoice.compliance.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Persisted Software Build Integrity & Release Checksum metadata.
 * Mandated by FDRE MoR Directive No. 1142/2026 Art. 10(9), 11(2), 12(3).
 */
@Entity
@Table(name = "software_build_checksums")
public class SoftwareBuildChecksum {

    @Id
    private UUID id;

    @Column(name = "version", nullable = false, unique = true, length = 32)
    private String version;

    @Column(name = "git_sha", nullable = false, length = 64)
    private String gitSha;

    @Column(name = "backend_sha256", nullable = false, length = 128)
    private String backendSha256;

    @Column(name = "frontend_build_sha256", length = 128)
    private String frontendBuildSha256;

    @Column(name = "container_digest", length = 128)
    private String containerDigest;

    @Column(name = "database_schema_version", nullable = false, length = 32)
    private String databaseSchemaVersion;

    @Column(name = "sbom_hash", length = 128)
    private String sbomHash;

    @Column(name = "registered_checksum", length = 128)
    private String registeredChecksum;

    @Column(name = "authority_signature", columnDefinition = "TEXT")
    private String authoritySignature;

    @Column(name = "build_timestamp", nullable = false)
    private Instant buildTimestamp;

    @Column(name = "is_active_release", nullable = false)
    private boolean activeRelease = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public SoftwareBuildChecksum() {}

    public SoftwareBuildChecksum(UUID id, String version, String gitSha, String backendSha256,
                                 String frontendBuildSha256, String containerDigest,
                                 String databaseSchemaVersion, String sbomHash,
                                 String registeredChecksum, String authoritySignature,
                                 Instant buildTimestamp, boolean activeRelease) {
        this.id = id;
        this.version = version;
        this.gitSha = gitSha;
        this.backendSha256 = backendSha256;
        this.frontendBuildSha256 = frontendBuildSha256;
        this.containerDigest = containerDigest;
        this.databaseSchemaVersion = databaseSchemaVersion;
        this.sbomHash = sbomHash;
        this.registeredChecksum = registeredChecksum;
        this.authoritySignature = authoritySignature;
        this.buildTimestamp = buildTimestamp;
        this.activeRelease = activeRelease;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getGitSha() {
        return gitSha;
    }

    public void setGitSha(String gitSha) {
        this.gitSha = gitSha;
    }

    public String getBackendSha256() {
        return backendSha256;
    }

    public void setBackendSha256(String backendSha256) {
        this.backendSha256 = backendSha256;
    }

    public String getFrontendBuildSha256() {
        return frontendBuildSha256;
    }

    public void setFrontendBuildSha256(String frontendBuildSha256) {
        this.frontendBuildSha256 = frontendBuildSha256;
    }

    public String getContainerDigest() {
        return containerDigest;
    }

    public void setContainerDigest(String containerDigest) {
        this.containerDigest = containerDigest;
    }

    public String getDatabaseSchemaVersion() {
        return databaseSchemaVersion;
    }

    public void setDatabaseSchemaVersion(String databaseSchemaVersion) {
        this.databaseSchemaVersion = databaseSchemaVersion;
    }

    public String getSbomHash() {
        return sbomHash;
    }

    public void setSbomHash(String sbomHash) {
        this.sbomHash = sbomHash;
    }

    public String getRegisteredChecksum() {
        return registeredChecksum;
    }

    public void setRegisteredChecksum(String registeredChecksum) {
        this.registeredChecksum = registeredChecksum;
    }

    public String getAuthoritySignature() {
        return authoritySignature;
    }

    public void setAuthoritySignature(String authoritySignature) {
        this.authoritySignature = authoritySignature;
    }

    public Instant getBuildTimestamp() {
        return buildTimestamp;
    }

    public void setBuildTimestamp(Instant buildTimestamp) {
        this.buildTimestamp = buildTimestamp;
    }

    public boolean isActiveRelease() {
        return activeRelease;
    }

    public void setActiveRelease(boolean activeRelease) {
        this.activeRelease = activeRelease;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
