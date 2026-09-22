package et.ut.einvoice.tenancy.service;

import et.ut.einvoice.audit.domain.AuditAction;
import et.ut.einvoice.audit.service.AuditService;
import et.ut.einvoice.platform.exception.BusinessException;
import et.ut.einvoice.tenancy.domain.*;
import et.ut.einvoice.tenancy.repository.ApiClientRepository;
import et.ut.einvoice.tenancy.repository.SubscriptionRepository;
import et.ut.einvoice.tenancy.repository.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Service for SaaS Master operations: tenant onboarding, lifecycle management, and subscription synchronization.
 */
@Service
public class SaasTenantService {

    private static final Logger log = LoggerFactory.getLogger(SaasTenantService.class);

    private final TenantRepository tenantRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ApiClientRepository apiClientRepository;
    private final AuditService auditService;

    public SaasTenantService(
            TenantRepository tenantRepository,
            SubscriptionRepository subscriptionRepository,
            ApiClientRepository apiClientRepository,
            AuditService auditService
    ) {
        this.tenantRepository = tenantRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.apiClientRepository = apiClientRepository;
        this.auditService = auditService;
    }

    public record TenantSummaryDto(
            UUID id,
            String organizationId,
            String legalName,
            String tradeName,
            String tin,
            String status,
            String subscriptionStatus,
            String governmentStatus,
            String planCode,
            String billingCycle,
            int maxMonthlyInvoices,
            int rateLimitRps,
            boolean offlineAllowed,
            Instant createdAt
    ) {}

    public record OnboardTenantRequest(
            String organizationId,
            String legalName,
            String tradeName,
            String tin,
            String planCode,
            String billingCycle,
            Integer maxMonthlyInvoices,
            Integer rateLimitRps,
            Boolean offlineAllowed,
            String initialApiKey,
            String initialClientSecret
    ) {}

    public record LifecycleTransitionRequest(
            String targetState,
            String reason
    ) {}

    @Transactional(readOnly = true)
    public List<TenantSummaryDto> listTenants() {
        List<Tenant> tenants = tenantRepository.findAll();
        List<TenantSummaryDto> dtos = new ArrayList<>();

        for (Tenant t : tenants) {
            Optional<Subscription> subOpt = subscriptionRepository.findByTenantId(t.getId());
            String plan = subOpt.map(Subscription::getPlanCode).orElse("STARTER");
            String cycle = subOpt.map(Subscription::getBillingCycle).orElse("MONTHLY");
            int maxInv = subOpt.map(Subscription::getMaxMonthlyInvoices).orElse(5000);
            int rps = subOpt.map(Subscription::getRateLimitRps).orElse(20);
            boolean offline = subOpt.map(Subscription::getOfflineAllowed).orElse(false);

            dtos.add(new TenantSummaryDto(
                    t.getId(),
                    t.getOrganizationId(),
                    t.getLegalName(),
                    t.getTradeName(),
                    t.getTin(),
                    t.getStatus().name(),
                    t.getSubscriptionStatus().name(),
                    t.getGovernmentStatus().name(),
                    plan,
                    cycle,
                    maxInv,
                    rps,
                    offline,
                    t.getCreatedAt()
            ));
        }

        return dtos;
    }

    @Transactional(readOnly = true)
    public TenantSummaryDto getTenantDetails(UUID tenantId) {
        Tenant t = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND", "Tenant not found: " + tenantId, "ድርጅቱ አልተገኘም", HttpStatus.NOT_FOUND));

        Optional<Subscription> subOpt = subscriptionRepository.findByTenantId(t.getId());
        String plan = subOpt.map(Subscription::getPlanCode).orElse("STARTER");
        String cycle = subOpt.map(Subscription::getBillingCycle).orElse("MONTHLY");
        int maxInv = subOpt.map(Subscription::getMaxMonthlyInvoices).orElse(5000);
        int rps = subOpt.map(Subscription::getRateLimitRps).orElse(20);
        boolean offline = subOpt.map(Subscription::getOfflineAllowed).orElse(false);

        return new TenantSummaryDto(
                t.getId(),
                t.getOrganizationId(),
                t.getLegalName(),
                t.getTradeName(),
                t.getTin(),
                t.getStatus().name(),
                t.getSubscriptionStatus().name(),
                t.getGovernmentStatus().name(),
                plan,
                cycle,
                maxInv,
                rps,
                offline,
                t.getCreatedAt()
        );
    }

    @Transactional
    public TenantSummaryDto onboardTenant(OnboardTenantRequest req, String operatorId) {
        if (req.tin() == null || req.tin().isBlank() || req.tin().trim().length() != 10) {
            throw new BusinessException("INVALID_TIN", "A valid 10-digit Ethiopian TIN is mandatory", "ትክክለኛ ባለ 10 አሃዝ የግብር ከፋይ መለያ ቁጥር (TIN) ያስፈልጋል", HttpStatus.BAD_REQUEST);
        }
        if (req.legalName() == null || req.legalName().isBlank()) {
            throw new BusinessException("INVALID_NAME", "Legal name is required", "ሕጋዊ የድርጅት ስም ያስፈልጋል", HttpStatus.BAD_REQUEST);
        }

        if (tenantRepository.findByTin(req.tin().trim()).isPresent()) {
            throw new BusinessException("DUPLICATE_TIN", "Tenant with TIN " + req.tin() + " is already registered", "ይህ የግብር መለያ ቁጥር ቀድሞ ተመዝግቧል", HttpStatus.CONFLICT);
        }

        UUID tenantId = UUID.randomUUID();
        String orgId = (req.organizationId() != null && !req.organizationId().isBlank())
                ? req.organizationId().trim()
                : "ORG-" + req.tin().trim();

        Tenant tenant = new Tenant(tenantId, orgId, req.legalName().trim(), req.tradeName(), req.tin().trim(), "SME");
        tenant.activate();
        tenant.setSubscriptionStatus(SubscriptionStatus.SUBSCRIPTION_ACTIVE);
        tenant.setGovernmentStatus(GovernmentStatus.GOVERNMENT_ACTIVE);
        tenantRepository.save(tenant);

        // Create initial Subscription
        String plan = (req.planCode() != null && !req.planCode().isBlank()) ? req.planCode() : "ENTERPRISE";
        String cycle = (req.billingCycle() != null && !req.billingCycle().isBlank()) ? req.billingCycle() : "MONTHLY";
        int maxInv = (req.maxMonthlyInvoices() != null && req.maxMonthlyInvoices() > 0) ? req.maxMonthlyInvoices() : 10000;
        int rps = (req.rateLimitRps() != null && req.rateLimitRps() > 0) ? req.rateLimitRps() : 50;
        boolean offline = req.offlineAllowed() != null && req.offlineAllowed();

        Subscription sub = new Subscription(
                UUID.randomUUID(),
                tenantId,
                plan,
                cycle,
                maxInv,
                rps,
                offline,
                false,
                "ACTIVE"
        );
        subscriptionRepository.save(sub);

        // Create default API Client
        String apiKey = (req.initialApiKey() != null && !req.initialApiKey().isBlank())
                ? req.initialApiKey().trim()
                : "KEY_" + req.tin().trim();
        String clientSecret = (req.initialClientSecret() != null && !req.initialClientSecret().isBlank())
                ? req.initialClientSecret().trim()
                : "secret_" + UUID.randomUUID().toString().substring(0, 12);

        ApiClient client = new ApiClient(
                UUID.randomUUID(),
                tenantId,
                apiKey,
                clientSecret,
                "Primary POS & ERP Client",
                "invoice:read invoice:create invoice:adjust invoice:cancel customer:read customer:create tenant:admin"
        );
        apiClientRepository.save(client);

        // Audit Event
        try {
            auditService.recordEvent(
                    tenantId,
                    "MAIN",
                    operatorId != null ? operatorId : "SAAS_OPERATOR",
                    "SAAS_OPERATOR",
                    AuditAction.TENANT_CREATED.name(),
                    "TENANT",
                    tenantId.toString(),
                    String.format("{\"legalName\":\"%s\",\"tin\":\"%s\",\"plan\":\"%s\"}", tenant.getLegalName(), tenant.getTin(), plan),
                    "127.0.0.1"
            );
        } catch (Exception e) {
            log.warn("Failed to write tenant creation audit event: {}", e.getMessage());
        }

        log.info("Onboarded new tenant '{}' (TIN: {}) by operator '{}'", tenant.getLegalName(), tenant.getTin(), operatorId);

        return new TenantSummaryDto(
                tenantId,
                orgId,
                tenant.getLegalName(),
                tenant.getTradeName(),
                tenant.getTin(),
                tenant.getStatus().name(),
                tenant.getSubscriptionStatus().name(),
                tenant.getGovernmentStatus().name(),
                plan,
                cycle,
                maxInv,
                rps,
                offline,
                tenant.getCreatedAt()
        );
    }

    @Transactional
    public TenantSummaryDto transitionLifecycle(UUID tenantId, LifecycleTransitionRequest req, String operatorId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND", "Tenant not found: " + tenantId, "ድርጅቱ አልተገኘም", HttpStatus.NOT_FOUND));

        String target = req.targetState() != null ? req.targetState().toUpperCase().trim() : "ACTIVE";
        TenantStatus previousStatus = tenant.getStatus();
        TenantStatus newStatus;

        try {
            newStatus = TenantStatus.valueOf(target);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("INVALID_STATE", "Unsupported tenant state: " + target, "ልክ ያልሆነ የድርጅት ሁኔታ", HttpStatus.BAD_REQUEST);
        }

        switch (newStatus) {
            case ACTIVE -> tenant.activate();
            case SUSPENDED -> tenant.suspend();
            case DEACTIVATED -> tenant.deactivate();
            default -> {}
        }
        tenantRepository.save(tenant);

        // Audit Lifecycle Change
        try {
            auditService.recordEvent(
                    tenantId,
                    "MAIN",
                    operatorId != null ? operatorId : "SAAS_OPERATOR",
                    "SAAS_OPERATOR",
                    AuditAction.TENANT_LIFECYCLE_CHANGED.name(),
                    "TENANT",
                    tenantId.toString(),
                    String.format("{\"from\":\"%s\",\"to\":\"%s\",\"reason\":\"%s\"}", previousStatus, newStatus, req.reason() != null ? req.reason() : ""),
                    "127.0.0.1"
            );
        } catch (Exception e) {
            log.warn("Failed to write lifecycle transition audit event: {}", e.getMessage());
        }

        log.info("Transitioned tenant '{}' ({}) from {} to {} by operator '{}'",
                tenant.getLegalName(), tenantId, previousStatus, newStatus, operatorId);

        return getTenantDetails(tenantId);
    }
}
