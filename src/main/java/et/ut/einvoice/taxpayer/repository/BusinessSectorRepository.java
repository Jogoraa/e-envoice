package et.ut.einvoice.taxpayer.repository;

import et.ut.einvoice.taxpayer.domain.BusinessSector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BusinessSectorRepository extends JpaRepository<BusinessSector, String> {
    List<BusinessSector> findByActiveTrue();
    List<BusinessSector> findByMandatoryOfflineContinuityTrue();
}
