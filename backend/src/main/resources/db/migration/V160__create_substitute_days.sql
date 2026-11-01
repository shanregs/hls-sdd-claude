-- Substitute day attendance and pay tracking (spec 017b). One day record per live allocation (requirement and
-- substitute), marked Present or Absent by the Zone Manager, locked per substitute and month, with an append-only
-- event history. Ids of other modules are plain ids (no foreign keys across modules). Money is numeric in Rupees.

CREATE TABLE substitute_day (
    id                    UUID PRIMARY KEY,
    requirement_id        UUID           NOT NULL,
    substitute_teacher_id UUID           NOT NULL,
    school_id             UUID           NOT NULL,
    zone_id               UUID           NOT NULL,
    absent_teacher_id     UUID           NOT NULL,
    day_date              DATE           NOT NULL,
    day_value             NUMERIC(3, 2)  NOT NULL CHECK (day_value IN (0.50, 1.00)),
    live                  BOOLEAN        NOT NULL DEFAULT TRUE,
    cancel_reason         VARCHAR(100),
    attendance            VARCHAR(12)    NOT NULL DEFAULT 'NOT_MARKED'
                          CHECK (attendance IN ('NOT_MARKED', 'PRESENT', 'ABSENT')),
    marked_by             UUID,
    marked_at             TIMESTAMPTZ,
    source                VARCHAR(12)    NOT NULL CHECK (source IN ('ALLOCATION', 'MIGRATED')),
    needs_review          BOOLEAN        NOT NULL DEFAULT FALSE,
    status                VARCHAR(40)    NOT NULL,
    mismatch_reason       VARCHAR(200),
    rate                  NUMERIC(10, 2),
    amount                NUMERIC(12, 2),
    run_id                UUID,
    run_month             DATE,
    paid_amount           NUMERIC(12, 2),
    correction_ref        VARCHAR(100),
    version               BIGINT         NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ    NOT NULL,
    updated_at            TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uq_substitute_day UNIQUE (requirement_id, substitute_teacher_id),
    CONSTRAINT ck_substitute_day_mark CHECK (attendance = 'NOT_MARKED' OR (marked_by IS NOT NULL AND marked_at IS NOT NULL))
);

CREATE INDEX ix_substitute_day_status ON substitute_day (status);
CREATE INDEX ix_substitute_day_teacher_date ON substitute_day (substitute_teacher_id, day_date);
CREATE INDEX ix_substitute_day_zone_date ON substitute_day (zone_id, day_date);
CREATE INDEX ix_substitute_day_run_month ON substitute_day (run_month);

CREATE TABLE substitute_month_lock (
    id                    UUID PRIMARY KEY,
    substitute_teacher_id UUID        NOT NULL,
    month                 DATE        NOT NULL,
    locked                BOOLEAN     NOT NULL,
    locked_by             UUID,
    locked_at             TIMESTAMPTZ,
    reopened_by           UUID,
    reopened_at           TIMESTAMPTZ,
    reopen_reason         VARCHAR(500),
    version               BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uq_substitute_month_lock UNIQUE (substitute_teacher_id, month),
    CONSTRAINT ck_substitute_month_first CHECK (EXTRACT(DAY FROM month) = 1),
    CONSTRAINT ck_substitute_month_reopen CHECK (locked OR reopen_reason IS NOT NULL)
);

CREATE TABLE substitute_day_event (
    id            UUID PRIMARY KEY,
    day_id        UUID         NOT NULL,
    at            TIMESTAMPTZ  NOT NULL,
    field         VARCHAR(20)  NOT NULL CHECK (field IN
                  ('ATTENDANCE', 'LIVE', 'STATUS', 'RUN', 'LOCK', 'REOPEN', 'MIGRATED', 'CORRECTION')),
    from_value    VARCHAR(100),
    to_value      VARCHAR(100),
    actor_user_id UUID,
    cause         VARCHAR(20)  NOT NULL CHECK (cause IN
                  ('MARK', 'EVENT_ASSIGNED', 'EVENT_ENDED', 'LOCK', 'JOB', 'RUN_APPROVED', 'MIGRATION', 'CORRECTION')),
    note          VARCHAR(200)
);

CREATE INDEX ix_substitute_day_event_day ON substitute_day_event (day_id, at);

CREATE FUNCTION substitute_day_events_are_final() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'substitute_day_event rows are append-only and cannot be updated or deleted';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_substitute_day_event_final
    BEFORE UPDATE OR DELETE ON substitute_day_event
    FOR EACH ROW EXECUTE FUNCTION substitute_day_events_are_final();
