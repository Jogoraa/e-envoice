package et.ut.einvoice.withholding.dto;

import et.ut.einvoice.withholding.domain.WithholdingReceipt;
import et.ut.einvoice.withholding.domain.WithholdingType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WithholdingReceiptResponse(
        UUID id,
        UUID tenantId,
        String receiptNumber,
        WithholdingType withholdingType,
        UUID relatedInvoiceId,
        String relatedInvoiceIrn,
        String withholdingAgentTin,
        String withholdingAgentName,
        String taxpayerTin,
        String taxpayerName,
        BigDecimal taxBaseAmount,
        BigDecimal withheldTaxRate,
        BigDecimal withheldTaxAmount,
        String paymentReference,
        Instant issueDate,
        String rrn,
        String qrCode,
        String status,
        Instant createdAt
) {
    public static WithholdingReceiptResponse fromEntity(WithholdingReceipt entity) {
        return new WithholdingReceiptResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getReceiptNumber(),
                entity.getWithholdingType(),
                entity.getRelatedInvoiceId(),
                entity.getRelatedInvoiceIrn(),
                entity.getWithholdingAgentTin(),
                entity.getWithholdingAgentName(),
                entity.getTaxpayerTin(),
                entity.getTaxpayerName(),
                entity.getTaxBaseAmount(),
                entity.getWithheldTaxRate(),
                entity.getWithheldTaxAmount(),
                entity.getPaymentReference(),
                entity.getIssueDate(),
                entity.getRrn(),
                entity.getQrCode(),
                entity.getStatus(),
                entity.getCreatedAt()
        );
    }
}
