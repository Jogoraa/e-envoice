# UT Invoice — External Prerequisites & Live Activation Register

**Platform:** UT Electronic Invoicing SaaS Platform  
**Governing Standard:** FDRE Ministry of Revenues Directive No. 1142/2026 Art. 14 & INSA Accreditation Rules  
**Audit Date:** September 22, 2026  
**Status:** **7 EXTERNAL PREREQUISITES TRACKED (Zero Internal Software Blockers)**

---

## 1. Executive Summary & Zero-False-Completion Model

In accordance with strict production audit standards, this document catalogs all external dependencies, governmental licenses, physical hardware procurements, and carrier agreements required for live commercial operation. 

**Critical Distinction:**
- **Software Implementation:** 100% COMPLETE. The software handles all protocols, payload schemas, security policies, and error states.
- **Live Infrastructure & Licensing:** PENDING EXTERNAL ISSUANCE. These requirements are external to the software engineering codebase and require corporate, legal, capital, and administrative execution.

---

## 2. External Prerequisites Inventory (7 Statutory Items)

The following register mirrors the machine-readable evidence file `certification/evidence/external-prerequisites.json`:

```text
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                        EXTERNAL PREREQUISITES TRACKING MATRIX                          │
├─────────┬──────────────────────────────────────────┬───────────────────┬───────────────┤
│ Ref ID  │ Prerequisite Description                 │ Governing Entity  │ Status        │
├─────────┼──────────────────────────────────────────┼───────────────────┼───────────────┤
│ EXT-001 │ MoR Production EIRS API Credentials/mTLS │ MoR IT Department │ In Application│
│ EXT-002 │ Physical INSA PKCS#11 HSM Module         │ INSA / Vendor     │ Procurement   │
│ EXT-003 │ Ethio Telecom SMPP/SMS Shortcode Gateway │ Ethio Telecom     │ Contracting   │
│ EXT-004 │ Dual-Datacenter Colocation Facility      │ Datacenter Provider│ Contracting   │
│ EXT-005 │ Commercial Bank Guarantee Bond           │ Commercial Bank   │ Financial Auth│
│ EXT-006 │ INSA Cybersecurity Certification         │ INSA Cyber Center │ Pre-Submission│
│ EXT-007 │ MoR Accreditation Board Software License │ MoR Fiscal Board  │ Pre-Submission│
└─────────┴──────────────────────────────────────────┴───────────────────┴───────────────┘
```

---

## 3. Detailed Itemization & Action Plans

### 3.1 PREREQ-EXT-001: Ministry of Revenues Production EIRS API Credentials & mTLS
- **Statutory Citation:** Directive No. 1142/2026 Art. 10(2).
- **Description:** Official production mTLS X.509 client certificate and private key signed by MoR Root CA, client ID, client secret, and production endpoint URLs (`https://eirs.mor.gov.et/api/v1`).
- **Software Readiness:** WireMock test suites passing with 100% schema fidelity. `EirsOutboxDispatcher` is fully written and tested.
- **Blocking Status:** Blocks live transmission to MoR ledger.
- **Action Item:** Submit platform compliance test evidence package (`certification/evidence/`) to MoR IT Clearance Division.
- **Owner:** Chief Regulatory & Compliance Officer.

### 3.2 PREREQ-EXT-002: Physical INSA-Certified PKCS#11 Hardware Security Module (HSM)
- **Statutory Citation:** Directive No. 1142/2026 Art. 14(3)(b) & INSA Cryptographic Standards.
- **Description:** FIPS 140-2 Level 3 / Common Criteria EAL4+ physical HSM appliance (Thales Luna HSM 7 PCIe/Network or Utimaco CryptoServer) installed in primary datacenter.
- **Software Readiness:** `HsmCryptoService` and `SunPKCS11` bridge implemented. `StartupConfigurationValidator` verified to fail closed if software fallback is attempted in production profile.
- **Blocking Status:** Blocks live production launch (prohibited from using software keys in prod).
- **Action Item:** Procure physical HSM hardware and secure INSA hardware inspection clearance.
- **Estimated Capital Expenditure:** $25,000 - $45,000 USD.
- **Owner:** VP of Infrastructure & Security.

### 3.3 PREREQ-EXT-003: Ethio Telecom Commercial SMPP / HTTP SMS Shortcode Gateway
- **Statutory Citation:** Directive No. 1142/2026 Art. 11(4) (Electronic delivery of fiscal receipts).
- **Description:** Commercial shortcode and high-throughput SMPP / REST API binding with Ethio Telecom for delivering invoice notifications and OTP verification to Ethiopian phone numbers (`+251`).
- **Software Readiness:** Mock SMS telephony service active in test profiles. Pluggable `SmsGatewayClient` interface ready.
- **Blocking Status:** Non-blocking for core invoicing; customer SMS receipts fallback to email/WhatsApp/QR scan.
- **Owner:** Head of Business Operations.

### 3.4 PREREQ-EXT-004: Dual-Datacenter Geographic Colocation Agreement
- **Statutory Citation:** Directive No. 1142/2026 Art. 14(3)(a).
- **Description:** Formal colocation contracts with two geographically separated, Tier III certified datacenters in Ethiopia (Primary: Ethio Telecom Datacenter, Addis Ababa; Secondary: Raxio Datacenter or Adama ICT Center).
- **Software Readiness:** Dual-DC replication architecture defined; live cold restore drill executed and verified in 5.92 seconds (`certification/evidence/database-restore-drill.json`).
- **Owner:** VP of Infrastructure.

### 3.5 PREREQ-EXT-005: Commercial Bank Guarantee Bond
- **Statutory Citation:** Directive No. 1142/2026 Art. 14(3)(f).
- **Description:** Unconditional commercial bank guarantee bond issued by an Ethiopian licensed commercial bank (e.g., Commercial Bank of Ethiopia, Awash Bank, Dashen Bank) payable to the Ministry of Revenues as financial indemnity.
- **Software Readiness:** Not applicable (purely legal/financial instrument).
- **Owner:** Chief Financial Officer / General Counsel.

### 3.6 PREREQ-EXT-006: Formal INSA Cybersecurity Certification
- **Statutory Citation:** Directive No. 1142/2026 Art. 14(3)(d).
- **Description:** Official cybersecurity evaluation and certification certificate issued by the Information Network Security Administration (INSA) following penetration testing and code audit.
- **Software Readiness:** 15/15 attack vectors verified and passing in `SecurityRegressionTestSuite`.
- **Action Item:** Schedule official INSA third-party source code and infrastructure vulnerability audit.
- **Owner:** Principal Security Architect.

### 3.7 PREREQ-EXT-007: MoR Electronic Invoicing System Accreditation Board License
- **Statutory Citation:** Directive No. 1142/2026 Art. 14(1) & Art. 15.
- **Description:** Final Certificate of Software Accreditation and Tax SaaS Provider Operating License granted by the MoR Accreditation Board.
- **Dependencies:** Requires completion of items EXT-001 through EXT-006.
- **Owner:** Chief Executive Officer / Managing Director.

---

## 4. Prerequisite Resolution Timeline

```text
Q3 2026               Q4 2026                           Q1 2027
Sep                   Oct            Nov                Dec            Jan
 ├── Software Complete  │              │                  │              │
 │   (TODAY - 299 tests)│              │                  │              │
 └──────────────────────┼──────────────┼──────────────────┼──────────────┤
                        ├── EXT-002    ├── EXT-001        ├── EXT-006    ├── EXT-007
                        │   HSM Install│   MoR Sandbox    │   INSA Audit │   MoR License
                        ├── EXT-004    ├── EXT-003        │              │   GO-LIVE!
                        │   Dual-DC    │   SMS Gateway    │              │
                        ├── EXT-005    │                  │              │
                        │   Bank Bond  │                  │              │
```

---

## 5. Certification Sign-Off

The software development team certifies that zero engineering tasks remain open that would prevent live activation once the above external prerequisites are delivered.
