-- Organization module Zone-Manager coverage (specs/006-zone-scoping, reworked). See data-model.md.
-- V8 — Identity=V1, Organization=V2, Audit=V3, School/Zone=V4, School/Places=V5, Teacher=V6, Teacher-Salary-History=V7.
-- Zone itself is NOT defined here — it is `school`'s table (school_zone, V4). This table only
-- references a zone_id opaquely, validated live through school.api.ZoneQueries, not a DB FK.

CREATE TABLE organization_zone_manager_assignment (
    id             UUID        PRIMARY KEY,
    zone_id        UUID        NOT NULL,
    manager_id     UUID        NOT NULL,
    effective_from TIMESTAMPTZ NOT NULL,
    effective_to   TIMESTAMPTZ,
    assigned_by    UUID        NOT NULL,
    assigned_at    TIMESTAMPTZ NOT NULL
);

-- research.md §2: at most one CURRENT row per (zone_id, manager_id) pair — NOT zone_id alone,
-- since a Zone may have more than one currently-covering Manager at once.
CREATE UNIQUE INDEX ux_zone_manager_assignment_current
    ON organization_zone_manager_assignment (zone_id, manager_id)
    WHERE effective_to IS NULL;

CREATE INDEX ix_zone_manager_assignment_zone_id
    ON organization_zone_manager_assignment (zone_id)
    WHERE effective_to IS NULL;
