-- Index review (missing and mis-shaped indexes), from a static read of V1-V18 against the actual queries.
-- Nothing is dropped on suspicion alone: the unused-looking indexes (name btrees on place/school/teacher,
-- attendance_mark date indexes, school.active) are left for a check against pg_stat_user_indexes.

-- ---------------------------------------------------------------------------------------------------
-- Missing
-- ---------------------------------------------------------------------------------------------------

-- Change History filters by entity id (with the entity type) and sorts newest first; the existing
-- (entity_type, occurred_at) index cannot serve the entity_id predicate.
CREATE INDEX idx_change_history_entry_entity_occurred
    ON change_history_entry (entity_type, entity_id, occurred_at DESC);

-- User Activity filters by actor; only affected_user_id was indexed.
CREATE INDEX idx_user_activity_entry_actor_occurred
    ON user_activity_entry (actor_user_id, occurred_at DESC);

-- OtpService verifies a code with findByCodeHashAndPurpose, which had no usable index. Not unique: the
-- hash is a SHA-256 of a short numeric code, so two rows can legitimately share it.
CREATE INDEX idx_one_time_code_hash_purpose ON one_time_code (code_hash, purpose);

-- Leave revoke and cancel read the history rows a request wrote (findByLeaveRequestId); V17 indexed the
-- same column on attendance_mark but not here.
CREATE INDEX idx_attendance_mark_history_leave_request
    ON attendance_mark_history (leave_request_id) WHERE leave_request_id IS NOT NULL;

-- ---------------------------------------------------------------------------------------------------
-- Mis-shaped: the old index does not match how the query filters and sorts, so replace it
-- ---------------------------------------------------------------------------------------------------

-- A Teacher's own history: where teacher_id = ? and status in (...) order by created_at desc.
-- (Overlap checks are served by the ex_leave_request_no_overlap exclusion constraint, not by this index.)
DROP INDEX idx_leave_request_teacher_first;
CREATE INDEX idx_leave_request_teacher_created ON leave_request (teacher_id, created_at DESC);

-- The supervisor list and Pending count: where status in (...) ... order by created_at desc.
DROP INDEX idx_leave_request_status_first;
CREATE INDEX idx_leave_request_status_created ON leave_request (status, created_at DESC);

-- The renewal lookup returns at most one row (findByCredentialHash returns an Optional) and the hash is a
-- SHA-256 of a random token, so make it unique: same lookup speed, and a duplicate can no longer exist.
DROP INDEX idx_renewal_credential_hash;
CREATE UNIQUE INDEX uq_renewal_credential_hash ON renewal_credential (credential_hash);

-- Notifications (V18): one partial index now serves both the unread count and the newest-first unread
-- list; merging an attendance notification only scans the recipient's unread rows, so the separate merge
-- index is no longer needed.
DROP INDEX idx_notification_unread;
DROP INDEX idx_notification_merge;
CREATE INDEX idx_notification_unread_updated
    ON notification (recipient_user_id, updated_at DESC) WHERE read_at IS NULL;
