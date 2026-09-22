package et.ut.einvoice.reports.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "report_definitions")
public class ReportDefinition {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "title", length = 255, nullable = false)
    private String title;

    @Column(name = "amharic_title", length = 255)
    private String amharicTitle;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "icon_name", length = 64, nullable = false)
    private String iconName = "assessment";

    @Column(name = "category", length = 64, nullable = false)
    private String category = "COMPLIANCE";

    @Column(name = "export_formats", length = 128, nullable = false)
    private String exportFormats = "PDF,EXCEL,CSV,JSON";

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public ReportDefinition() {}

    public ReportDefinition(String id, String title, String amharicTitle, String description,
                            String iconName, String category, String exportFormats, boolean isActive, int displayOrder) {
        this.id = id;
        this.title = title;
        this.amharicTitle = amharicTitle;
        this.description = description;
        this.iconName = iconName != null ? iconName : "assessment";
        this.category = category != null ? category : "COMPLIANCE";
        this.exportFormats = exportFormats != null ? exportFormats : "PDF,EXCEL,CSV,JSON";
        this.isActive = isActive;
        this.displayOrder = displayOrder;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getAmharicTitle() { return amharicTitle; }
    public void setAmharicTitle(String amharicTitle) { this.amharicTitle = amharicTitle; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getIconName() { return iconName; }
    public void setIconName(String iconName) { this.iconName = iconName; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getExportFormats() { return exportFormats; }
    public void setExportFormats(String exportFormats) { this.exportFormats = exportFormats; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
