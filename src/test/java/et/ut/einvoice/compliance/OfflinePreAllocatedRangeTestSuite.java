package et.ut.einvoice.compliance;

import et.ut.einvoice.offline.domain.DeviceOfflineAllocation;
import et.ut.einvoice.offline.domain.OfflineAllocationStatus;
import et.ut.einvoice.offline.repository.DeviceOfflineAllocationRepository;
import et.ut.einvoice.offline.service.DeviceOfflineAllocationService;
import et.ut.einvoice.platform.exception.BusinessException;
import et.ut.einvoice.taxpayer.domain.Device;
import et.ut.einvoice.taxpayer.repository.DeviceRepository;
import et.ut.einvoice.tenancy.domain.Tenant;
import et.ut.einvoice.tenancy.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class OfflinePreAllocatedRangeTestSuite {

    @Autowired
    private DeviceOfflineAllocationService allocationService;

    @Autowired
    private DeviceOfflineAllocationRepository allocationRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private TenantRepository tenantRepository;

    private static final java.util.concurrent.atomic.AtomicLong TIN_SEQ = new java.util.concurrent.atomic.AtomicLong(20000000L);

    private UUID tenantId;
    private UUID deviceId;

    @BeforeEach
    void setUp() {
        allocationRepository.deleteAll();

        tenantId = UUID.randomUUID();
        String tin = "09" + TIN_SEQ.incrementAndGet();
        Tenant tenant = new Tenant(tenantId, "ORG-01", "Test Offline Corp", "Test Trade", tin);
        tenantRepository.save(tenant);

        deviceId = UUID.randomUUID();
        Device device = new Device(deviceId, tenantId, "DEV-OFFLINE-POS-01", "POS", "ACTIVE");
        deviceRepository.save(device);
    }

    @Test
    @DisplayName("Stage 12: Allocate sequence range assigns discrete non-overlapping blocks")
    void test_AllocateRange_AssignsNonOverlappingBlocks() {
        DeviceOfflineAllocation alloc1 = allocationService.allocateRange(tenantId, deviceId, 50, Duration.ofDays(7), "MOR-APP-001");
        assertNotNull(alloc1);
        assertEquals(1000L, alloc1.getRangeStart());
        assertEquals(1049L, alloc1.getRangeEnd());
        assertEquals(1000L, alloc1.getNextValue());
        assertEquals(50L, alloc1.getRemainingCount());
        assertEquals(OfflineAllocationStatus.ACTIVE, alloc1.getStatus());

        // Allocate second range - must start immediately after previous range end
        DeviceOfflineAllocation alloc2 = allocationService.allocateRange(tenantId, deviceId, 30, Duration.ofDays(7), "MOR-APP-002");
        assertNotNull(alloc2);
        assertEquals(1050L, alloc2.getRangeStart());
        assertEquals(1079L, alloc2.getRangeEnd());
        assertEquals(1050L, alloc2.getNextValue());
        assertEquals(30L, alloc2.getRemainingCount());
    }

    @Test
    @DisplayName("Stage 12: Block size exceeding statutory limits is rejected")
    void test_AllocateRange_InvalidBlockSize_Rejected() {
        assertThrows(BusinessException.class, () ->
                allocationService.allocateRange(tenantId, deviceId, 0, Duration.ofDays(7), null));

        assertThrows(BusinessException.class, () ->
                allocationService.allocateRange(tenantId, deviceId, 1001, Duration.ofDays(7), null));
    }

    @Test
    @DisplayName("Stage 12: Consuming sequences validates bounds and advances nextValue")
    void test_ConsumeSequence_ValidProgression_Succeeds() {
        DeviceOfflineAllocation alloc = allocationService.allocateRange(tenantId, deviceId, 5, Duration.ofDays(7), null);

        // Consume sequence 1000
        assertDoesNotThrow(() ->
                allocationService.validateAndConsumeSequence(tenantId, deviceId, alloc.getAllocationId(), 1000L));

        DeviceOfflineAllocation updated = allocationRepository.findById(alloc.getId()).orElseThrow();
        assertEquals(1001L, updated.getNextValue());
        assertEquals(4L, updated.getRemainingCount());

        // Consume sequence 1001
        assertDoesNotThrow(() ->
                allocationService.validateAndConsumeSequence(tenantId, deviceId, alloc.getAllocationId(), 1001L));

        updated = allocationRepository.findById(alloc.getId()).orElseThrow();
        assertEquals(1002L, updated.getNextValue());
        assertEquals(3L, updated.getRemainingCount());
    }

    @Test
    @DisplayName("Stage 12: Sequence outside assigned range is rejected")
    void test_ConsumeSequence_OutOfRange_Rejected() {
        DeviceOfflineAllocation alloc = allocationService.allocateRange(tenantId, deviceId, 10, Duration.ofDays(7), null);

        // Sequence 999 is below range start (1000)
        BusinessException exBelow = assertThrows(BusinessException.class, () ->
                allocationService.validateAndConsumeSequence(tenantId, deviceId, alloc.getAllocationId(), 999L));
        assertEquals("SEQUENCE_OUT_OF_RANGE", exBelow.getCode());

        // Sequence 1010 is above range end (1009)
        BusinessException exAbove = assertThrows(BusinessException.class, () ->
                allocationService.validateAndConsumeSequence(tenantId, deviceId, alloc.getAllocationId(), 1010L));
        assertEquals("SEQUENCE_OUT_OF_RANGE", exAbove.getCode());
    }

    @Test
    @DisplayName("Stage 12: Sequence replay or already-consumed sequence is rejected")
    void test_ConsumeSequence_ReplayOrAlreadyUsed_Rejected() {
        DeviceOfflineAllocation alloc = allocationService.allocateRange(tenantId, deviceId, 10, Duration.ofDays(7), null);

        allocationService.validateAndConsumeSequence(tenantId, deviceId, alloc.getAllocationId(), 1000L);

        // Replay of sequence 1000
        BusinessException ex = assertThrows(BusinessException.class, () ->
                allocationService.validateAndConsumeSequence(tenantId, deviceId, alloc.getAllocationId(), 1000L));
        assertEquals("SEQUENCE_ALREADY_USED", ex.getCode());
    }

    @Test
    @DisplayName("Stage 12: Exhausting range transitions status to EXHAUSTED and blocks subsequent consumption")
    void test_ConsumeSequence_Exhaustion_TransitionsAndBlocks() {
        DeviceOfflineAllocation alloc = allocationService.allocateRange(tenantId, deviceId, 2, Duration.ofDays(7), null);
        String allocId = alloc.getAllocationId();

        allocationService.validateAndConsumeSequence(tenantId, deviceId, allocId, 1000L);
        allocationService.validateAndConsumeSequence(tenantId, deviceId, allocId, 1001L);

        DeviceOfflineAllocation exhausted = allocationRepository.findById(alloc.getId()).orElseThrow();
        assertEquals(OfflineAllocationStatus.EXHAUSTED, exhausted.getStatus());
        assertEquals(0L, exhausted.getRemainingCount());

        // Subsequent consumption must fail with fallback requirement
        BusinessException ex = assertThrows(BusinessException.class, () ->
                allocationService.validateAndConsumeSequence(tenantId, deviceId, allocId, 1002L));
        assertTrue(ex.getCode().equals("SEQUENCE_OUT_OF_RANGE") || ex.getCode().equals("NO_ACTIVE_ALLOCATION"));
    }

    @Test
    @DisplayName("Stage 12: Expired allocation blocks offline issuance and demands fallback")
    void test_ConsumeSequence_ExpiredAllocation_BlocksIssuance() {
        DeviceOfflineAllocation alloc = allocationService.allocateRange(tenantId, deviceId, 10, Duration.ofDays(7), null);
        alloc.setExpiresAt(Instant.now().minusSeconds(60));
        allocationRepository.save(alloc);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                allocationService.validateAndConsumeSequence(tenantId, deviceId, alloc.getAllocationId(), 1000L));
        assertEquals("ALLOCATION_EXPIRED", ex.getCode());
    }

    @Test
    @DisplayName("Stage 12: Revoked allocation blocks offline issuance")
    void test_ConsumeSequence_RevokedAllocation_BlocksIssuance() {
        DeviceOfflineAllocation alloc = allocationService.allocateRange(tenantId, deviceId, 10, Duration.ofDays(7), null);
        allocationService.revokeAllocation(tenantId, alloc.getAllocationId(), "Security compromise suspicion");

        BusinessException ex = assertThrows(BusinessException.class, () ->
                allocationService.validateAndConsumeSequence(tenantId, deviceId, alloc.getAllocationId(), 1000L));
        assertEquals("ALLOCATION_REVOKED", ex.getCode());
    }

    @Test
    @DisplayName("Stage 12: Cross-tenant device allocation isolation enforced")
    void test_CrossTenant_AllocationIsolation() {
        UUID otherTenantId = UUID.randomUUID();
        String otherTin = "08" + TIN_SEQ.incrementAndGet();
        Tenant otherTenant = new Tenant(otherTenantId, "ORG-02", "Cross Tenant PLC", "Cross Trade", otherTin);
        tenantRepository.save(otherTenant);

        DeviceOfflineAllocation alloc = allocationService.allocateRange(tenantId, deviceId, 10, Duration.ofDays(7), null);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                allocationService.validateAndConsumeSequence(otherTenantId, deviceId, alloc.getAllocationId(), 1000L));
        assertEquals("ALLOCATION_NOT_FOUND", ex.getCode());
    }

    @Test
    @DisplayName("Stage 12: Concurrent allocation requests never produce overlapping sequence ranges")
    void test_ConcurrentAllocation_GuaranteesDiscreteRanges() throws Exception {
        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<DeviceOfflineAllocation>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                latch.await();
                return allocationService.allocateRange(tenantId, deviceId, 25, Duration.ofDays(7), null);
            }));
        }

        latch.countDown();
        List<DeviceOfflineAllocation> allocations = new ArrayList<>();
        for (Future<DeviceOfflineAllocation> f : futures) {
            allocations.add(f.get());
        }
        executor.shutdown();

        assertEquals(threadCount, allocations.size());

        // Sort by rangeStart
        allocations.sort(Comparator.comparingLong(DeviceOfflineAllocation::getRangeStart));

        for (int i = 0; i < allocations.size() - 1; i++) {
            DeviceOfflineAllocation current = allocations.get(i);
            DeviceOfflineAllocation next = allocations.get(i + 1);

            assertTrue(current.getRangeEnd() < next.getRangeStart(),
                    "Allocation " + current.getAllocationId() + " rangeEnd " + current.getRangeEnd() +
                            " overlaps next rangeStart " + next.getRangeStart());
            assertEquals(current.getRangeEnd() + 1, next.getRangeStart(),
                    "Contiguous allocation ranges expected");
        }
    }
}
