package et.ut.einvoice.audit.dto;

public record AuditEventVerificationDto(
        Long sequenceNumber,
        boolean isValid,
        String eventHash
) {
}
