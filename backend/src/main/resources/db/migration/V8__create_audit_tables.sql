-- Audit module: the single append-only store for login history, change history, and user
-- activity (Constitution Principle I, VII). Rows are never updated or deleted by the application;
-- corrections are new rows. No table here carries a foreign key into another module's tables
-- (data-model.md).

CREATE TABLE login_history_entry (
    id               UUID PRIMARY KEY,
    occurred_at      TIMESTAMPTZ NOT NULL,
    source_event_id  UUID        NOT NULL UNIQUE,
    user_id          UUID,
    phone_masked     VARCHAR(20) NOT NULL,
    method           VARCHAR(20) NOT NULL,
    event_type       VARCHAR(30) NOT NULL,
    outcome          VARCHAR(255) NOT NULL
);

CREATE INDEX idx_login_history_entry_user_occurred ON login_history_entry (user_id, occurred_at DESC);
CREATE INDEX idx_login_history_entry_occurred ON login_history_entry (occurred_at DESC);

CREATE TABLE change_history_entry (
    id               UUID PRIMARY KEY,
    occurred_at      TIMESTAMPTZ NOT NULL,
    source_event_id  UUID        NOT NULL UNIQUE,
    actor_user_id    UUID        NOT NULL,
    entity_type      VARCHAR(60) NOT NULL,
    entity_id        VARCHAR(255) NOT NULL,
    field            VARCHAR(60) NOT NULL,
    before_value     TEXT,
    after_value      TEXT
);

CREATE INDEX idx_change_history_entry_actor_occurred ON change_history_entry (actor_user_id, occurred_at DESC);
CREATE INDEX idx_change_history_entry_type_occurred ON change_history_entry (entity_type, occurred_at DESC);

CREATE TABLE user_activity_entry (
    id                 UUID PRIMARY KEY,
    occurred_at        TIMESTAMPTZ NOT NULL,
    source_event_id    UUID        NOT NULL UNIQUE,
    actor_user_id      UUID,
    affected_user_id   UUID        NOT NULL,
    action             VARCHAR(40) NOT NULL,
    detail             TEXT
);

CREATE INDEX idx_user_activity_entry_affected_occurred ON user_activity_entry (affected_user_id, occurred_at DESC);
CREATE INDEX idx_user_activity_entry_occurred ON user_activity_entry (occurred_at DESC);
