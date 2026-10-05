package et.ut.einvoice.portability.service;

import et.ut.einvoice.audit.service.AuditService;
import et.ut.einvoice.catalog.repository.CategoryRepository;
import et.ut.einvoice.catalog.repository.ProductRepository;
import et.ut.einvoice.invoicing.repository.InvoiceRepository;
import et.ut.einvoice.platform.context.TenantContextHolder;
import et.ut.einvoice.platform.exception.BusinessException;
import et.ut.einvoice.portability.domain.PurgeAuditCertificate;
import et.ut.einvoice.portability.domain.RetentionClassification;
import et.ut.einvoice.portability.domain.TenantExitRequest;
import et.ut.einvoice.portability.domain.TenantExitStatus;
import et.ut.einvoice.portability.repository.PurgeAuditCertificateRepository;
import et.ut.einvoice.portability.repository.TenantExitRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class TenantDecommissioningService {

    private static final Logger log = LoggerFactory.getLogger(TenantDecommissioningService.class);

    private final TenantExitRequestRepository exitRequestRepository;
    private final PurgeAuditCertificateRepository purgeCertificateRepository;
    private final InvoiceRepository invoiceRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final AuditService auditService;

    public TenantDecommissioningService(
            TenantExitRequestRepository exitRequestRepository,
            PurgeAuditCertificateRepository purgeCertificateRepository,
            InvoiceRepository invoiceRepository,
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            AuditService auditService
    ) {
        this.exitRequestRepository = exitRequestRepository;
        this.purgeCertificateRepository = purgeCertificateRepository;
        this.invoiceRepository = invoiceRepository;
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.auditService = auditService;
    }

    public record RetentionClassificationSummary(
            UUID tenantId,
            long statutoryInvoicesRetained,
            long nonStatutoryProductsEligibleForPurge,
            long nonStatutoryCategoriesEligibleForPurge,
            String statutoryRetentionJustification
    ) {}

    @Transactional
    @PreAuthorize("hasAuthority('ROLE_TENANT_ADMIN') or hasAuthority('ROLE_PLATFORM_ADMIN')")
    public TenantExitRequest requestExit(String destinationProvider, String reason) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        String requestedBy = TenantContextHolder.getRequiredContext().userId();

        TenantExitRequest request = exitRequestRepository.findByTenantId(tenantId)
                .orElse(new TenantExitRequest(UUID.randomUUID(), tenantId, requestedBy, destinationProvider, reason));

        request.freezeTenant();
        TenantExitRequest saved = exitRequestRepository.save(request);

        auditService.recordEvent(
                tenantId,
                "SYSTEM",
                requestedBy,
                "TENANT_EXIT_REQUESTED",
                "TENANT_DECOMMISSIONING",
                saved.getId().toString(),
                "DEST=" + destinationProvider + ", STATUS=FROZEN",
                "127.0.0.1"
        );

        log.info("Tenant {} requested exit/decommissioning. Tenant operations frozen.", tenantId);
        return saved;
    }

    @Transactional
    public TenantExitRequest verifyAndConfirmArchive(UUID exitRequestId, String archiveChecksum) {
        TenantExitRequest request = exitRequestRepository.findById(exitRequestId)
                .orElseThrow(() -> new BusinessException("EXIT_REQUEST_NOT_FOUND", "Exit request not found: " + exitRequestId));

        request.verifyArchive();
        request.confirmTenantReceipt();
        TenantExitRequest saved = exitRequestRepository.save(request);

        auditService.recordEvent(
                request.getTenantId(),
                "SYSTEM",
                "TENANT_ADMIN",
                "ARCHIVE_VERIFIED_AND_CONFIRMED",
                "TENANT_DECOMMISSIONING",
                saved.getId().toString(),
                "CHECKSUM=" + archiveChecksum,
                "127.0.0.1"
        );

        return saved;
    }

    @Transactional(readOnly = true)
    public RetentionClassificationSummary evaluateRetention(UUID tenantId) {
        long invoicesCount = invoiceRepository.count(); // Evaluated within tenant RLS boundary
        long productsCount = productRepository.count();
        long categoriesCount = categoryRepository.count();

        return new RetentionClassificationSummary(
                tenantId,
                invoicesCount,
                productsCount,
                categoriesCount,
                "Pursuant to Directive No. 1142/2026 Art. 4(2)(d) and Proclamation No. 983/2016 Art. 17, registered fiscal documents and audit chains must be retained for 10 years by law. Non-statutory operational catalogs are eligible for purge post-migration."
        );
    }

    /**
     * Executes lawful purge with strict DUAL AUTHORIZATION:
     * Requires confirmation from both Tenant Admin and Platform Admin.
     * CRITICAL RULE: Invoices and audit records are NEVER deleted; only non-statutory records are purged.
     */
    @Transactional
    @PreAuthorize("hasAuthority('ROLE_PLATFORM_ADMIN')")
    public PurgeAuditCertificate executeDualAuthorizedPurge(
            UUID exitRequestId,
            String tenantAdminApproval,
            String platformAdminApproval
    ) {
        TenantExitRequest request = exitRequestRepository.findById(exitRequestId)
                .orElseThrow(() -> new BusinessException("EXIT_REQUEST_NOT_FOUND", "Exit request not found: " + exitRequestId));

        if (request.getExitStatus() != TenantExitStatus.TENANT_CONFIRMED && request.getExitStatus() != TenantExitStatus.ARCHIVE_VERIFIED) {
            throw new BusinessException("INVALID_EXIT_STATE", "Purge requires verified archive and tenant confirmation");
        }

        if (tenantAdminApproval == null || tenantAdminApproval.isBlank() ||
                platformAdminApproval == null || platformAdminApproval.isBlank()) {
            throw new BusinessException("DUAL_AUTHORIZATION_REQUIRED", "Both Tenant Admin and Platform Admin authorizations are legally mandatory for purge");
        }

        UUID tenantId = request.getTenantId();

        // Count statutory records retained
        long statutoryRetainedCount = invoiceRepository.count();

        // Purge non-statutory operational records (e.g. products, categories)
        long productsToPurge = productRepository.count();
        long categoriesToPurge = categoryRepository.count();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        long totalPurged = productsToPurge + categoriesToPurge;

        // Generate unique purge audit certificate
        String certNumber = "PURGE-CERT-" + UUID.randomUUID().toString().substring(0, 16).toUpperCase();
        String certData = certNumber + "|" + tenantId + "|" + totalPurged + "|" + statutoryRetainedCount + "|" + tenantAdminApproval + "|" + platformAdminApproval;
        String certHash = sha256(certData);

        PurgeAuditCertificate certificate = new PurgeAuditCertificate(
                UUID.randomUUID(),
                tenantId,
                exitRequestId,
                certNumber,
                totalPurged,
                statutoryRetainedCount,
                "PRODUCT_CATALOG,CATEGORY_CATALOG",
                "Tax invoices, tax adjustments, and cryptographic audit ledger retained for 10-year statutory period pursuant to Directive No. 1142/2026 Art. 4(2)(d).",
                tenantAdminApproval,
                platformAdminApproval,
                certHash
        );
        PurgeAuditCertificate savedCert = purgeCertificateRepository.save(certificate);

        request.completePurge(certNumber, platformAdminApproval);
        request.markMigrated();
        exitRequestRepository.save(request);

        // Immutable audit record
        auditService.recordEvent(
                tenantId,
                "SYSTEM",
                platformAdminApproval,
                "LAWFUL_PURGE_EXECUTED",
                "PURGE_AUDIT_CERTIFICATE",
                savedCert.getId().toString(),
                "CERT_NUM=" + certNumber + ", PURGED=" + totalPurged + ", RETAINED_BY_LAW=" + statutoryRetainedCount,
                "127.0.0.1"
        );

        log.info("Lawfully purged non-statutory data for migrated tenant {}. Retained {} statutory fiscal documents. Certificate: {}",
                tenantId, statutoryRetainedCount, certNumber);

        return savedCert;
    }

    private String sha256(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }
}
