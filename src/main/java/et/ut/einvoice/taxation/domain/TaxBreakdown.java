package et.ut.einvoice.taxation.domain;

import java.math.BigDecimal;

public record TaxBreakdown(
        TaxCode taxCode,
        BigDecimal taxableAmount,
        BigDecimal taxRate,
        BigDecimal taxAmount,
        BigDecimal exciseAmount
) {}
