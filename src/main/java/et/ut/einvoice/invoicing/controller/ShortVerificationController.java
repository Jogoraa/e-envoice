package et.ut.einvoice.invoicing.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Root-level short URL endpoint (/v/{token}) embedded in transactional SMS messages.
 * Delegates to the authoritative PublicInvoiceVerificationController.
 */
@RestController
@Tag(name = "Short Verification", description = "Short URL Verification Endpoint for SMS Links")
public class ShortVerificationController {

    private final PublicInvoiceVerificationController publicVerificationController;

    public ShortVerificationController(PublicInvoiceVerificationController publicVerificationController) {
        this.publicVerificationController = publicVerificationController;
    }

    @GetMapping("/v/{token}")
    @Operation(
            summary = "Verify Electronic Invoice by Short Link Token",
            description = "Resolves short URL verification link from transactional SMS messages."
    )
    public ResponseEntity<?> verifyByShortToken(@PathVariable("token") String token, HttpServletRequest request) {
        return publicVerificationController.verifyInvoiceByToken(token, request);
    }
}
