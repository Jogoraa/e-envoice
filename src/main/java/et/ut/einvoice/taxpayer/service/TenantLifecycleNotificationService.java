package et.ut.einvoice.taxpayer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import et.ut.einvoice.audit.service.AuditService;
import et.ut.einvoice.platform.exception.BusinessException;
import et.ut.einvoice.platform.outbox.service.OutboxService;
import et.ut.einvoice.taxpayer.domain.TaxpayerProfile;
import et.ut.einvoice.taxpayer.domain.TenantLifecycleEvent;
import et.ut.einvoice.taxpayer.domain.TenantLifecycleEventType;
import et.ut.einvoice.taxpayer.domain.TenantNotificationStatus;
import et.ut.einvoice.taxpayer.repository.TaxpayerProfileRepository;
import et.ut.einvoice.taxpayer.repository.TenantLifecycleEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TenantLifecycleNotificationService {

    private static final Logger log = LoggerFactory.getLogger(TenantLifecycleNotificationService.class);

    private final TenantLifecycleEventRepository eventRepository;
    private final TaxpayerProfileRepository profileRepository;
    private final OutboxService outboxService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public TenantLifecycleNotificationService(
            TenantLifecycleEventRepository eventRepository,
            TaxpayerProfileRepository profileRepository,
            OutboxService outboxService,
            AuditService auditService,
            ObjectMapper objectMapper
    ) {
        this.eventRepository = eventRepository;
        this.profileRepository = profileRepository;
        this.outboxService = outboxService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    @PreAuthorize("hasAuthority('ROLE_PLATFORM_ADMIN')")
    public TenantLifecycleEvent triggerCommencementNotification(UUID tenantId) {
        TaxpayerProfile profile = profileRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new BusinessException("TAXPAYER_PROFILE_NOT_FOUND", "Profile not found for tenant " + tenantId));

        TenantLifecycleEvent event = eventRepository.findByTenantIdAndEventType(tenantId, TenantLifecycleEventType.COMMENCEMENT)
                .orElse(new TenantLifecycleEvent(
                        UUID.randomUUID(),
                        tenantId,
                        TenantLifecycleEventType.COMMENCEMENT,
                        profile.getTin(),
                        profile.getLegalName(),
                        profile.getSystemNumber(),
                        profile.getSectorCode(),
                        Instant.now()
                ));

        event.markSubmitted();
        TenantLifecycleEvent saved = eventRepository.save(event);

        // Enqueue outbox event for government transmission
        String payloadJson = serializeLifecyclePayload(saved);
        outboxService.enqueueEvent(
                tenantId,
                "TENANT_LIFECYCLE",
                saved.getId().toString(),
                "GOVERNMENT_COMMENCEMENT_NOTIFICATION",
                payloadJson
        );

        auditService.recordEvent(
                tenantId,
                "SYSTEM",
                "ADMIN",
                "COMMENCEMENT_SUBMITTED",
                "TENANT_LIFECYCLE",
                saved.getId().toString(),
                "TIN=" + profile.getTin() + ", SYS=" + profile.getSystemNumber(),
                "127.0.0.1"
        );

        log.info("Triggered statutory commencement notification for tenant {} (TIN: {})", tenantId, profile.getTin());
        return saved;
    }

    @Transactional
    @PreAuthorize("hasAuthority('ROLE_PLATFORM_ADMIN')")
    public TenantLifecycleEvent triggerTerminationNotification(UUID tenantId) {
        TaxpayerProfile profile = profileRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new BusinessException("TAXPAYER_PROFILE_NOT_FOUND", "Profile not found for tenant " + tenantId));

        TenantLifecycleEvent event = eventRepository.findByTenantIdAndEventType(tenantId, TenantLifecycleEventType.TERMINATION)
                .orElse(new TenantLifecycleEvent(
                        UUID.randomUUID(),
                        tenantId,
                        TenantLifecycleEventType.TERMINATION,
                        profile.getTin(),
                        profile.getLegalName(),
                        profile.getSystemNumber(),
                        profile.getSectorCode(),
                        Instant.now()
                ));

        event.markSubmitted();
        TenantLifecycleEvent saved = eventRepository.save(event);

        // Enqueue outbox event for government transmission
        String payloadJson = serializeLifecyclePayload(saved);
        outboxService.enqueueEvent(
                tenantId,
                "TENANT_LIFECYCLE",
                saved.getId().toString(),
                "GOVERNMENT_TERMINATION_NOTIFICATION",
                payloadJson
        );

        auditService.recordEvent(
                tenantId,
                "SYSTEM",
                "ADMIN",
                "TERMINATION_SUBMITTED",
                "TENANT_LIFECYCLE",
                saved.getId().toString(),
                "TIN=" + profile.getTin() + ", SYS=" + profile.getSystemNumber(),
                "127.0.0.1"
        );

        log.info("Triggered statutory termination notification for tenant {} (TIN: {})", tenantId, profile.getTin());
        return saved;
    }

    @Transactional
    public TenantLifecycleEvent recordGovernmentAcknowledgement(UUID eventId, String ackReference, String responsePayload) {
        TenantLifecycleEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException("EVENT_NOT_FOUND", "Lifecycle event not found: " + eventId));

        event.markAcknowledged(ackReference, responsePayload);
        TenantLifecycleEvent saved = eventRepository.save(event);

        auditService.recordEvent(
                event.getTenantId(),
                "SYSTEM",
                "MOR_GATEWAY",
                "LIFECYCLE_ACKNOWLEDGED",
                "TENANT_LIFECYCLE",
                saved.getId().toString(),
                "TYPE=" + event.getEventType() + ", ACK_REF=" + ackReference,
                "127.0.0.1"
        );

        log.info("Recorded MoR acknowledgement for lifecycle event {} (Type: {}, AckRef: {})",
                eventId, event.getEventType(), ackReference);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<TenantLifecycleEvent> getEventsForTenant(UUID tenantId) {
        return eventRepository.findByTenantId(tenantId);
    }

    private String serializeLifecyclePayload(TenantLifecycleEvent event) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "eventId", event.getId().toString(),
                    "tenantId", event.getTenantId().toString(),
                    "eventType", event.getEventType().name(),
                    "tin", event.getTin(),
                    "taxpayerName", event.getTaxpayerName(),
                    "systemNumber", event.getSystemNumber(),
                    "sectorCode", event.getSectorCode() != null ? event.getSectorCode() : "",
                    "effectiveDate", event.getEffectiveDate().toString()
            ));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize lifecycle payload", ex);
        }
    }
}
