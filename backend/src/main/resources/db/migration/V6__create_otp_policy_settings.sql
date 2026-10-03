-- Configurable OTP request throttling policy (FR-028/FR-029), read at runtime rather than fixed
-- deployment config, so it can be tuned without a redeploy. Single-row table (id is always 1).
CREATE TABLE otp_policy_settings (
    id                                  SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    resend_cooldown_seconds             INTEGER NOT NULL DEFAULT 30,
    max_consecutive_requests            INTEGER NOT NULL DEFAULT 5,
    consecutive_request_lockout_hours   INTEGER NOT NULL DEFAULT 4,
    updated_at                          TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO otp_policy_settings (id, resend_cooldown_seconds, max_consecutive_requests, consecutive_request_lockout_hours)
VALUES (1, 30, 5, 4);
