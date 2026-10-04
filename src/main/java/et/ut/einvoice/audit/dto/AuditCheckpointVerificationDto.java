package et.ut.einvoice.audit.dto;

public record AuditCheckpointVerificationDto(
        String streamId,
        boolean isValid
) {
}
