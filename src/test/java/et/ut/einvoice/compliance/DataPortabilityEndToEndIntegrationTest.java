package et.ut.einvoice.compliance;

import et.ut.einvoice.audit.service.AuditService;
import et.ut.einvoice.customer.domain.Customer;
import et.ut.einvoice.customer.repository.CustomerRepository;
import et.ut.einvoice.invoicing.domain.Invoice;
import et.ut.einvoice.invoicing.domain.InvoiceLine;
import et.ut.einvoice.invoicing.domain.TransactionType;
import et.ut.einvoice.invoicing.repository.InvoiceRepository;
import et.ut.einvoice.portability.domain.ExportJob;
import et.ut.einvoice.portability.repository.ExportJobRepository;
import et.ut.einvoice.portability.service.DataPortabilityService;
import et.ut.einvoice.portability.validator.PortabilityArchiveValidator;
import et.ut.einvoice.taxpayer.domain.TaxpayerProfile;
import et.ut.einvoice.taxpayer.repository.TaxpayerProfileRepository;
import et.ut.einvoice.tenancy.domain.Tenant;
import et.ut.einvoice.tenancy.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class DataPortabilityEndToEndIntegrationTest {

    @Autowired
    private DataPortabilityService dataPortabilityService;

    @Autowired
    private ExportJobRepository exportJobRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private TaxpayerProfileRepository taxpayerProfileRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AuditService auditService;

    private final PortabilityArchiveValidator validator = new PortabilityArchiveValidator();

    private UUID tenantAId;
    private UUID tenantBId;

    @BeforeEach
    void setUp() {
        exportJobRepository.deleteAll();
        invoiceRepository.deleteAll();
        customerRepository.deleteAll();
        taxpayerProfileRepository.deleteAll();
        tenantRepository.deleteAll();

        tenantAId = UUID.randomUUID();
        tenantBId = UUID.randomUUID();

        // 1. Seed Tenant A
        Tenant tenantA = new Tenant(tenantAId, "ORG-EXP-A", "Export Test PLC", "Export PLC", "0011223344", "SME");
        tenantA.activate();
        tenantRepository.save(tenantA);

        TaxpayerProfile profileA = new TaxpayerProfile(
                tenantAId, "0011223344", "VAT-112233", "Export Test PLC", "Export PLC",
                "14", "05", "+251911223344", "export@test.com", "8EFBBDD7FA", "ERP"
        );
        taxpayerProfileRepository.save(profileA);

        // 2. Seed Customer for Tenant A
        Customer cust = new Customer(UUID.randomUUID(), tenantAId, "Customer Enterprise Share Co");
        cust.setTin("0099887766");
        cust.setVatNumber("VAT-CUST-1");
        cust.setTradeName("Customer Ent");
        cust.setPhone("+251922334455");
        cust.setEmail("cust@enterprise.et");
        cust.setCity("Addis Ababa");
        cust.setRegion("Bole");
        cust.setPreferredLanguage("en");
        customerRepository.save(cust);

        // 3. Seed Invoices for Tenant A
        for (int i = 1; i <= 3; i++) {
            Invoice invoice = new Invoice(
                    UUID.randomUUID(), tenantAId, "EXP-INV-" + i, (long) i,
                    Instant.now(), TransactionType.B2B, "BANK_TRANSFER", "NET_30"
            );
            invoice.addLine(new InvoiceLine(
                    UUID.randomUUID(), tenantAId, 1, "SKU-EXP-1", "Consulting", "services", "HRS",
                    new BigDecimal("5.0"), new BigDecimal("1000.00"), BigDecimal.ZERO, new BigDecimal("5000.00"),
                    "VAT15", new BigDecimal("0.15"), new BigDecimal("750.00"), BigDecimal.ZERO, new BigDecimal("5750.00")
            ));
            invoice.recalculateTotals();
            invoice.markRegistered("IRN-EXP-" + i, "RRN-EXP-" + i, Instant.now().toString(), "qr-exp-" + i, "sig-exp-" + i);
            invoiceRepository.save(invoice);
        }

        // 4. Seed Audit Events for Tenant A
        auditService.recordEvent(tenantAId, "PORTABILITY_TESTER", "SEED_DATA", "SYSTEM", tenantAId.toString(), "INITIAL_SEED");
    }

    @Test
    @DisplayName("Portability E2E: Full regulatory export generates valid archive with verified cryptographic checksums")
    void testFullExportArchiveGenerationAndValidation() throws Exception {
        // Initiate export
        ExportJob job = dataPortabilityService.initiateExport(tenantAId);
        assertNotNull(job);
        assertEquals(tenantAId, job.getTenantId());

        // Wait up to 5 seconds for async processing
        ExportJob completedJob = null;
        for (int i = 0; i < 50; i++) {
            completedJob = exportJobRepository.findById(job.getId()).orElse(null);
            if (completedJob != null && "COMPLETED".equals(completedJob.getStatus())) {
                break;
            }
            Thread.sleep(100);
        }

        assertNotNull(completedJob, "Export job must be found in database");
        assertEquals("COMPLETED", completedJob.getStatus(), "Export job must complete successfully");
        assertNotNull(completedJob.getArtifactChecksum(), "Checksum must be generated");
        assertEquals(64, completedJob.getArtifactChecksum().length(), "Checksum must be 64-character SHA-256 hex string");

        // Retrieve generated ZIP file from disk
        File archiveFile = dataPortabilityService.getExportArchiveFile(tenantAId, completedJob.getId());
        assertTrue(archiveFile.exists(), "Archive ZIP file must exist on disk");
        assertTrue(archiveFile.length() > 0, "Archive ZIP file must have non-zero size");

        // Validate archive using standalone independent validator
        var validationResult = validator.validateArchive(archiveFile);
        assertTrue(validationResult.isValid(), "Archive must be certified valid by independent validator");
        assertEquals("VALID", validationResult.status());
        assertEquals("0011223344", validationResult.sellerTin());
        assertEquals(tenantAId.toString(), validationResult.tenantId());
        assertEquals("FDRE MoR Directive No. 1142/2026 Art. 5(3)", validationResult.directive());

        // Assert record counts
        assertTrue(validationResult.verifiedRecordCounts().get("invoices") >= 3, "Export must contain all 3 invoices");
        assertTrue(validationResult.verifiedRecordCounts().get("customers") >= 1, "Export must contain customer");
        assertTrue(validationResult.errors().isEmpty(), "Validator must report zero errors");
    }

    @Test
    @DisplayName("Portability E2E: Cross-tenant download is blocked with SecurityException")
    void testCrossTenantArchiveAccessIsBlocked() {
        ExportJob job = dataPortabilityService.initiateExport(tenantAId);
        assertNotNull(job);

        // Tenant B attempts to fetch Tenant A's archive
        assertThrows(SecurityException.class, () ->
                dataPortabilityService.getExportArchiveFile(tenantBId, job.getId())
        );
    }
}
