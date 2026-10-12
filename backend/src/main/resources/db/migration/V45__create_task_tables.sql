-- Spec 025: Manager tasks. No foreign keys cross modules: user ids, linked record ids and file ids are plain UUIDs
-- checked through the owning module. task_history is append-only (trigger below), like the other audit-style tables.
-- Version 45 leaves a gap after V26 so that specs built in parallel do not collide on a version number.

CREATE TABLE task (
    id                   UUID PRIMARY KEY,
    type                 VARCHAR(30)  NOT NULL CHECK (type IN (
        'SCHOOL_VISIT', 'FOLLOW_UP', 'CONTENT_SUBMISSION', 'RECRUITMENT', 'TRAINING',
        'TEACHER_ASSIGNMENT', 'OBSERVATION', 'PRINCIPAL_FEEDBACK', 'SUBSTITUTE_ARRANGEMENT')),
    title                VARCHAR(120) NOT NULL CHECK (char_length(btrim(title)) >= 3),
    description          VARCHAR(2000),
    assignee_user_id     UUID         NOT NULL,
    created_by           UUID,
    due_on               DATE         NOT NULL,
    escalate_after_days  INTEGER      NOT NULL DEFAULT 3 CHECK (escalate_after_days BETWEEN 1 AND 30),
    status               VARCHAR(20)  NOT NULL CHECK (status IN (
        'ASSIGNED', 'ACKNOWLEDGED', 'IN_PROGRESS', 'COMPLETED', 'VERIFIED', 'CANCELLED')),
    linked_type          VARCHAR(40),
    linked_id            UUID,
    linked_label         VARCHAR(160),
    place                VARCHAR(160),
    source               VARCHAR(40)  NOT NULL DEFAULT 'MANUAL',
    source_key           VARCHAR(120),
    parent_task_id       UUID,
    overdue_reason       VARCHAR(500),
    completion_note      VARCHAR(2000),
    completed_at         TIMESTAMPTZ,
    verified_at          TIMESTAMPTZ,
    verified_by          UUID,
    overdue_notified_for DATE,
    escalated_at         TIMESTAMPTZ,
    cancel_reason        VARCHAR(300),
    version              BIGINT       NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ  NOT NULL,
    updated_at           TIMESTAMPTZ  NOT NULL
);

CREATE INDEX ix_task_assignee_due ON task (assignee_user_id, due_on);
CREATE INDEX ix_task_status_due ON task (status, due_on);
-- One open task per (source, key): the backstop for the idempotent creation interface (C2).
CREATE UNIQUE INDEX uq_task_open_source_key ON task (source, source_key)
    WHERE source_key IS NOT NULL AND status IN ('ASSIGNED', 'ACKNOWLEDGED', 'IN_PROGRESS', 'COMPLETED');

CREATE TABLE task_history (
    id            UUID PRIMARY KEY,
    task_id       UUID         NOT NULL REFERENCES task (id),
    event         VARCHAR(30)  NOT NULL,
    actor_user_id UUID,
    at            TIMESTAMPTZ  NOT NULL,
    before_value  VARCHAR(600),
    after_value   VARCHAR(600),
    note          VARCHAR(2000)
);

CREATE INDEX ix_task_history_task ON task_history (task_id, at);

CREATE FUNCTION task_history_rows_are_final() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% rows are append-only and cannot be updated or deleted', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_task_history_final
    BEFORE UPDATE OR DELETE ON task_history
    FOR EACH ROW EXECUTE FUNCTION task_history_rows_are_final();

CREATE TABLE task_evidence (
    id       UUID PRIMARY KEY,
    task_id  UUID        NOT NULL REFERENCES task (id),
    file_id  UUID        NOT NULL UNIQUE,
    added_by UUID        NOT NULL,
    added_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_task_evidence_task ON task_evidence (task_id);

-- Every spec that adds notification events used to rewrite the explicit list of types, so parallel specs would drop each
-- other's types. The Java enum is the source of truth; the database only checks the shape.
ALTER TABLE notification DROP CONSTRAINT IF EXISTS notification_type_check;
ALTER TABLE notification ADD CONSTRAINT notification_type_check CHECK (type ~ '^[A-Z_]+$');
