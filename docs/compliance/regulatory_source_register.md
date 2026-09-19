# Regulatory Source Register & Authoritative Baseline

**Document Version:** 1.0.0  
**Baseline Date:** September 18, 2026 (2018 EC)  
**Status:** AUTHORITATIVE REGULATORY BASELINE  
**Governing Authority:** Federal Democratic Republic of Ethiopia Ministry of Revenues (MoR) / Information Network Security Administration (INSA)

---

## 1. Authoritative Regulatory Documents Present in Repository

| Document Title | File Path | Language | Statutory Date / Version | SHA-256 Integrity Hash | Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Electronic Invoicing System Administration Directive No. 1142/2018 EC (2026 GC)**<br>*(የኤሌክትሮኒክ ደረሰኝ ሥርዓት አስተዳደር መመሪያ ቁጥር 1142/2018)* | `docs/directive/1142_የኤሌክትሮኒክ_ደረሰኝ_ሥርዓት_አስተዳደር_መመሪያ_ቁጥር_1142_2018.pdf` | Bilingual (Amharic / English) | ሐምሌ 2018 ዓ.ም (July 2026 GC)<br>57 Pages, 31 Articles, 2 Annexes | `1C595F84AB975FEF6D3B4D63F9E76E78F638AAE5175AE078CA6CFF690D3D4AED` | **PRIMARY STATUTORY TRUTH** |
| **Extracted Authoritative Directive Text** | `docs/directive/directive_1142_extracted.txt` | Bilingual (Amharic / English) | Complete 57-page raw extraction | `7D415FB04EE05771B38E7BB5851F8FBD8C44107B2B53B0ADC23F3EB8FCB6E3E9` | **PRIMARY AUDIT TEXT** |

---

## 2. External Statutory & Regulatory References

| External Statute / Regulation | Reference in Directive 1142 | Statutory Scope & Impact | Availability in Repository | Implementation Handling |
| :--- | :--- | :--- | :--- | :--- |
| **Value Added Tax Regulation No. 570/2024** | Article 4(1)(a), Article 20(3)(a) | Article 20 specifies mandatory invoice particulars (TIN, legal name, trade name, date/time, item code, description, quantity, unit price, pre-tax value, VAT/TOT rates & amounts, total payable). | Referenced in Directive text & Annex 1 sample layout. | Fully implemented in `Invoice.java`, `CreateInvoiceRequest.java`, and `InvoiceReportGenerator.java`. |
| **Federal Tax Administration Proclamation No. 983/2016** | Article 4(2)(d), Article 27, Article 28 | Article 17 prescribes books and records retention periods; Part Fourteen details administrative, civil, and criminal penalties for system tampering and tax evasion. | Referenced in Directive text. | Implemented via append-only audit logs, immutable triggers, and configurable retention periods. |
| **Value Added Tax Proclamation No. 285/2002 (as amended)** | Article 25(1), Article 26(1) | Legal basis for Tax Debit Notes, Tax Credit Notes, and sales price adjustments. | Referenced in Directive text. | Implemented in `AdjustmentService.java` and `Adjustment.java`. |

---

## 3. External Technical Specifications & Pending Standards

The Directive explicitly mandates compliance with technical specifications published or certified by government agencies. Where the authoritative specification is not yet formally issued or checked into this repository, it is explicitly classified as **PENDING EXTERNAL SPECIFICATION**:

| Specification Name | Statutory Mandate | Prescribed Scope | Present in Repo? | Engineering Baseline vs External Dependency |
| :--- | :--- | :--- | :---: | :--- |
| **INSA Cryptographic & Software Security Standard** | Directive Art. 4(6)(a–c) | Exact cryptographic profile: asymmetric key algorithm (RSA-2048 / ECDSA), hash function (SHA-256 / SHA-384), signature padding (PKCS#1 v1.5 / PSS), X.509 certificate hierarchy, and Hardware Security Module (HSM) Level 3 requirements. | **NO** (Referenced only) | **ENGINEERING BASELINE:** Implemented standard RSA-2048 with SHA-256 and PKCS#1 v1.5 in `InsaDigitalSignatureService.java`.<br>**DEPENDENCY:** Formal INSA software security evaluation and certificate issuance. |
| **MoR EIRS Public API & Payload Specification v1.0** | Directive Art. 3, Art. 4(1)(b–c), Art. 19(5) | Exact JSON/REST schemas for `/api/v1/invoices/register`, IRN/RRN calculation algorithm, QR code payload specification, and HTTP header conventions. | **NO** (Sandbox mock only) | **ENGINEERING BASELINE:** Modeled standard EIRS envelope with HMAC/RSA verification in `MockGovernmentRegistrationProvider.java`.<br>**DEPENDENCY:** MoR Sandbox integration sign-off and production API credentials. |
| **MoR Offline Resiliency & Reconciliation Specification** | Directive Art. 4(4), Art. 19(3), Art. 23(4) | Batch payload format, replay ordering, offline device digital certificate verification, and gap handling for the 26 Annex 2 sectors. | **NO** (Directive text only) | **ENGINEERING BASELINE:** Implemented encrypted SQLite client queue and server-side `OfflineSyncService.java` with 72-hour enforcement.<br>**DEPENDENCY:** Authority offline certification testing. |
| **MoR Public Verification Portal Specification** | Directive Art. 4(1)(d), Art. 20(3)(g) | Public URL format embedded into QR codes, response DTO for consumer verification, and anti-enumeration protections. | **NO** (Directive text only) | **ENGINEERING BASELINE:** Implemented public rate-limited verification endpoint `GET /api/v1/public/verify/{irn}`.<br>**DEPENDENCY:** Official MoR verification portal redirection agreement. |

---

## 4. Single Authoritative Rule

1. **Hierarchy of Truth**: In the event of any discrepancy between architectural documentation, test suite names, code comments, and the authoritative PDF, the text of **Directive No. 1142/2018 EC (`docs/directive/directive_1142_extracted.txt`)** shall strictly prevail.
2. **No Speculative Certification**: No requirement depending on external authority action (INSA certificate, MoR Board license, Commercial Bank performance guarantee) shall ever be documented as "certified" by repository code alone.
