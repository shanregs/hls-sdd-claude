-- Teacher module (spec 005): Teachers, their dated interim School placements, and the append-only
-- salary history (used from US9). school_id and user_id are plain ids validated through the owning
-- modules' public APIs (no cross-module foreign keys).
--
-- btree_gist is needed for the placement exclusion constraint below. If the database user cannot
-- create extensions, a privileged user must run `CREATE EXTENSION btree_gist` once before this
-- migration (see specs/005-master-data/research.md section 6). The service-level overlap check is
-- the primary rule; this constraint is a backstop.
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE teacher (
    id                  UUID PRIMARY KEY,
    name                VARCHAR(160) NOT NULL,
    phone               VARCHAR(20),
    email               VARCHAR(200),
    address             TEXT,
    status              VARCHAR(20)  NOT NULL,
    status_effective_on DATE         NOT NULL,
    user_id             UUID UNIQUE,
    version             BIGINT       NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_teacher_status ON teacher (status);
CREATE INDEX idx_teacher_name_lower ON teacher (lower(name));

CREATE TABLE teacher_placement (
    id          UUID PRIMARY KEY,
    teacher_id  UUID        NOT NULL REFERENCES teacher (id),
    school_id   UUID        NOT NULL,
    starts_on   DATE        NOT NULL,
    ends_on     DATE,
    status      VARCHAR(12) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    CHECK (ends_on IS NULL OR ends_on >= starts_on),
    EXCLUDE USING gist (
        teacher_id WITH =,
        daterange(starts_on, coalesce(ends_on, 'infinity'::date), '[]') WITH &&
    ) WHERE (status = 'ACTIVE')
);

CREATE INDEX idx_teacher_placement_school ON teacher_placement (school_id);
CREATE INDEX idx_teacher_placement_teacher ON teacher_placement (teacher_id);

CREATE TABLE teacher_salary_history (
    id           UUID PRIMARY KEY,
    teacher_id   UUID          NOT NULL REFERENCES teacher (id),
    amount       NUMERIC(12,2) NOT NULL CHECK (amount >= 0),
    effective_on DATE          NOT NULL,
    recorded_by  UUID          NOT NULL,
    created_at   TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_teacher_salary_teacher_effective ON teacher_salary_history (teacher_id, effective_on DESC);
