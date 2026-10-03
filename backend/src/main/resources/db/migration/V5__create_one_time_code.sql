-- Sign-in and password-reset one-time codes (FR-006, FR-016). Sign-in OTP is SMS-only; password
-- reset may use SMS or, if registered, email (Constitution v2.3.0).
CREATE TABLE one_time_code (
    id                  UUID PRIMARY KEY,
    channel             VARCHAR(10)  NOT NULL,
    destination         VARCHAR(255) NOT NULL,
    purpose             VARCHAR(20)  NOT NULL,
    code_hash           VARCHAR(255) NOT NULL,
    expires_at          TIMESTAMPTZ  NOT NULL,
    used_at             TIMESTAMPTZ,
    wrong_attempt_count INTEGER      NOT NULL DEFAULT 0
);

CREATE INDEX idx_one_time_code_destination ON one_time_code (destination);
