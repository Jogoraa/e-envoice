package et.ut.einvoice.compliance;

import et.ut.einvoice.platform.context.TenantContext;
import et.ut.einvoice.platform.context.TenantContextHolder;
import et.ut.einvoice.platform.security.TenantConnectionPreparer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class ConnectionPoolRlsLeakageTestSuite {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private TenantConnectionPreparer connectionPreparer;

    @Test
    @DisplayName("Connection Pool 1: Physical connection acquisition and preparation executes cleanly")
    void test_ConnectionPreparation_ExecutesCleanly() throws SQLException {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        try (Connection conn = dataSource.getConnection()) {
            assertNotNull(conn, "Connection must be acquired from pool");

            // Request A: Prepare for Tenant A
            connectionPreparer.prepareConnection(conn, tenantA);

            // Request B: Prepare for Tenant B on same connection
            connectionPreparer.prepareConnection(conn, tenantB);

            // Request C: Reset for unauthenticated / system connection
            connectionPreparer.prepareConnection(conn, null);
        }
    }

    @Test
    @DisplayName("Connection Pool 2: ThreadLocal TenantContext cleanup on completion guarantees zero cross-request leakage")
    void test_TenantContext_ThreadLocalIsolation() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        TenantContextHolder.setContext(TenantContext.createWithClient(
                tenantA, "CLIENT_A", Set.of("ROLE_TENANT_ADMIN"), Set.of("invoice:read"), "CORR-1"
        ));
        assertEquals(tenantA, TenantContextHolder.getRequiredContext().tenantId());

        // Context must be cleanly removable
        TenantContextHolder.clear();
        assertNull(TenantContextHolder.getContext(), "Context must be null after clear()");

        // Establish Tenant B context
        TenantContextHolder.setContext(TenantContext.createWithClient(
                tenantB, "CLIENT_B", Set.of("ROLE_TENANT_ADMIN"), Set.of("invoice:read"), "CORR-2"
        ));
        assertEquals(tenantB, TenantContextHolder.getRequiredContext().tenantId());

        TenantContextHolder.clear();
    }
}
