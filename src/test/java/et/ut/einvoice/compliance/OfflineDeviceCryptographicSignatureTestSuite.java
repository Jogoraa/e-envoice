package et.ut.einvoice.compliance;

import et.ut.einvoice.offline.dto.SyncOfflineBatchRequest;
import et.ut.einvoice.offline.repository.OfflineTransactionBufferRepository;
import et.ut.einvoice.offline.service.OfflineSyncService;
import et.ut.einvoice.platform.context.TenantContext;
import et.ut.einvoice.platform.context.TenantContextHolder;
import et.ut.einvoice.platform.exception.BusinessException;
import et.ut.einvoice.taxpayer.crypto.DeviceCryptographicService;
import et.ut.einvoice.taxpayer.crypto.DevicePublicKey;
import et.ut.einvoice.taxpayer.crypto.DevicePublicKeyRepository;
import et.ut.einvoice.taxpayer.crypto.OfflineTransactionEnvelope;
import et.ut.einvoice.taxpayer.domain.Device;
import et.ut.einvoice.taxpayer.repository.DeviceRepository;
import et.ut.einvoice.taxpayer.repository.DeviceRevocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class OfflineDeviceCryptographicSignatureTestSuite {

    @Autowired
    private OfflineSyncService offlineSyncService;

    @Autowired
    private DeviceCryptographicService deviceCryptoService;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private DevicePublicKeyRepository keyRepository;

    @Autowired
    private OfflineTransactionBufferRepository bufferRepository;

    @Autowired
    private DeviceRevocationRepository revocationRepository;

    private UUID tenantA;
    private UUID tenantB;
    private UUID deviceIdA;
    private UUID deviceIdB;
    private KeyPair deviceAKeyPair;

    @BeforeEach
    void setUp() throws Exception {
        bufferRepository.deleteAll();
        keyRepository.deleteAll();
        deviceRepository.deleteAll();
        revocationRepository.deleteAll();

        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();
        deviceIdA = UUID.randomUUID();
        deviceIdB = UUID.randomUUID();

        // Generate RSA-2048 keypair for device A
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        deviceAKeyPair = kpg.generateKeyPair();

        // Register device A for Tenant A
        Device devA = new Device(deviceIdA, tenantA, "DEV-OFFLINE-A", "MPOS", "SYS-001");
        deviceRepository.save(devA);

        // Register device B for Tenant B
        Device devB = new Device(deviceIdB, tenantB, "DEV-OFFLINE-B", "MPOS", "SYS-002");
        deviceRepository.save(devB);

        // Register Device A's public key in vault
        String pubKeyPem = toPem(deviceAKeyPair.getPublic());
        deviceCryptoService.registerDeviceKey(tenantA, deviceIdA, pubKeyPem, 1, "RSA-2048");

        // Set security context for Tenant A
        TenantContext ctx = new TenantContext(
                tenantA, tenantA.toString(), null, "cashier", "CLIENT_POS",
                Set.of("ROLE_CASHIER"), Collections.emptySet(), deviceIdA, UUID.randomUUID().toString()
        );
        TenantContextHolder.setContext(ctx);
        var auth = new UsernamePasswordAuthenticationToken("cashier", "pass", List.of(new SimpleGrantedAuthority("ROLE_CASHIER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private String toPem(PublicKey pubKey) {
        String base64 = Base64.getEncoder().encodeToString(pubKey.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----";
    }

    private String signEnvelope(OfflineTransactionEnvelope envelope, PrivateKey privateKey) throws Exception {
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(privateKey);
        signer.update(envelope.toCanonicalString().getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signer.sign());
    }

    private String sha256(String data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] b = md.digest(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    @Test
    @DisplayName("Stage 10: Valid Offline Cryptographic Signature - Synchronizes successfully")
    void test_ValidCryptographicSignature_SynchronizesSuccessfully() throws Exception {
        String payload = "{\"docNo\":\"OFF-001\",\"grandTotal\":250.00,\"tax\":32.61}";
        String digest = sha256(payload);
        Instant now = Instant.now();

        OfflineTransactionEnvelope envelope = new OfflineTransactionEnvelope(
                tenantA, deviceIdA, "OP-101", now, digest, "ALLOC-01", 101L, "NONCE-101", 1, null
        );
        String sig = signEnvelope(envelope, deviceAKeyPair.getPrivate());

        SyncOfflineBatchRequest.OfflineInvoiceItemDto item = new SyncOfflineBatchRequest.OfflineInvoiceItemDto(
                101L, now, payload, sig, "OP-101", digest, "ALLOC-01", "NONCE-101", 1
        );

        SyncOfflineBatchRequest req = new SyncOfflineBatchRequest(deviceIdA, List.of(item));
        var buffers = offlineSyncService.bufferOfflineTransactions(req);

        assertEquals(1, buffers.size());
        assertEquals("UPLOADED", buffers.get(0).getLifecycleState());
    }

    @Test
    @DisplayName("Stage 10: Modified Amount (Payload Tampered) - Fails with PAYLOAD_TAMPERED")
    void test_ModifiedPayload_FailsVerification() throws Exception {
        String originalPayload = "{\"docNo\":\"OFF-001\",\"grandTotal\":250.00}";
        String tamperedPayload = "{\"docNo\":\"OFF-001\",\"grandTotal\":9999.00}";
        String digest = sha256(originalPayload);
        Instant now = Instant.now();

        OfflineTransactionEnvelope envelope = new OfflineTransactionEnvelope(
                tenantA, deviceIdA, "OP-102", now, digest, "ALLOC-01", 102L, "NONCE-102", 1, null
        );
        String sig = signEnvelope(envelope, deviceAKeyPair.getPrivate());

        // Submit tampered payload with original envelope digest/sig
        SyncOfflineBatchRequest.OfflineInvoiceItemDto item = new SyncOfflineBatchRequest.OfflineInvoiceItemDto(
                102L, now, tamperedPayload, sig, "OP-102", digest, "ALLOC-01", "NONCE-102", 1
        );

        BusinessException ex = assertThrows(BusinessException.class, () ->
                offlineSyncService.bufferOfflineTransactions(new SyncOfflineBatchRequest(deviceIdA, List.of(item)))
        );
        assertEquals("PAYLOAD_TAMPERED", ex.getCode());
    }

    @Test
    @DisplayName("Stage 10: Modified Timestamp (Altered Envelope) - Fails with SIGNATURE_VERIFICATION_FAILED")
    void test_ModifiedTimestamp_FailsSignature() throws Exception {
        String payload = "{\"docNo\":\"OFF-003\",\"grandTotal\":100.00}";
        String digest = sha256(payload);
        Instant originalTime = Instant.now().minusSeconds(60);
        Instant alteredTime = Instant.now();

        // Sign with originalTime
        OfflineTransactionEnvelope envelope = new OfflineTransactionEnvelope(
                tenantA, deviceIdA, "OP-103", originalTime, digest, "ALLOC-01", 103L, "NONCE-103", 1, null
        );
        String sig = signEnvelope(envelope, deviceAKeyPair.getPrivate());

        // Submit with altered bufferedAt
        SyncOfflineBatchRequest.OfflineInvoiceItemDto item = new SyncOfflineBatchRequest.OfflineInvoiceItemDto(
                103L, alteredTime, payload, sig, "OP-103", digest, "ALLOC-01", "NONCE-103", 1
        );

        BusinessException ex = assertThrows(BusinessException.class, () ->
                offlineSyncService.bufferOfflineTransactions(new SyncOfflineBatchRequest(deviceIdA, List.of(item)))
        );
        assertEquals("SIGNATURE_VERIFICATION_FAILED", ex.getCode());
    }

    @Test
    @DisplayName("Stage 10: Wrong Device - Fails with DEVICE_TENANT_MISMATCH")
    void test_WrongDevice_FailsClosed() throws Exception {
        String payload = "{\"docNo\":\"OFF-004\"}";
        String digest = sha256(payload);
        Instant now = Instant.now();

        // Device B belongs to Tenant B. Submitting in Tenant A context must fail.
        SyncOfflineBatchRequest.OfflineInvoiceItemDto item = new SyncOfflineBatchRequest.OfflineInvoiceItemDto(
                104L, now, payload, "DUMMY-SIG", "OP-104", digest, "ALLOC-01", "NONCE-104", 1
        );

        BusinessException ex = assertThrows(BusinessException.class, () ->
                offlineSyncService.bufferOfflineTransactions(new SyncOfflineBatchRequest(deviceIdB, List.of(item)))
        );
        assertEquals("DEVICE_TENANT_MISMATCH", ex.getCode());
    }

    @Test
    @DisplayName("Stage 10: Replayed Transaction Sequence - Fails with DUPLICATE_OFFLINE_SEQUENCE")
    void test_ReplayedSequence_FailsClosed() throws Exception {
        String payload = "{\"docNo\":\"OFF-005\",\"grandTotal\":50.00}";
        String digest = sha256(payload);
        Instant now = Instant.now();

        OfflineTransactionEnvelope envelope = new OfflineTransactionEnvelope(
                tenantA, deviceIdA, "OP-105", now, digest, "ALLOC-01", 105L, "NONCE-105", 1, null
        );
        String sig = signEnvelope(envelope, deviceAKeyPair.getPrivate());

        SyncOfflineBatchRequest.OfflineInvoiceItemDto item = new SyncOfflineBatchRequest.OfflineInvoiceItemDto(
                105L, now, payload, sig, "OP-105", digest, "ALLOC-01", "NONCE-105", 1
        );

        // First submission succeeds
        offlineSyncService.bufferOfflineTransactions(new SyncOfflineBatchRequest(deviceIdA, List.of(item)));

        // Replay submission fails
        BusinessException ex = assertThrows(BusinessException.class, () ->
                offlineSyncService.bufferOfflineTransactions(new SyncOfflineBatchRequest(deviceIdA, List.of(item)))
        );
        assertEquals("DUPLICATE_OFFLINE_SEQUENCE", ex.getCode());
    }

    @Test
    @DisplayName("Stage 10: Revoked Cryptographic Key - Fails with DEVICE_KEY_REVOKED")
    void test_RevokedKey_FailsClosed() throws Exception {
        // Revoke version 1
        deviceCryptoService.revokeDeviceKey(tenantA, deviceIdA, 1, "Suspicion of private key compromise");

        String payload = "{\"docNo\":\"OFF-006\"}";
        String digest = sha256(payload);
        Instant now = Instant.now();

        OfflineTransactionEnvelope envelope = new OfflineTransactionEnvelope(
                tenantA, deviceIdA, "OP-106", now, digest, "ALLOC-01", 106L, "NONCE-106", 1, null
        );
        String sig = signEnvelope(envelope, deviceAKeyPair.getPrivate());

        SyncOfflineBatchRequest.OfflineInvoiceItemDto item = new SyncOfflineBatchRequest.OfflineInvoiceItemDto(
                106L, now, payload, sig, "OP-106", digest, "ALLOC-01", "NONCE-106", 1
        );

        BusinessException ex = assertThrows(BusinessException.class, () ->
                offlineSyncService.bufferOfflineTransactions(new SyncOfflineBatchRequest(deviceIdA, List.of(item)))
        );
        assertEquals("DEVICE_KEY_REVOKED", ex.getCode());
    }

    @Test
    @DisplayName("Stage 10: Rotated Key - Succeeds with Key Version 2")
    void test_RotatedKey_SucceedsWithNewVersion() throws Exception {
        // Generate new KeyPair for version 2
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair v2KeyPair = kpg.generateKeyPair();

        // Rotate key to version 2
        DevicePublicKey rotated = deviceCryptoService.rotateDeviceKey(tenantA, deviceIdA, toPem(v2KeyPair.getPublic()));
        assertEquals(2, rotated.getKeyVersion());

        String payload = "{\"docNo\":\"OFF-007\",\"grandTotal\":300.00}";
        String digest = sha256(payload);
        Instant now = Instant.now();

        // Sign with version 2
        OfflineTransactionEnvelope envelope = new OfflineTransactionEnvelope(
                tenantA, deviceIdA, "OP-107", now, digest, "ALLOC-01", 107L, "NONCE-107", 2, null
        );
        String sig = signEnvelope(envelope, v2KeyPair.getPrivate());

        SyncOfflineBatchRequest.OfflineInvoiceItemDto item = new SyncOfflineBatchRequest.OfflineInvoiceItemDto(
                107L, now, payload, sig, "OP-107", digest, "ALLOC-01", "NONCE-107", 2
        );

        var buffers = offlineSyncService.bufferOfflineTransactions(new SyncOfflineBatchRequest(deviceIdA, List.of(item)));
        assertEquals(1, buffers.size());
    }

    @Test
    @DisplayName("Stage 10: Malformed Signature - Fails with SIGNATURE_VERIFICATION_FAILED")
    void test_MalformedSignature_FailsVerification() throws Exception {
        String payload = "{\"docNo\":\"OFF-008\"}";
        String digest = sha256(payload);
        Instant now = Instant.now();

        // Invalid base64 or garbage signature bytes
        SyncOfflineBatchRequest.OfflineInvoiceItemDto item = new SyncOfflineBatchRequest.OfflineInvoiceItemDto(
                108L, now, payload, "dGhpcyBpcyBub3QgYSB2YWxpZCBzaWduYXR1cmU=", "OP-108", digest, "ALLOC-01", "NONCE-108", 1
        );

        BusinessException ex = assertThrows(BusinessException.class, () ->
                offlineSyncService.bufferOfflineTransactions(new SyncOfflineBatchRequest(deviceIdA, List.of(item)))
        );
        assertEquals("SIGNATURE_VERIFICATION_FAILED", ex.getCode());
    }
}
