package et.ut.einvoice.invoicing.controller;

import et.ut.einvoice.documents.service.PdfExportService;
import et.ut.einvoice.documents.service.ReceiptRenderingService;
import et.ut.einvoice.government.domain.MorReceiptViewModel;
import et.ut.einvoice.government.service.MorInvoiceCanonicalizationService;
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
import org.springframework.http.*;
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
    private final PdfExportService pdfExportService;
    private final MorInvoiceCanonicalizationService canonicalizationService;

    public InvoiceController(
            InvoiceService invoiceService,
            InvoiceRepository invoiceRepository,
            TaxpayerProfileRepository taxpayerProfileRepository,
            ReceiptRenderingService receiptRenderingService,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            PdfExportService pdfExportService,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            MorInvoiceCanonicalizationService canonicalizationService
    ) {
        this.invoiceService = invoiceService;
        this.invoiceRepository = invoiceRepository;
        this.taxpayerProfileRepository = taxpayerProfileRepository;
        this.receiptRenderingService = receiptRenderingService;
        this.pdfExportService = pdfExportService;
        this.canonicalizationService = canonicalizationService;
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

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('SCOPE_invoice:read') or hasRole('TENANT_ADMIN') or hasRole('CASHIER')")
    @Operation(summary = "Get Authoritative Invoice Summary and KPIs for Current Tenant")
    public ResponseEntity<et.ut.einvoice.invoicing.dto.InvoiceSummaryDto> getInvoiceSummary() {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        return ResponseEntity.ok(invoiceService.getTenantInvoiceSummary(tenantId));
    }

    @GetMapping(value = "/{id}/receipt", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('SCOPE_invoice:read') or hasRole('TENANT_ADMIN') or hasRole('CASHIER')")
    @Operation(summary = "Get Canonical MoR Receipt View Model")
    public ResponseEntity<MorReceiptViewModel> getReceiptViewModel(@PathVariable UUID id) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        Invoice invoice = invoiceRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new BusinessException("INVOICE_NOT_FOUND", "Invoice not found", HttpStatus.NOT_FOUND));
        TaxpayerProfile seller = taxpayerProfileRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("TAXPAYER_PROFILE_NOT_FOUND", "Seller profile missing", HttpStatus.INTERNAL_SERVER_ERROR));

        String qrImage = invoice.getSignedQr();
        String qrJson = null;
        if (canonicalizationService != null) {
            qrJson = canonicalizationService.buildCanonicalQrData(invoice, seller, invoice.getSignedInvoice(), invoice.getAckDate());
        }

        MorReceiptViewModel vm = canonicalizationService != null
                ? canonicalizationService.buildReceiptViewModel(invoice, seller, qrImage, qrJson)
                : null;

        return ResponseEntity.ok(vm);
    }

    @GetMapping(value = "/{id}/document", produces = {MediaType.TEXT_HTML_VALUE, MediaType.ALL_VALUE})
    @PreAuthorize("hasAuthority('SCOPE_invoice:read') or hasRole('TENANT_ADMIN') or hasRole('CASHIER')")
    @Operation(summary = "Download Rendered Invoice Document (HTML/Printable)")
    public ResponseEntity<String> getInvoiceDocument(@PathVariable UUID id) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        Invoice invoice = invoiceRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new BusinessException("INVOICE_NOT_FOUND", "Invoice not found", HttpStatus.NOT_FOUND));
        TaxpayerProfile seller = taxpayerProfileRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("TAXPAYER_PROFILE_NOT_FOUND", "Seller profile missing", HttpStatus.INTERNAL_SERVER_ERROR));

        String html = receiptRenderingService.renderHtmlTaxInvoice(invoice, seller);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_HTML);
        return new ResponseEntity<>(html, headers, HttpStatus.OK);
    }

    @GetMapping(value = "/{id}/pdf", produces = {MediaType.APPLICATION_PDF_VALUE, MediaType.APPLICATION_OCTET_STREAM_VALUE, MediaType.ALL_VALUE})
    @PreAuthorize("hasAuthority('SCOPE_invoice:read') or hasRole('TENANT_ADMIN') or hasRole('CASHIER')")
    @Operation(summary = "Download Official Rendered MoR Tax Invoice (PDF)")
    public ResponseEntity<byte[]> getInvoicePdf(@PathVariable UUID id) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        Invoice invoice = invoiceRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new BusinessException("INVOICE_NOT_FOUND", "Invoice not found", HttpStatus.NOT_FOUND));
        TaxpayerProfile seller = taxpayerProfileRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("TAXPAYER_PROFILE_NOT_FOUND", "Seller profile missing", HttpStatus.INTERNAL_SERVER_ERROR));

        if (pdfExportService == null || !pdfExportService.isPdfGenerationAvailable()) {
            throw new BusinessException(
                    "PDF_EXPORT_UNAVAILABLE",
                    "Headless PDF generator is not available on this server environment.",
                    "የፒዲኤፍ (PDF) ማመንጫ ስርዓት በዚህ አገልጋይ ላይ ለጊዜው አልተገኘም።",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }

        String html = receiptRenderingService.renderHtmlTaxInvoice(invoice, seller);
        byte[] pdfBytes = pdfExportService.exportHtmlToPdf(html);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.inline()
                .filename("Tax_Invoice_" + invoice.getDocumentNumber() + ".pdf")
                .build());
        headers.setContentLength(pdfBytes.length);
        headers.setCacheControl(CacheControl.noStore());

        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }

    @PostMapping(value = "/{id}/reprint", produces = {MediaType.TEXT_HTML_VALUE, MediaType.ALL_VALUE})
    @PreAuthorize("hasAuthority('SCOPE_invoice:create') or hasRole('TENANT_ADMIN') or hasRole('CASHIER')")
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
