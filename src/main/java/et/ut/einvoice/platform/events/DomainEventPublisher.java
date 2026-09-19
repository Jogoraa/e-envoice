package et.ut.einvoice.platform.events;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Encapsulated domain event publisher ensuring asynchronous and decoupled domain events.
 */
@Component
public class DomainEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public DomainEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void publish(Object event) {
        if (event != null) {
            eventPublisher.publishEvent(event);
        }
    }
}
