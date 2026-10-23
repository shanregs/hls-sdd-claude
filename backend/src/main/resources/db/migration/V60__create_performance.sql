-- Spec 026: Teacher observation and performance. No foreign keys cross modules: Teacher, School, user and file ids are plain
-- UUIDs checked through the owning module. performance_history is append-only (trigger below). An observation or Principal
-- feedback that is corrected is a new row with the same root_id; the earlier row stays (SUPERSEDED / current = false).
-- Version 60 leaves a gap after V53 so that specs built in parallel do not collide on a version number.

CREATE TABLE observation (
    id                       UUID PRIMARY KEY,
    root_id                  UUID         NOT NULL,
    version_no               INTEGER      NOT NULL CHECK (version_no >= 1),
    current                  BOOLEAN      NOT NULL,
    teacher_id               UUID         NOT NULL,
    school_id                UUID         NOT NULL,
    observer_user_id         UUID         NOT NULL,
    observer_name            VARCHAR(160) NOT NULL,
    manager_user_id          UUID,
    observed_on              DATE         NOT NULL,
    class_name               VARCHAR(100),
    subject                  VARCHAR(100),
    duration_minutes         INTEGER CHECK (duration_minutes IS NULL OR duration_minutes BETWEEN 5 AND 240),
    status                   VARCHAR(12)  NOT NULL CHECK (status IN ('DRAFT', 'SUBMITTED', 'NOT_HELD', 'SUPERSEDED')),
    not_held_reason          VARCHAR(20) CHECK (not_held_reason IN (
        'PRINCIPAL_DECLINED', 'PRINCIPAL_ABSENT', 'TEACHER_ABSENT', 'SCHOOL_CLOSED', 'OTHER')),
    not_held_text            VARCHAR(2000),
    overall_result           VARCHAR(20) CHECK (overall_result IN ('GOOD', 'NEEDS_IMPROVEMENT', 'UNSUITABLE')),
    average_rating           NUMERIC(3, 1),
    suggested_result         VARCHAR(20),
    comments                 VARCHAR(2000),
    strengths                VARCHAR(2000),
    improvements             VARCHAR(2000),
    principal_comments       VARCHAR(2000),
    permission_by            VARCHAR(160),
    permission_way           VARCHAR(12) CHECK (permission_way IN ('IN_PERSON', 'PHONE', 'MESSAGE', 'WRITTEN')),
    permission_on            DATE,
    issue_id                 UUID,
    followup_of_issue_id     UUID,
    correction_reason        VARCHAR(2000),
    submitted_at             TIMESTAMPTZ,
    version                  BIGINT       NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ  NOT NULL,
    updated_at               TIMESTAMPTZ  NOT NULL
);

CREATE UNIQUE INDEX uq_observation_current_root ON observation (root_id) WHERE current;
CREATE INDEX ix_observation_teacher ON observation (teacher_id, observed_on DESC) WHERE current;
CREATE INDEX ix_observation_school_status ON observation (school_id, status) WHERE current;
CREATE INDEX ix_observation_observer ON observation (observer_user_id) WHERE current;
CREATE INDEX ix_observation_manager ON observation (manager_user_id) WHERE current;

CREATE TABLE observation_rating (
    observation_id UUID        NOT NULL REFERENCES observation (id),
    area           VARCHAR(30) NOT NULL CHECK (area IN (
        'ENGLISH_COMMUNICATION', 'TEACHING_ABILITY', 'CONTENT_DELIVERY', 'CLASSROOM_MANAGEMENT',
        'STUDENT_ENGAGEMENT', 'PREPARATION', 'CONFIDENCE', 'PUNCTUALITY')),
    rating         INTEGER CHECK (rating IS NULL OR rating BETWEEN 1 AND 5),
    PRIMARY KEY (observation_id, area)
);

CREATE TABLE observation_attachment (
    id             UUID PRIMARY KEY,
    observation_id UUID         NOT NULL REFERENCES observation (id),
    file_id        UUID         NOT NULL,
    name           VARCHAR(200) NOT NULL,
    added_by       UUID         NOT NULL,
    added_at       TIMESTAMPTZ  NOT NULL
);

CREATE INDEX ix_observation_attachment_obs ON observation_attachment (observation_id);

CREATE TABLE principal_feedback (
    id                    UUID PRIMARY KEY,
    root_id               UUID         NOT NULL,
    version_no            INTEGER      NOT NULL CHECK (version_no >= 1),
    current               BOOLEAN      NOT NULL,
    teacher_id            UUID         NOT NULL,
    school_id             UUID         NOT NULL,
    principal_name        VARCHAR(160) NOT NULL,
    given_on              DATE         NOT NULL,
    category              VARCHAR(24)  NOT NULL CHECK (category IN (
        'GOOD_PERFORMANCE', 'NEEDS_IMPROVEMENT', 'ATTENDANCE', 'TEACHING', 'COMMUNICATION', 'BEHAVIOUR', 'OTHER')),
    comments              VARCHAR(2000) NOT NULL CHECK (char_length(btrim(comments)) >= 10),
    action_required       BOOLEAN      NOT NULL,
    observation_root_id   UUID,
    recorded_by           UUID         NOT NULL,
    status                VARCHAR(12)  NOT NULL CHECK (status IN ('RECORDED', 'WITHDRAWN', 'SUPERSEDED')),
    withdrawn_reason      VARCHAR(500),
    withdrawn_by          UUID,
    withdrawn_at          TIMESTAMPTZ,
    issue_id              UUID,
    correction_reason     VARCHAR(2000),
    version               BIGINT       NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL
);

CREATE UNIQUE INDEX uq_feedback_current_root ON principal_feedback (root_id) WHERE current;
CREATE INDEX ix_feedback_teacher ON principal_feedback (teacher_id, given_on DESC) WHERE current;
CREATE INDEX ix_feedback_school ON principal_feedback (school_id) WHERE current;

CREATE TABLE performance_issue (
    id                UUID PRIMARY KEY,
    teacher_id        UUID         NOT NULL,
    school_id         UUID         NOT NULL,
    manager_user_id   UUID,
    source_type       VARCHAR(12)  NOT NULL CHECK (source_type IN ('OBSERVATION', 'FEEDBACK')),
    source_root_id    UUID         NOT NULL,
    category          VARCHAR(24)  NOT NULL,
    status            VARCHAR(20)  NOT NULL CHECK (status IN (
        'OPEN', 'UNDER_CORRECTION', 'REFERRED', 'RESOLVED', 'CANCELLED')),
    resolution        VARCHAR(12) CHECK (resolution IN ('IMPROVED', 'CONTINUE', 'TRANSFERRED', 'REPLACED', 'EXITED')),
    correction_rounds INTEGER      NOT NULL DEFAULT 0,
    opened_by         UUID,
    opened_on         DATE         NOT NULL,
    closed_by         UUID,
    closed_on         DATE,
    closing_reason    VARCHAR(500),
    version           BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL
);

-- One open issue per Teacher: a new Needs improvement result is added to it.
CREATE UNIQUE INDEX uq_issue_open_teacher ON performance_issue (teacher_id)
    WHERE status IN ('OPEN', 'UNDER_CORRECTION', 'REFERRED');
CREATE INDEX ix_issue_status ON performance_issue (status, opened_on DESC);
CREATE INDEX ix_issue_school ON performance_issue (school_id);

CREATE TABLE issue_link (
    id              UUID PRIMARY KEY,
    issue_id        UUID        NOT NULL REFERENCES performance_issue (id),
    record_type     VARCHAR(12) NOT NULL CHECK (record_type IN ('OBSERVATION', 'FEEDBACK')),
    record_root_id  UUID        NOT NULL,
    linked_on       DATE        NOT NULL,
    UNIQUE (issue_id, record_type, record_root_id)
);

CREATE TABLE corrective_action (
    id                              UUID PRIMARY KEY,
    issue_id                        UUID        NOT NULL REFERENCES performance_issue (id),
    round_no                        INTEGER     NOT NULL CHECK (round_no >= 1),
    discussed                       VARCHAR(2000) NOT NULL,
    required_change                 VARCHAR(2000) NOT NULL,
    follow_up_on                    DATE        NOT NULL,
    recorded_by                     UUID        NOT NULL,
    recorded_on                     DATE        NOT NULL,
    follow_up_source_key            VARCHAR(120) NOT NULL,
    follow_up_observation_root_id   UUID,
    UNIQUE (issue_id, round_no)
);

CREATE TABLE referral (
    id                   UUID PRIMARY KEY,
    issue_id             UUID        NOT NULL UNIQUE REFERENCES performance_issue (id),
    recommendation       VARCHAR(8)  NOT NULL CHECK (recommendation IN ('TRANSFER', 'REPLACE')),
    reason               VARCHAR(2000) NOT NULL CHECK (char_length(btrim(reason)) >= 20),
    referred_by          UUID        NOT NULL,
    referred_on          DATE        NOT NULL,
    decision_outcome     VARCHAR(12) CHECK (decision_outcome IN ('TRANSFERRED', 'REPLACED')),
    decision_reported_on DATE
);

CREATE TABLE observation_plan (
    id                                UUID PRIMARY KEY,
    teacher_id                        UUID        NOT NULL,
    school_id                         UUID        NOT NULL,
    planned_on                        DATE        NOT NULL,
    reason                            VARCHAR(16) NOT NULL CHECK (reason IN (
        'SCHOOL_FEEDBACK', 'MANAGEMENT_PLAN', 'FOLLOW_UP', 'OTHER')),
    reason_text                       VARCHAR(500),
    planned_by                        UUID        NOT NULL,
    source_feedback_root_id           UUID,
    issue_id                          UUID,
    task_source                       VARCHAR(40) NOT NULL,
    task_source_key                   VARCHAR(120) NOT NULL,
    status                            VARCHAR(10) NOT NULL CHECK (status IN ('OPEN', 'DONE', 'CANCELLED')),
    completed_by_observation_root_id  UUID,
    cancel_reason                     VARCHAR(500),
    version                           BIGINT      NOT NULL DEFAULT 0,
    created_at                        TIMESTAMPTZ NOT NULL,
    updated_at                        TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX uq_plan_open_teacher ON observation_plan (teacher_id) WHERE status = 'OPEN';
CREATE INDEX ix_plan_status_date ON observation_plan (status, planned_on);

CREATE TABLE performance_history (
    id            UUID PRIMARY KEY,
    record_type   VARCHAR(20)  NOT NULL,
    record_id     UUID         NOT NULL,
    teacher_id    UUID         NOT NULL,
    event         VARCHAR(30)  NOT NULL,
    actor_user_id UUID,
    actor_name    VARCHAR(160),
    at            TIMESTAMPTZ  NOT NULL,
    before_json   TEXT,
    after_json    TEXT,
    note          VARCHAR(2000)
);

CREATE INDEX ix_performance_history_record ON performance_history (record_type, record_id, at);
CREATE INDEX ix_performance_history_teacher ON performance_history (teacher_id, at);

CREATE FUNCTION performance_history_rows_are_final() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% rows are append-only and cannot be updated or deleted', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_performance_history_final
    BEFORE UPDATE OR DELETE ON performance_history
    FOR EACH ROW EXECUTE FUNCTION performance_history_rows_are_final();
