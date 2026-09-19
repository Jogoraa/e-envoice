package et.ut.einvoice.platform.tenancy.dto;

import java.util.UUID;

public record TenantPlacement(
        UUID tenantId,
        String shardId,
        String logicalCluster,
        String schemaStrategy,
        String status,
        int placementVersion
) {}
