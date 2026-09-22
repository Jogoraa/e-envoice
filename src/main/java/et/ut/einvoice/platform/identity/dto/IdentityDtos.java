package et.ut.einvoice.platform.identity.dto;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class IdentityDtos {

    // ==========================================
    // 1. Account Settings DTOs
    // ==========================================
    public record MasterProfileResponse(
            UUID id,
            String username,
            String email,
            String fullName,
            String primaryRole,
            List<String> roles,
            String phone,
            boolean phoneVerified,
            boolean emailVerified,
            boolean mfaEnabled,
            String timezone,
            String dateFormat,
            Instant lastLoginAt,
            Instant createdAt,
            Instant passwordChangedAt
    ) {}

    public record UpdateProfileRequest(
            String fullName,
            String timezone,
            String dateFormat
    ) {}

    public record InitiateEmailChangeRequest(
            String newEmail
    ) {}

    public record ConfirmEmailChangeRequest(
            String newEmail,
            String verificationCode
    ) {}

    public record InitiatePhoneVerificationRequest(
            String phone
    ) {}

    public record ConfirmPhoneVerificationRequest(
            String phone,
            String verificationCode
    ) {}

    public record ChangePasswordRequest(
            String currentPassword,
            String newPassword,
            String confirmPassword
    ) {}

    public record MfaSetupResponse(
            String secret,
            String otpAuthUri,
            List<String> backupCodes
    ) {}

    public record VerifyMfaSetupRequest(
            String code
    ) {}

    public record DisableMfaRequest(
            String password,
            String code
    ) {}

    public record RegenerateRecoveryCodesResponse(
            List<String> recoveryCodes
    ) {}

    // ==========================================
    // 2. User Lifecycle & Directory DTOs
    // ==========================================
    public record PlatformUserSummaryDto(
            UUID id,
            String username,
            String email,
            String phone,
            String fullName,
            String primaryRole,
            List<String> roles,
            String status,
            boolean mfaEnabled,
            Instant lastLoginAt,
            Instant createdAt
    ) {}

    public record InviteAdminRequest(
            String email,
            String phone,
            String fullName,
            String initialRoleCode,
            UUID tenantId
    ) {}

    public record UpdateUserStatusRequest(
            String status,
            String reason
    ) {}

    public record UpdateUserRolesRequest(
            List<String> roleCodes
    ) {}

    public record InvitationSummaryDto(
            UUID id,
            String email,
            String phone,
            String fullName,
            String initialRoleCode,
            UUID tenantId,
            Instant expiresAt,
            String status,
            Instant createdAt
    ) {}

    // ==========================================
    // 3. RBAC & Effective Access DTOs
    // ==========================================
    public record SystemPermissionDto(
            UUID id,
            String code,
            String name,
            String description,
            String category,
            String riskLevel
    ) {}

    public record SystemRoleDto(
            UUID id,
            String code,
            String name,
            String description,
            String scope,
            boolean isSystem,
            String status,
            int permissionsCount,
            List<SystemPermissionDto> permissions
    ) {}

    public record CreateRoleRequest(
            String code,
            String name,
            String description,
            String scope,
            List<String> permissionCodes
    ) {}

    public record UpdateRoleRequest(
            String name,
            String description,
            List<String> permissionCodes
    ) {}

    public record EffectiveAccessDto(
            UUID userId,
            String username,
            List<String> assignedRoles,
            Set<String> effectivePermissions,
            Set<String> prohibitedPermissions,
            Instant accessCalculatedAt
    ) {}

    // ==========================================
    // 4. Session & Device DTOs
    // ==========================================
    public record PlatformSessionDto(
            UUID id,
            UUID userId,
            String username,
            String ipAddress,
            String userAgent,
            String deviceSummary,
            boolean mfaAuthenticated,
            Instant createdAt,
            Instant lastSeenAt,
            boolean isCurrent
    ) {}

    public record RevokeSessionRequest(
            String reason
    ) {}

    // ==========================================
    // 5. Access Review DTOs
    // ==========================================
    public record AccessReviewCampaignDto(
            UUID id,
            String title,
            String description,
            String initiatedBy,
            String status,
            Instant createdAt,
            Instant completedAt,
            int totalEntries,
            int reviewedEntries
    ) {}

    public record AccessReviewEntryDto(
            UUID id,
            UUID campaignId,
            UUID userId,
            String username,
            String fullName,
            String currentRoles,
            String effectivePermissionsSummary,
            String decision,
            String notes,
            Instant reviewedAt,
            String reviewerId
    ) {}

    public record CreateReviewCampaignRequest(
            String title,
            String description
    ) {}

    public record SubmitReviewDecisionRequest(
            String decision,
            String notes
    ) {}
}
