package et.ut.einvoice.platform.config.repository;

import et.ut.einvoice.platform.config.domain.ConfigurationRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConfigurationRevisionRepository extends JpaRepository<ConfigurationRevision, UUID> {

    Optional<ConfigurationRevision> findByRevisionNumber(Long revisionNumber);

    List<ConfigurationRevision> findAllByOrderByRevisionNumberDesc();

    @Query("SELECT COALESCE(MAX(r.revisionNumber), 0) FROM ConfigurationRevision r")
    long findMaxRevisionNumber();
}
