package et.ut.einvoice.government.dto;

import java.time.Instant;
import java.util.UUID;

public record ProvisionTenantCredentialRequest(
        String morSystemNumber,
        String sellerTin,
        String taxpayerRegistrationIdentity,
        String clientId,
        String clientSecret,
        String apiKey,
        String insaCertificateReference,
        String certificateSerial,
        Instant certificateExpiry
) {}
