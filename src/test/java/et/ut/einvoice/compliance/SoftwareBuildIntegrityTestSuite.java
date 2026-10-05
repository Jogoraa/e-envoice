package et.ut.einvoice.compliance;

import et.ut.einvoice.compliance.domain.SoftwareBuildChecksum;
import et.ut.einvoice.compliance.dto.SystemChecksumResponseDto;
import et.ut.einvoice.compliance.repository.SoftwareBuildChecksumRepository;
import et.ut.einvoice.compliance.service.SoftwareIntegrityService;
import et.ut.einvoice.platform.security.JwtTokenService;
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
import java.util.Set;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SoftwareBuildIntegrityTestSuite {

    @Autowired
    private SoftwareIntegrityService softwareIntegrityService;

    @Autowired
    private SoftwareBuildChecksumRepository checksumRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        checksumRepository.deleteAll();
    }

    @Test
    @DisplayName("Stage 11: System Checksum without registered release returns NOT_REGISTERED")
    void test_GetSystemChecksum_Default_ReturnsNotRegistered() {
        SystemChecksumResponseDto dto = softwareIntegrityService.getSystemChecksum();
        assertNotNull(dto);
        assertNotNull(dto.version());
        assertNotNull(dto.backendSha256());
        assertEquals("NOT_REGISTERED", dto.checksumStatus());
    }

    @Test
    @DisplayName("Stage 11: Active Release with matching registered checksum returns MATCH")
    void test_RecordBuildChecksum_Active_MatchesRegisteredChecksum() {
        String testSha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        softwareIntegrityService.recordBuildChecksum(
                "v2.1.0-RELEASE",
                "abc1234def5678",
                testSha256,
                "frontend-sha256-mock",
                "sha256:container-digest-mock",
                "V18",
                "sbom-hash-mock",
                testSha256,
                "INSA_DIGITAL_SIG_EVIDENCE_OK",
                Instant.now(),
                true
        );

        SystemChecksumResponseDto dto = softwareIntegrityService.getSystemChecksum();
        assertNotNull(dto);
        assertEquals("v2.1.0-RELEASE", dto.version());
        assertEquals("MATCH", dto.checksumStatus());
        assertEquals(testSha256, dto.backendSha256());
        assertEquals(testSha256, dto.registeredChecksum());
    }

    @Test
    @DisplayName("Stage 11: Active Release with altered registered checksum returns MISMATCH")
    void test_RecordBuildChecksum_Active_MismatchRegisteredChecksum() {
        String actualBackendSha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        String alteredRegisteredSha256 = "1111111111111111111111111111111111111111111111111111111111111111";

        softwareIntegrityService.recordBuildChecksum(
                "v2.1.1-UNAUTHORIZED",
                "tampered123",
                actualBackendSha256,
                null,
                null,
                "V18",
                null,
                alteredRegisteredSha256,
                null,
                Instant.now(),
                true
        );

        SystemChecksumResponseDto dto = softwareIntegrityService.getSystemChecksum();
        assertNotNull(dto);
        assertEquals("v2.1.1-UNAUTHORIZED", dto.version());
        assertEquals("MISMATCH", dto.checksumStatus());
    }

    @Test
    @DisplayName("Stage 11: Successive active release deactivates prior releases")
    void test_RecordBuildChecksum_SuccessiveActive_DeactivatesPrevious() {
        softwareIntegrityService.recordBuildChecksum(
                "v1.0.0", "git1", "sha1", null, null, "V17", null, "sha1", null, Instant.now().minusSeconds(3600), true
        );
        softwareIntegrityService.recordBuildChecksum(
                "v1.0.1", "git2", "sha2", null, null, "V18", null, "sha2", null, Instant.now(), true
        );

        SystemChecksumResponseDto dto = softwareIntegrityService.getSystemChecksum();
        assertEquals("v1.0.1", dto.version());
        assertEquals("sha2", dto.backendSha256());

        SoftwareBuildChecksum oldRelease = checksumRepository.findByVersion("v1.0.0").orElseThrow();
        assertFalse(oldRelease.isActiveRelease(), "Prior release must be marked inactive");
    }

    @Test
    @DisplayName("Stage 11: Authority endpoint GET /api/v1/authority/system-checksum accessible to authority auditor")
    void test_AuthorityEndpoint_WithAuthorityAuditor_Returns200() throws Exception {
        String testSha256 = "c0ffee25deadbeef1234567890abcdef1234567890abcdef1234567890abcdef";
        softwareIntegrityService.recordBuildChecksum(
                "v2.2.0-INSA",
                "gitcommit999",
                testSha256,
                null,
                null,
                "V18",
                null,
                testSha256,
                null,
                Instant.now(),
                true
        );

        String authorityToken = jwtTokenService.generateMasterToken(
                "auditor-insa-01",
                Set.of("ROLE_AUTHORITY_AUDITOR"),
                Set.of("authority:audit"),
                3600
        );

        mockMvc.perform(get("/api/v1/authority/system-checksum")
                        .header("X-Authority-Token", authorityToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version", is("v2.2.0-INSA")))
                .andExpect(jsonPath("$.backendSha256", is(testSha256)))
                .andExpect(jsonPath("$.checksumStatus", is("MATCH")));
    }

    @Test
    @DisplayName("Stage 11: Authority endpoint unauthenticated returns 401")
    void test_AuthorityEndpoint_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/authority/system-checksum")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}
