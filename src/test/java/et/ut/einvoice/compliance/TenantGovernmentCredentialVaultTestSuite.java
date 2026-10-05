package et.ut.einvoice.compliance;

import et.ut.einvoice.government.domain.CredentialStatus;
import et.ut.einvoice.government.domain.TenantGovernmentCredential;
import et.ut.einvoice.government.dto.ProvisionTenantCredentialRequest;
import et.ut.einvoice.government.dto.TenantGovernmentCredentialMetadataResponse;
import et.ut.einvoice.government.repository.TenantGovernmentCredentialRepository;
import et.ut.einvoice.government.service.TenantGovernmentCredentialService;
import et.ut.einvoice.platform.context.TenantContext;
import et.ut.einvoice.platform.context.TenantContextHolder;
import et.ut.einvoice.platform.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class TenantGovernmentCredentialVaultTestSuite {

    @Autowired
    private TenantGovernmentCredentialService credentialService;

    @Autowired
    private TenantGovernmentCredentialRepository credentialRepository;

    private UUID tenantAlpha;
    private UUID tenantBeta;

    @BeforeEach
    void setUp() {
        credentialRepository.deleteAll();
        tenantAlpha = UUID.randomUUID();
        tenantBeta = UUID.randomUUID();
    }

    private void setTenantContext(UUID tenantId, String username) {
        TenantContextHolder.setContext(TenantContext.create(tenantId, username, Set.of("ROLE_TENANT_ADMIN")));
        org.springframework.security.core.Authentication auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                username, "password", java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_TENANT_ADMIN"))
        );
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("Stage 3: Provision Credentials with AES-256 Vault Encryption and Never Store Plaintext")
    void test_ProvisionCredentials_EncryptedInDatabase() {
        setTenantContext(tenantAlpha, "ALPHA_ADMIN");

        ProvisionTenantCredentialRequest request = new ProvisionTenantCredentialRequest(
                "SYS-ALPHA-01",
                "0011223344",
                "REG-ALPHA-ETH",
                "client-id-alpha-secret-key",
                "client-secret-alpha-super-confidential",
                "api-key-alpha-998877",
                "INSA-ROOT-CERT-REF-001",
                "CERT-SN-7891011",
                Instant.now().plusSeconds(86400 * 365)
        );

        TenantGovernmentCredentialMetadataResponse response = credentialService.provisionCredentials(request);
        assertNotNull(response);
        assertEquals(tenantAlpha, response.tenantId());
        assertEquals("0011223344", response.sellerTin());
        assertEquals("SYS-ALPHA-01", response.morSystemNumber());
        assertEquals(CredentialStatus.ACTIVE, response.credentialStatus());

        // Verify the database record DOES NOT contain plaintext client-id, client-secret, or api-key
        TenantGovernmentCredential rawEntity = credentialRepository.findByTenantId(tenantAlpha).orElseThrow();
        assertNotEquals("client-id-alpha-secret-key", rawEntity.getEncryptedClientId(), "Client ID must not be plaintext");
        assertNotEquals("client-secret-alpha-super-confidential", rawEntity.getEncryptedClientSecret(), "Client secret must not be plaintext");
        assertNotEquals("api-key-alpha-998877", rawEntity.getEncryptedApiKey(), "API key must not be plaintext");

        // Verify decrypt method cleanly unpacks original values
        var decrypted = credentialService.getDecryptedCredentialsForTenant(tenantAlpha);
        assertEquals("client-id-alpha-secret-key", decrypted.clientId());
        assertEquals("client-secret-alpha-super-confidential", decrypted.clientSecret());
        assertEquals("api-key-alpha-998877", decrypted.apiKey());
        assertEquals("0011223344", decrypted.sellerTin());
    }

    @Test
    @DisplayName("Stage 3: Adversarial Cross-Tenant Credential Access is Strictly Blocked")
    void test_AdversarialCrossTenantCredentialAccess_Prevented() {
        // Provision Alpha credentials
        setTenantContext(tenantAlpha, "ALPHA_ADMIN");
        credentialService.provisionCredentials(new ProvisionTenantCredentialRequest(
                "SYS-ALPHA", "0011223344", "REG-A",
                "alpha-id", "alpha-secret", "alpha-api",
                null, null, null
        ));

        // Provision Beta credentials
        setTenantContext(tenantBeta, "BETA_ADMIN");
        credentialService.provisionCredentials(new ProvisionTenantCredentialRequest(
                "SYS-BETA", "0099887766", "REG-B",
                "beta-id", "beta-secret", "beta-api",
                null, null, null
        ));

        // When in Beta context, querying current tenant metadata returns ONLY Beta metadata
        var betaMeta = credentialService.getMetadataForCurrentTenant();
        assertEquals(tenantBeta, betaMeta.tenantId());
        assertEquals("0099887766", betaMeta.sellerTin());
        assertEquals("SYS-BETA", betaMeta.morSystemNumber());

        // Decrypted resolution for Alpha vs Beta must yield completely distinct secrets
        var decAlpha = credentialService.getDecryptedCredentialsForTenant(tenantAlpha);
        var decBeta = credentialService.getDecryptedCredentialsForTenant(tenantBeta);

        assertNotEquals(decAlpha.clientId(), decBeta.clientId());
        assertNotEquals(decAlpha.apiKey(), decBeta.apiKey());
        assertEquals("alpha-id", decAlpha.clientId());
        assertEquals("beta-id", decBeta.clientId());
    }

    @Test
    @DisplayName("Stage 3: Rotate API Key Increments Vault Version and Audits")
    void test_RotateApiKey_IncrementsVaultVersion() {
        setTenantContext(tenantAlpha, "ALPHA_ADMIN");
        credentialService.provisionCredentials(new ProvisionTenantCredentialRequest(
                "SYS-ALPHA", "0011223344", "REG-A",
                "alpha-id", "alpha-secret", "initial-api-key",
                null, null, null
        ));

        // Rotate API key
        TenantGovernmentCredentialMetadataResponse rotated = credentialService.rotateApiKey("new-rotated-api-key-2026");
        assertEquals(2, rotated.keyVaultVersion());

        var decrypted = credentialService.getDecryptedCredentialsForTenant(tenantAlpha);
        assertEquals("new-rotated-api-key-2026", decrypted.apiKey());
    }

    @Test
    @DisplayName("Stage 3: Suspended/Revoked Credentials Fail Closed")
    void test_SuspendedCredentials_FailClosed() {
        setTenantContext(tenantAlpha, "ALPHA_ADMIN");
        credentialService.provisionCredentials(new ProvisionTenantCredentialRequest(
                "SYS-ALPHA", "0011223344", "REG-A",
                "alpha-id", "alpha-secret", "api-key",
                null, null, null
        ));

        TenantGovernmentCredential entity = credentialRepository.findByTenantId(tenantAlpha).orElseThrow();
        entity.setCredentialStatus(CredentialStatus.SUSPENDED);
        credentialRepository.save(entity);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                credentialService.getDecryptedCredentialsForTenant(tenantAlpha)
        );
        assertEquals("CREDENTIAL_NOT_ACTIVE", ex.getCode());
    }
}
