package et.ut.einvoice.compliance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ProviderComplianceTierDto(
    UUID id,
    int tierLevel,
    int minActiveTaxpayers,
    int maxActiveTaxpayers,
    BigDecimal minAnnualSalesVolume,
    BigDecimal maxAnnualSalesVolume,
    BigDecimal requiredGuaranteeAmountUsd,
    int requiredTechnicalStaffing,
    LocalDate effectiveFrom,
    String sourceArticle,
    String version
) {}
