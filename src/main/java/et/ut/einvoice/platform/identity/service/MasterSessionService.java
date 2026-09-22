package et.ut.einvoice.platform.identity.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import et.ut.einvoice.audit.service.AuditService;
import et.ut.einvoice.platform.identity.domain.PlatformUserSession;
import et.ut.einvoice.platform.identity.dto.IdentityDtos.PlatformSessionDto;
import et.ut.einvoice.platform.identity.repository.PlatformUserSessionRepository;
import et.ut.einvoice.platform.security.domain.PlatformUser;
import et.ut.einvoice.platform.security.repository.PlatformUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class MasterSessionService {

    private static final Logger log = LoggerFactory.getLogger(MasterSessionService.class);

    private final PlatformUserSessionRepository sessionRepository;
    private final PlatformUserRepository userRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MasterSessionService(
            PlatformUserSessionRepository sessionRepository,
            PlatformUserRepository userRepository,
            AuditService auditService
    ) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    public List<PlatformSessionDto> listUserSessions(String username, String currentTokenHash) {
        PlatformUser user = userRepository.findByUsername(username)
                .or(() -> userRepository.findByEmail(username))
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        return sessionRepository.findByUserIdAndRevokedFalseAndExpiresAtAfter(user.getId(), Instant.now())
                .stream()
                .map(s -> toDto(s, user.getUsername(), currentTokenHash))
                .toList();
    }

    public List<PlatformSessionDto> listAllActiveSessions(String currentTokenHash) {
        return sessionRepository.findByRevokedFalseAndExpiresAtAfter(Instant.now())
                .stream()
                .map(s -> {
                    String uname = userRepository.findById(s.getUserId())
                            .map(PlatformUser::getUsername)
                            .orElse("UNKNOWN");
                    return toDto(s, uname, currentTokenHash);
                })
                .toList();
    }

    @Transactional
    public void revokeSession(String adminUsername, UUID sessionId, String reason) {
        PlatformUserSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        session.setRevoked(true);
        session.setRevokedAt(Instant.now());
        session.setRevocationReason(reason != null ? reason : "ADMINISTRATIVE_TERMINATION");
        sessionRepository.save(session);

        recordAudit(adminUsername, "SESSION_REVOKED", Map.of(
                "sessionId", sessionId.toString(),
                "userId", session.getUserId().toString(),
                "reason", session.getRevocationReason()
        ));
    }

    @Transactional
    public void revokeAllOtherSessions(String username, String currentTokenHash) {
        PlatformUser user = userRepository.findByUsername(username)
                .or(() -> userRepository.findByEmail(username))
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        List<PlatformUserSession> activeSessions = sessionRepository
                .findByUserIdAndRevokedFalseAndExpiresAtAfter(user.getId(), Instant.now());

        int count = 0;
        for (PlatformUserSession s : activeSessions) {
            if (currentTokenHash == null || !s.getTokenHash().equals(currentTokenHash)) {
                s.setRevoked(true);
                s.setRevokedAt(Instant.now());
                s.setRevocationReason("USER_REVOKED_OTHER_SESSIONS");
                sessionRepository.save(s);
                count++;
            }
        }

        recordAudit(username, "ALL_OTHER_SESSIONS_REVOKED", Map.of("revokedCount", count));
    }

    private PlatformSessionDto toDto(PlatformUserSession s, String username, String currentTokenHash) {
        boolean isCurrent = currentTokenHash != null && s.getTokenHash().equals(currentTokenHash);
        return new PlatformSessionDto(
                s.getId(),
                s.getUserId(),
                username,
                s.getIpAddress(),
                s.getUserAgent(),
                s.getDeviceSummary(),
                s.isMfaAuthenticated(),
                s.getCreatedAt(),
                s.getLastSeenAt(),
                isCurrent
        );
    }

    private void recordAudit(String username, String action, Map<String, Object> details) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>(details);
            payload.put("action", action);
            payload.put("operator", username);

            auditService.recordEvent(
                    UUID.fromString("00000000-0000-0000-0000-000000000000"),
                    username,
                    action,
                    "SECURITY_SESSION",
                    UUID.randomUUID().toString(),
                    objectMapper.writeValueAsString(payload)
            );
        } catch (Exception e) {
            log.warn("Failed to write session audit log: {}", e.getMessage());
        }
    }
}
