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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

/**
 * Public statutory invoice verification by IRN or QR-token.
 * Responses intentionally avoid buyer PII, tenant identifiers, and searched-reference echoing.
 */
@RestController
@RequestMapping("/api/v1/public")
@Tag(name = "Public Verification", description = "Public Electronic Invoice Verification and QR Validation")
public class PublicInvoiceVerificationController {

    private static final Logger log = LoggerFactory.getLogger(PublicInvoiceVerificationController.class);
    private static final int RATE_LIMIT_PER_MINUTE = 20;

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
        if (!rateLimitingService.tryAcquireKey("public_verify:" + clientIp, RATE_LIMIT_PER_MINUTE)) {
            log.warn("Rate limit exceeded for public invoice verification from IP: {}", clientIp);
            return tooManyRequests();
        }

        if (irn == null || irn.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "INVALID_IRN",
                    "message", "Invoice Reference Number (IRN) must be provided."
            ));
        }

        Optional<Invoice> invoiceOpt = invoiceRepository.findByIrn(irn.trim());
        if (invoiceOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", "INVOICE_NOT_FOUND",
                    "message", "No registered invoice found for the supplied reference."
            ));
        }

        return publicVerificationResponse(invoiceOpt.get());
    }

    @Operation(summary = "Verify invoice by public verification token",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Verified"),
                    @ApiResponse(responseCode = "404", description = "Token not found"),
                    @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
            })
    @GetMapping("/verify/token/{token}")
    public ResponseEntity<?> verifyInvoiceByToken(
            @PathVariable String token,
            HttpServletRequest request) {

        String clientIp = extractClientIp(request);
        if (!rateLimitingService.tryAcquireKey("public_verify:" + clientIp, RATE_LIMIT_PER_MINUTE)) {
            return tooManyRequests();
        }

        Optional<Invoice> invoiceOpt = invoiceRepository.findByPublicVerificationToken(token);
        if (invoiceOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "error", "TOKEN_NOT_FOUND",
                            "message", "No registered invoice found for the supplied verification token."
                    ));
        }

        return publicVerificationResponse(invoiceOpt.get());
    }

    private ResponseEntity<?> publicVerificationResponse(Invoice invoice) {
        TaxpayerProfile seller = taxpayerProfileRepository.findById(invoice.getTenantId()).orElse(null);
        PublicInvoiceVerificationDto response = PublicInvoiceVerificationDto.fromEntity(invoice, seller);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate")
                .header("X-Robots-Tag", "noindex, nofollow, noarchive")
                .header("Referrer-Policy", "no-referrer")
                .body(response);
    }

    private ResponseEntity<?> tooManyRequests() {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                "error", "RATE_LIMIT_EXCEEDED",
                "message", "Too many verification requests. Please try again later."
        ));
    }

    private String extractClientIp(HttpServletRequest request) {
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "0.0.0.0";
    }
}
