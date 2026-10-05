package et.ut.einvoice.compliance;

import com.fasterxml.jackson.databind.ObjectMapper;
import et.ut.einvoice.compliance.domain.ProviderExitStatus;
import et.ut.einvoice.compliance.domain.TenantTransitionStatus;
import et.ut.einvoice.compliance.dto.*;
import et.ut.einvoice.compliance.repository.ProviderExitPlanRepository;
import et.ut.einvoice.compliance.repository.ProviderTenantTransitionRepository;
import et.ut.einvoice.platform.security.JwtTokenService;
import et.ut.einvoice.tenancy.domain.Tenant;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration Test Suite for FDRE MoR Directive No. 1142/2026 Art. 17:
 * Provider Exit Strategy & Transition Governance.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ProviderExitGovernanceTestSuite {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private ProviderExitPlanRepository exitPlanRepository;

    @Autowired
    private ProviderTenantTransitionRepository tenantTransitionRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    private String adminToken;
    private Tenant activeTenant1;
    private Tenant activeTenant2;

    @BeforeEach
    void setUp() {
        tenantTransitionRepository.deleteAll();
        exitPlanRepository.deleteAll();

        adminToken = jwtTokenService.generateMasterToken(
                "platform-admin-1",
                Set.of("ROLE_PLATFORM_ADMIN", "ROLE_MASTER_ADMIN"),
                Set.of("platform:admin"),
                3600
        );

        activeTenant1 = new Tenant(UUID.randomUUID(), "ORG-1", "Alpha Trading PLC", "AlphaTrade", "TIN-" + UUID.randomUUID().toString().substring(0, 8));
        activeTenant1.activate();
        tenantRepository.save(activeTenant1);

        activeTenant2 = new Tenant(UUID.randomUUID(), "ORG-2", "Beta Logistics Ltd", "BetaLogistics", "TIN-" + UUID.randomUUID().toString().substring(0, 8));
        activeTenant2.activate();
        tenantRepository.save(activeTenant2);
    }

    @Test
    @DisplayName("Art. 17(1): Rejects voluntary exit plan with less than 6 months notice")
    void testRejectsShortNoticeVoluntaryExit() throws Exception {
        CreateExitPlanRequestDto dto = new CreateExitPlanRequestDto();
        dto.setExitReason("Commercial winding down");
        dto.setRevocation(false);
        // Notice of only 30 days
        dto.setEffectiveExitDate(Instant.now().plus(30, ChronoUnit.DAYS));

        mockMvc.perform(post("/api/v1/master/provider-exit/plans")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Art. 17(1) - 17(5): Full Provider Exit & Taxpayer Transition Lifecycle")
    void testFullProviderExitLifecycle() throws Exception {
        // 1. Create compliant exit plan with 7 months notice (> 6 months)
        CreateExitPlanRequestDto planDto = new CreateExitPlanRequestDto();
        planDto.setExitReason("Platform sunsetting and technology refresh");
        planDto.setRevocation(false);
        planDto.setNoticePeriodMonths(7);
        planDto.setEffectiveExitDate(Instant.now().plus(210, ChronoUnit.DAYS));

        String planResponse = mockMvc.perform(post("/api/v1/master/provider-exit/plans")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(planDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("PLANNED")))
                .andExpect(jsonPath("$.noticePeriodMonths", is(7)))
                .andReturn().getResponse().getContentAsString();

        ProviderExitPlanResponseDto plan = objectMapper.readValue(planResponse, ProviderExitPlanResponseDto.class);
        UUID planId = plan.getId();

        // 2. Submit Exit Strategy (Art. 17(3))
        SubmitExitStrategyRequestDto strategyDto = new SubmitExitStrategyRequestDto();
        strategyDto.setStrategyDocumentReference("DOC-EXIT-STRATEGY-2026-V1");

        mockMvc.perform(post("/api/v1/master/provider-exit/plans/" + planId + "/strategy")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(strategyDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("STRATEGY_SUBMITTED")))
                .andExpect(jsonPath("$.strategyDocumentReference", is("DOC-EXIT-STRATEGY-2026-V1")));

        // 3. Authority Approval (Art. 17(3))
        ApproveExitStrategyRequestDto approvalDto = new ApproveExitStrategyRequestDto();
        approvalDto.setApprovalReference("MOR-APPROVAL-EXIT-8842");
        approvalDto.setApprovedBy("Director General Ato Kebede");

        mockMvc.perform(post("/api/v1/master/provider-exit/plans/" + planId + "/approval")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approvalDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("AUTHORITY_APPROVED")))
                .andExpect(jsonPath("$.authorityApprovalReference", is("MOR-APPROVAL-EXIT-8842")));

        // 4. Notify Taxpayers & initialize transition records
        mockMvc.perform(post("/api/v1/master/provider-exit/plans/" + planId + "/notify-taxpayers")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("IN_TRANSITION")));

        // Verify transition records created
        var transitions = tenantTransitionRepository.findByExitPlanId(planId);
        assertFalse(transitions.isEmpty());
        int totalTenants = transitions.size();

        // 5. Tenant 1 retrieves data (Art. 17(4))
        var firstTransition = transitions.get(0);
        mockMvc.perform(post("/api/v1/master/provider-exit/plans/" + planId + "/tenants/" + firstTransition.getTenantId() + "/data-retrieved")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("DATA_RETRIEVED")));

        // 6. Tenant 1 completes migration
        RecordMigrationRequestDto mig1 = new RecordMigrationRequestDto();
        mig1.setTenantId(firstTransition.getTenantId());
        mig1.setDestinationProviderName("Ethio Telecom POS System");
        mig1.setDestinationSystemNumber("ET-POS-SYS-9901");
        mig1.setMigrationEvidenceHash("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");

        mockMvc.perform(post("/api/v1/master/provider-exit/plans/" + planId + "/tenants/migration")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mig1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("MIGRATED")));

        // 7. Attempt Cessation Confirmation before 100% migration -> MUST BE REJECTED (Art. 17(5))
        ConfirmCessationRequestDto cessationDto = new ConfirmCessationRequestDto();
        cessationDto.setSurrenderedCertificateReference("CERT-SURRENDER-2026-001");
        cessationDto.setCessationConfirmationReference("MOR-CONFIRM-CESSATION-7700");

        if (totalTenants > 1) {
            mockMvc.perform(post("/api/v1/master/provider-exit/plans/" + planId + "/confirm-cessation")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(cessationDto)))
                    .andExpect(status().isBadRequest());
        }

        // 8. Remaining tenants complete migration
        for (int i = 1; i < totalTenants; i++) {
            var trans = transitions.get(i);
            RecordMigrationRequestDto mig = new RecordMigrationRequestDto();
            mig.setTenantId(trans.getTenantId());
            mig.setDestinationProviderName("Alternative ERP System " + i);
            mig.setDestinationSystemNumber("ALT-SYS-" + i);
            mig.setMigrationEvidenceHash("ca978112ca1bbdcafac231b39a23dc4da786081cd1e14eed64724b1955ced6a" + i);

            mockMvc.perform(post("/api/v1/master/provider-exit/plans/" + planId + "/tenants/migration")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(mig)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("MIGRATED")));
        }

        // Verify plan is now MIGRATION_COMPLETED
        mockMvc.perform(get("/api/v1/master/provider-exit/plans/" + planId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("MIGRATION_COMPLETED")))
                .andExpect(jsonPath("$.migrationProgressPercent", is(100.0)));

        // 9. Cessation Confirmation succeeds now (Art. 17(5))
        mockMvc.perform(post("/api/v1/master/provider-exit/plans/" + planId + "/confirm-cessation")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cessationDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CESSATION_CONFIRMED")))
                .andExpect(jsonPath("$.surrenderedCertificateReference", is("CERT-SURRENDER-2026-001")));
    }
}
