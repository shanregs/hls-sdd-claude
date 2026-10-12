-- Spec 011: runtime system and security settings. One row per overridden setting; the catalog (keys, ranges,
-- defaults) is code. A missing row means "use the deployed property, then the built-in default".
CREATE TABLE system_setting (
    setting_key VARCHAR(80)  PRIMARY KEY,
    value       VARCHAR(60)  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by  UUID
);

-- The OTP throttle (spec 004) already lived in a table; carry its values over, then remove the table.
INSERT INTO system_setting (setting_key, value, updated_at, updated_by)
SELECT 'otp.resend-cooldown-seconds', resend_cooldown_seconds::text, updated_at, NULL FROM otp_policy_settings;
INSERT INTO system_setting (setting_key, value, updated_at, updated_by)
SELECT 'otp.max-consecutive-requests', max_consecutive_requests::text, updated_at, NULL FROM otp_policy_settings;
INSERT INTO system_setting (setting_key, value, updated_at, updated_by)
SELECT 'otp.lockout-hours', consecutive_request_lockout_hours::text, updated_at, NULL FROM otp_policy_settings;

DROP TABLE otp_policy_settings;
