-- ==============================================================================
-- UT INVOICE — CANONICAL DATABASE MIGRATION
-- Migration: V9__permit_pre_auth_tenant_user_and_api_client_lookups.sql
-- Classification: Pre-Authentication Tenant User & API Client Lookups
-- Authoritative Compliance: FDRE Ministry of Revenue Directive No. 1142/2026
-- ==============================================================================

-- Permit pre-authentication lookups and last-login updates on tenant_users when app.current_tenant_id is unestablished
DROP POLICY IF EXISTS tenant_users_rls_policy ON tenant_users;
CREATE POLICY tenant_users_rls_policy ON tenant_users
    FOR ALL
    USING (
        NULLIF(current_setting('app.current_tenant_id', true), '') IS NULL
        OR tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    )
    WITH CHECK (
        NULLIF(current_setting('app.current_tenant_id', true), '') IS NULL
        OR tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    );

-- Permit pre-authentication API client credential resolution when app.current_tenant_id is unestablished
DROP POLICY IF EXISTS tenant_api_clients_rls_policy ON api_clients;
CREATE POLICY tenant_api_clients_rls_policy ON api_clients
    FOR ALL
    USING (
        NULLIF(current_setting('app.current_tenant_id', true), '') IS NULL
        OR tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    )
    WITH CHECK (
        NULLIF(current_setting('app.current_tenant_id', true), '') IS NULL
        OR tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    );
