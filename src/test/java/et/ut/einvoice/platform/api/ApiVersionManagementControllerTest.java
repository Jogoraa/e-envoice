package et.ut.einvoice.platform.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiVersionManagementControllerTest {

    @Test
    void exposesTheFrozenV1ContractAndKeepsDraftMajorsUnpublished() {
        ApiVersionProperties properties = new ApiVersionProperties();
        properties.setCurrentVersion("v1");
        properties.setSupportedVersions("v1");

        ApiVersionProperties.VersionMetadata v1 = new ApiVersionProperties.VersionMetadata();
        v1.setLifecycle("CURRENT");
        v1.setContractVersion("1.0.0-RELEASE");
        v1.setReleasedAt("2026-09-18");
        v1.setFrozen(true);
        v1.setChangePolicy("ADDITIVE_ONLY");
        properties.getCatalog().put("v1", v1);

        ApiVersionProperties.VersionMetadata v2 = new ApiVersionProperties.VersionMetadata();
        v2.setLifecycle("DRAFT");
        v2.setContractVersion("2.0.0-DRAFT");
        v2.setChangePolicy("BREAKING_CHANGES_ALLOWED");
        properties.getCatalog().put("v2", v2);

        var response = new ApiVersionManagementController(properties, "1.0.0-RELEASE")
                .getVersionCatalog()
                .getBody();

        assertThat(response).isNotNull();
        assertThat(response.currentVersion()).isEqualTo("v1");
        assertThat(response.supportedVersions()).containsExactly("v1");
        assertThat(response.versions()).anySatisfy(version -> {
            assertThat(version.version()).isEqualTo("v1");
            assertThat(version.current()).isTrue();
            assertThat(version.supported()).isTrue();
            assertThat(version.frozen()).isTrue();
            assertThat(version.openApiPath()).isEqualTo("/v3/api-docs/v1");
        });
        assertThat(response.versions()).anySatisfy(version -> {
            assertThat(version.version()).isEqualTo("v2");
            assertThat(version.lifecycle()).isEqualTo("DRAFT");
            assertThat(version.supported()).isFalse();
        });
        assertThat(response.publicationControl().deploymentControlled()).isTrue();
        assertThat(response.publicationControl().activationEnvironmentVariable()).isEqualTo("API_SUPPORTED_VERSIONS");
    }
}
