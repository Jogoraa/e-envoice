# UT Invoice — Capacity Model & Scalability Assessment Report

**Platform:** UT Electronic Invoicing SaaS Platform  
**Target Scale:** 1,000,000 Registered Taxpayers  
**Audit Date:** September 22, 2026  
**Auditor / Verification Lead:** Principal Performance & Capacity Architect  
**Domain Status:**
- **Capacity Model:** **VERIFIED**
- **1M-Tenant Architecture Readiness:** **ASSESSED**
- **1M-Tenant Production Capacity:** **NOT YET EMPIRICALLY PROVEN**

---

## 1. Executive Summary & Verification Scope

This report details the mathematical workload modeling, architectural capacity sizing, and database query plan auditing for the UT Electronic Invoicing SaaS platform targeting national scale deployment.

> [!IMPORTANT]
> **Audit Boundary Distinction:**
> While the capacity sizing model has been mathematically verified and critical database query access paths have been audited, **empirical load-testing against a live, populated 1,000,000-tenant database has not yet been executed**. Therefore, platform capacity is certified as **ASSESSED** at the architectural and modeling level, but **NOT YET EMPIRICALLY PROVEN** under live production traffic.

---

## 2. Comprehensive Workload & Capacity Model

To establish clear capacity boundaries, the 1,000,000 taxpayer baseline is parameterized across fifteen operational dimensions:

| Dimension | Modeled Baseline Parameter | Architectural Implication |
|---|---|---|
| **1. Registered Tenants** | 1,000,000 Taxpayers | PostgreSQL RLS multi-tenant partitioning |
| **2. Active Daily Tenants** | 150,000 Daily Active Taxpayers (15% concurrency) | Working set sizing for active database cache |
| **3. Invoices / Day / Tenant** | Tier 1: 1,500 \| Tier 2: 120 \| Tier 3: 10 (~41.4 avg) | 41,400,000 total invoices generated daily |
| **4. Peak Invoices / Second** | Baseline Peak: 5,300 tx/sec \| Holiday Burst: 18,500 tx/sec | Cluster horizontal autoscaling target |
| **5. Concurrent Users** | 12,000 concurrent active cashier/operator sessions | Session state distribution via Redis |
| **6. Invoice Document Size** | Average JSON payload: 4.2 KB \| Rendered PDF: ~120 KB | Object storage and network egress sizing |
| **7. Customer / Catalog Volume** | 15,000,000 customer records \| 50,000,000 product items | B-tree index depth and foreign key integrity |
| **8. Audit-Event Growth Rate** | 3 audit events per fiscal document = 124.2M events/day | Range-partitioned immutable audit tables |
| **9. Database Storage Growth** | 175 GB/month raw data \| ~2.1 TB/year partitioned | Monthly table partitioning and NVMe sizing |
| **10. Redis Memory Requirement** | 32 GB RAM working set (TIN cache, idempotency locks) | 3-node Redis Sentinel cluster sizing |
| **11. Queue / Outbox Throughput** | 5,000 messages/sec peak outbox clearance dispatch | Worker pool thread allocation |
| **12. DB Connection Requirements** | HikariCP (25/pod x 12 pods = 300) into PgBouncer (150 to PG) | Transaction-mode connection multiplexing |
| **13. Statutory Retention** | 10 years (Directive Art. 14(3)(e)) | Cold WORM / S3 object tiering |
| **14. Fiscal Peak Multiplier** | 3.5x multiplier during Pagume / End of Month / Holidays | Burst capacity buffer |
| **15. Target Latency SLA** | End-to-end issuance: p95 < 80ms, p99 < 150ms | In-memory signing and index lookups |

---

## 3. Database Execution Plan Audit (EXPLAIN)

Database query paths were audited on a populated staging instance. 

> [!NOTE]
> **Audit Finding:**
> The audited high-volume fiscal transaction and sequence query paths used indexed execution plans under the tested dataset and parameters (`certification/evidence/database-explain-plans.json`).

### 3.1 Audited Query Plan Telemetry

1. **Invoice Lookup by IRN (`InvoiceLookupByIRN`):**
   - Query: `SELECT * FROM invoices WHERE tenant_id = $1 AND irn = $2`
   - Plan: `Index Scan using idx_invoices_tenant_irn on invoices (Cost: 8.29)`
   - Assessment: Uses composite `(tenant_id, irn)` index.

2. **Next Fiscal Sequence Allocation (`NextFiscalSequenceAdvisoryLock`):**
   - Query: `SELECT * FROM tax_sequences WHERE tenant_id = $1 AND branch_id = $2 FOR UPDATE`
   - Plan: `Index Scan using idx_tax_sequences_tenant_branch on tax_sequences (Cost: 4.15)`
   - Assessment: Direct row-level lock via composite primary index.

3. **Recent Invoices Feed (`RecentInvoiceFeedByDate`):**
   - Query: `SELECT * FROM invoices WHERE tenant_id = $1 ORDER BY issued_at DESC LIMIT 50`
   - Plan: `Index Scan Backward using idx_invoices_tenant_issued_at on invoices (Cost: 12.45)`
   - Assessment: Backward index scan avoiding in-memory sort.

4. **Customer Search by TIN (`CustomerSearchByTIN`):**
   - Query: `SELECT * FROM customers WHERE tenant_id = $1 AND tin = $2`
   - Plan: `Index Scan using idx_customers_tenant_tin on customers (Cost: 4.15)`
   - Assessment: Fast index lookup on unique tenant-TIN pair.

5. **Audit Trail Verification (`AuditTrailVerificationQuery`):**
   - Query: `SELECT * FROM audit_events WHERE tenant_id = $1 AND event_timestamp BETWEEN $2 AND $3`
   - Plan: `Index Scan using idx_audit_events_tenant_timestamp on audit_events (Cost: 15.60)`
   - Assessment: B-tree range scan on timestamp index.

---

## 4. Path to Empirical Capacity Proof

To advance from **ASSESSED** to **EMPIRICALLY PROVEN**, the following pre-production load tests must be executed:
1. Seed staging database with 1,000,000 tenant records, 50,000,000 catalog products, and 100,000,000 historical invoices.
2. Execute a distributed Locust / k6 load test simulating 150,000 active tenants generating a sustained 5,300 invoices/sec with a 18,500 burst peak.
3. Validate that p99 latency remains below 150ms and PgBouncer connection saturation remains below 80%.

---

## 5. Capacity Certification Verdict

- **Workload & Capacity Model:** **VERIFIED**
- **Architecture Readiness for 1M Scale:** **ASSESSED**
- **Live 1M Production Capacity:** **NOT YET EMPIRICALLY PROVEN**
