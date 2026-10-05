package et.ut.einvoice.compliance;

import et.ut.einvoice.platform.context.TenantContext;
import et.ut.einvoice.platform.context.TenantContextHolder;
import et.ut.einvoice.taxpayer.domain.TaxpayerProfile;
import et.ut.einvoice.taxpayer.domain.TenantLifecycleEvent;
import et.ut.einvoice.taxpayer.domain.TenantLifecycleEventType;
import et.ut.einvoice.taxpayer.domain.TenantNotificationStatus;
import et.ut.einvoice.taxpayer.repository.TaxpayerProfileRepository;
import et.ut.einvoice.taxpayer.repository.TenantLifecycleEventRepository;
import et.ut.einvoice.taxpayer.service.TenantLifecycleNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class TenantLifecycleNotificationTestSuite {

    @Autowired
    private TenantLifecycleNotificationService lifecycleService;

    @Autowired
    private TenantLifecycleEventRepository eventRepository;

    @Autowired
    private TaxpayerProfileRepository profileRepository;

    private UUID tenantX;

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();
        profileRepository.deleteAll();
        tenantX = UUID.randomUUID();

        TaxpayerProfile profile = new TaxpayerProfile(
                tenantX, "0044556677", "VAT-44556", "Apex Industrial PLC", "Apex Tools",
                "Addis Ababa", "Nifas Silk", "0911556677", "apex@ut.et", "SYS-APEX-01", "POS"
        );
        profile.assignSector("SEC-05", true);
        profileRepository.save(profile);

        // Authenticate as PLATFORM_ADMIN
        TenantContextHolder.setContext(TenantContext.create(tenantX, "platform.admin", Set.of("ROLE_PLATFORM_ADMIN")));
        var auth = new UsernamePasswordAuthenticationToken(
                "platform.admin", "password", List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("Stage 5: Commencement Notification Emits Outbox Event and Transitions to SUBMITTED")
    void test_CommencementNotification_EmitsEventAndSubmits() {
        TenantLifecycleEvent event = lifecycleService.triggerCommencementNotification(tenantX);
        assertNotNull(event);
        assertEquals(tenantX, event.getTenantId());
        assertEquals(TenantLifecycleEventType.COMMENCEMENT, event.getEventType());
        assertEquals(TenantNotificationStatus.SUBMITTED, event.getNotificationStatus());
        assertEquals("0044556677", event.getTin());
        assertEquals("SYS-APEX-01", event.getSystemNumber());
        assertEquals("SEC-05", event.getSectorCode());
        assertEquals(1, event.getRetryCount());
    }

    @Test
    @DisplayName("Stage 5: Termination Notification Emits Outbox Event and Awaits Acknowledgment")
    void test_TerminationNotification_EmitsEventAndSubmits() {
        TenantLifecycleEvent event = lifecycleService.triggerTerminationNotification(tenantX);
        assertNotNull(event);
        assertEquals(tenantX, event.getTenantId());
        assertEquals(TenantLifecycleEventType.TERMINATION, event.getEventType());
        assertEquals(TenantNotificationStatus.SUBMITTED, event.getNotificationStatus());
        assertEquals(1, event.getRetryCount());
    }

    @Test
    @DisplayName("Stage 5: Recording Government Acknowledgement Transitions to ACKNOWLEDGED")
    void test_GovernmentAcknowledgement_Recorded() {
        TenantLifecycleEvent submitted = lifecycleService.triggerCommencementNotification(tenantX);
        assertEquals(TenantNotificationStatus.SUBMITTED, submitted.getNotificationStatus());

        TenantLifecycleEvent acked = lifecycleService.recordGovernmentAcknowledgement(
                submitted.getId(),
                "MOR-ACK-COMMENCE-998811",
                "{\"status\":\"SUCCESS\",\"registeredAt\":\"2026-10-05T09:00:00Z\"}"
        );

        assertNotNull(acked);
        assertEquals(TenantNotificationStatus.ACKNOWLEDGED, acked.getNotificationStatus());
        assertEquals("MOR-ACK-COMMENCE-998811", acked.getMorAcknowledgementReference());
        assertNotNull(acked.getAcknowledgedAt());
    }
}
