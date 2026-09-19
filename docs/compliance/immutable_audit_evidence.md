# Regulatory Compliance Specification: Immutable Fiscal Audit Evidence

**Primary Legal Authority:**
1. **Directive No. 1142/2018 EC (2026 GC):** *የኤሌክትሮኒክ ደረሰኝ ሥርዓት አስተዳደር መመሪያ ቁጥር 1142/2018* (Electronic Invoicing System Administration Directive)
   - **Article 4(2)(b):** Requirement to record an operation audit log tracking data exchanges with the Electronic Invoice Registration System (EIRS), daily user activities, timestamps, and the identity of the individual who performed them, accessible to authorized entities.
   - **Article 4(2)(c):** Requirement that the system enable the Tax Authority to extract, view, and audit data at any time as required.
   - **Article 4(2)(d):** Requirement for sufficient storage capacity and the capability to retain tax information for the duration prescribed by law.
   - **Article 4(3)(b):** Role-Based Access Control (RBAC) mechanism tailoring access to user roles.
   - **Article 4(3)(d):** Service access restricted to authorized authentication methods (username/password or equivalent).
   - **Article 4(6):** Fulfillment of security procedures (INSA software security clearance, communication encryption, and digital signatures).
   - **Article 15(4) & (5):** Supplier obligation to provide reports and transaction data upon Authority inspection.
   - **Article 15(6):** Strict data confidentiality: software shall not collect unauthorized data, transfer data outside the system, or use data for unauthorized purposes.
   - **Article 19(5):** Mandatory taxpayer registration through the Authority's portal (System Number, API Key, Client Secret) and INSA Digital Signature Certificate.
   - **Article 20(3)(e):** Requirement to store invoice data for the period prescribed by law and provide upon request.
   - **Article 20(3)(g):** Requirement to provide a publicly accessible invoice verification service to validate invoice authenticity.
   - **Article 23(2):** Taxpayer obligation to grant access to Authority employees to view or inspect the system at any time.
   - **Article 25:** Issuance of Tax Debit and Credit Notes for sales adjustments.
   - **Article 26:** Regulated cancellation workflow for registered electronic invoices.
   - **Article 27(2):** Taxpayer liability where invoice information is altered or sales register system data is deleted without authorization.
   - **Article 27(5):** Administrative penalties and criminal liability under Tax Administration Proclamation No. 983/2016 for directive violations.
   - **Article 28(1) & (2):** Software and service provider civil/criminal liability and security bond forfeiture for system defects and discrepancies.
2. **Federal Tax Administration Proclamation No. 983/2016:**
   - **Article 17:** Statutory obligation to maintain books and records in Ethiopia for the period prescribed by tax law.
   - **Part Fourteen:** Civil, administrative, and criminal penalties for record falsification, failure to maintain records, and tax fraud.

---

## 1. Statutory Mandates vs. Engineering Implementation Controls

To ensure strict legal veracity, the platform distinguishes between the legal requirements set forth in Ethiopian tax law and the engineering mechanisms implemented in the UT Electronic Invoicing Platform:

| Dimension | Directive / Statutory Mandate | UT Platform Engineering Implementation Control | Evidence Level |
| :--- | :--- | :--- | :--- |
| **Operation Audit Trail** | Directive Art. 4(2)(b): Record operation audit log tracking EIRS exchanges, user activities, timestamps, and actor identities. | Monotonic 64-bit sequence numbers, millisecond UTC timestamps, canonical serialization, and append-only `audit_events` table. | `ENGINEERING_VERIFIED` |
| **Tamper Prevention** | Directive Art. 27(2): Prohibition against altering invoice information or deleting sales register system data without authorization. | PostgreSQL trigger `trg_audit_events_immutability` blocking `UPDATE`/`DELETE`, JPA `@PreUpdate`/`@PreRemove` lifecycle guards, deterministic SHA-256 backward hash chain. | `ENGINEERING_VERIFIED` |
| **Inspection & Audit Access** | Directive Art. 4(2)(c) & Art. 23(2): Enable Tax Authority to extract, view, and audit data at any time on demand. | On-demand auditor verification API (`/api/v1/authority/audit/export/{tenantId}/{streamId}`), `AuditExportPackage`, and standalone offline verifier `AuditExportVerifier`. | `ENGINEERING_VERIFIED` |
| **Record Retention Period** | Directive Art. 4(2)(d), Art. 20(3)(e); Proclamation No. 983/2016 Art. 17: Retain records for the period prescribed by law. | Configurable time-partitioned PostgreSQL storage, cold retention manifests, and immutable evidence manifests. *(Note: The Directive does not state a "10-year" number; records are retained for the period prescribed by applicable law).* | `ENGINEERING_VERIFIED` |
| **Public Verification** | Directive Art. 20(3)(g): Provide a publicly accessible invoice verification service to validate invoice authenticity. | Public verification endpoint `GET /api/v1/public/verify/{irn}` with IP rate limiting and zero buyer PII leakage. | `ENGINEERING_VERIFIED` |
| **Cryptographic Security** | Directive Art. 4(6): INSA security clearance, communication encryption, and digital signatures. | RSA-2048 / SHA-256 PKCS#1 v1.5 digital signature provider, TLS 1.3 encryption, and software key abstraction. Physical HSM deployment is deferred to production infrastructure. | `SPECIFICATION_VERIFIED` |
| **Credential Management** | Directive Art. 19(5): System Number, API Key, Client Secret from Authority; Digital Signature Certificate from INSA. | Multi-tier credential model in `TaxpayerProfile`, hashed secrets, and `AuditPayloadSanitizer` preventing credential leakage into logs. | `ENGINEERING_VERIFIED` |

---

## 2. Statutory and Operational Audit Event Catalog

Every operation with legal, fiscal, or security significance generates an audit event recorded in the immutable ledger. The catalog is separated into **Statutory Fiscal Operations** (directly implementing Directive workflows) and **Platform Governance Operations** (internal integrity controls):

### 2.1 Statutory Fiscal Operations
| Action Enum | Statutory Legal Basis | Description |
| :--- | :--- | :--- |
| `INVOICE_CREATED` | Directive Art. 4(1)(a) | Draft generation of an electronic sales invoice containing statutory line items. |
| `INVOICE_SUBMITTED` | Directive Art. 4(1)(b) | Real-time transmission of invoice payload to MoR/EIRS gateway. |
| `INVOICE_REGISTERED` | Directive Art. 4(1)(c) | Successful validation, receipt of IRN, RRN, and QR code from tax authority. |
| `INVOICE_REJECTED` | Directive Art. 4(1)(b) | Gateway validation failure or schema rejection by MoR EIRS. |
| `INVOICE_CANCEL_REQUESTED` | Directive Art. 4(1)(g), Art. 26(1) | Formal request by taxpayer to cancel an erroneous or duplicate registered invoice. |
| `INVOICE_CANCELLED` | Directive Art. 26(4) | Authority-approved cancellation registered in the EIRS system. |
| `CREDIT_NOTE_CREATED` | Directive Art. 25(1) | Downward fiscal adjustment referencing registered original invoice. |
| `DEBIT_NOTE_CREATED` | Directive Art. 25(1) | Upward fiscal adjustment referencing registered original invoice. |
| `OFFLINE_INVOICE_CREATED` | Directive Art. 4(4), Art. 23(4) | Autonomous offline invoice issuance during connectivity outage for Annex 2 sectors. |
| `OFFLINE_INVOICE_RECONCILED`| Directive Art. 4(4)(b), Art. 23(4) | Batch reconciliation and registration within 72 hours of connectivity restoration. |
| `SEQUENCE_ALLOCATED` | Directive Art. 4(1)(c) | Autonomous atomic allocation of sequential invoice document number. |
| `SEQUENCE_MISMATCH` | Directive Art. 27(2) | Detection of sequence gap or out-of-order document sequencing. |
| `GOVERNMENT_RESPONSE_RECEIVED`| Directive Art. 4(1)(b), (c) | Cryptographic verification and ingestion of MoR digital response. |
| `GOVERNMENT_RECONCILIATION` | Directive Art. 15(4), Art. 28(2) | Daily fiscal ledger reconciliation against authority records to detect discrepancies. |

### 2.2 Platform Governance & Security Operations (Engineering Controls)
| Action Enum | Engineering Purpose | Description |
| :--- | :--- | :--- |
| `TENANT_CREATED` | Multi-Tenant Lifecycle | Onboarding of new taxpayer enterprise entity. |
| `TENANT_SUSPENDED` | Commercial / Governance | Suspension of taxpayer invoicing privileges due to subscription or compliance breach. |
| `TENANT_CONFIG_CHANGED` | Configuration Governance | Modification of taxpayer operating parameters or tax rates. |
| `FEATURE_FLAG_CHANGED` | System Control | Toggling of platform operational capabilities. |
| `GOVERNMENT_CREDENTIAL_CHANGED`| Key Governance (Art. 4(2)(a)) | Rotation of taxpayer digital certificates, API keys, or Client Secrets. |
| `API_CLIENT_CREATED` | Access Governance (Art. 4(3)(d)) | Issuance of programmatic API credentials to taxpayer ERP system. |
| `API_CLIENT_REVOKED` | Access Governance (Art. 4(3)(d)) | Immediate revocation of compromised taxpayer API credentials. |
| `USER_PERMISSION_CHANGED` | RBAC Governance (Art. 4(3)(b)) | Modification of RBAC roles and permissions for taxpayer operating staff. |
| `SECURITY_EVENT` | Threat Telemetry (Art. 4(6)) | Detected intrusion attempt, rate limit breach, or cryptographic tampering anomaly. |
| `IDEMPOTENCY_CONFLICT` | Financial Concurrency | Rejection of duplicate submission containing existing client key with conflicting payload. |

---

## 3. Statutory Retention Period Clarification

> [!IMPORTANT]
> **Legal Retention Period Baseline:**
> - Directive No. 1142/2018 EC does **not** specify a fixed numeric "10-year" period. In Articles 4(2)(d) and 20(3)(e), it explicitly mandates that tax information and invoice records be retained **"for the duration prescribed by law"** (*በህግ ለተደነገገው ጊዜ ያክል*).
> - The parent statutory retention period is governed by **Federal Tax Administration Proclamation No. 983/2016 Article 17**, which requires books and records to be maintained for the statutory assessment period.
> - The platform is engineered to support indefinite, configurable retention with immutable time-based partitioning.

---

## 4. Current Implementation Status vs. Deferred Deployment Controls

> “Development-stage cryptographic, tamper-evident, append-only audit integrity controls are implemented and adversarially tested. Independent immutable ledger storage, production key custody, WORM/archive controls, and host/filesystem hardening remain deployment-stage controls.”

