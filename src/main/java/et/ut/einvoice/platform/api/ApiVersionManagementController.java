package et.ut.einvoice.platform.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read-only version catalog for the platform administration dashboard.
 *
 * <p>Publishing an API major is intentionally not exposed as a runtime mutation. It must be
 * released with its controllers, security policy, OpenAPI group and contract-test evidence.</p>
 */
@RestController
@RequestMapping("/api/v1/master/api-versions")
@Tag(name = "API Version Governance", description = "Read-only release state and activation controls for public API majors")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class ApiVersionManagementController {

    private static final Pattern VERSION = Pattern.compile("^v?([1-9][0-9]*)$", Pattern.CASE_INSENSITIVE);

    private final ApiVersionProperties properties;
    private final String platformRelease;

    public ApiVersionManagementController(
            ApiVersionProperties properties,
            @org.springframework.beans.factory.annotation.Value("${platform.version:1.0.0-RELEASE}") String platformRelease
    ) {
        this.properties = properties;
        this.platformRelease = platformRelease;
    }

    @GetMapping
    @Operation(summary = "Get the live API-version catalog and controlled publication checklist")
    public ResponseEntity<ApiVersionManagementResponse> getVersionCatalog() {
        String currentVersion = canonicalVersion(properties.getCurrentVersion());
        Set<String> supportedVersions = configuredVersions(properties.getSupportedVersions());
        if (!supportedVersions.contains(currentVersion)) {
            throw new IllegalStateException(
                    "platform.api.current-version must be included in platform.api.supported-versions"
            );
        }

        Set<String> catalogVersions = new LinkedHashSet<>(supportedVersions);
        catalogVersions.addAll(properties.getCatalog().keySet().stream()
                .map(this::canonicalVersion)
                .toList());

        List<ApiVersionSummary> versions = catalogVersions.stream()
                .sorted(Comparator.comparingInt(this::majorVersion))
                .map(version -> toSummary(version, currentVersion, supportedVersions.contains(version)))
                .toList();

        return ResponseEntity.ok(new ApiVersionManagementResponse(
                currentVersion,
                supportedVersions.stream().sorted(Comparator.comparingInt(this::majorVersion)).toList(),
                versions,
                new PublicationControl(
                        true,
                        "API_SUPPORTED_VERSIONS",
                        "Version publication is deployment-controlled. The dashboard cannot activate a version at runtime.",
                        "Deploy every instance with the new controllers, authorization rules, OpenAPI group, and green contract evidence before adding the major to API_SUPPORTED_VERSIONS."
                ),
                List.of(
                        "Approved version RFC and a documented compatibility assessment",
                        "Dedicated /api/v{major}/ controllers and v{major} wire DTOs",
                        "Security, authentication, rate-limit, signing, and tenant-isolation tests for the new path",
                        "Published /v3/api-docs/v{major} OpenAPI document and generated client validation",
                        "Backward-compatibility verification proving all supported prior versions are unchanged",
                        "Blue/green or all-instance deployment plan with rollback and client communications"
                ),
                List.of(
                        "Announce deprecation with migration guidance and a support end date",
                        "Emit Deprecation, Sunset, and successor Link headers on the retiring version",
                        "Track client migration until no supported integration relies on the retiring major",
                        "Remove the major from API_SUPPORTED_VERSIONS only after the announced sunset and approval"
                )
        ));
    }

    private ApiVersionSummary toSummary(String version, String currentVersion, boolean supported) {
        ApiVersionProperties.VersionMetadata metadata = metadataFor(version);
        boolean current = version.equals(currentVersion);
        String lifecycle = firstNonBlank(metadata.getLifecycle(), current ? "CURRENT" : supported ? "SUPPORTED" : "DRAFT");
        String contractVersion = firstNonBlank(metadata.getContractVersion(), current ? platformRelease : "Not published");
        String changePolicy = firstNonBlank(
                metadata.getChangePolicy(),
                metadata.isFrozen() ? "ADDITIVE_ONLY" : "BREAKING_CHANGES_ALLOWED"
        );

        return new ApiVersionSummary(
                version,
                current,
                supported,
                lifecycle.toUpperCase(Locale.ROOT),
                contractVersion,
                metadata.getReleasedAt(),
                metadata.isFrozen(),
                changePolicy,
                "/api/" + version,
                "/v3/api-docs/" + version,
                metadata.getDeprecationDate(),
                metadata.getSunsetDate(),
                metadata.getNotes()
        );
    }

    private ApiVersionProperties.VersionMetadata metadataFor(String version) {
        ApiVersionProperties.VersionMetadata metadata = properties.getCatalog().get(version);
        if (metadata != null) {
            return metadata;
        }
        return new ApiVersionProperties.VersionMetadata();
    }

    private Set<String> configuredVersions(String configuredVersions) {
        Set<String> versions = new LinkedHashSet<>();
        if (configuredVersions == null) {
            return versions;
        }
        for (String configuredVersion : configuredVersions.split(",")) {
            if (!configuredVersion.isBlank()) {
                versions.add(canonicalVersion(configuredVersion));
            }
        }
        return versions;
    }

    private String canonicalVersion(String value) {
        if (value == null) {
            throw new IllegalStateException("API version configuration must not be null");
        }
        Matcher matcher = VERSION.matcher(value.trim());
        if (!matcher.matches()) {
            throw new IllegalStateException("Invalid API version configuration: " + value);
        }
        return "v" + matcher.group(1);
    }

    private int majorVersion(String version) {
        return Integer.parseInt(version.substring(1));
    }

    private static String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record ApiVersionManagementResponse(
            String currentVersion,
            List<String> supportedVersions,
            List<ApiVersionSummary> versions,
            PublicationControl publicationControl,
            List<String> activationChecklist,
            List<String> retirementChecklist
    ) {
    }

    public record ApiVersionSummary(
            String version,
            boolean current,
            boolean supported,
            String lifecycle,
            String contractVersion,
            String releasedAt,
            boolean frozen,
            String changePolicy,
            String pathPrefix,
            String openApiPath,
            String deprecationDate,
            String sunsetDate,
            String notes
    ) {
    }

    public record PublicationControl(
            boolean deploymentControlled,
            String activationEnvironmentVariable,
            String dashboardPolicy,
            String activationRule
    ) {
    }
}
