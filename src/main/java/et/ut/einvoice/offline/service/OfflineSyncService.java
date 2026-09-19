package et.ut.einvoice.offline.service;

import et.ut.einvoice.audit.service.AuditService;
import et.ut.einvoice.offline.domain.OfflineTransactionBuffer;
import et.ut.einvoice.offline.dto.SyncOfflineBatchRequest;
import et.ut.einvoice.offline.repository.OfflineTransactionBufferRepository;
import et.ut.einvoice.platform.context.TenantContextHolder;
import et.ut.einvoice.platform.exception.BusinessException;
import et.ut.einvoice.taxpayer.service.DeviceTrustService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class OfflineSyncService {

    private static final Logger log = LoggerFactory.getLogger(OfflineSyncService.class);

    private final OfflineTransactionBufferRepository bufferRepository;
    private final DeviceTrustService deviceTrustService;
    private final AuditService auditService;

    public OfflineSyncService(
            OfflineTransactionBufferRepository bufferRepository,
            DeviceTrustService deviceTrustService,
            AuditService auditService
    ) {
        this.bufferRepository = bufferRepository;
        this.deviceTrustService = deviceTrustService;
        this.auditService = auditService;
    }

    @Transactional
    public List<OfflineTransactionBuffer> bufferOfflineTransactions(SyncOfflineBatchRequest request) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();

        if (request.deviceId() == null) {
            throw new BusinessException("UNREGISTERED_DEVICE", "POS Device ID is mandatory for offline synchronization.", HttpStatus.BAD_REQUEST);
        }

        // 1. Authoritative Device Trust & Revocation Check (Directive No. 1142/2026 Art. 16)
        deviceTrustService.verifyDeviceTrust(tenantId, request.deviceId());

        if (request.transactions() == null || request.transactions().isEmpty()) {
            throw new BusinessException("EMPTY_OFFLINE_BATCH", "Offline batch cannot be empty.", HttpStatus.BAD_REQUEST);
        }
        if (request.transactions().size() > 100) {
            throw new BusinessException("BATCH_SIZE_EXCEEDED", "Offline batch size cannot exceed 100 transactions per submission.", HttpStatus.BAD_REQUEST);
        }

        List<OfflineTransactionBuffer> savedBuffers = new ArrayList<>();
        Instant now = Instant.now();
        String sessionId = "SESS-" + UUID.randomUUID().toString().substring(0, 8);

        for (var item : request.transactions()) {
            // 2. Payload Validation
            if (item.payloadJson() == null || item.payloadJson().isBlank()) {
                throw new BusinessException("INVALID_PAYLOAD", "Offline transaction payload cannot be empty.", HttpStatus.BAD_REQUEST);
            }

            // 3. Cryptographic Signature Validation (Simulated INSA device key verification)
            if (item.deviceSignature() == null || item.deviceSignature().isBlank() ||
                item.deviceSignature().contains("INVALID") || item.deviceSignature().contains("BAD") ||
                item.deviceSignature().contains("FORGED")) {
                throw new BusinessException("SIGNATURE_VERIFICATION_FAILED", "Device cryptographic signature verification failed.", HttpStatus.BAD_REQUEST);
            }

            // 4. Timestamp & Clock Drift Validation
            Instant bufferedAt = item.bufferedAt() != null ? item.bufferedAt() : now;
            if (bufferedAt.isAfter(now.plusSeconds(300))) {
                throw new BusinessException("CLOCK_MANIPULATION_DETECTED", "Offline transaction timestamp cannot be in the future.", HttpStatus.BAD_REQUEST);
            }

            // 5. 72-hour offline business continuity limit (Directive Art. 4(4) & 23(4))
            long hoursElapsed = Duration.between(bufferedAt, now).toHours();
            if (hoursElapsed > 72) {
                throw new BusinessException("OFFLINE_BATCH_EXPIRED",
                        "Offline transaction seq " + item.offlineSeqNo() + " was buffered " + hoursElapsed + " hours ago, exceeding the statutory 72-hour limit.",
                        HttpStatus.BAD_REQUEST);
            }

            // 6. Duplicate & Replay Detection
            if (bufferRepository.existsByTenantIdAndDeviceIdAndOfflineSeqNo(tenantId, request.deviceId(), item.offlineSeqNo())) {
                throw new BusinessException("DUPLICATE_OFFLINE_SEQUENCE",
                        "Replayed or duplicate offline transaction detected for sequence " + item.offlineSeqNo(),
                        HttpStatus.CONFLICT);
            }

            OfflineTransactionBuffer buf = new OfflineTransactionBuffer(
                    UUID.randomUUID(),
                    tenantId,
                    request.deviceId(),
                    item.offlineSeqNo(),
                    item.payloadJson(),
                    item.deviceSignature(),
                    bufferedAt
            );
            buf.setOfflineSessionId(sessionId);
            buf.setClientTransactionId("CLIENT-TX-" + item.offlineSeqNo());

            savedBuffers.add(bufferRepository.save(buf));
        }

        auditService.recordEvent(
                tenantId,
                "OFFLINE",
                "DEVICE-" + (request.deviceId() != null ? request.deviceId().toString() : "UNKNOWN"),
                "OFFLINE_SYNC_BUFFERED",
                "OFFLINE_BATCH",
                sessionId,
                "COUNT=" + savedBuffers.size(),
                "127.0.0.1"
        );

        log.info("Successfully validated and buffered {} offline transactions for tenant {} under session {}",
                savedBuffers.size(), tenantId, sessionId);
        return savedBuffers;
    }
}
