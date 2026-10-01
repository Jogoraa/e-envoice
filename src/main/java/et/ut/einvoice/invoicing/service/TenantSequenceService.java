package et.ut.einvoice.invoicing.service;

import et.ut.einvoice.invoicing.repository.InvoiceRepository;
import et.ut.einvoice.invoicing.repository.TenantInvoiceSequenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.UUID;

/**
 * Autonomous tenant sequence allocation service.
 * Enforces atomic database-level sequence allocation using PostgreSQL native INSERT ... ON CONFLICT
 * DO UPDATE ... RETURNING semantics.
 * Guaranteed to be concurrency-safe across horizontal pods without JVM-level locks.
 */
@Service
public class TenantSequenceService {

    private static final Logger log = LoggerFactory.getLogger(TenantSequenceService.class);

    private final TenantInvoiceSequenceRepository sequenceRepository;
    private final InvoiceRepository invoiceRepository;
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private Boolean isPostgres;

    public TenantSequenceService(
            TenantInvoiceSequenceRepository sequenceRepository,
            InvoiceRepository invoiceRepository,
            JdbcTemplate jdbcTemplate,
            DataSource dataSource
    ) {
        this.sequenceRepository = sequenceRepository;
        this.invoiceRepository = invoiceRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    private boolean checkIsPostgres() {
        if (isPostgres == null) {
            try (Connection conn = dataSource.getConnection()) {
                String prod = conn.getMetaData().getDatabaseProductName();
                isPostgres = (prod != null && prod.toLowerCase().contains("postgresql"));
            } catch (Exception e) {
                isPostgres = false;
            }
        }
        return isPostgres;
    }

    /**
     * Allocates the next monotonic invoice counter for the tenant.
     * Committed immediately in an autonomous transaction to prevent uncommitted read anomalies under high concurrency.
     * In PostgreSQL, uses atomic database upsert with ON CONFLICT ... RETURNING to guarantee zero race conditions across pods.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long allocateNextCounter(UUID tenantId) {
        Long maxCounter = invoiceRepository.findMaxInvoiceCounter(tenantId);
        long initial = (maxCounter != null ? maxCounter : 0L) + 1L;

        if (checkIsPostgres()) {
            String sql = """
                INSERT INTO tenant_invoice_sequences (tenant_id, current_counter, updated_at)
                VALUES (?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (tenant_id) DO UPDATE
                SET current_counter = tenant_invoice_sequences.current_counter + 1,
                    updated_at = CURRENT_TIMESTAMP
                RETURNING current_counter
                """;
            Long allocated = jdbcTemplate.queryForObject(sql, Long.class, tenantId, initial);
            if (allocated == null) {
                throw new IllegalStateException("Failed to allocate sequence counter for tenant " + tenantId);
            }
            return allocated;
        }

        // H2 fallback for in-memory unit tests
        int updated = jdbcTemplate.update(
            "UPDATE tenant_invoice_sequences SET current_counter = current_counter + 1, updated_at = CURRENT_TIMESTAMP WHERE tenant_id = ?",
            tenantId
        );
        if (updated > 0) {
            Long val = jdbcTemplate.queryForObject(
                "SELECT current_counter FROM tenant_invoice_sequences WHERE tenant_id = ?",
                Long.class,
                tenantId
            );
            return val != null ? val : initial;
        }

        try {
            jdbcTemplate.update(
                "INSERT INTO tenant_invoice_sequences (tenant_id, current_counter, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP)",
                tenantId, initial
            );
            return initial;
        } catch (Exception ex) {
            jdbcTemplate.update(
                "UPDATE tenant_invoice_sequences SET current_counter = current_counter + 1, updated_at = CURRENT_TIMESTAMP WHERE tenant_id = ?",
                tenantId
            );
            Long val = jdbcTemplate.queryForObject(
                "SELECT current_counter FROM tenant_invoice_sequences WHERE tenant_id = ?",
                Long.class,
                tenantId
            );
            return val != null ? val : initial;
        }
    }

    /**
     * Synchronously adjusts the tenant sequence during government sequence mismatch recovery.
     * Uses database GREATEST() expression to guarantee monotonic progress.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void adjustCounterIfHigher(UUID tenantId, long expectedNextCounter) {
        if (checkIsPostgres()) {
            String sql = """
                INSERT INTO tenant_invoice_sequences (tenant_id, current_counter, updated_at)
                VALUES (?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (tenant_id) DO UPDATE
                SET current_counter = GREATEST(tenant_invoice_sequences.current_counter, EXCLUDED.current_counter),
                    updated_at = CURRENT_TIMESTAMP
                """;
            jdbcTemplate.update(sql, tenantId, expectedNextCounter);
            log.warn("Adjusted sequence counter for tenant {} to at least {}", tenantId, expectedNextCounter);
            return;
        }

        int updated = jdbcTemplate.update(
            "UPDATE tenant_invoice_sequences SET current_counter = CASE WHEN current_counter < ? THEN ? ELSE current_counter END, updated_at = CURRENT_TIMESTAMP WHERE tenant_id = ?",
            expectedNextCounter, expectedNextCounter, tenantId
        );
        if (updated == 0) {
            try {
                jdbcTemplate.update(
                    "INSERT INTO tenant_invoice_sequences (tenant_id, current_counter, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP)",
                    tenantId, expectedNextCounter
                );
            } catch (Exception ex) {
                jdbcTemplate.update(
                    "UPDATE tenant_invoice_sequences SET current_counter = CASE WHEN current_counter < ? THEN ? ELSE current_counter END, updated_at = CURRENT_TIMESTAMP WHERE tenant_id = ?",
                    expectedNextCounter, expectedNextCounter, tenantId
                );
            }
        }
    }
}

