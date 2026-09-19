package et.ut.einvoice.platform.inbox.repository;

import et.ut.einvoice.platform.inbox.domain.InboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InboxEventRepository extends JpaRepository<InboxEvent, UUID> {
    Optional<InboxEvent> findBySourceAndEventId(String source, String eventId);
}
