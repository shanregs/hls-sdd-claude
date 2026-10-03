-- Organization module (spec 005): Manager records and the dated Zone-Manager / School-Manager
-- assignments. zone_id and school_id are plain ids validated through school's public API (no
-- cross-module foreign keys). "Current" = ends_on IS NULL; nothing is ever overwritten.

CREATE TABLE manager (
    id          UUID PRIMARY KEY,
    user_id     UUID        NOT NULL UNIQUE,
    active      BOOLEAN     NOT NULL DEFAULT true,
    version     BIGINT      NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL
);

CREATE TABLE zone_manager_assignment (
    id          UUID PRIMARY KEY,
    zone_id     UUID NOT NULL,
    manager_id  UUID NOT NULL REFERENCES manager (id),
    starts_on   DATE NOT NULL,
    ends_on     DATE
);

CREATE UNIQUE INDEX uq_zone_manager_current
    ON zone_manager_assignment (zone_id, manager_id) WHERE ends_on IS NULL;
CREATE INDEX idx_zone_manager_manager ON zone_manager_assignment (manager_id);

CREATE TABLE school_manager_assignment (
    id          UUID PRIMARY KEY,
    school_id   UUID NOT NULL,
    manager_id  UUID NOT NULL REFERENCES manager (id),
    starts_on   DATE NOT NULL,
    ends_on     DATE
);

CREATE UNIQUE INDEX uq_school_manager_current
    ON school_manager_assignment (school_id) WHERE ends_on IS NULL;
CREATE INDEX idx_school_manager_manager ON school_manager_assignment (manager_id);
