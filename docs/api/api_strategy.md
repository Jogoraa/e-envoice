# Public API Strategy & Contract Specification
## Versioned REST Platform (`/api/v1`) — OpenAPI 3 / RFC-Compliant
### UT Electronic Invoicing Platform

---

## 1. API Platform Principles

The UT Invoicing Platform exposes a high-performance, developer-friendly, and enterprise-grade public API platform designed for:
1. Web, POS, and mobile frontend clients.
2. Third-party ERPs (SAP, Microsoft Dynamics 365, Odoo, custom software).
3. E-commerce marketplaces and digital payment platforms.
4. Future UT ERP modules (which consume this exact API without privileged internal shortcuts).

---

## 2. Global Request & Response Headers

### 2.1 Request Headers
```http
Authorization: Bearer <JWT_OR_API_KEY>
Content-Type: application/json
Accept: application/json
Idempotency-Key: 7e7d82a1-0f4b-48ae-94c6-a681c63b1234
X-Correlation-ID: req-9876-5432-1098
Accept-Language: en-US, am-ET;q=0.9
```

*Note: The server derives `tenantId` strictly from the authenticated token or client credentials. Any arbitrary `X-Tenant-ID` header supplied without administrative token authority is strictly rejected.*

### 2.2 Response Headers
```http
Content-Type: application/json
X-Correlation-ID: req-9876-5432-1098
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 994
X-RateLimit-Reset: 1726656000
```

---

## 3. Standardized Error Contract

All non-2xx HTTP responses return a deterministic JSON error envelope:

```json
{
  "code": "INVOICE_ALREADY_REGISTERED",
  "message": "The invoice with document number INV-2026-000014 has already been registered with the Ministry of Revenues.",
  "localizedMessage": "በዚህ የሰነድ ቁጥር INV-2026-000014 የተዘጋጀው ደረሰኝ አስቀድሞ በገቢዎች ሚኒስቴር ተመዝግቧል።",
  "requestId": "req-9876-5432-1098",
  "timestamp": "2026-09-18T13:05:00Z",
  "details": [
    {
      "field": "documentDetails.documentNumber",
      "issue": "DUPLICATE_DOCUMENT_NUMBER",
      "rejectedValue": "14"
    }
  ]
}
```

---

## 4. Standard Core Endpoints Overview

### 4.1 Invoice Management (`/api/v1/invoices`)
- `POST /api/v1/invoices` — Create and register a sales invoice (real-time synchronous registration with MoR). Requires `Idempotency-Key`.
- `GET /api/v1/invoices` — List invoices with pagination, date range filtering, status, and search.
- `GET /api/v1/invoices/{id}` — Retrieve invoice details by UUID.
- `GET /api/v1/invoices/by-irn/{irn}` — Retrieve invoice details by MoR IRN.
- `GET /api/v1/invoices/{id}/document` — Download rendered invoice document (`?format=pdf|html|thermal`).

### 4.2 Adjustments (Credit & Debit Notes per Art. 25)
- `POST /api/v1/adjustments/credit-notes` — Issue a Tax Credit Note referencing an original invoice IRN.
- `POST /api/v1/adjustments/debit-notes` — Issue a Tax Debit Note referencing an original invoice IRN.
- `GET /api/v1/adjustments` — List adjustment notes.

### 4.3 Cancellation Workflow (Art. 26)
- `POST /api/v1/cancellations` — Submit an invoice cancellation request with category & justification.
- `POST /api/v1/cancellations/{id}/evidence` — Submit supporting evidence before the 48-hour SLA deadline.
- `GET /api/v1/cancellations/{id}` — Check cancellation approval status from MoR.

### 4.4 Offline Synchronization (Art. 4(4) & Art. 23(4))
- `POST /api/v1/offline/sync` — Submit a batch of offline-buffered transactions for 72h reconciliation replay.
- `GET /api/v1/offline/status` — Check device sequence ranges and pending sync queue depth.

### 4.5 Receipts & Other Transaction Types
- `POST /api/v1/receipts/sales` — Issue a simplified cash sales receipt.
- `POST /api/v1/receipts/withholding` — Issue a withholding tax receipt.
- `POST /api/v1/receipts/purchase-vouchers` — Issue a purchase confirmation voucher.

### 4.6 Authority Audit & Inspection (Art. 4(2)(c) & Art. 23(2))
- `GET /api/v1/authority/invoices` — Dedicated read-only Authority inspection endpoint (requires `ROLE_AUTHORITY_AUDITOR`).
- `GET /api/v1/authority/audit-logs` — Query immutable operation audit trail with date/TIN filters.

---

## 5. Idempotency & Distributed Lock Mechanics

1. Client provides a unique `Idempotency-Key` in the request header.
2. The `IdempotencyFilter` checks Redis:
   - If key exists and status is `PROCESSING`, request is rejected with `HTTP 409 Conflict` (Concurrent duplicate).
   - If key exists and status is `COMPLETED`, the cached response payload is immediately returned with `X-Cache-Lookup: HIT`.
   - If key is new, an atomic Redis lock is acquired (`SET key PROCESSING NX EX 120`).
3. Upon transaction completion, the response is cached under the key for 24 hours, and the lock is updated to `COMPLETED`.

---

## 6. Webhooks & Outbound Events

The platform provides signed webhooks for asynchronous downstream integration:
- **Events**: `invoice.registered`, `invoice.rejected`, `invoice.cancelled`, `adjustment.created`, `offline.synced`.
- **Payload Signature**: Every webhook delivery includes an `X-UT-Signature` header calculated as:
  `HMAC-SHA256(webhook_secret, timestamp + "." + payload_body)`.
- **Delivery Guarantees**: At-least-once delivery with exponential backoff (1m, 5m, 15m, 1h, 6h, 24h) and Dead-Letter Queue (DLQ) logging.
