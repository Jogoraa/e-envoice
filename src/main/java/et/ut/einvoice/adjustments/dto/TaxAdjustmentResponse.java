package et.ut.einvoice.adjustments.dto;

import et.ut.einvoice.adjustments.domain.TaxAdjustment;
import et.ut.einvoice.adjustments.domain.NoteType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Client-safe representation of a tax adjustment. Tenant and invoice database
 * identifiers are deliberately not part of the external contract.
 */
public record TaxAdjustmentResponse(
        NoteType noteType,
        String originalIrn,
        String adjustmentReason,
        BigDecimal adjustedPreTax,
        BigDecimal adjustedTax,
        BigDecimal adjustedTotal,
        String irn,
        String ackDate,
        String status,
        Instant createdAt
) {
    public static TaxAdjustmentResponse fromEntity(TaxAdjustment adjustment) {
        return new TaxAdjustmentResponse(
                adjustment.getNoteType(),
                adjustment.getOriginalIrn(),
                adjustment.getAdjustmentReason(),
                adjustment.getAdjustedPreTax(),
                adjustment.getAdjustedTax(),
                adjustment.getAdjustedTotal(),
                adjustment.getIrn(),
                adjustment.getAckDate(),
                adjustment.getStatus(),
                adjustment.getCreatedAt()
        );
    }
}
