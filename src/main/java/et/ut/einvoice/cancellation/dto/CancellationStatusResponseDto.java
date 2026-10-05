package et.ut.einvoice.cancellation.dto;

import et.ut.einvoice.cancellation.domain.CancellationRequest;
import et.ut.einvoice.cancellation.domain.CancellationState;

import java.time.Instant;
import java.util.UUID;

public record CancellationStatusResponseDto(
        UUID id,
        UUID tenantId,
        UUID invoiceId,
        String irn,
        String reasonCategory,
        String detailedReason,
        CancellationState state,
        Instant authorityEvidenceRequestedAt,
        Instant evidenceDeadline,
        Instant evidenceSubmittedAt,
        String rejectionReason,
        String cancellationRef,
        Instant requestedAt,
        Instant approvedAt
) {
    public static CancellationStatusResponseDto fromEntity(CancellationRequest r) {
        return new CancellationStatusResponseDto(
                r.getId(),
                r.getTenantId(),
                r.getInvoiceId(),
                r.getIrn(),
                r.getReasonCategory(),
                r.getDetailedReason(),
                r.getState(),
                r.getAuthorityEvidenceRequestedAt(),
                r.getEvidenceDeadline(),
                r.getEvidenceSubmittedAt(),
                r.getRejectionReason(),
                r.getCancellationRef(),
                r.getRequestedAt(),
                r.getApprovedAt()
        );
    }
}
