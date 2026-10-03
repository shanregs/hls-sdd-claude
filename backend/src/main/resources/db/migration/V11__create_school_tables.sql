-- School module (spec 005): Zones, Places and Schools. A School is located in exactly one Place and
-- its Zone is its Place's Zone (spec clarification Q1), so the School stores only place_id.
-- Manager/Teacher references to these ids live in other modules' tables without foreign keys.

CREATE TABLE zone (
    id          UUID PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    version     BIGINT       NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL
);

CREATE UNIQUE INDEX uq_zone_name_lower ON zone (lower(name));

CREATE TABLE place (
    id          UUID PRIMARY KEY,
    zone_id     UUID         NOT NULL REFERENCES zone (id),
    name        VARCHAR(160) NOT NULL,
    pin_code    CHAR(6)      NOT NULL CHECK (pin_code ~ '^[0-9]{6}$'),
    created_at  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_place_zone ON place (zone_id);
CREATE INDEX idx_place_pin_code ON place (pin_code);
CREATE INDEX idx_place_name_lower ON place (lower(name));

CREATE TABLE school (
    id               UUID PRIMARY KEY,
    name             VARCHAR(200) NOT NULL,
    place_id         UUID         NOT NULL REFERENCES place (id),
    address          TEXT         NOT NULL,
    contact_person   VARCHAR(120),
    contact_phone    VARCHAR(20),
    billing_contact  VARCHAR(200),
    active           BOOLEAN      NOT NULL DEFAULT true,
    version          BIGINT       NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_school_place ON school (place_id);
CREATE INDEX idx_school_active ON school (active);
CREATE INDEX idx_school_name_lower ON school (lower(name));
