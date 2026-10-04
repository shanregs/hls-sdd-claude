-- Attendance module (spec 008). teacher_id, school_id and user_id are plain ids validated through the
-- owning modules' public APIs (no cross-module foreign keys). History and event tables are
-- append-only: the application has no update or delete path for them.

CREATE TABLE attendance_status_code (
    id         UUID PRIMARY KEY,
    short_code VARCHAR(8)   NOT NULL,
    name       VARCHAR(60)  NOT NULL,
    category   VARCHAR(12)  NOT NULL CHECK (category IN ('WORKED', 'LEAVE', 'TRAINING', 'NON_WORKING')),
    weight     NUMERIC(4,2) NOT NULL CHECK (weight >= 0 AND weight <= 1),
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    system     BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order INT          NOT NULL,
    version    BIGINT       NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_attendance_status_code_short ON attendance_status_code (lower(short_code));

INSERT INTO attendance_status_code (id, short_code, name, category, weight, active, system, sort_order, version) VALUES
    ('00000000-0000-0000-0008-000000000001', 'P', 'Present',      'WORKED',      1.00, TRUE, TRUE, 1, 0),
    ('00000000-0000-0000-0008-000000000002', 'L', 'Leave',        'LEAVE',       0.00, TRUE, TRUE, 2, 0),
    ('00000000-0000-0000-0008-000000000003', 'T', 'Training day', 'TRAINING',    1.00, TRUE, TRUE, 3, 0),
    ('00000000-0000-0000-0008-000000000004', 'N', 'Non-working',  'NON_WORKING', 0.00, TRUE, TRUE, 4, 0);

CREATE TABLE attendance_mark (
    id             UUID PRIMARY KEY,
    teacher_id     UUID         NOT NULL,
    mark_date      DATE         NOT NULL,
    status_code_id UUID         NOT NULL REFERENCES attendance_status_code (id),
    day_value      NUMERIC(3,2) NOT NULL CHECK (day_value IN (0.50, 1.00)),
    school_id      UUID         NOT NULL,
    note           VARCHAR(500),
    set_by_user_id UUID         NOT NULL,
    set_by_kind    VARCHAR(10)  NOT NULL CHECK (set_by_kind IN ('SELF', 'SUPERVISOR')),
    set_at         TIMESTAMPTZ  NOT NULL,
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_attendance_mark_teacher_date UNIQUE (teacher_id, mark_date)
);

CREATE INDEX idx_attendance_mark_date ON attendance_mark (mark_date);
CREATE INDEX idx_attendance_mark_school_date ON attendance_mark (school_id, mark_date);

CREATE TABLE attendance_mark_history (
    id             UUID PRIMARY KEY,
    teacher_id     UUID        NOT NULL,
    mark_date      DATE        NOT NULL,
    action         VARCHAR(10) NOT NULL CHECK (action IN ('CREATED', 'CORRECTED', 'CLEARED')),
    status_code_id UUID,
    day_value      NUMERIC(3,2),
    school_id      UUID,
    note           VARCHAR(500),
    set_by_user_id UUID        NOT NULL,
    set_by_kind    VARCHAR(10) NOT NULL CHECK (set_by_kind IN ('SELF', 'SUPERVISOR')),
    set_at         TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_attendance_mark_history_day ON attendance_mark_history (teacher_id, mark_date, set_at);

CREATE TABLE attendance_calendar_setting (
    id              UUID PRIMARY KEY,
    school_id       UUID,
    weekly_off_days VARCHAR(40) NOT NULL,
    version         BIGINT      NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_attendance_calendar_school ON attendance_calendar_setting (school_id)
    WHERE school_id IS NOT NULL;
CREATE UNIQUE INDEX uq_attendance_calendar_default ON attendance_calendar_setting ((TRUE))
    WHERE school_id IS NULL;

INSERT INTO attendance_calendar_setting (id, school_id, weekly_off_days, version)
VALUES ('00000000-0000-0000-0008-000000000010', NULL, 'SUN', 0);

CREATE TABLE attendance_non_working_date (
    id          UUID PRIMARY KEY,
    on_date     DATE         NOT NULL UNIQUE,
    description VARCHAR(200) NOT NULL,
    created_by  UUID         NOT NULL
);

CREATE TABLE attendance_teacher_month (
    id                   UUID PRIMARY KEY,
    teacher_id           UUID          NOT NULL,
    year_month           CHAR(7)       NOT NULL CHECK (year_month ~ '^[0-9]{4}-[0-9]{2}$'),
    state                VARCHAR(8)    NOT NULL CHECK (state IN ('LOCKED', 'OPEN')),
    working_days         NUMERIC(6,2)  NOT NULL,
    days_worked          NUMERIC(6,2)  NOT NULL,
    days_leave           NUMERIC(6,2)  NOT NULL,
    training_available   NUMERIC(6,2)  NOT NULL,
    training_attended    NUMERIC(6,2)  NOT NULL,
    unmarked             INT           NOT NULL,
    weighted_total       NUMERIC(7,2)  NOT NULL,
    changed_at           TIMESTAMPTZ   NOT NULL,
    version              BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_attendance_teacher_month UNIQUE (teacher_id, year_month)
);

CREATE TABLE attendance_teacher_month_event (
    id            UUID PRIMARY KEY,
    teacher_id    UUID        NOT NULL,
    year_month    CHAR(7)     NOT NULL,
    event         VARCHAR(10) NOT NULL CHECK (event IN ('LOCKED', 'REOPENED', 'RELOCKED')),
    reason        VARCHAR(500),
    actor_user_id UUID        NOT NULL,
    occurred_at   TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_attendance_month_event ON attendance_teacher_month_event (teacher_id, year_month, occurred_at);
