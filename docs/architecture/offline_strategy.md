# Offline Business Continuity & Synchronization Strategy
## 72-Hour Resilient Synchronization Protocol (Directive No. 1142/2026 Art. 4(4) & 23(4))
### Mandatory Resiliency for Annex 2 Business Sectors

---

## 1. Compliance Mandate & Context

Under Article 4(4), Article 22, and Article 23(4) of Directive No. 1142/2026:
- Taxpayers in **26 designated sectors (Annex 2)**—including grocery, retail, restaurants, pharmacies, hotels, passenger transport, and fuel stations—**must mandatorily operate an offline business continuity mechanism**.
- Transactions executed during network outages must be securely recorded and legally delivered to buyers.
- **The 72-Hour Rule**: All offline transactions **must be synchronized and registered with the Ministry of Revenues EIRS within 72 hours** of connectivity restoration.

---

## 2. Distributed Offline Transaction Protocol

```text
  [POS / Mobile Device]                    [UT Cloud Platform]            [Ministry EIRS]
           │                                        │                            │
           │ 1. Request Offline Sequence Range      │                            │
           ├───────────────────────────────────────►│                            │
           │    (Allocates Range: 1000 - 1500)      │                            │
           │◄───────────────────────────────────────┤                            │
           │                                        │                            │
    [NETWORK FAILS]                                 │                            │
           │                                        │                            │
           │ 2. Issues Invoices Locally             │                            │
           │    - Sequence 1001, 1002, 1003         │                            │
           │    - Signs payload with local key      │                            │
           │    - Prints receipt with offline QR    │                            │
           │    - Stores in encrypted SQLite        │                            │
           │                                        │                            │
   [NETWORK RESTORES]                               │                            │
           │                                        │                            │
           │ 3. POST /api/v1/offline/sync           │                            │
           ├───────────────────────────────────────►│                            │
           │    (Batched offline invoices)          │                            │
           │                                        │ 4. Validates Device,       │
           │                                        │    TIN, Sequence & Hash    │
           │                                        │                            │
           │                                        │ 5. Register with MoR       │
           │                                        │    (Reconciliation Job)    │
           │                                        ├───────────────────────────►│
           │                                        │◄───────────────────────────┤
           │                                        │    (Returns IRN & SignedQR)│
           │ 6. Sync Acknowledgment (IRN mapped)    │                            │
           │◄───────────────────────────────────────┤                            │
           │    (Updates local DB status to SYNCED) │                            │
```

---

## 3. Core Technical Protections

### 3.1 Pre-Allocated Sequence Ranges
- Devices cannot invent random invoice numbers while offline.
- When online, the device requests an offline sequence allocation block (e.g., Device POS-01 receives range `1001` through `1500`).
- The server records the allocation in `device_offline_allocations`. If a device submits a transaction outside its authorized sequence block, the sync is rejected.

### 3.2 Device Cryptographic Signing
- While offline, the device generates a local signature using its device-bound private key:
  `DeviceSig = Sign(SHA-256(device_id + document_number + total_amount + timestamp))`.
- When replayed to the server, this signature proves that the transaction was created on the authorized hardware at the recorded time.

### 3.3 The 72-Hour Reconciliation Batch Job
- The `offline` module runs a scheduled Spring Batch reconciliation process:
  - Scans `offline_transaction_buffer` for records where `status = 'QUEUED'`.
  - Replays transactions sequentially against the MoR gateway in batches of 50.
  - Automatically handles MoR counter synchronization if sequence adjustments are demanded by the gateway.
  - Verifies that `synced_at - created_at <= 72 hours`. Any breach triggers an immediate compliance alert.

### 3.4 Duplicate Detection & "DUPLICATE" Tagging (Art. 22 & 23(4))
- If a buyer requests a reprint of an offline transaction that has subsequently been registered, the receipt rendering engine automatically adds a prominent watermark:
  `"DUPLICATE - REPRINT OF REGISTERED TAX INVOICE"`.
- This fulfills Article 22's anti-fraud mandate against circulating multiple valid original receipts for one transaction.
