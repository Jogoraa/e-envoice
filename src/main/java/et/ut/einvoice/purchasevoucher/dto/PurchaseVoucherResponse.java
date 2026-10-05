package et.ut.einvoice.purchasevoucher.dto;

import et.ut.einvoice.purchasevoucher.domain.PurchaseVoucher;
import et.ut.einvoice.purchasevoucher.domain.UnavailableReceiptReason;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PurchaseVoucherResponse(
        UUID id,
        UUID tenantId,
        String voucherNumber,
        UUID buyerTaxpayerId,
        String buyerTin,
        String supplierName,
        String supplierTin,
        String supplierIdNumber,
        String supplierIdType,
        String supplierPhone,
        String supplierAddress,
        UnavailableReceiptReason unavailableReason,
        String reasonDescription,
        Instant transactionDate,
        BigDecimal totalAmount,
        BigDecimal totalTaxAmount,
        BigDecimal withholdingAmount,
        BigDecimal netPayableAmount,
        String currency,
        String attachmentReference,
        String rrn,
        String irn,
        String qrCode,
        String status,
        Instant createdAt,
        List<LineItemResponse> lines
) {
    public record LineItemResponse(
            int lineNumber,
            String itemDescription,
            BigDecimal quantity,
            String unitOfMeasure,
            BigDecimal unitPrice,
            BigDecimal totalPrice,
            BigDecimal taxRate,
            BigDecimal taxAmount
    ) {}

    public static PurchaseVoucherResponse fromEntity(PurchaseVoucher voucher) {
        List<LineItemResponse> lineDtos = voucher.getLines().stream()
                .map(line -> new LineItemResponse(
                        line.getLineNumber(),
                        line.getItemDescription(),
                        line.getQuantity(),
                        line.getUnitOfMeasure(),
                        line.getUnitPrice(),
                        line.getTotalPrice(),
                        line.getTaxRate(),
                        line.getTaxAmount()
                )).toList();

        return new PurchaseVoucherResponse(
                voucher.getId(),
                voucher.getTenantId(),
                voucher.getVoucherNumber(),
                voucher.getBuyerTaxpayerId(),
                voucher.getBuyerTin(),
                voucher.getSupplierName(),
                voucher.getSupplierTin(),
                voucher.getSupplierIdNumber(),
                voucher.getSupplierIdType(),
                voucher.getSupplierPhone(),
                voucher.getSupplierAddress(),
                voucher.getUnavailableReason(),
                voucher.getReasonDescription(),
                voucher.getTransactionDate(),
                voucher.getTotalAmount(),
                voucher.getTotalTaxAmount(),
                voucher.getWithholdingAmount(),
                voucher.getNetPayableAmount(),
                voucher.getCurrency(),
                voucher.getAttachmentReference(),
                voucher.getRrn(),
                voucher.getIrn(),
                voucher.getQrCode(),
                voucher.getStatus(),
                voucher.getCreatedAt(),
                lineDtos
        );
    }
}
