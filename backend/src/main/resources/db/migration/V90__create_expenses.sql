-- Spec 015: Expenses. No foreign keys cross modules: user ids, zone ids, linked record ids and file ids are plain UUIDs
-- checked through the owning module. Version 90 is used so that specs built in parallel do not collide on a number.

CREATE TABLE expense_claim (
    id                 UUID PRIMARY KEY,
    claimant_id        UUID           NOT NULL,
    client_request_id  UUID           NOT NULL,
    expense_date       DATE           NOT NULL,
    amount             NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
    category           VARCHAR(20)    NOT NULL CHECK (category IN (
        'RENT_UTILITIES', 'TRAVEL_FUEL', 'TRAINING_STAY', 'MARKETING', 'OFFICE_SUPPLIES', 'OTHER')),
    zone_id            UUID,
    note               VARCHAR(1000),
    paid_by            VARCHAR(10)    NOT NULL CHECK (paid_by IN ('CLAIMANT', 'COMPANY')),
    reason             VARCHAR(500),
    no_bill_reason     VARCHAR(500),
    link_kind          VARCHAR(40),
    link_ref           UUID,
    link_date          DATE,
    link_label         VARCHAR(200),
    status             VARCHAR(20)    NOT NULL CHECK (status IN (
        'DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'PAID', 'PAID_BY_COMPANY')),
    paid_how           VARCHAR(10)    CHECK (paid_how IN ('PAYROLL', 'OUTSIDE')),
    submitted_at       TIMESTAMPTZ,
    decided_by         UUID,
    decided_at         TIMESTAMPTZ,
    decision_reason    VARCHAR(500),
    paid_on            DATE,
    paid_mode          VARCHAR(20)    CHECK (paid_mode IN ('CASH', 'BANK_TRANSFER', 'UPI', 'OTHER')),
    paid_reference     VARCHAR(100),
    paid_by_user       UUID,
    payroll_run_id     UUID,
    payroll_month      DATE,
    version            BIGINT         NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ    NOT NULL,
    created_by         UUID           NOT NULL,
    updated_at         TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uq_expense_claim_request UNIQUE (claimant_id, client_request_id),
    CONSTRAINT ck_expense_claim_paid_how CHECK ((paid_how IS NOT NULL) = (status = 'PAID')),
    CONSTRAINT ck_expense_claim_link CHECK ((link_kind IS NULL) = (link_ref IS NULL))
);

CREATE INDEX ix_expense_claim_claimant ON expense_claim (claimant_id, expense_date DESC);
CREATE INDEX ix_expense_claim_status ON expense_claim (status, submitted_at);
CREATE INDEX ix_expense_claim_zone ON expense_claim (zone_id, expense_date);
CREATE INDEX ix_expense_claim_category ON expense_claim (category, expense_date);

-- the database half of SC-009: a paid claim's money facts never change
CREATE FUNCTION expense_claim_paid_immutable() RETURNS trigger AS $$
BEGIN
    IF OLD.status IN ('PAID', 'PAID_BY_COMPANY') THEN
        IF TG_OP = 'DELETE' THEN
            RAISE EXCEPTION 'A paid expense claim cannot be deleted';
        END IF;
        IF NEW.claimant_id IS DISTINCT FROM OLD.claimant_id
                OR NEW.expense_date IS DISTINCT FROM OLD.expense_date
                OR NEW.amount IS DISTINCT FROM OLD.amount
                OR NEW.category IS DISTINCT FROM OLD.category
                OR NEW.zone_id IS DISTINCT FROM OLD.zone_id
                OR NEW.paid_by IS DISTINCT FROM OLD.paid_by
                OR NEW.link_kind IS DISTINCT FROM OLD.link_kind
                OR NEW.link_ref IS DISTINCT FROM OLD.link_ref
                OR NEW.link_date IS DISTINCT FROM OLD.link_date
                OR NEW.link_label IS DISTINCT FROM OLD.link_label
                OR NEW.status IS DISTINCT FROM OLD.status THEN
            RAISE EXCEPTION 'A paid expense claim cannot be changed';
        END IF;
    END IF;
    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_expense_claim_paid_immutable
    BEFORE UPDATE OR DELETE ON expense_claim
    FOR EACH ROW EXECUTE FUNCTION expense_claim_paid_immutable();

CREATE TABLE expense_bill (
    id              UUID PRIMARY KEY,
    claim_id        UUID         NOT NULL REFERENCES expense_claim (id),
    file_id         UUID         NOT NULL,
    name            VARCHAR(255) NOT NULL,
    content_type    VARCHAR(100),
    size_bytes      BIGINT,
    added_by        UUID         NOT NULL,
    added_at        TIMESTAMPTZ  NOT NULL,
    removed_by      UUID,
    removed_at      TIMESTAMPTZ,
    removal_reason  VARCHAR(500),
    CONSTRAINT ck_expense_bill_removal CHECK (removed_at IS NULL OR removal_reason IS NOT NULL)
);

CREATE INDEX ix_expense_bill_claim ON expense_bill (claim_id);

CREATE TABLE expense_claim_event (
    id        UUID PRIMARY KEY,
    claim_id  UUID         NOT NULL,  -- no foreign key: a deleted Draft keeps its history
    at        TIMESTAMPTZ  NOT NULL,
    actor_id  UUID,
    event     VARCHAR(30)  NOT NULL CHECK (event IN (
        'CREATED', 'EDITED', 'SUBMITTED', 'WITHDRAWN', 'RESUBMITTED', 'APPROVED', 'REJECTED', 'REVERSED',
        'BILL_ADDED', 'BILL_REMOVED', 'DUPLICATE_CONFIRMED', 'OFFERED_TO_PAYROLL', 'PAYROLL_OFFER_SKIPPED',
        'PAID_WITH_PAYROLL', 'PAID_OUTSIDE', 'PAID_BY_COMPANY')),
    reason    VARCHAR(500),
    detail    VARCHAR(2000)
);

CREATE INDEX ix_expense_claim_event_claim ON expense_claim_event (claim_id, at);

CREATE TABLE expense_area_budget (
    id          UUID PRIMARY KEY,
    zone_id     UUID,
    month       DATE           NOT NULL,
    amount      NUMERIC(14, 2) NOT NULL CHECK (amount >= 0),
    version     BIGINT         NOT NULL DEFAULT 0,
    updated_at  TIMESTAMPTZ    NOT NULL,
    updated_by  UUID           NOT NULL
);

-- one budget per area and month; Head office (no zone) counts as one area
CREATE UNIQUE INDEX uq_expense_area_budget
    ON expense_area_budget ((COALESCE(zone_id, '00000000-0000-0000-0000-000000000000'::uuid)), month);

CREATE TABLE expense_budget_change (
    id          UUID PRIMARY KEY,
    budget_id   UUID           NOT NULL REFERENCES expense_area_budget (id),
    at          TIMESTAMPTZ    NOT NULL,
    actor_id    UUID           NOT NULL,
    old_amount  NUMERIC(14, 2),
    new_amount  NUMERIC(14, 2) NOT NULL
);

CREATE INDEX ix_expense_budget_change_budget ON expense_budget_change (budget_id, at);

-- history rows are final
CREATE FUNCTION expense_history_rows_are_final() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% rows are append-only and cannot be updated or deleted', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_expense_claim_event_final
    BEFORE UPDATE OR DELETE ON expense_claim_event
    FOR EACH ROW EXECUTE FUNCTION expense_history_rows_are_final();

CREATE TRIGGER trg_expense_budget_change_final
    BEFORE UPDATE OR DELETE ON expense_budget_change
    FOR EACH ROW EXECUTE FUNCTION expense_history_rows_are_final();
