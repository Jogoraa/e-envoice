package et.ut.einvoice.compliance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public class RecordMigrationRequestDto {

    @NotNull(message = "Tenant ID is required")
    private UUID tenantId;

    @NotBlank(message = "Destination provider name is required")
    private String destinationProviderName;

    private String destinationSystemNumber;

    @NotBlank(message = "Migration evidence hash is required")
    private String migrationEvidenceHash;

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getDestinationProviderName() { return destinationProviderName; }
    public void setDestinationProviderName(String destinationProviderName) { this.destinationProviderName = destinationProviderName; }
    public String getDestinationSystemNumber() { return destinationSystemNumber; }
    public void setDestinationSystemNumber(String destinationSystemNumber) { this.destinationSystemNumber = destinationSystemNumber; }
    public String getMigrationEvidenceHash() { return migrationEvidenceHash; }
    public void setMigrationEvidenceHash(String migrationEvidenceHash) { this.migrationEvidenceHash = migrationEvidenceHash; }
}
