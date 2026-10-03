-- Identity module: signed-in sessions and their rotating renewal credentials (FR-009/FR-010).
CREATE TABLE session (
    id                  UUID PRIMARY KEY,
    user_id             UUID        NOT NULL REFERENCES app_user (id),
    device_description  VARCHAR(255),
    signed_in_at        TIMESTAMPTZ NOT NULL,
    last_activity_at    TIMESTAMPTZ NOT NULL,
    status              VARCHAR(20) NOT NULL
);

CREATE TABLE renewal_credential (
    id               UUID PRIMARY KEY,
    session_id       UUID         NOT NULL REFERENCES session (id),
    credential_hash  VARCHAR(255) NOT NULL,
    issued_at        TIMESTAMPTZ  NOT NULL,
    used_at          TIMESTAMPTZ,
    superseded_by    UUID REFERENCES renewal_credential (id)
);

CREATE INDEX idx_renewal_credential_hash ON renewal_credential (credential_hash);
CREATE INDEX idx_session_user_id ON session (user_id);
