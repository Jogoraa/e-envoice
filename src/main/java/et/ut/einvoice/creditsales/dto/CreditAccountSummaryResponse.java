package et.ut.einvoice.creditsales.dto;

import et.ut.einvoice.creditsales.domain.CreditStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreditAccountSummaryResponse(
        UUID invoiceId,
        String documentNumber,
        String irn,
        String buyerName,
        String buyerTin,
        BigDecimal originalAmount,
        BigDecimal totalSettled,
        BigDecimal outstandingBalance,
        Instant creditDueDate,
        String creditTermsDescription,
        CreditStatus creditStatus,
        List<CreditSettlementResponse> settlements
) {}
