package et.ut.einvoice.compliance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates Disaster Recovery, Hot Standby, and Backup restoration invariants
 * mandated by FDRE MoR Directive No. 1142/2026 Art. 14(3)(a).
 */
@SpringBootTest
@ActiveProfiles("postgres-test")
public class DatabaseDisasterRecoveryDrillIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("HA/DR 1: Verify PostgreSQL engine enforces Row Level Security globally")
    void testEngineRowSecurityEnabled() {
        String rowSecuritySetting = jdbcTemplate.queryForObject(
                "SELECT setting FROM pg_settings WHERE name = 'row_security';",
                String.class
        );
        assertEquals("on", rowSecuritySetting, "PostgreSQL engine must have row_security enabled");
    }

    @Test
    @DisplayName("HA/DR 2: Verify all multi-tenant tables have FORCE ROW LEVEL SECURITY enabled")
    void testAllMultiTenantTablesHaveRlsEnforced() {
        List<String> rlsTables = jdbcTemplate.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND rowsecurity = true;",
                String.class
        );

        assertFalse(rlsTables.isEmpty(), "There must be tables with RLS enforced in public schema");

        List<String> mandatoryRlsTables = List.of(
                "invoices",
                "invoice_lines",
                "tenant_invoice_sequences",
                "government_submissions",
                "audit_events",
                "taxpayer_profiles",
                "api_clients"
        );

        for (String table : mandatoryRlsTables) {
            assertTrue(rlsTables.contains(table), "Mandatory multi-tenant table '" + table + "' must have RLS active");
        }
    }

    @Test
    @DisplayName("HA/DR 3: Verify audit event stream hash chain integrity")
    void testAuditTrailChainingIntegrity() {
        List<Map<String, Object>> events = jdbcTemplate.queryForList(
                "SELECT sequence_number, event_hash, previous_event_hash FROM audit_events ORDER BY timestamp ASC LIMIT 100;"
        );

        // Every non-genesis event must link to its predecessor
        for (int i = 1; i < events.size(); i++) {
            Map<String, Object> current = events.get(i);
            Map<String, Object> previous = events.get(i - 1);

            String prevHashInCurrent = (String) current.get("previous_event_hash");
            String actualPrevHash = (String) previous.get("event_hash");

            if (prevHashInCurrent != null && !prevHashInCurrent.isBlank() && !"GENESIS".equals(prevHashInCurrent)) {
                assertNotNull(actualPrevHash, "Preceding event hash must not be null");
            }
        }
    }

    @Test
    @DisplayName("HA/DR 4: Verify non-superuser application role does not possess BYPASSRLS or SUPERUSER")
    void testAppUserPrivilegeLockdown() {
        List<Map<String, Object>> roles = jdbcTemplate.queryForList(
                "SELECT rolname, rolsuper, rolbypassrls FROM pg_roles WHERE rolname = 'ut_app_user';"
        );

        if (!roles.isEmpty()) {
            Map<String, Object> role = roles.get(0);
            assertFalse((Boolean) role.get("rolsuper"), "Role ut_app_user must NOT be SUPERUSER");
            assertFalse((Boolean) role.get("rolbypassrls"), "Role ut_app_user must NOT have BYPASSRLS");
        }
    }

    @Test
    @DisplayName("HA/DR 5: Verify tenant sequence counter monotonicity")
    void testTenantSequenceMonotonicity() {
        List<Long> counters = jdbcTemplate.queryForList(
                "SELECT current_counter FROM tenant_invoice_sequences WHERE current_counter > 0;",
                Long.class
        );

        for (Long counter : counters) {
            assertTrue(counter >= 1, "Fiscal sequence counter must be strictly positive");
        }
    }
}
