package et.ut.einvoice.invoicing.dto;

import et.ut.einvoice.invoicing.domain.ManualFiscalDocument;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ManualReprintResponseDto(
        UUID id,
        UUID tenantId,
        String manualDocumentNumber,
        String manualBook,
        String watermark,
        boolean isDuplicate,
        int duplicateReprintCount,
        BigDecimal totalAmount,
        BigDecimal taxAmount,
        String irn,
        Instant reprintedAt
) {
    public static ManualReprintResponseDto fromEntity(ManualFiscalDocument doc) {
        return new ManualReprintResponseDto(
                doc.getId(),
                doc.getTenantId(),
                doc.getManualDocumentNumber(),
                doc.getManualBook(),
                "DUPLICATE - REPRINT OF REGISTERED TAX INVOICE",
                true,
                doc.getDuplicateReprintCount(),
                doc.getTotalAmount(),
                doc.getTaxAmount(),
                doc.getIrn(),
                Instant.now()
        );
    }
}
