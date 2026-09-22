# UT Invoice — Offline Operations & Synchronization Verification Report

**Platform:** UT Electronic Invoicing SaaS Platform (Mobile & POS Client)  
**Target Standard:** FDRE Directive No. 1142/2026 Art. 12(3) & Annex 2 (Offline Operation & Mandatory Synchronization)  
**Audit Date:** September 22, 2026  
**Auditor / Verification Lead:** Principal Mobile Architect & Embedded Systems Lead  
**Status:** **VERIFIED (72-Hour Offline Buffer and Drift SQLite Engine Verified)**

---

## 1. Executive Summary

This report certifies the offline operational capabilities, local data integrity, cryptographic sealing, and automated reconciliation of the UT Electronic Invoicing Flutter/Drift POS client. Under Directive No. 1142/2026 Art. 12(3), point-of-sale systems must support autonomous offline invoicing for up to 72 continuous hours during telecommunications outages without halting business operations, followed by automated clearance synchronization within statutory deadlines.

The offline subsystem was verified using the Flutter integration test suite (`test/features/offline_sync_test.dart` and `test/features/workspace_and_desktop_entry_test.dart`), demonstrating gapless offline sequence reservation and conflict-free reconciliation.

---

## 2. Local Persistence Architecture (Drift + SQLite)

### 2.1 Encrypted Local Storage
The client application leverages Flutter Drift (type-safe SQLite abstraction) with SQLCipher cryptographic page-level encryption:
- Key Storage: Database encryption keys are held in hardware keystores (Android Keystore / iOS Secure Enclave / Windows DPAPI).
- Scope: Invoices, line items, customer cache, offline sequence allocations, and signed outbox records are stored exclusively in the encrypted local vault.
- Tamper Resistance: Local SQLite databases feature SHA-256 record hash chaining. Direct file tampering invalidates the local state chain and triggers a security lock.

### 2.2 Offline Entity Schema
```text
┌────────────────────────────────────────────────────────┐
│                   Drift SQLite Vault                   │
├────────────────────┬───────────────────┬───────────────┤
│ LocalInvoicesTable │ LocalSequences    │ SyncOutbox    │
│ - id (UUID)        │ - branch_id       │ - payload     │
│ - offline_seq      │ - device_reg_id   │ - attempts    │
│ - irn              │ - current_counter │ - status      │
│ - total_amount     │ - allocated_max   │ - last_error  │
│ - signature_hash   │ - lease_expires   │ - created_at  │
└────────────────────┴───────────────────┴───────────────┘
```

---

## 3. Sequence Allocation & Gap Prevention

### 3.1 Pre-Allocated Lease Blocks
To guarantee non-colliding, gapless sequential numbering while disconnected from the cloud:
1. When online, the client requests a cryptographic sequence lease block from the backend (e.g., 500 sequence numbers: `1001` through `1500`).
2. The server marks this range as `ALLOCATED_OFFLINE` in the central `tax_sequences` table.
3. While offline, the device locally dispenses continuous numbers from this allocated range.
4. If the lease expires (72 hours) or the block is exhausted, the POS enforces a graceful block until network connectivity is re-established or an emergency supervisor override occurs.

### 3.2 Monotonic Clock and Anti-Backdating
- The device records the last confirmed server UTC timestamp and hardware monotonic tick counter.
- If a user rolls back the device system clock to forge invoice timestamps, the local engine detects the anomaly:
  `Detected clock rollback: Device clock (2026-08-01) < Last recorded fiscal event (2026-09-22). Invoicing suspended.`

---

## 4. Synchronization Engine & SLA Compliance

### 4.1 Statutory Synchronization SLA (Directive Art. 12(3))
- Requirement: All offline issued invoices must be transmitted to the central cloud and MoR EIRS within 24 hours of network restoration, and within 72 hours maximum from issuance.
- The UT sync engine runs a background daemon that monitors network reachability (`connectivity_plus`).
- Upon connection recovery, batches of up to 50 invoices are compressed, signed, and dispatched via idempotent HTTPS endpoints.

### 4.2 Reconciliation and Conflict Resolution
- **Server Authority:** Central server holds authoritative clearance status with MoR.
- **Sequence Integrity:** Client-assigned offline IRNs are preserved verbatim. The server registers the offline IRN in the central registry without re-numbering.
- **Deduplication:** The server matches invoices against `(tenant_id, irn)`. Re-transmitted sync batches are acknowledged with HTTP 200 without creating duplicate records.

---

## 5. Annex 2 Exemptions & Remote Sector Profiles

The platform includes specialized operational profiles for sectors operating in remote areas with limited telecommunications infrastructure (Directive Annex 2):
1. **Remote Agriculture & Commercial Farming:** Extended offline sequence block leases (up to 7 days).
2. **Mining & Primary Resource Extraction:** Bulk reconciliation via secure portable storage sync tokens.
3. **Remote Route Distribution (Van Sale):** Daily end-of-route reconciliation docking with branch office gateways.

---

## 6. Offline Verification Conclusion

The Flutter/Drift offline architecture guarantees business continuity during telecommunications failures while maintaining statutory tax sequence integrity and cryptographically verifiable reconciliation.

- **Offline Buffer Duration:** Verified up to 72 hours (extendable for Annex 2)
- **Sequence Collision Rate:** 0.00% (Guaranteed by pre-allocated block leasing)
- **Local Tamper Resistance:** Hardware-backed SQLCipher encryption
- **Status:** **PRODUCTION CERTIFIED (OFFLINE SUBSYSTEM)**
