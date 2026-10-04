-- Spec 018 (Android app foundation): record where an action came from.
-- Adds client-context columns (source, app version, location) to the audit tables and the session
-- table, and creates the append-only API Access trail. All additions are defaulted or nullable, so
-- existing rows stay valid. Audit rows are only ever inserted (Constitution Principle I).

-- ---- login history: source, app version, location, rooted-device flag -------------------------
ALTER TABLE login_history_entry
    ADD COLUMN source               VARCHAR(10)  NOT NULL DEFAULT 'WEB',
    ADD COLUMN app_version          VARCHAR(20),
    ADD COLUMN location_status      VARCHAR(20)  NOT NULL DEFAULT 'NOT_APPLICABLE',
    ADD COLUMN latitude             NUMERIC(9,6),
    ADD COLUMN longitude            NUMERIC(9,6),
    ADD COLUMN accuracy_meters      REAL,
    ADD COLUMN location_captured_at TIMESTAMPTZ,
    ADD COLUMN device_rooted        BOOLEAN      NOT NULL DEFAULT FALSE;

ALTER TABLE login_history_entry
    ADD CONSTRAINT chk_login_history_source CHECK (source IN ('WEB', 'ANDROID')),
    ADD CONSTRAINT chk_login_history_location_status CHECK (location_status IN
        ('AVAILABLE', 'PERMISSION_DENIED', 'SERVICES_OFF', 'NO_FIX', 'INVALID', 'OTHER', 'NOT_APPLICABLE')),
    ADD CONSTRAINT chk_login_history_location CHECK (
        (location_status = 'AVAILABLE'
            AND latitude IS NOT NULL AND longitude IS NOT NULL
            AND accuracy_meters IS NOT NULL AND location_captured_at IS NOT NULL
            AND latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180)
        OR (location_status <> 'AVAILABLE'
            AND latitude IS NULL AND longitude IS NULL
            AND accuracy_meters IS NULL AND location_captured_at IS NULL));

-- ---- user activity: source, app version, location ---------------------------------------------
ALTER TABLE user_activity_entry
    ADD COLUMN source               VARCHAR(10)  NOT NULL DEFAULT 'WEB',
    ADD COLUMN app_version          VARCHAR(20),
    ADD COLUMN location_status      VARCHAR(20)  NOT NULL DEFAULT 'NOT_APPLICABLE',
    ADD COLUMN latitude             NUMERIC(9,6),
    ADD COLUMN longitude            NUMERIC(9,6),
    ADD COLUMN accuracy_meters      REAL,
    ADD COLUMN location_captured_at TIMESTAMPTZ;

ALTER TABLE user_activity_entry
    ADD CONSTRAINT chk_user_activity_source CHECK (source IN ('WEB', 'ANDROID')),
    ADD CONSTRAINT chk_user_activity_location_status CHECK (location_status IN
        ('AVAILABLE', 'PERMISSION_DENIED', 'SERVICES_OFF', 'NO_FIX', 'INVALID', 'OTHER', 'NOT_APPLICABLE')),
    ADD CONSTRAINT chk_user_activity_location CHECK (
        (location_status = 'AVAILABLE'
            AND latitude IS NOT NULL AND longitude IS NOT NULL
            AND accuracy_meters IS NOT NULL AND location_captured_at IS NOT NULL
            AND latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180)
        OR (location_status <> 'AVAILABLE'
            AND latitude IS NULL AND longitude IS NULL
            AND accuracy_meters IS NULL AND location_captured_at IS NULL));

-- ---- session: which client signed in ------------------------------------------------------------
ALTER TABLE session
    ADD COLUMN client_type VARCHAR(10) NOT NULL DEFAULT 'WEB',
    ADD COLUMN app_version VARCHAR(20);

ALTER TABLE session
    ADD CONSTRAINT chk_session_client_type CHECK (client_type IN ('WEB', 'ANDROID'));

-- ---- API Access trail: one row per request made by the Android app (FR-023a) -------------------
-- No request or response content, headers, query strings or ids are stored; route_template is the
-- matched route pattern. No foreign key into another module's tables, like the other audit tables.
CREATE TABLE api_access_entry (
    id                   UUID PRIMARY KEY,
    occurred_at          TIMESTAMPTZ  NOT NULL,
    source_event_id      UUID         NOT NULL UNIQUE,
    user_id              UUID,
    session_id           UUID,
    http_method          VARCHAR(10)  NOT NULL,
    route_template       VARCHAR(200) NOT NULL,
    status_code          SMALLINT     NOT NULL,
    source               VARCHAR(10)  NOT NULL,
    app_version          VARCHAR(20),
    location_status      VARCHAR(20)  NOT NULL,
    latitude             NUMERIC(9,6),
    longitude            NUMERIC(9,6),
    accuracy_meters      REAL,
    location_captured_at TIMESTAMPTZ,
    CONSTRAINT chk_api_access_source CHECK (source IN ('WEB', 'ANDROID')),
    CONSTRAINT chk_api_access_location_status CHECK (location_status IN
        ('AVAILABLE', 'PERMISSION_DENIED', 'SERVICES_OFF', 'NO_FIX', 'INVALID', 'OTHER', 'NOT_APPLICABLE')),
    CONSTRAINT chk_api_access_location CHECK (
        (location_status = 'AVAILABLE'
            AND latitude IS NOT NULL AND longitude IS NOT NULL
            AND accuracy_meters IS NOT NULL AND location_captured_at IS NOT NULL
            AND latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180)
        OR (location_status <> 'AVAILABLE'
            AND latitude IS NULL AND longitude IS NULL
            AND accuracy_meters IS NULL AND location_captured_at IS NULL))
);

CREATE INDEX idx_api_access_entry_occurred ON api_access_entry (occurred_at DESC);
CREATE INDEX idx_api_access_entry_user_occurred ON api_access_entry (user_id, occurred_at DESC);
