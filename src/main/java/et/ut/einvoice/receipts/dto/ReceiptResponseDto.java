package et.ut.einvoice.receipts.dto;

import et.ut.einvoice.receipts.domain.Receipt;
import et.ut.einvoice.receipts.domain.ReceiptType;

import java.math.BigDecimal;
import java.time.Instant;

/** Public receipt status; database ownership and QR payload are not exposed. */
public record ReceiptResponseDto(
        ReceiptType receiptType,
        String invoiceIrn,
        String rrn,
        String receiptNumber,
        BigDecimal amount,
        BigDecimal withholdingAmount,
        String status,
        Instant createdAt
) {
    public static ReceiptResponseDto fromEntity(Receipt receipt) {
        return new ReceiptResponseDto(
                receipt.getReceiptType(),
                receipt.getInvoiceIrn(),
                receipt.getRrn(),
                receipt.getReceiptNumber(),
                receipt.getAmount(),
                receipt.getWithholdingAmount(),
                receipt.getStatus(),
                receipt.getCreatedAt()
        );
    }
}
