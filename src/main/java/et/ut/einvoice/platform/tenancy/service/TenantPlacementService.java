package et.ut.einvoice.platform.tenancy.service;

import et.ut.einvoice.platform.tenancy.dto.TenantPlacement;
import et.ut.einvoice.tenancy.domain.Tenant;
import et.ut.einvoice.tenancy.repository.TenantRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

public interface TenantPlacementService {
    TenantPlacement resolvePlacement(UUID tenantId);
}
