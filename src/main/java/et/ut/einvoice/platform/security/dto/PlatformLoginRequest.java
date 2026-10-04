package et.ut.einvoice.platform.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PlatformLoginRequest(
        @NotBlank(message = "Username or Email is required")
        @Size(max = 128)
        String username,

        @NotBlank(message = "Password is required")
        @Size(max = 128)
        String password,

        @Pattern(regexp = "^$|^\\d{6}$", message = "MFA code must contain six digits")
        String mfaCode
) {}
