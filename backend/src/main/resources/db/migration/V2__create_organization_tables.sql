-- Organization module (specs/003-organization-scoping). See data-model.md for field rationale.
-- V2, not V1 — Identity & Access (specs/002) already claimed V1__create_identity_tables.sql.

CREATE TABLE organization_school_assignment (
    id             UUID PRIMARY KEY,
    school_id      UUID        NOT NULL,
    manager_id     UUID        NOT NULL,
    effective_from TIMESTAMPTZ NOT NULL,
    effective_to   TIMESTAMPTZ,
    assigned_by    UUID        NOT NULL,
    assigned_at    TIMESTAMPTZ NOT NULL
);

-- research.md §1: at most one CURRENT (effective_to IS NULL) row per school_id,
-- enforced at the DB level so a race between two concurrent assign/reassign
-- requests can never leave two open rows for the same school.
CREATE UNIQUE INDEX ux_school_assignment_current
    ON organization_school_assignment (school_id)
    WHERE effective_to IS NULL;

CREATE INDEX ix_school_assignment_school_id ON organization_school_assignment (school_id);

CREATE TABLE organization_teacher_assignment (
    id             UUID PRIMARY KEY,
    teacher_id     UUID        NOT NULL,
    manager_id     UUID        NOT NULL,
    effective_from TIMESTAMPTZ NOT NULL,
    effective_to   TIMESTAMPTZ,
    assigned_by    UUID        NOT NULL,
    assigned_at    TIMESTAMPTZ NOT NULL
);

-- Independent of organization_school_assignment (FR-013) — its own partial
-- unique index, not shared with the school table.
CREATE UNIQUE INDEX ux_teacher_assignment_current
    ON organization_teacher_assignment (teacher_id)
    WHERE effective_to IS NULL;

CREATE INDEX ix_teacher_assignment_teacher_id ON organization_teacher_assignment (teacher_id);
