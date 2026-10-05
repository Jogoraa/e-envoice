package et.ut.einvoice.compliance;

import et.ut.einvoice.government.domain.GovernmentRegistrationProvider;
import et.ut.einvoice.marketplace.domain.MarketplaceMerchant;
import et.ut.einvoice.marketplace.domain.MerchantStatus;
import et.ut.einvoice.marketplace.repository.MarketplaceMerchantRepository;
import et.ut.einvoice.marketplace.service.MarketplaceService;
import et.ut.einvoice.marketplace.service.MarketplaceService.MarketplaceItem;
import et.ut.einvoice.marketplace.service.MarketplaceService.MultiSellerOrderRequest;
import et.ut.einvoice.marketplace.service.MarketplaceService.MultiSellerOrderResponse;
import et.ut.einvoice.platform.context.TenantContext;
import et.ut.einvoice.platform.context.TenantContextHolder;
import et.ut.einvoice.platform.exception.BusinessException;
import et.ut.einvoice.taxpayer.domain.TaxpayerProfile;
import et.ut.einvoice.taxpayer.repository.TaxpayerProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

@SpringBootTest
@ActiveProfiles("test")
public class MarketplaceComplianceTestSuite {

    @Autowired
    private MarketplaceService marketplaceService;

    @Autowired
    private MarketplaceMerchantRepository merchantRepository;

    @Autowired
    private TaxpayerProfileRepository profileRepository;

    @MockBean
    private GovernmentRegistrationProvider governmentRegistrationProvider;

    private UUID marketplaceTenant;
    private MarketplaceMerchant merchantA;
    private MarketplaceMerchant merchantB;

    @BeforeEach
    void setUp() {
        merchantRepository.deleteAll();
        profileRepository.deleteAll();

        marketplaceTenant = UUID.randomUUID();

        // Seed marketplace platform profile
        TaxpayerProfile mktProfile = new TaxpayerProfile(
                marketplaceTenant, "0011223344", "VAT-11223", "Ethiopia Digital Souq PLC", "Digital Souq",
                "Addis Ababa", "Bole", "0911001122", "souq@ut.et", "SYS-MKT-01", "POS"
        );
        profileRepository.save(mktProfile);

        // Default mock for invoice registration
        Mockito.when(governmentRegistrationProvider.registerInvoice(any(), any(), anyString()))
                .thenAnswer(inv -> {
                    String irn = "INV-EIRS-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();
                    String rrn = "RRN-EIRS-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();
                    return GovernmentRegistrationProvider.GovernmentRegistrationResult.success(
                            irn, rrn, "2026-10-05T10:00:00Z", "QR-MKT", "signed-payload"
                    );
                });

        // Set Marketplace Tenant Admin Context
        setContext(marketplaceTenant, "mkt.admin", "ROLE_PLATFORM_ADMIN");

        // Register Merchant A
        merchantA = new MarketplaceMerchant(
                UUID.randomUUID(), marketplaceTenant, "0055443322", "Blue Nile Crafts PLC", "Blue Nile Crafts",
                "Bahir Dar", "0918112233", "bluenile@crafts.et"
        );
        merchantRepository.save(merchantA);

        // Register Merchant B
        merchantB = new MarketplaceMerchant(
                UUID.randomUUID(), marketplaceTenant, "0077889900", "Lalibela Coffee Roasters PLC", "Lalibela Coffee",
                "Addis Ababa", "0911998877", "info@lalibelacoffee.et"
        );
        merchantRepository.save(merchantB);
    }

    private void setContext(UUID tenantId, String username, String role) {
        TenantContextHolder.setContext(TenantContext.create(tenantId, username, Set.of(role)));
        var auth = new UsernamePasswordAuthenticationToken(
                username, "password", List.of(new SimpleGrantedAuthority(role))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("Stage 7: Multi-Seller Order Generates Separate Independent Fiscal Invoices")
    void test_MultiSellerOrder_SplitsIntoIndependentInvoices() {
        MultiSellerOrderRequest request = new MultiSellerOrderRequest(
                "ORD-2026-901",
                "Abebe Bikila",
                "0099887766",
                List.of(
                        new MarketplaceItem("0055443322", "Handwoven Scarf", BigDecimal.valueOf(2), new BigDecimal("500.00"), "VAT15"),
                        new MarketplaceItem("0077889900", "Yirgacheffe Coffee 1KG", BigDecimal.valueOf(3), new BigDecimal("800.00"), "VAT15")
                ),
                new BigDecimal("150.00") // Platform commission
        );

        MultiSellerOrderResponse response = marketplaceService.processMultiSellerOrder(request);
        assertNotNull(response);
        assertEquals("ORD-2026-901", response.orderNumber());

        // Must produce 2 independent merchant invoices + 1 marketplace commission invoice
        assertEquals(2, response.merchantFiscalInvoices().size(), "Order must generate exactly 2 merchant invoices");
        assertNotNull(response.marketplaceCommissionInvoice(), "Marketplace commission invoice must be generated");

        // Verify independent documents
        assertNotEquals(
                response.merchantFiscalInvoices().get(0).irn(),
                response.merchantFiscalInvoices().get(1).irn()
        );
    }

    @Test
    @DisplayName("Stage 7: Authority Suspension Strictly Blocks Merchant From Issuing Invoices")
    void test_SuspendedMerchant_CannotIssueInvoices() {
        // Authority suspends Merchant A
        marketplaceService.suspendMerchantByAuthority(
                merchantA.getId(),
                "Non-compliance with tax withholding under Directive Art. 14(4)",
                "MOR-ORDER-2026-SUSP-01"
        );

        MarketplaceMerchant suspended = merchantRepository.findById(merchantA.getId()).orElseThrow();
        assertEquals(MerchantStatus.SUSPENDED_BY_AUTHORITY, suspended.getMerchantStatus());

        // Order containing Merchant A goods must fail closed
        MultiSellerOrderRequest request = new MultiSellerOrderRequest(
                "ORD-FAIL-001",
                "Kebede Michael",
                null,
                List.of(
                        new MarketplaceItem("0055443322", "Suspended Merchant Goods", BigDecimal.ONE, new BigDecimal("1000.00"), "VAT15")
                ),
                BigDecimal.ZERO
        );

        BusinessException ex = assertThrows(BusinessException.class, () ->
                marketplaceService.processMultiSellerOrder(request)
        );
        assertEquals("MERCHANT_SUSPENDED_BY_AUTHORITY", ex.getCode());

        // Reinstate Merchant A
        marketplaceService.reinstateMerchantByAuthority(merchantA.getId(), "MOR-REINSTATE-2026-01");
        MarketplaceMerchant reinstated = merchantRepository.findById(merchantA.getId()).orElseThrow();
        assertEquals(MerchantStatus.REINSTATED_BY_AUTHORITY, reinstated.getMerchantStatus());

        // Now order succeeds
        MultiSellerOrderResponse successResp = marketplaceService.processMultiSellerOrder(request);
        assertNotNull(successResp);
    }
}
