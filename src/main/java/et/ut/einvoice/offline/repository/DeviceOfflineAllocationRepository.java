package et.ut.einvoice.offline.repository;

import et.ut.einvoice.offline.domain.DeviceOfflineAllocation;
import et.ut.einvoice.offline.domain.OfflineAllocationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeviceOfflineAllocationRepository extends JpaRepository<DeviceOfflineAllocation, UUID> {

    Optional<DeviceOfflineAllocation> findByTenantIdAndAllocationId(UUID tenantId, String allocationId);

    Optional<DeviceOfflineAllocation> findFirstByTenantIdAndDeviceIdAndStatusInOrderByAllocatedAtDesc(
            UUID tenantId, UUID deviceId, Collection<OfflineAllocationStatus> statuses);

    List<DeviceOfflineAllocation> findAllByTenantIdAndDeviceIdOrderByAllocatedAtDesc(UUID tenantId, UUID deviceId);

    @Query("SELECT COALESCE(MAX(a.rangeEnd), 0) FROM DeviceOfflineAllocation a WHERE a.tenantId = :tenantId")
    long findMaxRangeEndByTenantId(@Param("tenantId") UUID tenantId);

    @Query("SELECT COUNT(a) > 0 FROM DeviceOfflineAllocation a WHERE a.tenantId = :tenantId AND " +
            "((:start BETWEEN a.rangeStart AND a.rangeEnd) OR (:end BETWEEN a.rangeStart AND a.rangeEnd) OR (a.rangeStart BETWEEN :start AND :end))")
    boolean existsOverlappingRange(@Param("tenantId") UUID tenantId, @Param("start") long start, @Param("end") long end);
}
