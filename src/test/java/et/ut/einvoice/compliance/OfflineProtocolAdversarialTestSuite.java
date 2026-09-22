package et.ut.einvoice.compliance;

import et.ut.einvoice.offline.dto.SyncOfflineBatchRequest;
import et.ut.einvoice.offline.service.OfflineSyncService;
import et.ut.einvoice.platform.context.TenantContext;
import et.ut.einvoice.platform.context.TenantContextHolder;
import et.ut.einvoice.platform.exception.BusinessException;
import et.ut.einvoice.taxpayer.service.DeviceTrustService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class OfflineProtocolAdversarialTestSuite {

    @Autowired
    private OfflineSyncService offlineSyncService;

    @Autowired
    private DeviceTrustService deviceTrustService;

    private UUID tenantId;
    private UUID validDeviceId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        validDeviceId = UUID.randomUUID();
        TenantContextHolder.setContext(TenantContext.createWithClient(
                tenantId, "POS_CLIENT_01", Set.of("ROLE_CASHIER"), Set.of("offline:sync"), UUID.randomUUID().toString()
        ));
    }

    @Test
    @DisplayName("Offline 1: Missing / Unregistered Device ID is rejected")
    void test_MissingDeviceId_Rejected() {
        var tx = new SyncOfflineBatchRequest.OfflineInvoiceItemDto(1L, Instant.now(), "{\"amount\":100}", "SIG-VALID-123");
        var batch = new SyncOfflineBatchRequest(null, List.of(tx));

        BusinessException ex = assertThrows(BusinessException.class, () -> offlineSyncService.bufferOfflineTransactions(batch));
        assertEquals("UNREGISTERED_DEVICE", ex.getCode());
    }

    @Test
    @DisplayName("Offline 2: Revoked device is barred with 403 Forbidden")
    void test_RevokedDevice_BarredWith403() {
        UUID revokedDevice = UUID.randomUUID();
        deviceTrustService.revokeDevice(tenantId, revokedDevice, "Device reported stolen", "SECURITY_ADMIN");

        var tx = new SyncOfflineBatchRequest.OfflineInvoiceItemDto(1L, Instant.now(), "{\"amount\":100}", "SIG-VALID-123");
        var batch = new SyncOfflineBatchRequest(revokedDevice, List.of(tx));

        BusinessException ex = assertThrows(BusinessException.class, () -> offlineSyncService.bufferOfflineTransactions(batch));
        assertEquals("DEVICE_REVOKED", ex.getCode());
    }

    @Test
    @DisplayName("Offline 3: Invalid cryptographic device signature is rejected")
    void test_InvalidSignature_Rejected() {
        var tx = new SyncOfflineBatchRequest.OfflineInvoiceItemDto(1L, Instant.now(), "{\"amount\":100}", "SIG-BAD-FORGED");
        var batch = new SyncOfflineBatchRequest(validDeviceId, List.of(tx));

        BusinessException ex = assertThrows(BusinessException.class, () -> offlineSyncService.bufferOfflineTransactions(batch));
        assertEquals("SIGNATURE_VERIFICATION_FAILED", ex.getCode());
    }

    @Test
    @DisplayName("Offline 4: Empty payload is rejected")
    void test_EmptyPayload_Rejected() {
        var tx = new SyncOfflineBatchRequest.OfflineInvoiceItemDto(1L, Instant.now(), "", "SIG-VALID-123");
        var batch = new SyncOfflineBatchRequest(validDeviceId, List.of(tx));

        BusinessException ex = assertThrows(BusinessException.class, () -> offlineSyncService.bufferOfflineTransactions(batch));
        assertEquals("INVALID_PAYLOAD", ex.getCode());
    }

    @Test
    @DisplayName("Offline 5: Replayed transaction with duplicate sequence number is blocked with 409 Conflict")
    void test_DuplicateSequenceReplay_BlockedWith409() {
        long sequence = System.currentTimeMillis();
        var tx1 = new SyncOfflineBatchRequest.OfflineInvoiceItemDto(sequence, Instant.now(), "{\"amount\":100}", "SIG-VALID-123");
        var batch1 = new SyncOfflineBatchRequest(validDeviceId, List.of(tx1));

        // First sync succeeds
        var buffers = offlineSyncService.bufferOfflineTransactions(batch1);
        assertEquals(1, buffers.size());

        // Second sync with identical sequence must be rejected as replay attack
        BusinessException ex = assertThrows(BusinessException.class, () -> offlineSyncService.bufferOfflineTransactions(batch1));
        assertEquals("DUPLICATE_OFFLINE_SEQUENCE", ex.getCode());
    }

    @Test
    @DisplayName("Offline 6: Transaction older than 72 hours exceeds statutory limit")
    void test_TransactionOlderThan72Hours_Rejected() {
        Instant oldTime = Instant.now().minus(73, ChronoUnit.HOURS);
        var tx = new SyncOfflineBatchRequest.OfflineInvoiceItemDto(1L, oldTime, "{\"amount\":100}", "SIG-VALID-123");
        var batch = new SyncOfflineBatchRequest(validDeviceId, List.of(tx));

        BusinessException ex = assertThrows(BusinessException.class, () -> offlineSyncService.bufferOfflineTransactions(batch));
        assertEquals("OFFLINE_BATCH_EXPIRED", ex.getCode());
    }

    @Test
    @DisplayName("Offline 7: Clock manipulation with future timestamp is detected and rejected")
    void test_FutureTimestamp_Rejected() {
        Instant futureTime = Instant.now().plus(1, ChronoUnit.HOURS);
        var tx = new SyncOfflineBatchRequest.OfflineInvoiceItemDto(1L, futureTime, "{\"amount\":100}", "SIG-VALID-123");
        var batch = new SyncOfflineBatchRequest(validDeviceId, List.of(tx));

        BusinessException ex = assertThrows(BusinessException.class, () -> offlineSyncService.bufferOfflineTransactions(batch));
        assertEquals("CLOCK_MANIPULATION_DETECTED", ex.getCode());
    }

    @Test
    @DisplayName("Offline 8: Compliant offline batch is buffered successfully")
    void test_CompliantBatch_BufferedSuccessfully() {
        long sequence = System.currentTimeMillis() + 99;
        var tx = new SyncOfflineBatchRequest.OfflineInvoiceItemDto(sequence, Instant.now().minus(2, ChronoUnit.HOURS), "{\"amount\":500}", "SIG-VALID-999");
        var batch = new SyncOfflineBatchRequest(validDeviceId, List.of(tx));

        var buffers = offlineSyncService.bufferOfflineTransactions(batch);
        assertNotNull(buffers);
        assertEquals(1, buffers.size());
        assertEquals("QUEUED", buffers.get(0).getSyncStatus());
    }
}
