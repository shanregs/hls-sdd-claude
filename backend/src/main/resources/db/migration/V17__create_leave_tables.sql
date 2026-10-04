-- Leave management (spec 009): leave types, leave requests, and the link from an attendance mark to
-- the leave request that made it. btree_gist was created by V13 (placement exclusion constraint).

CREATE TABLE leave_type (
    id         UUID PRIMARY KEY,
    code       VARCHAR(20) NOT NULL UNIQUE,
    name       VARCHAR(60) NOT NULL,
    sort_order INT         NOT NULL,
    active     BOOLEAN     NOT NULL DEFAULT TRUE
);

INSERT INTO leave_type (id, code, name, sort_order, active) VALUES
    ('00000000-0000-0000-0009-000000000001', 'CASUAL',   'Casual',   1, TRUE),
    ('00000000-0000-0000-0009-000000000002', 'SICK',     'Sick',     2, TRUE),
    ('00000000-0000-0000-0009-000000000003', 'PERSONAL', 'Personal', 3, TRUE),
    ('00000000-0000-0000-0009-000000000004', 'OTHER',    'Other',    4, TRUE);

CREATE TABLE leave_request (
    id                 UUID PRIMARY KEY,
    teacher_id         UUID         NOT NULL,
    school_id          UUID         NOT NULL,
    leave_type_id      UUID         NOT NULL REFERENCES leave_type (id),
    first_date         DATE         NOT NULL,
    last_date          DATE         NOT NULL,
    half_day_start     BOOLEAN      NOT NULL DEFAULT FALSE,
    half_day_end       BOOLEAN      NOT NULL DEFAULT FALSE,
    working_days       NUMERIC(5,2) NOT NULL,
    reason             VARCHAR(500) NOT NULL,
    status             VARCHAR(12)  NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    decided_by_user_id UUID,
    decided_at         TIMESTAMPTZ,
    decision_note      VARCHAR(500),
    cancelled_by_kind  VARCHAR(12) CHECK (cancelled_by_kind IN ('TEACHER', 'SUPERVISOR')),
    created_by_user_id UUID         NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL,
    version            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_leave_request_dates CHECK (last_date >= first_date AND last_date - first_date <= 89),
    CONSTRAINT ck_leave_request_reject_reason CHECK (status <> 'REJECTED' OR decision_note IS NOT NULL),
    -- One live (pending or approved) request per Teacher per date.
    CONSTRAINT ex_leave_request_no_overlap EXCLUDE USING gist (
        teacher_id WITH =,
        daterange(first_date, last_date, '[]') WITH &&
    ) WHERE (status IN ('PENDING', 'APPROVED'))
);

CREATE INDEX idx_leave_request_status_first ON leave_request (status, first_date);
CREATE INDEX idx_leave_request_teacher_first ON leave_request (teacher_id, first_date DESC);

ALTER TABLE attendance_mark ADD COLUMN leave_request_id UUID;
CREATE INDEX idx_attendance_mark_leave_request ON attendance_mark (leave_request_id) WHERE leave_request_id IS NOT NULL;
ALTER TABLE attendance_mark_history ADD COLUMN leave_request_id UUID;
