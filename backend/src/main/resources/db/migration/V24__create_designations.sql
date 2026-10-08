-- Designations and employment details (spec 005a, amendment A1 to spec 005).
-- designation_id columns are plain ids checked through designation.api (no cross-module foreign keys);
-- the foreign key from manager_designation to manager stays inside the organization module.

CREATE TABLE designation (
    id          UUID PRIMARY KEY,
    name        VARCHAR(80)  NOT NULL CHECK (length(btrim(name)) > 0),
    kind        VARCHAR(8)   NOT NULL CHECK (kind IN ('TEACHER', 'MANAGER')),
    retired     BOOLEAN      NOT NULL DEFAULT FALSE,
    held_ever   BOOLEAN      NOT NULL DEFAULT FALSE,
    version     BIGINT       NOT NULL DEFAULT 0,
    created_by  UUID         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL
);

-- same name allowed for different kinds; same kind refused ignoring capitals and extra spaces (FR-002)
CREATE UNIQUE INDEX uq_designation_kind_name
    ON designation (kind, lower(regexp_replace(btrim(name), '\s+', ' ', 'g')));

-- One employee id per person, unique across Managers and Teachers together (FR-007). The primary key is the
-- trimmed lower-case id, so two simultaneous claims cannot both succeed.
CREATE TABLE employee_id_claim (
    employee_key  VARCHAR(20)  PRIMARY KEY,
    person_kind   VARCHAR(8)   NOT NULL CHECK (person_kind IN ('TEACHER', 'MANAGER')),
    person_id     UUID         NOT NULL UNIQUE,
    employee_id   VARCHAR(20)  NOT NULL,
    person_name   VARCHAR(200) NOT NULL,
    claimed_at    TIMESTAMPTZ  NOT NULL
);

ALTER TABLE manager
    ADD COLUMN employee_id  VARCHAR(20),
    ADD COLUMN joining_date DATE,
    ADD COLUMN exit_date    DATE,
    ADD CONSTRAINT ck_manager_exit_after_joining
        CHECK (exit_date IS NULL OR joining_date IS NULL OR exit_date >= joining_date);

-- A Manager's designation history: append-only, the designation on a date is the row with the greatest
-- (effective_on, seq) that has effective_on <= the date.
CREATE TABLE manager_designation (
    id              UUID PRIMARY KEY,
    seq             BIGINT GENERATED ALWAYS AS IDENTITY,
    manager_id      UUID        NOT NULL REFERENCES manager (id),
    designation_id  UUID        NOT NULL,
    effective_on    DATE        NOT NULL,
    recorded_by     UUID        NOT NULL,
    recorded_at     TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_manager_designation_lookup ON manager_designation (manager_id, effective_on, seq);
CREATE INDEX ix_manager_designation_designation ON manager_designation (designation_id);

CREATE FUNCTION manager_designation_is_final() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'manager_designation rows are never changed or deleted';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_manager_designation_final
    BEFORE UPDATE OR DELETE ON manager_designation
    FOR EACH ROW EXECUTE FUNCTION manager_designation_is_final();

ALTER TABLE teacher
    ADD COLUMN designation_id UUID,
    ADD COLUMN employee_id    VARCHAR(20);

CREATE INDEX ix_teacher_designation ON teacher (designation_id);
