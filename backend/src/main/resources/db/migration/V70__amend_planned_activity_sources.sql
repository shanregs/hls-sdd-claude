-- Spec 035: additive facts the Activity Calendar needs from the specs 016 and 023 sources.
-- No backfill: rows completed or held before this migration keep NULL (shown as Done without a "done late" mark).
ALTER TABLE marketing_activity ADD COLUMN completed_on DATE NULL;
ALTER TABLE campus_drive ADD COLUMN held_on DATE NULL;

CREATE TABLE drive_date_change (
    id         UUID PRIMARY KEY,
    drive_id   UUID        NOT NULL REFERENCES campus_drive (id),
    old_date   DATE        NOT NULL,
    new_date   DATE        NOT NULL,
    changed_by UUID        NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_drive_date_change_drive ON drive_date_change (drive_id);

CREATE FUNCTION drive_date_change_is_final() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'drive_date_change rows cannot be updated or deleted';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_drive_date_change_final
    BEFORE UPDATE OR DELETE ON drive_date_change
    FOR EACH ROW EXECUTE FUNCTION drive_date_change_is_final();
