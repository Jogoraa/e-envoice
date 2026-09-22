package et.ut.einvoice.platform.config.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "configuration_revision_entries")
public class ConfigurationRevisionEntry {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revision_id", nullable = false)
    @JsonIgnore
    private ConfigurationRevision revision;

    @Column(name = "key_name", nullable = false, length = 128)
    private String keyName;

    @Column(name = "action", nullable = false, length = 32)
    private String action; // CREATED, UPDATED, ROTATED, ROLLED_BACK

    @Column(name = "old_value_classification", length = 32)
    private String oldValueClassification;

    @Column(name = "new_value_classification", length = 32)
    private String newValueClassification;

    @Column(name = "old_value_masked", columnDefinition = "TEXT")
    private String oldValueMasked;

    @Column(name = "new_value_masked", columnDefinition = "TEXT")
    private String newValueMasked;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public ConfigurationRevisionEntry() {}

    public ConfigurationRevisionEntry(
            UUID id,
            ConfigurationRevision revision,
            String keyName,
            String action,
            String oldValueClassification,
            String newValueClassification,
            String oldValueMasked,
            String newValueMasked
    ) {
        this.id = id != null ? id : UUID.randomUUID();
        this.revision = revision;
        this.keyName = keyName;
        this.action = action;
        this.oldValueClassification = oldValueClassification;
        this.newValueClassification = newValueClassification;
        this.oldValueMasked = oldValueMasked;
        this.newValueMasked = newValueMasked;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ConfigurationRevision getRevision() {
        return revision;
    }

    public void setRevision(ConfigurationRevision revision) {
        this.revision = revision;
    }

    public String getKeyName() {
        return keyName;
    }

    public void setKeyName(String keyName) {
        this.keyName = keyName;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getOldValueClassification() {
        return oldValueClassification;
    }

    public void setOldValueClassification(String oldValueClassification) {
        this.oldValueClassification = oldValueClassification;
    }

    public String getNewValueClassification() {
        return newValueClassification;
    }

    public void setNewValueClassification(String newValueClassification) {
        this.newValueClassification = newValueClassification;
    }

    public String getOldValueMasked() {
        return oldValueMasked;
    }

    public void setOldValueMasked(String oldValueMasked) {
        this.oldValueMasked = oldValueMasked;
    }

    public String getNewValueMasked() {
        return newValueMasked;
    }

    public void setNewValueMasked(String newValueMasked) {
        this.newValueMasked = newValueMasked;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
