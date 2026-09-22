# UT Invoice — Production Operations & Deployment Runbook

**Platform:** UT Electronic Invoicing SaaS Platform  
**Target Environment:** Enterprise Linux / Kubernetes (Dual-DC Tier III Infrastructure)  
**Document Classification:** Confidential — Operational Runbook  
**Last Updated:** September 22, 2026  
**Status:** **ACTIVE PRODUCTION RUNBOOK**

---

## 1. Production Architecture Overview & Prerequisites

### 1.1 Minimum Target Node Specifications
- **Operating System:** Red Hat Enterprise Linux (RHEL) 9.x or Ubuntu 24.04 LTS (Kernel 6.8+).
- **Runtime Environment:** OpenJDK 21 LTS (Temurin / Eclipse Adoptium).
- **Database Engine:** PostgreSQL 16.3+ with PostGIS and `contrib` modules.
- **Cache / Distributed Lock:** Redis 7.2+ Cluster or Sentinel.
- **HSM Interface:** PKCS#11 client shared object (e.g., `/usr/lib/libCryptoki2.so` for Thales Luna).

### 1.2 Secret Management Strategy
No production secrets (database credentials, private keys, API secrets) may ever be stored in configuration files or Git repositories. Secrets are injected at container runtime via **HashiCorp Vault Agent** or **Kubernetes Secrets Store CSI Driver** into environment variables:
- `SPRING_DATASOURCE_PASSWORD`
- `UT_CRYPTO_HSM_PIN`
- `UT_EIRS_CLIENT_SECRET`
- `UT_JWT_PRIVATE_KEY`

---

## 2. Zero-Downtime Deployment Procedure

```text
┌────────────────────────────────────────────────────────┐
│  Phase 1: Pre-Flight Verification & Canary Schema      │
│  - Run Flyway migrate (backward-compatible DDL only)   │
├────────────────────────────────────────────────────────┤
│  Phase 2: Rolling Pod Replacement (MaxSurge: 25%)      │
│  - Spin up v2 pods with new image                      │
│  - StartupConfigurationValidator checks 12 subsystems   │
│  - Readiness probe passes ──► Traffic shifted to v2    │
├────────────────────────────────────────────────────────┤
│  Phase 3: Deprecate v1 Pods & Post-Flight Validation   │
│  - Drain active v1 connections (graceful 30s timeout)  │
│  - Terminate v1 pods                                   │
└────────────────────────────────────────────────────────┘
```

### 2.1 Step-by-Step Deployment Commands
```bash
# 1. Verify cluster connectivity
kubectl get nodes -o wide

# 2. Apply database migrations via dedicated Flyway job
kubectl apply -f k8s/jobs/flyway-migration-job.yaml
kubectl wait --for=condition=complete job/flyway-migration --timeout=120s

# 3. Deploy new application release with rolling update
kubectl set image deployment/ut-einvoice-backend \
  backend=registry.ut.et/einvoice/backend:2.1.0-prod \
  --record

# 4. Monitor rollout status
kubectl rollout status deployment/ut-einvoice-backend --timeout=300s

# 5. Verify application readiness probes
kubectl get pods -l app=ut-einvoice-backend
curl -f https://api.einvoice.ut.et/actuator/health/readiness
```

---

## 3. Secret & Credential Rotation Procedures

### 3.1 PostgreSQL Database Password Rotation
1. Update database user password on PostgreSQL primary:
   ```sql
   ALTER USER einvoice_app WITH PASSWORD 'NEW_SECURE_PASSWORD_32_CHAR';
   ```
2. Update Vault secret path `secret/data/production/database`.
3. Trigger Vault agent refresh on running application pods:
   ```bash
   kubectl rollout restart deployment/ut-einvoice-backend
   ```
4. Confirm HikariCP re-establishes pool connections cleanly without errors.

### 3.2 PKCS#11 HSM Signing Key Rotation
1. Connect to HSM slot via administrative utility:
   ```bash
   cmu generatekeypair -keytype=EC -curvename=secp256r1 -label="ut-signing-2027" -slot=1
   ```
2. Export public certificate and submit to MoR EIRS registration API:
   ```bash
   curl -X POST https://eirs.mor.gov.et/api/v1/certificates/register \
     -H "Authorization: Bearer $MOR_TOKEN" \
     -d @ut-signing-2027.crt
   ```
3. Update environment variable `UT_CRYPTO_HSM_KEY_ALIAS=ut-signing-2027`.
4. Trigger rolling restart of backend services. Old public keys remain accessible in the slot for historical verification.

---

## 4. Datacenter Disaster Failover Runbook (DC-1 to DC-2)

In the event of total network or utility blackout at Primary Datacenter (Addis Ababa DC-1):

### Phase 1: Failover Decision & Activation (Elapsed: T+0 to T+2 min)
1. Incident Commander confirms loss of DC-1 (3 consecutive failed heartbeats).
2. Issue emergency failover directive in `#ops-critical` channel.

### Phase 2: Database Promotion at Secondary Site DC-2 (Elapsed: T+2 to T+5 min)
1. Log into Patroni cluster coordinator in DC-2:
   ```bash
   patronictl -c /etc/patroni/dc2.yml failover ut-pg-cluster --candidate ut-pg-dc2-01 --force
   ```
2. Verify DC-2 PostgreSQL instance is in write mode:
   ```sql
   SELECT pg_is_in_recovery(); -- Must return FALSE
   ```

### Phase 3: Traffic Redirection (Elapsed: T+5 to T+8 min)
1. Update Global Server Load Balancer (GSLB) / Cloudflare / DNS records:
   - Point `api.einvoice.ut.et` CNAME to `dc2-ingress.ut.et`.
   - TTL is set to 60 seconds; worldwide traffic shifts within 3 minutes.

### Phase 4: Standby Application Cluster Scaling (Elapsed: T+8 to T+12 min)
1. Scale up DC-2 application deployment from warm capacity:
   ```bash
   kubectl scale deployment/ut-einvoice-backend --replicas=24 --context=dc2
   ```
2. Verify `/actuator/health` returns HTTP 200 across all pods.
3. Inform MoR EIRS Operations team of source IP CIDR switch for mTLS whitelist.

---

## 5. Cold Disaster Recovery Restore Procedure

Derived directly from the verified live drill (`certification/evidence/database-restore-drill.json`):

```bash
# 1. Provision target clean PostgreSQL 16 instance
createdb -h dr-pg-host -U postgres -O einvoice_app ut_einvoice_db

# 2. Decrypt latest pgBackRest / pg_dump encrypted backup container
gpg --decrypt ut_backup_latest.sql.gpg > /tmp/ut_backup_latest.sql

# 3. Restore schema, constraints, sequences, and tables
psql -h dr-pg-host -U postgres -d ut_einvoice_db -f /tmp/ut_backup_latest.sql

# 4. Verify RLS policies and row counts
psql -h dr-pg-host -U postgres -d ut_einvoice_db -c "
  SELECT count(*) FROM pg_tables WHERE tablename IN ('invoices', 'audit_events', 'tax_sequences');
  SELECT tablename, rowsecurity FROM pg_tables WHERE schemaname = 'public' AND rowsecurity = true;
"

# 5. Clean up decrypted temporary dump
shred -u /tmp/ut_backup_latest.sql
```

---

## 6. Incident Response & Escalation Matrix

```text
┌──────────────┬────────────────────────────┬─────────────────────────────┬──────────────┐
│ Severity     │ Impact Description         │ Escalation Target           │ SLA Response │
├──────────────┼────────────────────────────┼─────────────────────────────┼──────────────┤
│ P0 - Blocker │ Total outage; RLS breach;  │ CTO, Principal Architect,   │ < 15 minutes │
│              │ sequence collision         │ Lead SecOps, Lead SRE       │              │
├──────────────┼────────────────────────────┼─────────────────────────────┼──────────────┤
│ P1 - Critical│ EIRS clearance failing;    │ Lead Integration Architect, │ < 30 minutes │
│              │ HSM signing degraded       │ Lead Backend Engineer       │              │
├──────────────┼────────────────────────────┼─────────────────────────────┼──────────────┤
│ P2 - Major   │ Single tenant degraded;    │ Senior Backend Engineer,    │ < 2 hours    │
│              │ SMS delivery delay         │ Operations On-Call          │              │
├──────────────┼────────────────────────────┼─────────────────────────────┼──────────────┤
│ P3 - Minor   │ Non-fiscal UI defect;      │ Frontend Team,              │ < 1 business │
│              │ documentation typo         │ Support Desk                │ day          │
└──────────────┴────────────────────────────┴─────────────────────────────┴──────────────┘
```
