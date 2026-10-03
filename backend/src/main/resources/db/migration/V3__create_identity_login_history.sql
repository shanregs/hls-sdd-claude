-- Identity module: append-only login-history events (Constitution Principle I, FR-019).
-- Rows are never updated or deleted by the application; corrections are new rows.
CREATE TABLE login_history_event (
    id                  UUID PRIMARY KEY,
    occurred_at         TIMESTAMPTZ  NOT NULL,
    user_id             UUID REFERENCES app_user (id),
    phone_masked        VARCHAR(20)  NOT NULL,
    method              VARCHAR(20)  NOT NULL,
    event_type          VARCHAR(30)  NOT NULL,
    outcome             VARCHAR(255) NOT NULL,
    client_ip           VARCHAR(64),
    device_description  VARCHAR(255)
);

CREATE INDEX idx_login_history_user_id ON login_history_event (user_id);
CREATE INDEX idx_login_history_occurred_at ON login_history_event (occurred_at);
