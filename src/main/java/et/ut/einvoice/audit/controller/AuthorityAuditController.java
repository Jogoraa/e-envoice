package et.ut.einvoice.audit.controller;

import et.ut.einvoice.audit.domain.AuditEvent;
import et.ut.einvoice.audit.repository.AuditEventRepository;
import et.ut.einvoice.invoicing.domain.Invoice;
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

    public AuthorityAuditController(AuditEventRepository auditRepository, InvoiceRepository invoiceRepository) {
        this.auditRepository = auditRepository;
        this.invoiceRepository = invoiceRepository;
    }

    @GetMapping("/audit-logs")
    @PreAuthorize("hasAuthority('ROLE_AUTHORITY_AUDITOR')")
    @Operation(summary = "Query Operation Audit Logs (Auditors Only)")
    public ResponseEntity<Page<AuditEvent>> getAuditLogs(
            @RequestParam(required = false) UUID tenantId,
            @PageableDefault(size = 50) Pageable pageable
    ) {
        Page<AuditEvent> logs = (tenantId != null)
                ? auditRepository.findAllByTenantId(tenantId, pageable)
                : auditRepository.findAll(pageable);
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/invoices")
    @PreAuthorize("hasAuthority('ROLE_AUTHORITY_AUDITOR')")
    @Operation(summary = "Query Invoices Across Taxpayers (Auditors Only)")
    public ResponseEntity<Page<InvoiceResponseDto>> getInvoicesByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 50) Pageable pageable
    ) {
        Page<Invoice> invoices = invoiceRepository.findAllByInvoiceDateBetween(from, to, pageable);
        return ResponseEntity.ok(invoices.map(InvoiceResponseDto::fromEntity));
    }
}
