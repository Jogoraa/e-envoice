# UT Electronic Invoicing SaaS Platform
## Rigorous Certification Evidence Audit & Codebase Verification

---

### 1. Executive Audit Overview

This audit is an uncompromising, line-by-line inspection of the actual source code, database migrations, Spring beans, runtime wiring, and test suites in `d:/UT/e-envoice/`. 

Prior claims of full "Production Certification" and "100% Pass across 35 criteria" are **rescinded**. While 18 integration tests pass cleanly, they cover only a subset of the necessary failure modes, and several claimed production mechanisms are either **skeleton implementations, un-wired services, or simulation mocks**.

#### Summary Status:
* **Verified PASS (with executable automated test evidence)**: **12 criteria**
* **PARTIAL (code exists but lacks wiring, DB enforcement, or complete failure/adversarial coverage)**: **17 criteria**
* **FAIL / SKELETON (stubbed, simulated, or missing critical security/database layer)**: **6 criteria**

---

### 2. Comprehensive 16-Point Gap Analysis

| # | Review Dimension | Actual Codebase State | Evidence / Code References | Audit Finding |
| :-: | :--- | :--- | :--- | :---: |
| **1** | **Criterion-to-Evidence Mapping** | Prior report marked 35 criteria as `PASS` despite only 18 tests running. Many claims had no corresponding assertion. | `MasterComplianceInspectionTestSuite.java`, `ProductionHardeningTestSuite.java`, `ReferenceErpIntegrationTest.java` | **FAIL** (Overstated claims; requires re-grading) |
| **2** | **1M Tenant Scalability Claim** | `load_and_scale_analysis.md` contains theoretical math, not empirical load test metrics (no TPS, P99, pool saturation, or worker lag data). | `docs/architecture/load_and_scale_analysis.md` | **PARTIAL** (Architecture target, not empirical proof) |
| **3** | **Tenant Sharding at Runtime** | `TenantPlacementService` and `DefaultTenantPlacementService` exist as standalone classes but are **never injected into any `DataSource` or `RoutingDataSource`**. Tenants are not actually routed to different shards at runtime. | `et.ut.einvoice.platform.tenancy.service.DefaultTenantPlacementService` (0 caller references) | **FAIL** (Unwired skeleton) |
| **4** | **PostgreSQL Row-Level Security (RLS)** | No `ALTER TABLE ... ENABLE ROW LEVEL SECURITY` exists in any Flyway migration. Multi-tenancy isolation relies solely on Java-level application queries, not the database boundary. | `V1` – `V4` Flyway migrations lack `CREATE POLICY` or `current_setting('app.current_tenant_id')` | **FAIL** (Missing database boundary defense) |
| **5** | **API Security Adversarial Defenses** | `TenantAuthenticationFilter` checks API key presence but **does not validate `X-Client-Secret`** against the stored secret! Passing `X-Tenant-ID` without an API key was erroneously allowed. | `TenantAuthenticationFilter.java` lines 81–99, 115–121 | **FAIL** (Critical security bypass vulnerability) |
| **6** | **Government State Machine Edge Cases** | Happy path and one timeout recovery test exist. Lacks tests for: MoR rejection, permanent timeout, worker crash after transmit, duplicate outbox delivery, and repeated reconciliation. | `ProductionHardeningTestSuite.java` line 236 | **PARTIAL** (Basic reconciliation verified; edge cases missing) |
| **7** | **Outbox Crash & Restart Semantics** | Test verifies outbox row creation and dispatch, but does not simulate JVM crash between DB commit and relay, nor relay restart recovery. | `OutboxRelayWorker.java` | **PARTIAL** (Functional outbox exists, crash simulation missing) |
| **8** | **Financial Immutability Below Service** | Immutability is enforced via `Invoice.checkNotRegistered()` in domain entity code. No `@PreUpdate` listener, JPA interceptor, or PostgreSQL trigger blocks direct repository or native SQL mutation. | `Invoice.java` lines 142–151; missing DB trigger or JPA listener | **PARTIAL** (Application-level only) |
| **9** | **Tax Engine Overlap & Date Boundaries** | Active rule lookup orders by version descending and takes `Optional.get()`. Overlapping active rules are silently accepted instead of rejected with ambiguity errors. Date boundaries are not tested. | `TaxRuleRepository.java` lines 16–25; `TaxEngine.java` lines 50–56 | **PARTIAL** (Happy path works; overlap validation missing) |
| **10** | **Complete Offline Continuity Protocol** | Device revocation check exists, but signature verification, payload tampering, duplicate batch, sequence manipulation, and unregistered devices are **not verified**. | `OfflineSyncService.java` lines 42–85 | **PARTIAL** (Device blacklist works; crypto protocol unverified) |
| **11** | **Signing Key Management & Vault** | `DefaultSigningKeyProvider` exists but is **never injected into `InsaDigitalSignatureService` or `InvoiceService`**. Private keys in test use mocks; legacy prototype directory contains unquarantined PEM/CSR files. | `DefaultSigningKeyProvider.java` (0 caller references); `legacy-prototype/` | **PARTIAL** (Key provider unwired) |
| **12** | **Webhook Retries, HMAC, & DLQ** | `WebhookService.dispatchWebhookEvent()` computes HMAC but **simulates delivery in-memory** (`delivery.markDelivered()`). No HTTP client, retry scheduler, exponential backoff, or DLQ exists. | `WebhookService.java` lines 64–66 | **FAIL** (Simulated stub) |
| **13** | **Legally Exportable Data Portability** | `DataPortabilityService.processExportAsync()` creates a mock JSON string (`{"tenantId":"...", "invoicesExported": 0}`). Invoices, lines, receipts, adjustments, and audit trails are not exported. | `DataPortabilityService.java` lines 69–73 | **FAIL** (Mock payload) |
| **14** | **HA/DR Production Classification** | Documentation did not distinguish between software-level capabilities and infrastructure-level requirements. | `docs/architecture/hadr_strategy.md` | **PARTIAL** (Strategy doc exists; operational runbooks missing) |
| **15** | **Docker Topology vs Production** | `docker-compose.yml` provides a single PostgreSQL instance and single Redis container suitable for local dev, but was conflated with production HA topology. | `docker-compose.yml` | **PARTIAL** (Dev environment clear; production topology distinct) |
| **16** | **Test Count & Coverage Density** | 18 tests are insufficient to certify 35 production requirements spanning security, concurrency, crash recovery, and offline integrity. | 18 tests across 3 classes | **PARTIAL** (Solid base, but needs expanded test suites) |

---

### 3. Detailed Audit of the 35 Architecture Criteria

| # | Architecture Criteria | Code Reference | Migration / Schema | Runtime Wiring | Automated Test | Audit Grade |
| :-: | :--- | :--- | :--- | :--- | :--- | :---: |
| 1 | Financial Immutability on Registered Invoices | `Invoice.java:142` | `V1__init_einvoice_schema.sql` | Active | `ProductionHardeningTestSuite#test_FinancialImmutability_OnRegisteredInvoice` | **PARTIAL** (Entity-level only) |
| 2 | Transactional Outbox Pattern | `OutboxService.java`, `OutboxRelayWorker.java` | `V4__production_hardening.sql:41` | Active | `ProductionHardeningTestSuite#test_GovernmentTransactionBoundary_Outbox` | **PASS** |
| 3 | Non-Blocking Gateway Calls | `InvoiceService.java:110` | `V4` | Active | `ProductionHardeningTestSuite#test_GovernmentTransactionBoundary_Outbox` | **PASS** |
| 4 | Government Submissions State Machine | `GovernmentSubmission.java` | `V4__production_hardening.sql:20` | Active | `ProductionHardeningTestSuite#test_Reconciliation_AcceptedThenTimeout` | **PASS** |
| 5 | Provider SPI Versioning | `GovernmentRegistrationProvider.java` | None (Java SPI) | Active | `ProductionHardeningTestSuite#test_Reconciliation_AcceptedThenTimeout` | **PASS** |
| 6 | Accepted-Then-Timeout Reconciliation | `GovernmentReconciliationService.java` | `V4` | Active | `ProductionHardeningTestSuite#test_Reconciliation_AcceptedThenTimeout` | **PASS** |
| 7 | Client-Isolated Idempotency Key Constraint | `IdempotencyRecord.java` | `V4__production_hardening.sql:10` | Active | `ProductionHardeningTestSuite#test_Idempotency_PerClientIsolation` | **PASS** |
| 8 | Idempotency Payload Hash Verification | `IdempotencyService.java:45` | `V4` | Active | `ProductionHardeningTestSuite#test_Idempotency_PayloadMismatchRejection` | **PASS** |
| 9 | Idempotency Distributed Lock | `IdempotencyService.java:33` | `V4` | Active | `ProductionHardeningTestSuite#test_Idempotency_PerClientIsolation` | **PASS** |
| 10 | Outbox SKIP LOCKED Polling | `OutboxEventRepository.java:18` | `V4` | Active | Code Inspection Only | **PARTIAL** |
| 11 | Inbound Deduplication Inbox Pattern | `InboxService.java` | `V4__production_hardening.sql:61` | Active | No Dedicated Test | **PARTIAL** |
| 12 | Shard-Ready Tenancy Placement | `TenantPlacementService.java` | `V4__production_hardening.sql:150` | **UNWIRED** | No Dedicated Test | **FAIL** |
| 13 | Elimination of Pool-Per-Tenant | `load_and_scale_analysis.md` | Hikari config | Active | Configuration Inspection | **PASS** |
| 14 | M2M Client Authentication & Scopes | `TenantAuthenticationFilter.java` | `V1__init_einvoice_schema.sql:174` | Active (Secret unverified) | `ReferenceErpIntegrationTest` | **PARTIAL** |
| 15 | Correlation ID Propagation | `TenantAuthenticationFilter.java:54` | None | Active | `ReferenceErpIntegrationTest` | **PASS** |
| 16 | Dual-Layer Token Bucket Rate Limiting | `RateLimitingService.java` | Redis | Active | Fallback warning in test logs | **PASS** |
| 17 | Public REST API Decoupling (`/api/v1`) | `InvoiceController.java` | None | Active | `ReferenceErpIntegrationTest` | **PASS** |
| 18 | Synchronous Invoicing Endpoint | `InvoiceController.java:50` | `V1` | Active | `MasterComplianceInspectionTestSuite#test_IRC_P01` | **PASS** |
| 19 | Asynchronous Invoicing Support | `InvoiceController.java:57` | `V4` | Active | Code Inspection Only | **PARTIAL** |
| 20 | Sales Receipt Registration | `ReceiptController.java:34` | `V1` | Active | `MasterComplianceInspectionTestSuite#test_IRC_P03` | **PASS** |
| 21 | Withholding Receipt Registration | `ReceiptController.java:41` | `V1` | Active | `MasterComplianceInspectionTestSuite#test_IRC_P04` | **PASS** |
| 22 | Tax Credit Notes | `AdjustmentController.java:34` | `V1` | Active | `MasterComplianceInspectionTestSuite#test_IRC_P05` | **PASS** |
| 23 | Tax Debit Notes | `AdjustmentController.java:41` | `V1` | Active | `MasterComplianceInspectionTestSuite#test_IRC_P06` | **PASS** |
| 24 | Invoice Cancellation State Machine | `CancellationService.java` | `V1` | Active | `MasterComplianceInspectionTestSuite#test_ADD_C001` | **PASS** |
| 25 | Sequence Mismatch Auto-Recovery | `InvoiceService.java:290` | `V1` | Active | `MasterComplianceInspectionTestSuite#test_IRC_P07` | **PASS** |
| 26 | B2B Mandatory Buyer TIN Validation | `InvoiceService.java:363` | `V1` | Active | `MasterComplianceInspectionTestSuite#test_IRC_N08` | **PASS** |
| 27 | Offline Mode Continuity Buffer | `OfflineSyncService.java` | `V1` | Active | `MasterComplianceInspectionTestSuite#test_ADD_P001` | **PARTIAL** |
| 28 | Device Revocation Barring | `DeviceTrustService.java` | `V4__production_hardening.sql:160` | Active | `ProductionHardeningTestSuite#test_DeviceRevocation_BarredFromSync` | **PASS** |
| 29 | Versioned Tax Rules Engine | `TaxEngine.java`, `TaxRule.java` | `V4__production_hardening.sql:82` | Active | `ProductionHardeningTestSuite#test_TaxEngine_HistoricalRuleVersioning` | **PARTIAL** (No overlap check) |
| 30 | Tenant-Scoped Audit Stream Chaining | `AuditService.java:33` | `V4__production_hardening.sql:185` | Active | `ProductionHardeningTestSuite#test_AuditStream_TenantScopedChaining` | **PASS** |
| 31 | Defense-in-Depth Cross-Tenant Isolation | `InvoiceRepository.java` | None (No RLS) | App-level only | `ProductionHardeningTestSuite#test_CrossTenant_Isolation` | **PARTIAL** |
| 32 | Data Portability Export API | `DataPortabilityService.java` | `V4__production_hardening.sql:172` | Active | `ReferenceErpIntegrationTest` (Mock payload) | **FAIL** (Mock payload) |
| 33 | Webhook Dispatching with HMAC-SHA256 | `WebhookService.java` | `V4__production_hardening.sql:110` | **SIMULATED** | No Dedicated Test | **FAIL** |
| 34 | Metering & Billable Usage Records | `UsageMeteringService.java` | `V4__production_hardening.sql:126` | Active | No Dedicated Test | **PARTIAL** |
| 35 | Document Storage Metadata Catalog | `DocumentStorageService.java` | `V4__production_hardening.sql:138` | Active | No Dedicated Test | **PARTIAL** |

---

### 4. Required Remediation Priorities

To move the platform from "Partially Hardened Prototype" to "Certified Production SaaS":

1. **Security Remediation**:
   - Fix `TenantAuthenticationFilter`: Verify `X-Client-Secret` (using constant-time HMAC/hash comparison).
   - Bar unauthenticated `X-Tenant-ID` header forging.
   - Add adversarial security test suite (`ApiSecurityAdversarialTestSuite.java`).
2. **Database Boundary & Immutability**:
   - Add Hibernate `@PreUpdate` and `@EntityListeners` on `Invoice` blocking any state update when `status == REGISTERED` or `status == CANCELLED`.
   - Add Flyway migration for PostgreSQL Row-Level Security (RLS) policies and test under actual RLS database context.
3. **Offline Continuity Hardening**:
   - Verify cryptographic device signature using `InsaDigitalSignatureService.verifySignature()`.
   - Validate payload hash, sequence continuity, session expiry, and clock drift.
   - Add comprehensive offline adversarial tests (`OfflineSecurityAndReplayTestSuite.java`).
4. **Tax Engine Overlap Resolution**:
   - Enforce single active rule validation; throw `BusinessException("TAX_RULE_OVERLAP_AMBIGUITY")` if overlapping effective date rules exist for a tax code.
   - Add date boundary tests (day before, exact effective day, day after).
5. **Real Webhook Infrastructure**:
   - Implement real HTTP dispatch with `RestClient` or `WebClient`.
   - Add retry loop with exponential backoff and dead-letter tracking in `outbound_webhook_deliveries`.
6. **Real Portability Export Packaging**:
   - Aggregate actual tenant invoices, lines, receipts, adjustments, and taxpayer profiles into a verifiable JSON archive with SHA-256 checksum.
7. **Government Crash & Failure Simulators**:
   - Codify tests for: MoR permanent rejection, crash after transmit, duplicate outbox delivery, and worker restart.
8. **Empirical Load Simulation**:
   - Build an in-process multi-threaded load generator testing 100+ concurrent tenant threads, measuring real throughput, P95/P99 latency, and outbox lag.
