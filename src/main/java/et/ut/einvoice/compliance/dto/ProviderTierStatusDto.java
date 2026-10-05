package et.ut.einvoice.compliance.dto;

import et.ut.einvoice.compliance.domain.ProviderTierStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProviderTierStatusDto(
    UUID id,
    Instant assessmentTime,
    int activeTaxpayerCount,
    BigDecimal annualSalesVolume,
    int currentLevel,
    int projectedLevel,
    ProviderTierStatus status,
    BigDecimal proximityPercentage,
    String alertMessage,
    Instant notifiedAt,
    Instant createdAt
) {}
