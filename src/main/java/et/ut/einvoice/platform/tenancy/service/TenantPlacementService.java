package et.ut.einvoice.platform.tenancy.service;

import et.ut.einvoice.platform.tenancy.dto.TenantPlacement;

import java.util.UUID;

public interface TenantPlacementService {
    TenantPlacement resolvePlacement(UUID tenantId);
}
