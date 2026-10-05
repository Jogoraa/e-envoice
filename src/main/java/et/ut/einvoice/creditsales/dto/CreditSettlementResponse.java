package et.ut.einvoice.creditsales.dto;

import et.ut.einvoice.creditsales.domain.CreditSettlement;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreditSettlementResponse(
        UUID id,
        UUID tenantId,
        UUID invoiceId,
        UUID cashReceiptId,
        String settlementNumber,
        BigDecimal settlementAmount,
        BigDecimal balanceBefore,
        BigDecimal balanceAfter,
        String paymentMethod,
        String paymentReference,
        Instant settledAt
) {
    public static CreditSettlementResponse fromEntity(CreditSettlement entity) {
        return new CreditSettlementResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getInvoiceId(),
                entity.getCashReceiptId(),
                entity.getSettlementNumber(),
                entity.getSettlementAmount(),
                entity.getBalanceBefore(),
                entity.getBalanceAfter(),
                entity.getPaymentMethod(),
                entity.getPaymentReference(),
                entity.getSettledAt()
        );
    }
}
