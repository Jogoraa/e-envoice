# High Availability & Disaster Recovery (HA/DR) Strategy
## Tier III Multi-Datacenter Resiliency in Addis Ababa, Ethiopia
### Compliance with Directive No. 1142/2026 Art. 5(4) & Art. 14(1)(g)

---

## 1. Compliance Mandates & SLO Targets

Under Article 5(4) and Article 14(1)(g) of Directive No. 1142/2026:
- The SaaS platform must operate across **at least two independent in-country datacenters** meeting Tier III standards (Raxio ET1 and Wingu Africa, Addis Ababa).
- The platform must support automatic failover to maintain service continuity if the primary facility fails.
- Reliable, redundant data links directly connecting to the Ministry of Revenues EIRS.

### Platform Service Level Objectives (SLOs):
| Metric | Target | Technical Mechanism |
|---|---|---|
| **Service Availability** | 99.95% | Stateless active-active application nodes across dual availability zones. |
| **Recovery Point Objective (RPO)** | < 1 second | PostgreSQL synchronous streaming replication to local standby, async to secondary DC. |
| **Recovery Time Objective (RTO)** | < 60 seconds | Automated health-check failover via PgBouncer and Patroni leader election. |

---

## 2. Infrastructure Deployment Topology

```text
               Global DNS / Anycast Any-IP
                           │
         ┌─────────────────┴─────────────────┐
         ▼                                   ▼
 [Primary DC: Raxio ET1]             [Secondary DC: Wingu Africa]
 ├── Border Routers (Dual ISP)       ├── Border Routers (Dual ISP)
 ├── Cloudflare / WAF                ├── Cloudflare / WAF
 ├── HAProxy / Keepalived LB         ├── HAProxy / Keepalived Standby
 ├── Spring Boot App Nodes (1..4)    ├── Spring Boot App Nodes (1..2)
 ├── PgBouncer Poolers               ├── PgBouncer Poolers
 ├── PostgreSQL Primary (RW) ───────►├── PostgreSQL Standby (RO / Failover)
 ├── Redis Sentinel (Leader) ───────►├── Redis Standby Replica
 └── MinIO S3 Primary ──────────────►└── MinIO S3 Bucket Replication
```

---

## 3. High Availability Design Principles

### 3.1 Stateless Application Nodes
- Spring Boot instances maintain zero session state in JVM memory.
- User authentication tokens are stateless JWTs signed with RSA-256.
- Ephemeral state (rate limits, idempotency locks, pending sync jobs) is externalized to the Redis cluster.
- Nodes can be terminated, scaled out, or restarted during deployment with zero impact on active transactions.

### 3.2 Database High Availability & Connection Pooling
- Application instances connect to local **PgBouncer** instances using transaction-level pooling.
- PgBouncer handles up to 20,000 incoming client connections while maintaining a lean pool of 100-200 active connections to the PostgreSQL primary engine.
- High-availability replication and automated failover are orchestrated via **Patroni** and Raft consensus. If the primary node fails, a standby is promoted within 15 seconds.

### 3.3 Disaster Recovery Verification Drills
- **Weekly Automated Backup Validation**: Backups created via `pg_dump` and physical WAL archiving are automatically restored into an isolated sandbox VM to verify integrity and readability.
- **Bi-Annual Failover Simulation**: Chaos testing cutting off the primary DC to validate automated traffic redirection and transaction continuity in the secondary DC.
