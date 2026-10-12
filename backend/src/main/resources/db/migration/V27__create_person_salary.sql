-- Spec 013a amendment (2026-10-11): HLS has no fixed pay scale, so a Manager's or a staff member's salary is their own.
-- A Teacher's stays in teacher_salary_history (spec 005). designation_salary is now only a suggested default.
-- person_salary is append-only, like the other payroll rule tables (the function comes from V26).

CREATE TABLE person_salary (
    id           UUID PRIMARY KEY,
    person_kind  VARCHAR(10)    NOT NULL CHECK (person_kind IN ('MANAGER', 'STAFF')),
    -- a Manager id for MANAGER, a user id for STAFF; no foreign key across modules
    person_id    UUID           NOT NULL,
    amount       NUMERIC(12, 2) NOT NULL CHECK (amount > 0 AND amount <= 9999999.99),
    effective_on DATE           NOT NULL,
    note         VARCHAR(500),
    recorded_by  UUID           NOT NULL,
    recorded_at  TIMESTAMPTZ    NOT NULL
);

CREATE INDEX ix_person_salary_lookup
    ON person_salary (person_kind, person_id, effective_on DESC, recorded_at DESC);

CREATE TRIGGER trg_person_salary_final
    BEFORE UPDATE OR DELETE ON person_salary
    FOR EACH ROW EXECUTE FUNCTION payroll_rule_rows_are_final();

-- A staff member's (Admin, Director, System) joining and exit dates. These are dates, not pay, so the row can be
-- changed; every change is audited.
CREATE TABLE staff_employment (
    user_id      UUID PRIMARY KEY,
    joining_date DATE,
    exit_date    DATE,
    updated_by   UUID        NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    CHECK (exit_date IS NULL OR joining_date IS NULL OR exit_date >= joining_date)
);
