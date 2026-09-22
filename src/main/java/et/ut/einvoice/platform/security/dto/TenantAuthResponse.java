package et.ut.einvoice.platform.security.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record TenantAuthResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        UUID tenantId,
        String tin,
        String legalName,
        String tradeName,
        String username,
        String fullName,
        String role,
        Set<String> scopes,
        Instant issuedAt
) {}
