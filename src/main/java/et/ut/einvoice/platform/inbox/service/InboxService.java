package et.ut.einvoice.platform.inbox.service;

import et.ut.einvoice.compliance.service.InsaDigitalSignatureService;
import et.ut.einvoice.platform.inbox.domain.InboxEvent;
import et.ut.einvoice.platform.inbox.repository.InboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class InboxService {

    private static final Logger log = LoggerFactory.getLogger(InboxService.class);

    private final InboxEventRepository repository;
    private final InsaDigitalSignatureService signatureService;

    public InboxService(InboxEventRepository repository, InsaDigitalSignatureService signatureService) {
        this.repository = repository;
        this.signatureService = signatureService;
    }

    /**
     * Checks if an inbound event has already been received. If not, records it.
     * Returns true if newly accepted, false if duplicate.
     */
    @Transactional
    public boolean acceptEvent(String source, String eventId, String eventType, String payloadJson) {
        if (repository.findBySourceAndEventId(source, eventId).isPresent()) {
            log.warn("Duplicate inbound event detected from {} [ID: {}]. Ignoring to prevent duplicate processing.", source, eventId);
            return false;
        }

        String payloadHash = signatureService.computeSha256Hash(payloadJson != null ? payloadJson : "");
        InboxEvent event = new InboxEvent(
                UUID.randomUUID(),
                source,
                eventId,
                eventType,
                payloadHash
        );
        repository.save(event);
        log.info("Accepted new inbound event from {} [ID: {}, Type: {}]", source, eventId, eventType);
        return true;
    }

    @Transactional
    public void markProcessed(String source, String eventId) {
        repository.findBySourceAndEventId(source, eventId).ifPresent(e -> {
            e.markProcessed();
            repository.save(e);
        });
    }
}
