package et.ut.einvoice.invoicing.dto;

import java.math.BigDecimal;

public record InvoiceSummaryDto(
        long todayInvoicesCount,
        BigDecimal todayGrossSales,
        BigDecimal todayVatAmount,
        long totalInvoicesCount,
        BigDecimal totalGrossSales,
        BigDecimal totalVatAmount,
        long registeredCount,
        long pendingCount
) {}
