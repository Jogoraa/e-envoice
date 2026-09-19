# Immutable Audit Architecture: Development Stage Integrity Controls

**Directive Reference:** Ethiopian Ministry of Revenues Directive No. 1142/2018 EC (2026 GC) Art. 4(2)(b), Art. 4(2)(c), Art. 4(2)(d), Art. 27(2)  
**Tax Proclamation Reference:** Federal Tax Administration Proclamation No. 983/2016 Art. 17  
**Architecture Scope:** CODEBASE ONLY (Development Stage)  
**Status:** Development-stage cryptographic, tamper-evident, append-only audit integrity controls are implemented and adversarially tested. Independent immutable ledger storage, production key custody, WORM/archive controls, and host/filesystem hardening remain deployment-stage controls.

---

## 1. Architectural Blueprint & Data Flow

The audit subsystem in the UT Electronic Invoicing SaaS Platform enforces forensic integrity through an end-to-end decoupled pipeline:

```
[ Application / Business Transaction ]
                 │
                 ▼
     [ PostgreSQL Fiscal DB ]
                 │
                 ▼
    [ PostgreSQL Audit Ledger ] ──── (Append-only triggers & row locks)
                 │
                 ▼
    [ Transactional Audit Outbox ] ── (Atomic commit with fiscal transaction)
                 │
                 ▼
  [ Immutable Ledger Abstraction ] ── (ImmutableAuditLedger Port SPI)
                 │
                 ▼
   [ Cryptographic Checkpoints ] ─── (Chain-state digests & dev signatures)
                 │
                 ▼
[ Future Independent Immutable Storage ] (immudb / evaluated ledger in deployment phase)
```

---

## 2. Distinction of Security Protections

The platform establishes a strict boundary between controls implemented within the codebase for the current development phase and controls deferred to operational infrastructure deployment:

### CURRENT DEVELOPMENT PROTECTION:
* **Application-Level Append-Only Enforcement:** JPA entity lifecycle callbacks (`@PreUpdate`, `@PreRemove`) reject any ORM-level modification or deletion with explicit runtime exceptions.
* **PostgreSQL Database-Level Immutability Controls:** Native database trigger `trg_audit_events_immutability` blocks SQL `UPDATE` and `DELETE` statements under the application database role.
* **Deterministic SHA-256 Hash Chain:** Every audit event is canonically serialized and cryptographically chained to its ancestor via SHA-256 (`previousEventHash` $\to$ `eventHash`). UT implements SHA-256 hash chaining as an engineering control supporting the directive's audit-integrity and anti-tampering objectives.
* **Fail-Closed Identity Validation:** `tenantId`, `actorId`, `actorType`, `action`, `resourceType`, `resourceId`, and timestamp are mandatory; missing identities fail closed without silent substitution.
* **Payload Secret Sanitization:** `AuditPayloadSanitizer` scrubs secrets (passwords, JWTs, API keys, client secrets, private keys, database passwords, session credentials, authorization headers) and uses server-held HMAC-SHA-256 for correlation without exposing raw hashes of low-entropy secrets.
* **Tamper Detection:** `AuditChainVerifier` validates full backward continuity, payload hashes, and monotonic sequence ordering.
* **Fail-Closed Verification:** Corrupted, reordered, skipped, or modified records fail closed immediately without automated history rewriting or silent "repair."
* **Transactional Audit Outbox:** Guarantees atomic commit of business state changes, audit events, and outbox publication records in a single database transaction. Outbox provides at-least-once publication with duplicate-safe downstream processing.
* **Signed Development Checkpoints:** Periodic cryptographic chain-state digests (`AuditCheckpoint`) signed with an ephemeral local development key pair (`DevKeyCheckpointSigner`).
* **Deterministic Audit Export:** `AuditExportService` and `AuditExportVerifier` package and verify forensic audit bundles independently of the live database.

### DEFERRED DEPLOYMENT PROTECTION:
* **Independent Immutable Ledger Deployment:** Managed external zero-trust ledger service (immudb or another evaluated immutable ledger) with separate network perimeter and credentials.
* **Independent Credentials:** Cryptographically separated credentials isolating audit infrastructure from primary application database administrators.
* **Production Key Custody:** Production key custody must satisfy the applicable authority and security specification. Where FIPS validation is required, prefer a currently valid FIPS 140-3 validated cryptographic module.
* **Independent WORM / Archive Storage:** Independent WORM/archive storage enforcing the platform's configured legal-retention policy and any applicable preservation/legal-hold requirements (AWS S3 Object Lock, GCP Bucket Lock, Azure Immutable Blob).
* **Linux Host Hardening:** Host-level immutable attributes (`chattr +i`, retained exclusively for finalized evidence artifacts or static configurations — never applied to live PostgreSQL PGDATA), restricted mounts, and bastion access architecture.
* **Filesystem Integrity Controls:** Host-based file integrity monitoring (OSSEC / Wazuh / Tripwire).
* **Secure Boot & Kernel Protections:** Measured boot, UEFI Secure Boot, and kernel module lockdown.

---

## 3. Threat Model & Privilege Baseline

PostgreSQL triggers, Row Level Security (RLS) policies, and application constraints do not constitute protection against an unrestricted PostgreSQL database superuser (`postgres`) or host administrator (`root`). An unrestricted superuser can execute `ALTER TABLE audit_events DISABLE TRIGGER ALL`, drop tables, or directly rewrite disk blocks.

The SHA-256 cryptographic chain does not physically prevent a superuser from altering storage; rather, it makes unauthorized historical modification **DETECTABLE** as long as an intact trusted copy, checkpoint, or external export remains available.

| Threat Profile | Status | Defense Mechanism |
| :--- | :--- | :--- |
| **Application-role UPDATE** | **BLOCKED** | Rejected by database trigger `trg_audit_events_immutability` and ORM `@PreUpdate`. |
| **Application-role DELETE** | **BLOCKED** | Rejected by database trigger `trg_audit_events_immutability` and ORM `@PreRemove`. |
| **Application omission / forgery** | **NOT FULLY PREVENTED** | Application compromise may omit legitimate events or generate forged events through the legitimate audit path. Cryptographic verification can detect broken historical continuity, while expected-event reconciliation, business invariants, and independent authority records provide additional detection. Sequence gaps are an anomaly signal, not by themselves proof of tampering. |
| **Database account compromise (UPDATE/DELETE)** | **BLOCKED** | Restricted application role cannot update/delete historical audit rows; triggers enforce immutability. |
| **Restricted application DB-role event injection** | **NOT FULLY PREVENTED** | The restricted role cannot update/delete historical audit rows, but authorized INSERT capability means a compromised application context may potentially insert syntactically valid forged events. Detection relies on application/business reconciliation, actor/context validation, anomaly detection, and independently preserved evidence. |
| **PostgreSQL superuser modification** | **NOT PREVENTED** | A superuser (`postgres`) can bypass triggers or edit database blocks directly. |
| **PostgreSQL superuser modification + independently preserved checkpoint/export** | **DETECTABLE** | Comparison against an independently preserved trusted checkpoint, export, immutable ledger, or archive can reveal historical divergence. Development-local checkpoints alone do not provide independent trust against compromise of the same trust boundary. |
| **Database destruction (DROP TABLE / DROP DB)** | **NOT PREVENTED** | Destructive drop cannot be blocked at software layer once superuser is compromised. |
| **Database destruction + independent archive** | **RECOVERABLE / CONDITIONALLY DETECTABLE** | An independently preserved archive or immutable ledger can recover audit evidence to its last preserved checkpoint/state. Verification of continuity depends on the completeness and trustworthiness of the independently preserved evidence. |
| **Linux root host modification** | **NOT PREVENTED** | Root can alter disk files directly. |
| **Linux root modification + independent archive** | **DETECTABLE** | Discrepancies are detectable only when compared against an intact trusted external artifact. |

---

## 4. Development Checkpoint Trust Model

* **Current Development Implementation:**
  - Component: `DevKeyCheckpointSigner`
  - Mechanism: Ephemeral in-memory RSA-2048 key pair
  - Verification: Local checkpoint verification
  - **Classification:** **INTERNAL CRYPTOGRAPHIC INTEGRITY EVIDENCE**
  - **Explicit Clarification:**
    - NOT an external trust anchor.
    - NOT an independent witness.
    - NOT a legal non-repudiation authority.
    - NOT a regulatory signature.
    - Development-local checkpoints alone do not provide independent trust against compromise of the same trust boundary. Never state that the current development checkpoint is an independent external witness.

* **Production Deployment Stage (Deferred):**
  - Production key custody must satisfy the applicable authority and security specification. Where FIPS validation is required, prefer a currently valid FIPS 140-3 validated cryptographic module.
  - Independent immutable ledger (immudb or another evaluated immutable ledger).
  - Independent WORM / object lock storage.

---

## 5. Audit Outbox Delivery Guarantees

The audit outbox enforces:
> **"At-least-once audit publication with duplicate-safe downstream processing."**
*(Do NOT claim exactly-once external delivery).*

* Transactional atomicity: business state, audit event, and outbox record commit in the same local database transaction.
* Outbox status transitions: `PENDING` $\to$ `IN_FLIGHT` $\to$ `PUBLISHED` (with `FAILED` retry and `DEAD_LETTER` escalation).
* Downstream processing: duplicate-safe via unique `auditEventId` deduplication.

---

## 6. Official Architecture Status

> **DEVELOPMENT-STAGE IMMUTABLE AUDIT INTEGRITY VERIFIED — BACKEND AUDIT ARCHITECTURE FROZEN — INDEPENDENT IMMUTABLE STORAGE, EXTERNAL TRUST ANCHORING, PRODUCTION KEY CUSTODY, WORM/ARCHIVE, AND HOST HARDENING DEFERRED TO DEPLOYMENT**”
