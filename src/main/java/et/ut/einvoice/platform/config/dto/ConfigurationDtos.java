package et.ut.einvoice.platform.config.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class ConfigurationDtos {

    public record StepUpMfaRequest(
            @NotBlank(message = "Master password re-entry is required")
            String password,

            @NotBlank(message = "6-digit MFA verification code is required")
            @jakarta.validation.constraints.Pattern(regexp = "^\\d{6}$", message = "MFA verification code must contain six digits")
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
            @Size(max = 128)
            String username,
            @jakarta.validation.constraints.Pattern(regexp = "^$|^(\\+251|0)(9|7)\\d{8}$|^(\\+251|0)[1-5]\\d{7,8}$", message = "Invalid Ethiopian phone number")
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
            @NotNull(message = "Expected revision number is required")
            @PositiveOrZero(message = "Expected revision number cannot be negative")
            Long expectedRevisionNumber,
            @NotEmpty(message = "At least one configuration value is required")
            @Size(max = 100, message = "No more than 100 configuration values may be changed at once")
            Map<@NotBlank @Size(max = 128) String, @NotBlank @Size(max = 20_000) String> configurations,
            @Size(max = 1000)
            String changeSummary
    ) {}

    public record SecretRotationRequest(
            @NotBlank(message = "Secret key name is required")
            @Size(max = 128)
            String keyName,

            @NotBlank(message = "New secret plaintext is required")
            @Size(max = 20_000)
            String newSecret,

            @Size(max = 1000)
            String reason
    ) {}

    public record RollbackRequest(
            @Positive(message = "Target revision number must be positive")
            long targetRevisionNumber,
            @Size(max = 1000)
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

    /** Mutation result without database ids or revision-entry values. */
    public record ConfigurationRevisionResultDto(
            Long revisionNumber,
            Long rollbackFromRevision,
            String status,
            Instant createdAt
    ) {
        public Long getRevisionNumber() {
            return revisionNumber;
        }

        public Long getRollbackFromRevision() {
            return rollbackFromRevision;
        }
    }

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
