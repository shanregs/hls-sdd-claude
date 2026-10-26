-- Spec 022 School Billing and Receivables: an internal ledger of what each School owes for its Teachers.
-- Version 120 sits above the other parallel specs (V100 MoU approval, V110 training calendar).
-- Ledger rows are append-only (triggers below); the only writes after insert are the write-once cancellation of a
-- monthly due and the write-once closing of an overdue follow-up. No foreign keys to other modules.

CREATE SEQUENCE monthly_due_reference_seq;

CREATE TABLE monthly_due (
    id            UUID PRIMARY KEY,
    reference     VARCHAR(30)    NOT NULL UNIQUE,
    school_id     UUID           NOT NULL,
    month         DATE           NOT NULL,
    raised_on     DATE           NOT NULL,
    due_on        DATE           NOT NULL,
    total         NUMERIC(14, 2) NOT NULL CHECK (total >= 0),
    raised_by     UUID,
    cancelled_at  TIMESTAMPTZ,
    cancelled_by  UUID,
    cancel_reason VARCHAR(300),
    created_at    TIMESTAMPTZ    NOT NULL,
    CONSTRAINT ck_monthly_due_first_of_month CHECK (EXTRACT(DAY FROM month) = 1),
    CONSTRAINT ck_monthly_due_cancel CHECK (
        (cancelled_at IS NULL AND cancel_reason IS NULL) OR (cancelled_at IS NOT NULL AND cancel_reason IS NOT NULL))
);

CREATE UNIQUE INDEX ux_monthly_due_school_month ON monthly_due (school_id, month) WHERE cancelled_at IS NULL;
CREATE INDEX ix_monthly_due_due_on ON monthly_due (due_on);
CREATE INDEX ix_monthly_due_school ON monthly_due (school_id, month);

CREATE TABLE due_line (
    id              UUID PRIMARY KEY,
    due_id          UUID           NOT NULL,
    teacher_id      UUID           NOT NULL,
    position_id     UUID,
    position_number INT,
    contract_id     UUID,
    salary          NUMERIC(14, 2) NOT NULL,
    span_from       DATE           NOT NULL,
    span_to         DATE           NOT NULL,
    worked_days     NUMERIC(5, 1)  NOT NULL,
    month_days      NUMERIC(5, 1)  NOT NULL,
    full_month      BOOLEAN        NOT NULL,
    amount          NUMERIC(14, 2) NOT NULL CHECK (amount >= 0)
);

CREATE INDEX ix_due_line_due ON due_line (due_id);
CREATE INDEX ix_due_line_teacher ON due_line (teacher_id);

CREATE TABLE due_adjustment (
    id          UUID PRIMARY KEY,
    due_id      UUID           NOT NULL,
    amount      NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
    reason      VARCHAR(300)   NOT NULL,
    reverses_id UUID,
    created_by  UUID,
    created_at  TIMESTAMPTZ    NOT NULL
);

CREATE INDEX ix_due_adjustment_due ON due_adjustment (due_id);
CREATE UNIQUE INDEX ux_due_adjustment_reverses ON due_adjustment (reverses_id) WHERE reverses_id IS NOT NULL;

CREATE TABLE school_collection (
    id          UUID PRIMARY KEY,
    school_id   UUID           NOT NULL,
    amount      NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
    received_on DATE           NOT NULL,
    mode        VARCHAR(10)    NOT NULL CHECK (mode IN ('BANK', 'CASH', 'CHEQUE', 'UPI')),
    reference   VARCHAR(80),
    receiver    VARCHAR(120),
    comment     VARCHAR(500),
    reverses_id UUID,
    reason      VARCHAR(300),
    created_by  UUID,
    created_at  TIMESTAMPTZ    NOT NULL
);

CREATE INDEX ix_school_collection_school ON school_collection (school_id, received_on);
CREATE UNIQUE INDEX ux_school_collection_reverses ON school_collection (reverses_id) WHERE reverses_id IS NOT NULL;

CREATE TABLE collection_allocation (
    id            UUID PRIMARY KEY,
    collection_id UUID           NOT NULL,
    due_id        UUID           NOT NULL,
    amount        NUMERIC(14, 2) NOT NULL CHECK (amount <> 0),
    created_by    UUID,
    created_at    TIMESTAMPTZ    NOT NULL
);

CREATE INDEX ix_collection_allocation_due ON collection_allocation (due_id);
CREATE INDEX ix_collection_allocation_collection ON collection_allocation (collection_id);

CREATE TABLE overdue_follow_up (
    id         UUID PRIMARY KEY,
    due_id     UUID        NOT NULL UNIQUE,
    task_id    UUID,
    created_on DATE        NOT NULL,
    closed_on  DATE
);

CREATE FUNCTION receivable_rows_are_final() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% rows are append-only and cannot be updated or deleted', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_due_line_final BEFORE UPDATE OR DELETE ON due_line
    FOR EACH ROW EXECUTE FUNCTION receivable_rows_are_final();
CREATE TRIGGER trg_due_adjustment_final BEFORE UPDATE OR DELETE ON due_adjustment
    FOR EACH ROW EXECUTE FUNCTION receivable_rows_are_final();
CREATE TRIGGER trg_school_collection_final BEFORE UPDATE OR DELETE ON school_collection
    FOR EACH ROW EXECUTE FUNCTION receivable_rows_are_final();
CREATE TRIGGER trg_collection_allocation_final BEFORE UPDATE OR DELETE ON collection_allocation
    FOR EACH ROW EXECUTE FUNCTION receivable_rows_are_final();

-- A monthly due never changes; the only allowed update is the one-time cancellation.
CREATE FUNCTION monthly_due_write_once() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'monthly_due rows cannot be deleted';
    END IF;
    IF OLD.cancelled_at IS NOT NULL THEN
        RAISE EXCEPTION 'a cancelled monthly due cannot be changed';
    END IF;
    IF NEW.id <> OLD.id OR NEW.reference <> OLD.reference OR NEW.school_id <> OLD.school_id OR NEW.month <> OLD.month
        OR NEW.raised_on <> OLD.raised_on OR NEW.due_on <> OLD.due_on OR NEW.total <> OLD.total THEN
        RAISE EXCEPTION 'a raised monthly due cannot be changed';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_monthly_due_write_once BEFORE UPDATE OR DELETE ON monthly_due
    FOR EACH ROW EXECUTE FUNCTION monthly_due_write_once();

CREATE FUNCTION overdue_follow_up_write_once() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'overdue_follow_up rows cannot be deleted';
    END IF;
    IF OLD.closed_on IS NOT NULL OR NEW.due_id <> OLD.due_id OR NEW.created_on <> OLD.created_on THEN
        RAISE EXCEPTION 'an overdue follow-up can only be closed once';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_overdue_follow_up_write_once BEFORE UPDATE OR DELETE ON overdue_follow_up
    FOR EACH ROW EXECUTE FUNCTION overdue_follow_up_write_once();

-- Two billing settings, edited on the School Billing screen (audited through Change History).
CREATE TABLE billing_setting (
    key        VARCHAR(40) PRIMARY KEY,
    value      INT         NOT NULL,
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL
);

-- What each monthly due has had taken off it: active adjustments (not reversed, not a reversal) and net allocations.
CREATE VIEW due_balance AS
SELECT d.id AS due_id,
       d.total,
       COALESCE(adj.amount, 0) AS adjustments,
       COALESCE(al.amount, 0) AS collected,
       d.total - COALESCE(adj.amount, 0) - COALESCE(al.amount, 0) AS balance
FROM monthly_due d
LEFT JOIN (
    SELECT a.due_id, SUM(a.amount) AS amount
    FROM due_adjustment a
    WHERE a.reverses_id IS NULL
      AND NOT EXISTS (SELECT 1 FROM due_adjustment r WHERE r.reverses_id = a.id)
    GROUP BY a.due_id) adj ON adj.due_id = d.id
LEFT JOIN (
    SELECT due_id, SUM(amount) AS amount FROM collection_allocation GROUP BY due_id) al ON al.due_id = d.id;
