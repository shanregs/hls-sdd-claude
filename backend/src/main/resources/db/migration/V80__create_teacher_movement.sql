-- Spec 027: Teacher transfer and replacement requests. No foreign keys cross modules: Teacher, School, position, assignment and
-- user ids are plain UUIDs checked through the owning module. contract_assignment is not changed. movement_request_history is
-- append-only (trigger below). Version 80 sits well above the other parallel specs (V60 performance, V70 and others).

CREATE TABLE movement_request (
    id                             UUID PRIMARY KEY,
    kind                           VARCHAR(12)  NOT NULL CHECK (kind IN ('TRANSFER', 'REPLACEMENT')),
    teacher_id                     UUID         NOT NULL,
    from_school_id                 UUID         NOT NULL,
    to_school_id                   UUID         NOT NULL,
    position_id                    UUID,
    replacement_teacher_id         UUID,
    outgoing_destination_school_id UUID,
    effective_on                   DATE         NOT NULL,
    reason_category                VARCHAR(24)  NOT NULL CHECK (reason_category IN (
        'SCHOOL_REQUEST', 'TEACHER_REQUEST', 'MANAGER_REBALANCING', 'AFTER_REVIEW', 'OTHER')),
    note                           VARCHAR(1000),
    status                         VARCHAR(12)  NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    requested_by                   UUID         NOT NULL,
    requested_at                   TIMESTAMPTZ  NOT NULL,
    decided_by                     UUID,
    decided_at                     TIMESTAMPTZ,
    decision_reason                VARCHAR(1000),
    applied_notified_at            TIMESTAMPTZ,
    version                        BIGINT       NOT NULL DEFAULT 0,
    created_at                     TIMESTAMPTZ  NOT NULL,
    updated_at                     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_movement_replacement CHECK (
        kind <> 'REPLACEMENT' OR (replacement_teacher_id IS NOT NULL AND to_school_id = from_school_id)),
    CONSTRAINT ck_movement_transfer CHECK (
        kind <> 'TRANSFER' OR (replacement_teacher_id IS NULL AND outgoing_destination_school_id IS NULL
                               AND to_school_id <> from_school_id)),
    CONSTRAINT ck_movement_decider CHECK (
        status NOT IN ('APPROVED', 'REJECTED') OR decided_by IS DISTINCT FROM requested_by)
);

CREATE INDEX ix_movement_request_status ON movement_request (status, effective_on);
CREATE INDEX ix_movement_request_from ON movement_request (from_school_id);
CREATE INDEX ix_movement_request_to ON movement_request (to_school_id);
CREATE INDEX ix_movement_request_teacher ON movement_request (teacher_id);

CREATE TABLE movement_participant (
    id         UUID PRIMARY KEY,
    request_id UUID        NOT NULL,
    teacher_id UUID        NOT NULL,
    role       VARCHAR(12) NOT NULL CHECK (role IN ('MOVING', 'OUTGOING', 'REPLACEMENT')),
    open       BOOLEAN     NOT NULL
);

CREATE UNIQUE INDEX uq_movement_participant_open ON movement_participant (teacher_id) WHERE open;
CREATE INDEX ix_movement_participant_request ON movement_participant (request_id);

CREATE TABLE movement_assignment (
    id            UUID PRIMARY KEY,
    request_id    UUID        NOT NULL,
    assignment_id UUID        NOT NULL,
    teacher_id    UUID        NOT NULL,
    effect        VARCHAR(8)  NOT NULL CHECK (effect IN ('ENDED', 'CREATED')),
    CONSTRAINT uq_movement_assignment UNIQUE (request_id, assignment_id)
);

CREATE INDEX ix_movement_assignment_assignment ON movement_assignment (assignment_id);

CREATE TABLE movement_request_history (
    id            UUID PRIMARY KEY,
    request_id    UUID        NOT NULL,
    action        VARCHAR(16) NOT NULL CHECK (action IN (
        'RAISED', 'CHANGED', 'APPROVED', 'REJECTED', 'CANCELLED', 'APPLIED', 'CLOSED_STALE')),
    from_status   VARCHAR(12),
    to_status     VARCHAR(12),
    actor_user_id UUID,
    note          VARCHAR(1000),
    at            TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_movement_request_history_request ON movement_request_history (request_id, at);

CREATE FUNCTION movement_history_rows_are_final() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% rows are append-only and cannot be updated or deleted', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_movement_request_history_final
    BEFORE UPDATE OR DELETE ON movement_request_history
    FOR EACH ROW EXECUTE FUNCTION movement_history_rows_are_final();
