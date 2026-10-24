-- MoU documents and approval (spec 031). One approval row per contract that has real terms, the uploaded signed
-- document versions (bytes live in the shared files module), and an append-only approval history.
-- User ids and file ids are plain ids (no foreign keys into identity or files); foreign keys to contract are
-- allowed (same module). Existing ACTIVE contracts are backfilled as APPROVED_LEGACY.

CREATE TABLE mou_approval (
    id              UUID PRIMARY KEY,
    contract_id     UUID         NOT NULL UNIQUE REFERENCES contract (id),
    status          VARCHAR(20)  NOT NULL CHECK (status IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED', 'APPROVED_LEGACY')),
    round           INT          NOT NULL DEFAULT 0 CHECK (round >= 0),
    submitted_by    UUID,
    submitted_at    TIMESTAMPTZ,
    decided_by      UUID,
    decided_at      TIMESTAMPTZ,
    decision_reason VARCHAR(500),
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_mou_approval_pending CHECK (
        status <> 'PENDING' OR (submitted_by IS NOT NULL AND submitted_at IS NOT NULL AND decided_by IS NULL)),
    CONSTRAINT ck_mou_approval_rejected CHECK (
        status <> 'REJECTED' OR (decided_by IS NOT NULL AND decision_reason IS NOT NULL)),
    CONSTRAINT ck_mou_approval_approved CHECK (
        status <> 'APPROVED' OR (decided_by IS NOT NULL AND decided_at IS NOT NULL)),
    CONSTRAINT ck_mou_approval_legacy CHECK (
        status <> 'APPROVED_LEGACY' OR (decided_by IS NULL AND round = 0))
);

CREATE INDEX idx_mou_approval_status ON mou_approval (status, submitted_at);

CREATE TABLE mou_document (
    id             UUID PRIMARY KEY,
    contract_id    UUID         NOT NULL REFERENCES contract (id),
    version_no     INT          NOT NULL CHECK (version_no >= 1),
    file_id        UUID         NOT NULL,
    file_name      VARCHAR(255) NOT NULL,
    content_type   VARCHAR(100) NOT NULL,
    size_bytes     BIGINT       NOT NULL CHECK (size_bytes > 0),
    added_by       UUID         NOT NULL,
    added_at       TIMESTAMPTZ  NOT NULL,
    removed_by     UUID,
    removed_at     TIMESTAMPTZ,
    removal_reason VARCHAR(300),
    CONSTRAINT uq_mou_document_version UNIQUE (contract_id, version_no),
    CONSTRAINT uq_mou_document_file UNIQUE (file_id),
    CONSTRAINT ck_mou_document_removal CHECK (
        (removed_by IS NULL AND removed_at IS NULL AND removal_reason IS NULL)
        OR (removed_by IS NOT NULL AND removed_at IS NOT NULL AND removal_reason IS NOT NULL))
);

-- A document row is written once; only the removal columns may be set, once. Never deleted.
CREATE FUNCTION mou_document_guard() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'mou_document rows cannot be deleted';
    END IF;
    IF OLD.removed_at IS NOT NULL
       OR NEW.id <> OLD.id OR NEW.contract_id <> OLD.contract_id OR NEW.version_no <> OLD.version_no
       OR NEW.file_id <> OLD.file_id OR NEW.file_name <> OLD.file_name OR NEW.content_type <> OLD.content_type
       OR NEW.size_bytes <> OLD.size_bytes OR NEW.added_by <> OLD.added_by OR NEW.added_at <> OLD.added_at THEN
        RAISE EXCEPTION 'mou_document rows can only be marked removed, once';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_mou_document_guard
    BEFORE UPDATE OR DELETE ON mou_document
    FOR EACH ROW EXECUTE FUNCTION mou_document_guard();

CREATE TABLE mou_approval_event (
    id               UUID PRIMARY KEY,
    seq              BIGINT GENERATED ALWAYS AS IDENTITY,
    contract_id      UUID         NOT NULL REFERENCES contract (id),
    round            INT          NOT NULL CHECK (round >= 0),
    action           VARCHAR(30)  NOT NULL CHECK (action IN (
        'DOCUMENT_ADDED', 'DOCUMENT_REPLACED', 'DOCUMENT_REMOVED', 'SUBMITTED', 'RESUBMITTED', 'APPROVED',
        'REJECTED', 'RESET_TO_PENDING', 'TERMS_AMENDED', 'LEGACY_MARKED')),
    from_status      VARCHAR(20),
    to_status        VARCHAR(20),
    actor_user_id    UUID,
    actor_role       VARCHAR(20),
    document_version INT,
    note             VARCHAR(1000),
    detail           TEXT,
    at               TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_mou_event_actor CHECK (action = 'LEGACY_MARKED' OR actor_user_id IS NOT NULL)
);

CREATE INDEX idx_mou_approval_event_contract ON mou_approval_event (contract_id, at);

CREATE FUNCTION mou_approval_event_is_final() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'mou_approval_event rows cannot be updated or deleted';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_mou_approval_event_final
    BEFORE UPDATE OR DELETE ON mou_approval_event
    FOR EACH ROW EXECUTE FUNCTION mou_approval_event_is_final();

-- Positions stay final, except that an approver may correct the salary while the MoU is PENDING (FR-012).
CREATE FUNCTION contract_position_guard() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION '% rows of a signed contract cannot be updated or deleted', TG_TABLE_NAME;
    END IF;
    IF NEW.id = OLD.id AND NEW.contract_id = OLD.contract_id AND NEW.number = OLD.number
       AND NEW.title IS NOT DISTINCT FROM OLD.title AND NEW.salary > 0
       AND EXISTS (SELECT 1 FROM mou_approval a WHERE a.contract_id = NEW.contract_id AND a.status = 'PENDING') THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION '% rows of a signed contract cannot be updated or deleted', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER trg_contract_position_final ON contract_position;

CREATE TRIGGER trg_contract_position_final
    BEFORE UPDATE OR DELETE ON contract_position
    FOR EACH ROW EXECUTE FUNCTION contract_position_guard();

-- Backfill: every MoU recorded before the approval step is approved as it stands.
INSERT INTO mou_approval (id, contract_id, status, round, version, created_at, updated_at)
SELECT gen_random_uuid(), id, 'APPROVED_LEGACY', 0, 0, now(), now() FROM contract WHERE state = 'ACTIVE';

INSERT INTO mou_approval_event (id, contract_id, round, action, to_status, at)
SELECT gen_random_uuid(), contract_id, 0, 'LEGACY_MARKED', 'APPROVED_LEGACY', now() FROM mou_approval;
