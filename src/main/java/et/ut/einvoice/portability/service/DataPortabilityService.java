package et.ut.einvoice.portability.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import et.ut.einvoice.adjustments.repository.TaxAdjustmentRepository;
import et.ut.einvoice.audit.repository.AuditEventRepository;
import et.ut.einvoice.audit.service.AuditService;
import et.ut.einvoice.cancellation.repository.CancellationRequestRepository;
import et.ut.einvoice.catalog.repository.CategoryRepository;
import et.ut.einvoice.catalog.repository.ProductRepository;
import et.ut.einvoice.catalog.repository.ServiceItemRepository;
import et.ut.einvoice.customer.repository.CustomerRepository;
import et.ut.einvoice.invoicing.repository.InvoiceRepository;
import et.ut.einvoice.portability.domain.ExportJob;
import et.ut.einvoice.portability.repository.ExportJobRepository;
import et.ut.einvoice.taxpayer.repository.TaxpayerProfileRepository;
import et.ut.einvoice.tenancy.repository.TenantUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class DataPortabilityService {

    private static final Logger log = LoggerFactory.getLogger(DataPortabilityService.class);

    private final ExportJobRepository exportJobRepository;
    private final InvoiceRepository invoiceRepository;
    private final TaxpayerProfileRepository taxpayerProfileRepository;
    private final CustomerRepository customerRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ServiceItemRepository serviceItemRepository;
    private final TaxAdjustmentRepository taxAdjustmentRepository;
    private final CancellationRequestRepository cancellationRequestRepository;
    private final AuditEventRepository auditEventRepository;
    private final TenantUserRepository tenantUserRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public DataPortabilityService(
            ExportJobRepository exportJobRepository,
            InvoiceRepository invoiceRepository,
            TaxpayerProfileRepository taxpayerProfileRepository,
            CustomerRepository customerRepository,
            CategoryRepository categoryRepository,
            ProductRepository productRepository,
            ServiceItemRepository serviceItemRepository,
            TaxAdjustmentRepository taxAdjustmentRepository,
            CancellationRequestRepository cancellationRequestRepository,
            AuditEventRepository auditEventRepository,
            TenantUserRepository tenantUserRepository,
            AuditService auditService,
            ObjectMapper objectMapper
    ) {
        this.exportJobRepository = exportJobRepository;
        this.invoiceRepository = invoiceRepository;
        this.taxpayerProfileRepository = taxpayerProfileRepository;
        this.customerRepository = customerRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.serviceItemRepository = serviceItemRepository;
        this.taxAdjustmentRepository = taxAdjustmentRepository;
        this.cancellationRequestRepository = cancellationRequestRepository;
        this.auditEventRepository = auditEventRepository;
        this.tenantUserRepository = tenantUserRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    private String calculateChecksum(byte[] data) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    @Transactional
    public ExportJob initiateExport(UUID tenantId) {
        ExportJob job = new ExportJob(UUID.randomUUID(), tenantId);
        ExportJob saved = exportJobRepository.save(job);

        auditService.recordEvent(
                tenantId,
                "PORTABILITY_USER",
                "INITIATE_DATA_EXPORT",
                "EXPORT_JOB",
                saved.getId().toString(),
                "STATUS=REQUESTED"
        );

        // Process export asynchronously
        processExportAsync(saved.getId(), tenantId);
        return saved;
    }

    @Async
    public void processExportAsync(UUID jobId, UUID tenantId) {
        try {
            log.info("Generating comprehensive legally exportable ZIP archive for tenant {}...", tenantId);

            // Fetch all tenant-scoped business entities
            var profile = taxpayerProfileRepository.findById(tenantId).orElse(null);
            var users = tenantUserRepository.findByTenantId(tenantId).stream().map(u -> Map.of(
                    "id", u.getId(),
                    "username", u.getUsername(),
                    "email", u.getEmail() != null ? u.getEmail() : "",
                    "fullName", u.getFullName() != null ? u.getFullName() : "",
                    "role", u.getRole(),
                    "status", u.getStatus()
            )).toList();
            var customers = customerRepository.findByTenantId(tenantId, PageRequest.of(0, 5000)).getContent();
            var categories = categoryRepository.findByTenantIdOrderByCodeAsc(tenantId);
            var products = productRepository.findByTenantIdOrderByItemCodeAsc(tenantId);
            var services = serviceItemRepository.findByTenantIdOrderByServiceCodeAsc(tenantId);
            var invoices = invoiceRepository.findAllByTenantId(tenantId, PageRequest.of(0, 5000)).getContent();
            var adjustments = taxAdjustmentRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
            var cancellations = cancellationRequestRepository.findByTenantIdOrderByRequestedAtDesc(tenantId);
            var auditEvents = auditEventRepository.findByTenantIdOrderBySequenceNumberAsc(tenantId);

            Path exportDir = Paths.get("exports", tenantId.toString());
            Files.createDirectories(exportDir);
            Path zipFilePath = exportDir.resolve("export-" + jobId + ".zip");

            Map<String, String> fileChecksums = new LinkedHashMap<>();
            Map<String, Integer> recordCounts = new LinkedHashMap<>();
            recordCounts.put("users", users.size());
            recordCounts.put("customers", customers.size());
            recordCounts.put("categories", categories.size());
            recordCounts.put("products", products.size());
            recordCounts.put("services", services.size());
            recordCounts.put("invoices", invoices.size());
            recordCounts.put("adjustments", adjustments.size());
            recordCounts.put("cancellations", cancellations.size());
            recordCounts.put("auditEvents", auditEvents.size());

            try (FileOutputStream fos = new FileOutputStream(zipFilePath.toFile());
                 ZipOutputStream zos = new ZipOutputStream(fos)) {

                addJsonEntry(zos, "tenant/profile.json", profile, fileChecksums);
                addJsonEntry(zos, "tenant/users.json", users, fileChecksums);
                addJsonEntry(zos, "customers/customers.json", customers, fileChecksums);
                addJsonEntry(zos, "catalog/categories.json", categories, fileChecksums);
                addJsonEntry(zos, "catalog/products.json", products, fileChecksums);
                addJsonEntry(zos, "catalog/services.json", services, fileChecksums);
                addJsonEntry(zos, "invoices/invoices.json", invoices, fileChecksums);
                addJsonEntry(zos, "adjustments/adjustments.json", adjustments, fileChecksums);
                addJsonEntry(zos, "cancellations/cancellations.json", cancellations, fileChecksums);
                addJsonEntry(zos, "audit/audit_events.json", auditEvents, fileChecksums);

                // Add documents directory placeholder / sample thermal receipt preview
                String sampleReceipt = """
                ========================================
                       FDRE MINISTRY OF REVENUES
                   ELECTRONIC INVOICE ARCHIVE RECEIPT
                ========================================
                Taxpayer TIN: %s
                Archive Job ID: %s
                Export Timestamp: %s
                Directive Compliance: Directive No. 1142/2026
                ========================================
                """.formatted(profile != null ? profile.getTin() : "N/A", jobId, Instant.now());
                byte[] docBytes = sampleReceipt.getBytes(StandardCharsets.UTF_8);
                zos.putNextEntry(new ZipEntry("documents/receipt_archive_summary.txt"));
                zos.write(docBytes);
                zos.closeEntry();
                fileChecksums.put("documents/receipt_archive_summary.txt", calculateChecksum(docBytes));

                // Add Manifest JSON
                Map<String, Object> manifest = new LinkedHashMap<>();
                manifest.put("exportJobId", jobId);
                manifest.put("tenantId", tenantId);
                manifest.put("sellerTin", profile != null ? profile.getTin() : "UNKNOWN");
                manifest.put("generatedAt", Instant.now().toString());
                manifest.put("directive", "FDRE MoR Directive No. 1142/2026 Art. 5(3)");
                manifest.put("recordCounts", recordCounts);
                manifest.put("files", fileChecksums);

                byte[] manifestBytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest);
                zos.putNextEntry(new ZipEntry("manifest.json"));
                zos.write(manifestBytes);
                zos.closeEntry();

                zos.finish();
            }

            // Calculate overall ZIP file checksum
            byte[] zipBytes = Files.readAllBytes(zipFilePath);
            String zipChecksum = calculateChecksum(zipBytes);
            String downloadUrl = "/api/v1/portability/exports/" + jobId + "/download";

            exportJobRepository.findById(jobId).ifPresent(job -> {
                job.complete(downloadUrl, zipChecksum);
                exportJobRepository.save(job);
                log.info("Tenant {} data export complete! Path={}, Checksum={}", tenantId, zipFilePath, zipChecksum);
            });

        } catch (Exception ex) {
            log.error("Failed to generate comprehensive data export bundle for tenant {}", tenantId, ex);
            exportJobRepository.findById(jobId).ifPresent(job -> {
                job.fail();
                exportJobRepository.save(job);
            });
        }
    }

    private void addJsonEntry(ZipOutputStream zos, String entryPath, Object data, Map<String, String> checksums) throws IOException {
        byte[] bytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(data);
        zos.putNextEntry(new ZipEntry(entryPath));
        zos.write(bytes);
        zos.closeEntry();
        checksums.put(entryPath, calculateChecksum(bytes));
    }

    @Transactional(readOnly = true)
    public List<ExportJob> listExports(UUID tenantId) {
        return exportJobRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Transactional(readOnly = true)
    public File getExportArchiveFile(UUID tenantId, UUID jobId) {
        ExportJob job = exportJobRepository.findById(jobId)
                .orElseThrow(() -> new NoSuchElementException("Export job not found"));

        if (!job.getTenantId().equals(tenantId)) {
            throw new SecurityException("Access denied to requested tenant export archive");
        }

        Path zipFilePath = Paths.get("exports", tenantId.toString(), "export-" + jobId + ".zip");
        File file = zipFilePath.toFile();
        if (!file.exists()) {
            throw new NoSuchElementException("Export bundle file not found on disk");
        }
        return file;
    }
}
