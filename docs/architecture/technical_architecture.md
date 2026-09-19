# Technical Architecture Specification
## UT Electronic Invoicing Platform — Spring Boot 3.x / Java 21 LTS
### National-Scale Multi-Tenant SaaS Architecture

---

## 1. Architectural Vision & Guiding Principles

The UT Electronic Invoicing Platform is an independent, national-scale **financial infrastructure platform** built to serve Ethiopian businesses across web, mobile, POS, and third-party enterprise resource planning (ERP) systems.

### Core Architectural Principles:
1. **API-First Product**: The platform is an autonomous product. UT's future ERP and third-party ERPs (SAP, Microsoft Dynamics, Odoo, custom accounting engines) consume the exact same public REST APIs (`/api/v1`).
2. **Modular Monolith First**: Built as a modular Spring Boot 3 application with strict domain isolation and decoupled event-driven communication. High-load components can be extracted into microservices without rewriting domain logic.
3. **Partitioned Multi-Tenancy**: Designed to scale horizontally toward **1 million registered tenants** without creating a separate database or connection pool per tenant. Tenant data is isolated logically, cryptographically, and physically via table partitioning and optional dedicated enterprise shards.
4. **Financial Immutability**: Registered financial transactions cannot be mutated. Adjustments and cancellations are discrete immutable events with complete cryptographic audit chains.
5. **Government Gateway Isolation**: Communication with the Ministry of Revenues Electronic Invoice Registration System (EIRS) is strictly isolated behind an adapter SPI, preventing external API changes from leaking into the core invoice domain.

---

## 2. System Context & Top-Level Architecture

```text
                        INTERNET / WAN
                              │
                              ▼
                      Cloudflare WAF / LB
                              │
                              ▼
                API Gateway / Reverse Proxy (TLS 1.3)
                              │
         ┌────────────────────┼─────────────────────┐
         │                    │                     │
         ▼                    ▼                     ▼
   Web Portal          Mobile / POS App      External ERP / POS
 (Next.js / SPA)     (Android / iOS / mPOS)  (UT Future ERP / SAP)
         │                    │                     │
         └────────────────────┼─────────────────────┘
                              ▼
                    Public REST API (/api/v1)
                              │
┌─────────────────────────────▼───────────────────────────────┐
│              SPRING BOOT 3 PLATFORM KERNEL                  │
│                                                             │
│  [Identity]          [Tenancy]             [Taxpayer]       │
│   Users & RBAC        Tenant Lifecycle      Profiles & GPS  │
│                                                             │
│  [Invoicing]         [Taxation]            [Receipts]       │
│   Lifecycle FSM       Versioned Rules       Cash/Withhold   │
│                                                             │
│  [Adjustments]       [Cancellation]        [Offline]        │
│   Debit/Credit Notes  48h SLA State FSM     72h Sync Batch  │
│                                                             │
│  [Government]        [Documents]           [Notifications]  │
│   EIRS Adapter SPI    ZXing QR & PDF/HTML   Email & SMS     │
│                                                             │
│  [Audit]             [Compliance]          [Portability]    │
│   Hash-Chained Log    INSA Signing & Keys   Async Exports   │
└─────────────────────────────┬───────────────────────────────┘
                              │
          ┌───────────────────┼───────────────────┐
          ▼                   ▼                   ▼
    PostgreSQL 16           Redis 7            S3 Storage
  Partitioned Tables     Distributed Locks    PDF Invoices
  Indexed Tenant IDs     Idempotency Keys     Receipt HTMLs
  Audit Event Chains     Rate Limiting        Export Archives
  PgBouncer Pools        Session Cache        Evidence Files
```

---

## 3. Modular Monolith Domain Boundaries

Each domain module lives in `et.ut.einvoice.<module>` and enforces strict encapsulation:
- Controllers call only local Application Services.
- Cross-domain interactions occur strictly via **Domain Events** (`ApplicationEventPublisher` / Spring Events) or clearly defined **Facade Interfaces**.
- Direct repository access across module boundaries is strictly prohibited.

```text
et.ut.einvoice
├── platform/         # Cross-cutting kernel (Security, TenantContext, Idempotency, Exceptions)
├── identity/         # IAM, User authentication, Role permissions, API client credentials
├── tenancy/          # Tenant registry, deployment tiers, shard routing metadata
├── taxpayer/         # Legal profile, branches, devices, PostGIS geofences
├── taxation/         # Tax rules engine, VAT, Excise, Withholding, rounding policies
├── invoicing/        # Core invoice aggregate, line items, state machine, numbering
├── receipts/         # Sales receipts, cash receipts, withholding vouchers
├── adjustments/      # Tax Debit and Credit Notes (Directive Art. 25)
├── cancellation/     # Controlled cancellation state machine & 48-hour SLA (Directive Art. 26)
├── payments/         # Payment modes (Cash, Telebirr, CBE, Bank transfer)
├── offline/          # Secure device queue, sequence pre-allocation, 72h sync reconciliation
├── government/       # EIRS integration gateway adapter, Bouncy Castle payload signing
├── documents/        # Authoritative document rendering (A4 PDF, HTML, Thermal) & ZXing QR
├── notifications/    # Asynchronous buyer delivery (Email, SMS)
├── webhooks/         # Outbound event dispatch with signature verification and retry
├── audit/            # Append-only hash-chained business & security audit trail
├── compliance/       # Software version registry, SHA-256 build checksums, INSA keys
├── reporting/        # Operational tax reconciliation & summary reports
├── portability/      # Asynchronous tenant export and retention-aware data lifecycle
└── administration/   # Platform management and dedicated read-only Authority inspection APIs
```

---

## 4. Future Microservice Extraction Boundaries

When throughput and organizational boundaries dictate service separation, the domain boundaries allow zero-friction extraction:

| Module Candidates for Extraction | Scaling & Isolation Rationale |
|---|---|
| **Government Integration Gateway** | Independent scaling for MoR rate-limiting, connection pooling, and mTLS lifecycle management. |
| **Document Rendering Service** | High CPU & memory footprint (PDF generation, font rasterization, image encoding). |
| **Offline Synchronization Service** | Batch processing bursts during morning reconnect spikes without degrading online checkout APIs. |
| **Notification & Webhook Dispatcher** | Asynchronous network I/O with external SMS/Email/Webhook endpoints requiring retry queues. |
| **Audit & Compliance Datastore** | Long-term archival and immutable cold-storage offloading. |

---

## 5. Technology Stack & Component Justification

| Technology | Selected Tool | Rationale |
|---|---|---|
| **Language** | Java 21 LTS | Long-term enterprise stability, Virtual Threads (Project Loom) for high-concurrency I/O, strong type safety preventing financial calculation bugs. |
| **Framework** | Spring Boot 3.3.x on Spring 6 | Premier Java enterprise ecosystem, first-class support for Spring Security 6, Spring Data JPA, Actuator, and Flyway. |
| **Relational Database** | PostgreSQL 16 | ACID transactions, native declarative table partitioning, robust JSONB support, PostGIS for geospatial checks, streaming replication. |
| **Connection Pooling** | HikariCP + PgBouncer | HikariCP inside Spring Boot; PgBouncer at infrastructure layer allowing tens of thousands of client connections without exhausting PostgreSQL backends. |
| **In-Memory Cache & Locks** | Redis 7 | Distributed locking (`Redisson`), high-speed idempotency key verification, token bucket rate-limiting, and ephemeral sync state. |
| **Cryptography** | Bouncy Castle (Java Cryptography Provider) | INSA-compliant RSA/ECDSA digital signatures, PKCS#11 HSM integration, and SHA-256 canonical hashing. |
| **QR Generation** | ZXing 3.5.x | High-performance, error-correcting 2D barcode generation matching Ministry specifications. |
| **Migrations** | Flyway 10.x | Deterministic, version-controlled database schema migrations executed during deployment. |
| **Observability** | Micrometer, Prometheus, Grafana | Structured JSON logging with correlation IDs, Prometheus scraping metrics for invoice registration success/failure rates. |

---

## 6. Horizontal Scaling Strategy to 1M Tenants

1. **Stateless Compute**: Application containers run statelessly behind a layer-7 load balancer. Any container can process requests for any tenant.
2. **Shared Transactional Partitioning**:
   - Primary tables (`invoices`, `invoice_lines`, `audit_events`, `offline_buffer`) are partitioned by hash of `tenant_id` or range of `(tenant_id, created_at)`.
   - Queries always include `tenant_id` to guarantee partition pruning.
3. **Dedicated Enterprise Shards**: The `TenantRoutingDatasource` abstraction routes high-volume enterprise customers (e.g., national retail chains, telecom partners) to dedicated database shards while regular SME tenants share the primary cluster.
4. **Read Replicas**: High-volume reporting and Authority audit queries are offloaded to read replicas, ensuring operational checkout traffic is never starved of database CPU.
