package et.ut.einvoice.audit.h2;

import org.h2.api.Trigger;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * H2 Database Trigger enforcing append-only immutability for the testing profile.
 * Rejects native SQL UPDATE and DELETE statements on the audit_events table.
 */
public class H2AuditImmutabilityTrigger implements Trigger {

    @Override
    public void init(Connection conn, String schemaName, String triggerName, String tableName, boolean before, int type) throws SQLException {
        // Trigger initialization
    }

    @Override
    public void fire(Connection conn, Object[] oldRow, Object[] newRow) throws SQLException {
        if (oldRow != null && newRow != null) {
            throw new SQLException("AUDIT_TRAIL_IMMUTABLE: Native SQL UPDATE rejected on audit_events", "23506");
        }
        if (oldRow != null && newRow == null) {
            throw new SQLException("AUDIT_TRAIL_IMMUTABLE: Native SQL DELETE rejected on audit_events", "23506");
        }
    }

    @Override
    public void close() throws SQLException {}

    @Override
    public void remove() throws SQLException {}
}
