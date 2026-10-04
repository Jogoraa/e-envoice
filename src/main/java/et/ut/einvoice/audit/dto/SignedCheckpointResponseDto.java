package et.ut.einvoice.audit.dto;

import et.ut.einvoice.audit.domain.AuditCheckpoint;

public record SignedCheckpointResponseDto(
        AuditCheckpointResponseDto checkpoint,
        String signature,
        String keyId,
        String algorithm
) {
    public static SignedCheckpointResponseDto fromCheckpoint(
            AuditCheckpoint checkpoint,
            String signature,
            String keyId,
            String algorithm
    ) {
        return new SignedCheckpointResponseDto(
                AuditCheckpointResponseDto.fromEntity(checkpoint),
                signature,
                keyId,
                algorithm
        );
    }
}
