package et.ut.einvoice.platform.config.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class ConfigurationDtos {

    public record StepUpMfaRequest(
            @NotBlank(message = "Master password re-entry is required")
            String password,

            @NotBlank(message = "6-digit MFA verification code is required")
            String mfaCode
    ) {}

    public record StepUpMfaResponse(
            UUID sessionId,
            String privilegedToken,
            String username,
            Instant expiresAt,
            long durationSeconds,
            String message
    ) {}

    public record SendStepUpOtpRequest(
            String username,
            String phone
    ) {}

    public record SendStepUpOtpResponse(
            boolean dispatched,
            String maskedEmail,
            String maskedPhone,
            Instant expiresAt,
            int cooldownSeconds,
            String message
    ) {}

    public record ConfigurationUpdateRequest(
            long expectedRevisionNumber,
            Map<String, String> configurations,
            String changeSummary
    ) {}

    public record SecretRotationRequest(
            @NotBlank(message = "Secret key name is required")
            String keyName,

            @NotBlank(message = "New secret plaintext is required")
            String newSecret,

            String reason
    ) {}

    public record RollbackRequest(
            long targetRevisionNumber,
            String reason
    ) {}

    public record ConfigurationItemDto(
            UUID id,
            et.ut.einvoice.platform.config.domain.ConfigurationScope scope,
            String keyName,
            et.ut.einvoice.platform.config.domain.ConfigurationValueType valueType,
            et.ut.einvoice.platform.config.domain.ConfigurationClassification classification,
            String currentValue,
            boolean isSecret,
            boolean isConfigured,
            String maskedValue,
            String secretFingerprint,
            boolean runtimeMutable,
            boolean requiresRestart,
            String status,
            Long version,
            String description,
            java.util.List<String> allowedValues,
            Long minValue,
            Long maxValue,
            String updatedBy,
            Instant updatedAt
    ) {}

    public record ConfigurationHealthDto(
            String applicationStatus,
            String databaseStatus,
            String redisStatus,
            String eirsStatus,
            boolean eirsKillSwitchActive,
            String smsStatus,
            String smsProvider,
            boolean smsLiveBlocked,
            boolean smsKillSwitchActive,
            String emailStatus,
            boolean emailKillSwitchActive,
            String storageStatus,
            long currentRevision,
            Instant lastChangeTimestamp,
            String lastChangedBy
    ) {}

    public record RevisionSummaryDto(
            UUID id,
            Long revisionNumber,
            String createdBy,
            String changeSummary,
            Long rollbackFromRevision,
            String status,
            Instant createdAt,
            java.util.List<RevisionEntryDto> entries
    ) {}

    public record RevisionEntryDto(
            String keyName,
            String action,
            String oldValueClassification,
            String newValueClassification,
            String oldValueMasked,
            String newValueMasked
    ) {}

    public record ApplyResponse(
            String status,
            boolean restartRequired,
            String message,
            Instant appliedAt
    ) {}

    public record EmailDiagnosticStatusDto(
            String status,
            String providerName,
            String host,
            int port,
            String fromAddress,
            String usernameMasked,
            boolean deliveryEnabled,
            boolean isConfigured,
            String verifiedAdminEmail,
            String operationalMessage,
            Instant lastCheckedAt
    ) {}

    public record EmailTestSendResponse(
            boolean success,
            String status,
            String recipientMasked,
            String messageId,
            String message,
            Instant dispatchedAt
    ) {}

    public record SmsDiagnosticStatusDto(
            String status,
            String activeProvider,
            String endpointMasked,
            String senderId,
            boolean safetyLockActive,
            boolean deliveryEnabled,
            String verifiedAdminPhone,
            String operationalMessage,
            Instant lastCheckedAt
    ) {}

    public record SmsTestSendResponse(
            boolean success,
            String status,
            String recipientMasked,
            String providerMessageId,
            String message,
            Instant dispatchedAt
    ) {}
}
