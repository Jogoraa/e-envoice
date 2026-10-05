package et.ut.einvoice.offline.dto;

import et.ut.einvoice.offline.domain.DeviceOfflineAllocation;
import et.ut.einvoice.offline.domain.OfflineAllocationStatus;

import java.time.Instant;
import java.util.UUID;

public record DeviceOfflineAllocationResponseDto(
        UUID id,
        UUID tenantId,
        UUID deviceId,
        String allocationId,
        long rangeStart,
        long rangeEnd,
        long nextValue,
        long remainingQuota,
        Instant allocatedAt,
        Instant expiresAt,
        OfflineAllocationStatus status,
        String authorityRegistrationRef
) {
    public static DeviceOfflineAllocationResponseDto fromEntity(DeviceOfflineAllocation a) {
        return new DeviceOfflineAllocationResponseDto(
                a.getId(),
                a.getTenantId(),
                a.getDeviceId(),
                a.getAllocationId(),
                a.getRangeStart(),
                a.getRangeEnd(),
                a.getNextValue(),
                a.getRemainingCount(),
                a.getAllocatedAt(),
                a.getExpiresAt(),
                a.getStatus(),
                a.getAuthorityRegistrationRef()
        );
    }
}
