package et.ut.einvoice.tenancy.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "api_clients")
public class ApiClient {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "client_id", nullable = false, unique = true, length = 64)
    private String clientId;

    @Column(name = "client_secret_hash", nullable = false)
    private String clientSecretHash;

    @Column(name = "client_name", nullable = false, length = 128)
    private String clientName;

    @Column(name = "client_type", nullable = false, length = 32)
    private String clientType = "EXTERNAL_ERP";

    @Column(name = "scopes", nullable = false, length = 512)
    private String scopes = "invoice:read invoice:create";

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    public ApiClient() {}

    public ApiClient(UUID id, UUID tenantId, String clientId, String clientSecretHash, String clientName, String scopes) {
        this.id = id;
        this.tenantId = tenantId;
        this.clientId = clientId;
        this.clientSecretHash = clientSecretHash;
        this.clientName = clientName;
        this.scopes = scopes != null ? scopes : "invoice:read invoice:create";
        this.status = "ACTIVE";
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getClientId() { return clientId; }
    public String getClientSecretHash() { return clientSecretHash; }
    public String getClientName() { return clientName; }
    public String getClientType() { return clientType; }
    public String getScopes() { return scopes; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public void suspend() { this.status = "SUSPENDED"; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }

    public Set<String> getScopeSet() {
        if (scopes == null || scopes.isBlank()) return Collections.emptySet();
        return Arrays.stream(scopes.split("[,\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    public void updateLastUsed() {
        this.lastUsedAt = Instant.now();
    }
}
