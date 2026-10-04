package et.ut.einvoice.webhooks.controller;

import et.ut.einvoice.platform.context.TenantContextHolder;
import et.ut.einvoice.webhooks.service.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/webhooks")
@Tag(name = "Webhooks Subscription API", description = "Endpoints for registering and managing merchant webhook subscriptions")
public class WebhookApiController {

    private final WebhookService webhookService;
    private final et.ut.einvoice.platform.security.SsrfValidator ssrfValidator;

    public WebhookApiController(WebhookService webhookService, et.ut.einvoice.platform.security.SsrfValidator ssrfValidator) {
        this.webhookService = webhookService;
        this.ssrfValidator = ssrfValidator;
    }

    @PostMapping("/subscriptions")
    @PreAuthorize("hasAuthority('SCOPE_tenant:admin') or hasRole('TENANT_ADMIN')")
    @Operation(summary = "Register a new webhook subscription endpoint")
    public ResponseEntity<WebhookRegistrationResponse> registerSubscription(@Valid @RequestBody CreateSubscriptionRequest request) {
        ssrfValidator.validateDestinationUrl(request.targetUrl());
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        var sub = webhookService.registerSubscription(
                tenantId,
                request.targetUrl(),
                request.secretKey(),
                request.subscribedEvents()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(new WebhookRegistrationResponse(
                sub.isActive(),
                "Webhook subscription registered. Its secret is never returned."
        ));
    }

    public record CreateSubscriptionRequest(
            @NotBlank(message = "Target URL is mandatory")
            @jakarta.validation.constraints.Pattern(regexp = "^https?://.*", message = "Target URL must be an HTTP or HTTPS URL")
            @jakarta.validation.constraints.Size(max = 512, message = "Target URL must not exceed 512 characters")
            String targetUrl,

            @NotBlank(message = "Secret key is mandatory")
            @jakarta.validation.constraints.Size(min = 16, max = 256, message = "Secret key must be between 16 and 256 characters")
            String secretKey,

            @jakarta.validation.constraints.Size(max = 512, message = "Subscribed events must not exceed 512 characters")
            @jakarta.validation.constraints.Pattern(regexp = "^$|^[A-Za-z0-9_:, .-]+$", message = "Subscribed events contain unsupported characters")
            String subscribedEvents
    ) {}

    /** Registration acknowledgement intentionally does not expose database or tenant identifiers. */
    public record WebhookRegistrationResponse(boolean active, String message) {}
}
