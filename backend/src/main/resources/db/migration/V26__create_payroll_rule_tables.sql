-- Spec 013a: salary structures and the pay policy. Both tables are append-only: a wrong entry is fixed by a newer
-- row, never by an update or a delete, and the trigger below makes that true for direct SQL as well (SC-002).
-- No foreign keys cross modules: designation_id and the user ids are plain UUIDs checked through the owning module.

CREATE TABLE designation_salary (
    id             UUID PRIMARY KEY,
    designation_id UUID           NOT NULL,
    amount         NUMERIC(12, 2) NOT NULL CHECK (amount > 0 AND amount <= 9999999.99),
    effective_on   DATE           NOT NULL,
    note           VARCHAR(500),
    recorded_by    UUID           NOT NULL,
    recorded_at    TIMESTAMPTZ    NOT NULL
);

-- The latest row on or before a date is one index lookup; the row recorded last wins a tie on the same date.
CREATE INDEX ix_designation_salary_lookup
    ON designation_salary (designation_id, effective_on DESC, recorded_at DESC);

CREATE TABLE pay_policy (
    id                UUID PRIMARY KEY,
    effective_on      DATE          NOT NULL,
    lop_divisor       VARCHAR(20)   NOT NULL CHECK (lop_divisor IN ('WORKING_DAYS')),
    half_day_fraction NUMERIC(4, 3) NOT NULL CHECK (half_day_fraction > 0 AND half_day_fraction < 1),
    rounding          VARCHAR(20)   NOT NULL CHECK (rounding IN ('NEAREST_RUPEE', 'UP', 'DOWN')),
    pay_month         VARCHAR(20)   NOT NULL CHECK (pay_month IN ('CALENDAR_MONTH')),
    note              VARCHAR(500),
    recorded_by       UUID,
    recorded_at       TIMESTAMPTZ   NOT NULL
);

CREATE INDEX ix_pay_policy_lookup ON pay_policy (effective_on DESC, recorded_at DESC);

CREATE FUNCTION payroll_rule_rows_are_final() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% rows are append-only and cannot be updated or deleted', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_designation_salary_final
    BEFORE UPDATE OR DELETE ON designation_salary
    FOR EACH ROW EXECUTE FUNCTION payroll_rule_rows_are_final();

CREATE TRIGGER trg_pay_policy_final
    BEFORE UPDATE OR DELETE ON pay_policy
    FOR EACH ROW EXECUTE FUNCTION payroll_rule_rows_are_final();

-- Policy version 1: calendar month, working-day divisor, half day = 0.5, rounded to the nearest rupee.
INSERT INTO pay_policy (id, effective_on, lop_divisor, half_day_fraction, rounding, pay_month, note, recorded_by, recorded_at)
VALUES ('00000000-0000-0000-0013-000000000001', DATE '2000-01-01', 'WORKING_DAYS', 0.500, 'NEAREST_RUPEE',
        'CALENDAR_MONTH', 'Standard rules', NULL, now());
