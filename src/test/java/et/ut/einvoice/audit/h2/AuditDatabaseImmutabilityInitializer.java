package et.ut.einvoice.audit.h2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Statement;

/**
 * Ensures H2 tables and database-level immutability triggers are installed for test profiles.
 */
@Component
@ConditionalOnClass(name = "org.h2.Driver")
public class AuditDatabaseImmutabilityInitializer implements ApplicationListener<ContextRefreshedEvent> {

    private static final Logger log = LoggerFactory.getLogger(AuditDatabaseImmutabilityInitializer.class);

    private final DataSource dataSource;

    public AuditDatabaseImmutabilityInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        registerTriggersIfH2();
    }

    public void registerTriggersIfH2() {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            if (meta.getDatabaseProductName() != null && meta.getDatabaseProductName().toLowerCase().contains("h2")) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("CREATE TABLE IF NOT EXISTS audit_streams (" +
                            "tenant_id UUID NOT NULL, " +
                            "stream_id VARCHAR(64) NOT NULL, " +
                            "last_sequence_number BIGINT NOT NULL DEFAULT 0, " +
                            "last_event_hash VARCHAR(64) NOT NULL, " +
                            "last_event_id UUID, " +
                            "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                            "CONSTRAINT pk_audit_streams PRIMARY KEY (tenant_id, stream_id))");

                    stmt.execute("CREATE TABLE IF NOT EXISTS audit_outbox_events (" +
                            "id UUID PRIMARY KEY, " +
                            "tenant_id UUID, " +
                            "audit_event_id UUID NOT NULL, " +
                            "stream_id VARCHAR(64) NOT NULL, " +
                            "sequence_number BIGINT NOT NULL, " +
                            "event_hash VARCHAR(64) NOT NULL, " +
                            "canonical_payload CLOB NOT NULL, " +
                            "status VARCHAR(32) NOT NULL DEFAULT 'PENDING', " +
                            "attempt_count INTEGER NOT NULL DEFAULT 0, " +
                            "max_attempts INTEGER NOT NULL DEFAULT 5, " +
                            "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                            "next_attempt_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                            "published_at TIMESTAMP, " +
                            "last_error VARCHAR(1024))");

                    stmt.execute("CREATE TABLE IF NOT EXISTS audit_checkpoints (" +
                            "checkpoint_id UUID PRIMARY KEY, " +
                            "tenant_id UUID, " +
                            "stream_id VARCHAR(64) NOT NULL, " +
                            "first_event_sequence BIGINT NOT NULL, " +
                            "last_event_sequence BIGINT NOT NULL, " +
                            "event_count BIGINT NOT NULL, " +
                            "first_event_hash VARCHAR(64) NOT NULL, " +
                            "last_event_hash VARCHAR(64) NOT NULL, " +
                            "chain_state_hash VARCHAR(64) NOT NULL, " +
                            "previous_checkpoint_hash VARCHAR(64) NOT NULL, " +
                            "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                            "schema_version INTEGER NOT NULL DEFAULT 1)");

                    stmt.execute("CREATE TABLE IF NOT EXISTS evidence_manifests (" +
                            "artifact_id UUID PRIMARY KEY, " +
                            "tenant_id UUID, " +
                            "artifact_type VARCHAR(64) NOT NULL, " +
                            "content_hash VARCHAR(64) NOT NULL, " +
                            "previous_artifact_hash VARCHAR(64) NOT NULL, " +
                            "producer_version VARCHAR(32) NOT NULL DEFAULT '1.0.0-RELEASE', " +
                            "schema_version INTEGER NOT NULL DEFAULT 1, " +
                            "signature_metadata VARCHAR(512), " +
                            "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");

                    stmt.execute("CREATE TRIGGER IF NOT EXISTS trg_h2_audit_no_update " +
                            "BEFORE UPDATE ON audit_events FOR EACH ROW " +
                            "CALL \"et.ut.einvoice.audit.h2.H2AuditImmutabilityTrigger\"");

                    stmt.execute("CREATE TRIGGER IF NOT EXISTS trg_h2_audit_no_delete " +
                            "BEFORE DELETE ON audit_events FOR EACH ROW " +
                            "CALL \"et.ut.einvoice.audit.h2.H2AuditImmutabilityTrigger\"");

                    log.info("Successfully registered H2 database-level immutability triggers on audit_events");
                }
            }
        } catch (Exception ex) {
            log.warn("Could not register H2 audit immutability triggers: {}", ex.getMessage());
        }
    }
}
