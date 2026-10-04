package et.ut.einvoice.tenancy.controller;

import et.ut.einvoice.audit.domain.AuditAction;
import et.ut.einvoice.audit.service.AuditService;
import et.ut.einvoice.platform.context.TenantContext;
import et.ut.einvoice.platform.context.TenantContextHolder;
import et.ut.einvoice.platform.exception.BusinessException;
import et.ut.einvoice.tenancy.domain.Subscription;
import et.ut.einvoice.tenancy.repository.SubscriptionRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/saas/subscriptions")
@Tag(name = "SaaS Subscription Administration", description = "Endpoints for managing commercial plans, invoice limits, and rate limits")
public class SaasSubscriptionController {

    private static final Logger log = LoggerFactory.getLogger(SaasSubscriptionController.class);

    private final SubscriptionRepository subscriptionRepository;
    private final et.ut.einvoice.tenancy.repository.TenantRepository tenantRepository;
    private final AuditService auditService;

    public SaasSubscriptionController(
            SubscriptionRepository subscriptionRepository,
            et.ut.einvoice.tenancy.repository.TenantRepository tenantRepository,
            AuditService auditService
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.tenantRepository = tenantRepository;
        this.auditService = auditService;
    }

    public record SubscriptionDetailDto(
            UUID tenantId,
            String tenantName,
            String tin,
            String planCode,
            String billingCycle,
            int maxMonthlyInvoices,
            int rateLimitRps,
            boolean offlineAllowed,
            String status,
            int maxBranches,
            int maxPosDevices,
            int monthlyInvoiceQuota,
            java.time.Instant nextBillingDate,
            java.time.Instant createdAt
    ) {}

    private SubscriptionDetailDto toDto(Subscription sub) {
        et.ut.einvoice.tenancy.domain.Tenant tenant = tenantRepository.findById(sub.getTenantId()).orElse(null);
        String tenantName = tenant != null ? tenant.getLegalName() : "Taxpayer Entity";
        String tin = tenant != null ? tenant.getTin() : "";
        int maxBranches = sub.getPlanCode().equalsIgnoreCase("ENTERPRISE") ? 50 : (sub.getPlanCode().equalsIgnoreCase("GROWTH") ? 10 : 2);
        int maxPos = sub.getPlanCode().equalsIgnoreCase("ENTERPRISE") ? 100 : (sub.getPlanCode().equalsIgnoreCase("GROWTH") ? 20 : 5);
        return new SubscriptionDetailDto(
                sub.getTenantId(), tenantName, tin, sub.getPlanCode(), sub.getBillingCycle(),
                sub.getMaxMonthlyInvoices(), sub.getRateLimitRps(), sub.getOfflineAllowed(), sub.getStatus(),
                maxBranches, maxPos, sub.getMaxMonthlyInvoices(), sub.getCreatedAt().plus(java.time.Duration.ofDays(30)),
                sub.getCreatedAt()
        );
    }

    private String resolveOperatorId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null && !auth.getName().isBlank()) {
            return auth.getName();
        }
        TenantContext ctx = TenantContextHolder.getContext();
        return (ctx != null && ctx.userId() != null) ? ctx.userId() : "SAAS_OPERATOR";
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SAAS_ADMIN', 'PLATFORM_ADMIN', 'SAAS_OPERATOR')")
    @Operation(summary = "List all commercial tenant subscriptions with taxpayer identity details")
    public ResponseEntity<List<SubscriptionDetailDto>> listSubscriptions() {
        List<Subscription> subs = subscriptionRepository.findAll();
        return ResponseEntity.ok(subs.stream().map(this::toDto).toList());
    }

    @GetMapping("/{tenantId}")
    @PreAuthorize("hasAnyRole('SAAS_ADMIN', 'PLATFORM_ADMIN', 'SAAS_OPERATOR')")
    @Operation(summary = "Get subscription details for a specific tenant")
    public ResponseEntity<SubscriptionDetailDto> getSubscription(@PathVariable("tenantId") UUID tenantId) {
        return subscriptionRepository.findByTenantId(tenantId)
                .map(this::toDto)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{tenantId}")
    @PreAuthorize("hasAnyRole('SAAS_ADMIN', 'PLATFORM_ADMIN')")
    @Operation(summary = "Update commercial subscription plan, limits, and allowances")
    public ResponseEntity<SubscriptionDetailDto> updateSubscription(
            @PathVariable("tenantId") UUID tenantId,
            @Valid @RequestBody UpdateSubscriptionRequest request
    ) {
        Subscription subscription = subscriptionRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new BusinessException("SUBSCRIPTION_NOT_FOUND", "Subscription not found for tenant: " + tenantId, "የደንበኝነት ምዝገባ አልተገኘም", HttpStatus.NOT_FOUND));

        if (request.planCode() != null && !request.planCode().isBlank()) {
            subscription.setPlanCode(request.planCode().trim().toUpperCase());
        }
        if (request.billingCycle() != null && !request.billingCycle().isBlank()) {
            subscription.setBillingCycle(request.billingCycle().trim().toUpperCase());
        }
        if (request.maxMonthlyInvoices() != null && request.maxMonthlyInvoices() > 0) {
            subscription.setMaxMonthlyInvoices(request.maxMonthlyInvoices());
        }
        if (request.rateLimitRps() != null && request.rateLimitRps() > 0) {
            subscription.setRateLimitRps(request.rateLimitRps());
        }
        if (request.offlineAllowed() != null) {
            subscription.setOfflineAllowed(request.offlineAllowed());
        }
        if (request.status() != null && !request.status().isBlank()) {
            subscription.setStatus(request.status().trim().toUpperCase());
        }

        Subscription saved = subscriptionRepository.save(subscription);
        String operatorId = resolveOperatorId();

        // Audit Event
        try {
            auditService.recordEvent(
                    tenantId,
                    "MAIN",
                    operatorId,
                    "SAAS_OPERATOR",
                    AuditAction.SUBSCRIPTION_CHANGED.name(),
                    "SUBSCRIPTION",
                    saved.getId().toString(),
                    String.format("{\"plan\":\"%s\",\"maxInvoices\":%d,\"status\":\"%s\"}", saved.getPlanCode(), saved.getMaxMonthlyInvoices(), saved.getStatus()),
                    "127.0.0.1"
            );
        } catch (Exception e) {
            log.warn("Failed to write subscription audit event: {}", e.getMessage());
        }

        log.info("Updated subscription for tenant '{}' to plan '{}' by operator '{}'", tenantId, saved.getPlanCode(), operatorId);

        return ResponseEntity.ok(toDto(saved));
    }

    public record UpdateSubscriptionRequest(
            @jakarta.validation.constraints.Pattern(regexp = "^$|^[A-Z][A-Z0-9_-]{0,63}$", message = "Plan code is invalid")
            String planCode,
            @jakarta.validation.constraints.Pattern(regexp = "^$|^(MONTHLY|ANNUAL)$", message = "Billing cycle is invalid")
            String billingCycle,
            @jakarta.validation.constraints.Positive
            Integer maxMonthlyInvoices,
            @jakarta.validation.constraints.Positive
            Integer rateLimitRps,
            Boolean offlineAllowed,
            @jakarta.validation.constraints.Pattern(regexp = "^$|^(ACTIVE|SUSPENDED|CANCELLED)$", message = "Subscription status is invalid")
            String status
    ) {
        @jakarta.validation.constraints.AssertTrue(message = "At least one subscription field must be provided")
        public boolean hasUpdate() {
            return planCode != null || billingCycle != null || maxMonthlyInvoices != null
                    || rateLimitRps != null || offlineAllowed != null || status != null;
        }
    }
}
