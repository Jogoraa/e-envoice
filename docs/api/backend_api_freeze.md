# Backend API Freeze Specification (Version 1.0.0-RELEASE)

**Document Reference**: UT-API-FREEZE-2026-001  
**Freeze Status**: **OFFICIALLY FROZEN FOR FRONTEND & CLIENT INTEGRATION**  
**Compliance Baseline**: Federal Democratic Republic of Ethiopia Ministry of Revenues — Electronic Invoicing System Management Directive No. 1142/2018 EC (2026 GC) (*የኤሌክትሮኒክ ደረሰኝ ሥርዓት አስተዳደር መመሪያ ቁጥር 1142/2018*)  
**Primary Directive Source**: [`docs/directive/1142_የኤሌክትሮኒክ_ደረሰኝ_ሥርዓት_አስተዳደር_መመሪያ_ቁጥር_1142_2018.pdf`](file:///d:/UT/e-envoice/docs/directive/1142_የኤሌክትሮኒክ_ደረሰኝ_ሥርዓት_አስተዳደር_መመሪያ_ቁጥር_1142_2018.pdf)  
**Date of Freeze**: September 18, 2026

---

## 1. Architectural Guarantee & Freeze Declaration

All fiscal endpoints, request/response DTO schemas, error envelopes, and cryptographic verification contracts documented herein are **FROZEN**.  
Frontend applications (Web POS, Mobile Billing, Desktop Client, ERP Connectors) can build directly against this specification with guaranteed wire compatibility.

### Standard Request Ingress Headers

Every authenticated M2M or user-facing request to `/api/v1/*` (except public verification routes) must provide:

| Header Name | Type | Mandatory | Description |
| :--- | :--- | :--- | :--- |
| `X-API-Key` | String | Yes | Unique Client identifier allocated to the ERP / POS terminal. |
| `X-Client-Secret` | String | Yes | Shared secret for M2M authentication (verified via constant-time comparison). |
| `X-Tenant-ID` | UUID | Optional | Verified against client's registered tenant ID to prevent cross-tenant forgery. |
| `Idempotency-Key` | String | Mandatory on mutations | UUID v4 or unique string guaranteeing once-only fiscal counter allocation. |
| `X-Correlation-ID` | String | Optional | Distributed tracing UUID (auto-generated if omitted). |

---

## 2. Frozen Endpoint Catalog

### 1. Electronic Invoicing Lifecycle

#### `POST /api/v1/invoices`
- **Purpose**: Issues a fiscal electronic invoice, allocates the next monotonic sequence counter, records transactional outbox event, and initiates synchronous/asynchronous MoR EIRS registration.
- **Directive Citation**: Directive No. 1142/2018 Art. 4, Art. 8, Art. 9, Art. 10.
- **Request Headers**: `X-API-Key`, `X-Client-Secret`, `Idempotency-Key` (Mandatory).
- **Request Body**:
  ```json
  {
    "transactionType": "B2C",
    "paymentMode": "CASH",
    "paymentTerm": "IMMEDIATE",
    "buyer": {
      "legalName": "Customer Name",
      "tin": "0012345678",
      "phone": "+251911223344",
      "email": "buyer@example.com",
      "region": "14",
      "woreda": "02"
    },
    "items": [
      {
        "itemCode": "ITEM-001",
        "productDescription": "Standard Goods",
        "quantity": 2.00,
        "unit": "PCS",
        "unitPrice": 100.00,
        "discount": 0.00,
        "taxCode": "VAT_15"
      }
    ]
  }
  ```
- **Response (`200 OK` or `202 ACCEPTED`)**:
  ```json
  {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "tenantId": "7d2b5c40-1a2b-4c3d-8e4f-5a6b7c8d9e0f",
    "documentNumber": "0000000001",
    "invoiceCounter": 1,
    "invoiceDate": "2026-09-18T16:00:00Z",
    "transactionType": "B2C",
    "status": "REGISTERED",
    "preTaxTotal": 200.00,
    "taxTotal": 30.00,
    "grandTotal": 230.00,
    "currency": "ETB",
    "irn": "IRN-2026-09-18-0012345678-0000000001",
    "rrn": "RRN-99887766",
    "ackDate": "2026-09-18T16:00:01Z",
    "signedQr": "iVBORw0KGgoAAAANSUhEUgAA...",
    "reprintCount": 0
  }
  ```

#### `GET /api/v1/invoices/{id}`
- **Purpose**: Fetches full details of a specific invoice under caller's tenant boundary.
- **Response**: `InvoiceResponseDto` (`200 OK` or `404 NOT_FOUND`).

#### `GET /api/v1/invoices`
- **Purpose**: Paginated listing of tenant invoices with filtering by status and date range.
- **Query Params**: `page=0&size=20&status=REGISTERED`.

#### `GET /api/v1/invoices/{id}/receipt`
- **Purpose**: Generates the printable fiscal thermal receipt payload (ESC/POS or formatted text) including mandatory Directive Art. 4 items and Base64 QR code.

---

### 2. Fiscal Adjustments & Cancellations

#### `POST /api/v1/adjustments`
- **Purpose**: Issues a Credit Note or Debit Note adjusting a previously registered invoice. Direct modification of registered invoices is forbidden by law.
- **Directive Citation**: Directive No. 1142/2018 Art. 24.
- **Request Body**:
  ```json
  {
    "originalIrn": "IRN-2026-09-18-0012345678-0000000001",
    "type": "CREDIT_NOTE",
    "reason": "GOODS_RETURN",
    "adjustmentAmount": 50.00,
    "taxAdjustmentAmount": 7.50
  }
  ```

#### `POST /api/v1/cancellations`
- **Purpose**: Formally cancels an erroneous invoice within the statutory cancellation window.
- **Directive Citation**: Directive No. 1142/2018 Art. 23.
- **Request Body**:
  ```json
  {
    "irn": "IRN-2026-09-18-0012345678-0000000001",
    "reasonCode": "INCORRECT_BUYER_TIN",
    "justification": "Buyer TIN entered incorrectly by cashier."
  }
  ```

---

### 3. Offline Protocol & Device Reconciliation

#### `POST /api/v1/offline/sync`
- **Purpose**: Uploads batch of locally buffered invoices issued during network or EIRS downtime. Enforces 48h/72h statutory window and reconciles sequence numbers.
- **Directive Citation**: Directive No. 1142/2018 Art. 8(3), Art. 9, Art. 10.

---

### 4. Public Verification Endpoint (Open Ingress)

#### `GET /api/v1/public/verify/{irn}`
- **Purpose**: Public validation of electronic invoice authenticity for consumers, buyers, and field auditors.
- **Authentication**: NONE required. Open ingress.
- **Rate Limit**: Strictly 60 requests/minute per client IP (exceeding returns `429 TOO_MANY_REQUESTS`).
- **Privacy & Security**: Stripped of buyer PII, client secrets, and database primary keys.
- **Response (`200 OK`)**:
  ```json
  {
    "irn": "IRN-2026-09-18-0012345678-0000000001",
    "documentNumber": "0000000001",
    "invoiceDate": "2026-09-18T16:00:00Z",
    "sellerTin": "0012345678",
    "sellerLegalName": "Abyssinia Trading PLC",
    "transactionType": "B2C",
    "status": "REGISTERED",
    "preTaxTotal": 200.00,
    "taxTotal": 30.00,
    "exciseTotal": 0.00,
    "grandTotal": 230.00,
    "currency": "ETB",
    "verificationStatus": "VALID_REGISTERED",
    "ackDate": "2026-09-18T16:00:01Z"
  }
  ```

---

### 5. Audit & Data Portability

#### `GET /api/v1/audit/records`
- **Purpose**: Read-only access to tamper-evident audit logs with cryptographic hash chain proofs.
- **Authorization**: Requires `ROLE_AUTHORITY_AUDITOR` or `authority:audit` scope.

#### `POST /api/v1/tenants/export`
- **Purpose**: Initiates asynchronous standard data export (JSON/CSV) for taxpayer portability under Proclamation No. 983/2016 Art. 17.

---

## 3. Standardized Error Response Envelope

All error responses from the backend adhere to the following schema:

```json
{
  "error": "STRING_ERROR_CODE",
  "message": "Human readable technical English description.",
  "amharicMessage": "በአማርኛ የተዘጋጀ የሕግና የአሠራር ማብራሪያ።",
  "status": 400,
  "timestamp": "2026-09-18T16:00:00Z",
  "correlationId": "corr-uuid"
}
```

### Statutory Error Codes

| Error Code | HTTP Status | Regulatory Meaning |
| :--- | :--- | :--- |
| `TENANT_SUSPENDED` | `403 FORBIDDEN` | Tenant organization account is suspended. |
| `SUBSCRIPTION_SUSPENDED` | `402 PAYMENT_REQUIRED` | Commercial SaaS billing is suspended. Payment required. |
| `GOVERNMENT_AUTHORIZATION_REQUIRED` | `403 FORBIDDEN` | Commercial subscription paid, but MoR/EIRS accreditation is revoked/pending. |
| `EMPTY_LINE_ITEMS` | `400 BAD_REQUEST` | Invoice contains no lines (Directive Art. 4(1)(a)). |
| `INVALID_BUYER_TIN` | `400 BAD_REQUEST` | Buyer TIN missing/invalid on B2B transaction (Directive Art. 4(1)(b)). |
| `FINANCIAL_MUTATION_FORBIDDEN` | `400 BAD_REQUEST` | Attempted direct mutation of registered tax invoice (Directive Art. 23 & 24). |
| `SYNC_WINDOW_EXPIRED` | `422 UNPROCESSABLE_ENTITY` | Offline invoice older than statutory 48h/72h window (Directive Art. 10). |
| `RATE_LIMIT_EXCEEDED` | `429 TOO_MANY_REQUESTS` | Ingress request rate exceeded limit. |
