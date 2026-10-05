package et.ut.einvoice.compliance.dto;

import java.time.Instant;

/**
 * System Checksum & Version Integrity response mandated by Directive No. 1142/2026 Art. 10(9), 11(2), 12(3).
 */
public record SystemChecksumResponseDto(
        String version,
        String gitSha,
        String backendSha256,
        String frontendBuildSha256,
        String containerDigest,
        String databaseSchemaVersion,
        String registeredChecksum,
        String checksumStatus,
        Instant buildTimestamp
) {}
