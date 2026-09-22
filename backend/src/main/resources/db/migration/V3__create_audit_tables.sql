-- Audit module (specs/004-audit). See data-model.md for field rationale.
-- V3, not V1 — Identity & Access (specs/002) claimed V1, Organization (specs/003) claimed V2.

CREATE TABLE audit_entry (
    id              UUID PRIMARY KEY,
    sequence_no     BIGSERIAL   NOT NULL,
    source_module   VARCHAR(64)  NOT NULL,
    entity_type     VARCHAR(128) NOT NULL,
    entity_id       VARCHAR(128) NOT NULL,
    action          VARCHAR(32)  NOT NULL CHECK (action IN ('CREATED', 'UPDATED', 'CORRECTED')),
    summary         TEXT         NOT NULL,
    before_value    TEXT,
    after_value     TEXT,
    actor_user_id   UUID         NOT NULL,
    actor_role      VARCHAR(32),
    occurred_at     TIMESTAMPTZ  NOT NULL,
    request_id      VARCHAR(64)
);

-- research.md §5: sequence_no (database-assigned, monotonic) is the real
-- ordering key for a record's history — occurred_at is for display only and
-- can collide under concurrent writes within the same instant (Edge Case 2).
-- FR-007/FR-011: history lookup is always by (entity_type, entity_id), ordered
-- by sequence_no.
CREATE INDEX ix_audit_entry_entity ON audit_entry (entity_type, entity_id, sequence_no);
