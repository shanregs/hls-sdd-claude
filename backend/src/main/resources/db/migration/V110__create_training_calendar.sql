-- Spec 024: Training calendar (weekend refreshers and monthly sessions). No foreign keys cross modules: Teacher, Manager, School,
-- Zone and user ids are plain UUIDs checked through the owning module. The two history tables are append-only (trigger below).
-- Nothing here stores pay, a stipend or a cost (FR-030). Version 110: V90 is the newest on main and spec 031 plans V100.

CREATE TABLE training_pattern (
    id                 UUID PRIMARY KEY,
    rule_kind          VARCHAR(16)  NOT NULL CHECK (rule_kind IN ('NTH_WEEKDAY', 'DAY_OF_MONTH')),
    nth                INTEGER,
    weekday            INTEGER,
    day_of_month       INTEGER,
    starts_at          TIME         NOT NULL,
    ends_at            TIME         NOT NULL,
    topic              VARCHAR(200) NOT NULL,
    trainer_name       VARCHAR(120),
    trainer_contact    VARCHAR(160),
    venue_kind         VARCHAR(10)  NOT NULL CHECK (venue_kind IN ('PHYSICAL', 'VIRTUAL')),
    venue              VARCHAR(300),
    description        VARCHAR(1000),
    default_group_kind VARCHAR(16)  CHECK (default_group_kind IN ('SCHOOL', 'MANAGER', 'ZONE', 'ALL_TEACHERS', 'ALL_MANAGERS', 'ALL')),
    default_group_ref  UUID,
    start_month        DATE         NOT NULL,
    end_month          DATE,
    version            BIGINT       NOT NULL DEFAULT 0,
    created_by         UUID         NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_training_pattern_times CHECK (ends_at > starts_at),
    CONSTRAINT ck_training_pattern_rule CHECK (
        (rule_kind = 'NTH_WEEKDAY' AND nth BETWEEN 1 AND 5 AND weekday BETWEEN 1 AND 7 AND day_of_month IS NULL)
        OR (rule_kind = 'DAY_OF_MONTH' AND day_of_month BETWEEN 1 AND 31 AND nth IS NULL AND weekday IS NULL)),
    CONSTRAINT ck_training_pattern_months CHECK (end_month IS NULL OR end_month >= start_month),
    CONSTRAINT ck_training_pattern_group CHECK (
        default_group_kind IS NULL
        OR (default_group_kind IN ('SCHOOL', 'MANAGER', 'ZONE') AND default_group_ref IS NOT NULL)
        OR (default_group_kind IN ('ALL_TEACHERS', 'ALL_MANAGERS', 'ALL') AND default_group_ref IS NULL))
);

-- A second active pattern with the same rule and start time is probably a duplicate and is refused.
CREATE UNIQUE INDEX uq_training_pattern_rule ON training_pattern
    (rule_kind, COALESCE(nth, 0), COALESCE(weekday, 0), COALESCE(day_of_month, 0), starts_at)
    WHERE end_month IS NULL;

CREATE TABLE training_pattern_skip (
    id         UUID PRIMARY KEY,
    pattern_id UUID         NOT NULL REFERENCES training_pattern (id),
    month      DATE         NOT NULL,
    reason     VARCHAR(300) NOT NULL,
    skipped_by UUID         NOT NULL,
    skipped_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_training_pattern_skip UNIQUE (pattern_id, month)
);

CREATE TABLE training_session (
    id              UUID PRIMARY KEY,
    session_type    VARCHAR(8)   NOT NULL CHECK (session_type IN ('WEEKEND', 'MONTHLY')),
    topic           VARCHAR(200) NOT NULL,
    session_date    DATE         NOT NULL,
    starts_at       TIME         NOT NULL,
    ends_at         TIME         NOT NULL,
    trainer_name    VARCHAR(120),
    trainer_contact VARCHAR(160),
    venue_kind      VARCHAR(10)  NOT NULL CHECK (venue_kind IN ('PHYSICAL', 'VIRTUAL')),
    venue           VARCHAR(300),
    description     VARCHAR(1000),
    seat_limit      INT          CHECK (seat_limit IS NULL OR seat_limit > 0),
    status          VARCHAR(10)  NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED', 'HELD', 'CANCELLED', 'SKIPPED')),
    pattern_id      UUID         REFERENCES training_pattern (id),
    pattern_month   DATE,
    hand_edited     BOOLEAN      NOT NULL DEFAULT FALSE,
    skip_reason     VARCHAR(300),
    cancel_reason   VARCHAR(300),
    held_at         TIMESTAMPTZ,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_by      UUID         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_training_session_times CHECK (ends_at > starts_at),
    CONSTRAINT ck_training_session_skipped CHECK (status <> 'SKIPPED' OR pattern_id IS NOT NULL),
    CONSTRAINT ck_training_session_pattern CHECK (pattern_id IS NULL OR pattern_month IS NOT NULL)
);

CREATE UNIQUE INDEX uq_training_session_pattern_month ON training_session (pattern_id, pattern_month) WHERE pattern_id IS NOT NULL;
CREATE INDEX ix_training_session_date ON training_session (session_date);
CREATE INDEX ix_training_session_status_date ON training_session (status, session_date);

CREATE TABLE training_session_history (
    id           UUID PRIMARY KEY,
    session_id   UUID         NOT NULL REFERENCES training_session (id),
    field        VARCHAR(16)  NOT NULL CHECK (field IN ('date', 'time', 'trainer', 'venue', 'topic', 'status', 'seat_limit')),
    before_value VARCHAR(400),
    after_value  VARCHAR(400),
    reason       VARCHAR(300),
    changed_by   UUID,
    changed_at   TIMESTAMPTZ  NOT NULL
);

CREATE INDEX ix_training_session_history_session ON training_session_history (session_id, changed_at);

CREATE TABLE training_registration (
    id            UUID PRIMARY KEY,
    session_id    UUID        NOT NULL REFERENCES training_session (id),
    teacher_id    UUID        NOT NULL,
    status        VARCHAR(10) NOT NULL CHECK (status IN ('REGISTERED', 'CANCELLED')),
    registered_by UUID        NOT NULL,
    registered_at TIMESTAMPTZ NOT NULL,
    cancelled_at  TIMESTAMPTZ,
    cancelled_by  UUID,
    version       BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uq_training_registration UNIQUE (session_id, teacher_id)
);

CREATE INDEX ix_training_registration_teacher ON training_registration (teacher_id);

CREATE TABLE training_assignment (
    id                 UUID PRIMARY KEY,
    session_id         UUID        NOT NULL REFERENCES training_session (id),
    person_kind        VARCHAR(8)  NOT NULL CHECK (person_kind IN ('TEACHER', 'MANAGER')),
    person_id          UUID        NOT NULL,
    source             VARCHAR(10) NOT NULL CHECK (source IN ('INDIVIDUAL', 'GROUP')),
    group_kind         VARCHAR(16),
    group_ref          UUID,
    status             VARCHAR(10) NOT NULL CHECK (status IN ('REQUIRED', 'REMOVED')),
    assigned_by        UUID        NOT NULL,
    assigned_at        TIMESTAMPTZ NOT NULL,
    removed_by         UUID,
    removed_at         TIMESTAMPTZ,
    removed_reason     VARCHAR(300),
    cannot_attend_note VARCHAR(300),
    version            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uq_training_assignment UNIQUE (session_id, person_kind, person_id)
);

CREATE INDEX ix_training_assignment_person ON training_assignment (person_kind, person_id);

CREATE TABLE training_attendance_entry (
    id               UUID PRIMARY KEY,
    session_id       UUID          NOT NULL REFERENCES training_session (id),
    person_kind      VARCHAR(8)    NOT NULL CHECK (person_kind IN ('TEACHER', 'MANAGER')),
    person_id        UUID          NOT NULL,
    basis            VARCHAR(10)   NOT NULL CHECK (basis IN ('ASSIGNED', 'REGISTERED', 'WALK_IN')),
    outcome          VARCHAR(8)    NOT NULL CHECK (outcome IN ('PRESENT', 'ABSENT', 'EXCUSED')),
    day_value        NUMERIC(3, 2),
    reason           VARCHAR(300),
    on_leave         BOOLEAN       NOT NULL DEFAULT FALSE,
    certificate_note VARCHAR(300),
    internal_remark  VARCHAR(500),
    sync_state       VARCHAR(14)   NOT NULL CHECK (sync_state IN ('NOT_NEEDED', 'WRITTEN', 'CLASH', 'PENDING', 'NOT_APPLICABLE')),
    clash_note       VARCHAR(300),
    recorded_by      UUID          NOT NULL,
    recorded_at      TIMESTAMPTZ   NOT NULL,
    version          BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_training_attendance_entry UNIQUE (session_id, person_kind, person_id),
    CONSTRAINT ck_training_entry_present CHECK (outcome <> 'PRESENT' OR day_value IN (1.00, 0.50)),
    CONSTRAINT ck_training_entry_walk_in CHECK (basis <> 'WALK_IN' OR outcome = 'PRESENT')
);

CREATE INDEX ix_training_attendance_entry_person ON training_attendance_entry (person_kind, person_id);

CREATE TABLE training_attendance_history (
    id         UUID PRIMARY KEY,
    entry_id   UUID         NOT NULL REFERENCES training_attendance_entry (id),
    action     VARCHAR(10)  NOT NULL CHECK (action IN ('CREATED', 'CORRECTED')),
    outcome    VARCHAR(8)   NOT NULL,
    day_value  NUMERIC(3, 2),
    reason     VARCHAR(300),
    changed_by UUID         NOT NULL,
    changed_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX ix_training_attendance_history_entry ON training_attendance_history (entry_id, changed_at);

CREATE TABLE training_exception (
    id                UUID PRIMARY KEY,
    session_id        UUID         NOT NULL REFERENCES training_session (id),
    person_kind       VARCHAR(8)   NOT NULL CHECK (person_kind IN ('TEACHER', 'MANAGER')),
    person_id         UUID         NOT NULL,
    entry_id          UUID         NOT NULL REFERENCES training_attendance_entry (id),
    status            VARCHAR(10)  NOT NULL CHECK (status IN ('OPEN', 'CLOSED', 'WITHDRAWN')),
    closure_kind      VARCHAR(12)  CHECK (closure_kind IN ('EXCUSED', 'MADE_UP', 'ACKNOWLEDGED')),
    closure_note      VARCHAR(300),
    makeup_session_id UUID,
    raised_at         TIMESTAMPTZ  NOT NULL,
    closed_by         UUID,
    closed_at         TIMESTAMPTZ,
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_training_exception UNIQUE (session_id, person_kind, person_id),
    CONSTRAINT ck_training_exception_closed CHECK (status <> 'CLOSED' OR closure_kind IS NOT NULL)
);

CREATE INDEX ix_training_exception_status ON training_exception (status, raised_at);
CREATE INDEX ix_training_exception_person ON training_exception (person_kind, person_id);

CREATE TABLE training_reminder_sent (
    id          UUID PRIMARY KEY,
    session_id  UUID        NOT NULL REFERENCES training_session (id),
    person_kind VARCHAR(8)  NOT NULL,
    person_id   UUID        NOT NULL,
    days_before INTEGER    NOT NULL,
    sent_at     TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_training_reminder_sent UNIQUE (session_id, person_kind, person_id, days_before)
);

CREATE FUNCTION training_history_rows_are_final() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% rows are append-only and cannot be updated or deleted', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_training_session_history_final
    BEFORE UPDATE OR DELETE ON training_session_history
    FOR EACH ROW EXECUTE FUNCTION training_history_rows_are_final();

CREATE TRIGGER trg_training_attendance_history_final
    BEFORE UPDATE OR DELETE ON training_attendance_history
    FOR EACH ROW EXECUTE FUNCTION training_history_rows_are_final();
