-- In-app notifications (spec 010). One row per recipient user. No cross-module foreign keys.

CREATE TABLE notification (
    id                UUID PRIMARY KEY,
    recipient_user_id UUID         NOT NULL,
    type              VARCHAR(30)  NOT NULL CHECK (type IN (
        'LEAVE_DECIDED', 'LEAVE_REQUESTED', 'LEAVE_CANCELLED',
        'ATTENDANCE_CHANGED', 'ATTENDANCE_MONTH_LOCKED', 'ATTENDANCE_MONTH_REOPENED')),
    title             VARCHAR(80)  NOT NULL,
    message           VARCHAR(300) NOT NULL,
    link              VARCHAR(200),
    channel           VARCHAR(10)  NOT NULL DEFAULT 'IN_APP' CHECK (channel IN ('IN_APP')),
    group_key         VARCHAR(120),
    detail            VARCHAR(600),
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    read_at           TIMESTAMPTZ,
    CONSTRAINT ck_notification_link CHECK (link IS NULL OR link LIKE '/%')
);

-- The list: newest activity first.
CREATE INDEX idx_notification_recipient_updated ON notification (recipient_user_id, updated_at DESC);
-- The unread count.
CREATE INDEX idx_notification_unread ON notification (recipient_user_id) WHERE read_at IS NULL;
-- Merging attendance-change notifications.
CREATE INDEX idx_notification_merge ON notification (recipient_user_id, group_key)
    WHERE read_at IS NULL AND group_key IS NOT NULL;
-- Retention.
CREATE INDEX idx_notification_created ON notification (created_at);
