package et.ut.einvoice.platform.identity.repository;

import et.ut.einvoice.platform.identity.domain.AccessReviewEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AccessReviewEntryRepository extends JpaRepository<AccessReviewEntry, UUID> {

    List<AccessReviewEntry> findByCampaignId(UUID campaignId);

    List<AccessReviewEntry> findByUserId(UUID userId);
}
