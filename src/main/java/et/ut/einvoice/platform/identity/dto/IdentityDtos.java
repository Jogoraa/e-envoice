package et.ut.einvoice.platform.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
            @Size(max = 128)
            String fullName,
            @Size(max = 64)
            String timezone,
            @Size(max = 32)
            String dateFormat
    ) {
        @jakarta.validation.constraints.AssertTrue(message = "At least one profile field must be provided")
        public boolean hasUpdate() {
            return (fullName != null && !fullName.isBlank())
                    || (timezone != null && !timezone.isBlank())
                    || (dateFormat != null && !dateFormat.isBlank());
        }
    }

    public record InitiateEmailChangeRequest(
            @NotBlank(message = "New email is required")
            @Email(message = "New email must be a valid address")
            @Size(max = 128)
            String newEmail
    ) {}

    public record ConfirmEmailChangeRequest(
            @NotBlank(message = "New email is required")
            @Email(message = "New email must be a valid address")
            @Size(max = 128)
            String newEmail,
            @NotBlank(message = "Verification code is required")
            @Pattern(regexp = "^\\d{6}$", message = "Verification code must contain six digits")
            String verificationCode
    ) {}

    public record InitiatePhoneVerificationRequest(
            @NotBlank(message = "Phone number is required")
            @Pattern(regexp = "^(\\+251|0)(9|7)\\d{8}$|^(\\+251|0)[1-5]\\d{7,8}$", message = "Invalid Ethiopian phone number")
            String phone
    ) {}

    public record ConfirmPhoneVerificationRequest(
            @NotBlank(message = "Phone number is required")
            @Pattern(regexp = "^(\\+251|0)(9|7)\\d{8}$|^(\\+251|0)[1-5]\\d{7,8}$", message = "Invalid Ethiopian phone number")
            String phone,
            @NotBlank(message = "Verification code is required")
            @Pattern(regexp = "^\\d{6}$", message = "Verification code must contain six digits")
            String verificationCode
    ) {}

    public record ChangePasswordRequest(
            @NotBlank(message = "Current password is required")
            @Size(max = 128)
            String currentPassword,
            @NotBlank(message = "New password is required")
            @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{12,128}$", message = "New password must meet password complexity requirements")
            String newPassword,
            @NotBlank(message = "Password confirmation is required")
            @Size(max = 128)
            String confirmPassword
    ) {}

    public record MfaSetupResponse(
            String secret,
            String otpAuthUri,
            List<String> backupCodes
    ) {}

    public record VerifyMfaSetupRequest(
            @NotBlank(message = "MFA code is required")
            @Pattern(regexp = "^\\d{6}$", message = "MFA code must contain six digits")
            String code
    ) {}

    public record DisableMfaRequest(
            @NotBlank(message = "Password is required")
            @Size(max = 128)
            String password,
            @NotBlank(message = "MFA code is required")
            @Pattern(regexp = "^\\d{6}$", message = "MFA code must contain six digits")
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
            String fullName,
            String primaryRole,
            List<String> roles,
            String status,
            boolean mfaEnabled,
            Instant lastLoginAt,
            Instant createdAt
    ) {}

    public record InviteAdminRequest(
            @NotBlank(message = "Email is required")
            @Pattern(regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", message = "Email must be a valid address")
            @Size(max = 128)
            String email,
            @Pattern(regexp = "^$|^(\\+251|0)(9|7)\\d{8}$|^(\\+251|0)[1-5]\\d{7,8}$", message = "Invalid Ethiopian phone number")
            String phone,
            @NotBlank(message = "Full name is required")
            @Size(max = 128)
            String fullName,
            @NotBlank(message = "Initial role code is required")
            @Pattern(regexp = "^ROLE_[A-Z0-9_]{2,64}$", message = "Initial role code is invalid")
            String initialRoleCode,
            UUID tenantId
    ) {}

    public record UpdateUserStatusRequest(
            @NotBlank(message = "Status is required")
            @Pattern(regexp = "^(ACTIVE|SUSPENDED|DISABLED)$", message = "Status must be ACTIVE, SUSPENDED, or DISABLED")
            String status,
            @Size(max = 500)
            String reason
    ) {}

    public record UpdateUserRolesRequest(
            @NotEmpty(message = "At least one role code is required")
            @Size(max = 20)
            List<String> roleCodes
    ) {}

    public record InvitationSummaryDto(
            UUID id,
            String email,
            String fullName,
            String initialRoleCode,
            UUID tenantId,
            Instant expiresAt,
            String status,
            String emailDeliveryStatus,
            String smsDeliveryStatus,
            Instant lastDeliveryAttemptAt,
            int resendCount,
            Instant createdAt
    ) {}

    public record InvitationValidationDto(
            boolean valid,
            String state,
            String email,
            String displayName,
            String role,
            String organization,
            Instant expiresAt,
            boolean requiresMfa
    ) {}

    public record AcceptInvitationRequest(
            @NotBlank(message = "Invitation token is required")
            @Size(max = 256)
            String token,
            @NotBlank(message = "Password is required")
            @Size(max = 128)
            String password,
            @NotBlank(message = "Password confirmation is required")
            @Size(max = 128)
            String confirmPassword
    ) {}

    public record InvitationAcceptanceResponse(
            boolean success,
            boolean requiresMfa,
            String loginPath
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
            @NotBlank(message = "Role code is required")
            @Pattern(regexp = "^ROLE_[A-Z0-9_]{2,64}$", message = "Role code is invalid")
            String code,
            @NotBlank(message = "Role name is required")
            @Size(max = 128)
            String name,
            @Size(max = 1000)
            String description,
            @Pattern(regexp = "^$|^(PLATFORM|TENANT)$", message = "Role scope must be PLATFORM or TENANT")
            String scope,
            @Size(max = 100)
            List<String> permissionCodes
    ) {}

    public record UpdateRoleRequest(
            @Size(max = 128)
            String name,
            @Size(max = 1000)
            String description,
            @Size(max = 100)
            List<String> permissionCodes
    ) {
        @jakarta.validation.constraints.AssertTrue(message = "At least one role field must be provided")
        public boolean hasUpdate() {
            return (name != null && !name.isBlank()) || description != null || permissionCodes != null;
        }
    }

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
            @Size(max = 500)
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
            @NotBlank(message = "Campaign title is required")
            @Size(max = 255)
            String title,
            @Size(max = 2000)
            String description
    ) {}

    public record SubmitReviewDecisionRequest(
            @NotBlank(message = "Review decision is required")
            @Pattern(regexp = "^(APPROVED|MODIFIED|REVOKED|SUSPENDED)$", message = "Review decision is invalid")
            String decision,
            @Size(max = 2000)
            String notes
    ) {}
}
