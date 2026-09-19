# Multi-Tenancy Architecture & Scaling Strategy
## High-Density Multi-Tenant Partitioning toward 1 Million Registered Tenants
### UT Electronic Invoicing Platform

---

## 1. Architectural Evolution: Moving Past Database-Per-Tenant

The initial prototype conceptualized a `tenant_<id>_db` physical database and HikariCP connection pool per tenant. While conceptually simple, that model breaks down in production at SaaS scale:
- 1,000 tenants = 1,000 Hikari pools * 10 connections = 10,000 idle database connections (exhausting PostgreSQL limits).
- Schema migrations with Flyway across 50,000 separate databases cause extreme deployment latencies and lock contention.
- High operational overhead for backups, monitoring, and index maintenance.

### The Production Tenancy Strategy:
1. **SME Tenants (Default, 99% of customers)**: Shared transactional database cluster with logical and cryptographic tenant boundaries, reinforced by native PostgreSQL declarative table partitioning.
2. **Enterprise Tenants (High volume)**: Dedicated database cluster or shard, routed transparently via an application-level `TenantDatasourceRouter` without changing business code or API contracts.
3. **Control Plane Isolation**: A distinct `controlplane_db` holds tenant accounts, subscription plans, API client credentials, and shard assignment mappings.

```text
                        REQUEST
                           │
                           ▼
                  [TenantContextFilter]
              (Extracts tenant_id from JWT)
                           │
                           ▼
                 [TenantContextHolder]
                           │
         ┌─────────────────┴──────────────────┐
         │                                    │
    [Standard SME]                      [Enterprise]
         │                                    │
         ▼                                    ▼
   Shared Cluster                     Dedicated Cluster
  (Partitioned Tables)               (Exclusive Hikari Pool)
```

---

## 2. Declarative Table Partitioning

All high-volume transactional tables in the shared cluster include `tenant_id` and are partitioned to ensure optimal query performance, efficient index sizing, and rapid bulk maintenance:

```sql
-- Example: Core Invoices Table Partitioned by Hash of tenant_id
CREATE TABLE invoices (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    document_number VARCHAR(64) NOT NULL,
    invoice_counter BIGINT NOT NULL,
    invoice_date TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL,
    irn VARCHAR(128),
    pre_tax_total NUMERIC(18, 2) NOT NULL,
    tax_total NUMERIC(18, 2) NOT NULL,
    grand_total NUMERIC(18, 2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id, tenant_id)
) PARTITION BY HASH (tenant_id);

-- Create 16 hash partitions for high-throughput distribution
CREATE TABLE invoices_p00 PARTITION OF invoices FOR VALUES WITH (MODULUS 16, REMAINDER 0);
CREATE TABLE invoices_p01 PARTITION OF invoices FOR VALUES WITH (MODULUS 16, REMAINDER 1);
-- ... p02 to p15 ...
```

For audit logs and government submissions, a composite partition strategy `(tenant_id, created_at)` is used for monthly rolling range partitions, allowing older partitions to be detached and moved to cold storage without locking the live database.

---

## 3. Tenant Context Propagation & Enforcement

### 3.1 Establishing Trusted Tenant Context
Tenant identity is **never** derived from untrusted client-supplied headers like `X-Tenant-ID` unless signed by a platform administrator. The `TenantContextFilter`:
1. Authenticates the Bearer JWT or API Key.
2. Resolves `tenantId`, `organizationId`, `branchId`, `roles`, and `scopes`.
3. Populates `TenantContextHolder` (a `ThreadLocal` wrapper).

```java
public class TenantContextHolder {
    private static final ThreadLocal<TenantContext> CONTEXT = new ThreadLocal<>();

    public static void setContext(TenantContext context) {
        CONTEXT.set(context);
    }

    public static TenantContext getRequiredContext() {
        TenantContext ctx = CONTEXT.get();
        if (ctx == null) {
            throw new TenantNotResolvedException("No authenticated tenant context found for the current execution thread.");
        }
        return ctx;
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
```

### 3.2 Repository-Level Tenant Isolation
1. **Entity Base Class**: All tenant-owned domain entities extend `TenantAwareEntity`, containing an immutable `tenantId`.
2. **Spring Data Specifications / Hibernate Filter**: A persistent Hibernate filter `@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")` is activated on all sessions, automatically appending `AND tenant_id = ?` to every generated SQL query.
3. **PostgreSQL Row-Level Security (RLS)**: Activated on the database role as an additional defense-in-depth barrier against accidental cross-tenant queries.
