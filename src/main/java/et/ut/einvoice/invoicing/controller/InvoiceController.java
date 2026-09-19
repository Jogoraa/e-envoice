package et.ut.einvoice.invoicing.controller;

import et.ut.einvoice.documents.service.ReceiptRenderingService;
import et.ut.einvoice.invoicing.domain.Invoice;
import et.ut.einvoice.invoicing.domain.InvoiceStatus;
import et.ut.einvoice.invoicing.dto.CreateInvoiceRequest;
import et.ut.einvoice.invoicing.dto.InvoiceResponseDto;
import et.ut.einvoice.invoicing.repository.InvoiceRepository;
import et.ut.einvoice.invoicing.service.InvoiceService;
import et.ut.einvoice.platform.context.TenantContextHolder;
import et.ut.einvoice.platform.exception.BusinessException;
import et.ut.einvoice.taxpayer.domain.TaxpayerProfile;
import et.ut.einvoice.taxpayer.repository.TaxpayerProfileRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invoices")
@Tag(name = "Invoices", description = "Electronic Invoice Registration and Lifecycle Operations")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final InvoiceRepository invoiceRepository;
    private final TaxpayerProfileRepository taxpayerProfileRepository;
    private final ReceiptRenderingService receiptRenderingService;

    public InvoiceController(
            InvoiceService invoiceService,
            InvoiceRepository invoiceRepository,
            TaxpayerProfileRepository taxpayerProfileRepository,
            ReceiptRenderingService receiptRenderingService
    ) {
        this.invoiceService = invoiceService;
        this.invoiceRepository = invoiceRepository;
        this.taxpayerProfileRepository = taxpayerProfileRepository;
        this.receiptRenderingService = receiptRenderingService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_invoice:create') or hasRole('TENANT_ADMIN') or hasRole('CASHIER')")
    @Operation(summary = "Issue and Register Invoice", description = "Registers an invoice in real-time with the Ministry of Revenues.")
    public ResponseEntity<InvoiceResponseDto> createInvoice(
            @Valid @RequestBody CreateInvoiceRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestParam(value = "async", defaultValue = "false") boolean async
    ) {
        InvoiceResponseDto response = invoiceService.createAndRegisterInvoice(request, idempotencyKey);
        if (async || (response.status() != null && "PENDING_REGISTRATION".equals(response.status().name()))) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        }
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_invoice:read') or hasRole('TENANT_ADMIN') or hasRole('CASHIER')")
    @Operation(summary = "Get Invoice by ID")
    public ResponseEntity<InvoiceResponseDto> getInvoice(@PathVariable UUID id) {
        return ResponseEntity.ok(invoiceService.getInvoiceById(id));
    }

    @GetMapping("/by-irn/{irn}")
    @PreAuthorize("hasAuthority('SCOPE_invoice:read') or hasRole('TENANT_ADMIN') or hasRole('CASHIER')")
    @Operation(summary = "Get Invoice by Ministry IRN")
    public ResponseEntity<InvoiceResponseDto> getInvoiceByIrn(@PathVariable String irn) {
        return ResponseEntity.ok(invoiceService.getInvoiceByIrn(irn));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_invoice:read') or hasRole('TENANT_ADMIN') or hasRole('CASHIER')")
    @Operation(summary = "List Invoices with Pagination and Filters")
    public ResponseEntity<Page<InvoiceResponseDto>> listInvoices(
            @RequestParam(required = false) InvoiceStatus status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(invoiceService.listInvoices(status, pageable));
    }

    @GetMapping(value = "/{id}/document", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(summary = "Download Rendered Invoice Document (HTML/Printable)")
    public ResponseEntity<String> getInvoiceDocument(@PathVariable UUID id) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        Invoice invoice = invoiceRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new BusinessException("INVOICE_NOT_FOUND", "Invoice not found", HttpStatus.NOT_FOUND));
        TaxpayerProfile seller = taxpayerProfileRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("TAXPAYER_PROFILE_NOT_FOUND", "Seller profile missing", HttpStatus.INTERNAL_SERVER_ERROR));

        String html = receiptRenderingService.renderHtmlTaxInvoice(invoice, seller);
        return ResponseEntity.ok(html);
    }

    @PostMapping("/{id}/reprint")
    @Operation(summary = "Record Reprint and Generate Duplicate Receipt", description = "Increments reprint counter and watermarks document as DUPLICATE per Art. 22.")
    public ResponseEntity<String> reprintInvoice(@PathVariable UUID id) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        Invoice invoice = invoiceService.recordReprint(id);
        TaxpayerProfile seller = taxpayerProfileRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("TAXPAYER_PROFILE_NOT_FOUND", "Seller profile missing", HttpStatus.INTERNAL_SERVER_ERROR));

        String html = receiptRenderingService.renderHtmlTaxInvoice(invoice, seller);
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Reprint-Count", String.valueOf(invoice.getReprintCount()));
        headers.setContentType(MediaType.TEXT_HTML);
        return new ResponseEntity<>(html, headers, HttpStatus.OK);
    }
}
