package et.ut.einvoice.platform.config.repository;

import et.ut.einvoice.platform.config.domain.ConfigurationRevisionEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ConfigurationRevisionEntryRepository extends JpaRepository<ConfigurationRevisionEntry, UUID> {

    List<ConfigurationRevisionEntry> findByRevisionIdOrderByKeyNameAsc(UUID revisionId);
}
