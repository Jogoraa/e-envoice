package et.ut.einvoice.platform.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Executes transaction-local PostgreSQL session variable setting:
 * SELECT set_config('app.current_tenant_id', :tenantId, true);
 *
 * Setting 'is_local = true' guarantees that the setting is transaction-scoped.
 * Upon transaction commit or rollback, PostgreSQL automatically resets the setting,
 * completely preventing tenant context leakage when physical connections are returned
 * to the HikariCP connection pool.
 */
@Component
public class TenantConnectionPreparer {

    private static final Logger log = LoggerFactory.getLogger(TenantConnectionPreparer.class);

    public void prepareConnection(Connection connection, UUID tenantId) {
        if (connection == null) return;
        try {
            String dbProduct = connection.getMetaData().getDatabaseProductName();
            if (dbProduct != null && dbProduct.toLowerCase().contains("postgresql")) {
                String val = tenantId != null ? tenantId.toString() : "";
                try (PreparedStatement stmt = connection.prepareStatement("SELECT set_config('app.current_tenant_id', ?, true)")) {
                    stmt.setString(1, val);
                    stmt.execute();
                }
            }
        } catch (SQLException e) {
            log.warn("Could not set transaction-local app.current_tenant_id: {}", e.getMessage());
        }
    }
}
