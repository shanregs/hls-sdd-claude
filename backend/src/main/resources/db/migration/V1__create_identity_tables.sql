-- Identity & Access module (specs/002-identity-access). See data-model.md for field rationale.
-- NOTE: this is the first Flyway migration in the codebase. If Organization's (spec 003) own
-- migration is implemented later, it must use V2 or higher, not renumber this one.

CREATE TABLE identity_user (
    id                   UUID PRIMARY KEY,
    display_name         VARCHAR(255)   NOT NULL,
    phone_number         VARCHAR(32)    NOT NULL,
    email                VARCHAR(255),
    password_hash        VARCHAR(255),
    mfa_enabled          BOOLEAN        NOT NULL DEFAULT FALSE,
    mfa_method           VARCHAR(16),
    active               BOOLEAN        NOT NULL DEFAULT TRUE,
    failed_attempt_count INTEGER        NOT NULL DEFAULT 0,
    locked_until         TIMESTAMPTZ,
    linked_teacher_id    UUID,
    linked_manager_id    UUID,
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_identity_user_phone_number UNIQUE (phone_number)
);

CREATE TABLE identity_user_role (
    user_id UUID        NOT NULL REFERENCES identity_user (id),
    role    VARCHAR(32) NOT NULL,
    CONSTRAINT pk_identity_user_role PRIMARY KEY (user_id, role),
    CONSTRAINT ck_identity_user_role_value CHECK (role IN ('DIRECTOR', 'MANAGER', 'ADMIN', 'ACCOUNTS_OFFICER', 'TEACHER'))
);

CREATE TABLE identity_session (
    id                 UUID PRIMARY KEY,
    user_id            UUID         NOT NULL REFERENCES identity_user (id),
    channel            VARCHAR(16)  NOT NULL,
    refresh_token_hash VARCHAR(255) NOT NULL,
    issued_at          TIMESTAMPTZ  NOT NULL,
    last_active_at     TIMESTAMPTZ  NOT NULL,
    expires_at         TIMESTAMPTZ  NOT NULL,
    revoked            BOOLEAN      NOT NULL DEFAULT FALSE,
    revoked_at         TIMESTAMPTZ,
    device_label       VARCHAR(255),
    CONSTRAINT ck_identity_session_channel CHECK (channel IN ('WEB', 'MOBILE'))
);

CREATE INDEX ix_identity_session_user_id ON identity_session (user_id);
CREATE INDEX ix_identity_session_refresh_token_hash ON identity_session (refresh_token_hash);

CREATE TABLE identity_auth_audit_entry (
    id                  UUID PRIMARY KEY,
    actor_user_id       UUID,
    attempted_identifier VARCHAR(255),
    action              VARCHAR(32) NOT NULL,
    role_at_time        VARCHAR(255),
    occurred_at         TIMESTAMPTZ NOT NULL,
    request_id          VARCHAR(64),
    CONSTRAINT ck_identity_audit_action CHECK (action IN (
        'LOGIN_SUCCESS', 'LOGIN_FAILURE', 'ACCESS_DENIED', 'SESSION_REVOKED',
        'ACCOUNT_LOCKED', 'ACCOUNT_UNLOCKED', 'PASSWORD_RESET_REQUESTED', 'PASSWORD_RESET_COMPLETED'
    ))
);

CREATE INDEX ix_identity_audit_actor_user_id ON identity_auth_audit_entry (actor_user_id);
CREATE INDEX ix_identity_audit_occurred_at ON identity_auth_audit_entry (occurred_at);

-- "identifier" is a phone number for Teacher OTP login (FR-008) and for SMS-method
-- MFA (FR-018), or an email address for EMAIL-method MFA (FR-018) — the OTP/MFA
-- challenge mechanism is the same regardless of which channel delivers the code.
CREATE TABLE identity_otp_challenge (
    id            UUID PRIMARY KEY,
    identifier    VARCHAR(255) NOT NULL,
    code_hash     VARCHAR(255) NOT NULL,
    expires_at    TIMESTAMPTZ  NOT NULL,
    consumed      BOOLEAN      NOT NULL DEFAULT FALSE,
    attempt_count INTEGER      NOT NULL DEFAULT 0
);

CREATE INDEX ix_identity_otp_challenge_identifier ON identity_otp_challenge (identifier);

CREATE TABLE identity_password_reset_token (
    id         UUID PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES identity_user (id),
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,
    consumed   BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX ix_identity_password_reset_token_user_id ON identity_password_reset_token (user_id);
