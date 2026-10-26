-- Spec 029: Content management. No foreign keys cross modules: user ids, task ids and file ids are plain UUIDs checked
-- through the owning module. content_history is append-only and content_version never changes except to record a removal.
-- The new role value CONTENT_CONTRIBUTOR (19 characters) fits the VARCHAR(20) role columns, so no change is needed there.

CREATE SEQUENCE content_code_seq START 1;

CREATE TABLE content (
    id                           UUID PRIMARY KEY,
    code                         VARCHAR(20)   NOT NULL UNIQUE,
    title                        VARCHAR(120)  NOT NULL CHECK (char_length(btrim(title)) >= 3),
    category                     VARCHAR(60)   NOT NULL,
    subject                      VARCHAR(80)   NOT NULL,
    description                  VARCHAR(2000),
    author_user_id               UUID          NOT NULL,
    created_by_user_id           UUID          NOT NULL,
    due_on                       DATE,
    status                       VARCHAR(12)   NOT NULL CHECK (status IN ('DRAFT', 'ASSIGNED', 'SUBMITTED', 'APPROVED', 'RETURNED')),
    current_version              INTEGER       NOT NULL DEFAULT 0,
    last_approved_version        INTEGER,
    reviewer_user_id             UUID,
    approved_on                  DATE,
    task_id                      UUID,
    no_reviewer_notified_version INTEGER,
    version                      BIGINT        NOT NULL DEFAULT 0,
    created_at                   TIMESTAMPTZ   NOT NULL,
    updated_at                   TIMESTAMPTZ   NOT NULL
);

CREATE INDEX ix_content_author_status ON content (author_user_id, status);
CREATE INDEX ix_content_status_due ON content (status, due_on);
CREATE INDEX ix_content_category ON content (category);
CREATE INDEX ix_content_subject ON content (subject);

CREATE TABLE content_version (
    id                   UUID PRIMARY KEY,
    content_id           UUID         NOT NULL REFERENCES content (id),
    number               INTEGER      NOT NULL CHECK (number >= 1),
    file_id              UUID         NOT NULL UNIQUE,
    file_name            VARCHAR(255) NOT NULL,
    content_type         VARCHAR(120) NOT NULL,
    size_bytes           BIGINT       NOT NULL,
    author_user_id       UUID         NOT NULL,
    submitted_by_user_id UUID         NOT NULL,
    note                 VARCHAR(500),
    submitted_at         TIMESTAMPTZ  NOT NULL,
    removed_by           UUID,
    removed_at           TIMESTAMPTZ,
    removal_reason       VARCHAR(300),
    CONSTRAINT uq_content_version_number UNIQUE (content_id, number)
);

CREATE FUNCTION content_version_rows_are_final() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION '% rows cannot be deleted', TG_TABLE_NAME;
    END IF;
    IF NEW.id IS DISTINCT FROM OLD.id OR NEW.content_id IS DISTINCT FROM OLD.content_id
        OR NEW.number IS DISTINCT FROM OLD.number OR NEW.file_id IS DISTINCT FROM OLD.file_id
        OR NEW.file_name IS DISTINCT FROM OLD.file_name OR NEW.content_type IS DISTINCT FROM OLD.content_type
        OR NEW.size_bytes IS DISTINCT FROM OLD.size_bytes OR NEW.author_user_id IS DISTINCT FROM OLD.author_user_id
        OR NEW.submitted_by_user_id IS DISTINCT FROM OLD.submitted_by_user_id OR NEW.note IS DISTINCT FROM OLD.note
        OR NEW.submitted_at IS DISTINCT FROM OLD.submitted_at THEN
        RAISE EXCEPTION '% rows cannot be changed, only a removal can be recorded', TG_TABLE_NAME;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_content_version_final
    BEFORE UPDATE OR DELETE ON content_version
    FOR EACH ROW EXECUTE FUNCTION content_version_rows_are_final();

CREATE TABLE content_review (
    id               UUID PRIMARY KEY,
    content_id       UUID          NOT NULL REFERENCES content (id),
    version_number   INTEGER       NOT NULL,
    reviewer_user_id UUID          NOT NULL,
    decision         VARCHAR(10)   NOT NULL CHECK (decision IN ('APPROVED', 'RETURNED')),
    comment          VARCHAR(1000),
    decided_at       TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_content_review_comment CHECK (decision = 'APPROVED' OR char_length(btrim(coalesce(comment, ''))) > 0)
);

CREATE INDEX ix_content_review_content ON content_review (content_id, decided_at);

CREATE TABLE content_history (
    id            UUID PRIMARY KEY,
    content_id    UUID         NOT NULL REFERENCES content (id),
    event         VARCHAR(30)  NOT NULL,
    actor_user_id UUID,
    at            TIMESTAMPTZ  NOT NULL,
    before_value  VARCHAR(600),
    after_value   VARCHAR(600),
    note          VARCHAR(2000)
);

CREATE INDEX ix_content_history_content ON content_history (content_id, at);

CREATE FUNCTION content_history_rows_are_final() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% rows are append-only and cannot be updated or deleted', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_content_history_final
    BEFORE UPDATE OR DELETE ON content_history
    FOR EACH ROW EXECUTE FUNCTION content_history_rows_are_final();
