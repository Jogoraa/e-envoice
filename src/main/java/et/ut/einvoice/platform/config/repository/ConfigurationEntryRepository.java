package et.ut.einvoice.platform.config.repository;

import et.ut.einvoice.platform.config.domain.ConfigurationEntry;
import et.ut.einvoice.platform.config.domain.ConfigurationScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConfigurationEntryRepository extends JpaRepository<ConfigurationEntry, UUID> {

    Optional<ConfigurationEntry> findByKeyName(String keyName);

    List<ConfigurationEntry> findByScopeOrderByKeyNameAsc(ConfigurationScope scope);

    List<ConfigurationEntry> findAllByOrderByScopeAscKeyNameAsc();

    boolean existsByKeyName(String keyName);
}
