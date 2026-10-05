-- ==============================================================================
-- UT Electronic Invoicing Platform — Flyway Migration V10
-- Statutory Document Taxonomy & Credit Sales Lifecycle (Directive No. 1142/2026)
-- Articles 2(14, 16, 17, 18, 19), 4(1)(e, f), 24, 25
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 1. CASH RECEIPTS (የጥሬ ገንዘብ መቀበያ ደረሰኝ - Art. 2(16), 4(1)(f))
-- Standalone cash collection for advances, credit settlements, loans, account deposits
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS cash_receipts (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    receipt_number VARCHAR(64) NOT NULL,
    payer_name VARCHAR(255) NOT NULL,
    payer_tin VARCHAR(32),
    amount NUMERIC(18, 2) NOT NULL,
    currency VARCHAR(8) NOT NULL DEFAULT 'ETB',
    purpose VARCHAR(64) NOT NULL, -- ADVANCE_PAYMENT, CREDIT_SALE_SETTLEMENT, LOAN_REPAYMENT, CUSTOMER_ACCOUNT_PAYMENT, OTHER_NON_SALE
    purpose_description VARCHAR(255),
    related_invoice_id UUID REFERENCES invoices(id),
    related_credit_account_id VARCHAR(64),
    payment_method VARCHAR(32) NOT NULL DEFAULT 'CASH', -- CASH, BANK_TRANSFER, CHECK, DIGITAL_WALLET
    reference_number VARCHAR(128),
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    rrn VARCHAR(128) NOT NULL UNIQUE,
    irn VARCHAR(128),
    qr_code TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'REGISTERED', -- REGISTERED, CANCELLED
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_cash_receipt_tenant_number UNIQUE (tenant_id, receipt_number)
);

CREATE INDEX idx_cash_receipts_tenant_number ON cash_receipts(tenant_id, receipt_number);
CREATE INDEX idx_cash_receipts_tenant_purpose ON cash_receipts(tenant_id, purpose);
CREATE INDEX idx_cash_receipts_tenant_invoice ON cash_receipts(tenant_id, related_invoice_id);
CREATE INDEX idx_cash_receipts_rrn ON cash_receipts(rrn);

ALTER TABLE cash_receipts ENABLE ROW LEVEL SECURITY;
ALTER TABLE cash_receipts FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_cash_receipts_rls_policy ON cash_receipts;
CREATE POLICY tenant_cash_receipts_rls_policy ON cash_receipts
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    );

-- ------------------------------------------------------------------------------
-- 2. PURCHASE VOUCHERS (የግዢ ማረጋገጫ ሰነድ - Art. 2(18), 4(1)(f))
-- Buyer-generated document when procuring from suppliers legally exempt or unable to issue receipts
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS purchase_vouchers (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    voucher_number VARCHAR(64) NOT NULL,
    buyer_taxpayer_id UUID NOT NULL REFERENCES taxpayers(id),
    buyer_tin VARCHAR(32) NOT NULL,
    supplier_name VARCHAR(255) NOT NULL,
    supplier_tin VARCHAR(32),
    supplier_id_number VARCHAR(64),
    supplier_id_type VARCHAR(32), -- NATIONAL_ID, PASSPORT, DRIVING_LICENSE, KEBELE_ID
    supplier_phone VARCHAR(32),
    supplier_address TEXT,
    unavailable_reason VARCHAR(64) NOT NULL, -- FARMER_AGRICULTURAL_PRODUCE, INFORMAL_SUPPLIER_BELOW_THRESHOLD, STATUTORY_EXEMPT_SUPPLIER, OUT_OF_OFFICE_EMERGENCY, OTHER_LEGAL_EXEMPTION
    reason_description VARCHAR(255),
    transaction_date TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    total_amount NUMERIC(18, 2) NOT NULL,
    total_tax_amount NUMERIC(18, 2) NOT NULL DEFAULT 0.00,
    withholding_amount NUMERIC(18, 2) NOT NULL DEFAULT 0.00,
    net_payable_amount NUMERIC(18, 2) NOT NULL,
    currency VARCHAR(8) NOT NULL DEFAULT 'ETB',
    attachment_reference VARCHAR(255),
    rrn VARCHAR(128) NOT NULL UNIQUE,
    irn VARCHAR(128),
    qr_code TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'REGISTERED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_purchase_voucher_tenant_number UNIQUE (tenant_id, voucher_number)
);

CREATE INDEX idx_purchase_vouchers_tenant_number ON purchase_vouchers(tenant_id, voucher_number);
CREATE INDEX idx_purchase_vouchers_tenant_supplier ON purchase_vouchers(tenant_id, supplier_tin);
CREATE INDEX idx_purchase_vouchers_rrn ON purchase_vouchers(rrn);

ALTER TABLE purchase_vouchers ENABLE ROW LEVEL SECURITY;
ALTER TABLE purchase_vouchers FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_purchase_vouchers_rls_policy ON purchase_vouchers;
CREATE POLICY tenant_purchase_vouchers_rls_policy ON purchase_vouchers
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    );

CREATE TABLE IF NOT EXISTS purchase_voucher_lines (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    voucher_id UUID NOT NULL REFERENCES purchase_vouchers(id) ON DELETE CASCADE,
    line_number INT NOT NULL,
    item_description VARCHAR(255) NOT NULL,
    quantity NUMERIC(18, 4) NOT NULL,
    unit_of_measure VARCHAR(16) NOT NULL,
    unit_price NUMERIC(18, 2) NOT NULL,
    total_price NUMERIC(18, 2) NOT NULL,
    tax_rate NUMERIC(6, 4) NOT NULL DEFAULT 0.0000,
    tax_amount NUMERIC(18, 2) NOT NULL DEFAULT 0.00,
    CONSTRAINT uk_purchase_voucher_line UNIQUE (voucher_id, line_number)
);

CREATE INDEX idx_pv_lines_tenant_voucher ON purchase_voucher_lines(tenant_id, voucher_id);

ALTER TABLE purchase_voucher_lines ENABLE ROW LEVEL SECURITY;
ALTER TABLE purchase_voucher_lines FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_pv_lines_rls_policy ON purchase_voucher_lines;
CREATE POLICY tenant_pv_lines_rls_policy ON purchase_voucher_lines
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    );

-- ------------------------------------------------------------------------------
-- 3. WITHHOLDING RECEIPTS (የታክስ ተቀናሽ ደረሰኝ - Art. 2(17, 19), 4(1)(e))
-- Explicit separation of Income Tax Withholding vs VAT Withholding
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS withholding_receipts (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    receipt_number VARCHAR(64) NOT NULL,
    withholding_type VARCHAR(32) NOT NULL, -- INCOME_TAX_WITHHOLDING, VAT_WITHHOLDING
    related_invoice_id UUID REFERENCES invoices(id),
    related_invoice_irn VARCHAR(128),
    withholding_agent_tin VARCHAR(32) NOT NULL,
    withholding_agent_name VARCHAR(255) NOT NULL,
    taxpayer_tin VARCHAR(32) NOT NULL,
    taxpayer_name VARCHAR(255) NOT NULL,
    tax_base_amount NUMERIC(18, 2) NOT NULL,
    withheld_tax_rate NUMERIC(6, 4) NOT NULL, -- e.g. 0.0200 for 2% income, 0.5000 or 1.0000 for VAT
    withheld_tax_amount NUMERIC(18, 2) NOT NULL,
    payment_reference VARCHAR(128),
    issue_date TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    rrn VARCHAR(128) NOT NULL UNIQUE,
    qr_code TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'REGISTERED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_withholding_receipt_tenant_number UNIQUE (tenant_id, receipt_number)
);

CREATE INDEX idx_withholding_receipts_tenant_type ON withholding_receipts(tenant_id, withholding_type);
CREATE INDEX idx_withholding_receipts_tenant_invoice ON withholding_receipts(tenant_id, related_invoice_id);
CREATE INDEX idx_withholding_receipts_rrn ON withholding_receipts(rrn);

ALTER TABLE withholding_receipts ENABLE ROW LEVEL SECURITY;
ALTER TABLE withholding_receipts FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_withholding_receipts_rls_policy ON withholding_receipts;
CREATE POLICY tenant_withholding_receipts_rls_policy ON withholding_receipts
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    );

-- ------------------------------------------------------------------------------
-- 4. CREDIT SALES LIFECYCLE (የብድር ሽያጭ - Art. 2(14), 24)
-- Extend invoices with credit terms, due date, outstanding balance, and credit status
-- ------------------------------------------------------------------------------
ALTER TABLE invoices
    ADD COLUMN IF NOT EXISTS credit_due_date TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS credit_terms_description VARCHAR(255),
    ADD COLUMN IF NOT EXISTS outstanding_balance NUMERIC(18, 2) DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS credit_status VARCHAR(32) DEFAULT 'NOT_APPLICABLE'; -- NOT_APPLICABLE, UNPAID, PARTIALLY_PAID, SETTLED, OVERDUE

UPDATE invoices
SET outstanding_balance = 0.00, credit_status = 'NOT_APPLICABLE'
WHERE credit_status IS NULL;

-- ------------------------------------------------------------------------------
-- 5. CREDIT SETTLEMENTS (የብድር ሽያጭ ክፍያ ማረጋገጫ መዝገብ - Art. 2(14))
-- Immutable ledger tracking subsequent partial and full settlements against credit invoices
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS credit_settlements (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    invoice_id UUID NOT NULL REFERENCES invoices(id),
    cash_receipt_id UUID NOT NULL REFERENCES cash_receipts(id),
    settlement_number VARCHAR(64) NOT NULL,
    settlement_amount NUMERIC(18, 2) NOT NULL,
    balance_before NUMERIC(18, 2) NOT NULL,
    balance_after NUMERIC(18, 2) NOT NULL,
    payment_method VARCHAR(32) NOT NULL DEFAULT 'CASH',
    payment_reference VARCHAR(128),
    settled_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_credit_settlement_tenant_number UNIQUE (tenant_id, settlement_number)
);

CREATE INDEX idx_credit_settlements_tenant_invoice ON credit_settlements(tenant_id, invoice_id);
CREATE INDEX idx_credit_settlements_receipt ON credit_settlements(cash_receipt_id);

ALTER TABLE credit_settlements ENABLE ROW LEVEL SECURITY;
ALTER TABLE credit_settlements FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_credit_settlements_rls_policy ON credit_settlements;
CREATE POLICY tenant_credit_settlements_rls_policy ON credit_settlements
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    );

-- ------------------------------------------------------------------------------
-- 6. IMMUTABILITY ENFORCEMENT ON FISCAL DOCUMENTS
-- Prevent deletion or unauthorized mutation of registered statutory documents
-- ------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION enforce_statutory_document_immutability()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'STATUTORY_DOCUMENT_DELETE_FORBIDDEN: Registered statutory fiscal documents are permanently immutable by law';
    END IF;
    IF TG_OP = 'UPDATE' THEN
        IF OLD.tenant_id <> NEW.tenant_id OR
           OLD.amount <> NEW.amount OR
           OLD.currency <> NEW.currency OR
           (OLD.rrn IS NOT NULL AND OLD.rrn <> NEW.rrn) OR
           (OLD.receipt_number <> NEW.receipt_number) THEN
            RAISE EXCEPTION 'STATUTORY_DOCUMENT_MUTATION_FORBIDDEN: Registered amounts, numbers, and currency cannot be modified';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_cash_receipts_immutability ON cash_receipts;
CREATE TRIGGER trg_cash_receipts_immutability
BEFORE UPDATE OR DELETE ON cash_receipts
FOR EACH ROW EXECUTE FUNCTION enforce_statutory_document_immutability();

DROP TRIGGER IF EXISTS trg_credit_settlements_immutability ON credit_settlements;
CREATE TRIGGER trg_credit_settlements_immutability
BEFORE UPDATE OR DELETE ON credit_settlements
FOR EACH ROW EXECUTE FUNCTION enforce_statutory_document_immutability();
