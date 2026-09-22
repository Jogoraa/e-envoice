package et.ut.einvoice.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import et.ut.einvoice.audit.service.AuditService;
import et.ut.einvoice.tenancy.domain.DelegatedTenantSessionEntity;
import et.ut.einvoice.tenancy.domain.Tenant;
import et.ut.einvoice.tenancy.repository.DelegatedTenantSessionRepository;
import et.ut.einvoice.tenancy.repository.TenantRepository;
import et.ut.einvoice.tenancy.service.DelegatedTenantSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class MultiInstanceDelegatedSessionRevocationTest {

    private DelegatedTenantSessionRepository sharedDatabaseSessionRepository;
    private TenantRepository sharedTenantRepository;
    private JwtTokenService sharedJwtTokenService;
    private AuditService auditService;

    private DelegatedTenantSessionService instanceA_Service;
    private DelegatedTenantSessionService instanceB_Service;

    @BeforeEach
    void setUp() {
        sharedDatabaseSessionRepository = mock(DelegatedTenantSessionRepository.class);
        sharedTenantRepository = mock(TenantRepository.class);
        auditService = mock(AuditService.class);
        sharedJwtTokenService = new JwtTokenService(
                "cluster-secret-key-at-least-256-bits-long-secure-and-robust!",
                "ut-einvoice-platform",
                "ut-invoice-tenant",
                new ObjectMapper()
        );

        // Instance A and Instance B are two distinct service nodes sharing the persistent database repository
        instanceA_Service = new DelegatedTenantSessionService(
                sharedJwtTokenService,
                sharedTenantRepository,
                sharedDatabaseSessionRepository,
                auditService
        );

        instanceB_Service = new DelegatedTenantSessionService(
                sharedJwtTokenService,
                sharedTenantRepository,
                sharedDatabaseSessionRepository,
                auditService
        );
    }

    @Test
    @DisplayName("Cluster-wide Revocation: Session issued by Instance A and revoked by Instance A is immediately rejected by Instance B")
    void testMultiInstanceRevocationConsistency() {
        UUID targetTenantId = UUID.randomUUID();
        UUID targetBranchId = UUID.randomUUID();
        String masterUserId = "platform.admin";

        Tenant tenant = new Tenant(targetTenantId, "ORG-DEMO", "Alpha Logistics PLC", "Alpha Store", "0011223344", "SME");
        tenant.activate();

        when(sharedTenantRepository.findById(targetTenantId)).thenReturn(Optional.of(tenant));

        // 1. Instance A issues delegated session
        var result = instanceA_Service.requestSupportSession(
                masterUserId,
                targetTenantId,
                targetBranchId,
                "TESTING",
                "Customer invoice layout troubleshooting",
                1800
        );

        UUID sessionId = result.sessionId();
        assertNotNull(sessionId);
        assertNotNull(result.token());

        // Construct mock active DB record representing shared persistent database state
        DelegatedTenantSessionEntity dbSession = new DelegatedTenantSessionEntity(
                sessionId,
                masterUserId,
                targetTenantId,
                targetBranchId,
                "TESTING",
                "Customer invoice layout troubleshooting",
                Instant.now(),
                Instant.now().plusSeconds(1800)
        );

        when(sharedDatabaseSessionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(dbSession));
        when(sharedDatabaseSessionRepository.existsBySessionIdAndIsRevokedFalseAndExpiresAtAfter(eq(sessionId), any(Instant.class)))
                .thenAnswer(inv -> !dbSession.isRevoked() && !dbSession.isExpired());

        // 2. Instance B verifies the session is active via shared DB state
        assertTrue(instanceB_Service.isSessionActive(sessionId), "Instance B must recognize the active session issued by Instance A");

        // Validate token claims on Instance B
        var claimsOpt = sharedJwtTokenService.validateAndExtract(result.token());
        assertTrue(claimsOpt.isPresent());
        assertTrue(claimsOpt.get().isDelegated());
        assertEquals(sessionId.toString(), claimsOpt.get().sessionId());
        assertEquals(targetTenantId, claimsOpt.get().tenantId());

        // 3. Instance A terminates/revokes the support session
        instanceA_Service.terminateSupportSession(sessionId, masterUserId);

        // Update DB session mock state
        dbSession.setRevoked(true);
        dbSession.setRevokedAt(Instant.now());
        dbSession.setRevokedBy(masterUserId);

        // 4. Instance B immediately rejects the revoked session
        assertFalse(instanceB_Service.isSessionActive(sessionId), "Instance B must reject the session revoked by Instance A");
    }
}
