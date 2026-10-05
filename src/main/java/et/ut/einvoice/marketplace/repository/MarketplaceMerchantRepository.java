package et.ut.einvoice.marketplace.repository;

import et.ut.einvoice.marketplace.domain.MarketplaceMerchant;
import et.ut.einvoice.marketplace.domain.MerchantStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MarketplaceMerchantRepository extends JpaRepository<MarketplaceMerchant, UUID> {
    Optional<MarketplaceMerchant> findByMarketplaceTenantIdAndMerchantTin(UUID marketplaceTenantId, String merchantTin);
    List<MarketplaceMerchant> findByMarketplaceTenantId(UUID marketplaceTenantId);
    List<MarketplaceMerchant> findByMarketplaceTenantIdAndMerchantStatus(UUID marketplaceTenantId, MerchantStatus status);
}
