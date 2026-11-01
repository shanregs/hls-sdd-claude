-- Substitute Teachers are paid by the day (spec 017b). A Teacher now has an engagement type, and each designation has an
-- effective-dated, append-only rupees-per-full-day rate. Ids of other modules are plain ids.

ALTER TABLE teacher
    ADD COLUMN engagement VARCHAR(10) NOT NULL DEFAULT 'REGULAR'
        CONSTRAINT ck_teacher_engagement CHECK (engagement IN ('REGULAR', 'SUBSTITUTE'));

CREATE TABLE designation_day_rate (
    id             UUID PRIMARY KEY,
    seq            BIGINT GENERATED ALWAYS AS IDENTITY,
    designation_id UUID          NOT NULL,
    amount         NUMERIC(10, 2) NOT NULL CHECK (amount > 0),
    effective_on   DATE          NOT NULL,
    note           VARCHAR(500),
    recorded_by    UUID          NOT NULL,
    recorded_at    TIMESTAMPTZ   NOT NULL
);

-- The rate on a date is one index lookup: latest effective date, ties to the highest sequence.
CREATE INDEX ix_designation_day_rate_lookup ON designation_day_rate (designation_id, effective_on DESC, seq DESC);

CREATE TRIGGER trg_designation_day_rate_final
    BEFORE UPDATE OR DELETE ON designation_day_rate
    FOR EACH ROW EXECUTE FUNCTION payroll_rule_rows_are_final();
