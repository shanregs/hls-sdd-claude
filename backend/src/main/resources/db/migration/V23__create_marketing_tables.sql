-- School marketing and the MoU pipeline (spec 023) and the shared file store (module `files`). No foreign keys cross a
-- module boundary (ids are plain UUIDs); foreign keys inside this sub-package and inside `files` are allowed.
-- Money is NUMERIC(12,2). MoU and Active are not stored as stages: they are derived from spec 012.

CREATE TABLE stored_file (
    id             UUID PRIMARY KEY,
    owner_type     VARCHAR(30)  NOT NULL,
    owner_id       UUID         NOT NULL,
    original_name  VARCHAR(255) NOT NULL,
    content_type   VARCHAR(100) NOT NULL,
    size_bytes     BIGINT       NOT NULL CHECK (size_bytes > 0),
    sha256         VARCHAR(64)  NOT NULL,
    storage_path   VARCHAR(300) NOT NULL,
    added_by       UUID         NOT NULL,
    added_at       TIMESTAMPTZ  NOT NULL,
    removed_at     TIMESTAMPTZ,
    removed_by     UUID,
    removal_reason VARCHAR(300),
    -- a file row only ever changes to record its removal, and a removal records who, when and why
    CONSTRAINT ck_stored_file_removal CHECK (
        (removed_at IS NULL AND removed_by IS NULL AND removal_reason IS NULL)
        OR (removed_at IS NOT NULL AND removed_by IS NOT NULL AND length(trim(coalesce(removal_reason, ''))) > 0))
);

CREATE INDEX idx_stored_file_owner ON stored_file (owner_type, owner_id);

CREATE FUNCTION stored_file_only_removal() RETURNS trigger AS $$
BEGIN
    IF NEW.id IS DISTINCT FROM OLD.id OR NEW.owner_type IS DISTINCT FROM OLD.owner_type
        OR NEW.owner_id IS DISTINCT FROM OLD.owner_id OR NEW.original_name IS DISTINCT FROM OLD.original_name
        OR NEW.content_type IS DISTINCT FROM OLD.content_type OR NEW.size_bytes IS DISTINCT FROM OLD.size_bytes
        OR NEW.sha256 IS DISTINCT FROM OLD.sha256 OR NEW.storage_path IS DISTINCT FROM OLD.storage_path
        OR NEW.added_by IS DISTINCT FROM OLD.added_by OR NEW.added_at IS DISTINCT FROM OLD.added_at
        OR (OLD.removed_at IS NOT NULL AND NEW.removed_at IS DISTINCT FROM OLD.removed_at) THEN
        RAISE EXCEPTION 'A stored file is never changed, only removed';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_stored_file_only_removal BEFORE UPDATE ON stored_file
    FOR EACH ROW EXECUTE FUNCTION stored_file_only_removal();

CREATE FUNCTION stored_file_no_delete() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'A stored file row is never deleted';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_stored_file_no_delete BEFORE DELETE ON stored_file
    FOR EACH ROW EXECUTE FUNCTION stored_file_no_delete();

CREATE TABLE marketing_prospect (
    id                UUID PRIMARY KEY,
    name              VARCHAR(200) NOT NULL CHECK (length(trim(name)) > 0),
    board             VARCHAR(60),
    address           VARCHAR(300),
    zone_id           UUID         NOT NULL,
    name_key          VARCHAR(260) NOT NULL,
    contact_person    VARCHAR(160),
    designation       VARCHAR(160),
    phone             VARCHAR(20),
    email             VARCHAR(200),
    expected_teachers INT          CHECK (expected_teachers BETWEEN 1 AND 500),
    owner_user_id     UUID         NOT NULL,
    stage             VARCHAR(14)  NOT NULL CHECK (stage IN (
        'PROSPECT', 'CONTACTED', 'VISIT', 'FOLLOW_UP', 'INTERESTED', 'NEGOTIATION', 'FINAL_STAGE', 'ON_HOLD', 'LOST')),
    stage_before_hold VARCHAR(14),
    lost_reason       VARCHAR(300),
    won_at            TIMESTAMPTZ,
    won_by            UUID,
    place_id          UUID,
    school_id         UUID,
    version           BIGINT       NOT NULL DEFAULT 0,
    created_by        UUID         NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_marketing_prospect_name_key UNIQUE (name_key),
    CONSTRAINT ck_marketing_prospect_lost CHECK (stage <> 'LOST' OR length(trim(coalesce(lost_reason, ''))) > 0),
    CONSTRAINT ck_marketing_prospect_won CHECK ((won_at IS NULL) = (won_by IS NULL))
);

CREATE INDEX idx_marketing_prospect_zone_stage ON marketing_prospect (zone_id, stage);
CREATE INDEX idx_marketing_prospect_owner ON marketing_prospect (owner_user_id);
CREATE INDEX idx_marketing_prospect_school ON marketing_prospect (school_id) WHERE school_id IS NOT NULL;

CREATE TABLE prospect_stage_history (
    id          UUID PRIMARY KEY,
    prospect_id UUID         NOT NULL REFERENCES marketing_prospect (id),
    kind        VARCHAR(16)  NOT NULL CHECK (kind IN ('STAGE', 'REVIEW_APPROVED', 'REVIEW_REJECTED')),
    from_stage  VARCHAR(14),
    to_stage    VARCHAR(14)  NOT NULL,
    reason      VARCHAR(300),
    changed_by  UUID         NOT NULL,
    changed_at  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_prospect_stage_history_prospect ON prospect_stage_history (prospect_id, changed_at);

CREATE TABLE prospect_owner_history (
    id          UUID PRIMARY KEY,
    prospect_id UUID        NOT NULL REFERENCES marketing_prospect (id),
    from_owner  UUID,
    to_owner    UUID        NOT NULL,
    changed_by  UUID        NOT NULL,
    changed_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_prospect_owner_history_prospect ON prospect_owner_history (prospect_id, changed_at);

CREATE TABLE marketing_activity (
    id            UUID PRIMARY KEY,
    prospect_id   UUID         REFERENCES marketing_prospect (id),
    school_id     UUID,
    type          VARCHAR(16)  NOT NULL CHECK (type IN ('VISIT', 'CALL', 'PROPOSAL_MEETING', 'FOLLOW_UP')),
    status        VARCHAR(10)  NOT NULL CHECK (status IN ('PLANNED', 'COMPLETED', 'CANCELLED')),
    activity_date DATE         NOT NULL,
    notes         VARCHAR(1000),
    outcome       VARCHAR(1000),
    follow_up_on  DATE,
    cancel_reason VARCHAR(300),
    created_by    UUID         NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    -- exactly one of a prospect or a School with an MoU (an account visit)
    CONSTRAINT ck_marketing_activity_target CHECK ((prospect_id IS NULL) <> (school_id IS NULL)),
    CONSTRAINT ck_marketing_activity_outcome CHECK (status <> 'COMPLETED' OR length(trim(coalesce(outcome, ''))) > 0),
    CONSTRAINT ck_marketing_activity_cancel CHECK (status <> 'CANCELLED' OR length(trim(coalesce(cancel_reason, ''))) > 0)
);

CREATE INDEX idx_marketing_activity_date ON marketing_activity (activity_date);
CREATE INDEX idx_marketing_activity_prospect ON marketing_activity (prospect_id) WHERE prospect_id IS NOT NULL;

CREATE TABLE activity_attendee (
    activity_id UUID NOT NULL REFERENCES marketing_activity (id),
    user_id     UUID NOT NULL,
    PRIMARY KEY (activity_id, user_id)
);

CREATE INDEX idx_activity_attendee_user ON activity_attendee (user_id);

CREATE TABLE activity_date_history (
    id          UUID PRIMARY KEY,
    activity_id UUID        NOT NULL REFERENCES marketing_activity (id),
    old_date    DATE        NOT NULL,
    new_date    DATE        NOT NULL,
    changed_by  UUID        NOT NULL,
    changed_at  TIMESTAMPTZ NOT NULL
);

CREATE TABLE activity_attachment (
    id          UUID PRIMARY KEY,
    activity_id UUID        NOT NULL REFERENCES marketing_activity (id),
    file_id     UUID        NOT NULL REFERENCES stored_file (id),
    added_by    UUID        NOT NULL,
    added_at    TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_activity_attachment_activity ON activity_attachment (activity_id);

CREATE TABLE proposal_revision (
    id            UUID PRIMARY KEY,
    prospect_id   UUID          NOT NULL REFERENCES marketing_prospect (id),
    revision      INT           NOT NULL CHECK (revision >= 1),
    teacher_count INT           NOT NULL CHECK (teacher_count BETWEEN 1 AND 500),
    start_month   DATE          NOT NULL,
    salary_mode   VARCHAR(12)   NOT NULL CHECK (salary_mode IN ('SAME_FOR_ALL', 'PER_TEACHER')),
    rate          NUMERIC(12,2),
    notes         VARCHAR(500),
    created_by    UUID          NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_proposal_revision UNIQUE (prospect_id, revision),
    CONSTRAINT ck_proposal_rate CHECK (
        (salary_mode = 'SAME_FOR_ALL' AND rate IS NOT NULL AND rate > 0)
        OR (salary_mode = 'PER_TEACHER' AND rate IS NULL))
);

CREATE TABLE proposal_position (
    id          UUID PRIMARY KEY,
    revision_id UUID          NOT NULL REFERENCES proposal_revision (id),
    number      INT           NOT NULL CHECK (number >= 1),
    title       VARCHAR(80),
    salary      NUMERIC(12,2) NOT NULL CHECK (salary > 0),
    CONSTRAINT uq_proposal_position UNIQUE (revision_id, number)
);

-- A proposal revision is a record of what was offered: it is never changed or removed (a new revision replaces it).
CREATE FUNCTION proposal_insert_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'A proposal revision is never changed or removed';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_proposal_revision_insert_only BEFORE UPDATE OR DELETE ON proposal_revision
    FOR EACH ROW EXECUTE FUNCTION proposal_insert_only();
CREATE TRIGGER trg_proposal_position_insert_only BEFORE UPDATE OR DELETE ON proposal_position
    FOR EACH ROW EXECUTE FUNCTION proposal_insert_only();

CREATE TABLE marketing_setting (
    key        VARCHAR(60) PRIMARY KEY,
    int_value  INT,
    version    BIGINT      NOT NULL DEFAULT 0,
    updated_by UUID,
    updated_at TIMESTAMPTZ,
    CONSTRAINT ck_marketing_setting_overdue CHECK (key <> 'mou_overdue_days' OR int_value BETWEEN 1 AND 90)
);

INSERT INTO marketing_setting (key, int_value, version) VALUES ('mou_overdue_days', 14, 0);

CREATE TABLE overdue_notice (
    prospect_id UUID        NOT NULL REFERENCES marketing_prospect (id),
    limit_days  INT         NOT NULL,
    noticed_at  TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (prospect_id, limit_days)
);

-- The overdue-MoU notification (spec 010 pattern): keep every existing type and add the new one.
ALTER TABLE notification DROP CONSTRAINT notification_type_check;
ALTER TABLE notification ADD CONSTRAINT notification_type_check CHECK (type IN (
    'LEAVE_DECIDED', 'LEAVE_REQUESTED', 'LEAVE_CANCELLED',
    'ATTENDANCE_CHANGED', 'ATTENDANCE_MONTH_LOCKED', 'ATTENDANCE_MONTH_REOPENED',
    'MOU_NOT_RECORDED'));
