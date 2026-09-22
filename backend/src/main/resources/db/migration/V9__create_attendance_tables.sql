-- Attendance module (specs/011-attendance). See data-model.md for field rationale.
-- V9 — Identity=V1, Organization=V2, Audit=V3, School/Zone=V4, School/Places=V5,
-- Teacher=V6, Teacher Salary History=V7, Zone-Manager Assignment=V8.

CREATE TABLE attendance_status_code (
    code        VARCHAR(20)   PRIMARY KEY,
    label       VARCHAR(100)  NOT NULL,
    category    VARCHAR(20)   NOT NULL,
    weight      NUMERIC(3,2)  NOT NULL DEFAULT 1.00,
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ   NOT NULL,
    created_by  UUID          NOT NULL
);

INSERT INTO attendance_status_code (code, label, category, weight, active, created_at, created_by) VALUES
    ('PRESENT', 'Present', 'WORKED', 1.00, TRUE, now(), '00000000-0000-0000-0000-000000000000'),
    ('LEAVE', 'Leave', 'LEAVE', 0.00, TRUE, now(), '00000000-0000-0000-0000-000000000000'),
    ('TRAINING', 'Training Day', 'TRAINING', 1.00, TRUE, now(), '00000000-0000-0000-0000-000000000000'),
    ('NON_WORKING', 'Non-Working Day', 'NON_WORKING', 0.00, TRUE, now(), '00000000-0000-0000-0000-000000000000');

CREATE TABLE attendance_mark (
    id                     UUID           PRIMARY KEY,
    teacher_id             UUID           NOT NULL,
    mark_date              DATE           NOT NULL,
    school_id              UUID           NOT NULL,
    status_code            VARCHAR(20)    NOT NULL REFERENCES attendance_status_code(code),
    fractional_value       NUMERIC(3,2)   NOT NULL DEFAULT 1.00 CHECK (fractional_value >= 0 AND fractional_value <= 1),
    evidence_geo_lat       NUMERIC(9,6),
    evidence_geo_lng       NUMERIC(9,6),
    evidence_photo_url     VARCHAR(500),
    evidence_checkin_code  VARCHAR(50),
    marked_by              UUID           NOT NULL,
    marked_by_role         VARCHAR(20)    NOT NULL,
    marked_at              TIMESTAMPTZ    NOT NULL,
    UNIQUE (teacher_id, mark_date)
);

CREATE INDEX ix_attendance_mark_teacher_id_mark_date ON attendance_mark (teacher_id, mark_date);

CREATE TABLE attendance_teacher_month_lock (
    id         UUID         PRIMARY KEY,
    teacher_id UUID         NOT NULL,
    period     VARCHAR(7)   NOT NULL,
    status     VARCHAR(20)  NOT NULL,
    locked_at  TIMESTAMPTZ  NOT NULL,
    locked_by  UUID         NOT NULL,
    UNIQUE (teacher_id, period)
);

CREATE TABLE attendance_reopen_record (
    id            UUID         PRIMARY KEY,
    lock_id       UUID         NOT NULL REFERENCES attendance_teacher_month_lock(id),
    reason        VARCHAR(500) NOT NULL,
    reopened_at   TIMESTAMPTZ  NOT NULL,
    reopened_by   UUID         NOT NULL,
    relocked_at   TIMESTAMPTZ,
    relocked_by   UUID
);

CREATE TABLE attendance_non_working_date (
    id         UUID          PRIMARY KEY,
    date       DATE          NOT NULL UNIQUE,
    label      VARCHAR(200)  NOT NULL,
    active     BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ   NOT NULL,
    created_by UUID          NOT NULL
);

CREATE INDEX ix_attendance_non_working_date_date ON attendance_non_working_date (date);
