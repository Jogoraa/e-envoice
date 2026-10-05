package et.ut.einvoice.compliance;

import et.ut.einvoice.compliance.domain.ProviderComplianceTier;
import et.ut.einvoice.compliance.domain.ProviderTierStatus;
import et.ut.einvoice.compliance.domain.ProviderTierStatusHistory;
import et.ut.einvoice.compliance.dto.ProviderComplianceTierDto;
import et.ut.einvoice.compliance.dto.ProviderDashboardSummaryDto;
import et.ut.einvoice.compliance.dto.ProviderTierStatusDto;
import et.ut.einvoice.compliance.repository.ProviderComplianceTierRepository;
import et.ut.einvoice.compliance.repository.ProviderTierStatusHistoryRepository;
import et.ut.einvoice.compliance.service.ProviderComplianceTierService;
import et.ut.einvoice.platform.security.JwtTokenService;
import et.ut.einvoice.tenancy.domain.Tenant;
import et.ut.einvoice.tenancy.domain.TenantStatus;
import et.ut.einvoice.tenancy.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ProviderTierComplianceTestSuite {

    @Autowired
    private ProviderComplianceTierService tierService;

    @Autowired
    private ProviderComplianceTierRepository tierRepository;

    @Autowired
    private ProviderTierStatusHistoryRepository historyRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        historyRepository.deleteAll();
    }

    @Test
    @DisplayName("Stage 15: Regulatory Tiers 1 through 10 are seeded with statutory guarantees and staffing")
    void test_StatutoryTiersSeeded() {
        List<ProviderComplianceTier> tiers = tierRepository.findAllByOrderByTierLevelAsc();
        assertFalse(tiers.isEmpty(), "Provider compliance tiers must be seeded");
        assertEquals(10, tiers.size(), "Directive Art. 14(6) defines 10 regulatory tiers");

        ProviderComplianceTier level1 = tiers.get(0);
        assertEquals(1, level1.getTierLevel());
        assertEquals(0, level1.getMinActiveTaxpayers());
        assertEquals(500, level1.getMaxActiveTaxpayers());
        assertEquals(BigDecimal.ZERO.setScale(2), level1.getRequiredGuaranteeAmountUsd());
        assertEquals(2, level1.getRequiredTechnicalStaffing());

        ProviderComplianceTier level10 = tiers.get(9);
        assertEquals(10, level10.getTierLevel());
        assertEquals(40001, level10.getMinActiveTaxpayers());
        assertEquals(new BigDecimal("250000.00"), level10.getRequiredGuaranteeAmountUsd());
        assertEquals(8, level10.getRequiredTechnicalStaffing());
    }

    @Test
    @DisplayName("Stage 15: Baseline provider metrics evaluate to Level 1 CURRENT_LEVEL")
    void test_BaselineEvaluation_ReturnsLevel1() {
        ProviderTierStatusDto status = tierService.evaluateCurrentTier();
        assertNotNull(status);
        assertEquals(1, status.currentLevel());
        assertEquals(1, status.projectedLevel());
        assertEquals(ProviderTierStatus.CURRENT_LEVEL, status.status());
    }

    @Test
    @DisplayName("Stage 15: Proximity threshold warning when taxpayers approach 80% of tier limit")
    void test_ProximityThresholdApproaching() {
        UUID testTenantId = UUID.randomUUID();
        Tenant testTenant = new Tenant(testTenantId, "ORG-PROX", "Proximity Org", "Trade", "TIN999888111");
        testTenant.activate();
        tenantRepository.save(testTenant);

        // Pre-seed a history record reflecting 450 active taxpayers (90% of Level 1 max 500)
        ProviderTierStatusHistory record = new ProviderTierStatusHistory(
                UUID.randomUUID(),
                java.time.Instant.now(),
                450,
                BigDecimal.valueOf(100000),
                1,
                1,
                ProviderTierStatus.THRESHOLD_APPROACHING,
                BigDecimal.valueOf(90.00),
                "Approaching threshold",
                null
        );
        historyRepository.save(record);

        ProviderDashboardSummaryDto dashboard = tierService.getDashboardSummary();
        assertEquals(1, dashboard.currentLevel());
        assertEquals(ProviderTierStatus.THRESHOLD_APPROACHING, dashboard.status());
        assertEquals(new BigDecimal("90.00"), dashboard.proximityPercentage());
    }

    @Test
    @DisplayName("Stage 15: Pending notification when projected level exceeds current level")
    void test_PendingNotification_WhenProjectedLevelExceedsCurrent() {
        // Pre-seed history with 1200 taxpayers (Level 3 required, current is Level 1)
        ProviderTierStatusHistory record = new ProviderTierStatusHistory(
                UUID.randomUUID(),
                java.time.Instant.now(),
                1200,
                BigDecimal.valueOf(30000000),
                1,
                3,
                ProviderTierStatus.LEVEL_CHANGE_PENDING_NOTIFICATION,
                BigDecimal.valueOf(100.00),
                "Level 3 upgrade required",
                null
        );
        historyRepository.save(record);

        ProviderDashboardSummaryDto summary = tierService.getDashboardSummary();
        assertEquals(1, summary.currentLevel());
        assertEquals(3, summary.projectedLevel());
        assertEquals(ProviderTierStatus.LEVEL_CHANGE_PENDING_NOTIFICATION, summary.status());
    }

    @Test
    @DisplayName("Stage 15: Mark notified and confirm tier transition lifecycle")
    void test_TierTransitionLifecycle_NotifyAndConfirm() {
        ProviderTierStatusHistory record = new ProviderTierStatusHistory(
                UUID.randomUUID(),
                java.time.Instant.now(),
                600,
                BigDecimal.valueOf(15000000),
                1,
                2,
                ProviderTierStatus.LEVEL_CHANGE_PENDING_NOTIFICATION,
                BigDecimal.valueOf(100.00),
                "Level 2 upgrade required",
                null
        );
        ProviderTierStatusHistory saved = historyRepository.save(record);

        // Notify MoR
        ProviderTierStatusDto notified = tierService.markNotified(saved.getId());
        assertEquals(ProviderTierStatus.LEVEL_CHANGE_NOTIFIED, notified.status());
        assertNotNull(notified.notifiedAt());

        // Confirm upgrade
        ProviderTierStatusDto confirmed = tierService.confirmTierTransition(saved.getId());
        assertEquals(2, confirmed.currentLevel());
        assertEquals(ProviderTierStatus.CONFIRMED, confirmed.status());
    }

    @Test
    @DisplayName("Stage 15: Master Dashboard API returns comprehensive compliance metrics")
    void test_DashboardApi_ReturnsCompletePayload() throws Exception {
        String token = jwtTokenService.generateMasterToken(
                "super-admin",
                Set.of("ROLE_PLATFORM_ADMIN"),
                Set.of("master:read", "master:write"),
                3600
        );

        mockMvc.perform(get("/api/v1/master/provider-tiers/dashboard")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentLevel", is(1)))
                .andExpect(jsonPath("$.allTiers.length()", is(10)))
                .andExpect(jsonPath("$.requiredTechnicalStaffing", is(2)));
    }

    @Test
    @DisplayName("Stage 15: Authority Auditor can inspect tiers, but non-admin cannot transition")
    void test_Rbac_AuthorityAuditorAndAnonymous() throws Exception {
        String auditorToken = jwtTokenService.generateMasterToken(
                "gov-auditor",
                Set.of("ROLE_AUTHORITY_AUDITOR"),
                Set.of("authority:read"),
                3600
        );

        // Auditor can read tiers
        mockMvc.perform(get("/api/v1/master/provider-tiers")
                        .header("Authorization", "Bearer " + auditorToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // Anonymous request is rejected
        mockMvc.perform(get("/api/v1/master/provider-tiers/dashboard")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}
