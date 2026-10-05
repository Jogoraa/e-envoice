package et.ut.einvoice.compliance.dto;

import et.ut.einvoice.compliance.domain.AuthorityInvestigationExport;
import java.time.Instant;
import java.util.UUID;

public record AuthorityExportJobDto(
    UUID id,
    String caseReference,
    String reason,
    String requestedBy,
    UUID tenantId,
    String customerTin,
    String status,
    int recordCount,
    String payloadEncryptedBase64,
    String encryptionAlgorithm,
    String sha256Checksum,
    Instant completedAt,
    Instant createdAt
) {
    public static AuthorityExportJobDto fromEntity(AuthorityInvestigationExport e) {
        return new AuthorityExportJobDto(
                e.getId(),
                e.getCaseReference(),
                e.getReason(),
                e.getRequestedBy(),
                e.getTenantId(),
                e.getCustomerTin(),
                e.getStatus(),
                e.getRecordCount(),
                e.getPayloadEncryptedBase64(),
                e.getEncryptionAlgorithm(),
                e.getSha256Checksum(),
                e.getCompletedAt(),
                e.getCreatedAt()
        );
    }
}
