-- Teacher module (specs/005-teacher). See data-model.md for field rationale.
-- V6 — Identity=V1, Organization=V2, Audit=V3, School/Zone=V4, School/Places=V5.
-- No bank-detail columns (spec.md Assumptions, scope correction 2026-09-22) —
-- history (FR-005) is realized through the audit module, not a second table here.

CREATE TABLE teacher_profile (
    id                  UUID PRIMARY KEY,
    name                VARCHAR(255)   NOT NULL,
    phone               VARCHAR(32)    NOT NULL,
    email               VARCHAR(255),
    hls_offered_salary  NUMERIC(12,2)  NOT NULL,
    status              VARCHAR(20)    NOT NULL,
    created_at          TIMESTAMPTZ    NOT NULL,
    created_by          UUID           NOT NULL
);
