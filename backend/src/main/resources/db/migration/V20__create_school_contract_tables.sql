-- School contracts (spec 012): the MoU between HLS and a School, its Teacher positions and signatories, and
-- the dated Teacher assignments that replace the interim teacher_placement of spec 005.
-- school_id, teacher_id and user ids are plain ids validated through the owning modules' public APIs (no
-- cross-module foreign keys); foreign keys inside this module are allowed. btree_gist comes from V13.

CREATE TABLE contract (
    id            UUID PRIMARY KEY,
    school_id     UUID          NOT NULL,
    -- RATE_PENDING is shown as "MoU pending": a School whose placements were carried over before an MoU was recorded.
    state         VARCHAR(12)   NOT NULL CHECK (state IN ('RATE_PENDING', 'ACTIVE', 'CANCELLED')),
    salary_mode   VARCHAR(12)   CHECK (salary_mode IN ('SAME_FOR_ALL', 'PER_TEACHER')),
    teacher_count INT           CHECK (teacher_count BETWEEN 1 AND 500),
    rate          NUMERIC(12,2),
    signed_on     DATE,
    cycle         VARCHAR(10)   NOT NULL DEFAULT 'MONTHLY' CHECK (cycle = 'MONTHLY'),
    starts_on     DATE          NOT NULL,
    ends_on       DATE,
    version       BIGINT        NOT NULL DEFAULT 0,
    created_by    UUID,
    created_at    TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_contract_dates CHECK (ends_on IS NULL OR ends_on >= starts_on),
    -- a pending contract carries no MoU details; any other live contract carries all of them
    CONSTRAINT ck_contract_mou CHECK (
        state = 'CANCELLED'
        OR (state = 'RATE_PENDING' AND salary_mode IS NULL AND teacher_count IS NULL
            AND rate IS NULL AND signed_on IS NULL)
        OR (state = 'ACTIVE' AND salary_mode IS NOT NULL AND teacher_count IS NOT NULL
            AND signed_on IS NOT NULL)),
    CONSTRAINT ck_contract_rate CHECK (
        salary_mode IS NULL
        OR (salary_mode = 'SAME_FOR_ALL' AND rate IS NOT NULL AND rate > 0)
        OR (salary_mode = 'PER_TEACHER' AND rate IS NULL)),
    -- one School never has two overlapping live contracts
    CONSTRAINT ex_contract_school_dates EXCLUDE USING gist (
        school_id WITH =,
        daterange(starts_on, coalesce(ends_on, 'infinity'::date), '[]') WITH &&
    ) WHERE (state <> 'CANCELLED')
);

CREATE INDEX idx_contract_school ON contract (school_id);

CREATE TABLE contract_position (
    id          UUID PRIMARY KEY,
    contract_id UUID          NOT NULL REFERENCES contract (id),
    number      INT           NOT NULL CHECK (number >= 1),
    title       VARCHAR(80),
    salary      NUMERIC(12,2) NOT NULL CHECK (salary > 0),
    CONSTRAINT uq_contract_position_number UNIQUE (contract_id, number)
);

CREATE TABLE contract_signatory (
    id          UUID PRIMARY KEY,
    contract_id UUID         NOT NULL REFERENCES contract (id),
    party       VARCHAR(6)   NOT NULL CHECK (party IN ('SCHOOL', 'HLS')),
    name        VARCHAR(120) NOT NULL CHECK (length(trim(name)) > 0),
    designation VARCHAR(120) NOT NULL CHECK (length(trim(designation)) > 0),
    user_id     UUID
);

CREATE INDEX idx_contract_signatory_contract ON contract_signatory (contract_id);

-- Positions and signatories are part of the signed contract: written once, never changed or removed.
CREATE FUNCTION contract_signed_rows_are_final() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% rows of a signed contract cannot be updated or deleted', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_contract_position_final
    BEFORE UPDATE OR DELETE ON contract_position
    FOR EACH ROW EXECUTE FUNCTION contract_signed_rows_are_final();

CREATE TRIGGER trg_contract_signatory_final
    BEFORE UPDATE OR DELETE ON contract_signatory
    FOR EACH ROW EXECUTE FUNCTION contract_signed_rows_are_final();

CREATE TABLE contract_assignment (
    id          UUID PRIMARY KEY,
    teacher_id  UUID        NOT NULL,
    school_id   UUID        NOT NULL,
    -- null only for carried-over assignments (and those made under a pending contract) not yet mapped
    position_id UUID        REFERENCES contract_position (id),
    starts_on   DATE        NOT NULL,
    ends_on     DATE,
    status      VARCHAR(12) NOT NULL CHECK (status IN ('ACTIVE', 'CANCELLED', 'CORRECTED')),
    created_by  UUID,
    created_at  TIMESTAMPTZ NOT NULL,
    CHECK (ends_on IS NULL OR ends_on >= starts_on),
    -- one Teacher is never assigned twice on the same date
    CONSTRAINT ex_assignment_teacher_dates EXCLUDE USING gist (
        teacher_id WITH =,
        daterange(starts_on, coalesce(ends_on, 'infinity'::date), '[]') WITH &&
    ) WHERE (status = 'ACTIVE'),
    -- one position is never filled by two Teachers on the same date
    CONSTRAINT ex_assignment_position_dates EXCLUDE USING gist (
        position_id WITH =,
        daterange(starts_on, coalesce(ends_on, 'infinity'::date), '[]') WITH &&
    ) WHERE (status = 'ACTIVE' AND position_id IS NOT NULL)
);

CREATE INDEX idx_contract_assignment_school ON contract_assignment (school_id);
CREATE INDEX idx_contract_assignment_teacher ON contract_assignment (teacher_id);
CREATE INDEX idx_contract_assignment_position ON contract_assignment (position_id);

-- Carry over the interim placements of spec 005: one "MoU pending" contract per School that has live
-- placements (from its earliest start, open-ended), and every placement (all statuses, same id) as an
-- assignment with no position.
INSERT INTO contract (id, school_id, state, cycle, starts_on, ends_on, version, created_at)
SELECT gen_random_uuid(), school_id, 'RATE_PENDING', 'MONTHLY', MIN(starts_on), NULL, 0, now()
FROM teacher_placement
WHERE status = 'ACTIVE'
GROUP BY school_id;

INSERT INTO contract_assignment (id, teacher_id, school_id, position_id, starts_on, ends_on, status, created_by, created_at)
SELECT id, teacher_id, school_id, NULL, starts_on, ends_on, status, NULL, created_at
FROM teacher_placement;
