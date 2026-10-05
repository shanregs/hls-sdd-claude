-- Campus recruitment, job offers and induction (spec 016). recruitment and training own their tables; teacher_id,
-- user ids and (for training) nothing else cross a module boundary as plain ids. Foreign keys inside a module are
-- allowed. btree_gist comes from V13. Phones are stored normalized (digits only, last 10) in phone_key.

CREATE TABLE college (
    id             UUID PRIMARY KEY,
    name           VARCHAR(160) NOT NULL CHECK (length(trim(name)) > 0),
    city           VARCHAR(120) NOT NULL CHECK (length(trim(city)) > 0),
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    version        BIGINT       NOT NULL DEFAULT 0,
    created_by     UUID,
    created_at     TIMESTAMPTZ  NOT NULL
);

CREATE UNIQUE INDEX uq_college_name_city ON college (lower(name), lower(city));

-- A college keeps at most one contact of each role: the placement officer and the principal.
CREATE TABLE college_contact (
    college_id UUID         NOT NULL REFERENCES college (id),
    role       VARCHAR(16)  NOT NULL CHECK (role IN ('PLACEMENT_OFFICER', 'PRINCIPAL')),
    name       VARCHAR(160) NOT NULL CHECK (length(trim(name)) > 0),
    phone      VARCHAR(20),
    email      VARCHAR(200),
    PRIMARY KEY (college_id, role)
);

CREATE TABLE campus_drive (
    id            UUID PRIMARY KEY,
    college_id    UUID         NOT NULL REFERENCES college (id),
    season_label  VARCHAR(40),
    venue         VARCHAR(200),
    status        VARCHAR(10)  NOT NULL CHECK (status IN ('PLANNED', 'HELD', 'CANCELLED')),
    cancel_reason VARCHAR(300),
    scheduled_by  UUID         NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_campus_drive_cancel CHECK (status <> 'CANCELLED' OR length(trim(coalesce(cancel_reason, ''))) > 0)
);

CREATE INDEX idx_campus_drive_college ON campus_drive (college_id);

CREATE TABLE campus_drive_date (
    drive_id   UUID NOT NULL REFERENCES campus_drive (id),
    drive_date DATE NOT NULL,
    PRIMARY KEY (drive_id, drive_date)
);

CREATE INDEX idx_campus_drive_date ON campus_drive_date (drive_date);

CREATE TABLE campus_drive_interviewer (
    drive_id UUID NOT NULL REFERENCES campus_drive (id),
    user_id  UUID NOT NULL,
    PRIMARY KEY (drive_id, user_id)
);

CREATE TABLE candidate (
    id          UUID PRIMARY KEY,
    drive_id    UUID         NOT NULL REFERENCES campus_drive (id),
    name        VARCHAR(160) NOT NULL CHECK (length(trim(name)) > 0),
    phone       VARCHAR(20)  NOT NULL,
    phone_key   VARCHAR(10)  NOT NULL,
    email       VARCHAR(200),
    degree      VARCHAR(120),
    study_year  VARCHAR(40),
    notes       VARCHAR(500),
    outcome     VARCHAR(10)  CHECK (outcome IN ('SELECTED', 'WAITLISTED', 'REJECTED')),
    outcome_by  UUID,
    outcome_at  TIMESTAMPTZ,
    teacher_id  UUID,
    created_by  UUID,
    created_at  TIMESTAMPTZ  NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_candidate_drive_phone UNIQUE (drive_id, phone_key)
);

CREATE INDEX idx_candidate_phone_key ON candidate (phone_key);
CREATE INDEX idx_candidate_outcome ON candidate (outcome);

CREATE TABLE candidate_outcome_history (
    id           UUID PRIMARY KEY,
    candidate_id UUID         NOT NULL REFERENCES candidate (id),
    outcome      VARCHAR(10)  NOT NULL CHECK (outcome IN ('SELECTED', 'WAITLISTED', 'REJECTED')),
    note         VARCHAR(300),
    changed_by   UUID         NOT NULL,
    changed_at   TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_candidate_outcome_history_candidate ON candidate_outcome_history (candidate_id, changed_at);

CREATE TABLE assessment_score (
    id            UUID PRIMARY KEY,
    candidate_id  UUID        NOT NULL REFERENCES candidate (id),
    assessment_no INT         NOT NULL CHECK (assessment_no >= 1),
    criterion     VARCHAR(14) NOT NULL CHECK (criterion IN ('SPEAKING', 'ENGLISH', 'COMMUNICATION')),
    score         SMALLINT    NOT NULL CHECK (score BETWEEN 1 AND 5),
    remarks       VARCHAR(300),
    assessed_by   UUID        NOT NULL,
    assessed_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_assessment_score UNIQUE (candidate_id, assessment_no, criterion)
);

CREATE TABLE job_offer (
    id               UUID PRIMARY KEY,
    candidate_id     UUID          NOT NULL REFERENCES candidate (id),
    role             VARCHAR(60)   NOT NULL CHECK (length(trim(role)) > 0),
    -- the candidate's normalized phone, so one person never has two open offers even across two drives
    phone_key        VARCHAR(10)   NOT NULL,
    monthly_salary   NUMERIC(12,2) NOT NULL CHECK (monthly_salary > 0),
    allowances       VARCHAR(300),
    terms            VARCHAR(1000),
    expected_joining DATE,
    offer_date       DATE          NOT NULL,
    response_deadline DATE         NOT NULL,
    status           VARCHAR(10)   NOT NULL CHECK (status IN ('DRAFT', 'ISSUED', 'ACCEPTED', 'DECLINED', 'EXPIRED', 'SUPERSEDED')),
    supersedes_id    UUID,
    decline_reason   VARCHAR(300),
    issued_by        UUID,
    issued_at        TIMESTAMPTZ,
    decided_by       UUID,
    decided_at       TIMESTAMPTZ,
    teacher_id       UUID,
    version          BIGINT        NOT NULL DEFAULT 0,
    created_by       UUID,
    created_at       TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_job_offer_deadline CHECK (response_deadline >= offer_date)
);

CREATE UNIQUE INDEX uq_job_offer_open ON job_offer (phone_key) WHERE status IN ('DRAFT', 'ISSUED');
CREATE UNIQUE INDEX uq_job_offer_accepted ON job_offer (phone_key) WHERE status = 'ACCEPTED';
CREATE INDEX idx_job_offer_status_deadline ON job_offer (status, response_deadline);
CREATE INDEX idx_job_offer_candidate ON job_offer (candidate_id);

-- An issued offer is a document the person has seen: its terms never change (a new offer supersedes it).
CREATE FUNCTION job_offer_terms_locked() RETURNS trigger AS $$
BEGIN
    IF OLD.status <> 'DRAFT' AND (
        NEW.role IS DISTINCT FROM OLD.role
        OR NEW.monthly_salary IS DISTINCT FROM OLD.monthly_salary
        OR NEW.allowances IS DISTINCT FROM OLD.allowances
        OR NEW.terms IS DISTINCT FROM OLD.terms
        OR NEW.expected_joining IS DISTINCT FROM OLD.expected_joining
        OR NEW.offer_date IS DISTINCT FROM OLD.offer_date
        OR NEW.response_deadline IS DISTINCT FROM OLD.response_deadline) THEN
        RAISE EXCEPTION 'The terms of an issued offer cannot be changed';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_job_offer_terms_locked BEFORE UPDATE ON job_offer
    FOR EACH ROW EXECUTE FUNCTION job_offer_terms_locked();

CREATE TABLE induction_batch (
    id          UUID PRIMARY KEY,
    name        VARCHAR(120) NOT NULL CHECK (length(trim(name)) > 0),
    starts_on   DATE         NOT NULL,
    ends_on     DATE         NOT NULL,
    trainer     VARCHAR(160),
    venue_type  VARCHAR(8)   NOT NULL CHECK (venue_type IN ('PHYSICAL', 'VIRTUAL')),
    venue       VARCHAR(200),
    seat_limit  INT          NOT NULL CHECK (seat_limit >= 1),
    status      VARCHAR(10)  NOT NULL CHECK (status IN ('PLANNED', 'RUNNING', 'COMPLETED', 'CANCELLED')),
    version     BIGINT       NOT NULL DEFAULT 0,
    created_by  UUID,
    created_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_induction_batch_dates CHECK (ends_on >= starts_on)
);

CREATE TABLE induction_enrolment (
    id          UUID PRIMARY KEY,
    batch_id    UUID         NOT NULL REFERENCES induction_batch (id),
    teacher_id  UUID         NOT NULL,
    starts_on   DATE         NOT NULL,
    ends_on     DATE         NOT NULL,
    enrolled_by UUID         NOT NULL,
    enrolled_at TIMESTAMPTZ  NOT NULL,
    result      VARCHAR(14)  CHECK (result IN ('COMPLETED', 'NOT_COMPLETED')),
    remarks     VARCHAR(300),
    signed_by   UUID,
    signed_at   TIMESTAMPTZ,
    follow_up   VARCHAR(10)  CHECK (follow_up IN ('NEXT_BATCH', 'RELEASED')),
    version     BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_induction_enrolment UNIQUE (batch_id, teacher_id),
    CONSTRAINT ck_induction_enrolment_dates CHECK (ends_on >= starts_on),
    -- a Teacher is never in two batches on overlapping dates (a not-completed attempt is over and does not block)
    CONSTRAINT ex_induction_enrolment_overlap EXCLUDE USING gist (
        teacher_id WITH =,
        daterange(starts_on, ends_on, '[]') WITH &&
    ) WHERE (result IS NULL OR result = 'COMPLETED')
);

CREATE INDEX idx_induction_enrolment_teacher ON induction_enrolment (teacher_id);

CREATE TABLE induction_absence (
    id           UUID PRIMARY KEY,
    enrolment_id UUID         NOT NULL REFERENCES induction_enrolment (id),
    absent_on    DATE         NOT NULL,
    reason       VARCHAR(300) NOT NULL CHECK (length(trim(reason)) > 0),
    recorded_by  UUID         NOT NULL,
    recorded_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_induction_absence UNIQUE (enrolment_id, absent_on)
);

-- Amendment A5 to spec 008: an induction day is marked before the Teacher has a School. Only the training status
-- code may carry no School; a CHECK cannot look at another table, so a trigger enforces it.
ALTER TABLE attendance_mark ALTER COLUMN school_id DROP NOT NULL;

CREATE FUNCTION attendance_mark_school_required() RETURNS trigger AS $$
BEGIN
    IF NEW.school_id IS NULL AND NOT EXISTS (
        SELECT 1 FROM attendance_status_code c WHERE c.id = NEW.status_code_id AND c.category = 'TRAINING') THEN
        RAISE EXCEPTION 'An attendance mark needs a School unless it is a training day';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_attendance_mark_school_required BEFORE INSERT OR UPDATE ON attendance_mark
    FOR EACH ROW EXECUTE FUNCTION attendance_mark_school_required();

-- Amendment A8 to spec 005 (owned by the school module): a School also keeps a principal and an accountant contact.
CREATE TABLE school_contact (
    school_id UUID         NOT NULL REFERENCES school (id),
    role      VARCHAR(12)  NOT NULL CHECK (role IN ('PRINCIPAL', 'ACCOUNTANT')),
    name      VARCHAR(160) NOT NULL CHECK (length(trim(name)) > 0),
    phone     VARCHAR(20),
    email     VARCHAR(200),
    PRIMARY KEY (school_id, role)
);
