package et.ut.einvoice.platform.security.dto;

import jakarta.validation.constraints.NotBlank;

public record PlatformLoginRequest(
        @NotBlank(message = "Username or Email is required")
        String username,

        @NotBlank(message = "Password is required")
        String password,

        String mfaCode
) {}
