# UT Invoice — Comprehensive Test Evidence & Audit Catalog

**Platform:** UT Electronic Invoicing SaaS Platform  
**Audit Date:** September 22, 2026  
**Test Lead:** Principal QA & Automation Engineer  
**Status:** **279/279 AUTOMATED SOFTWARE TESTS PASSED + 6 OPERATIONAL DRILLS VERIFIED**

---

## 1. Executive Summary & Exact Count Reconciliation

This document provides a fully reconciled, unambiguous accounting of all empirical test suites, operational drills, execution logs, and evidence files generated during the platform production readiness audit.

### 1.1 Formal Reconciliation of Verification Counts
To eliminate any ambiguity regarding test numbers:
- **Backend Automated Tests (Maven):** **217** (inclusive of 15 security regression tests, 3 portability tests, and 1 startup validator test).
- **Frontend Automated Tests (Flutter):** **62** (widget, unit, and integration tests).
- **Total Automated Software Regression Tests:** **279** (279/279 passing, 100% pass rate).
- **Operational & Infrastructure Quality Drills:** **6** (1 live PostgreSQL 16 cold restore drill + 5 audited PostgreSQL EXPLAIN query execution plans).
- **Total Empirical Audit Checks:** **285** (279 software tests + 6 infrastructure verifications).

> [!NOTE]
> **Audit Note on Count Reconciliation:**
> An earlier draft cited "299 tests" due to double-counting the 15 security scenarios, 3 portability tests, 1 startup test, and 1 restore drill on top of the 217 backend tests that already incorporated them. The authoritative, exact count is **279 automated software tests** and **6 operational infrastructure checks**, yielding **285 total verified audit artifacts**.

---

## 2. Test Execution Evidence Catalog

| Verification Category | Suite Identifier | Checks Run | Passed | Failures | Duration | Committed Evidence File |
|---|---|:---:|:---:|:---:|:---:|---|
| **Backend Full Test Suite** | Spring Boot Maven Test Runner | 217 | 217 | 0 | 70.3s | `certification/evidence/backend-test-results.txt` |
| ↳ *Security Regression Suite* | `SecurityRegressionTestSuite` | *15* | *15* | *0* | *17.3s* | `src/test/java/.../SecurityRegressionTestSuite.java` |
| ↳ *Portability Verification* | `IndependentPortabilityValidatorTest` | *3* | *3* | *0* | *0.09s* | `src/test/java/.../IndependentPortabilityValidatorTest.java` |
| ↳ *Startup Pre-Flight Suite* | `StartupConfigurationValidatorTest` | *1* | *1* | *0* | *0.61s* | `src/test/java/.../StartupConfigurationValidatorTest.java` |
| **Frontend Test Suite** | Flutter Test Runner | 62 | 62 | 0 | 17.1s | `certification/evidence/frontend-test-results.txt` |
| **Static Code Quality** | Flutter Analyzer | Clean | 0 err | 0 warn | 5.6s | `certification/evidence/static-analysis.txt` |
| **Subtotal Software Tests** | **Automated Codebase Baseline** | **279** | **279** | **0** | **~93s** | **ALL TESTS COMMITTED & PASSING** |
| **Database Restore Drill** | PostgreSQL 16 Cold Restore Drill | 1 | 1 | 0 | 5.92s | `certification/evidence/database-restore-drill.json` |
| **Database Query Optimizer** | EXPLAIN Execution Plan Audit | 5 | 5 | 0 | 1.2s | `certification/evidence/database-explain-plans.json` |
| **TOTAL EMPIRICAL ASSETS** | **Complete Quality Verification** | **285** | **285** | **0** | **~100s** | **100% REPRODUCIBLE** |

---

## 3. Backend Test Suite Composition (217 Tests)

The backend Maven test suite encompasses 217 distinct test methods executing against containerized PostGIS and Redis instances:

1. **Automated Security Regression Suite (15 tests):**
   - Implemented in `SecurityRegressionTestSuite.java`. Tests resistance to 15 hostile attack vectors: IDOR, tenant context breakout, horizontal branch breakout, privilege escalation, session confusion, delegated session isolation, JWT manipulation, secret leakage, SSRF, path traversal, SQL injection resistance, unsafe file upload, replay resistance, webhook forgery, API scope bypass.
   - *Audit Distinction:* Demonstrates automated resistance against specified attack cases; distinct from an independent third-party penetration test.
2. **Tax Engine & Calculation (38 tests):**
   - Regulation 570 rate application (15%, 0%, Exempt), reverse charge, withholding (3%), multi-line rounding, fractional cent reconciliation, cash discounts vs. line discounts.
3. **Sequential Fiscal Numbering (24 tests):**
   - Multi-threaded concurrency stress testing, gapless allocation, advisory lock release, calendar boundary stability, device-specific sequence leasing.
4. **MoR EIRS Integration & Outbox (32 tests):**
   - WireMock simulated responses, JSON payload serialization, transactional outbox dispatching, exponential backoff retries, dead-letter routing.
5. **Multi-Tenant RLS & Data Portability (28 tests):**
   - RLS insertion rejection, cross-tenant query suppression, ZIP container creation, SHA-256 manifest verification (`PortabilityArchiveValidator`).
6. **Domain Services & Controllers (79 tests):**
   - Customer CRUD, product catalog, branch and device registration, payment recording, user authentication, PDF generation, QR code generation.
7. **Platform Startup & Infrastructure (1 test):**
   - `StartupConfigurationValidatorTest`: Decoupled subsystem health evaluation, fail-closed production verification.

---

## 4. Frontend Test Suite Composition (62 Tests)

The Flutter test suite validates UI rendering, state machines, offline persistence, and user workflows:

1. **Feature & Integration Tests (28 tests):**
   - `test/features/offline_sync_test.dart`: 72-hour offline buffer, Drift SQLite persistence, background reconciliation.
   - `test/features/workspace_and_desktop_entry_test.dart`: Multi-tenant workspace switching, desktop POS counter layout, responsive drawer navigation.
2. **Authentication & Session (12 tests):**
   - Login, MFA TOTP flow, tenant selection, session expiration handling, token refresh.
3. **Fiscal Document Lifecycle (22 tests):**
   - Draft creation, line item addition, tax breakdown calculation, fiscal issue, void operation, credit note generation, thermal receipt preview.

---

## 5. Operational Drills & Infrastructure Audits (6 Verifications)

1. **Database Cold Disaster Recovery Restore Drill (1 drill):**
   - Executed against clean database `ut_einvoice_db_drill`. Fully restored 42 tables, 18 RLS policies, Flyway migrations, and 12,042,000 bytes in **5.92 seconds** (achieved RTO 5.92s vs 1,800s SLA).
2. **Database Query Optimizer Audits (5 execution plans):**
   - Audited high-volume fiscal transaction and sequence query paths (`InvoiceLookupByIRN`, `NextFiscalSequenceAdvisoryLock`, `RecentInvoiceFeedByDate`, `CustomerSearchByTIN`, `AuditTrailVerificationQuery`), confirming indexed execution paths under tested parameters.

---

## 6. Reproduction Instructions

### 6.1 Backend Full Test Run
```powershell
$env:PATH = "C:\Users\davej\tools\apache-maven-3.9.9\bin;" + $env:PATH
cd d:\UT\e-envoice
mvn clean test -Dspring.profiles.active=test
```
**Expected Outcome:** `Tests run: 217, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS`

### 6.2 Frontend Test Run & Static Analysis
```powershell
cd d:\UT\e-envoice\frontend
flutter test
flutter analyze
```
**Expected Outcome:** `All tests passed! (62 tests)` followed by `No issues found! (0 errors, 0 warnings)`
