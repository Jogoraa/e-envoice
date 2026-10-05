package et.ut.einvoice.invoicing.dto;

import java.math.BigDecimal;

public record ManualFiscalItemDto(
        String itemCode,
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal taxRate,
        BigDecimal taxAmount,
        BigDecimal totalAmount
) {}
