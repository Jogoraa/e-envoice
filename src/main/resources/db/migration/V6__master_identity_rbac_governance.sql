-- ==============================================================================
-- UT Electronic Invoicing Platform — V6 Master Identity & RBAC Governance
-- Directive No. 1142/2026 Art. 4 & NIST SP 800-63B / SP 800-53 Compliance
-- ==============================================================================

-- 1. SYSTEM ROLES
CREATE TABLE IF NOT EXISTS system_roles (
    id UUID PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    description TEXT,
    scope VARCHAR(32) NOT NULL DEFAULT 'PLATFORM', -- 'PLATFORM', 'TENANT', 'GLOBAL'
    is_system BOOLEAN NOT NULL DEFAULT false,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', -- 'ACTIVE', 'RETIRED'
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_system_roles_code ON system_roles(code);
CREATE INDEX IF NOT EXISTS idx_system_roles_status ON system_roles(status);

-- 2. SYSTEM PERMISSIONS
CREATE TABLE IF NOT EXISTS system_permissions (
    id UUID PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    description TEXT,
    category VARCHAR(64) NOT NULL, -- 'IDENTITY', 'SECURITY', 'TENANCY', 'FISCAL', 'CONFIGURATION', 'AUDIT'
    risk_level VARCHAR(32) NOT NULL DEFAULT 'NORMAL', -- 'NORMAL', 'SENSITIVE', 'HIGH', 'CRITICAL'
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_system_permissions_code ON system_permissions(code);
CREATE INDEX IF NOT EXISTS idx_system_permissions_cat ON system_permissions(category);

-- 3. ROLE PERMISSION MAPPING
CREATE TABLE IF NOT EXISTS role_permissions (
    role_id UUID NOT NULL REFERENCES system_roles(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES system_permissions(id) ON DELETE CASCADE,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    granted_by VARCHAR(64) NOT NULL DEFAULT 'SYSTEM',
    PRIMARY KEY (role_id, permission_id)
);

-- 4. PLATFORM USERS SCHEMA ENHANCEMENTS
ALTER TABLE platform_users ADD COLUMN IF NOT EXISTS phone VARCHAR(32);
ALTER TABLE platform_users ADD COLUMN IF NOT EXISTS phone_verified BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE platform_users ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE platform_users ADD COLUMN IF NOT EXISTS mfa_enabled BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE platform_users ADD COLUMN IF NOT EXISTS mfa_secret VARCHAR(128);
ALTER TABLE platform_users ADD COLUMN IF NOT EXISTS timezone VARCHAR(64) NOT NULL DEFAULT 'Africa/Addis_Ababa';
ALTER TABLE platform_users ADD COLUMN IF NOT EXISTS date_format VARCHAR(32) NOT NULL DEFAULT 'YYYY-MM-DD';
ALTER TABLE platform_users ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 1;
ALTER TABLE platform_users ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE platform_users ADD COLUMN IF NOT EXISTS password_changed_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_platform_users_phone ON platform_users(phone);

-- 5. PLATFORM USER ROLES MAPPING
CREATE TABLE IF NOT EXISTS platform_user_roles (
    user_id UUID NOT NULL REFERENCES platform_users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES system_roles(id) ON DELETE CASCADE,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_by VARCHAR(64) NOT NULL DEFAULT 'SYSTEM',
    PRIMARY KEY (user_id, role_id)
);

-- 6. MFA RECOVERY CODES
CREATE TABLE IF NOT EXISTS platform_user_recovery_codes (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES platform_users(id) ON DELETE CASCADE,
    code_hash VARCHAR(255) NOT NULL,
    used BOOLEAN NOT NULL DEFAULT false,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_user_recovery_codes_user ON platform_user_recovery_codes(user_id);

-- 7. ADMINISTRATOR INVITATIONS
CREATE TABLE IF NOT EXISTS platform_user_invitations (
    id UUID PRIMARY KEY,
    email VARCHAR(128) NOT NULL,
    phone VARCHAR(32),
    full_name VARCHAR(128) NOT NULL,
    initial_role_code VARCHAR(64) NOT NULL,
    tenant_id UUID,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ,
    created_by VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING', -- 'PENDING', 'ACCEPTED', 'EXPIRED', 'REVOKED'
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_user_invitations_email ON platform_user_invitations(email);
CREATE INDEX IF NOT EXISTS idx_user_invitations_status ON platform_user_invitations(status);

-- 8. ACTIVE SESSIONS & DEVICES
CREATE TABLE IF NOT EXISTS platform_user_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES platform_users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL,
    ip_address VARCHAR(64),
    user_agent TEXT,
    device_summary VARCHAR(128),
    mfa_authenticated BOOLEAN NOT NULL DEFAULT false,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked BOOLEAN NOT NULL DEFAULT false,
    revoked_at TIMESTAMPTZ,
    revocation_reason VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_user_sessions_user ON platform_user_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_user_sessions_revoked ON platform_user_sessions(revoked);

-- 9. ACCOUNT VERIFICATIONS (Email Change, Phone Change, Password Reset)
CREATE TABLE IF NOT EXISTS platform_account_verifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES platform_users(id) ON DELETE CASCADE,
    verification_type VARCHAR(32) NOT NULL, -- 'EMAIL_CHANGE', 'PHONE_CHANGE', 'PASSWORD_RESET'
    target_value VARCHAR(128),
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used BOOLEAN NOT NULL DEFAULT false,
    used_at TIMESTAMPTZ,
    attempts_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_account_verif_user_type ON platform_account_verifications(user_id, verification_type);

-- 10. ACCESS REVIEW CAMPAIGNS & ENTRIES
CREATE TABLE IF NOT EXISTS access_review_campaigns (
    id UUID PRIMARY KEY,
    title VARCHAR(128) NOT NULL,
    description TEXT,
    initiated_by VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', -- 'ACTIVE', 'COMPLETED', 'CANCELLED'
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS access_review_entries (
    id UUID PRIMARY KEY,
    campaign_id UUID NOT NULL REFERENCES access_review_campaigns(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES platform_users(id) ON DELETE CASCADE,
    reviewer_id VARCHAR(64),
    current_roles TEXT,
    effective_permissions_summary TEXT,
    decision VARCHAR(32) NOT NULL DEFAULT 'PENDING', -- 'PENDING', 'APPROVED', 'MODIFIED', 'REVOKED', 'SUSPENDED'
    notes TEXT,
    reviewed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_access_entries_campaign ON access_review_entries(campaign_id);
CREATE INDEX IF NOT EXISTS idx_access_entries_user ON access_review_entries(user_id);

-- 11. SEED CANONICAL SYSTEM ROLES
INSERT INTO system_roles (id, code, name, description, scope, is_system, status)
VALUES
    ('a0000000-0000-0000-0000-000000000001', 'ROLE_PLATFORM_ADMIN', 'Platform Administrator', 'Full platform operational governance and security administration', 'PLATFORM', true, 'ACTIVE'),
    ('a0000000-0000-0000-0000-000000000002', 'ROLE_SAAS_ADMIN', 'SaaS Administrator', 'Tenant lifecycle, onboarding, and subscription management', 'PLATFORM', true, 'ACTIVE'),
    ('a0000000-0000-0000-0000-000000000003', 'ROLE_TENANT_ADMIN', 'Tenant Administrator', 'Tenant organization business administration and staff management', 'TENANT', true, 'ACTIVE'),
    ('a0000000-0000-0000-0000-000000000004', 'ROLE_SECURITY_ADMIN', 'Security Administrator', 'MFA, credential lifecycle, session governance, and access controls', 'PLATFORM', true, 'ACTIVE'),
    ('a0000000-0000-0000-0000-000000000005', 'ROLE_AUDIT_ADMIN', 'Audit Administrator', 'Immutable audit log investigation, compliance reviews, and reporting', 'GLOBAL', true, 'ACTIVE'),
    ('a0000000-0000-0000-0000-000000000006', 'ROLE_SUPPORT_ADMIN', 'Support Administrator', 'Technical support, diagnostics, and delegated assistance', 'PLATFORM', true, 'ACTIVE'),
    ('a0000000-0000-0000-0000-000000000007', 'ROLE_FINANCE_ADMIN', 'Finance Administrator', 'Billing, subscription invoices, usage metrics, and reconciliation', 'PLATFORM', true, 'ACTIVE'),
    ('a0000000-0000-0000-0000-000000000008', 'ROLE_READ_ONLY', 'Read-Only Auditor', 'Read-only visibility for system oversight without mutation authority', 'GLOBAL', true, 'ACTIVE')
ON CONFLICT (code) DO NOTHING;

-- 12. SEED CANONICAL PERMISSIONS
INSERT INTO system_permissions (id, code, name, description, category, risk_level)
VALUES
    -- Identity Category
    ('b0000000-0000-0000-0000-000000000001', 'USER_VIEW', 'View Administrators', 'View administrator users and profiles', 'IDENTITY', 'NORMAL'),
    ('b0000000-0000-0000-0000-000000000002', 'USER_CREATE', 'Create Administrator', 'Provision or invite new administrator users', 'IDENTITY', 'HIGH'),
    ('b0000000-0000-0000-0000-000000000003', 'USER_UPDATE', 'Update Administrator', 'Modify administrator profile and settings', 'IDENTITY', 'HIGH'),
    ('b0000000-0000-0000-0000-000000000004', 'USER_SUSPEND', 'Suspend Administrator', 'Temporarily suspend an administrator account', 'IDENTITY', 'HIGH'),
    ('b0000000-0000-0000-0000-000000000005', 'USER_DISABLE', 'Disable Administrator', 'Permanently deactivate an administrator account', 'IDENTITY', 'CRITICAL'),
    ('b0000000-0000-0000-0000-000000000006', 'USER_UNLOCK', 'Unlock Administrator', 'Unlock locked accounts after security review', 'IDENTITY', 'HIGH'),
    ('b0000000-0000-0000-0000-000000000007', 'ROLE_VIEW', 'View Roles', 'View system and custom role definitions', 'IDENTITY', 'NORMAL'),
    ('b0000000-0000-0000-0000-000000000008', 'ROLE_CREATE', 'Create Roles', 'Author new custom administrative roles', 'IDENTITY', 'HIGH'),
    ('b0000000-0000-0000-0000-000000000009', 'ROLE_UPDATE', 'Update Roles', 'Modify custom role definitions and permissions', 'IDENTITY', 'HIGH'),
    ('b0000000-0000-0000-0000-000000000010', 'ROLE_ASSIGN', 'Assign Roles', 'Assign or revoke roles from administrators', 'IDENTITY', 'CRITICAL'),
    ('b0000000-0000-0000-0000-000000000011', 'PERMISSION_VIEW', 'View Permissions', 'Inspect permission catalog and mappings', 'IDENTITY', 'NORMAL'),
    ('b0000000-0000-0000-0000-000000000012', 'ACCESS_REVIEW_MANAGE', 'Manage Access Reviews', 'Initiate, review, and finalize periodic access audits', 'IDENTITY', 'HIGH'),

    -- Security Category
    ('b0000000-0000-0000-0000-000000000013', 'MFA_VIEW', 'View MFA Status', 'Inspect MFA configuration and enforcement status', 'SECURITY', 'NORMAL'),
    ('b0000000-0000-0000-0000-000000000014', 'MFA_ENFORCE', 'Enforce MFA', 'Require mandatory MFA enrollment for administrators', 'SECURITY', 'HIGH'),
    ('b0000000-0000-0000-0000-000000000015', 'MFA_RESET', 'Reset MFA', 'Reset user MFA factors in recovery scenarios', 'SECURITY', 'CRITICAL'),
    ('b0000000-0000-0000-0000-000000000016', 'SESSION_VIEW', 'View Active Sessions', 'Inspect live operator sessions and device telemetry', 'SECURITY', 'NORMAL'),
    ('b0000000-0000-0000-0000-000000000017', 'SESSION_REVOKE', 'Revoke Sessions', 'Terminate operator sessions and force re-authentication', 'SECURITY', 'HIGH'),
    ('b0000000-0000-0000-0000-000000000018', 'SECURITY_POLICY_EDIT', 'Edit Security Policy', 'Configure password, lockout, and session policies', 'SECURITY', 'CRITICAL'),

    -- Tenancy Category
    ('b0000000-0000-0000-0000-000000000019', 'TENANT_VIEW', 'View Tenants', 'Inspect registered taxpayer organizations and status', 'TENANCY', 'NORMAL'),
    ('b0000000-0000-0000-0000-000000000020', 'TENANT_CREATE', 'Onboard Tenant', 'Provision new tenant organizations and primary admins', 'TENANCY', 'HIGH'),
    ('b0000000-0000-0000-0000-000000000021', 'TENANT_UPDATE', 'Update Tenant', 'Modify tenant metadata and subscription tiers', 'TENANCY', 'HIGH'),
    ('b0000000-0000-0000-0000-000000000022', 'TENANT_SUSPEND', 'Suspend Tenant', 'Enforce operational suspension or reactivation', 'TENANCY', 'CRITICAL'),

    -- Fiscal Category
    ('b0000000-0000-0000-0000-000000000023', 'FISCAL_MONITOR_VIEW', 'View Fiscal Health', 'Monitor invoice counters, sequences, and sync states', 'FISCAL', 'NORMAL'),
    ('b0000000-0000-0000-0000-000000000024', 'GATEWAY_STATUS_VIEW', 'View Gateway Telemetry', 'Inspect MoR EIRS latency and network availability', 'FISCAL', 'NORMAL'),

    -- Configuration Category
    ('b0000000-0000-0000-0000-000000000025', 'ENVIRONMENT_VIEW', 'View Environment', 'View masked platform configuration items', 'CONFIGURATION', 'SENSITIVE'),
    ('b0000000-0000-0000-0000-000000000026', 'ENVIRONMENT_EDIT', 'Edit Environment', 'Stage configuration changes and revisions', 'CONFIGURATION', 'HIGH'),
    ('b0000000-0000-0000-0000-000000000027', 'ENVIRONMENT_SECRET_ROTATE', 'Rotate Secrets', 'Rotate cryptographic keys, JWT, and database credentials', 'CONFIGURATION', 'CRITICAL'),
    ('b0000000-0000-0000-0000-000000000028', 'ENVIRONMENT_ROLLBACK', 'Rollback Environment', 'Revert configurations to previous immutable revision', 'CONFIGURATION', 'HIGH'),
    ('b0000000-0000-0000-0000-000000000029', 'ENVIRONMENT_APPLY', 'Apply Environment', 'Activate staged revisions into runtime registry', 'CONFIGURATION', 'CRITICAL'),

    -- Audit Category
    ('b0000000-0000-0000-0000-000000000030', 'AUDIT_VIEW', 'View Audit Ledger', 'Query immutable cryptographic audit event log', 'AUDIT', 'NORMAL'),
    ('b0000000-0000-0000-0000-000000000031', 'AUDIT_EXPORT', 'Export Audit Reports', 'Generate certified compliance audit exports', 'AUDIT', 'HIGH')
ON CONFLICT (code) DO NOTHING;

-- 13. MAP PERMISSIONS TO PLATFORM_ADMIN (All permissions)
INSERT INTO role_permissions (role_id, permission_id, granted_by)
SELECT 'a0000000-0000-0000-0000-000000000001', id, 'SYSTEM_BOOTSTRAP'
FROM system_permissions
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- MAP PERMISSIONS TO SECURITY_ADMIN
INSERT INTO role_permissions (role_id, permission_id, granted_by)
SELECT 'a0000000-0000-0000-0000-000000000004', id, 'SYSTEM_BOOTSTRAP'
FROM system_permissions
WHERE category IN ('SECURITY', 'AUDIT') OR code IN ('USER_VIEW', 'USER_UNLOCK', 'ROLE_VIEW', 'PERMISSION_VIEW')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- MAP PERMISSIONS TO AUDIT_ADMIN
INSERT INTO role_permissions (role_id, permission_id, granted_by)
SELECT 'a0000000-0000-0000-0000-000000000005', id, 'SYSTEM_BOOTSTRAP'
FROM system_permissions
WHERE category = 'AUDIT' OR code IN ('USER_VIEW', 'ROLE_VIEW', 'PERMISSION_VIEW', 'TENANT_VIEW', 'FISCAL_MONITOR_VIEW', 'GATEWAY_STATUS_VIEW')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- MAP PERMISSIONS TO READ_ONLY
INSERT INTO role_permissions (role_id, permission_id, granted_by)
SELECT 'a0000000-0000-0000-0000-000000000008', id, 'SYSTEM_BOOTSTRAP'
FROM system_permissions
WHERE code LIKE '%_VIEW'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- 14. ASSOCIATE DEFAULT MASTER ADMIN USER TO ROLE_PLATFORM_ADMIN
INSERT INTO platform_user_roles (user_id, role_id, assigned_by)
SELECT u.id, 'a0000000-0000-0000-0000-000000000001', 'SYSTEM_BOOTSTRAP'
FROM platform_users u
WHERE u.username = 'platform.admin'
ON CONFLICT (user_id, role_id) DO NOTHING;
