package et.ut.einvoice.purchasevoucher.service;

import et.ut.einvoice.audit.service.AuditService;
import et.ut.einvoice.documents.service.QrCodeService;
import et.ut.einvoice.platform.context.TenantContextHolder;
import et.ut.einvoice.platform.exception.BusinessException;
import et.ut.einvoice.purchasevoucher.domain.PurchaseVoucher;
import et.ut.einvoice.purchasevoucher.domain.PurchaseVoucherLine;
import et.ut.einvoice.purchasevoucher.dto.CreatePurchaseVoucherRequest;
import et.ut.einvoice.purchasevoucher.repository.PurchaseVoucherRepository;
import et.ut.einvoice.taxpayer.domain.TaxpayerProfile;
import et.ut.einvoice.taxpayer.repository.TaxpayerProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PurchaseVoucherService {

    private final PurchaseVoucherRepository purchaseVoucherRepository;
    private final TaxpayerProfileRepository taxpayerProfileRepository;
    private final QrCodeService qrCodeService;
    private final AuditService auditService;

    public PurchaseVoucherService(PurchaseVoucherRepository purchaseVoucherRepository,
                                  TaxpayerProfileRepository taxpayerProfileRepository,
                                  QrCodeService qrCodeService,
                                  AuditService auditService) {
        this.purchaseVoucherRepository = purchaseVoucherRepository;
        this.taxpayerProfileRepository = taxpayerProfileRepository;
        this.qrCodeService = qrCodeService;
        this.auditService = auditService;
    }

    @Transactional
    public PurchaseVoucher createPurchaseVoucher(CreatePurchaseVoucherRequest request) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();

        TaxpayerProfile buyer = taxpayerProfileRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new BusinessException(
                        "TAXPAYER_PROFILE_NOT_FOUND",
                        "Taxpayer profile not configured for tenant " + tenantId,
                        "የታክስ ከፋይ መረጃ አልተገኘም።",
                        HttpStatus.PRECONDITION_FAILED
                ));

        if (request.items() == null || request.items().isEmpty()) {
            throw new BusinessException(
                    "EMPTY_VOUCHER_ITEMS",
                    "Purchase voucher must contain at least one line item",
                    "የግዢ ማረጋገጫ ሰነድ ቢያንስ አንድ እቃ/አገልግሎት ማካተት አለበት።",
                    HttpStatus.BAD_REQUEST
            );
        }

        UUID voucherId = UUID.randomUUID();
        String voucherNumber = "PV-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String rrn = "RRN-PV-" + UUID.randomUUID().toString().substring(0, 18).toUpperCase();

        BigDecimal runningTotal = BigDecimal.ZERO;
        BigDecimal runningTax = BigDecimal.ZERO;

        PurchaseVoucher voucher = new PurchaseVoucher(
                voucherId,
                tenantId,
                voucherNumber,
                buyer.getId(),
                buyer.getTin(),
                request.supplierName().trim(),
                request.supplierTin() != null ? request.supplierTin().trim() : null,
                request.supplierIdNumber(),
                request.supplierIdType(),
                request.supplierPhone(),
                request.supplierAddress(),
                request.unavailableReason(),
                request.reasonDescription(),
                request.transactionDate() != null ? request.transactionDate() : Instant.now(),
                BigDecimal.ZERO, // calculated below
                BigDecimal.ZERO,
                request.withholdingAmount() != null ? request.withholdingAmount() : BigDecimal.ZERO,
                BigDecimal.ZERO,
                request.currency() != null ? request.currency().trim() : "ETB",
                request.attachmentReference(),
                rrn,
                null,
                null
        );

        int lineNumber = 1;
        for (CreatePurchaseVoucherRequest.LineItemDto itemDto : request.items()) {
            BigDecimal totalPrice = itemDto.quantity().multiply(itemDto.unitPrice()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal taxRate = itemDto.taxRate() != null ? itemDto.taxRate() : BigDecimal.ZERO;
            BigDecimal taxAmount = totalPrice.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);

            PurchaseVoucherLine line = new PurchaseVoucherLine(
                    UUID.randomUUID(),
                    tenantId,
                    voucher,
                    lineNumber++,
                    itemDto.itemDescription().trim(),
                    itemDto.quantity(),
                    itemDto.unitOfMeasure(),
                    itemDto.unitPrice(),
                    totalPrice,
                    taxRate,
                    taxAmount
            );
            voucher.addLine(line);

            runningTotal = runningTotal.add(totalPrice);
            runningTax = runningTax.add(taxAmount);
        }

        BigDecimal withholding = request.withholdingAmount() != null ? request.withholdingAmount() : BigDecimal.ZERO;
        BigDecimal netPayable = runningTotal.add(runningTax).subtract(withholding).setScale(2, RoundingMode.HALF_UP);

        // Update calculated totals on voucher
        setField(voucher, "totalAmount", runningTotal);
        setField(voucher, "totalTaxAmount", runningTax);
        setField(voucher, "netPayableAmount", netPayable);

        String qrPayload = String.format("TYPE:PURCHASE_VOUCHER|BUYER:%s|SUPPLIER:%s|VOUCHER:%s|RRN:%s|AMT:%.2f",
                buyer.getTin(), request.supplierName(), voucherNumber, rrn, netPayable);
        String qrBase64 = qrCodeService.generateQrCodeBase64(qrPayload, 180, 180);
        setField(voucher, "qrCode", qrBase64);

        PurchaseVoucher saved = purchaseVoucherRepository.save(voucher);

        auditService.recordEvent(
                tenantId,
                "USER",
                "CREATE_PURCHASE_VOUCHER",
                "PURCHASE_VOUCHER",
                saved.getId().toString(),
                "VOUCHER_NO=" + voucherNumber + ", SUPPLIER=" + request.supplierName() + ", REASON=" + request.unavailableReason() + ", AMT=" + netPayable
        );

        return saved;
    }

    @Transactional(readOnly = true)
    public PurchaseVoucher getPurchaseVoucher(UUID id) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        return purchaseVoucherRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new BusinessException(
                        "PURCHASE_VOUCHER_NOT_FOUND",
                        "Purchase voucher " + id + " not found",
                        "የግዢ ማረጋገጫ ሰነድ አልተገኘም።",
                        HttpStatus.NOT_FOUND
                ));
    }

    @Transactional(readOnly = true)
    public List<PurchaseVoucher> listPurchaseVouchers(String supplierTin) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        if (supplierTin != null && !supplierTin.isBlank()) {
            return purchaseVoucherRepository.findByTenantIdAndSupplierTinOrderByTransactionDateDesc(tenantId, supplierTin.trim());
        }
        return purchaseVoucherRepository.findByTenantIdOrderByTransactionDateDesc(tenantId);
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to set field " + fieldName, ex);
        }
    }
}
