package et.ut.einvoice.compliance.repository;

import et.ut.einvoice.compliance.domain.SoftwareBuildChecksum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SoftwareBuildChecksumRepository extends JpaRepository<SoftwareBuildChecksum, UUID> {
    Optional<SoftwareBuildChecksum> findTopByActiveReleaseTrueOrderByBuildTimestampDesc();
    Optional<SoftwareBuildChecksum> findByVersion(String version);
}
