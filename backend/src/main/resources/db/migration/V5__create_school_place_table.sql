-- School module Places slice (specs/008-school-places). See data-model.md.
-- V5 — Identity=V1, Organization=V2, Audit=V3, School/Zone=V4.
-- No uniqueness on name or pincode (FR-002) — real geography doesn't guarantee either.

CREATE TABLE school_place (
    id         UUID PRIMARY KEY,
    zone_id    UUID         NOT NULL,
    name       VARCHAR(255) NOT NULL,
    pincode    VARCHAR(20)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    created_by UUID         NOT NULL
);

CREATE INDEX ix_school_place_pincode ON school_place (pincode);
CREATE INDEX ix_school_place_name ON school_place (lower(name));
CREATE INDEX ix_school_place_zone_id ON school_place (zone_id);
