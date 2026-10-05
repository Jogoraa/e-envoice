package et.ut.einvoice.offline.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Pre-allocated offline document sequence range for authorized devices.
 * Implements FDRE MoR Directive No. 1142/2026 Art. 4(4) & Art. 22.
 */
@Entity
@Table(name = "device_offline_allocations")
public class DeviceOfflineAllocation {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "allocation_id", nullable = false, length = 64)
    private String allocationId;

    @Column(name = "range_start", nullable = false)
    private long rangeStart;

    @Column(name = "range_end", nullable = false)
    private long rangeEnd;

    @Column(name = "next_value", nullable = false)
    private long nextValue;

    @Column(name = "allocated_at", nullable = false)
    private Instant allocatedAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private OfflineAllocationStatus status = OfflineAllocationStatus.ALLOCATED;

    @Column(name = "authority_registration_ref", length = 128)
    private String authorityRegistrationRef;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public DeviceOfflineAllocation() {}

    public DeviceOfflineAllocation(UUID id, UUID tenantId, UUID deviceId, String allocationId,
                                   long rangeStart, long rangeEnd, Instant expiresAt,
                                   String authorityRegistrationRef) {
        this.id = id;
        this.tenantId = tenantId;
        this.deviceId = deviceId;
        this.allocationId = allocationId;
        this.rangeStart = rangeStart;
        this.rangeEnd = rangeEnd;
        this.nextValue = rangeStart;
        this.allocatedAt = Instant.now();
        this.expiresAt = expiresAt;
        this.status = OfflineAllocationStatus.ACTIVE;
        this.authorityRegistrationRef = authorityRegistrationRef;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(UUID deviceId) {
        this.deviceId = deviceId;
    }

    public String getAllocationId() {
        return allocationId;
    }

    public void setAllocationId(String allocationId) {
        this.allocationId = allocationId;
    }

    public long getRangeStart() {
        return rangeStart;
    }

    public void setRangeStart(long rangeStart) {
        this.rangeStart = rangeStart;
    }

    public long getRangeEnd() {
        return rangeEnd;
    }

    public void setRangeEnd(long rangeEnd) {
        this.rangeEnd = rangeEnd;
    }

    public long getNextValue() {
        return nextValue;
    }

    public void setNextValue(long nextValue) {
        this.nextValue = nextValue;
    }

    public Instant getAllocatedAt() {
        return allocatedAt;
    }

    public void setAllocatedAt(Instant allocatedAt) {
        this.allocatedAt = allocatedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public OfflineAllocationStatus getStatus() {
        return status;
    }

    public void setStatus(OfflineAllocationStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public String getAuthorityRegistrationRef() {
        return authorityRegistrationRef;
    }

    public void setAuthorityRegistrationRef(String authorityRegistrationRef) {
        this.authorityRegistrationRef = authorityRegistrationRef;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public boolean isExhausted() {
        return nextValue > rangeEnd;
    }

    public long getRemainingCount() {
        return Math.max(0, rangeEnd - nextValue + 1);
    }
}
