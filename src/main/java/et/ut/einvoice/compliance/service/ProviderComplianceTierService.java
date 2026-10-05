package et.ut.einvoice.compliance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import et.ut.einvoice.audit.service.AuditService;
import et.ut.einvoice.compliance.domain.ProviderComplianceTier;
import et.ut.einvoice.compliance.domain.ProviderTierStatus;
import et.ut.einvoice.compliance.domain.ProviderTierStatusHistory;
import et.ut.einvoice.compliance.dto.ProviderComplianceTierDto;
import et.ut.einvoice.compliance.dto.ProviderDashboardSummaryDto;
import et.ut.einvoice.compliance.dto.ProviderTierStatusDto;
import et.ut.einvoice.compliance.repository.ProviderComplianceTierRepository;
import et.ut.einvoice.compliance.repository.ProviderTierStatusHistoryRepository;
import et.ut.einvoice.invoicing.repository.InvoiceRepository;
import et.ut.einvoice.tenancy.domain.TenantStatus;
import et.ut.einvoice.tenancy.repository.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service governing SaaS Provider compliance tiering, statutory guarantee calculations,
 * and threshold proximity alerts under Directive No. 1142/2026 Art. 14(6).
 */
@Service
public class ProviderComplianceTierService {

    private static final Logger log = LoggerFactory.getLogger(ProviderComplianceTierService.class);
    private static final UUID PLATFORM_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final ProviderComplianceTierRepository tierRepository;
    private final ProviderTierStatusHistoryRepository historyRepository;
    private final TenantRepository tenantRepository;
    private final InvoiceRepository invoiceRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public ProviderComplianceTierService(
            ProviderComplianceTierRepository tierRepository,
            ProviderTierStatusHistoryRepository historyRepository,
            TenantRepository tenantRepository,
            InvoiceRepository invoiceRepository,
            AuditService auditService,
            ObjectMapper objectMapper
    ) {
        this.tierRepository = tierRepository;
        this.historyRepository = historyRepository;
        this.tenantRepository = tenantRepository;
        this.invoiceRepository = invoiceRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @jakarta.annotation.PostConstruct
    @Transactional
    public void initTiersIfEmpty() {
        if (tierRepository.count() == 0) {
            tierRepository.save(new ProviderComplianceTier(UUID.fromString("b1000001-0000-0000-0000-000000000001"), 1, 0, 500, BigDecimal.ZERO, new BigDecimal("10000000.00"), BigDecimal.ZERO, 2, java.time.LocalDate.parse("2026-01-01"), "Directive 1142/2026 Art. 14(6)", "2026.1"));
            tierRepository.save(new ProviderComplianceTier(UUID.fromString("b1000001-0000-0000-0000-000000000002"), 2, 501, 1000, new BigDecimal("10000000.01"), new BigDecimal("25000000.00"), new BigDecimal("10000.00"), 2, java.time.LocalDate.parse("2026-01-01"), "Directive 1142/2026 Art. 14(6)", "2026.1"));
            tierRepository.save(new ProviderComplianceTier(UUID.fromString("b1000001-0000-0000-0000-000000000003"), 3, 1001, 2500, new BigDecimal("25000000.01"), new BigDecimal("50000000.00"), new BigDecimal("25000.00"), 3, java.time.LocalDate.parse("2026-01-01"), "Directive 1142/2026 Art. 14(6)", "2026.1"));
            tierRepository.save(new ProviderComplianceTier(UUID.fromString("b1000001-0000-0000-0000-000000000004"), 4, 2501, 5000, new BigDecimal("50000000.01"), new BigDecimal("100000000.00"), new BigDecimal("50000.00"), 4, java.time.LocalDate.parse("2026-01-01"), "Directive 1142/2026 Art. 14(6)", "2026.1"));
            tierRepository.save(new ProviderComplianceTier(UUID.fromString("b1000001-0000-0000-0000-000000000005"), 5, 5001, 10000, new BigDecimal("100000000.01"), new BigDecimal("250000000.00"), new BigDecimal("75000.00"), 4, java.time.LocalDate.parse("2026-01-01"), "Directive 1142/2026 Art. 14(6)", "2026.1"));
            tierRepository.save(new ProviderComplianceTier(UUID.fromString("b1000001-0000-0000-0000-000000000006"), 6, 10001, 15000, new BigDecimal("250000000.01"), new BigDecimal("500000000.00"), new BigDecimal("100000.00"), 5, java.time.LocalDate.parse("2026-01-01"), "Directive 1142/2026 Art. 14(6)", "2026.1"));
            tierRepository.save(new ProviderComplianceTier(UUID.fromString("b1000001-0000-0000-0000-000000000007"), 7, 15001, 20000, new BigDecimal("500000000.01"), new BigDecimal("1000000000.00"), new BigDecimal("125000.00"), 6, java.time.LocalDate.parse("2026-01-01"), "Directive 1142/2026 Art. 14(6)", "2026.1"));
            tierRepository.save(new ProviderComplianceTier(UUID.fromString("b1000001-0000-0000-0000-000000000008"), 8, 20001, 30000, new BigDecimal("1000000000.01"), new BigDecimal("2000000000.00"), new BigDecimal("150000.00"), 6, java.time.LocalDate.parse("2026-01-01"), "Directive 1142/2026 Art. 14(6)", "2026.1"));
            tierRepository.save(new ProviderComplianceTier(UUID.fromString("b1000001-0000-0000-0000-000000000009"), 9, 30001, 40000, new BigDecimal("2000000000.01"), new BigDecimal("5000000000.00"), new BigDecimal("200000.00"), 7, java.time.LocalDate.parse("2026-01-01"), "Directive 1142/2026 Art. 14(6)", "2026.1"));
            tierRepository.save(new ProviderComplianceTier(UUID.fromString("b1000001-0000-0000-0000-000000000010"), 10, 40001, 999999999, new BigDecimal("5000000000.01"), new BigDecimal("999999999999.00"), new BigDecimal("250000.00"), 8, java.time.LocalDate.parse("2026-01-01"), "Directive 1142/2026 Art. 14(6)", "2026.1"));
        }
    }

    @Transactional(readOnly = true)
    public List<ProviderComplianceTierDto> getAllTiers() {
        return tierRepository.findAllByOrderByTierLevelAsc().stream()
                .map(this::toTierDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProviderTierStatusDto> getStatusHistory() {
        return historyRepository.findAllByOrderByAssessmentTimeDesc().stream()
                .map(this::toStatusDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public ProviderTierStatusDto evaluateCurrentTier() {
        int activeTaxpayers = (int) tenantRepository.countByStatus(TenantStatus.ACTIVE);
        Instant oneYearAgo = Instant.now().minus(365, ChronoUnit.DAYS);
        BigDecimal annualSalesVolume = invoiceRepository.calculateTotalSalesVolumeSince(oneYearAgo);
        if (annualSalesVolume == null) {
            annualSalesVolume = BigDecimal.ZERO;
        }

        int levelFromTaxpayers = tierRepository.findMatchingByTaxpayers(activeTaxpayers)
                .map(ProviderComplianceTier::getTierLevel)
                .orElse(1);

        int levelFromSales = tierRepository.findMatchingBySalesVolume(annualSalesVolume)
                .map(ProviderComplianceTier::getTierLevel)
                .orElse(1);

        int projectedLevel = Math.max(levelFromTaxpayers, levelFromSales);

        Optional<ProviderTierStatusHistory> latestHistory = historyRepository.findLatest();
        int currentLevel = latestHistory.map(ProviderTierStatusHistory::getCurrentLevel).orElse(1);

        ProviderComplianceTier currentTier = tierRepository.findByTierLevel(currentLevel)
                .orElseGet(() -> tierRepository.findByTierLevel(1).orElseThrow());

        BigDecimal proximityPercentage = calculateProximity(activeTaxpayers, annualSalesVolume, currentTier);

        ProviderTierStatus status;
        String alertMessage;

        if (projectedLevel > currentLevel) {
            status = ProviderTierStatus.LEVEL_CHANGE_PENDING_NOTIFICATION;
            alertMessage = String.format("Provider exceeded Level %d limits. Level %d upgrade required pursuant to Art. 14(6). Formal notification pending.",
                    currentLevel, projectedLevel);
        } else if (proximityPercentage.compareTo(BigDecimal.valueOf(80)) >= 0) {
            status = ProviderTierStatus.THRESHOLD_APPROACHING;
            alertMessage = String.format("Provider at %s%% of Level %d capacity. Level %d threshold approaching.",
                    proximityPercentage.toPlainString(), currentLevel, Math.min(10, currentLevel + 1));
        } else {
            status = ProviderTierStatus.CURRENT_LEVEL;
            alertMessage = String.format("Provider metrics compliant with Level %d statutory limits.", currentLevel);
        }

        ProviderTierStatusHistory history = new ProviderTierStatusHistory(
                UUID.randomUUID(),
                Instant.now(),
                activeTaxpayers,
                annualSalesVolume,
                currentLevel,
                projectedLevel,
                status,
                proximityPercentage,
                alertMessage,
                null
        );

        ProviderTierStatusHistory saved = historyRepository.save(history);

        recordAudit("EVALUATE_TIER", saved);
        log.info("Evaluated provider compliance tier: Current={}, Projected={}, Status={}, Proximity={}%",
                currentLevel, projectedLevel, status, proximityPercentage);

        return toStatusDto(saved);
    }

    @Transactional
    public ProviderTierStatusDto markNotified(UUID historyId) {
        ProviderTierStatusHistory history = historyRepository.findById(historyId)
                .orElseThrow(() -> new IllegalArgumentException("Tier status history not found: " + historyId));

        history.setStatus(ProviderTierStatus.LEVEL_CHANGE_NOTIFIED);
        history.setNotifiedAt(Instant.now());
        history.setAlertMessage("Statutory tier change notification delivered to FDRE Ministry of Revenues pursuant to Art. 14(6).");

        ProviderTierStatusHistory updated = historyRepository.save(history);
        recordAudit("NOTIFY_MOR_TIER_CHANGE", updated);
        return toStatusDto(updated);
    }

    @Transactional
    public ProviderTierStatusDto confirmTierTransition(UUID historyId) {
        ProviderTierStatusHistory history = historyRepository.findById(historyId)
                .orElseThrow(() -> new IllegalArgumentException("Tier status history not found: " + historyId));

        history.setCurrentLevel(history.getProjectedLevel());
        history.setStatus(ProviderTierStatus.CONFIRMED);
        history.setAlertMessage(String.format("Level %d tier transition confirmed by Ministry of Revenues.", history.getProjectedLevel()));

        ProviderTierStatusHistory updated = historyRepository.save(history);
        recordAudit("CONFIRM_TIER_TRANSITION", updated);
        return toStatusDto(updated);
    }

    @Transactional(readOnly = true)
    public ProviderDashboardSummaryDto getDashboardSummary() {
        List<ProviderComplianceTierDto> allTiers = getAllTiers();
        Optional<ProviderTierStatusHistory> latestOpt = historyRepository.findLatest();

        int currentLevel = latestOpt.map(ProviderTierStatusHistory::getCurrentLevel).orElse(1);
        int projectedLevel = latestOpt.map(ProviderTierStatusHistory::getProjectedLevel).orElse(1);
        int activeTaxpayers = latestOpt.map(ProviderTierStatusHistory::getActiveTaxpayerCount)
                .orElseGet(() -> (int) tenantRepository.countByStatus(TenantStatus.ACTIVE));

        Instant oneYearAgo = Instant.now().minus(365, ChronoUnit.DAYS);
        BigDecimal annualSales = latestOpt.map(ProviderTierStatusHistory::getAnnualSalesVolume)
                .orElseGet(() -> {
                    BigDecimal sum = invoiceRepository.calculateTotalSalesVolumeSince(oneYearAgo);
                    return sum != null ? sum : BigDecimal.ZERO;
                });

        ProviderTierStatus status = latestOpt.map(ProviderTierStatusHistory::getStatus)
                .orElse(ProviderTierStatus.CURRENT_LEVEL);

        BigDecimal proximity = latestOpt.map(ProviderTierStatusHistory::getProximityPercentage)
                .orElse(BigDecimal.ZERO);

        String alert = latestOpt.map(ProviderTierStatusHistory::getAlertMessage)
                .orElse("Provider metrics operating within statutory limits.");

        ProviderComplianceTierDto currentTier = allTiers.stream()
                .filter(t -> t.tierLevel() == currentLevel)
                .findFirst()
                .orElse(allTiers.isEmpty() ? null : allTiers.get(0));

        ProviderComplianceTierDto projectedTier = allTiers.stream()
                .filter(t -> t.tierLevel() == projectedLevel)
                .findFirst()
                .orElse(currentTier);

        BigDecimal guarantee = currentTier != null ? currentTier.requiredGuaranteeAmountUsd() : BigDecimal.ZERO;
        int staffing = currentTier != null ? currentTier.requiredTechnicalStaffing() : 2;

        return new ProviderDashboardSummaryDto(
                currentLevel,
                currentTier,
                projectedLevel,
                projectedTier,
                activeTaxpayers,
                annualSales,
                status,
                proximity,
                alert,
                guarantee,
                staffing,
                allTiers
        );
    }

    private BigDecimal calculateProximity(int activeTaxpayers, BigDecimal annualSalesVolume, ProviderComplianceTier tier) {
        if (tier.getMaxActiveTaxpayers() <= 0 || tier.getMaxAnnualSalesVolume().compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal taxpayerRatio = BigDecimal.valueOf(activeTaxpayers)
                .divide(BigDecimal.valueOf(tier.getMaxActiveTaxpayers()), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        BigDecimal salesRatio = annualSalesVolume
                .divide(tier.getMaxAnnualSalesVolume(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        BigDecimal maxRatio = taxpayerRatio.max(salesRatio);
        return maxRatio.setScale(2, RoundingMode.HALF_UP);
    }

    private void recordAudit(String action, ProviderTierStatusHistory history) {
        try {
            String payload = objectMapper.writeValueAsString(history);
            auditService.recordEvent(
                    PLATFORM_TENANT_ID,
                    "SYSTEM",
                    action,
                    "PROVIDER_COMPLIANCE_TIER",
                    history.getId().toString(),
                    payload
            );
        } catch (Exception e) {
            log.warn("Failed to serialize audit event for tier history {}: {}", history.getId(), e.getMessage());
        }
    }

    private ProviderComplianceTierDto toTierDto(ProviderComplianceTier t) {
        return new ProviderComplianceTierDto(
                t.getId(),
                t.getTierLevel(),
                t.getMinActiveTaxpayers(),
                t.getMaxActiveTaxpayers(),
                t.getMinAnnualSalesVolume(),
                t.getMaxAnnualSalesVolume(),
                t.getRequiredGuaranteeAmountUsd(),
                t.getRequiredTechnicalStaffing(),
                t.getEffectiveFrom(),
                t.getSourceArticle(),
                t.getVersion()
        );
    }

    private ProviderTierStatusDto toStatusDto(ProviderTierStatusHistory h) {
        return new ProviderTierStatusDto(
                h.getId(),
                h.getAssessmentTime(),
                h.getActiveTaxpayerCount(),
                h.getAnnualSalesVolume(),
                h.getCurrentLevel(),
                h.getProjectedLevel(),
                h.getStatus(),
                h.getProximityPercentage(),
                h.getAlertMessage(),
                h.getNotifiedAt(),
                h.getCreatedAt()
        );
    }
}
