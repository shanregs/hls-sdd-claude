-- School module (specs/007-school-zone). See data-model.md for field rationale.
-- V4 — Identity=V1, Organization=V2, Audit=V3.
-- Deliberately minimal, pulled-forward slice of School Master Data: only
-- Zones and each School's current Zone (constitution Amendment 1.7.0).

CREATE TABLE school_zone (
    id         UUID PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    created_by UUID         NOT NULL
);

CREATE TABLE school_zone_assignment (
    id             UUID PRIMARY KEY,
    school_id      UUID        NOT NULL,
    zone_id        UUID        NOT NULL,
    effective_from TIMESTAMPTZ NOT NULL,
    effective_to   TIMESTAMPTZ,
    assigned_by    UUID        NOT NULL,
    assigned_at    TIMESTAMPTZ NOT NULL
);

-- research.md §1: at most one CURRENT (effective_to IS NULL) row per school_id,
-- enforced at the DB level, same technique as organization_school_assignment.
CREATE UNIQUE INDEX ux_school_zone_assignment_current
    ON school_zone_assignment (school_id)
    WHERE effective_to IS NULL;

CREATE INDEX ix_school_zone_assignment_zone_id ON school_zone_assignment (zone_id);
