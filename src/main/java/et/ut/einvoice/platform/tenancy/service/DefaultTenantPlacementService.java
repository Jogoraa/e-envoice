package et.ut.einvoice.platform.tenancy.service;

import et.ut.einvoice.platform.tenancy.dto.TenantPlacement;
import et.ut.einvoice.tenancy.domain.Tenant;
import et.ut.einvoice.tenancy.repository.TenantRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class DefaultTenantPlacementService implements TenantPlacementService {

    private final TenantRepository tenantRepository;

    public DefaultTenantPlacementService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Override
    public TenantPlacement resolvePlacement(UUID tenantId) {
        Optional<Tenant> tenantOpt = tenantRepository.findById(tenantId);
        String shard = tenantOpt.map(Tenant::getDatabaseShard).orElse("shared_cluster");
        String cluster = "enterprise".equalsIgnoreCase(tenantOpt.map(Tenant::getTenantType).orElse("SME"))
                ? "enterprise_cluster_primary"
                : "shared_cluster_primary";

        return new TenantPlacement(
                tenantId,
                shard,
                cluster,
                "LOGICAL_PARTITIONED",
                "ACTIVE",
                1
        );
    }
}
