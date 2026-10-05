package et.ut.einvoice.government.dto;

import et.ut.einvoice.government.domain.CredentialStatus;
import java.time.Instant;
import java.util.UUID;

public record TenantGovernmentCredentialMetadataResponse(
        UUID tenantId,
        String morSystemNumber,
        String sellerTin,
        String taxpayerRegistrationIdentity,
        String insaCertificateReference,
        String certificateSerial,
        Instant certificateExpiry,
        CredentialStatus credentialStatus,
        Instant lastValidatedAt,
        int keyVaultVersion,
        Instant createdAt,
        Instant updatedAt
) {}
