package et.ut.einvoice.compliance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import et.ut.einvoice.audit.service.AuditService;
import et.ut.einvoice.compliance.domain.AuthorityInvestigationExport;
import et.ut.einvoice.compliance.dto.AuthorityCustomerResponseDto;
import et.ut.einvoice.compliance.dto.AuthorityExportJobDto;
import et.ut.einvoice.compliance.dto.AuthorityExportRequestDto;
import et.ut.einvoice.compliance.repository.AuthorityInvestigationExportRepository;
import et.ut.einvoice.customer.domain.Customer;
import et.ut.einvoice.customer.repository.CustomerRepository;
import et.ut.einvoice.invoicing.domain.Invoice;
import et.ut.einvoice.invoicing.dto.InvoiceResponseDto;
import et.ut.einvoice.invoicing.repository.InvoiceRepository;
import et.ut.einvoice.platform.config.service.SecretEncryptionService;
import et.ut.einvoice.platform.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service orchestrating tax authority on-demand investigation queries and encrypted exports
 * pursuant to FDRE MoR Directive No. 1142/2026 Art. 15(5).
 */
@Service
public class AuthorityInvestigationService {

    private static final Logger log = LoggerFactory.getLogger(AuthorityInvestigationService.class);
    private static final UUID PLATFORM_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final CustomerRepository customerRepository;
    private final InvoiceRepository invoiceRepository;
    private final AuthorityInvestigationExportRepository exportRepository;
    private final SecretEncryptionService encryptionService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public AuthorityInvestigationService(
            CustomerRepository customerRepository,
            InvoiceRepository invoiceRepository,
            AuthorityInvestigationExportRepository exportRepository,
            SecretEncryptionService encryptionService,
            AuditService auditService,
            ObjectMapper objectMapper
    ) {
        this.customerRepository = customerRepository;
        this.invoiceRepository = invoiceRepository;
        this.exportRepository = exportRepository;
        this.encryptionService = encryptionService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Page<AuthorityCustomerResponseDto> searchCustomers(
            UUID tenantId,
            String tin,
            String legalName,
            String caseReference,
            String reason,
            String auditorId,
            Pageable pageable
    ) {
        if (caseReference == null || caseReference.isBlank() || reason == null || reason.isBlank()) {
            throw new BusinessException("MANDATORY_CASE_REFERENCE_REQUIRED",
                    "Statutory case reference and investigation reason are required for customer inspection under Art. 15(5).");
        }

        Page<Customer> page;
        if (tenantId != null) {
            if (tin != null && !tin.isBlank()) {
                Optional<Customer> cust = customerRepository.findByTenantIdAndTin(tenantId, tin.trim());
                List<Customer> list = cust.map(List::of).orElse(Collections.emptyList());
                page = new PageImpl<>(list, pageable, list.size());
            } else if (legalName != null && !legalName.isBlank()) {
                page = customerRepository.searchCustomers(tenantId, legalName.trim(), pageable);
            } else {
                page = customerRepository.findByTenantId(tenantId, pageable);
            }
        } else {
            page = customerRepository.findAll(pageable);
        }

        recordAudit(
                auditorId != null ? auditorId : "GOV_AUDITOR",
                "AUTHORITY_CUSTOMER_INSPECTION",
                "CUSTOMER",
                tenantId != null ? tenantId.toString() : "ALL_TENANTS",
                Map.of(
                        "caseReference", caseReference,
                        "reason", reason,
                        "tenantId", String.valueOf(tenantId),
                        "tin", String.valueOf(tin),
                        "resultCount", page.getNumberOfElements()
                )
        );

        return page.map(AuthorityCustomerResponseDto::fromEntity);
    }

    @Transactional
    public AuthorityExportJobDto createExportJob(AuthorityExportRequestDto request, String auditorId) {
        if (request.caseReference() == null || request.caseReference().isBlank() ||
            request.reason() == null || request.reason().isBlank()) {
            throw new BusinessException("MANDATORY_CASE_REFERENCE_REQUIRED",
                    "Statutory case reference and investigation reason are required under Art. 15(5).");
        }

        final String actor = auditorId != null ? auditorId : "GOV_AUDITOR";

        AuthorityInvestigationExport export = new AuthorityInvestigationExport(
                UUID.randomUUID(),
                request.caseReference(),
                request.reason(),
                actor,
                request.tenantId(),
                request.customerTin(),
                request.dateFrom(),
                request.dateTo(),
                request.invoiceRangeStart(),
                request.invoiceRangeEnd(),
                request.transactionType()
        );
        export.setStatus("PROCESSING");
        export = exportRepository.save(export);

        try {
            // Collect matching invoices
            List<Invoice> matchingInvoices;
            if (request.dateFrom() != null && request.dateTo() != null) {
                matchingInvoices = invoiceRepository.findAllByInvoiceDateBetween(
                        request.dateFrom(), request.dateTo(), Pageable.unpaged()
                ).getContent();
            } else if (request.tenantId() != null) {
                matchingInvoices = invoiceRepository.findAllByTenantId(request.tenantId(), Pageable.unpaged()).getContent();
            } else {
                matchingInvoices = invoiceRepository.findAll();
            }

            // Filter by customer TIN if requested
            if (request.customerTin() != null && !request.customerTin().isBlank()) {
                matchingInvoices = matchingInvoices.stream()
                        .filter(i -> request.customerTin().equalsIgnoreCase(i.getBuyerTin()))
                        .collect(Collectors.toList());
            }

            List<InvoiceResponseDto> dtoList = matchingInvoices.stream()
                    .map(InvoiceResponseDto::fromEntity)
                    .collect(Collectors.toList());

            String exportJson = objectMapper.writeValueAsString(Map.of(
                    "caseReference", request.caseReference(),
                    "reason", request.reason(),
                    "exportedAt", java.time.Instant.now(),
                    "recordCount", dtoList.size(),
                    "records", dtoList
            ));

            String sha256Hex = computeSha256(exportJson);
            String encryptedPayload = encryptionService.encryptSecret(exportJson);

            export.markCompleted(dtoList.size(), encryptedPayload, sha256Hex);
            AuthorityInvestigationExport saved = exportRepository.save(export);

            recordAudit(
                    actor,
                    "AUTHORITY_INVESTIGATION_EXPORT_CREATED",
                    "INVESTIGATION_EXPORT",
                    saved.getId().toString(),
                    Map.of(
                            "exportId", saved.getId().toString(),
                            "caseReference", saved.getCaseReference(),
                            "recordCount", saved.getRecordCount(),
                            "sha256Checksum", saved.getSha256Checksum()
                    )
            );

            log.info("Authority investigation export {} completed with {} records for case {}",
                    saved.getId(), saved.getRecordCount(), saved.getCaseReference());

            return AuthorityExportJobDto.fromEntity(saved);

        } catch (Exception e) {
            log.error("Failed to generate authority investigation export {}: {}", export.getId(), e.getMessage(), e);
            export.markFailed();
            exportRepository.save(export);
            throw new BusinessException("EXPORT_PROCESSING_FAILED", "Failed to generate encrypted investigation export: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public AuthorityExportJobDto getExportJob(UUID id, String auditorId) {
        AuthorityInvestigationExport job = exportRepository.findById(id)
                .orElseThrow(() -> new BusinessException("EXPORT_JOB_NOT_FOUND", "Investigation export job not found: " + id));

        recordAudit(
                auditorId != null ? auditorId : "GOV_AUDITOR",
                "AUTHORITY_EXPORT_ACCESSED",
                "INVESTIGATION_EXPORT",
                job.getId().toString(),
                Map.of("caseReference", job.getCaseReference(), "recordCount", job.getRecordCount())
        );

        return AuthorityExportJobDto.fromEntity(job);
    }

    @Transactional(readOnly = true)
    public Page<AuthorityExportJobDto> listExportJobs(Pageable pageable) {
        return exportRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(AuthorityExportJobDto::fromEntity);
    }

    private String computeSha256(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 calculation failed", e);
        }
    }

    private void recordAudit(String actor, String action, String resourceType, String resourceId, Map<String, Object> details) {
        try {
            String payload = objectMapper.writeValueAsString(details);
            auditService.recordEvent(
                    PLATFORM_TENANT_ID,
                    actor,
                    action,
                    resourceType,
                    resourceId,
                    payload
            );
        } catch (Exception e) {
            log.warn("Failed to serialize audit event for authority inspection: {}", e.getMessage());
        }
    }
}
