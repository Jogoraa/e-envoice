package et.ut.einvoice.compliance.dto;

import et.ut.einvoice.compliance.domain.ProviderTierStatus;
import java.math.BigDecimal;
import java.util.List;

public record ProviderDashboardSummaryDto(
    int currentLevel,
    ProviderComplianceTierDto currentTier,
    int projectedLevel,
    ProviderComplianceTierDto projectedTier,
    int activeTaxpayers,
    BigDecimal annualSalesVolume,
    ProviderTierStatus status,
    BigDecimal proximityPercentage,
    String alertMessage,
    BigDecimal requiredGuaranteeAmountUsd,
    int requiredTechnicalStaffing,
    List<ProviderComplianceTierDto> allTiers
) {}
