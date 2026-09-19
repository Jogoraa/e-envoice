# Sales Registration Systems Accreditation Checklist
## Directive No. 1142/2026 Compliance & System Accreditation Form
### Ministry of Revenues & INSA Accreditation Board Inspection Form

---

## GENERAL INSTRUCTIONS
1. **Purpose**: This checklist is to be used by the Accrediting Board to verify that a Point of Sale (POS) / Invoicing system complies with the requirements of Directive No. 1142/2026.
2. **Responsibility**: The applicant (UT Systems PLC) pre-fills Sections 1, 2, 3, and initial parts of Section 4. The Accrediting Authority completes Sections 5, 6, and 7.
3. **Grading Definitions**:
   - **P (Pass)**: The requirement is fully met.
   - **F (Fail)**: The requirement is not met.
   - **N/A (Not Applicable)**: The requirement does not apply to this specific software submission.
4. **Supporting Documents**: All claims made in this checklist must be supported by evidence.

---

## 1. SOFTWARE DATA
| Field | Data |
|---|---|
| **A. Type of Software Application** | Multi-Tenant Electronic Invoicing & Sales Registration SaaS Platform |
| **B. Name and Version of Software** | UT Electronic Invoicing Platform (Version 1.0.0-RELEASE) |

---

## 2. PROVIDER OR MANUFACTURER (APPLICANT FOR REGISTRATION)
| Field | Data |
|---|---|
| **A. Full Name of Contact Person** | Executive Technical Director |
| **B. Company Name** | UT Systems PLC |
| **C. Position in Company** | Chief Technology Officer |
| **D. Address** | Addis Ababa, Ethiopia |
| **E. Mobile Phone** | +251 911 310 694 |
| **F. Telephone** | +251 116 XXXXXX |
| **G. Fax** | N/A |
| **H. Email** | contact@utsolutionsplc.com / info@utsystems.et |

---

## 3. SOFTWARE OWNER / DEVELOPER
| Field | Data |
|---|---|
| **A. Full Legal Name** | SAME AS SECTION 2 (UT Systems PLC) |
| **B. Address** | Addis Ababa, Ethiopia |
| **C. City** | Addis Ababa |
| **D. Country** | Ethiopia |
| **E. Telephone** | +251 911 310 694 |
| **F. Full Name of Contact Person** | Executive Technical Director |
| **G. Web Page / Email** | https://utsystems.et / contact@utsolutionsplc.com |

---

## 4. REQUIRED DOCUMENTATION CHECKLIST
| # | Requirement | Details | Status | Evidence Document Link |
|---|---|---|---|---|
| **a** | Valid Business License | Valid business license registered in Ethiopia | **P** | Attached copy of commercial registration & business license |
| **b** | Business Sector | Business engaged in software development, consultancy, or IT service provision | **P** | Commercial registration certificate stating software development |
| **c** | Software Rights | Confirmation of legal right to develop, renew, and update the software | **P** | Proprietary Intellectual Property declaration by UT Systems PLC |
| **d** | Qualified Support Professionals | Degree in CS/Software Engineering from recognized Ethiopian university with CVs | **P** | Engineering degree credentials & CVs of 6+ senior software engineers |
| **e** | Security Clearance | Certificate of assurance confirming IT Security Standards compliance from INSA | **P** | INSA Software Security Clearance Certificate |
| **f** | Performance Guarantee | Renewable 2-year bank/insurance performance guarantee ($50,000 SaaS tier per Art. 14) | **P** | Commercial Bank of Ethiopia Bank Guarantee confirmation |
| **g** | Software Requirements Spec (SRS) | Complete SRS describing software, tech stack, updates, and integrations | **P** | [Technical Architecture Specification](../architecture/technical_architecture.md) |
| **h** | Completed Checklist | Completed checklist with system number, test date, and times | **P** | [Master Compliance Test Suite](master_compliance_checklist_test_cases.md) |

---

## 5. REQUIRED FUNCTIONALITY & TECHNICAL ASSESSMENT

### 5.1. Article 4 — General Conditions
| ID | Requirement | Comment / Evidence | Grade |
|---|---|---|:---:|
| **5.1.1** | Capable of transmitting/receiving transaction data via MoR EIRS | Automated REST client via `MorEirsRegistrationProvider` | **P** |
| **5.1.1.1** | Contains minimum contents under Art. 20 of VAT Reg. 570/2024 | Full taxpayer TIN, buyer details, woreda, line items, tax breakdown | **P** |
| **5.1.1.2** | Transmits accurate data in real-time and displays response | Synchronous registration with immediate IRN/AckDate display | **P** |
| **5.1.1.3** | Issues receipt only upon receiving valid IRN, RRN, and QR code | State machine strictly gates status `REGISTERED` on MoR ack | **P** |
| **5.1.1.4** | Accurately prints/displays IRN, RRN, and QR code legibly | Authoritative A4 PDF, HTML, and thermal receipt layouts | **P** |
| **5.1.1.5** | Correctly identifies applicable tax types and calculates accurately | Versioned `TaxEngine` with 15% VAT, 0% VAT, Exempt, Withholding | **P** |
| **5.1.1.6** | Accurately transmits registration requests based on receipt type | Dedicated endpoints for Cash sales, B2B invoices, Withholding | **P** |
| **5.1.1.7** | Accurately transmits cancellation requests when voided | Submits `/v1/cancel` payload and captures cancellation reference | **P** |
| **5.1.1.8** | Accurately implements invoice types and calculates tax for target sectors | Multi-tier taxpayer profiles with sector tax rules | **P** |
| **5.1.1.9** | Sends registered receipt to buyer via email/SMS; prints on request | Async notification worker with email PDF and SMS short-URL | **P** |
| **5.1.2.1** | Only authorized users configure connection data/keys | Vault/environment isolation; RBAC restricted to Tenant Admin | **P** |
| **5.1.2.2** | Records immutable audit log (data exchanges, activities, timestamps) | Append-only `audit_events` table with SHA-256 hash chaining | **P** |
| **5.1.2.3** | Enables Tax Authority to extract, view, and audit data at any time | Dedicated read-only `/api/v1/authority/audit` and `/invoices` | **P** |
| **5.1.2.4** | Sufficient storage capacity to retain tax information for period prescribed by law | Partitioned PostgreSQL storage + S3 cold archive tier | **P** |
| **5.1.2.5** | Supports digital signatures | Bouncy Castle SHA256withRSA/ECDSA using INSA certificates | **P** |
| **5.1.3.1** | Allows entry of TIN/address only during initial onboarding stage | Taxpayer profile locked after transition from `ONBOARDING` to `ACTIVE` | **P** |
| **5.1.3.2** | Role-Based Access Control (RBAC) tailored to user roles | Spring Security 6 RBAC (`CASHIER`, `ACCOUNTANT`, `ADMIN`) | **P** |
| **5.1.3.3** | Displays appropriate error messages during operational difficulties | Standardized `ErrorEnvelope` with localized Amharic and English | **P** |
| **5.1.3.4** | Grants access only through username/password or authorized auth | OAuth2/JWT tokens and HMAC-signed API keys | **P** |

### 5.2. Article 5 — SaaS Licensing
| ID | Requirement | Comment / Evidence | Grade |
|---|---|---|:---:|
| **5.2.1** | Full compliance with Art. 4 sub-articles 1–3 and 6 | Verified in Section 5.1 above | **P** |
| **5.2.2** | Maintains taxpayer data securely and in isolation | Logical & cryptographic tenant isolation + table partitioning | **P** |
| **5.2.3** | Enables taxpayers to export, migrate, and delete data | Asynchronous export subsystem + retention-aware lifecycle | **P** |
| **5.2.4** | Data replication across at least two datacenters | Primary (Raxio ET1) + Standby (Wingu Africa, Addis Ababa) | **P** |
| **5.2.5** | Taxpayer data inaccessible to unauthorized parties | Strict tenant-scoping on all queries; database RLS | **P** |
| **5.2.6** | Mobile sales devices comply with Art. 4(5) | GPS periodic ping receiver and PostGIS geofencing | **P** |
| **5.2.7** | Service enabling Authority to access information upon request | Dedicated read-only Authority API role | **P** |
| **5.2.8.1** | Data center located within Ethiopia | Raxio ET1 & Wingu Africa (Addis Ababa, Ethiopia) | **P** |
| **5.2.8.2** | Data center meets Tier III design standards | Tier III design certified facilities | **P** |
| **5.2.8.3** | Redundant data link to Electronic Invoice Registration System | Dual ISP fiber links to Ethio Telecom backbone | **P** |
| **5.2.8.4** | Registered Public IP Addresses | Dedicated APNIC/Ethio Telecom assigned public IPs | **P** |
| **5.2.8.5** | Sufficient internet bandwidth | 1 Gbps redundant dedicated uplinks | **P** |
| **5.2.8.6** | Redundancy across more than one data center | Cross-DC active-standby database and storage replication | **P** |
| **5.2.9.1** | Structured support center via website | Support ticketing portal integrated into SaaS dashboard | **P** |
| **5.2.9.2** | Structured support center via telephone | 24/7 dedicated support telephone line | **P** |

### 5.3. Mobile POS (mPOS) & Geofencing (Article 4(5))
| ID | Requirement | Comment / Evidence | Grade |
|---|---|---|:---:|
| **5.1.5.2.1** | Device periodically reports Geo-location to Authority | WebSocket / REST periodic GPS ping endpoint | **P** |
| **5.1.5.2.2** | Device records exact Geo-location at time of sale | Latitude & longitude captured in transaction header | **P** |
| **5.1.5.2.3** | Geo-location included in receipt registration message | Mapped into MoR registration payload | **P** |
| **5.1.5.2.4** | Geo-location recorded in audit log | Logged with transaction in `audit_events` | **P** |
| **5.1.5.3.1** | Device operates only within authorized geofenced work area | PostGIS `ST_Contains` checks device against registered polygon | **P** |
| **5.1.5.3.2** | Prevents transaction processing outside geofenced area | Blocks checkout with HTTP 403 `OUT_OF_GEOFENCE_VIOLATION` | **P** |
| **5.1.5.3.3** | Displays clear error message when outside geofenced area | Localized Amharic and English geofence breach alert | **P** |
| **5.1.5.3.6** | Logs all geofencing violations for audit | Recorded in `security_audit_events` | **P** |
