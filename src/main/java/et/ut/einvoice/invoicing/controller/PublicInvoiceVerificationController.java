package et.ut.einvoice.invoicing.controller;

import et.ut.einvoice.invoicing.domain.Invoice;
import et.ut.einvoice.invoicing.dto.PublicInvoiceVerificationDto;
import et.ut.einvoice.invoicing.repository.InvoiceRepository;
import et.ut.einvoice.platform.ratelimit.RateLimitingService;
import et.ut.einvoice.taxpayer.domain.TaxpayerProfile;
import et.ut.einvoice.taxpayer.repository.TaxpayerProfileRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * Public Verification Controller providing the statutory invoice verification capability
 * referenced in Directive No. 1142/2018 EC (2026 GC) Art. 20(3)(g) and Art. 4(2)(c).
 * Note: The REST endpoint structure (/api/v1/public/verify/{irn}) is an engineering implementation choice;
 * the underlying legal requirement is the publicly accessible verification capability.
 * Provides open verification of issued electronic invoices via IRN or QR code scan.
 * Enforces IP-based rate limiting to prevent enumeration attacks without leaking buyer PII or platform secrets.
 */
@RestController
@RequestMapping("/api/v1/public")
@Tag(name = "Public Verification", description = "Public Electronic Invoice Verification and QR Validation")
public class PublicInvoiceVerificationController {

    private static final Logger log = LoggerFactory.getLogger(PublicInvoiceVerificationController.class);
    private static final int RATE_LIMIT_PER_MINUTE = 60;

    private final InvoiceRepository invoiceRepository;
    private final TaxpayerProfileRepository taxpayerProfileRepository;
    private final RateLimitingService rateLimitingService;

    public PublicInvoiceVerificationController(
            InvoiceRepository invoiceRepository,
            TaxpayerProfileRepository taxpayerProfileRepository,
            RateLimitingService rateLimitingService
    ) {
        this.invoiceRepository = invoiceRepository;
        this.taxpayerProfileRepository = taxpayerProfileRepository;
        this.rateLimitingService = rateLimitingService;
    }

    @GetMapping("/verify/{irn}")
    @Operation(
            summary = "Verify Electronic Invoice by IRN",
            description = "Publicly verifies the authenticity and status of an electronic invoice registered with the Ministry of Revenues."
    )
    @ApiResponse(responseCode = "200", description = "Invoice found and verification data returned")
    @ApiResponse(responseCode = "404", description = "Invoice with specified IRN not found")
    @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    public ResponseEntity<?> verifyInvoiceByIrn(@PathVariable("irn") String irn, HttpServletRequest request) {
        String clientIp = extractClientIp(request);

        // 1. IP-based rate limiting
        boolean allowed = rateLimitingService.tryAcquireKey("public_verify:" + clientIp, RATE_LIMIT_PER_MINUTE);
        if (!allowed) {
            log.warn("Rate limit exceeded for public invoice verification from IP: {}", clientIp);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                    "error", "RATE_LIMIT_EXCEEDED",
                    "message", "Rate limit exceeded. Maximum 60 verification requests per minute.",
                    "amharicMessage", "የማረጋገጫ ጥያቄ ገደብ አልፏል። እባክዎ ከጥቂት ደቂቃዎች በኋላ እንደገና ይሞክሩ።"
            ));
        }

        if (irn == null || irn.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "INVALID_IRN",
                    "message", "Invoice Reference Number (IRN) must be provided."
            ));
        }

        // 2. Lookup invoice by IRN
        Optional<Invoice> invoiceOpt = invoiceRepository.findByIrn(irn.trim());
        if (invoiceOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", "INVOICE_NOT_FOUND",
                    "message", "No registered invoice found matching IRN: " + irn.trim(),
                    "amharicMessage", "የተጠቀሰው የደረሰኝ ማመሳከሪያ ቁጥር (IRN) አልተገኘም።",
                    "irn", irn.trim()
            ));
        }

        Invoice invoice = invoiceOpt.get();
        TaxpayerProfile seller = taxpayerProfileRepository.findById(invoice.getTenantId()).orElse(null);
        PublicInvoiceVerificationDto response = PublicInvoiceVerificationDto.fromEntity(invoice, seller);

        return ResponseEntity.ok(response);
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "0.0.0.0";
    }
}
