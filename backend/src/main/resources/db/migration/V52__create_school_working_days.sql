-- Spec 037: School monthly working days. One figure per School and calendar month: the working days that School counts
-- in that month. No foreign keys cross modules: school_id and user ids are plain UUIDs checked through school.api and
-- the JWT. Year and month are INTEGER so the schema validation of Hibernate accepts them as int.

CREATE TABLE school_working_days (
    id           UUID PRIMARY KEY,
    school_id    UUID          NOT NULL,
    year         INTEGER       NOT NULL CHECK (year BETWEEN 1900 AND 2200),
    month        INTEGER       NOT NULL CHECK (month BETWEEN 1 AND 12),
    working_days NUMERIC(3, 1) NOT NULL CHECK (working_days BETWEEN 0 AND 31 AND working_days * 2 = floor(working_days * 2)),
    version      BIGINT        NOT NULL DEFAULT 0,
    created_by   UUID          NOT NULL,
    created_at   TIMESTAMPTZ   NOT NULL,
    updated_by   UUID          NOT NULL,
    updated_at   TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_school_working_days UNIQUE (school_id, year, month)
);

CREATE INDEX ix_school_working_days_period ON school_working_days (year, month);

-- Every create, edit, delete and copied row, kept for ever. Keyed by School, year and month (not by row id) so the
-- history of a deleted row stays readable. A trigger refuses any update or delete.
CREATE TABLE school_working_days_history (
    id          UUID PRIMARY KEY,
    school_id   UUID          NOT NULL,
    year        INTEGER       NOT NULL,
    month       INTEGER       NOT NULL,
    action      VARCHAR(10)   NOT NULL CHECK (action IN ('CREATE', 'EDIT', 'DELETE', 'COPY')),
    old_value   NUMERIC(3, 1),
    new_value   NUMERIC(3, 1),
    actor_id    UUID          NOT NULL,
    actor_roles VARCHAR(100)  NOT NULL,
    occurred_at TIMESTAMPTZ   NOT NULL
);

CREATE INDEX ix_swd_history_period ON school_working_days_history (school_id, year, month, occurred_at DESC);
CREATE INDEX ix_swd_history_action ON school_working_days_history (school_id, year, action);

CREATE FUNCTION school_working_days_history_is_final() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% rows are append-only and cannot be updated or deleted', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_school_working_days_history_final
    BEFORE UPDATE OR DELETE ON school_working_days_history
    FOR EACH ROW EXECUTE FUNCTION school_working_days_history_is_final();
