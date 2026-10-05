package et.ut.einvoice.compliance.service;

import et.ut.einvoice.compliance.domain.SoftwareBuildChecksum;
import et.ut.einvoice.compliance.dto.SystemChecksumResponseDto;
import et.ut.einvoice.compliance.repository.SoftwareBuildChecksumRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Service managing Software Build Checksums and Version Integrity Verification
 * under FDRE MoR Directive No. 1142/2026 Art. 10(9), 11(2), 12(3).
 */
@Service
public class SoftwareIntegrityService {

    private static final Logger log = LoggerFactory.getLogger(SoftwareIntegrityService.class);

    private final SoftwareBuildChecksumRepository checksumRepository;

    @Value("${application.version:1.0.0-PROD}")
    private String appVersion;

    @Value("${application.git-sha:dev-build}")
    private String gitSha;

    @Value("${application.schema-version:V18}")
    private String schemaVersion;

    public SoftwareIntegrityService(SoftwareBuildChecksumRepository checksumRepository) {
        this.checksumRepository = checksumRepository;
    }

    @Transactional(readOnly = true)
    public SystemChecksumResponseDto getSystemChecksum() {
        Optional<SoftwareBuildChecksum> latestOpt = checksumRepository.findTopByActiveReleaseTrueOrderByBuildTimestampDesc();

        if (latestOpt.isPresent()) {
            SoftwareBuildChecksum record = latestOpt.get();
            String status = determineStatus(record.getBackendSha256(), record.getRegisteredChecksum());
            return new SystemChecksumResponseDto(
                    record.getVersion(),
                    record.getGitSha(),
                    record.getBackendSha256(),
                    record.getFrontendBuildSha256(),
                    record.getContainerDigest(),
                    record.getDatabaseSchemaVersion(),
                    record.getRegisteredChecksum(),
                    status,
                    record.getBuildTimestamp()
            );
        }

        // Fallback when no active release has been explicitly seeded or registered
        String computedBackendHash = computeSha256(appVersion + ":" + gitSha + ":" + schemaVersion);
        return new SystemChecksumResponseDto(
                appVersion,
                gitSha,
                computedBackendHash,
                null,
                null,
                schemaVersion,
                null,
                "NOT_REGISTERED",
                Instant.now()
        );
    }

    @Transactional
    public SoftwareBuildChecksum recordBuildChecksum(
            String version,
            String gitSha,
            String backendSha256,
            String frontendBuildSha256,
            String containerDigest,
            String dbSchemaVersion,
            String sbomHash,
            String registeredChecksum,
            String authoritySignature,
            Instant buildTimestamp,
            boolean isActiveRelease
    ) {
        if (isActiveRelease) {
            // Deactivate prior active releases
            checksumRepository.findTopByActiveReleaseTrueOrderByBuildTimestampDesc()
                    .ifPresent(prior -> {
                        prior.setActiveRelease(false);
                        checksumRepository.save(prior);
                    });
        }

        SoftwareBuildChecksum record = new SoftwareBuildChecksum(
                UUID.randomUUID(),
                version,
                gitSha,
                backendSha256,
                frontendBuildSha256,
                containerDigest,
                dbSchemaVersion,
                sbomHash,
                registeredChecksum,
                authoritySignature,
                buildTimestamp != null ? buildTimestamp : Instant.now(),
                isActiveRelease
        );

        log.info("Recorded software build checksum version={} backendSha256={} registeredChecksum={}",
                version, backendSha256, registeredChecksum);
        return checksumRepository.save(record);
    }

    private String determineStatus(String actualSha256, String registeredChecksum) {
        if (registeredChecksum == null || registeredChecksum.isBlank()) {
            return "NOT_REGISTERED";
        }
        if (actualSha256 != null && actualSha256.equalsIgnoreCase(registeredChecksum.trim())) {
            return "MATCH";
        }
        return "MISMATCH";
    }

    private String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
