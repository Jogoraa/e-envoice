package et.ut.einvoice.platform.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TenantLoginRequest(
        @NotBlank(message = "TIN is required")
        @Pattern(regexp = "^\\d{10}$", message = "TIN must contain exactly 10 digits")
        String tin,

        @NotBlank(message = "Username is required")
        @Size(max = 128)
        String username,

        @NotBlank(message = "Password is required")
        @Size(max = 128)
        String password,

        @Size(max = 128)
        String deviceSerial,
        @Size(max = 64)
        String branchCode
) {}
