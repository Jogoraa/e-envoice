package et.ut.einvoice.portability.service;

import et.ut.einvoice.audit.service.AuditService;
import et.ut.einvoice.compliance.service.InsaDigitalSignatureService;
import et.ut.einvoice.invoicing.repository.InvoiceRepository;
import et.ut.einvoice.portability.domain.ExportJob;
import et.ut.einvoice.portability.repository.ExportJobRepository;
import et.ut.einvoice.taxpayer.repository.TaxpayerProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class DataPortabilityService {

    private static final Logger log = LoggerFactory.getLogger(DataPortabilityService.class);

    private final ExportJobRepository exportJobRepository;
    private final InvoiceRepository invoiceRepository;
    private final TaxpayerProfileRepository taxpayerProfileRepository;
    private final InsaDigitalSignatureService signatureService;
    private final AuditService auditService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public DataPortabilityService(
            ExportJobRepository exportJobRepository,
            InvoiceRepository invoiceRepository,
            TaxpayerProfileRepository taxpayerProfileRepository,
            InsaDigitalSignatureService signatureService,
            AuditService auditService,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper
    ) {
        this.exportJobRepository = exportJobRepository;
        this.invoiceRepository = invoiceRepository;
        this.taxpayerProfileRepository = taxpayerProfileRepository;
        this.signatureService = signatureService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ExportJob initiateExport(UUID tenantId) {
        ExportJob job = new ExportJob(UUID.randomUUID(), tenantId);
        ExportJob saved = exportJobRepository.save(job);

        auditService.recordEvent(
                tenantId,
                "PORTABILITY",
                "USER",
                "INITIATE_DATA_EXPORT",
                "EXPORT_JOB",
                saved.getId().toString(),
                "STATUS=REQUESTED",
                "127.0.0.1"
        );

        // Process export
        processExportAsync(saved.getId(), tenantId);
        return saved;
    }

    @Async
    public void processExportAsync(UUID jobId, UUID tenantId) {
        try {
            log.info("Generating legally exportable data archive for tenant {}...", tenantId);
            var invoices = invoiceRepository.findAllByTenantId(tenantId, org.springframework.data.domain.PageRequest.of(0, 500)).getContent();
            var profile = taxpayerProfileRepository.findById(tenantId).orElse(null);

            var archiveMap = new java.util.LinkedHashMap<String, Object>();
            archiveMap.put("tenantId", tenantId);
            archiveMap.put("exportedAt", Instant.now().toString());
            archiveMap.put("complianceDirective", "1142/2026");
            archiveMap.put("taxpayerProfile", profile);
            archiveMap.put("invoicesCount", invoices.size());
            archiveMap.put("invoices", invoices);

            String bundleJson = objectMapper.writeValueAsString(archiveMap);
            String checksum = signatureService.computeSha256Hash(bundleJson);
            String artifactUrl = "/storage/exports/" + tenantId + "/export-" + jobId + ".json";

            exportJobRepository.findById(jobId).ifPresent(job -> {
                job.complete(artifactUrl, checksum);
                exportJobRepository.save(job);
                log.info("Tenant {} data export completed successfully: artifact={}, checksum={}", tenantId, artifactUrl, checksum);
            });
        } catch (Exception ex) {
            log.error("Failed to generate data export bundle for tenant {}", tenantId, ex);
            exportJobRepository.findById(jobId).ifPresent(job -> {
                job.fail();
                exportJobRepository.save(job);
            });
        }
    }
}
