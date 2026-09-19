# Audit Trust Boundary & Threat Model

**Directive Reference:** Ethiopian Ministry of Revenues Directive No. 1142/2018 EC / Directive No. 1142/2026 Art. 4(2)(b), Art. 4(2)(c), Art. 4(2)(d), Art. 27(2)  
**Tax Proclamation Reference:** Federal Tax Administration Proclamation No. 983/2016 Art. 17  
**Scope:** Trust Assumptions, Adversary Profiles, Threat Matrix, and Defense Layer Boundaries  
**Status:** DEVELOPMENT-STAGE IMMUTABLE AUDIT INTEGRITY VERIFIED — BACKEND AUDIT ARCHITECTURE FROZEN — INDEPENDENT IMMUTABLE STORAGE, EXTERNAL TRUST ANCHORING, PRODUCTION KEY CUSTODY, WORM/ARCHIVE, AND HOST HARDENING DEFERRED TO DEPLOYMENT

---

## 1. Seven-Layer Defense-in-Depth Model

The platform architecture defines a seven-layer security model for immutable audit evidence. Only **Layers 1 through 4** are implemented in the current development phase; **Layers 5 through 7** are deferred to infrastructure deployment:

| Layer | Security Layer Description | Implementation Phase | Implementation Mechanism |
| :---: | :--- | :--- | :--- |
| **Layer 1** | **Application Controls** | **Current Development Phase** | Per-stream monotonic sequencing, canonical serialization (`AuditCanonicalizer`), payload secret sanitization via server-held HMAC-SHA-256 (`AuditPayloadSanitizer`), JPA `@PreUpdate`/`@PreRemove` lifecycle guards, transactional outbox. |
| **Layer 2** | **PostgreSQL Integrity Controls** | **Current Development Phase** | Append-only database triggers (`trg_audit_events_immutability`), pessimistic row locking (`findWithLock`), Row Level Security (RLS) tenant boundaries, restricted application role (`ut_app_user`). |
| **Layer 3** | **Cryptographic Audit Chain** | **Current Development Phase** | Deterministic SHA-256 backward hash chaining (`previousEventHash` $\to$ `eventHash`), fail-closed verification (`AuditChainVerifier`). |
| **Layer 4** | **Development Checkpoint Signing** | **Current Development Phase** | Periodic cryptographic chain-state digests (`AuditCheckpoint`), ephemeral RSA-2048 signing (`DevKeyCheckpointSigner`), offline export packages (`AuditExportPackage`). |
| **Layer 5** | **Independent Immutable Ledger** | **Deferred to Deployment Phase** | Production external zero-trust tamper-evident database (clustered immudb or another evaluated immutable ledger) with separate network perimeter and independent credentials. |
| **Layer 6** | **Independent WORM / Archive Storage** | **Deferred to Deployment Phase** | Independent WORM/archive storage enforcing the platform's configured legal-retention policy and any applicable preservation/legal-hold requirements (AWS S3 Object Lock, GCP Bucket Lock, Azure Immutable Blob). |
| **Layer 7** | **Host & Filesystem Hardening** | **Deferred to Deployment Phase** | Linux `chattr +i` filesystem attributes (retained exclusively for finalized evidence artifacts or static configurations, never applied to live PostgreSQL PGDATA), SELinux/AppArmor mandatory access controls, kernel integrity lockdown, UEFI Secure Boot. |

---

## 2. Database Role Assumptions & Privilege Baseline

The immutable database controls operate under tested, restricted application role privileges (`ut_app_user`):

* **Permitted Operations:**
  - `SELECT` on `audit_events`, `audit_streams`, `audit_checkpoints`, `audit_outbox`
  - `INSERT` on `audit_events`, `audit_streams`, `audit_checkpoints`, `audit_outbox`
  - `UPDATE` strictly on `audit_streams` (monotonic sequence and latest event hash advancement under row lock) and `audit_outbox` (state transition from PENDING $\to$ IN_FLIGHT $\to$ PUBLISHED)
* **Blocked Operations:**
  - `UPDATE` on `audit_events`: **BLOCKED** by database trigger `trg_audit_events_immutability` and JPA lifecycle callback
  - `DELETE` on `audit_events`: **BLOCKED** by database trigger `trg_audit_events_immutability` and JPA lifecycle callback
  - `ALTER TABLE`: **BLOCKED** (application role lacks DDL privileges)
  - `DROP TABLE`: **BLOCKED** (application role lacks DDL privileges)
  - `DISABLE TRIGGER`: **BLOCKED** (requires superuser or table owner privileges)
  - `CREATE SUPERUSER`: **BLOCKED** (application role lacks privilege)
  - `GRANT BYPASSRLS`: **BLOCKED** (application role lacks privilege)

---

## 3. Threat Model & Adversarial Compromise Matrix

PostgreSQL triggers, RLS, constraints, and application controls **do not** physically prevent an unrestricted PostgreSQL database superuser (`postgres`) or host administrator (`root`) from modifying or destroying database files.

The cryptographic SHA-256 chain makes unauthorized historical modification **DETECTABLE** provided an intact trusted checkpoint or offline evidence export remains available.

The threat matrix strictly uses standard classifications: **`BLOCKED`**, **`DETECTABLE`**, **`NOT PREVENTED`**, and **`FUTURE CONTROL`**:

| Threat Scenario | Threat Status | Defense Mechanism & Architectural Reality |
| :--- | :--- | :--- |
| **Application-role UPDATE** | **BLOCKED** | Rejected by database trigger `trg_audit_events_immutability` and ORM `@PreUpdate` lifecycle callback. |
| **Application-role DELETE** | **BLOCKED** | Rejected by database trigger `trg_audit_events_immutability` and ORM `@PreRemove` lifecycle callback. |
| **Application omission / forgery** | **NOT FULLY PREVENTED** | Application compromise may omit legitimate events or generate forged events through the legitimate audit path. Cryptographic verification can detect broken historical continuity, while expected-event reconciliation, business invariants, and independent authority records provide additional detection. Sequence gaps are an anomaly signal, not by themselves proof of tampering. |
| **Database account compromise (UPDATE/DELETE)** | **BLOCKED** | Restricted application role cannot update/delete historical audit rows; triggers enforce immutability even under direct SQL connection using app credentials. |
| **Restricted application DB-role event injection** | **NOT FULLY PREVENTED** | The restricted role cannot update/delete historical audit rows, but authorized INSERT capability means a compromised application context may potentially insert syntactically valid forged events. Detection relies on application/business reconciliation, actor/context validation, anomaly detection, and independently preserved evidence. |
| **PostgreSQL superuser modification** | **NOT PREVENTED** | A compromised superuser account (`postgres`) has absolute database authority and can disable triggers or directly rewrite database pages. |
| **PostgreSQL superuser modification + independently preserved checkpoint/export** | **DETECTABLE** | Comparison against an independently preserved trusted checkpoint, export, immutable ledger, or archive can reveal historical divergence. Development-local checkpoints alone do not provide independent trust against compromise of the same trust boundary. |
| **Database destruction (DROP TABLE / DROP DB)** | **NOT PREVENTED** | A superuser or disk wipe cannot be prevented at the database software layer. |
| **Database destruction + independent archive** | **RECOVERABLE / CONDITIONALLY DETECTABLE** | An independently preserved archive or immutable ledger can recover audit evidence to its last preserved checkpoint/state. Verification of continuity depends on the completeness and trustworthiness of the independently preserved evidence. |
| **Linux root host modification** | **NOT PREVENTED** | Root can modify block devices, unmount filesystems, or edit binary tables directly on disk. |
| **Linux root modification + independent archive** | **DETECTABLE** | Discrepancies are detectable only when compared against an intact trusted external artifact (WORM archive or external ledger). |

---

## 4. Development Checkpoint Trust Model

* **Current Development Implementation:**
  - Component: `DevKeyCheckpointSigner`
  - Key Material: Ephemeral in-memory RSA-2048 key pair
  - Verification: Local checkpoint verification via `AuditCheckpointVerifier` and `CheckpointSignatureVerifier`
  - **Classification:** **INTERNAL CRYPTOGRAPHIC INTEGRITY EVIDENCE**
  - **Explicit Limitations:**
    - It is **NOT** an external trust anchor.
    - It is **NOT** an independent witness.
    - It is **NOT** a legal non-repudiation authority.
    - It is **NOT** a government or regulatory signature.
    - Development-local checkpoints alone do not provide independent trust against compromise of the same trust boundary. Never state that the current development checkpoint is an independent external witness.

* **Deferred Production Implementation:**
  - Independent signing key managed under strict separation of duties.
  - Production key custody must satisfy the applicable authority and security specification. Where FIPS validation is required, prefer a currently valid FIPS 140-3 validated cryptographic module where applicable.
  - Independent immutable ledger (clustered immudb or another evaluated tamper-evident ledger).
  - Independent WORM/archive storage enforcing the platform's configured legal-retention policy and applicable legal holds.

---

## 5. Fail-Closed Verifier Policy

> [!IMPORTANT]
> The verification subsystem (`AuditChainVerifier`, `AuditCheckpointVerifier`, `AuditExportVerifier`) operates under an absolute **FAIL-CLOSED** security policy:
> 1. **Zero Automated Repair:** The platform never recalculates hashes or rewrites damaged history to "heal" a broken chain.
> 2. **Immediate Telemetry:** Detected discrepancies trigger high-priority alerts via `AuditSecurityTelemetry`.
> 3. **Preservation of Evidence:** Tampered or corrupted database records are preserved verbatim for forensic examination and regulatory notification.

---

## 6. Official Subsystem Status

> **DEVELOPMENT-STAGE IMMUTABLE AUDIT INTEGRITY VERIFIED — BACKEND AUDIT ARCHITECTURE FROZEN — INDEPENDENT IMMUTABLE STORAGE, EXTERNAL TRUST ANCHORING, PRODUCTION KEY CUSTODY, WORM/ARCHIVE, AND HOST HARDENING DEFERRED TO DEPLOYMENT**
