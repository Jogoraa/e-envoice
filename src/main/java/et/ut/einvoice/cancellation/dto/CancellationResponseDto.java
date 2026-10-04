package et.ut.einvoice.cancellation.dto;

import et.ut.einvoice.cancellation.domain.CancellationRequest;
import et.ut.einvoice.cancellation.domain.CancellationState;

import java.time.Instant;
import java.util.UUID;

/**
 * Cancellation workflow status without tenant, invoice, evidence, or free-form
 * reason data that can contain sensitive customer information.
 */
public record CancellationResponseDto(
        UUID id,
        String irn,
        String reasonCategory,
        CancellationState state,
        Instant slaDeadlineAt,
        String cancellationRef
) {
    public static CancellationResponseDto fromEntity(CancellationRequest request) {
        return new CancellationResponseDto(
                request.getId(),
                request.getIrn(),
                request.getReasonCategory(),
                request.getState(),
                request.getSlaDeadlineAt(),
                request.getCancellationRef()
        );
    }
}
