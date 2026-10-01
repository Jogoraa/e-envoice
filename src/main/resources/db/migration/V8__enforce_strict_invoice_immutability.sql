-- ==============================================================================
-- UT Electronic Invoicing Platform — Flyway Migration V8
-- Enforce Strict Invoice & Invoice Line Immutability (FDRE Directive No. 1142/2026)
-- Prevents mutation or deletion of fiscal, buyer, totals, sequence, or lines of REGISTERED invoices
-- ==============================================================================

CREATE OR REPLACE FUNCTION enforce_invoice_immutability()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.status IN ('REGISTERED', 'CANCELLED') THEN
        IF TG_OP = 'DELETE' THEN
            RAISE EXCEPTION 'CANNOT_DELETE_OFFICIAL_INVOICE: Registered or cancelled invoices are permanently immutable';
        END IF;
        IF TG_OP = 'UPDATE' THEN
            -- Any attempt to mutate fiscal, sequence, identity, buyer, tax totals, or cryptographic evidence is prohibited
            IF OLD.tenant_id <> NEW.tenant_id OR
               OLD.document_number <> NEW.document_number OR
               OLD.invoice_counter <> NEW.invoice_counter OR
               OLD.invoice_date <> NEW.invoice_date OR
               OLD.currency <> NEW.currency OR
               OLD.pre_tax_total <> NEW.pre_tax_total OR
               OLD.tax_total <> NEW.tax_total OR
               OLD.excise_total <> NEW.excise_total OR
               OLD.grand_total <> NEW.grand_total OR
               (OLD.irn IS NOT NULL AND OLD.irn <> NEW.irn) OR
               (OLD.buyer_legal_name IS NOT NULL AND OLD.buyer_legal_name IS DISTINCT FROM NEW.buyer_legal_name) OR
               (OLD.buyer_tin IS NOT NULL AND OLD.buyer_tin IS DISTINCT FROM NEW.buyer_tin) OR
               (OLD.signed_qr IS NOT NULL AND OLD.signed_qr IS DISTINCT FROM NEW.signed_qr) OR
               (OLD.signed_invoice IS NOT NULL AND OLD.signed_invoice IS DISTINCT FROM NEW.signed_invoice) OR
               (OLD.ack_date IS NOT NULL AND OLD.ack_date IS DISTINCT FROM NEW.ack_date) THEN
                RAISE EXCEPTION 'FINANCIAL_MUTATION_FORBIDDEN: Registered or cancelled invoices are permanently immutable';
            END IF;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_invoice_immutability ON invoices;
CREATE TRIGGER trg_invoice_immutability
BEFORE UPDATE OR DELETE ON invoices
FOR EACH ROW
EXECUTE FUNCTION enforce_invoice_immutability();

CREATE OR REPLACE FUNCTION enforce_invoice_line_immutability()
RETURNS TRIGGER AS $$
DECLARE
    v_status character varying(32);
BEGIN
    IF TG_OP = 'DELETE' THEN
        SELECT status INTO v_status FROM invoices WHERE id = OLD.invoice_id;
        IF v_status IN ('REGISTERED', 'CANCELLED') THEN
            RAISE EXCEPTION 'CANNOT_DELETE_REGISTERED_INVOICE_LINE: Registered invoice line items are permanently immutable';
        END IF;
        RETURN OLD;
    ELSIF TG_OP = 'UPDATE' THEN
        SELECT status INTO v_status FROM invoices WHERE id = OLD.invoice_id;
        IF v_status IN ('REGISTERED', 'CANCELLED') THEN
            RAISE EXCEPTION 'CANNOT_UPDATE_REGISTERED_INVOICE_LINE: Registered invoice line items are permanently immutable';
        END IF;
        RETURN NEW;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_invoice_lines_immutability ON invoice_lines;
CREATE TRIGGER trg_invoice_lines_immutability
BEFORE UPDATE OR DELETE ON invoice_lines
FOR EACH ROW
EXECUTE FUNCTION enforce_invoice_line_immutability();
