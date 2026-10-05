package et.ut.einvoice.marketplace.service;

import et.ut.einvoice.audit.service.AuditService;
import et.ut.einvoice.invoicing.domain.Invoice;
import et.ut.einvoice.invoicing.domain.InvoiceLine;
import et.ut.einvoice.invoicing.domain.TransactionType;
import et.ut.einvoice.invoicing.dto.CreateInvoiceRequest;
import et.ut.einvoice.invoicing.dto.InvoiceResponseDto;
import et.ut.einvoice.invoicing.service.InvoiceService;
import et.ut.einvoice.marketplace.domain.MarketplaceMerchant;
import et.ut.einvoice.marketplace.domain.MerchantStatus;
import et.ut.einvoice.marketplace.repository.MarketplaceMerchantRepository;
import et.ut.einvoice.platform.context.TenantContextHolder;
import et.ut.einvoice.platform.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class MarketplaceService {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceService.class);

    private final MarketplaceMerchantRepository merchantRepository;
    private final InvoiceService invoiceService;
    private final AuditService auditService;

    public MarketplaceService(
            MarketplaceMerchantRepository merchantRepository,
            InvoiceService invoiceService,
            AuditService auditService
    ) {
        this.merchantRepository = merchantRepository;
        this.invoiceService = invoiceService;
        this.auditService = auditService;
    }

    public record MarketplaceItem(
            String merchantTin,
            String productDescription,
            BigDecimal quantity,
            BigDecimal unitPrice,
            String taxCode
    ) {}

    public record MultiSellerOrderRequest(
            String orderNumber,
            String buyerName,
            String buyerTin,
            List<MarketplaceItem> items,
            BigDecimal marketplaceCommissionAmount
    ) {}

    public record MultiSellerOrderResponse(
            String orderNumber,
            List<InvoiceResponseDto> merchantFiscalInvoices,
            InvoiceResponseDto marketplaceCommissionInvoice,
            BigDecimal grandTotal
    ) {}

    @Transactional
    public MarketplaceMerchant registerMerchant(MarketplaceMerchant merchant) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        MarketplaceMerchant saved = merchantRepository.save(merchant);

        auditService.recordEvent(
                tenantId,
                "SYSTEM",
                "MARKETPLACE_ADMIN",
                "REGISTER_MERCHANT",
                "MARKETPLACE_MERCHANT",
                saved.getId().toString(),
                "TIN=" + saved.getMerchantTin() + ", NAME=" + saved.getLegalName(),
                "127.0.0.1"
        );

        return saved;
    }

    @Transactional
    @PreAuthorize("hasAuthority('ROLE_PLATFORM_ADMIN')")
    public MarketplaceMerchant suspendMerchantByAuthority(UUID merchantId, String reason, String authorityRef) {
        MarketplaceMerchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new BusinessException("MERCHANT_NOT_FOUND", "Marketplace merchant not found: " + merchantId));

        merchant.suspendByAuthority(reason);
        MarketplaceMerchant saved = merchantRepository.save(merchant);

        auditService.recordEvent(
                merchant.getMarketplaceTenantId(),
                "SYSTEM",
                "AUTHORITY_COMMAND",
                "SUSPEND_MERCHANT",
                "MARKETPLACE_MERCHANT",
                saved.getId().toString(),
                "REF=" + authorityRef + ", REASON=" + reason,
                "127.0.0.1"
        );

        log.warn("Authority suspended marketplace merchant {} (TIN: {}). Reason: {}",
                merchant.getId(), merchant.getMerchantTin(), reason);
        return saved;
    }

    @Transactional
    @PreAuthorize("hasAuthority('ROLE_PLATFORM_ADMIN')")
    public MarketplaceMerchant reinstateMerchantByAuthority(UUID merchantId, String authorityRef) {
        MarketplaceMerchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new BusinessException("MERCHANT_NOT_FOUND", "Marketplace merchant not found: " + merchantId));

        merchant.reinstateByAuthority();
        MarketplaceMerchant saved = merchantRepository.save(merchant);

        auditService.recordEvent(
                merchant.getMarketplaceTenantId(),
                "SYSTEM",
                "AUTHORITY_COMMAND",
                "REINSTATE_MERCHANT",
                "MARKETPLACE_MERCHANT",
                saved.getId().toString(),
                "REF=" + authorityRef,
                "127.0.0.1"
        );

        log.info("Authority reinstated marketplace merchant {} (TIN: {})", merchant.getId(), merchant.getMerchantTin());
        return saved;
    }

    /**
     * Splits one marketplace checkout order into independent fiscal invoices:
     * - Distinct fiscal invoice per merchant TIN (issued under seller identity)
     * - Distinct marketplace commission invoice (where applicable)
     * - Enforces authority suspension check: suspended merchants CANNOT issue invoices.
     */
    @Transactional
    public MultiSellerOrderResponse processMultiSellerOrder(MultiSellerOrderRequest request) {
        UUID marketplaceTenant = TenantContextHolder.getRequiredContext().tenantId();

        // 1. Group items by Merchant TIN
        Map<String, List<MarketplaceItem>> itemsByMerchant = new java.util.HashMap<>();
        for (MarketplaceItem item : request.items()) {
            itemsByMerchant.computeIfAbsent(item.merchantTin(), k -> new ArrayList<>()).add(item);
        }

        // 2. Validate all merchants: check suspension status
        for (String merchantTin : itemsByMerchant.keySet()) {
            MarketplaceMerchant merchant = merchantRepository.findByMarketplaceTenantIdAndMerchantTin(marketplaceTenant, merchantTin)
                    .orElseThrow(() -> new BusinessException("MERCHANT_NOT_REGISTERED",
                            "Merchant with TIN " + merchantTin + " is not registered in this marketplace"));

            if (merchant.getMerchantStatus() == MerchantStatus.SUSPENDED_BY_AUTHORITY) {
                throw new BusinessException("MERCHANT_SUSPENDED_BY_AUTHORITY",
                        "Merchant " + merchant.getLegalName() + " (TIN: " + merchantTin + ") has been suspended by the Tax Authority. Cannot issue invoices.");
            }
        }

        // 3. Issue separate fiscal invoice for each seller
        List<InvoiceResponseDto> merchantInvoices = new ArrayList<>();
        BigDecimal orderSum = BigDecimal.ZERO;

        for (Map.Entry<String, List<MarketplaceItem>> entry : itemsByMerchant.entrySet()) {
            String tin = entry.getKey();
            List<MarketplaceItem> sellerItems = entry.getValue();

            List<CreateInvoiceRequest.LineItemRequest> invoiceLines = new ArrayList<>();
            for (MarketplaceItem item : sellerItems) {
                invoiceLines.add(new CreateInvoiceRequest.LineItemRequest(
                        "ITEM-" + UUID.randomUUID().toString().substring(0, 6),
                        item.productDescription(),
                        null,
                        "PCS",
                        item.quantity(),
                        item.unitPrice(),
                        BigDecimal.ZERO,
                        item.taxCode() != null ? item.taxCode() : "VAT15",
                        BigDecimal.ZERO
                ));
            }

            CreateInvoiceRequest sellerInvoiceReq = new CreateInvoiceRequest(
                    TransactionType.B2C,
                    "CASH",
                    "IMMEDIATE",
                    new CreateInvoiceRequest.BuyerRequest(
                            request.buyerName(),
                            request.buyerTin(),
                            null,
                            null,
                            null,
                            null,
                            null,
                            "ET",
                            null,
                            null
                    ),
                    invoiceLines,
                    null,
                    null,
                    "MKT-ORDER-" + request.orderNumber() + "-SELLER-" + tin,
                    false
            );

            InvoiceResponseDto sellerInvoice = invoiceService.createAndRegisterInvoice(
                    sellerInvoiceReq,
                    "MKT-IDEM-" + request.orderNumber() + "-" + tin
            );
            merchantInvoices.add(sellerInvoice);
            orderSum = orderSum.add(sellerInvoice.grandTotal());
        }

        // 4. Issue marketplace commission invoice if applicable
        InvoiceResponseDto commissionInvoice = null;
        if (request.marketplaceCommissionAmount() != null && request.marketplaceCommissionAmount().compareTo(BigDecimal.ZERO) > 0) {
            CreateInvoiceRequest commReq = new CreateInvoiceRequest(
                    TransactionType.B2C,
                    "CASH",
                    "IMMEDIATE",
                    new CreateInvoiceRequest.BuyerRequest(
                            request.buyerName(),
                            request.buyerTin(),
                            null,
                            null,
                            null,
                            null,
                            null,
                            "ET",
                            null,
                            null
                    ),
                    List.of(new CreateInvoiceRequest.LineItemRequest(
                            "COMM-SERVICE",
                            "Marketplace Platform Facilitation Fee",
                            null,
                            "SVC",
                            BigDecimal.ONE,
                            request.marketplaceCommissionAmount(),
                            BigDecimal.ZERO,
                            "VAT15",
                            BigDecimal.ZERO
                    )),
                    null,
                    null,
                    "MKT-COMM-" + request.orderNumber(),
                    false
            );
            commissionInvoice = invoiceService.createAndRegisterInvoice(
                    commReq,
                    "MKT-IDEM-COMM-" + request.orderNumber()
            );
            orderSum = orderSum.add(commissionInvoice.grandTotal());
        }

        log.info("Processed multi-seller marketplace order {} into {} independent fiscal documents. Total: {}",
                request.orderNumber(), merchantInvoices.size() + (commissionInvoice != null ? 1 : 0), orderSum);

        return new MultiSellerOrderResponse(request.orderNumber(), merchantInvoices, commissionInvoice, orderSum);
    }
}
