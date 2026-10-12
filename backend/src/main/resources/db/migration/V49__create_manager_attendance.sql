-- Spec 032: Manager attendance. One record per Manager per date holds the check-in, check-out and, when leave is
-- approved, the leave on that date. No foreign keys cross modules: manager and user ids are plain UUIDs checked through
-- organization.api, file ids come from files.api.

CREATE TABLE manager_day (
    id                  UUID PRIMARY KEY,
    manager_id          UUID          NOT NULL,
    day_date            DATE          NOT NULL,
    day_type            VARCHAR(10) CHECK (day_type IN ('OFFICE', 'FIELD')),
    purpose             VARCHAR(500),
    unlinked            BOOLEAN       NOT NULL DEFAULT FALSE,
    check_in_at         TIMESTAMPTZ,
    check_in_lat        NUMERIC(9, 6) CHECK (check_in_lat BETWEEN -90 AND 90),
    check_in_lng        NUMERIC(9, 6) CHECK (check_in_lng BETWEEN -180 AND 180),
    check_in_accuracy_m NUMERIC(8, 2),
    check_out_at        TIMESTAMPTZ,
    check_out_lat       NUMERIC(9, 6) CHECK (check_out_lat BETWEEN -90 AND 90),
    check_out_lng       NUMERIC(9, 6) CHECK (check_out_lng BETWEEN -180 AND 180),
    check_out_accuracy_m NUMERIC(8, 2),
    photo_file_id       UUID,
    late                BOOLEAN       NOT NULL DEFAULT FALSE,
    added_later         BOOLEAN       NOT NULL DEFAULT FALSE,
    corrected           BOOLEAN       NOT NULL DEFAULT FALSE,
    leave_request_id    UUID,
    leave_fraction      NUMERIC(2, 1) CHECK (leave_fraction IN (0.5, 1.0)),
    leave_unpaid        BOOLEAN       NOT NULL DEFAULT FALSE,
    version             BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_manager_day UNIQUE (manager_id, day_date),
    CONSTRAINT ck_manager_day_out_after_in CHECK (check_out_at IS NULL OR (check_in_at IS NOT NULL AND check_out_at >= check_in_at))
);

CREATE INDEX ix_manager_day_date ON manager_day (day_date);

-- A planned activity (contract C1) linked to a Field day, named by its kind and id.
CREATE TABLE manager_day_activity (
    day_id      UUID        NOT NULL REFERENCES manager_day (id),
    kind        VARCHAR(40) NOT NULL,
    activity_id UUID        NOT NULL,
    PRIMARY KEY (day_id, kind, activity_id)
);

-- Append-only: the first row of a day is the original, every change after it is a new row.
CREATE TABLE manager_day_history (
    id         UUID PRIMARY KEY,
    day_id     UUID         NOT NULL,
    changed_by UUID         NOT NULL,
    changed_at TIMESTAMPTZ  NOT NULL,
    action     VARCHAR(20)  NOT NULL CHECK (action IN
        ('CHECK_IN', 'CHECK_OUT', 'COMPLETE_CHECK_OUT', 'CORRECT', 'LEAVE_APPLIED', 'LEAVE_REMOVED', 'ADDED_LATER')),
    reason     VARCHAR(500),
    before_json TEXT,
    after_json  TEXT
);

CREATE INDEX ix_manager_day_history_day ON manager_day_history (day_id, changed_at);

CREATE FUNCTION manager_attendance_rows_are_final() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% rows are append-only and cannot be updated or deleted', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_manager_day_history_final
    BEFORE UPDATE OR DELETE ON manager_day_history
    FOR EACH ROW EXECUTE FUNCTION manager_attendance_rows_are_final();

-- A Manager's request to add a forgotten check-in on a past day.
CREATE TABLE manager_regularization (
    id              UUID PRIMARY KEY,
    manager_id      UUID         NOT NULL,
    day_date        DATE         NOT NULL,
    proposed_time   TIME         NOT NULL,
    day_type        VARCHAR(10)  NOT NULL CHECK (day_type IN ('OFFICE', 'FIELD')),
    purpose         VARCHAR(500),
    reason          VARCHAR(500) NOT NULL,
    status          VARCHAR(10)  NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    requested_at    TIMESTAMPTZ  NOT NULL,
    decided_by      UUID,
    decided_at      TIMESTAMPTZ,
    decision_reason VARCHAR(500),
    version         BIGINT       NOT NULL DEFAULT 0
);

-- At most one pending request per Manager and date.
CREATE UNIQUE INDEX uq_manager_regularization_pending
    ON manager_regularization (manager_id, day_date) WHERE status = 'PENDING';
CREATE INDEX ix_manager_regularization_status ON manager_regularization (status, day_date);

-- A Manager's month and its lock; a missing row means OPEN.
CREATE TABLE manager_month (
    manager_id            UUID         NOT NULL,
    year_month            VARCHAR(7)   NOT NULL,
    state                 VARCHAR(8)   NOT NULL CHECK (state IN ('OPEN', 'LOCKED')),
    frozen_working_days   NUMERIC(4, 1),
    frozen_days_worked    NUMERIC(4, 1),
    frozen_leave_paid     NUMERIC(4, 1),
    frozen_leave_unpaid   NUMERIC(4, 1),
    frozen_unpaid         NUMERIC(4, 1),
    frozen_field_days     NUMERIC(4, 1),
    frozen_unpaid_dates   TEXT,
    locked_at             TIMESTAMPTZ,
    locked_by             UUID,
    version               BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (manager_id, year_month)
);

CREATE TABLE manager_month_event (
    id          UUID PRIMARY KEY,
    manager_id  UUID         NOT NULL,
    year_month  VARCHAR(7)   NOT NULL,
    event       VARCHAR(10)  NOT NULL CHECK (event IN ('LOCKED', 'REOPENED')),
    reason      VARCHAR(500),
    actor       UUID         NOT NULL,
    occurred_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX ix_manager_month_event ON manager_month_event (manager_id, year_month, occurred_at);

CREATE TRIGGER trg_manager_month_event_final
    BEFORE UPDATE OR DELETE ON manager_month_event
    FOR EACH ROW EXECUTE FUNCTION manager_attendance_rows_are_final();

-- The cut-off after which a missing check-in is reported, and the time after which a check-in is late.
CREATE TABLE manager_attendance_setting (
    id            INT PRIMARY KEY CHECK (id = 1),
    missed_cutoff TIME        NOT NULL,
    late_after    TIME        NOT NULL,
    updated_by    UUID,
    updated_at    TIMESTAMPTZ NOT NULL,
    version       BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_manager_setting_order CHECK (late_after < missed_cutoff)
);

INSERT INTO manager_attendance_setting (id, missed_cutoff, late_after, updated_by, updated_at)
VALUES (1, TIME '10:30', TIME '09:30', NULL, now());

-- Makes the missed check-in alert happen once per Manager per day, even if the job runs twice.
CREATE TABLE manager_missed_alert (
    manager_id UUID        NOT NULL,
    alert_date DATE        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (manager_id, alert_date)
);
