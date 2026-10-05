package et.ut.einvoice.invoicing.dto;

import et.ut.einvoice.invoicing.domain.ManualFiscalDocument;
import et.ut.einvoice.invoicing.domain.ManualFiscalState;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ManualFiscalDocumentResponseDto(
        UUID id,
        UUID tenantId,
        UUID branchId,
        String manualDocumentNumber,
        String manualBook,
        Instant originalIssueTime,
        Instant enteredAt,
        String customerTin,
        String customerName,
        BigDecimal totalAmount,
        BigDecimal subtotal,
        BigDecimal taxAmount,
        String itemsJson,
        String operatorId,
        String outageReference,
        Instant reconciliationDeadline,
        ManualFiscalState eirsRegistrationState,
        String irn,
        int duplicateReprintCount,
        Instant createdAt
) {
    public static ManualFiscalDocumentResponseDto fromEntity(ManualFiscalDocument doc) {
        return new ManualFiscalDocumentResponseDto(
                doc.getId(),
                doc.getTenantId(),
                doc.getBranchId(),
                doc.getManualDocumentNumber(),
                doc.getManualBook(),
                doc.getOriginalIssueTime(),
                doc.getEnteredAt(),
                doc.getCustomerTin(),
                doc.getCustomerName(),
                doc.getTotalAmount(),
                doc.getSubtotal(),
                doc.getTaxAmount(),
                doc.getItemsJson(),
                doc.getOperatorId(),
                doc.getOutageReference(),
                doc.getReconciliationDeadline(),
                doc.getEirsRegistrationState(),
                doc.getIrn(),
                doc.getDuplicateReprintCount(),
                doc.getCreatedAt()
        );
    }
}
