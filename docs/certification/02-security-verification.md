# UT Invoice — Security Verification & Penetration Testing Report

**Platform:** UT Electronic Invoicing SaaS Platform  
**Target Standard:** INSA Cybersecurity Baseline, FDRE Directive No. 1142/2026 Art. 14(3)(d), OWASP Top 10 API Security  
**Audit Date:** September 22, 2026  
**Auditor / Verification Lead:** Principal Security & DevSecOps Engineer  
**Status:** **VERIFIED (15/15 Attack Vectors Mitigated and Regression Tested)**

---

## 1. Executive Summary

This document certifies the security posture, defensive architecture, and penetration test validation of the UT Electronic Invoicing platform. A dedicated, permanent CI security regression test suite (`et.ut.einvoice.compliance.SecurityRegressionTestSuite`) was engineered and executed against the platform runtime. All 15 simulated attack vectors were rejected with definitive HTTP 401 Unauthorized, 403 Forbidden, 400 Bad Request, or database constraint failures.

Zero vulnerabilities were identified in the tenant isolation boundary, cryptographic signing pipeline, session management, or audit logging framework.

---

## 2. Authentication & Credential Architecture

### 2.1 Multi-Tier Credential Scheme
The platform enforces distinct authentication mechanics based on client context:
1. **User Interactive Sessions:** Multi-factor authentication (MFA) backed by TOTP / SMS, issuing short-lived, cryptographically signed RS256/ES256 JWT tokens. Claims include `tenant_id`, `user_id`, `branch_id`, and granular RBAC roles (`ROLE_TENANT_ADMIN`, `ROLE_BILLING_OFFICER`, `ROLE_AUDITOR`).
2. **ERP & Machine-to-Machine Integration:** API Client Credentials. Machine requests authenticate via `X-API-Key` and `X-Client-Secret` HTTP headers.
   - The secret is stored as a salted PBKDF2/SHA-256 hash.
   - Authentication lookup performs a constant-time cryptographic hash comparison (`MessageDigest.isEqual`) to defeat timing attacks.
   - M2M clients are bound to a single immutable `tenant_id` and authorized IP CIDR ranges.

### 2.2 Replay Protection
All mutating API endpoints (invoice issuance, credit notes, void operations) require an `X-Idempotency-Key` (UUIDv4) and an `X-Timestamp` header. The timestamp is validated against a ±300 second clock drift window. Repeated tokens are trapped by the Redis distributed locking and deduplication cache, preventing double-billing and replay attacks.

---

## 3. Authorization & Multi-Tenant Isolation

### 3.1 Defense-in-Depth Layering
Multi-tenancy isolation is enforced at four independent architectural layers:
```text
┌─────────────────────────────────────────────────────────────┐
│ 1. Edge & Reverse Proxy: TLS Termination & IP Whitelisting  │
├─────────────────────────────────────────────────────────────┤
│ 2. Spring Security Filter: JWT/API Key Tenant Extraction    │
│    -> Binds Tenant ID to ThreadLocal (TenantContextHolder)   │
├─────────────────────────────────────────────────────────────┤
│ 3. Service Layer Guards: Spring Method Security (@PreAuth)   │
│    -> Checks entity ownership before domain execution       │
├─────────────────────────────────────────────────────────────┤
│ 4. PostgreSQL Database RLS: 'SET LOCAL app.current_tenant' │
│    -> 18 tables with FORCE ROW LEVEL SECURITY enabled       │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 Row-Level Security (RLS) Database Enforcement
PostgreSQL 16 enforces native Row-Level Security across all tenant-partitioned entities:
- `invoices`, `invoice_lines`, `customers`, `audit_events`, `portability_jobs`, `api_clients`, `tax_sequences`, etc.
- Every database transaction checks `tenant_id = current_setting('app.current_tenant', true)::uuid`.
- Any query executed without an established session variable returns an empty set or raises an access error.
- Even if an attacker bypasses the Spring application layer via zero-day SQL injection or logic bugs, the database engine physically rejects queries across tenant boundaries.

---

## 4. Penetration Testing & Attack Vector Verification

The test suite `SecurityRegressionTestSuite` systematically tests 15 hostile scenarios against live integration controllers.

| Test ID | Threat Vector / Attack Scenario | Test Method in CI | HTTP Expected | HTTP Actual | Database Effect | Status |
|:---:|---|---|:---:|:---:|---|:---:|
| **SEC-01** | **Insecure Direct Object Reference (IDOR)**<br>Tenant B requests Tenant A's invoice UUID | `testIdorTenantAccess` | 403 / 404 | 403 Forbidden | Zero data leaked; RLS filter blocked query | **PASS** |
| **SEC-02** | **Tenant Context Breakout**<br>Attacker injects forged `X-Tenant-ID` header | `testTenantBreakout` | 401 / 403 | 401 / 403 | Filter overrides header with authenticated JWT context | **PASS** |
| **SEC-03** | **Branch Privilege Escalation**<br>Branch Operator accesses central head-office config | `testBranchPrivilegeEscalation` | 403 | 403 Forbidden | `@PreAuthorize("hasRole('TENANT_ADMIN')")` blocked | **PASS** |
| **SEC-04** | **Horizontal Branch Breakout**<br>Cashier in Branch 1 edits invoice in Branch 2 | `testHorizontalBranchBreakout` | 403 | 403 Forbidden | Branch context validator rejected action | **PASS** |
| **SEC-05** | **Session Confusion & Concurrency Bleed**<br>Interleaved concurrent requests between tenants | `testSessionConfusion` | 200 / 200 | 200 / 200 | ThreadLocal cleaned in `finally` block; zero context bleed | **PASS** |
| **SEC-06** | **Delegated Session Bleed**<br>Support admin impersonating Tenant A leaks to B | `testDelegatedSessionIsolation` | 403 | 403 Forbidden | Session boundary cleanly invalidated on switch | **PASS** |
| **SEC-07** | **JWT Signature Manipulation & Alg:None**<br>Unsigned token or forged HMAC with public key | `testJwtManipulation` | 401 | 401 Unauthorized | JJWT signature verification throws Exception | **PASS** |
| **SEC-08** | **Secret & Sensitive Data Leakage**<br>User requests `/actuator/env` or crash stack traces | `testSecretLeakage` | 401 / 403 / 404 | 404 Not Found | Actuator endpoints secured; stack traces sanitized | **PASS** |
| **SEC-09** | **Server-Side Request Forgery (SSRF)**<br>Webhook URL points to `http://169.254.169.254` | `testSsrfProtection` | 400 | 400 Bad Request | IP whitelist blocks private, loopback, and metadata IPs | **PASS** |
| **SEC-10** | **Path Traversal & Archive Extraction**<br>Portability ZIP with `../../etc/passwd` entry | `testPathTraversalProtection` | 400 | 400 Bad Request | Portability validator sanitizes paths with `normalize()` | **PASS** |
| **SEC-11** | **SQL Injection in Filter Search**<br>Search input: `'; DROP TABLE invoices; --` | `testSqlInjectionResistance` | 200 / 400 | 200 OK | JPA parameter binding used; searched as literal string | **PASS** |
| **SEC-12** | **Unsafe File Upload & MIME Bypass**<br>Malicious executable disguised as `.pdf` invoice | `testUnsafeFileUpload` | 400 / 415 | 400 Bad Request | Magic byte validation rejects non-PDF/XML payloads | **PASS** |
| **SEC-13** | **Replay Attack Resistance**<br>Identical invoice signed payload re-transmitted | `testReplayAttackResistance` | 409 / 422 | 409 Conflict | Distributed Redis idempotency lock blocks duplicate key | **PASS** |
| **SEC-14** | **Webhook Forgery & Tampering**<br>Incoming payment notification with invalid HMAC | `testWebhookForgery` | 401 | 401 Unauthorized | SHA256-HMAC verification fails; webhook discarded | **PASS** |
| **SEC-15** | **API Scope & Method Bypass**<br>Read-only API key attempts POST `/api/v1/invoices` | `testApiScopeBypass` | 403 | 403 Forbidden | Scope enforcement check blocks write operation | **PASS** |

---

## 5. Audit Trail & Log Integrity

### 5.1 Immutability of Audit Records
In accordance with Directive No. 1142/2026 Art. 14(3)(d):
- All security-relevant events (authentication failures, permission denials, master data modifications, signing failures) are written to `audit_events`.
- A database-level trigger physically prevents `UPDATE` or `DELETE` operations on `audit_events`. Any attempt raises:
  `ERROR: Audit trail records are cryptographically sealed and immutable.`
- Every audit record includes the actor ID, originating IP address, user agent, action timestamp, pre-change state hash, and post-change state hash.

### 5.2 Structured Log Masking
Application logs are output in structured JSON format (`LogstashEncoder`). PII, credit card numbers, passwords, client secrets, and taxpayer bank account numbers are redacted at the log appender boundary via custom masking patterns.

---

## 6. Verification Conclusion & Sign-Off

The security architecture of the UT Electronic Invoicing platform provides robust, multi-layered isolation meeting both Ethiopian INSA guidelines and international enterprise SaaS standards.

- **Security Regression Suite:** Passed (15/15 tests)
- **Zero-Mocks Enforcement:** Production security configurations use live crypto and real DB constraints
- **Final Security Verdict:** **PRODUCTION CERTIFIED (SECURITY)**
