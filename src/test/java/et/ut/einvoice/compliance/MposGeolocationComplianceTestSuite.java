package et.ut.einvoice.compliance;

import et.ut.einvoice.government.domain.GovernmentRegistrationProvider;
import et.ut.einvoice.invoicing.domain.TransactionType;
import et.ut.einvoice.invoicing.dto.CreateInvoiceRequest;
import et.ut.einvoice.invoicing.dto.InvoiceResponseDto;
import et.ut.einvoice.invoicing.service.InvoiceService;
import et.ut.einvoice.platform.context.TenantContext;
import et.ut.einvoice.platform.context.TenantContextHolder;
import et.ut.einvoice.platform.exception.BusinessException;
import et.ut.einvoice.platform.outbox.repository.OutboxEventRepository;
import et.ut.einvoice.taxpayer.domain.*;
import et.ut.einvoice.taxpayer.repository.*;
import et.ut.einvoice.taxpayer.service.GeofenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

@SpringBootTest
@ActiveProfiles("test")
public class MposGeolocationComplianceTestSuite {

    @Autowired
    private GeofenceService geofenceService;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private GeofenceRepository geofenceRepository;

    @Autowired
    private DeviceTelemetryLogRepository telemetryLogRepository;

    @Autowired
    private DeviceRevocationRepository revocationRepository;

    @Autowired
    private TaxpayerProfileRepository taxpayerProfileRepository;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @MockBean
    private GovernmentRegistrationProvider governmentRegistrationProvider;

    private UUID tenantA;
    private UUID tenantB;
    private UUID mposDeviceId;
    private UUID desktopDeviceId;
    private Geofence addisBoleGeofence;

    // GeoJSON polygon covering Addis Ababa Bole area
    // Ring: (lon, lat) [ [38.78, 8.98], [38.82, 8.98], [38.82, 9.02], [38.78, 9.02], [38.78, 8.98] ]
    private static final String BOLE_GEOJSON = """
            {
              "type": "Polygon",
              "coordinates": [
                [
                  [38.780000, 8.980000],
                  [38.820000, 8.980000],
                  [38.820000, 9.020000],
                  [38.780000, 9.020000],
                  [38.780000, 8.980000]
                ]
              ]
            }
            """;

    @BeforeEach
    void setUp() {
        telemetryLogRepository.deleteAll();
        deviceRepository.deleteAll();
        geofenceRepository.deleteAll();
        revocationRepository.deleteAll();
        taxpayerProfileRepository.deleteAll();

        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();

        // Seed Taxpayer Profile for Tenant A
        TaxpayerProfile profile = new TaxpayerProfile(
                tenantA, "0012345678", "VAT-123456", "Alpha General Trading PLC", "Alpha Store",
                "Addis Ababa", "Bole", "0911223344", "alpha@store.et", "SYS-ALPHA-01", "MPOS"
        );
        taxpayerProfileRepository.save(profile);

        // Seed Geofence for Tenant A
        addisBoleGeofence = new Geofence(UUID.randomUUID(), tenantA, "Bole Commercial District", BOLE_GEOJSON);
        geofenceRepository.save(addisBoleGeofence);

        // Seed Regulated mPOS Device bound to Geofence
        mposDeviceId = UUID.randomUUID();
        Device mpos = new Device(mposDeviceId, tenantA, "DEV-MPOS-001", "MPOS", "SYS-ALPHA-01");
        mpos.setAuthorizedGeofenceId(addisBoleGeofence.getId());
        deviceRepository.save(mpos);

        // Seed Desktop Fixed Terminal
        desktopDeviceId = UUID.randomUUID();
        Device desktop = new Device(desktopDeviceId, tenantA, "DEV-DESK-001", "DESKTOP", "SYS-ALPHA-01");
        deviceRepository.save(desktop);

        // Mock EIRS Registration
        Mockito.when(governmentRegistrationProvider.registerInvoice(any(), any(), anyString()))
                .thenAnswer(inv -> GovernmentRegistrationProvider.GovernmentRegistrationResult.success(
                        "IRN-GEO-" + UUID.randomUUID().toString().substring(0, 8),
                        "RRN-GEO-" + UUID.randomUUID().toString().substring(0, 8),
                        "2026-10-05T10:00:00Z", "QR-GEO", "sig"
                ));
    }

    private void setSecurityContext(UUID tenantId, UUID deviceId) {
        TenantContext ctx = new TenantContext(
                tenantId, tenantId.toString(), null, "cashier.alpha", "POS_CLIENT",
                Set.of("ROLE_CASHIER"), Collections.emptySet(), deviceId, UUID.randomUUID().toString()
        );
        TenantContextHolder.setContext(ctx);
        var auth = new UsernamePasswordAuthenticationToken("cashier.alpha", "pass", List.of(new SimpleGrantedAuthority("ROLE_CASHIER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private CreateInvoiceRequest createRequest(Double lat, Double lon) {
        return new CreateInvoiceRequest(
                TransactionType.B2C, "CASH", "IMMEDIATE",
                new CreateInvoiceRequest.BuyerRequest("Walk-in", null, null, null, null, null, null, "ET", null, null),
                List.of(new CreateInvoiceRequest.LineItemRequest(
                        "ITEM-01", "Goods item", null, "PCS", BigDecimal.ONE, new BigDecimal("100.00"),
                        BigDecimal.ZERO, "VAT15", BigDecimal.ZERO
                )),
                lat, lon, "DOC-GEO-" + UUID.randomUUID().toString().substring(0, 6), false
        );
    }

    @Test
    @DisplayName("Stage 8: Inside Geofence Polygon - Transaction succeeds and captures GPS")
    void test_InsideGeofence_TransactionSucceeds() {
        setSecurityContext(tenantA, mposDeviceId);

        // Coordinates strictly inside Bole polygon (lat=9.00, lon=38.80)
        CreateInvoiceRequest req = createRequest(9.000000, 38.800000);
        InvoiceResponseDto response = invoiceService.createAndRegisterInvoice(req, "IDEM-GEO-01");

        assertNotNull(response);
        assertTrue(response.irn().startsWith("IRN-GEO-"));

        // Verify telemetry log recorded
        List<DeviceTelemetryLog> logs = telemetryLogRepository.findAllByTenantIdAndDeviceIdOrderByCapturedAtDesc(tenantA, mposDeviceId);
        assertFalse(logs.isEmpty());
        assertTrue(logs.get(0).isInsideGeofence());
        assertEquals("TRANSACTION", logs.get(0).getTelemetrySource());

        // Verify Device last coordinates updated
        Device updatedDev = deviceRepository.findById(mposDeviceId).orElseThrow();
        assertEquals(9.000000, updatedDev.getLastLatitude());
        assertEquals(38.800000, updatedDev.getLastLongitude());
    }

    @Test
    @DisplayName("Stage 8: Outside Geofence Polygon - Fails with OUT_OF_GEOFENCE_VIOLATION")
    void test_OutsideGeofence_FailsClosed() {
        setSecurityContext(tenantA, mposDeviceId);

        // Coordinates outside Bole polygon (e.g. Hawassa: lat=7.05, lon=38.47)
        CreateInvoiceRequest req = createRequest(7.050000, 38.470000);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                invoiceService.createAndRegisterInvoice(req, "IDEM-GEO-FAIL")
        );
        assertEquals("OUT_OF_GEOFENCE_VIOLATION", ex.getCode());
    }

    @Test
    @DisplayName("Stage 8: On Polygon Edge - Accepted as inside geofence")
    void test_PolygonEdge_Accepted() {
        setSecurityContext(tenantA, mposDeviceId);

        // Exactly on horizontal boundary segment: lat=8.980000, lon=38.800000
        CreateInvoiceRequest req = createRequest(8.980000, 38.800000);
        InvoiceResponseDto response = invoiceService.createAndRegisterInvoice(req, "IDEM-GEO-EDGE");

        assertNotNull(response);
    }

    @Test
    @DisplayName("Stage 8: Regulated mPOS Missing Coordinates - Fails with MANDATORY_MPOS_GPS_REQUIRED")
    void test_MposMissingGps_FailsClosed() {
        setSecurityContext(tenantA, mposDeviceId);

        // Null latitude & longitude on regulated mPOS
        CreateInvoiceRequest req = createRequest(null, null);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                invoiceService.createAndRegisterInvoice(req, "IDEM-GEO-NOGPS")
        );
        assertEquals("MANDATORY_MPOS_GPS_REQUIRED", ex.getCode());
    }

    @Test
    @DisplayName("Stage 8: Desktop Fixed Terminal - Mandatory GPS is waived under Art. 4(5)")
    void test_DesktopTerminal_GpsWaived() {
        setSecurityContext(tenantA, desktopDeviceId);

        // Desktop terminal without GPS succeeds
        CreateInvoiceRequest req = createRequest(null, null);
        InvoiceResponseDto response = invoiceService.createAndRegisterInvoice(req, "IDEM-DESK-01");

        assertNotNull(response);
    }

    @Test
    @DisplayName("Stage 8: Inaccurate GPS Coordinates (> 100m) - Fails with INACCURATE_GPS_COORDINATES")
    void test_InaccurateGps_FailsClosed() {
        // Direct call to validateMposTransaction with accuracy 150m
        BusinessException ex = assertThrows(BusinessException.class, () ->
                geofenceService.validateMposTransaction(
                        tenantA, mposDeviceId, 9.00, 38.80, 150.0, Instant.now()
                )
        );
        assertEquals("INACCURATE_GPS_COORDINATES", ex.getCode());
    }

    @Test
    @DisplayName("Stage 8: Device Tenant Mismatch (Spoofing) - Fails with DEVICE_TENANT_MISMATCH")
    void test_DeviceTenantMismatch_FailsClosed() {
        // Tenant B attempts to use Tenant A's device
        BusinessException ex = assertThrows(BusinessException.class, () ->
                geofenceService.validateMposTransaction(
                        tenantB, mposDeviceId, 9.00, 38.80, 10.0, Instant.now()
                )
        );
        assertEquals("DEVICE_TENANT_MISMATCH", ex.getCode());
    }

    @Test
    @DisplayName("Stage 8: Revoked Device - Fails with DEVICE_REVOKED")
    void test_RevokedDevice_FailsClosed() {
        Device mpos = deviceRepository.findById(mposDeviceId).orElseThrow();
        mpos.setRegistrationStatus(DeviceRegistrationStatus.REVOKED);
        deviceRepository.save(mpos);

        setSecurityContext(tenantA, mposDeviceId);
        CreateInvoiceRequest req = createRequest(9.000000, 38.800000);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                invoiceService.createAndRegisterInvoice(req, "IDEM-REVOKED")
        );
        assertEquals("DEVICE_REVOKED", ex.getCode());
    }

    @Test
    @DisplayName("Stage 8: Periodic Background Telemetry - Records Log and Emits Durable Outbox Event")
    void test_PeriodicTelemetry_HeartbeatRecordedAndOutboxEmitted() {
        DeviceTelemetryLog telemetry = geofenceService.recordTelemetryHeartbeat(
                tenantA, mposDeviceId, 9.005000, 38.805000, 5.0, Instant.now(), 88
        );

        assertNotNull(telemetry);
        assertTrue(telemetry.isInsideGeofence());
        assertEquals("HEARTBEAT", telemetry.getTelemetrySource());
        assertEquals(88, telemetry.getBatteryLevel());

        // Verify outbox event emitted
        var outboxEvents = outboxEventRepository.findByTenantIdAndAggregateId(tenantA, mposDeviceId.toString());
        boolean foundTelemetryEvent = outboxEvents.stream()
                .anyMatch(e -> "DEVICE_TELEMETRY".equals(e.getEventType()));
        assertTrue(foundTelemetryEvent, "Durable outbox event for device telemetry must be emitted");
    }
}
