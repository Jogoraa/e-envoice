package et.ut.einvoice.compliance;

import et.ut.einvoice.catalog.domain.Category;
import et.ut.einvoice.catalog.domain.Product;
import et.ut.einvoice.catalog.repository.CategoryRepository;
import et.ut.einvoice.catalog.repository.ProductRepository;
import et.ut.einvoice.invoicing.domain.Invoice;
import et.ut.einvoice.invoicing.domain.InvoiceStatus;
import et.ut.einvoice.invoicing.repository.InvoiceRepository;
import et.ut.einvoice.platform.context.TenantContext;
import et.ut.einvoice.platform.context.TenantContextHolder;
import et.ut.einvoice.platform.exception.BusinessException;
import et.ut.einvoice.portability.domain.PurgeAuditCertificate;
import et.ut.einvoice.portability.domain.TenantExitRequest;
import et.ut.einvoice.portability.domain.TenantExitStatus;
import et.ut.einvoice.portability.repository.PurgeAuditCertificateRepository;
import et.ut.einvoice.portability.repository.TenantExitRequestRepository;
import et.ut.einvoice.portability.service.TenantDecommissioningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class TenantDecommissioningTestSuite {

    @Autowired
    private TenantDecommissioningService decommissioningService;

    @Autowired
    private TenantExitRequestRepository exitRequestRepository;

    @Autowired
    private PurgeAuditCertificateRepository purgeCertificateRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private UUID departingTenant;

    @BeforeEach
    void setUp() {
        purgeCertificateRepository.deleteAll();
        exitRequestRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();

        departingTenant = UUID.randomUUID();

        // Seed non-statutory data
        UUID catId = UUID.randomUUID();
        Category cat = new Category(catId, departingTenant, "ELEC-CAT", "Electronics", "PRODUCT", "Electronics category", null);
        categoryRepository.save(cat);
        Product prod = new Product(UUID.randomUUID(), departingTenant, null, "SP-01", "SKU-SP-01", null,
                "Smartphone", catId, "ELEC-CAT", "PCS", new BigDecimal("15000.00"), "VAT15", true, new BigDecimal("10.0000"));
        productRepository.save(prod);

        // Seed a statutory invoice with unique IRN
        String uniqueIrn = "IRN-MIGRATE-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();
        Invoice inv = new Invoice(
                UUID.randomUUID(), departingTenant, "INV-MIGRATE-" + UUID.randomUUID().toString().substring(0, 8), 1L, java.time.Instant.now(),
                et.ut.einvoice.invoicing.domain.TransactionType.B2C, "CASH", "IMMEDIATE"
        );
        inv.markRegistered(uniqueIrn, "RRN-MIGRATE-" + UUID.randomUUID().toString().substring(0, 8), java.time.Instant.now().toString(), "QR-MIGRATE", "signed");
        invoiceRepository.save(inv);

        // Set Tenant Admin context
        setContext(departingTenant, "tenant.admin", "ROLE_TENANT_ADMIN");
    }

    private void setContext(UUID tenantId, String username, String role) {
        TenantContextHolder.setContext(TenantContext.create(tenantId, username, Set.of(role)));
        var auth = new UsernamePasswordAuthenticationToken(
                username, "password", List.of(new SimpleGrantedAuthority(role))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("Stage 6: Tenant Exit Request Freezes Tenant and Sets Status to FROZEN")
    void test_RequestExit_FreezesTenant() {
        TenantExitRequest request = decommissioningService.requestExit("NextGen Invoicing Ltd", "Contract expiration");
        assertNotNull(request);
        assertEquals(departingTenant, request.getTenantId());
        assertEquals(TenantExitStatus.FROZEN, request.getExitStatus());
        assertNotNull(request.getFrozenAt());
    }

    @Test
    @DisplayName("Stage 6: Retention Evaluation Separates Statutory Invoices from Purge-Eligible Catalogs")
    void test_EvaluateRetention_SeparatesStatutoryFromPurgeEligible() {
        var summary = decommissioningService.evaluateRetention(departingTenant);
        assertNotNull(summary);
        assertTrue(summary.statutoryInvoicesRetained() >= 1, "Statutory invoices must be retained by law");
        assertEquals(1, summary.nonStatutoryProductsEligibleForPurge());
        assertEquals(1, summary.nonStatutoryCategoriesEligibleForPurge());
        assertTrue(summary.statutoryRetentionJustification().contains("10 years by law"));
    }

    @Test
    @DisplayName("Stage 6: Dual Authorized Purge Deletes Operational Data but Strictly Retains Fiscal Invoices")
    void test_DualAuthorizedPurge_PurgesCatalog_RetainsInvoices() {
        TenantExitRequest request = decommissioningService.requestExit("NextGen Invoicing Ltd", "Moving to on-premise");
        TenantExitRequest confirmed = decommissioningService.verifyAndConfirmArchive(request.getId(), "SHA256-ARCHIVE-MOCK-HASH");
        assertEquals(TenantExitStatus.TENANT_CONFIRMED, confirmed.getExitStatus());

        // Switch to PLATFORM_ADMIN for dual authorization purge
        setContext(departingTenant, "platform.admin", "ROLE_PLATFORM_ADMIN");

        PurgeAuditCertificate cert = decommissioningService.executeDualAuthorizedPurge(
                request.getId(),
                "TENANT_ADMIN_JOHN_DOE",
                "PLATFORM_ADMIN_JANE_SMITH"
        );

        assertNotNull(cert);
        assertTrue(cert.getRecordsPurgedCount() >= 2, "Product and category records must be purged");
        assertTrue(cert.getStatutoryRecordsRetainedCount() >= 1, "Invoices must be strictly retained");
        assertNotNull(cert.getCertificateNumber());
        assertNotNull(cert.getCertificateHash());

        // Verify database: Products and categories purged
        assertEquals(0, productRepository.count());
        assertEquals(0, categoryRepository.count());

        // Verify database: Statutory invoice STRICTLY PRESERVED
        assertTrue(invoiceRepository.count() >= 1, "Fiscal invoice must NEVER be deleted during tenant exit");

        // Verify exit request status transitioned to MIGRATED
        TenantExitRequest updated = exitRequestRepository.findById(request.getId()).orElseThrow();
        assertEquals(TenantExitStatus.MIGRATED, updated.getExitStatus());
    }

    @Test
    @DisplayName("Stage 6: Purge Without Dual Authorization Fails Closed")
    void test_PurgeWithoutDualAuth_FailsClosed() {
        TenantExitRequest request = decommissioningService.requestExit("NextGen", "Migration");
        decommissioningService.verifyAndConfirmArchive(request.getId(), "HASH");

        setContext(departingTenant, "platform.admin", "ROLE_PLATFORM_ADMIN");

        BusinessException ex = assertThrows(BusinessException.class, () ->
                decommissioningService.executeDualAuthorizedPurge(request.getId(), null, "PLATFORM_ADMIN")
        );
        assertEquals("DUAL_AUTHORIZATION_REQUIRED", ex.getCode());
    }
}
