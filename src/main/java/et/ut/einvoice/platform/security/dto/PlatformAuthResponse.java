package et.ut.einvoice.platform.security.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record PlatformAuthResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        UUID userId,
        String username,
        String email,
        String fullName,
        String role,
        Set<String> scopes,
        Instant issuedAt
) {}
