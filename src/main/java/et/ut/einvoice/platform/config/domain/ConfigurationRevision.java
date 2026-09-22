package et.ut.einvoice.platform.config.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "configuration_revisions")
public class ConfigurationRevision {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "revision_number", nullable = false, unique = true)
    private Long revisionNumber;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    @Column(name = "change_summary", nullable = false, columnDefinition = "TEXT")
    private String changeSummary;

    @Column(name = "rollback_from_revision")
    private Long rollbackFromRevision;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "revision", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ConfigurationRevisionEntry> entries = new ArrayList<>();

    public ConfigurationRevision() {}

    public ConfigurationRevision(
            UUID id,
            Long revisionNumber,
            String createdBy,
            String changeSummary,
            Long rollbackFromRevision,
            String status
    ) {
        this.id = id != null ? id : UUID.randomUUID();
        this.revisionNumber = revisionNumber;
        this.createdBy = createdBy;
        this.changeSummary = changeSummary;
        this.rollbackFromRevision = rollbackFromRevision;
        this.status = status != null ? status : "APPLIED";
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Long getRevisionNumber() {
        return revisionNumber;
    }

    public void setRevisionNumber(Long revisionNumber) {
        this.revisionNumber = revisionNumber;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getChangeSummary() {
        return changeSummary;
    }

    public void setChangeSummary(String changeSummary) {
        this.changeSummary = changeSummary;
    }

    public Long getRollbackFromRevision() {
        return rollbackFromRevision;
    }

    public void setRollbackFromRevision(Long rollbackFromRevision) {
        this.rollbackFromRevision = rollbackFromRevision;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public List<ConfigurationRevisionEntry> getEntries() {
        return entries;
    }

    public void setEntries(List<ConfigurationRevisionEntry> entries) {
        this.entries = entries;
    }

    public void addEntry(ConfigurationRevisionEntry entry) {
        entries.add(entry);
        entry.setRevision(this);
    }
}
