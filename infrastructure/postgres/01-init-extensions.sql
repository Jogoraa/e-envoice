-- =====================================================================
-- UT Electronic Invoicing Platform — Database Initialization Script
-- Executed on initial PostgreSQL container startup
-- Defense-in-depth: Unprivileged application role without SUPERUSER or BYPASSRLS
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "btree_gist";

-- Set default search path
ALTER DATABASE ut_einvoice_db SET search_path TO public;

-- Create unprivileged runtime application role without BYPASSRLS or SUPERUSER
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'ut_app_user') THEN
        CREATE ROLE ut_app_user WITH LOGIN PASSWORD 'appuserpassword' NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE;
    END IF;
END $$;

GRANT CONNECT ON DATABASE ut_einvoice_db TO ut_app_user;
GRANT USAGE ON SCHEMA public TO ut_app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO ut_app_user;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO ut_app_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ut_app_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO ut_app_user;
