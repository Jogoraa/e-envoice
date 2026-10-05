package et.ut.einvoice.cashreceipt.dto;

import et.ut.einvoice.cashreceipt.domain.CashReceipt;
import et.ut.einvoice.cashreceipt.domain.CashReceiptPaymentMethod;
import et.ut.einvoice.cashreceipt.domain.CashReceiptPurpose;
import et.ut.einvoice.cashreceipt.domain.CashReceiptStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CashReceiptResponse(
        UUID id,
        UUID tenantId,
        String receiptNumber,
        String payerName,
        String payerTin,
        BigDecimal amount,
        String currency,
        CashReceiptPurpose purpose,
        String purposeDescription,
        UUID relatedInvoiceId,
        String relatedCreditAccountId,
        CashReceiptPaymentMethod paymentMethod,
        String referenceNumber,
        Instant receivedAt,
        String rrn,
        String irn,
        String qrCode,
        CashReceiptStatus status,
        Instant createdAt
) {
    public static CashReceiptResponse fromEntity(CashReceipt entity) {
        return new CashReceiptResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getReceiptNumber(),
                entity.getPayerName(),
                entity.getPayerTin(),
                entity.getAmount(),
                entity.getCurrency(),
                entity.getPurpose(),
                entity.getPurposeDescription(),
                entity.getRelatedInvoiceId(),
                entity.getRelatedCreditAccountId(),
                entity.getPaymentMethod(),
                entity.getReferenceNumber(),
                entity.getReceivedAt(),
                entity.getRrn(),
                entity.getIrn(),
                entity.getQrCode(),
                entity.getStatus(),
                entity.getCreatedAt()
        );
    }
}
