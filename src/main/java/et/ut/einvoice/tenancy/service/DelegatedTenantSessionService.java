package et.ut.einvoice.tenancy.service;

import et.ut.einvoice.audit.domain.AuditAction;
import et.ut.einvoice.audit.service.AuditService;
import et.ut.einvoice.platform.exception.BusinessException;
import et.ut.einvoice.platform.security.JwtTokenService;
import et.ut.einvoice.tenancy.domain.DelegatedTenantSessionEntity;
import et.ut.einvoice.tenancy.domain.Tenant;
import et.ut.einvoice.tenancy.domain.TenantStatus;
import et.ut.einvoice.tenancy.repository.DelegatedTenantSessionRepository;
import et.ut.einvoice.tenancy.repository.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Service managing Controlled "Test as Tenant" / Delegated Support Sessions.
 * Backed by distributed, database-persisted session storage AND Redis distributed revocation registry
 * to guarantee cluster-wide consistency and multi-instance revocation across all application nodes.
 */
@Service
public class DelegatedTenantSessionService {

    private static final Logger log = LoggerFactory.getLogger(DelegatedTenantSessionService.class);

    private final JwtTokenService jwtTokenService;
    private final TenantRepository tenantRepository;
    private final DelegatedTenantSessionRepository sessionRepository;
    private final AuditService auditService;
    private final StringRedisTemplate redisTemplate;

    public DelegatedTenantSessionService(
            JwtTokenService jwtTokenService,
            TenantRepository tenantRepository,
            DelegatedTenantSessionRepository sessionRepository,
            AuditService auditService
    ) {
        this(jwtTokenService, tenantRepository, sessionRepository, auditService, null);
    }

    @Autowired
    public DelegatedTenantSessionService(
            JwtTokenService jwtTokenService,
            TenantRepository tenantRepository,
            DelegatedTenantSessionRepository sessionRepository,
            AuditService auditService,
            @Autowired(required = false) StringRedisTemplate redisTemplate
    ) {
        this.jwtTokenService = jwtTokenService;
        this.tenantRepository = tenantRepository;
        this.sessionRepository = sessionRepository;
        this.auditService = auditService;
        this.redisTemplate = redisTemplate;
    }

    public record DelegatedSessionResult(
            UUID sessionId,
            String token,
            UUID targetTenantId,
            String tenantLegalName,
            String tenantTin,
            UUID targetBranchId,
            String accessType,
            Instant issuedAt,
            Instant expiresAt,
            String masterUserId
    ) {}

    @Transactional
    public DelegatedSessionResult requestSupportSession(
            String masterUserId,
            UUID targetTenantId,
            UUID targetBranchId,
            String requestedAccessType,
            String reason,
            long ttlSeconds
    ) {
        if (masterUserId == null || masterUserId.isBlank()) {
            throw new BusinessException("UNAUTHORIZED_MASTER", "Master operator identity is required", "የኦፕሬተር መለያ አልቀረበም", HttpStatus.UNAUTHORIZED);
        }

        // 1. Target Tenant Verification
        Tenant tenant = tenantRepository.findById(targetTenantId)
                .orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND", "Target tenant does not exist: " + targetTenantId, "ድርጅቱ አልተገኘም", HttpStatus.NOT_FOUND));

        if (tenant.getStatus() == TenantStatus.ARCHIVED) {
            throw new BusinessException("TENANT_ARCHIVED", "Cannot enter archived tenant environment", "የተሰረዘ ድርጅት አካባቢ መግባት አይቻልም", HttpStatus.FORBIDDEN);
        }

        String accessType = "READ_ONLY_SUPPORT".equalsIgnoreCase(requestedAccessType) ? "READ_ONLY_SUPPORT" : "TESTING";
        UUID sessionId = UUID.randomUUID();
        long effectiveTtl = (ttlSeconds > 0 && ttlSeconds <= 7200) ? ttlSeconds : 1800; // default 30 mins
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(effectiveTtl);

        // 2. Roles & Scopes for Delegated Context
        Set<String> roles = new HashSet<>();
        roles.add("ROLE_DELEGATED_OPERATOR");
        roles.add("ROLE_TENANT_USER");

        Set<String> scopes = new HashSet<>();
        scopes.add("invoice:read");
        scopes.add("customer:read");
        scopes.add("catalog:read");
        scopes.add("report:read");

        if ("TESTING".equalsIgnoreCase(accessType)) {
            roles.add("ROLE_TENANT_ADMIN");
            scopes.add("invoice:create");
            scopes.add("invoice:adjust");
            scopes.add("invoice:cancel");
            scopes.add("customer:create");
            scopes.add("customer:update");
        }

        // 3. Issue Cryptographically Scoped Delegated JWT
        String token = jwtTokenService.generateDelegatedTenantToken(
                masterUserId,
                targetTenantId,
                accessType,
                sessionId.toString(),
                reason,
                roles,
                scopes,
                effectiveTtl
        );

        // 4. Register Active Session in Database (Cluster-wide state)
        DelegatedTenantSessionEntity sessionEntity = new DelegatedTenantSessionEntity(
                sessionId,
                masterUserId,
                targetTenantId,
                targetBranchId,
                accessType,
                reason,
                now,
                expiresAt
        );
        sessionRepository.save(sessionEntity);

        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set("delegated_session:active:" + sessionId, "ACTIVE", Duration.ofSeconds(effectiveTtl));
            } catch (Exception ex) {
                log.warn("Failed to seed Redis delegated session active cache: {}", ex.getMessage());
            }
        }

        // 5. Immutable Dual-Identity Audit Trail
        String auditPayload = String.format(
                "{\"sessionId\":\"%s\",\"masterUserId\":\"%s\",\"targetTenantId\":\"%s\",\"accessType\":\"%s\",\"reason\":\"%s\",\"expiresAt\":\"%s\"}",
                sessionId, masterUserId, targetTenantId, accessType, reason != null ? reason : "", expiresAt
        );

        try {
            auditService.recordEvent(
                    targetTenantId,
                    "MAIN",
                    masterUserId,
                    "MASTER_DELEGATED_OPERATOR",
                    AuditAction.MASTER_TENANT_ACCESS_GRANTED.name(),
                    "TENANT_SUPPORT_SESSION",
                    sessionId.toString(),
                    auditPayload,
                    "127.0.0.1"
            );
        } catch (Exception e) {
            log.warn("Failed to write delegated access audit event: {}", e.getMessage());
        }

        log.info("Master User '{}' entered delegated tenant session '{}' for Tenant '{}' [Mode: {}, Expires: {}]",
                masterUserId, sessionId, targetTenantId, accessType, expiresAt);

        return new DelegatedSessionResult(
                sessionId,
                token,
                targetTenantId,
                tenant.getLegalName(),
                tenant.getTin(),
                targetBranchId,
                accessType,
                now,
                expiresAt,
                masterUserId
        );
    }

    @Transactional
    public void terminateSupportSession(UUID sessionId, String masterUserId) {
        if (sessionId == null) return;

        Optional<DelegatedTenantSessionEntity> sessionOpt = sessionRepository.findBySessionId(sessionId);
        if (sessionOpt.isPresent()) {
            DelegatedTenantSessionEntity session = sessionOpt.get();
            session.setRevoked(true);
            session.setRevokedAt(Instant.now());
            session.setRevokedBy(masterUserId);
            sessionRepository.save(session);

            String auditPayload = String.format(
                    "{\"sessionId\":\"%s\",\"masterUserId\":\"%s\",\"targetTenantId\":\"%s\",\"terminatedAt\":\"%s\"}",
                    sessionId, masterUserId, session.getTargetTenantId(), Instant.now()
            );

            try {
                auditService.recordEvent(
                        session.getTargetTenantId(),
                        "MAIN",
                        masterUserId,
                        "MASTER_DELEGATED_OPERATOR",
                        AuditAction.MASTER_TENANT_SESSION_TERMINATED.name(),
                        "TENANT_SUPPORT_SESSION",
                        sessionId.toString(),
                        auditPayload,
                        "127.0.0.1"
                );
            } catch (Exception e) {
                log.warn("Failed to write session termination audit event: {}", e.getMessage());
            }

            log.info("Terminated delegated support session '{}' for Tenant '{}'", sessionId, session.getTargetTenantId());

            // Real-time cluster-wide revocation through Redis
            if (redisTemplate != null) {
                try {
                    redisTemplate.delete("delegated_session:active:" + sessionId);
                    redisTemplate.opsForValue().set("delegated_session:revoked:" + sessionId, "REVOKED", Duration.ofHours(24));
                } catch (Exception ex) {
                    log.warn("Redis revocation write failed: {}", ex.getMessage());
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public boolean isSessionActive(UUID sessionId) {
        if (sessionId == null) return false;

        // 1. Fast path: check Redis distributed revocation registry
        if (redisTemplate != null) {
            try {
                Boolean isRevoked = redisTemplate.hasKey("delegated_session:revoked:" + sessionId);
                if (Boolean.TRUE.equals(isRevoked)) {
                    return false;
                }
                Boolean isActive = redisTemplate.hasKey("delegated_session:active:" + sessionId);
                if (Boolean.TRUE.equals(isActive)) {
                    return true;
                }
            } catch (Exception e) {
                log.warn("Redis check failed for delegated session {}, falling back to DB: {}", sessionId, e.getMessage());
            }
        }

        // 2. Authoritative PostgreSQL check
        boolean activeInDb = sessionRepository.existsBySessionIdAndIsRevokedFalseAndExpiresAtAfter(sessionId, Instant.now());
        if (activeInDb && redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set("delegated_session:active:" + sessionId, "ACTIVE", Duration.ofMinutes(15));
            } catch (Exception ignored) {}
        }
        return activeInDb;
    }

    @Transactional(readOnly = true)
    public Optional<DelegatedTenantSessionEntity> getSession(UUID sessionId) {
        if (sessionId == null) return Optional.empty();
        return sessionRepository.findBySessionId(sessionId);
    }
}
