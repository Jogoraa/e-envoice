package et.ut.einvoice.platform.identity.repository;

import et.ut.einvoice.platform.identity.domain.AccessReviewCampaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AccessReviewCampaignRepository extends JpaRepository<AccessReviewCampaign, UUID> {

    List<AccessReviewCampaign> findByStatus(String status);
}
