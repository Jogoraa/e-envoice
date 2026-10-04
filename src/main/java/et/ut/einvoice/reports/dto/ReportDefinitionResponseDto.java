package et.ut.einvoice.reports.dto;

import et.ut.einvoice.reports.domain.ReportDefinition;

/** Report catalog fields used to select and label a report. */
public record ReportDefinitionResponseDto(
        String id,
        String title,
        String amharicTitle,
        String description,
        String iconName,
        String category,
        String exportFormats,
        boolean active,
        int displayOrder
) {
    public static ReportDefinitionResponseDto fromEntity(ReportDefinition definition) {
        return new ReportDefinitionResponseDto(
                definition.getId(), definition.getTitle(), definition.getAmharicTitle(), definition.getDescription(),
                definition.getIconName(), definition.getCategory(), definition.getExportFormats(),
                definition.isActive(), definition.getDisplayOrder()
        );
    }
}
