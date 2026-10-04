package et.ut.einvoice.audit.dto;

import et.ut.einvoice.audit.service.AuditChainVerifier;

public record AuditStreamVerificationDto(
        AuditChainVerifier.VerificationStatus status,
        boolean isValid,
        long verifiedEventCount,
        Long failedSequenceNumber,
        String errorMessage
) {
    public static AuditStreamVerificationDto fromResult(AuditChainVerifier.StreamVerificationResult result) {
        return new AuditStreamVerificationDto(
                result.status(),
                result.isValid(),
                result.verifiedEventCount(),
                result.failedSequenceNumber(),
                result.errorMessage()
        );
    }
}
