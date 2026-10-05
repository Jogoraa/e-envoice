package et.ut.einvoice.invoicing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreateManualFiscalDocumentDto(
        UUID branchId,
        @NotBlank String manualDocumentNumber,
        @NotBlank String manualBook,
        @NotNull Instant originalIssueTime,
        String customerTin,
        String customerName,
        @NotNull BigDecimal totalAmount,
        @NotNull BigDecimal subtotal,
        @NotNull BigDecimal taxAmount,
        @NotNull List<ManualFiscalItemDto> items,
        @NotBlank String operatorId,
        @NotBlank String outageReference
) {}
