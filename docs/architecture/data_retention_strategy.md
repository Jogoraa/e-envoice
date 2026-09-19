# Data Retention, Portability & Deletion Strategy
## Compliance with Directive No. 1142/2026 & Federal Tax Proclamation No. 983/2016
### UT Electronic Invoicing Platform

---

## 1. Legal Retention Mandates in Ethiopia

Under Article 4(2)(d) of Directive No. 1142/2026 and Article 17 of the Federal Tax Administration Proclamation No. 983/2016:
- Taxpayers and invoicing service providers **must retain all tax invoices, receipts, debit/credit notes, cancellation records, and operation audit logs for a mandatory minimum of ten (10) years**.
- Records must remain accessible, retrievable, and verifiable upon demand by the Ministry of Revenues.
- Financial transactions cannot be purged simply because a SaaS subscription has expired or been terminated.

---

## 2. Tiered Storage Architecture

```text
┌─────────────────────────────────────────────────────────────┐
│                 HOT TIER (PostgreSQL Live)                  │
│  - Active transactions (Current Year + Previous 2 Years)    │
│  - Fast index lookups, real-time checkout & API queries     │
└──────────────────────────────┬──────────────────────────────┘
                               │ Automated Partition Detach
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                 WARM TIER (PostgreSQL Archive)              │
│  - Historical transactions (Years 3 through 5)              │
│  - Compressed tables, read-only replica queries             │
└──────────────────────────────┬──────────────────────────────┘
                               │ S3 Glacier / WORM Archive
                               ▼
┌─────────────────────────────────────────────────────────────┐
│               COLD TIER (Immutable Object Store)            │
│  - Archived data (Years 6 through 10)                       │
│  - Parquet/JSONL export packages with SHA-256 manifests     │
│  - WORM (Write Once, Read Many) compliance lock             │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. Tenant Data Portability Subsystem (Directive Art. 5(3))

Taxpayers have the explicit legal right to export their complete transaction history in a standardized, machine-readable format at any time to migrate to another provider.

### 3.1 Asynchronous Export Workflow
1. Tenant triggers export via `POST /api/v1/portability/exports`.
2. The platform initiates a background `TenantExportJob`:
   - Extracts taxpayer profile, branch registry, device list.
   - Extracts all invoices, lines, tax breakdowns, payments, and references as JSON/XML.
   - Compiles rendered PDF/thermal receipts from object storage.
   - Generates an immutable SHA-256 cryptographic manifest.
3. The package is zipped and uploaded to a secure object storage bucket.
4. An email notification is dispatched to the tenant owner with a signed, time-limited download URL (valid for 48 hours).

---

## 4. Retention-Aware Tenant Deletion

When a business cancels its SaaS subscription or requests account closure:

### 4.1 Permitted Application Deletions:
- Active user passwords and session tokens.
- OAuth2 client secrets and API keys (revoked immediately).
- Webhook subscriptions and notification endpoints.
- Draft invoices and non-registered staging records.

### 4.2 Prohibited Deletions (Mandatory Legal Preservation):
- Registered invoices (`REGISTERED`, `CANCELLED`).
- Tax Credit Notes and Debit Notes.
- MoR IRN/RRN acknowledgments and signed QR payloads.
- Operational audit logs (`audit_events`).
- INSA digital signature verification records.

*Implementation: The platform transitions the tenant state to `ARCHIVED`. The taxpayer's data is placed in a read-only compliance vault accessible only by Tax Authority auditors or the verified taxpayer upon legal request.*
