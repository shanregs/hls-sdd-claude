-- Spec 017a: the substitution workflow, and amendment A2 to spec 008 (loss-of-pay flag per day, optional Principal
-- confirmation). No foreign keys cross modules: teacher, school and user ids are plain UUIDs checked through the owning
-- module's public interface. The version number leaves a gap above V26 so work built in parallel does not collide.

-- 008 A2: the loss-of-pay flag and the optional Principal confirmation of a day.
ALTER TABLE attendance_mark
    ADD COLUMN loss_of_pay            BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN principal_confirmed    BOOLEAN,
    ADD COLUMN principal_name         VARCHAR(120),
    ADD COLUMN principal_note         VARCHAR(500),
    ADD COLUMN principal_recorded_by  UUID,
    ADD COLUMN principal_recorded_at  TIMESTAMPTZ;

-- Existing leave-category marks: an absence with no approved leave behind it is unpaid, and so is a Loss-of-Pay leave day.
UPDATE attendance_mark m
   SET loss_of_pay = TRUE
  FROM attendance_status_code c
 WHERE c.id = m.status_code_id
   AND c.category = 'LEAVE'
   AND (m.leave_request_id IS NULL
        OR EXISTS (SELECT 1
                     FROM leave_request r
                     JOIN leave_type t ON t.id = r.leave_type_id
                    WHERE r.id = m.leave_request_id AND t.code = 'LOP'));

CREATE INDEX idx_attendance_mark_lop ON attendance_mark (teacher_id, mark_date) WHERE loss_of_pay;

CREATE TABLE substitution_requirement (
    id                    UUID PRIMARY KEY,
    teacher_id            UUID         NOT NULL,
    school_id             UUID         NOT NULL,
    duty_date             DATE         NOT NULL,
    day_value             NUMERIC(3,2) NOT NULL CHECK (day_value IN (0.50, 1.00)),
    kind                  VARCHAR(8)   NOT NULL CHECK (kind IN ('ABSENT', 'LEAVE')),
    status                VARCHAR(16)  NOT NULL CHECK (status IN ('OPEN', 'ASSIGNED', 'MANAGER_COVER', 'CANCELLED')),
    substitute_teacher_id UUID,
    cover_user_id         UUID,
    note                  VARCHAR(500),
    informed_at           TIMESTAMPTZ,
    informed_by           UUID,
    informed_contact      VARCHAR(120),
    informed_method       VARCHAR(10) CHECK (informed_method IN ('PHONE', 'IN_PERSON', 'EMAIL', 'MESSAGE', 'OTHER')),
    informed_note         VARCHAR(500),
    raised_at             TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL,
    version               BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_substitution_teacher_date UNIQUE (teacher_id, duty_date),
    CONSTRAINT ck_substitution_substitute CHECK ((status = 'ASSIGNED') = (substitute_teacher_id IS NOT NULL)),
    CONSTRAINT ck_substitution_informed CHECK (
        (informed_at IS NULL AND informed_by IS NULL AND informed_contact IS NULL AND informed_method IS NULL)
        OR (informed_at IS NOT NULL AND informed_by IS NOT NULL AND informed_contact IS NOT NULL AND informed_method IS NOT NULL))
);

CREATE INDEX ix_substitution_school_date ON substitution_requirement (school_id, duty_date);
CREATE INDEX ix_substitution_status_date ON substitution_requirement (status, duty_date);
CREATE INDEX ix_substitution_substitute_date ON substitution_requirement (substitute_teacher_id, duty_date)
    WHERE status = 'ASSIGNED';

CREATE TABLE substitution_history (
    id                    UUID PRIMARY KEY,
    requirement_id        UUID        NOT NULL REFERENCES substitution_requirement (id),
    action                VARCHAR(12) NOT NULL CHECK (action IN
        ('RAISED', 'UPDATED', 'ASSIGNED', 'REASSIGNED', 'RELEASED', 'COVERED', 'INFORMED', 'CANCELLED', 'REOPENED')),
    from_status           VARCHAR(16),
    to_status             VARCHAR(16) NOT NULL,
    substitute_teacher_id UUID,
    actor_user_id         UUID,
    note                  VARCHAR(500),
    at                    TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_substitution_history_requirement ON substitution_history (requirement_id, at);

CREATE FUNCTION substitution_history_rows_are_final() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% rows are append-only and cannot be updated or deleted', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_substitution_history_final
    BEFORE UPDATE OR DELETE ON substitution_history
    FOR EACH ROW EXECUTE FUNCTION substitution_history_rows_are_final();

-- Notification types. A closed list here would be overwritten by whichever spec widens it next, so the check only
-- insists on the shape of the name; the application enum is the authority.
ALTER TABLE notification DROP CONSTRAINT IF EXISTS notification_type_check;
ALTER TABLE notification ADD CONSTRAINT notification_type_check CHECK (type ~ '^[A-Z][A-Z_]*$');
