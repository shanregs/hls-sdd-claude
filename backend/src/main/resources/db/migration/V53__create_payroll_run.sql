-- Spec 013b: the payroll run. A run, its lines (each with the working it was computed from), the adjustments carried
-- into runs, the payslips, the manual payment entries and the key that makes "paid once per month" a fact.
-- The triggers below make an approved run immutable for direct SQL as well (SC-003, SC-006, Principle IX).
-- No foreign keys cross modules: user ids and person ids are plain UUIDs checked through the owning module.

CREATE TABLE payroll_run (
    id                 UUID PRIMARY KEY,
    month              DATE          NOT NULL CHECK (month = date_trunc('month', month)::date),
    kind               VARCHAR(15)   NOT NULL CHECK (kind IN ('PRIMARY', 'SUPPLEMENTARY')),
    sequence           INT           NOT NULL CHECK (sequence >= 1),
    status             VARCHAR(20)   NOT NULL CHECK (status IN ('DRAFT', 'AWAITING_APPROVAL', 'APPROVED', 'CANCELLED')),
    compute_state      VARCHAR(10)   NOT NULL CHECK (compute_state IN ('QUEUED', 'RUNNING', 'DONE', 'FAILED')),
    progress_done      INT           NOT NULL DEFAULT 0,
    progress_total     INT           NOT NULL DEFAULT 0,
    heartbeat_at       TIMESTAMPTZ,
    policy             JSONB,
    total_earned       NUMERIC(16, 2) NOT NULL DEFAULT 0,
    total_deduction    NUMERIC(16, 2) NOT NULL DEFAULT 0,
    total_adjustments  NUMERIC(16, 2) NOT NULL DEFAULT 0,
    total_net          NUMERIC(16, 2) NOT NULL DEFAULT 0,
    payable_count      INT           NOT NULL DEFAULT 0,
    cannot_pay_count   INT           NOT NULL DEFAULT 0,
    recompute_summary  JSONB,
    started_by         UUID          NOT NULL,
    started_at         TIMESTAMPTZ   NOT NULL,
    submitted_by       UUID,
    submitted_at       TIMESTAMPTZ,
    approved_by        UUID,
    approved_at        TIMESTAMPTZ,
    cancelled_by       UUID,
    cancelled_at       TIMESTAMPTZ,
    cancel_reason      VARCHAR(500),
    version            BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_payroll_run_sequence UNIQUE (month, kind, sequence),
    -- whoever processed a run cannot approve it (FR-014); a second guard behind the service rule
    CONSTRAINT ck_payroll_run_separation CHECK (approved_by IS NULL OR submitted_by IS NULL OR approved_by <> submitted_by),
    CONSTRAINT ck_payroll_run_approved CHECK (
        status <> 'APPROVED' OR (approved_by IS NOT NULL AND approved_at IS NOT NULL AND submitted_by IS NOT NULL)),
    CONSTRAINT ck_payroll_run_cancelled CHECK (
        status <> 'CANCELLED' OR (cancelled_by IS NOT NULL AND cancelled_at IS NOT NULL AND cancel_reason IS NOT NULL))
);

-- one primary run per month, also under two simultaneous starts (FR-001)
CREATE UNIQUE INDEX uq_payroll_run_primary_month ON payroll_run (month)
    WHERE kind = 'PRIMARY' AND status <> 'CANCELLED';
-- one open supplementary run per month (FR-021)
CREATE UNIQUE INDEX uq_payroll_run_open_supplementary_month ON payroll_run (month)
    WHERE kind = 'SUPPLEMENTARY' AND status IN ('DRAFT', 'AWAITING_APPROVAL');
CREATE INDEX ix_payroll_run_month ON payroll_run (month DESC, kind, sequence);

CREATE TABLE payroll_run_line (
    id            UUID PRIMARY KEY,
    run_id        UUID           NOT NULL REFERENCES payroll_run (id),
    person_kind   VARCHAR(10)    NOT NULL CHECK (person_kind IN ('TEACHER', 'MANAGER', 'STAFF')),
    person_id     UUID           NOT NULL,
    person_name   VARCHAR(200)   NOT NULL,
    person_label  VARCHAR(200),
    state         VARCHAR(12)    NOT NULL CHECK (state IN ('PAYABLE', 'CANNOT_PAY')),
    reason        VARCHAR(500),
    payable_days  INT,
    working_days  NUMERIC(6, 2),
    unpaid_days   NUMERIC(6, 2),
    salary        NUMERIC(14, 2),
    earned        NUMERIC(14, 2),
    deduction     NUMERIC(14, 2),
    adjustments   NUMERIC(14, 2),
    net           NUMERIC(14, 2),
    working       JSONB,
    fingerprint   VARCHAR(64)    NOT NULL,
    warning       VARCHAR(500),
    computed_at   TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uq_payroll_run_line_person UNIQUE (run_id, person_kind, person_id),
    -- a person who cannot be paid has a reason and no amounts, never zero (FR-006, SC-004)
    CONSTRAINT ck_payroll_run_line_cannot CHECK (
        state <> 'CANNOT_PAY'
        OR (reason IS NOT NULL AND earned IS NULL AND deduction IS NULL AND adjustments IS NULL AND net IS NULL)),
    CONSTRAINT ck_payroll_run_line_payable CHECK (
        state <> 'PAYABLE'
        OR (earned IS NOT NULL AND deduction IS NOT NULL AND adjustments IS NOT NULL AND net IS NOT NULL)),
    CONSTRAINT ck_payroll_run_line_net CHECK (net IS NULL OR net = earned - deduction + adjustments)
);

CREATE INDEX ix_payroll_run_line_run_state ON payroll_run_line (run_id, state);
CREATE INDEX ix_payroll_run_line_person ON payroll_run_line (person_kind, person_id);

CREATE TABLE payroll_adjustment (
    id                UUID PRIMARY KEY,
    person_kind       VARCHAR(10)    NOT NULL CHECK (person_kind IN ('TEACHER', 'MANAGER', 'STAFF')),
    person_id         UUID           NOT NULL,
    person_name       VARCHAR(200)   NOT NULL,
    kind              VARCHAR(20)    NOT NULL CHECK (kind IN ('CORRECTION', 'INCENTIVE', 'REIMBURSEMENT', 'SUBSTITUTE_CREDIT')),
    amount            NUMERIC(14, 2) NOT NULL CHECK (amount <> 0),
    reason            VARCHAR(500)   NOT NULL,
    status            VARCHAR(10)    NOT NULL CHECK (status IN ('WAITING', 'APPLIED', 'WITHDRAWN')),
    attached_run_id   UUID REFERENCES payroll_run (id),
    applied_run_id    UUID REFERENCES payroll_run (id),
    applied_line_id   UUID REFERENCES payroll_run_line (id),
    source_ref        VARCHAR(100),
    created_by        UUID           NOT NULL,
    created_at        TIMESTAMPTZ    NOT NULL,
    withdrawn_by      UUID,
    withdrawn_at      TIMESTAMPTZ,
    withdraw_reason   VARCHAR(500),
    CONSTRAINT ck_payroll_adjustment_applied CHECK (
        status <> 'APPLIED' OR (applied_run_id IS NOT NULL AND applied_line_id IS NOT NULL)),
    CONSTRAINT ck_payroll_adjustment_withdrawn CHECK (
        status <> 'WITHDRAWN' OR (withdrawn_by IS NOT NULL AND withdrawn_at IS NOT NULL AND withdraw_reason IS NOT NULL))
);

CREATE INDEX ix_payroll_adjustment_person ON payroll_adjustment (person_kind, person_id, status);
CREATE INDEX ix_payroll_adjustment_attached ON payroll_adjustment (attached_run_id) WHERE attached_run_id IS NOT NULL;

-- the frozen copy of an adjustment inside a line
CREATE TABLE payroll_run_line_adjustment (
    id             UUID PRIMARY KEY,
    line_id        UUID           NOT NULL REFERENCES payroll_run_line (id),
    adjustment_id  UUID           NOT NULL REFERENCES payroll_adjustment (id),
    kind           VARCHAR(20)    NOT NULL,
    amount         NUMERIC(14, 2) NOT NULL,
    reason         VARCHAR(500)   NOT NULL,
    CONSTRAINT uq_payroll_run_line_adjustment UNIQUE (line_id, adjustment_id)
);

CREATE TABLE payroll_run_event (
    id        UUID PRIMARY KEY,
    run_id    UUID         NOT NULL REFERENCES payroll_run (id),
    at        TIMESTAMPTZ  NOT NULL,
    actor     UUID         NOT NULL,
    kind      VARCHAR(30)  NOT NULL,
    reason    VARCHAR(500),
    detail    JSONB
);

CREATE INDEX ix_payroll_run_event_run ON payroll_run_event (run_id, at);

CREATE TABLE payroll_payslip (
    id         UUID PRIMARY KEY,
    line_id    UUID        NOT NULL UNIQUE REFERENCES payroll_run_line (id),
    run_id     UUID        NOT NULL REFERENCES payroll_run (id),
    file_id    UUID        NOT NULL,
    issued_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_payroll_payslip_run ON payroll_payslip (run_id);

-- append-only: the current state of a line is its newest entry
CREATE TABLE payroll_payment_entry (
    id          UUID PRIMARY KEY,
    line_id     UUID         NOT NULL REFERENCES payroll_run_line (id),
    paid        BOOLEAN      NOT NULL,
    paid_on     DATE,
    mode        VARCHAR(15)  CHECK (mode IN ('BANK_TRANSFER', 'CASH', 'UPI', 'CHEQUE', 'OTHER')),
    reference   VARCHAR(100),
    entered_by  UUID         NOT NULL,
    entered_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_payroll_payment_paid CHECK (NOT paid OR (paid_on IS NOT NULL AND mode IS NOT NULL))
);

CREATE INDEX ix_payroll_payment_entry_line ON payroll_payment_entry (line_id, entered_at DESC);

-- a person is paid for a month once: written at approval, in the same transaction as the status change (SC-007)
CREATE TABLE payroll_paid_month (
    month        DATE        NOT NULL,
    person_kind  VARCHAR(10) NOT NULL CHECK (person_kind IN ('TEACHER', 'MANAGER', 'STAFF')),
    person_id    UUID        NOT NULL,
    run_id       UUID        NOT NULL REFERENCES payroll_run (id),
    line_id      UUID        NOT NULL REFERENCES payroll_run_line (id),
    PRIMARY KEY (month, person_kind, person_id)
);

-- ------------------------------------------------------------------ immutability

CREATE FUNCTION payroll_rows_are_final() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% rows are append-only and cannot be updated or deleted', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_payroll_run_event_final
    BEFORE UPDATE OR DELETE ON payroll_run_event
    FOR EACH ROW EXECUTE FUNCTION payroll_rows_are_final();

CREATE TRIGGER trg_payroll_payslip_final
    BEFORE UPDATE OR DELETE ON payroll_payslip
    FOR EACH ROW EXECUTE FUNCTION payroll_rows_are_final();

CREATE TRIGGER trg_payroll_payment_entry_final
    BEFORE UPDATE OR DELETE ON payroll_payment_entry
    FOR EACH ROW EXECUTE FUNCTION payroll_rows_are_final();

CREATE TRIGGER trg_payroll_paid_month_final
    BEFORE UPDATE OR DELETE ON payroll_paid_month
    FOR EACH ROW EXECUTE FUNCTION payroll_rows_are_final();

-- the lines of an approved run never change
CREATE FUNCTION payroll_line_of_approved_run_is_final() RETURNS trigger AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM payroll_run WHERE id = OLD.run_id AND status = 'APPROVED') THEN
        RAISE EXCEPTION 'The lines of an approved payroll run cannot be updated or deleted';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_payroll_run_line_final
    BEFORE UPDATE OR DELETE ON payroll_run_line
    FOR EACH ROW EXECUTE FUNCTION payroll_line_of_approved_run_is_final();

CREATE FUNCTION payroll_line_adjustment_of_approved_run_is_final() RETURNS trigger AS $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM payroll_run_line l JOIN payroll_run r ON r.id = l.run_id
        WHERE l.id = OLD.line_id AND r.status = 'APPROVED') THEN
        RAISE EXCEPTION 'The adjustment lines of an approved payroll run cannot be updated or deleted';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_payroll_run_line_adjustment_final
    BEFORE UPDATE OR DELETE ON payroll_run_line_adjustment
    FOR EACH ROW EXECUTE FUNCTION payroll_line_adjustment_of_approved_run_is_final();

-- an approved run does not change at all; only a draft or a cancelled run may ever be deleted
CREATE FUNCTION payroll_run_guard() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.status NOT IN ('DRAFT', 'CANCELLED') THEN
            RAISE EXCEPTION 'A payroll run that is % cannot be deleted', OLD.status;
        END IF;
        RETURN OLD;
    END IF;
    IF OLD.status = 'APPROVED' THEN
        RAISE EXCEPTION 'An approved payroll run cannot be changed';
    END IF;
    IF OLD.status = 'CANCELLED' THEN
        RAISE EXCEPTION 'A cancelled payroll run cannot be changed';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_payroll_run_guard
    BEFORE UPDATE OR DELETE ON payroll_run
    FOR EACH ROW EXECUTE FUNCTION payroll_run_guard();

-- an adjustment is never deleted, and once applied or withdrawn it never changes
CREATE FUNCTION payroll_adjustment_guard() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Payroll adjustments cannot be deleted';
    END IF;
    IF OLD.status IN ('APPLIED', 'WITHDRAWN') THEN
        RAISE EXCEPTION 'A payroll adjustment that is % cannot be changed', OLD.status;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_payroll_adjustment_guard
    BEFORE UPDATE OR DELETE ON payroll_adjustment
    FOR EACH ROW EXECUTE FUNCTION payroll_adjustment_guard();
