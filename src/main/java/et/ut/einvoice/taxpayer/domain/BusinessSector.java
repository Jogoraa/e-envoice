package et.ut.einvoice.taxpayer.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "business_sectors")
public class BusinessSector {

    @Id
    @Column(name = "sector_code", length = 32)
    private String sectorCode;

    @Column(name = "name_en", nullable = false)
    private String nameEn;

    @Column(name = "name_am", nullable = false)
    private String nameAm;

    @Column(name = "is_mandatory_offline_continuity", nullable = false)
    private boolean mandatoryOfflineContinuity = false;

    @Column(name = "source_reference", nullable = false, length = 64)
    private String sourceReference = "DIRECTIVE_1142_ANNEX_2";

    @Column(name = "effective_version", nullable = false, length = 32)
    private String effectiveVersion = "2018_EC_2026_GC";

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public BusinessSector() {}

    public BusinessSector(String sectorCode, String nameEn, String nameAm, boolean mandatoryOfflineContinuity) {
        this.sectorCode = sectorCode;
        this.nameEn = nameEn;
        this.nameAm = nameAm;
        this.mandatoryOfflineContinuity = mandatoryOfflineContinuity;
        this.sourceReference = "DIRECTIVE_1142_ANNEX_2";
        this.effectiveVersion = "2018_EC_2026_GC";
        this.active = true;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public String getSectorCode() { return sectorCode; }
    public String getNameEn() { return nameEn; }
    public String getNameAm() { return nameAm; }
    public boolean isMandatoryOfflineContinuity() { return mandatoryOfflineContinuity; }
    public String getSourceReference() { return sourceReference; }
    public String getEffectiveVersion() { return effectiveVersion; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
