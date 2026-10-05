package et.ut.einvoice.compliance.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;

public record AuthorityExportRequestDto(
    @NotBlank(message = "Statutory case reference is required under Art. 15(5)")
    String caseReference,

    @NotBlank(message = "Auditing investigation reason is required under Art. 15(5)")
    String reason,

    UUID tenantId,
    String customerTin,
    Instant dateFrom,
    Instant dateTo,
    String invoiceRangeStart,
    String invoiceRangeEnd,
    String transactionType
) {}
