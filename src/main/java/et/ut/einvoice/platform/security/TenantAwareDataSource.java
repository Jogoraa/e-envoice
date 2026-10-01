package et.ut.einvoice.platform.security;

import et.ut.einvoice.platform.context.TenantContext;
import et.ut.einvoice.platform.context.TenantContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Tenant-Aware DataSource proxy ensuring PostgreSQL session variable 'app.current_tenant_id'
 * and 'app.is_platform_admin' are deterministically established on every acquired connection
 * and sanitized when the connection is returned to the pool.
 */
public class TenantAwareDataSource extends DelegatingDataSource {

    private static final Logger log = LoggerFactory.getLogger(TenantAwareDataSource.class);
    private static final UUID PLATFORM_ADMIN_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    public TenantAwareDataSource(DataSource targetDataSource) {
        super(targetDataSource);
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection conn = getTargetDataSource().getConnection();
        prepareTenantSession(conn);
        return wrapConnection(conn);
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        Connection conn = getTargetDataSource().getConnection(username, password);
        prepareTenantSession(conn);
        return wrapConnection(conn);
    }

    private void prepareTenantSession(Connection connection) {
        if (connection == null) return;
        try {
            String dbProduct = connection.getMetaData().getDatabaseProductName();
            if (dbProduct == null || !dbProduct.toLowerCase().contains("postgresql")) {
                return; // Non-PostgreSQL dialect (e.g. H2) does not support set_config
            }

            TenantContext ctx = TenantContextHolder.getContext();
            boolean isPlatformAdmin = ctx != null && (
                    PLATFORM_ADMIN_TENANT_ID.equals(ctx.tenantId()) ||
                    (ctx.roles() != null && (ctx.roles().contains("ROLE_PLATFORM_ADMIN") || ctx.roles().contains("ROLE_SAAS_ADMIN")))
            );

            String tenantIdStr = "";
            if (ctx != null && ctx.tenantId() != null && !PLATFORM_ADMIN_TENANT_ID.equals(ctx.tenantId())) {
                tenantIdStr = ctx.tenantId().toString();
            }

            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT set_config('app.is_platform_admin', ?, false), set_config('app.current_tenant_id', ?, false)")) {
                stmt.setString(1, isPlatformAdmin ? "true" : "false");
                stmt.setString(2, tenantIdStr);
                stmt.execute();
            }
        } catch (SQLException e) {
            log.warn("Could not set PostgreSQL session variable on connection checkout: {}", e.getMessage());
        }
    }

    private Connection wrapConnection(Connection target) {
        return (Connection) Proxy.newProxyInstance(
                TenantAwareDataSource.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                new ConnectionInvocationHandler(target)
        );
    }

    private static class ConnectionInvocationHandler implements InvocationHandler {
        private final Connection target;

        ConnectionInvocationHandler(Connection target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("close".equals(method.getName()) && (args == null || args.length == 0)) {
                try {
                    if (!target.isClosed()) {
                        String dbProduct = target.getMetaData().getDatabaseProductName();
                        if (dbProduct != null && dbProduct.toLowerCase().contains("postgresql")) {
                            try (PreparedStatement stmt = target.prepareStatement(
                                    "SELECT set_config('app.is_platform_admin', 'false', false), set_config('app.current_tenant_id', '', false)")) {
                                stmt.execute();
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("Failed to reset session variable before returning to pool: {}", e.getMessage());
                }
                return method.invoke(target, args);
            }
            return method.invoke(target, args);
        }
    }
}
