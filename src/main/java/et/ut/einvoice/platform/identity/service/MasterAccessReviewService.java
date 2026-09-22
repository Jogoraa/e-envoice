package et.ut.einvoice.platform.identity.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import et.ut.einvoice.audit.service.AuditService;
import et.ut.einvoice.platform.identity.domain.AccessReviewCampaign;
import et.ut.einvoice.platform.identity.domain.AccessReviewEntry;
import et.ut.einvoice.platform.identity.dto.IdentityDtos.*;
import et.ut.einvoice.platform.identity.repository.AccessReviewCampaignRepository;
import et.ut.einvoice.platform.identity.repository.AccessReviewEntryRepository;
import et.ut.einvoice.platform.identity.repository.PlatformUserSessionRepository;
import et.ut.einvoice.platform.security.domain.PlatformUser;
import et.ut.einvoice.platform.security.repository.PlatformUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class MasterAccessReviewService {

    private static final Logger log = LoggerFactory.getLogger(MasterAccessReviewService.class);

    private final AccessReviewCampaignRepository campaignRepository;
    private final AccessReviewEntryRepository entryRepository;
    private final PlatformUserRepository userRepository;
    private final MasterRbacService rbacService;
    private final PlatformUserSessionRepository sessionRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MasterAccessReviewService(
            AccessReviewCampaignRepository campaignRepository,
            AccessReviewEntryRepository entryRepository,
            PlatformUserRepository userRepository,
            MasterRbacService rbacService,
            PlatformUserSessionRepository sessionRepository,
            AuditService auditService
    ) {
        this.campaignRepository = campaignRepository;
        this.entryRepository = entryRepository;
        this.userRepository = userRepository;
        this.rbacService = rbacService;
        this.sessionRepository = sessionRepository;
        this.auditService = auditService;
    }

    public List<AccessReviewCampaignDto> listCampaigns() {
        return campaignRepository.findAll().stream()
                .map(this::toCampaignDto)
                .toList();
    }

    @Transactional
    public AccessReviewCampaignDto createCampaign(String adminUsername, CreateReviewCampaignRequest req) {
        AccessReviewCampaign campaign = new AccessReviewCampaign(
                UUID.randomUUID(),
                req.title() != null ? req.title().trim() : "Quarterly Access Certification",
                req.description(),
                adminUsername
        );
        campaignRepository.save(campaign);

        List<PlatformUser> users = userRepository.findAll();
        Instant ninetyDaysAgo = Instant.now().minus(90, ChronoUnit.DAYS);

        for (PlatformUser user : users) {
            EffectiveAccessDto access = rbacService.calculateEffectiveAccess(user.getId());
            String rolesStr = String.join(", ", access.assignedRoles());
            String permsStr = String.format("%d effective permissions (%s...)",
                    access.effectivePermissions().size(),
                    access.effectivePermissions().stream().limit(5).reduce((a, b) -> a + ", " + b).orElse("NONE"));

            AccessReviewEntry entry = new AccessReviewEntry(
                    UUID.randomUUID(),
                    campaign.getId(),
                    user.getId(),
                    rolesStr,
                    permsStr
            );

            // Inactive account detection (>90 days without login)
            if (user.getLastLoginAt() != null && user.getLastLoginAt().isBefore(ninetyDaysAgo)) {
                entry.setNotes("[INACTIVE WARNING]: Account has had no login activity for over 90 days. Recommend suspension.");
            } else if (user.getLastLoginAt() == null && user.getCreatedAt().isBefore(ninetyDaysAgo)) {
                entry.setNotes("[INACTIVE WARNING]: Account was provisioned >90 days ago with zero login activity. Recommend suspension.");
            }

            entryRepository.save(entry);
        }

        recordAudit(adminUsername, "ACCESS_REVIEW_CAMPAIGN_CREATED", Map.of(
                "campaignId", campaign.getId().toString(),
                "title", campaign.getTitle(),
                "entriesCount", users.size()
        ));

        return toCampaignDto(campaign);
    }

    public List<AccessReviewEntryDto> getCampaignEntries(UUID campaignId) {
        List<AccessReviewEntry> entries = entryRepository.findByCampaignId(campaignId);
        List<AccessReviewEntryDto> result = new ArrayList<>();

        for (AccessReviewEntry e : entries) {
            PlatformUser u = userRepository.findById(e.getUserId()).orElse(null);
            String username = u != null ? u.getUsername() : "UNKNOWN";
            String fullName = u != null ? u.getFullName() : "UNKNOWN";

            result.add(new AccessReviewEntryDto(
                    e.getId(),
                    e.getCampaignId(),
                    e.getUserId(),
                    username,
                    fullName,
                    e.getCurrentRoles(),
                    e.getEffectivePermissionsSummary(),
                    e.getDecision(),
                    e.getNotes(),
                    e.getReviewedAt(),
                    e.getReviewerId()
            ));
        }

        return result;
    }

    @Transactional
    public AccessReviewEntryDto submitEntryDecision(String adminUsername, UUID entryId, SubmitReviewDecisionRequest req) {
        AccessReviewEntry entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("Entry not found: " + entryId));

        String decision = req.decision().toUpperCase(Locale.ROOT);
        if (!List.of("APPROVED", "MODIFIED", "REVOKED", "SUSPENDED").contains(decision)) {
            throw new IllegalArgumentException("Invalid review decision: " + decision);
        }

        entry.setDecision(decision);
        if (req.notes() != null) {
            entry.setNotes(req.notes());
        }
        entry.setReviewerId(adminUsername);
        entry.setReviewedAt(Instant.now());
        entryRepository.save(entry);

        // Execute automated remediation if decision is SUSPENDED
        if ("SUSPENDED".equals(decision)) {
            userRepository.findById(entry.getUserId()).ifPresent(u -> {
                u.setStatus("SUSPENDED");
                u.setUpdatedAt(Instant.now());
                userRepository.save(u);

                // Terminate live sessions
                var sessions = sessionRepository.findByUserId(u.getId());
                for (var sess : sessions) {
                    sess.setRevoked(true);
                    sess.setRevokedAt(Instant.now());
                    sess.setRevocationReason("ACCESS_REVIEW_SUSPENSION");
                    sessionRepository.save(sess);
                }
            });
        }

        // Auto-complete campaign if all entries are reviewed
        List<AccessReviewEntry> allEntries = entryRepository.findByCampaignId(entry.getCampaignId());
        boolean allDone = allEntries.stream().noneMatch(e -> "PENDING".equalsIgnoreCase(e.getDecision()));
        if (allDone) {
            campaignRepository.findById(entry.getCampaignId()).ifPresent(c -> {
                c.setStatus("COMPLETED");
                c.setCompletedAt(Instant.now());
                campaignRepository.save(c);
            });
        }

        recordAudit(adminUsername, "ACCESS_REVIEW_DECISION_RECORDED", Map.of(
                "entryId", entry.getId().toString(),
                "userId", entry.getUserId().toString(),
                "decision", decision
        ));

        PlatformUser u = userRepository.findById(entry.getUserId()).orElse(null);
        return new AccessReviewEntryDto(
                entry.getId(),
                entry.getCampaignId(),
                entry.getUserId(),
                u != null ? u.getUsername() : "UNKNOWN",
                u != null ? u.getFullName() : "UNKNOWN",
                entry.getCurrentRoles(),
                entry.getEffectivePermissionsSummary(),
                entry.getDecision(),
                entry.getNotes(),
                entry.getReviewedAt(),
                entry.getReviewerId()
        );
    }

    @Transactional
    public AccessReviewCampaignDto finalizeCampaign(String adminUsername, UUID campaignId) {
        AccessReviewCampaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found: " + campaignId));

        campaign.setStatus("COMPLETED");
        campaign.setCompletedAt(Instant.now());
        campaignRepository.save(campaign);

        recordAudit(adminUsername, "ACCESS_REVIEW_CAMPAIGN_FINALIZED", Map.of(
                "campaignId", campaign.getId().toString()
        ));

        return toCampaignDto(campaign);
    }

    private AccessReviewCampaignDto toCampaignDto(AccessReviewCampaign c) {
        List<AccessReviewEntry> entries = entryRepository.findByCampaignId(c.getId());
        int total = entries.size();
        int reviewed = (int) entries.stream().filter(e -> !"PENDING".equalsIgnoreCase(e.getDecision())).count();

        return new AccessReviewCampaignDto(
                c.getId(),
                c.getTitle(),
                c.getDescription(),
                c.getInitiatedBy(),
                c.getStatus(),
                c.getCreatedAt(),
                c.getCompletedAt(),
                total,
                reviewed
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
                    "IDENTITY_ACCESS_REVIEW",
                    UUID.randomUUID().toString(),
                    objectMapper.writeValueAsString(payload)
            );
        } catch (Exception e) {
            log.warn("Failed to write access review audit log: {}", e.getMessage());
        }
    }
}
