package et.ut.einvoice.compliance;

import et.ut.einvoice.invoicing.domain.Invoice;
import et.ut.einvoice.invoicing.domain.InvoiceLine;
import et.ut.einvoice.invoicing.domain.InvoiceStatus;
import et.ut.einvoice.invoicing.domain.TransactionType;
import et.ut.einvoice.invoicing.repository.InvoiceRepository;
import et.ut.einvoice.platform.context.TenantContext;
import et.ut.einvoice.platform.context.TenantContextHolder;
import et.ut.einvoice.platform.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class FinancialImmutabilityAndRlsTestSuite {

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;
    private UUID tenantA;
    private UUID tenantB;

    @BeforeEach
    void setUp() {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();
        TenantContextHolder.setContext(TenantContext.createWithClient(tenantA, "CLIENT_A", Set.of("ROLE_TENANT_ADMIN"), Set.of("invoice:create", "invoice:read"), UUID.randomUUID().toString()));
    }

    private Invoice createRegisteredInvoice(UUID tenantId) {
        Invoice invoice = new Invoice(
                UUID.randomUUID(), tenantId, "DOC-" + UUID.randomUUID(), 100L,
                Instant.now(), TransactionType.B2C, "CASH", "IMMEDIATE"
        );
        InvoiceLine line = new InvoiceLine(
                UUID.randomUUID(), tenantId, 1, "SKU-01", "Item", "goods", "PCS",
                BigDecimal.ONE, new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("100.00"),
                "VAT15", new BigDecimal("0.15"), new BigDecimal("15.00"), BigDecimal.ZERO, new BigDecimal("115.00")
        );
        invoice.addLine(line);
        invoice.recalculateTotals();
        invoice.markRegistered("IRN-" + UUID.randomUUID(), "RRN-1", Instant.now().toString(), "qr", "sig");
        return invoiceRepository.save(invoice);
    }

    @Test
    @DisplayName("Immutability 1: Direct JPA Repository mutation on REGISTERED invoice is blocked")
    void test_DirectJpaRepository_MutationBlocked() {
        Invoice registered = createRegisteredInvoice(tenantA);
        UUID invoiceId = registered.getId();

        // Attempting to mutate grandTotal on a registered invoice via JPA repository
        assertThrows(BusinessException.class, () -> {
            transactionTemplate.execute(status -> {
                Invoice loaded = invoiceRepository.findById(invoiceId).orElseThrow();
                // Mutating amount on registered invoice
                loaded.setGrandTotal(new BigDecimal("9999.00"));
                return invoiceRepository.save(loaded);
            });
        });

        // Verify the persisted grand total remained unchanged
        Invoice unchanged = invoiceRepository.findById(invoiceId).orElseThrow();
        assertEquals(new BigDecimal("115.00"), unchanged.getGrandTotal());
    }

    @Test
    @DisplayName("Immutability 2: Direct mutation on CANCELLED invoice is blocked")
    void test_CancelledInvoice_MutationBlocked() {
        Invoice registered = createRegisteredInvoice(tenantA);
        registered.markCancelled();
        Invoice cancelled = invoiceRepository.save(registered);
        UUID invoiceId = cancelled.getId();

        Exception thrown = assertThrows(Exception.class, () -> {
            transactionTemplate.execute(status -> {
                Invoice loaded = invoiceRepository.findById(invoiceId).orElseThrow();
                loaded.setGrandTotal(new BigDecimal("50.00"));
                return invoiceRepository.save(loaded);
            });
        });
        assertTrue(thrown.getMessage().contains("immutable") ||
                   (thrown.getCause() != null && thrown.getCause().getMessage().contains("immutable")) ||
                   (thrown.getCause() != null && thrown.getCause().getCause() instanceof BusinessException));

        Invoice verify = invoiceRepository.findById(invoiceId).orElseThrow();
        assertEquals(InvoiceStatus.CANCELLED, verify.getStatus());
        assertEquals(new BigDecimal("115.00"), verify.getGrandTotal());
    }

    @Test
    @DisplayName("Immutability 3: Adding lines or recalculating totals on REGISTERED invoice throws FINANCIAL_MUTATION_FORBIDDEN")
    void test_AddLineOrRecalculate_OnRegisteredInvoice_ThrowsForbidden() {
        Invoice registered = createRegisteredInvoice(tenantA);

        InvoiceLine extraLine = new InvoiceLine(
                UUID.randomUUID(), tenantA, 2, "SKU-02", "Extra Item", "goods", "PCS",
                BigDecimal.ONE, new BigDecimal("20.00"), BigDecimal.ZERO, new BigDecimal("20.00"),
                "VAT15", new BigDecimal("0.15"), new BigDecimal("3.00"), BigDecimal.ZERO, new BigDecimal("23.00")
        );

        BusinessException ex1 = assertThrows(BusinessException.class, () -> registered.addLine(extraLine));
        assertEquals("FINANCIAL_MUTATION_FORBIDDEN", ex1.getCode());

        BusinessException ex2 = assertThrows(BusinessException.class, registered::recalculateTotals);
        assertEquals("FINANCIAL_MUTATION_FORBIDDEN", ex2.getCode());
    }

    @Test
    @DisplayName("Immutability 4: Cross-Tenant context boundary isolation prevents data leakage")
    void test_CrossTenant_QueryIsolation() {
        Invoice invoiceA = createRegisteredInvoice(tenantA);

        // Switch Tenant Context to Tenant B
        TenantContextHolder.setContext(TenantContext.createWithClient(
                tenantB, "CLIENT_B", Set.of("ROLE_TENANT_ADMIN"), Set.of("invoice:read"), UUID.randomUUID().toString()
        ));

        // Querying Tenant A's invoice ID under Tenant B context returns empty
        assertTrue(invoiceRepository.findByIdAndTenantId(invoiceA.getId(), tenantB).isEmpty());
        assertTrue(invoiceRepository.findByIrnAndTenantId(invoiceA.getIrn(), tenantB).isEmpty());
    }
}
