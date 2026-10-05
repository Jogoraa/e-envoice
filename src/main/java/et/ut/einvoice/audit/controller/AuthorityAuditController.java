package et.ut.einvoice.audit.controller;

import et.ut.einvoice.audit.domain.AuditEvent;
import et.ut.einvoice.audit.dto.AuthorityAuditEventDto;
import et.ut.einvoice.audit.repository.AuditEventRepository;
import et.ut.einvoice.invoicing.domain.Invoice;
import et.ut.einvoice.invoicing.dto.InvoiceListItemDto;
import et.ut.einvoice.invoicing.dto.InvoiceResponseDto;
import et.ut.einvoice.invoicing.repository.InvoiceRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/authority")
@Tag(name = "Authority Audit Interface", description = "On-Demand Inspection Interface for Ministry of Revenues Auditors (Directive No. 1142/2026 Art. 4(2)(c))")
@SecurityRequirement(name = "AuthorityAuth")
public class AuthorityAuditController {

    private final AuditEventRepository auditRepository;
    private final InvoiceRepository invoiceRepository;
    private final et.ut.einvoice.compliance.service.SoftwareIntegrityService softwareIntegrityService;
    private final et.ut.einvoice.compliance.service.AuthorityInvestigationService investigationService;

    public AuthorityAuditController(AuditEventRepository auditRepository,
                                    InvoiceRepository invoiceRepository,
                                    et.ut.einvoice.compliance.service.SoftwareIntegrityService softwareIntegrityService,
                                    et.ut.einvoice.compliance.service.AuthorityInvestigationService investigationService) {
        this.auditRepository = auditRepository;
        this.invoiceRepository = invoiceRepository;
        this.softwareIntegrityService = softwareIntegrityService;
        this.investigationService = investigationService;
    }

    @GetMapping("/system-checksum")
    @PreAuthorize("hasAnyAuthority('ROLE_AUTHORITY_AUDITOR', 'ROLE_PLATFORM_ADMIN')")
    @Operation(summary = "Get Software Build Checksum and Version Integrity Evidence (Directive No. 1142/2026 Art. 10(9), 11(2), 12(3))")
    public ResponseEntity<et.ut.einvoice.compliance.dto.SystemChecksumResponseDto> getSystemChecksum() {
        return ResponseEntity.ok(softwareIntegrityService.getSystemChecksum());
    }

    @GetMapping("/audit-logs")
    @PreAuthorize("hasAuthority('ROLE_AUTHORITY_AUDITOR')")
    @Operation(summary = "Query Operation Audit Logs (Auditors Only)")
    public ResponseEntity<Page<AuthorityAuditEventDto>> getAuditLogs(
            @RequestParam(required = false) UUID tenantId,
            @PageableDefault(size = 50) Pageable pageable
    ) {
        Page<AuditEvent> logs = (tenantId != null)
                ? auditRepository.findAllByTenantId(tenantId, pageable)
                : auditRepository.findAll(pageable);
        return ResponseEntity.ok(logs.map(AuthorityAuditEventDto::fromEntity));
    }

    @GetMapping("/invoices")
    @PreAuthorize("hasAuthority('ROLE_AUTHORITY_AUDITOR')")
    @Operation(summary = "Query Invoices Across Taxpayers (Auditors Only)")
    public ResponseEntity<Page<InvoiceListItemDto>> getInvoicesByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 50) Pageable pageable
    ) {
        Page<Invoice> invoices = invoiceRepository.findAllByInvoiceDateBetween(from, to, pageable);
        return ResponseEntity.ok(invoices.map(InvoiceResponseDto::fromEntity).map(InvoiceListItemDto::fromInvoiceResponse));
    }

    @GetMapping("/customers")
    @PreAuthorize("hasAnyAuthority('ROLE_AUTHORITY_AUDITOR', 'ROLE_PLATFORM_ADMIN')")
    @Operation(summary = "Controlled Tax Authority Customer Inspection (Directive No. 1142/2026 Art. 15(5))")
    public ResponseEntity<Page<et.ut.einvoice.compliance.dto.AuthorityCustomerResponseDto>> getCustomers(
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) String tin,
            @RequestParam(required = false) String legalName,
            @RequestParam String caseReference,
            @RequestParam String reason,
            org.springframework.security.core.Authentication authentication,
            @PageableDefault(size = 50) Pageable pageable
    ) {
        String auditorId = authentication != null ? authentication.getName() : "GOV_AUDITOR";
        return ResponseEntity.ok(investigationService.searchCustomers(tenantId, tin, legalName, caseReference, reason, auditorId, pageable));
    }

    @PostMapping("/investigations/exports")
    @PreAuthorize("hasAnyAuthority('ROLE_AUTHORITY_AUDITOR', 'ROLE_PLATFORM_ADMIN')")
    @Operation(summary = "Initiate Encrypted Tax Authority Investigation Export Job (Directive No. 1142/2026 Art. 15(5))")
    public ResponseEntity<et.ut.einvoice.compliance.dto.AuthorityExportJobDto> createExportJob(
            @jakarta.validation.Valid @RequestBody et.ut.einvoice.compliance.dto.AuthorityExportRequestDto request,
            org.springframework.security.core.Authentication authentication
    ) {
        String auditorId = authentication != null ? authentication.getName() : "GOV_AUDITOR";
        return ResponseEntity.ok(investigationService.createExportJob(request, auditorId));
    }

    @GetMapping("/investigations/exports/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_AUTHORITY_AUDITOR', 'ROLE_PLATFORM_ADMIN')")
    @Operation(summary = "Retrieve Encrypted Investigation Export Job Artifact (Directive No. 1142/2026 Art. 15(5))")
    public ResponseEntity<et.ut.einvoice.compliance.dto.AuthorityExportJobDto> getExportJob(
            @PathVariable UUID id,
            org.springframework.security.core.Authentication authentication
    ) {
        String auditorId = authentication != null ? authentication.getName() : "GOV_AUDITOR";
        return ResponseEntity.ok(investigationService.getExportJob(id, auditorId));
    }

    @GetMapping("/investigations/exports")
    @PreAuthorize("hasAnyAuthority('ROLE_AUTHORITY_AUDITOR', 'ROLE_PLATFORM_ADMIN')")
    @Operation(summary = "List Authority Investigation Export Jobs (Directive No. 1142/2026 Art. 15(5))")
    public ResponseEntity<Page<et.ut.einvoice.compliance.dto.AuthorityExportJobDto>> listExportJobs(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(investigationService.listExportJobs(pageable));
    }
}
