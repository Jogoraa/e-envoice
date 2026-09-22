package et.ut.einvoice.platform.security.dto;

import jakarta.validation.constraints.NotBlank;

public record TenantLoginRequest(
        @NotBlank(message = "TIN is required")
        String tin,

        @NotBlank(message = "Username is required")
        String username,

        @NotBlank(message = "Password is required")
        String password,

        String deviceSerial,
        String branchCode
) {}
