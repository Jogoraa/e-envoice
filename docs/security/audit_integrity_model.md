# Audit Integrity Model: Cryptographic Specifications & Invariants

**Directive Reference:** Ethiopian Ministry of Revenues Directive No. 1142/2018 EC (2026 GC) Art. 4(2)(b), Art. 4(2)(c), Art. 4(2)(d), Art. 27(2)  
**Tax Proclamation Reference:** Federal Tax Administration Proclamation No. 983/2016 Art. 17  
**Scope:** Forensic Audit Trail Mathematical & Cryptographic Specifications  
**Status:** Development-stage cryptographic, tamper-evident, append-only audit integrity controls are implemented and adversarially tested. Independent immutable ledger storage, production key custody, WORM/archive controls, and host/filesystem hardening remain deployment-stage controls.

---

## 1. Statutory Objectives vs. Engineering Controls

Directive No. 1142/2018 EC Art. 4(2)(b) requires an operation audit log tracking all EIRS exchanges, user activities, timestamps, and actor identities. Article 27(2) strictly prohibits the alteration of invoice information or deletion of sales register data.

UT implements SHA-256 hash chaining as an engineering control supporting the directive's audit-integrity and anti-tampering objectives:
1. Every fiscal and operational event generates an immutable audit record.
2. The sequence and parent-child relationship of audit events form a continuous, unidirectional SHA-256 backward hash chain.
3. Any alteration, insertion, omission, or reordering of audit events renders the hash chain cryptographically invalid.
4. Historical records cannot be rewritten or modified under application roles.
5. All verification algorithms fail closed, preserving corrupt evidence for regulatory review without automated "repair."

*(Note: While Directive No. 1142/2018 mandates the audit log and anti-tampering objectives, the specific use of SHA-256 backward hash chaining and canonical serialization is an engineering control designed by UT to satisfy the regulatory objectives).*

---

## 2. Hardened Canonical Serialization Algorithm

Arbitrary JSON serialization is inherently non-deterministic due to variable key ordering, whitespace differences, and character escaping across serializers.

To guarantee deterministic hash calculation across runtimes and platforms, `AuditCanonicalizer` defines an unambiguous canonical format:

### Serialization Policies:
1. **UTF-8 Encoding:** Bytes are computed exclusively from UTF-8 strings.
2. **Unicode Normalization:** Unicode Standard Normalization Form C (NFC) is applied to all string values prior to escaping, guaranteeing identical byte sequences across different OS/runtime text representations.
3. **Delimiter Escaping:** Safe round-trip escaping applies to all values:
   - `\` $\to$ `\\`
   - `|` $\to$ `\|`
   - `=` $\to$ `\=`
4. **Fail-Closed Required Identity:**
   - `tenantId` is **REQUIRED** (zero UUID `00000000-0000-0000-0000-000000000000` substitution is prohibited).
   - `actorId` is **REQUIRED** (non-blank string).
   - `actorType` is **REQUIRED** (non-blank string).
   - `action` is **REQUIRED** (non-blank string).
   - `resourceType` is **REQUIRED** (non-blank string).
   - `resourceId` is **REQUIRED** (non-blank string).
   - `occurredAt` is **REQUIRED** (non-null timestamp).
   - Missing or malformed identities reject audit event creation and canonicalization immediately (`IllegalArgumentException`).
5. **Controlled Stream Default:** `streamId` defaults to `"MAIN"` if unspecified.
6. **Null Policy (Optional Fields):** Null optional fields (`correlationId`, `traceId`) serialize to empty string (`key=`).
7. **Empty String Policy:** Empty strings serialize as `key=` (unambiguous because key positions and names are fixed).
8. **Numeric Representation:** Base-10 canonical integer without leading zeros (`String.valueOf(long)`).
9. **Timestamp Representation:** ISO-8601 UTC Instant truncated to milliseconds (`YYYY-MM-DDTHH:mm:ss.sssZ`).

### Canonical Field Order:
$$\text{Canonical String} = \text{schema\_version}=1 \mid \text{event\_id}=E \mid \text{tenant\_id}=T \mid \text{stream\_id}=S \mid \text{sequence\_number}=N \mid \text{occurred\_at}=TS \mid \text{actor\_id}=A \mid \text{actor\_type}=AT \mid \text{action}=ACT \mid \text{resource\_type}=RT \mid \text{resource\_id}=RI \mid \text{payload\_hash}=PH \mid \text{previous\_event\_hash}=PEH \mid \text{correlation\_id}=C \mid \text{trace\_id}=TR \mid \text{application\_version}=V$$

Two distinct logical events can never produce the same canonical byte sequence.

---

## 3. Cryptographic Hash Specifications

The platform uses standard SHA-256 (FIPS 180-4) with lowercase hex encoding.

### 3.1 Genesis Stream Hash
For sequence $S = 1$, the `previousEventHash` is deterministically bound to the tenant and stream identity to prevent stream substitution or cross-stream splicing:
$$\text{GenesisHash} = \text{SHA-256}(\text{"GENESIS\|"} \parallel \text{tenantId} \parallel \text{"\|"} \parallel \text{streamId})$$

### 3.2 Payload Sanitization & Secret Fingerprinting
Audit payloads are automatically sanitized by `AuditPayloadSanitizer` before hash calculation. Secrets (passwords, private keys, JWTs, API keys, client secrets, database passwords, government credentials, session tokens, authorization headers) are scrubbed.
For secret correlation, `AuditPayloadSanitizer` uses **HMAC-SHA-256 with a dedicated server-held fingerprint key** (not raw SHA-256) to prevent offline dictionary guessing of low-entropy secrets. High-risk keys (PEM private keys, bearer JWTs) are replaced with constant redaction tokens (`[REDACTED_PRIVATE_KEY]`, `[REDACTED_JWT]`).

$$\text{PayloadHash} = \text{SHA-256}(\text{UTF-8}(\text{SanitizedPayloadJson}))$$

### 3.3 Event Hash
$$\text{EventHash} = \text{SHA-256}(\text{UTF-8}(\text{CanonicalEventString}))$$

---

## 4. Hash Chain Invariants & Fail-Closed Enforcement

For any valid audit stream $E = [e_1, e_2, \dots, e_n]$, `AuditChainVerifier` enforces the following mathematical invariants:
1. **Genesis Invariant:** $e_1.\text{previousEventHash} = \text{GenesisHash}(\text{tenantId}, \text{streamId})$
2. **Chain Continuity Invariant:** $\forall i \in [2, n], \quad e_i.\text{previousEventHash} = e_{i-1}.\text{eventHash}$
3. **Monotonic Sequence Invariant:** $\forall i \in [2, n], \quad e_i.\text{sequenceNumber} = e_{i-1}.\text{sequenceNumber} + 1$
4. **Internal Event Integrity:** $\forall i \in [1, n], \quad e_i.\text{eventHash} = \text{SHA-256}(\text{CanonicalString}(e_i))$
5. **Tenant & Stream Isolation:** $\forall i \in [1, n], \quad e_i.\text{tenantId} = T \land e_i.\text{streamId} = S$

When any invariant is violated, verification fails closed immediately. History is never repaired.

---

## 5. Cryptographic Checkpoint & Chain-State Digest Specifications

To anchor segments of the audit chain, `AuditCheckpointService` generates deterministic checkpoint records. The checkpoint state digest binds the entire segment:

$$\text{ChainStateHash} = \text{SHA-256}(\text{checkpointId} \mid \text{tenantId} \mid \text{streamId} \mid \text{firstSeq} \mid \text{lastSeq} \mid \text{count} \mid \text{firstHash} \mid \text{lastHash} \mid \text{prevCheckpointHash} \mid \text{schemaVersion})$$

### Checkpoint Fields:
* `checkpointId` (UUID)
* `tenantId` (UUID)
* `streamId` (String)
* `firstSequence` (Long)
* `lastSequence` (Long)
* `eventCount` (Long)
* `firstEventHash` (String)
* `lastEventHash` (String)
* `previousCheckpointHash` (String)
* `chainStateHash` (String - Cryptographic Chain-State Digest)
* `schemaVersion` (int)
* `createdAt` (Instant)

For the initial checkpoint: $\text{prevCheckpointHash} = \text{"0".repeat(64)}$. Subsequent checkpoints link sequentially to previous checkpoints.

---

## 6. Official Subsystem Status

> **DEVELOPMENT-STAGE IMMUTABLE AUDIT INTEGRITY VERIFIED — BACKEND AUDIT ARCHITECTURE FROZEN — INDEPENDENT IMMUTABLE STORAGE, EXTERNAL TRUST ANCHORING, PRODUCTION KEY CUSTODY, WORM/ARCHIVE, AND HOST HARDENING DEFERRED TO DEPLOYMENT**
