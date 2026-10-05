package et.ut.einvoice.compliance.service;

import et.ut.einvoice.compliance.domain.ProviderExitPlan;
import et.ut.einvoice.compliance.domain.ProviderExitStatus;
import et.ut.einvoice.compliance.domain.ProviderTenantTransition;
import et.ut.einvoice.compliance.domain.TenantTransitionStatus;
import et.ut.einvoice.compliance.dto.*;
import et.ut.einvoice.compliance.repository.ProviderExitPlanRepository;
import et.ut.einvoice.compliance.repository.ProviderTenantTransitionRepository;
import et.ut.einvoice.tenancy.domain.Tenant;
import et.ut.einvoice.tenancy.domain.TenantStatus;
import et.ut.einvoice.tenancy.repository.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProviderExitGovernanceService {

    private static final Logger log = LoggerFactory.getLogger(ProviderExitGovernanceService.class);

    private final ProviderExitPlanRepository exitPlanRepository;
    private final ProviderTenantTransitionRepository tenantTransitionRepository;
    private final TenantRepository tenantRepository;

    public ProviderExitGovernanceService(
            ProviderExitPlanRepository exitPlanRepository,
            ProviderTenantTransitionRepository tenantTransitionRepository,
            TenantRepository tenantRepository) {
        this.exitPlanRepository = exitPlanRepository;
        this.tenantTransitionRepository = tenantTransitionRepository;
        this.tenantRepository = tenantRepository;
    }

    @Transactional
    public ProviderExitPlanResponseDto createExitPlan(CreateExitPlanRequestDto dto) {
        Instant now = Instant.now();

        if (dto.isRevocation()) {
            // Art. 17(2): At least 10 days notice if accreditation canceled
            Instant minRevocationDate = now.plus(10, ChronoUnit.DAYS);
            if (dto.getEffectiveExitDate().isBefore(minRevocationDate.minus(1, ChronoUnit.HOURS))) {
                throw new IllegalArgumentException("Directive No. 1142/2026 Art. 17(2) requires at least 10 days advance notice to taxpayers for accreditation revocation");
            }
        } else {
            // Art. 17(1): At least 6 months advance notice to Authority and user taxpayers
            Instant minVoluntaryExitDate = now.plus(175, ChronoUnit.DAYS);
            if (dto.getEffectiveExitDate().isBefore(minVoluntaryExitDate)) {
                throw new IllegalArgumentException("Directive No. 1142/2026 Art. 17(1) requires at least 6 months advance notice to the Authority and taxpayers for voluntary cessation");
            }
        }

        int noticeMonths = dto.getNoticePeriodMonths() != null ? dto.getNoticePeriodMonths() : (dto.isRevocation() ? 1 : 6);
        UUID planId = UUID.randomUUID();
        ProviderExitPlan plan = new ProviderExitPlan(
                planId,
                dto.getExitReason(),
                noticeMonths,
                now,
                dto.getEffectiveExitDate()
        );

        ProviderExitPlan saved = exitPlanRepository.save(plan);
        log.info("Created Provider Exit Plan id={}, effectiveExitDate={}, isRevocation={}", saved.getId(), saved.getEffectiveExitDate(), dto.isRevocation());
        return ProviderExitPlanResponseDto.fromEntity(saved);
    }

    @Transactional
    public ProviderExitPlanResponseDto submitStrategy(UUID planId, SubmitExitStrategyRequestDto dto) {
        ProviderExitPlan plan = exitPlanRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Provider exit plan not found: " + planId));

        plan.submitStrategy(dto.getStrategyDocumentReference());
        ProviderExitPlan saved = exitPlanRepository.save(plan);
        log.info("Submitted Exit Strategy for plan id={}, docRef={}", planId, dto.getStrategyDocumentReference());
        return ProviderExitPlanResponseDto.fromEntity(saved);
    }

    @Transactional
    public ProviderExitPlanResponseDto recordAuthorityApproval(UUID planId, ApproveExitStrategyRequestDto dto) {
        ProviderExitPlan plan = exitPlanRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Provider exit plan not found: " + planId));

        plan.recordAuthorityApproval(dto.getApprovalReference(), dto.getApprovedBy());
        ProviderExitPlan saved = exitPlanRepository.save(plan);
        log.info("Authority approved Exit Strategy for plan id={}, ref={}", planId, dto.getApprovalReference());
        return ProviderExitPlanResponseDto.fromEntity(saved);
    }

    @Transactional
    public ProviderExitPlanResponseDto notifyTaxpayers(UUID planId) {
        ProviderExitPlan plan = exitPlanRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Provider exit plan not found: " + planId));

        List<Tenant> activeTenants = tenantRepository.findAll().stream()
                .filter(t -> t.getStatus() == TenantStatus.ACTIVE)
                .collect(Collectors.toList());

        for (Tenant tenant : activeTenants) {
            if (tenantTransitionRepository.findByExitPlanIdAndTenantId(planId, tenant.getId()).isEmpty()) {
                ProviderTenantTransition transition = new ProviderTenantTransition(
                        UUID.randomUUID(),
                        planId,
                        tenant.getId(),
                        tenant.getTin(),
                        tenant.getLegalName()
                );
                tenantTransitionRepository.save(transition);
            }
        }

        plan.recordTaxpayersNotified(activeTenants.size());
        plan.markInTransition();
        ProviderExitPlan saved = exitPlanRepository.save(plan);
        log.info("Notified {} active taxpayers for exit plan id={}", activeTenants.size(), planId);
        return ProviderExitPlanResponseDto.fromEntity(saved);
    }

    @Transactional
    public ProviderTenantTransition recordTenantDataRetrieval(UUID planId, UUID tenantId) {
        ProviderTenantTransition transition = tenantTransitionRepository.findByExitPlanIdAndTenantId(planId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Transition record not found for tenant " + tenantId + " in plan " + planId));

        transition.markDataRetrieved();
        return tenantTransitionRepository.save(transition);
    }

    @Transactional
    public ProviderTenantTransition recordTenantMigration(UUID planId, RecordMigrationRequestDto dto) {
        ProviderExitPlan plan = exitPlanRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Provider exit plan not found: " + planId));

        ProviderTenantTransition transition = tenantTransitionRepository.findByExitPlanIdAndTenantId(planId, dto.getTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Transition record not found for tenant " + dto.getTenantId() + " in plan " + planId));

        boolean wasAlreadyMigrated = transition.getStatus() == TenantTransitionStatus.MIGRATED;
        transition.recordMigration(dto.getDestinationProviderName(), dto.getDestinationSystemNumber(), dto.getMigrationEvidenceHash());
        ProviderTenantTransition savedTransition = tenantTransitionRepository.save(transition);

        if (!wasAlreadyMigrated) {
            plan.recordTenantMigrationComplete();
            exitPlanRepository.save(plan);
        }

        log.info("Tenant {} migration recorded. Total migrated: {}/{}", dto.getTenantId(), plan.getMigratedTenantsCount(), plan.getTotalActiveTenants());
        return savedTransition;
    }

    @Transactional
    public ProviderExitPlanResponseDto confirmCessation(UUID planId, ConfirmCessationRequestDto dto) {
        ProviderExitPlan plan = exitPlanRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Provider exit plan not found: " + planId));

        // Enforce Art. 17(5): 100% tenant migration + certificate surrender
        plan.confirmCessation(dto.getSurrenderedCertificateReference(), dto.getCessationConfirmationReference());
        ProviderExitPlan saved = exitPlanRepository.save(plan);
        log.info("Cessation confirmed for plan id={}, certRef={}, confirmRef={}", planId, dto.getSurrenderedCertificateReference(), dto.getCessationConfirmationReference());
        return ProviderExitPlanResponseDto.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public ProviderExitPlanResponseDto getLatestExitPlan() {
        return exitPlanRepository.findTopByOrderByCreatedAtDesc()
                .map(ProviderExitPlanResponseDto::fromEntity)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public ProviderExitPlanResponseDto getExitPlan(UUID planId) {
        return exitPlanRepository.findById(planId)
                .map(ProviderExitPlanResponseDto::fromEntity)
                .orElseThrow(() -> new IllegalArgumentException("Provider exit plan not found: " + planId));
    }

    @Transactional(readOnly = true)
    public List<ProviderTenantTransition> getTenantTransitions(UUID planId) {
        return tenantTransitionRepository.findByExitPlanId(planId);
    }
}
