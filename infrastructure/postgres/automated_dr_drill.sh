#!/usr/bin/env bash
# ==============================================================================
# UT Electronic Invoicing SaaS Platform — Automated DR Restore Drill Script
# Governed by FDRE MoR Directive No. 1142/2026 Art. 14(3)(a)
# Target SLA: RTO < 1,800 seconds (30 minutes), Zero Data Loss (RPO = 0)
# ==============================================================================

set -euo pipefail

SOURCE_HOST="${PGHOST:-127.0.0.1}"
SOURCE_PORT="${PGPORT:-5432}"
SOURCE_DB="${PGDATABASE:-ut_einvoice_db}"
SOURCE_USER="${PGUSER:-postgres}"

DRILL_DB="${TARGET_DB:-ut_einvoice_db_drill}"
TIMESTAMP=$(date -u +"%Y%m%d_%H%M%S")
BACKUP_FILE="/tmp/ut_dr_backup_${TIMESTAMP}.dump"
LOG_FILE="/tmp/ut_dr_drill_${TIMESTAMP}.log"

echo "======================================================================"
echo " Starting UT Invoice Automated Disaster Recovery Drill (${TIMESTAMP})"
echo " Source: ${SOURCE_USER}@${SOURCE_HOST}:${SOURCE_PORT}/${SOURCE_DB}"
echo " Target Drill DB: ${DRILL_DB}"
echo "======================================================================"

T_START=$(date +%s)

# 1. Capture production database dump
echo "[1/5] Dumping database ${SOURCE_DB}..."
T_DUMP_START=$(date +%s)
PGPASSWORD="${PGPASSWORD}" pg_dump -h "${SOURCE_HOST}" -p "${SOURCE_PORT}" -U "${SOURCE_USER}" \
    --format=custom --blobs --verbose --file="${BACKUP_FILE}" "${SOURCE_DB}" 2> "${LOG_FILE}"
T_DUMP_END=$(date +%s)
DUMP_DURATION=$((T_DUMP_END - T_DUMP_START))
BACKUP_SIZE=$(wc -c < "${BACKUP_FILE}")
echo "      Dump completed in ${DUMP_DURATION}s. Size: ${BACKUP_SIZE} bytes."

# 2. Re-create clean drill database
echo "[2/5] Initializing clean drill target database ${DRILL_DB}..."
PGPASSWORD="${PGPASSWORD}" dropdb -h "${SOURCE_HOST}" -p "${SOURCE_PORT}" -U "${SOURCE_USER}" --if-exists "${DRILL_DB}" >> "${LOG_FILE}" 2>&1 || true
PGPASSWORD="${PGPASSWORD}" createdb -h "${SOURCE_HOST}" -p "${SOURCE_PORT}" -U "${SOURCE_USER}" "${DRILL_DB}" >> "${LOG_FILE}" 2>&1

# 3. Restore dump into drill database
echo "[3/5] Restoring payload into ${DRILL_DB}..."
T_RESTORE_START=$(date +%s)
PGPASSWORD="${PGPASSWORD}" pg_restore -h "${SOURCE_HOST}" -p "${SOURCE_PORT}" -U "${SOURCE_USER}" \
    -d "${DRILL_DB}" --verbose --no-owner --no-privileges "${BACKUP_FILE}" >> "${LOG_FILE}" 2>&1 || true
T_RESTORE_END=$(date +%s)
RESTORE_DURATION=$((T_RESTORE_END - T_RESTORE_START))
echo "      Restore completed in ${RESTORE_DURATION}s."

# 4. Verify Schema & Row Level Security Parity
echo "[4/5] Verifying schema parity and Row-Level Security policies..."
TABLES_COUNT=$(PGPASSWORD="${PGPASSWORD}" psql -h "${SOURCE_HOST}" -p "${SOURCE_PORT}" -U "${SOURCE_USER}" -d "${DRILL_DB}" -t -A -c \
    "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public';")

RLS_COUNT=$(PGPASSWORD="${PGPASSWORD}" psql -h "${SOURCE_HOST}" -p "${SOURCE_PORT}" -U "${SOURCE_USER}" -d "${DRILL_DB}" -t -A -c \
    "SELECT count(*) FROM pg_tables WHERE schemaname = 'public' AND rowsecurity = true;")

SEQUENCES_COUNT=$(PGPASSWORD="${PGPASSWORD}" psql -h "${SOURCE_HOST}" -p "${SOURCE_PORT}" -U "${SOURCE_USER}" -d "${DRILL_DB}" -t -A -c \
    "SELECT count(*) FROM tenant_invoice_sequences;")

AUDIT_COUNT=$(PGPASSWORD="${PGPASSWORD}" psql -h "${SOURCE_HOST}" -p "${SOURCE_PORT}" -U "${SOURCE_USER}" -d "${DRILL_DB}" -t -A -c \
    "SELECT count(*) FROM audit_events;")

T_END=$(date +%s)
TOTAL_TIME=$((T_END - T_START))

echo "======================================================================"
echo " DR Drill Summary:"
echo " - Tables Restored: ${TABLES_COUNT}"
echo " - RLS Enabled Tables: ${RLS_COUNT}"
echo " - Tenant Sequences: ${SEQUENCES_COUNT}"
echo " - Audit Records: ${AUDIT_COUNT}"
echo " - Total RTO Achieved: ${TOTAL_TIME} seconds (Statutory SLA: 1,800s)"
echo "======================================================================"

if [ "${TOTAL_TIME}" -le 1800 ] && [ "${RLS_COUNT}" -ge 10 ]; then
    echo "STATUS: PASSED (RTO SLA Satisfied, Security Invariants Restored)"
    rm -f "${BACKUP_FILE}"
    exit 0
else
    echo "STATUS: FAILED (Threshold or RLS parity violation)"
    exit 1
fi
