package et.ut.einvoice.adjustments.dto;

import et.ut.einvoice.adjustments.domain.AdjustmentMoRStatus;
import et.ut.einvoice.adjustments.domain.NoteType;
import et.ut.einvoice.adjustments.domain.TaxAdjustment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Client-safe representation of a tax adjustment. Tenant and invoice database
 * identifiers are deliberately not part of the external contract.
 */
public record TaxAdjustmentResponse(
        UUID id,
        NoteType noteType,
        String originalIrn,
        String adjustmentReason,
        BigDecimal adjustedPreTax,
        BigDecimal adjustedTax,
        BigDecimal adjustedTotal,
        BigDecimal originalGrandTotal,
        BigDecimal newGrandTotal,
        String irn,
        String rrn,
        String qrCode,
        String ackDate,
        String status,
        AdjustmentMoRStatus morStatus,
        Instant createdAt
) {
    public TaxAdjustmentResponse(
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
        this(null, noteType, originalIrn, adjustmentReason, adjustedPreTax, adjustedTax, adjustedTotal,
             null, null, irn, null, null, ackDate, status, AdjustmentMoRStatus.REGISTERED, createdAt);
    }

    public static TaxAdjustmentResponse fromEntity(TaxAdjustment adjustment) {
        return new TaxAdjustmentResponse(
                adjustment.getId(),
                adjustment.getNoteType(),
                adjustment.getOriginalIrn(),
                adjustment.getAdjustmentReason(),
                adjustment.getAdjustedPreTax(),
                adjustment.getAdjustedTax(),
                adjustment.getAdjustedTotal(),
                adjustment.getOriginalGrandTotal(),
                adjustment.getNewGrandTotal(),
                adjustment.getIrn(),
                adjustment.getRrn(),
                adjustment.getQrCode(),
                adjustment.getAckDate(),
                adjustment.getStatus(),
                adjustment.getMorStatus(),
                adjustment.getCreatedAt()
        );
    }
}
