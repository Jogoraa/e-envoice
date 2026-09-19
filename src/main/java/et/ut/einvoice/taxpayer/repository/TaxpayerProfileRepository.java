package et.ut.einvoice.taxpayer.repository;

import et.ut.einvoice.taxpayer.domain.TaxpayerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaxpayerProfileRepository extends JpaRepository<TaxpayerProfile, UUID> {
    Optional<TaxpayerProfile> findByTin(String tin);
}
