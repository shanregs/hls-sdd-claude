-- Constitution v2.3.0: password sign-in may be identified by phone number or an optional
-- username; password reset may be delivered to phone (SMS) or an optional registered email.
ALTER TABLE app_user
    ADD COLUMN username        VARCHAR(30),
    ADD COLUMN username_lower  VARCHAR(30),
    ADD COLUMN email           VARCHAR(255);

CREATE UNIQUE INDEX uq_app_user_username_lower ON app_user (username_lower) WHERE username_lower IS NOT NULL;
CREATE UNIQUE INDEX uq_app_user_email ON app_user (email) WHERE email IS NOT NULL;
