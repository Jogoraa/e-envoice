package et.ut.einvoice.platform.context;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable trusted tenant execution context established by security filters.
 */
public record TenantContext(
        UUID tenantId,
        String organizationId,
        UUID branchId,
        String userId,
        String clientId,
        Set<String> roles,
        Set<String> scopes,
        UUID deviceId,
        String correlationId
) {
    public static TenantContext create(UUID tenantId, String userId, Set<String> roles) {
        return new TenantContext(
                tenantId,
                tenantId.toString(),
                null,
                userId,
                "DEFAULT_CLIENT",
                roles != null ? Set.copyOf(roles) : Collections.emptySet(),
                Collections.emptySet(),
                null,
                UUID.randomUUID().toString()
        );
    }

    public static TenantContext createWithClient(UUID tenantId, String clientId, Set<String> roles, Set<String> scopes, String correlationId) {
        return new TenantContext(
                tenantId,
                tenantId.toString(),
                null,
                "api-client-" + clientId,
                clientId != null ? clientId : "DEFAULT_CLIENT",
                roles != null ? Set.copyOf(roles) : Collections.emptySet(),
                scopes != null ? Set.copyOf(scopes) : Collections.emptySet(),
                null,
                correlationId != null ? correlationId : UUID.randomUUID().toString()
        );
    }
}
