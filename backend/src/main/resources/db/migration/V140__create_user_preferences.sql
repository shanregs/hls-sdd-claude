-- Spec 039: personal theme preference, one row per user; no row means the defaults (Classic, device mode).
-- Version 140 sits above the planned numbers of parallel specs (V130 is 030).
CREATE TABLE user_preferences (
    user_id        UUID PRIMARY KEY REFERENCES app_user (id) ON DELETE CASCADE,
    theme_preset   VARCHAR(30) NOT NULL DEFAULT 'classic',
    theme_mode     VARCHAR(10) NOT NULL DEFAULT 'device' CHECK (theme_mode IN ('light', 'dark', 'device')),
    custom_accent  VARCHAR(7) CHECK (custom_accent ~ '^#[0-9A-F]{6}$'),
    sidebar_dark   BOOLEAN NOT NULL DEFAULT FALSE,
    changed_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
