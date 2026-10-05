package et.ut.einvoice.offline.service;

import et.ut.einvoice.audit.service.AuditService;
import et.ut.einvoice.offline.domain.DeviceOfflineAllocation;
import et.ut.einvoice.offline.domain.OfflineAllocationStatus;
import et.ut.einvoice.offline.repository.DeviceOfflineAllocationRepository;
import et.ut.einvoice.platform.exception.BusinessException;
import et.ut.einvoice.taxpayer.service.DeviceTrustService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages Pre-Allocated Temporary Offline Document Sequences.
 * Mandated by FDRE MoR Directive No. 1142/2026 Art. 4(4) & Art. 22.
 */
@Service
public class DeviceOfflineAllocationService {

    private static final Logger log = LoggerFactory.getLogger(DeviceOfflineAllocationService.class);
    private static final long INITIAL_BASELINE_SEQUENCE = 1000L;
    private static final long MAX_BLOCK_SIZE = 1000L;

    private final DeviceOfflineAllocationRepository allocationRepository;
    private final DeviceTrustService deviceTrustService;
    private final AuditService auditService;

    public DeviceOfflineAllocationService(
            DeviceOfflineAllocationRepository allocationRepository,
            DeviceTrustService deviceTrustService,
            AuditService auditService
    ) {
        this.allocationRepository = allocationRepository;
        this.deviceTrustService = deviceTrustService;
        this.auditService = auditService;
    }

    @Transactional
    public synchronized DeviceOfflineAllocation allocateRange(
            UUID tenantId,
            UUID deviceId,
            long blockSize,
            Duration validityDuration,
            String authorityRef
    ) {
        if (blockSize <= 0 || blockSize > MAX_BLOCK_SIZE) {
            throw new BusinessException("INVALID_BLOCK_SIZE",
                    "Offline allocation block size must be between 1 and " + MAX_BLOCK_SIZE,
                    HttpStatus.BAD_REQUEST);
        }

        // Verify device trust & registration
        deviceTrustService.verifyDeviceTrust(tenantId, deviceId);

        // Expire any existing active allocations for this device that have passed their deadline
        allocationRepository.findFirstByTenantIdAndDeviceIdAndStatusInOrderByAllocatedAtDesc(
                tenantId, deviceId, List.of(OfflineAllocationStatus.ACTIVE, OfflineAllocationStatus.ALLOCATED)
        ).ifPresent(existing -> {
            if (existing.isExpired()) {
                existing.setStatus(OfflineAllocationStatus.EXPIRED);
                allocationRepository.save(existing);
            }
        });

        long maxEnd = allocationRepository.findMaxRangeEndByTenantId(tenantId);
        long rangeStart = (maxEnd == 0) ? INITIAL_BASELINE_SEQUENCE : maxEnd + 1;
        long rangeEnd = rangeStart + blockSize - 1;

        if (allocationRepository.existsOverlappingRange(tenantId, rangeStart, rangeEnd)) {
            throw new BusinessException("RANGE_OVERLAP_DETECTED",
                    "Computed sequence range [" + rangeStart + ", " + rangeEnd + "] overlaps an existing allocation.",
                    HttpStatus.CONFLICT);
        }

        Duration duration = validityDuration != null ? validityDuration : Duration.ofDays(7);
        Instant expiresAt = Instant.now().plus(duration);
        String allocationId = "ALLOC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        DeviceOfflineAllocation allocation = new DeviceOfflineAllocation(
                UUID.randomUUID(),
                tenantId,
                deviceId,
                allocationId,
                rangeStart,
                rangeEnd,
                expiresAt,
                authorityRef
        );

        DeviceOfflineAllocation saved = allocationRepository.save(allocation);

        auditService.recordEvent(
                tenantId,
                "DEVICE-" + deviceId,
                "DEVICE_OFFLINE_RANGE_ALLOCATED",
                "ALLOCATION",
                allocationId,
                "START=" + rangeStart + ",END=" + rangeEnd + ",BLOCK=" + blockSize
        );

        log.info("Allocated offline range [{}-{}] for tenant {} device {} under allocation {}",
                rangeStart, rangeEnd, tenantId, deviceId, allocationId);
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<DeviceOfflineAllocation> getActiveAllocation(UUID tenantId, UUID deviceId) {
        Optional<DeviceOfflineAllocation> allocOpt = allocationRepository.findFirstByTenantIdAndDeviceIdAndStatusInOrderByAllocatedAtDesc(
                tenantId, deviceId, List.of(OfflineAllocationStatus.ACTIVE, OfflineAllocationStatus.ALLOCATED)
        );

        if (allocOpt.isPresent()) {
            DeviceOfflineAllocation alloc = allocOpt.get();
            if (alloc.isExpired()) {
                return Optional.empty();
            }
            return Optional.of(alloc);
        }
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public boolean allocationExists(UUID tenantId, String allocationId) {
        if (allocationId == null || allocationId.isBlank()) {
            return false;
        }
        return allocationRepository.findByTenantIdAndAllocationId(tenantId, allocationId.trim()).isPresent();
    }

    @Transactional
    public void validateAndConsumeSequence(UUID tenantId, UUID deviceId, String allocationId, long sequenceNo) {
        DeviceOfflineAllocation allocation;

        if (allocationId != null && !allocationId.isBlank()) {
            allocation = allocationRepository.findByTenantIdAndAllocationId(tenantId, allocationId.trim())
                    .orElseThrow(() -> new BusinessException("ALLOCATION_NOT_FOUND",
                            "Offline allocation " + allocationId + " does not exist for tenant.", HttpStatus.BAD_REQUEST));
        } else {
            allocation = getActiveAllocation(tenantId, deviceId)
                    .orElseThrow(() -> new BusinessException("NO_ACTIVE_ALLOCATION",
                            "No active offline sequence allocation found for device " + deviceId +
                                    ". Statutory manual paper fallback required under Directive No. 1142/2026 Art. 22.",
                            HttpStatus.BAD_REQUEST));
        }

        if (!allocation.getDeviceId().equals(deviceId)) {
            throw new BusinessException("DEVICE_ALLOCATION_MISMATCH",
                    "Allocation " + allocation.getAllocationId() + " does not belong to device " + deviceId,
                    HttpStatus.FORBIDDEN);
        }

        if (allocation.getStatus() == OfflineAllocationStatus.REVOKED) {
            throw new BusinessException("ALLOCATION_REVOKED",
                    "Offline allocation " + allocation.getAllocationId() + " has been revoked.", HttpStatus.FORBIDDEN);
        }

        if (allocation.isExpired()) {
            allocation.setStatus(OfflineAllocationStatus.EXPIRED);
            allocationRepository.save(allocation);
            throw new BusinessException("ALLOCATION_EXPIRED",
                    "Offline sequence allocation expired. Synchronization or manual paper fallback required under Art. 22.",
                    HttpStatus.BAD_REQUEST);
        }

        if (sequenceNo < allocation.getRangeStart() || sequenceNo > allocation.getRangeEnd()) {
            throw new BusinessException("SEQUENCE_OUT_OF_RANGE",
                    "Offline sequence " + sequenceNo + " is outside assigned range [" +
                            allocation.getRangeStart() + ", " + allocation.getRangeEnd() + "].",
                    HttpStatus.BAD_REQUEST);
        }

        if (sequenceNo < allocation.getNextValue()) {
            throw new BusinessException("SEQUENCE_ALREADY_USED",
                    "Offline sequence " + sequenceNo + " has already been consumed or registered.",
                    HttpStatus.CONFLICT);
        }

        allocation.setNextValue(sequenceNo + 1);
        if (allocation.getNextValue() > allocation.getRangeEnd()) {
            allocation.setStatus(OfflineAllocationStatus.EXHAUSTED);
            log.warn("Offline allocation {} for device {} is now EXHAUSTED", allocation.getAllocationId(), deviceId);
        }
        allocationRepository.save(allocation);
    }

    @Transactional
    public void revokeAllocation(UUID tenantId, String allocationId, String reason) {
        DeviceOfflineAllocation allocation = allocationRepository.findByTenantIdAndAllocationId(tenantId, allocationId)
                .orElseThrow(() -> new BusinessException("ALLOCATION_NOT_FOUND",
                        "Allocation not found.", HttpStatus.NOT_FOUND));

        allocation.setStatus(OfflineAllocationStatus.REVOKED);
        allocationRepository.save(allocation);

        auditService.recordEvent(
                tenantId,
                "DEVICE-" + allocation.getDeviceId(),
                "DEVICE_OFFLINE_ALLOCATION_REVOKED",
                "ALLOCATION",
                allocationId,
                "REASON=" + reason
        );
    }
}
