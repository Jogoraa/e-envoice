# Domain Model Specification
## UT Electronic Invoicing Platform — Domain-Driven Design (DDD)
### Core Aggregates, Value Objects, State Machines & Domain Events

---

## 1. Domain Aggregate Overview

```text
┌─────────────────────────────────────────────────────────────┐
│                      INVOICE AGGREGATE                      │
│                                                             │
│  [Invoice (Root)]                                           │
│   ├── UUID id                                               │
│   ├── TenantId tenantId                                     │
│   ├── DocumentNumber documentNumber                         │
│   ├── InvoiceCounter invoiceCounter                         │
│   ├── TransactionType (B2B, B2C, EXPORT)                    │
│   ├── InvoiceStatus status                                  │
│   ├── SellerSnapshot seller                                 │
│   ├── BuyerSnapshot buyer                                   │
│   ├── BranchReference branch                                │
│   ├── DeviceReference device                                │
│   ├── List<InvoiceLine> lines (1..*)                        │
│   ├── List<TaxBreakdown> taxes (1..*)                       │
│   ├── PaymentSnapshot payment                               │
│   ├── GovernmentRegistration registration                   │
│   ├── DigitalSignature signature                            │
│   └── List<InvoiceLifecycleEvent> events                    │
└─────────────────────────────────────────────────────────────┘

┌────────────────────────────┐    ┌───────────────────────────┐
│   ADJUSTMENT AGGREGATE     │    │  CANCELLATION AGGREGATE   │
│                            │    │                           │
│  [TaxAdjustmentNote (Root)]│    │  [CancellationReq (Root)] │
│   ├── UUID id              │    │   ├── UUID id             │
│   ├── TenantId tenantId    │    │   ├── TenantId tenantId   │
│   ├── NoteType (CREDIT/    │    │   ├── UUID invoiceId      │
│   │            DEBIT)      │    │   ├── Irn irn             │
│   ├── UUID originalInvoice │    │   ├── CancellationState   │
│   ├── Irn originalIrn      │    │   ├── ReasonCategory      │
│   ├── Money adjustedPreTax │    │   ├── SlaDeadline (48h)   │
│   ├── Money adjustedTax    │    │   └── EvidenceSnapshot    │
│   └── AdjustmentReason     │    └───────────────────────────┘
└────────────────────────────┘
```

---

## 2. Core Entities & Value Objects

### 2.1 Invoice Aggregate Root (`et.ut.einvoice.invoicing.domain`)
- **`Invoice`**:
  - `id`: `UUID` (Globally unique identifier, UUIDv7)
  - `tenantId`: `TenantId` (Tenant security boundary)
  - `documentNumber`: `DocumentNumber` (Business sequential invoice number, e.g., `INV-2026-000014`)
  - `invoiceCounter`: `Long` (Sequential counter per system number required by MoR)
  - `invoiceDate`: `Instant` (UTC transaction timestamp)
  - `transactionType`: `TransactionType` (`B2B`, `B2C`, `EXPORT`)
  - `paymentDetails`: `PaymentDetails` (Mode: `CASH`, `BANK_TRANSFER`, `MOBILE_PAYMENT`; Term: `IMMEDIATE`, `CREDIT_30`)
  - `referenceDetails`: `ReferenceDetails` (Previous IRN for sequence linking, related contract)
  - `seller`: `SellerSnapshot` (Immutable snapshot of legal name, TIN, VAT number, address at invoice issuance)
  - `buyer`: `BuyerSnapshot` (Buyer name, TIN/KID, address, phone, email)
  - `lines`: `List<InvoiceLine>` (Immutable collection of sold items)
  - `valueSummary`: `ValueSummary` (PreTaxTotal, TaxTotal, ExciseTotal, GrandTotal in ETB)
  - `registration`: `GovernmentRegistration` (IRN, RRN, AckDate, Status, SignedQR, SignedHash)
  - `status`: `InvoiceStatus` (State machine lifecycle status)
  - `reprintCount`: `Integer` (Counter for tracking duplicate print requests per Art. 22)

### 2.2 Invoice Line (`InvoiceLine`)
- `lineNumber`: `Integer` (1-based line index)
- `itemCode`: `String` (Internal SKU or product code)
- `productDescription`: `String` (Clear description of goods/services per Art. 4(1))
- `natureOfSupplies`: `NatureOfSupplies` (`GOODS`, `SERVICES`)
- `unit`: `String` (`PCS`, `KG`, `LTR`, `HRS`, `SET`)
- `quantity`: `BigDecimal` (Quantity sold, scale 4)
- `unitPrice`: `BigDecimal` (Unit price pre-tax, scale 2)
- `preTaxValue`: `BigDecimal` (Quantity * UnitPrice - Discount)
- `taxCode`: `TaxCode` (`VAT15`, `EXEMPT`, `ZERO_RATED`, `TOT_2`, `TOT_10`)
- `taxRate`: `BigDecimal` (e.g., `0.1500`)
- `taxAmount`: `BigDecimal` (Tax calculated by TaxEngine)
- `discount`: `BigDecimal` (Line discount)
- `exciseTaxValue`: `BigDecimal` (Excise tax if applicable)
- `totalLineAmount`: `BigDecimal` (PreTaxValue + TaxAmount + ExciseTaxValue)

### 2.3 Value Objects
- **`Irn`**: `String` — 64-character hexadecimal SHA-256 Invoice Reference Number returned by MoR.
- **`Rrn`**: `String` — Receipt Reference Number.
- **`SignedQr`**: `String` — Base64 encoded PNG or string payload containing MoR digital signature.
- **`Money`**: `BigDecimal amount, Currency currency` (Default `ETB`).
- **`DocumentNumber`**: `String prefix, Long sequence, Integer year`.

---

## 3. Invoice Lifecycle State Machine

```text
       ┌───────────────┐
       │     DRAFT     │
       └───────┬───────┘
               │ validate()
               ▼
       ┌───────────────┐
       │   VALIDATED   │
       └───────┬───────┘
               │ submitToMor()
               ▼
       ┌────────────────────────┐
       │  SUBMISSION_PENDING    │
       └───┬────────────────┬───┘
           │                │
  [MoR 200 OK]      [Network Timeout / Offline]
           │                │
           ▼                ▼
   ┌───────────────┐ ┌───────────────────┐
   │  REGISTERED   │ │ OFFLINE_BUFFERED  │
   └───┬───────────┘ └───┬───────────────┘
       │                 │ [Connectivity Restored:
       │                 │  Reconciliation Job]
       │                 └───────► [submitToMor()]
       │
   [Cancellation Approved]
       │
       ▼
   ┌───────────────┐
   │   CANCELLED   │
   └───────────────┘
```

### State Definitions:
1. **`DRAFT`**: Invoice payload created; validation in progress.
2. **`VALIDATED`**: Tax calculations verified, buyer details conform to schema, INSA digital signature generated.
3. **`SUBMISSION_PENDING`**: Payload dispatched to MoR gateway over HTTPS/REST.
4. **`REGISTERED`**: MoR returned HTTP 200 with valid IRN, RRN, and SignedQR. Invoice is now **legally issued and immutable**.
5. **`OFFLINE_BUFFERED`**: Dispatched during network outage; signed payload stored in secure local buffer queue for 72h replay.
6. **`SUBMISSION_FAILED`**: Rejected by MoR with fatal validation errors (e.g., TIN mismatch); requires resolution.
7. **`CANCELLED`**: Successfully cancelled via approved Ministry workflow (Art. 26).

---

## 4. Cancellation State Machine (Directive Art. 26)

```text
 ┌─────────────┐
 │  REQUESTED  │ ──► Taxpayer submits cancellation with justification
 └──────┬──────┘
        │ [Authority requests more info]
        ▼
 ┌──────────────────────┐
 │  EVIDENCE_REQUIRED   │ ──► 48-hour countdown SLA timer starts
 └──────┬───────────────┘
        │ [Evidence submitted within 48h]
        ▼
 ┌──────────────────────────┐
 │  SUBMITTED_TO_AUTHORITY  │ ──► Dispatched to MoR /v1/cancel endpoint
 └──────┬───────────────────┘
        ├──────────────────────┬──────────────────────┐
 [Authority Approves]   [Authority Rejects]    [48h SLA Breached]
        │                      │                      │
        ▼                      ▼                      ▼
 ┌──────────────┐       ┌──────────────┐       ┌──────────────┐
 │  REGISTERED  │       │   REJECTED   │       │ SLA_EXPIRED  │
 └──────────────┘       └──────────────┘       └──────────────┘
```

---

## 5. Domain Events

All cross-module actions are triggered by immutable domain events published via `DomainEventPublisher`:

| Domain Event | Payload Data | Subscribing Modules |
|---|---|---|
| **`InvoiceRegisteredEvent`** | `invoiceId`, `tenantId`, `irn`, `buyerEmail`, `buyerPhone`, `totalValue` | `documents` (render PDF), `notifications` (send email/SMS), `webhooks` (dispatch external ERP webhook), `audit` (record registration). |
| **`InvoiceOfflineBufferedEvent`** | `invoiceId`, `tenantId`, `bufferedAt`, `offlineSeqNo` | `offline` (track queue depth), `audit` (record offline transaction). |
| **`OfflineReconciliationCompletedEvent`**| `batchId`, `replayedCount`, `failedCount`, `tenantId` | `audit`, `reporting`, `notifications` (alert admin on failures). |
| **`TaxAdjustmentCreatedEvent`** | `adjustmentId`, `noteType`, `originalIrn`, `adjustedTotal` | `documents`, `government`, `audit`, `webhooks`. |
| **`InvoiceCancellationRequestedEvent`** | `cancellationId`, `invoiceId`, `irn`, `slaDeadline` | `cancellation` (schedule 48h SLA timer), `notifications` (notify buyer). |
| **`InvoiceCancelledEvent`** | `cancellationId`, `invoiceId`, `irn`, `morCancellationRef` | `invoicing` (update status to CANCELLED), `documents` (watermark CANCELLED), `notifications` (notify buyer). |
| **`SecurityAuditEvent`** | `actorId`, `tenantId`, `action`, `resource`, `ip`, `payloadHash` | `audit` (append to hash-chained log). |
