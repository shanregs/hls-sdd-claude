-- =====================================================================
-- HLS simplified data model - PostgreSQL 16 DDL
-- Source of truth: docs/simplified-requirements.md (origin/main), section 6.
-- 51 tables + 1 view. Real foreign keys everywhere.
--
-- Conventions
--   * ids: bigint generated always as identity (setting: text key)
--   * money: numeric(12,2), dates: date, timestamps: timestamptz (UTC)
--   * enums: text + CHECK (cheaper to change than CREATE TYPE)
--   * created_by / decided_by etc. always mean app_user.id
--   * "kind guard": employee has UNIQUE (id, kind); child tables that must
--     point to one kind of employee carry a constant kind column and a
--     composite FK, so the database (not only the code) refuses e.g.
--     a Manager being placed in a School.
--   * [MVP]     = needed to run payroll and billing for the first month
--     [PHASE 2] = recruitment, marketing, tasks, performance, transfers,
--                 training, content, expense budgets. The tables can be
--                 created on day one (they stay empty); only the screens
--                 come later.
--   * Index policy: at 200 Teachers / 50 Schools / 10 Zones almost every
--     table fits in memory, so only the lookups that every screen repeats
--     are indexed (marked "IDX"). Unique constraints give indexes for free.
--
-- Run:  psql -v ON_ERROR_STOP=1 -f schema.sql
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS btree_gist;   -- needed for the no-overlap exclusion constraints

-- ---------------------------------------------------------------------
-- Helper trigger function: append-only tables
-- ---------------------------------------------------------------------
CREATE FUNCTION forbid_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION '% is append-only (% refused)', TG_TABLE_NAME, TG_OP;
END $$;

-- =====================================================================
-- 1. PLATFORM  (7 tables)  [MVP]
-- =====================================================================

-- [MVP] Sign-in accounts. Roles are a fixed list held as an array (R2).
CREATE TABLE app_user (
  id                   bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  phone                text        NOT NULL,
  username             text,
  password_hash        text        NOT NULL,
  full_name            text        NOT NULL,
  roles                text[]      NOT NULL,
  active               boolean     NOT NULL DEFAULT true,
  failed_attempts      smallint    NOT NULL DEFAULT 0,
  locked_until         timestamptz,
  last_login_at        timestamptz,
  must_change_password boolean     NOT NULL DEFAULT false,
  created_at           timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT app_user_phone_uk UNIQUE (phone),
  CONSTRAINT app_user_username_uk UNIQUE (username),
  CONSTRAINT app_user_roles_ck CHECK (
    cardinality(roles) >= 1
    AND roles <@ ARRAY['ADMIN','DIRECTOR','MANAGER','TEACHER','CONTENT_CREATOR']::text[])
);
COMMENT ON TABLE app_user IS '[MVP] Sign-in account. roles is a text[] of the five fixed roles. Last-Admin guard is an application check inside one transaction.';

-- [MVP] SMS one-time codes. Hash only; at most 3 requests/phone/minute is an app rule.
CREATE TABLE otp_code (
  id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id     bigint      NOT NULL REFERENCES app_user(id),
  code_hash   text        NOT NULL,
  purpose     text        NOT NULL CHECK (purpose IN ('LOGIN','PASSWORD_RESET')),
  expires_at  timestamptz NOT NULL,
  consumed_at timestamptz,
  created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX otp_code_user_idx ON otp_code (user_id, created_at DESC);   -- IDX: throttle + verify
COMMENT ON TABLE otp_code IS '[MVP] One-time SMS codes (login and password reset). Purge rows older than a day.';

-- [MVP] One session token of ~12h renewed on activity. Admin "sign out user" sets revoked_at.
CREATE TABLE login_session (
  id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id      bigint      NOT NULL REFERENCES app_user(id),
  token_hash   text        NOT NULL,
  created_at   timestamptz NOT NULL DEFAULT now(),
  last_seen_at timestamptz NOT NULL DEFAULT now(),
  expires_at   timestamptz NOT NULL,
  revoked_at   timestamptz,
  user_agent   text,
  CONSTRAINT login_session_token_uk UNIQUE (token_hash)
);
CREATE INDEX login_session_user_idx ON login_session (user_id);          -- IDX: sign-out-user, deactivate
COMMENT ON TABLE login_session IS '[MVP] Session token (hash). Deactivating a user revokes all of their rows.';

-- [MVP] Key-value settings (incentive amount, payment due days, default substitute rate, lockout constants...).
CREATE TABLE setting (
  key         text PRIMARY KEY,
  value       text        NOT NULL,
  description text,
  updated_by  bigint      REFERENCES app_user(id),
  updated_at  timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE setting IS '[MVP] Key-value runtime settings. Changes are written to audit_log.';

-- [MVP] The single audit trail (replaces ~25 history/event tables and 4 audit tables).
CREATE TABLE audit_log (
  id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  at            timestamptz NOT NULL DEFAULT now(),
  actor_user_id bigint      REFERENCES app_user(id),     -- null = system action
  action        text        NOT NULL,                    -- e.g. ATTENDANCE_CORRECTED, PAYROLL_APPROVED
  entity_type   text        NOT NULL,                    -- table name
  entity_id     bigint,
  before_data   jsonb,
  after_data    jsonb,
  reason        text
);
CREATE INDEX audit_log_entity_idx ON audit_log (entity_type, entity_id);  -- IDX: "history of this record"
CREATE INDEX audit_log_at_idx     ON audit_log (at DESC);                 -- IDX: Admin search screen
CREATE TRIGGER audit_log_append_only BEFORE UPDATE OR DELETE ON audit_log
  FOR EACH ROW EXECUTE FUNCTION forbid_change();
COMMENT ON TABLE audit_log IS '[MVP] Append-only audit of people, placements, attendance corrections, money, approvals, settings. Keep 3 years (a purge by a DBA needs the trigger disabled, deliberately).';

-- [MVP] In-app bell (about 10 events). Inserted by the same code, same transaction as the cause.
CREATE TABLE notification (
  id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id    bigint      NOT NULL REFERENCES app_user(id),
  kind       text        NOT NULL,
  message    text        NOT NULL,
  link       text,
  created_at timestamptz NOT NULL DEFAULT now(),
  read_at    timestamptz
);
CREATE INDEX notification_user_idx ON notification (user_id, created_at DESC);  -- IDX: bell
COMMENT ON TABLE notification IS '[MVP] In-app notification. No push/SMS. Message must not reveal more than the reader may already see.';

-- [MVP] Metadata for every uploaded file (bills, MoU PDFs, evidence, content). Bytes live in a folder.
CREATE TABLE stored_file (
  id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  original_name     text        NOT NULL,
  content_type      text        NOT NULL,
  size_bytes        bigint      NOT NULL CHECK (size_bytes > 0),
  storage_path      text        NOT NULL,
  sha256            text,
  uploaded_by       bigint      NOT NULL REFERENCES app_user(id),
  uploaded_at       timestamptz NOT NULL DEFAULT now(),
  replaces_file_id  bigint      REFERENCES stored_file(id),     -- version chain (MoU PDF re-uploads)
  observation_id    bigint,                                     -- FK added below (observation attachments)
  CONSTRAINT stored_file_path_uk UNIQUE (storage_path)
);
CREATE INDEX stored_file_observation_idx ON stored_file (observation_id) WHERE observation_id IS NOT NULL;
COMMENT ON TABLE stored_file IS '[MVP] File metadata. replaces_file_id keeps all MoU document versions. observation_id is the only owner column that has no join table (observation attachments, max 5 - app rule).';

-- =====================================================================
-- 2. MASTER DATA  (8 tables)  [MVP]
-- =====================================================================

-- [MVP] Zones. manager FK added after employee exists.
CREATE TABLE zone (
  id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name         text    NOT NULL,
  manager_id   bigint,
  manager_kind text    NOT NULL DEFAULT 'MANAGER' CHECK (manager_kind = 'MANAGER'),
  active       boolean NOT NULL DEFAULT true,
  CONSTRAINT zone_name_uk UNIQUE (name)
);
COMMENT ON TABLE zone IS '[MVP] 10 Zones. One Zone Manager each (manager_id); a Manager may head several Zones.';

-- [MVP] Designations lookup: name, kind, suggested salary and Substitute default day rate.
CREATE TABLE designation (
  id                  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name                text    NOT NULL,
  kind                text    NOT NULL CHECK (kind IN ('TEACHER','MANAGER','STAFF','SUBSTITUTE')),
  suggested_salary    numeric(12,2) CHECK (suggested_salary >= 0),      -- a suggestion only (D12)
  substitute_day_rate numeric(12,2) CHECK (substitute_day_rate >= 0),
  active              boolean NOT NULL DEFAULT true,
  CONSTRAINT designation_name_uk UNIQUE (name)
);
COMMENT ON TABLE designation IS '[MVP] Designation list. No dated history; salary of record lives in employee_salary.';

-- [MVP] Teacher, Manager, staff and Substitute in one table.
CREATE TABLE employee (
  id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  kind           text    NOT NULL CHECK (kind IN ('TEACHER','MANAGER','STAFF','SUBSTITUTE')),
  employee_code  text    NOT NULL,                              -- unique across all kinds
  full_name      text    NOT NULL,
  phone          text,
  email          text,
  user_id        bigint  REFERENCES app_user(id),               -- null for Substitutes (no login) and not-yet-activated trainees
  designation_id bigint  REFERENCES designation(id),
  zone_id        bigint  REFERENCES zone(id),                   -- Substitute's / Manager's home Zone
  status         text    NOT NULL DEFAULT 'ACTIVE'
                   CHECK (status IN ('IN_TRAINING','ACTIVE','EXITED')),
  joining_date   date,
  exit_date      date,
  day_rate       numeric(12,2) CHECK (day_rate >= 0),           -- Substitutes only
  bank_account   text,
  bank_ifsc      text,
  address        text,
  created_at     timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT employee_code_uk UNIQUE (employee_code),
  CONSTRAINT employee_user_uk UNIQUE (user_id),
  CONSTRAINT employee_id_kind_uk UNIQUE (id, kind),             -- target of the kind-guard composite FKs
  CONSTRAINT employee_dates_ck CHECK (exit_date IS NULL OR joining_date IS NULL OR exit_date >= joining_date),
  CONSTRAINT employee_exited_ck CHECK ((status = 'EXITED') = (exit_date IS NOT NULL)),
  CONSTRAINT employee_substitute_ck CHECK (
    (kind = 'SUBSTITUTE' AND user_id IS NULL AND zone_id IS NOT NULL)
    OR (kind <> 'SUBSTITUTE' AND day_rate IS NULL))
);
CREATE INDEX employee_kind_status_idx ON employee (kind, status);        -- IDX: every list screen
CREATE INDEX employee_zone_idx ON employee (zone_id) WHERE zone_id IS NOT NULL;
COMMENT ON TABLE employee IS '[MVP] Teacher, Manager, staff, Substitute. Never deleted (status EXITED). Available/Assigned/Under Review/Transferred/Replaced are derived from placement and transfer rows, not stored.';

ALTER TABLE zone ADD CONSTRAINT zone_manager_fk
  FOREIGN KEY (manager_id, manager_kind) REFERENCES employee (id, kind);

-- [MVP] The one dated salary history (replaces designation_salary, person_salary, teacher_salary_history).
CREATE TABLE employee_salary (
  id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  employee_id    bigint        NOT NULL REFERENCES employee(id),
  monthly_salary numeric(12,2) NOT NULL CHECK (monthly_salary >= 0),
  effective_from date          NOT NULL,
  reason         text,
  set_by         bigint        NOT NULL REFERENCES app_user(id),
  created_at     timestamptz   NOT NULL DEFAULT now(),
  CONSTRAINT employee_salary_uk UNIQUE (employee_id, effective_from)    -- also the lookup index
);
CREATE TRIGGER employee_salary_append_only BEFORE UPDATE OR DELETE ON employee_salary
  FOR EACH ROW EXECUTE FUNCTION forbid_change();
COMMENT ON TABLE employee_salary IS '[MVP] Append-only dated salary. Salary in effect on date D = row with greatest effective_from <= D. Substitutes have none (employee.day_rate). Visible to Admin/Director only (app rule).';

-- [MVP] Schools. manager_id null = the Zone's Manager.
CREATE TABLE school (
  id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name            text   NOT NULL,
  zone_id         bigint NOT NULL REFERENCES zone(id),
  manager_id      bigint,
  manager_kind    text   NOT NULL DEFAULT 'MANAGER' CHECK (manager_kind = 'MANAGER'),
  address         text,
  city            text,
  pincode         text   CHECK (pincode ~ '^[0-9]{6}$'),
  principal_name  text,
  phone           text,
  email           text,
  weekly_offs     smallint[],     -- ISO weekdays 1=Mon..7=Sun; null = organisation default (setting weekly_offs)
  active          boolean NOT NULL DEFAULT true,
  created_at      timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT school_name_uk UNIQUE (name, zone_id),
  CONSTRAINT school_manager_fk FOREIGN KEY (manager_id, manager_kind) REFERENCES employee (id, kind),
  CONSTRAINT school_weekly_offs_ck CHECK (weekly_offs IS NULL OR weekly_offs <@ ARRAY[1,2,3,4,5,6,7]::smallint[])
);
CREATE INDEX school_zone_idx ON school (zone_id);                        -- IDX: Manager scope query
COMMENT ON TABLE school IS '[MVP] 50 Schools. Cannot be deactivated while Teachers are placed (app rule). City and pincode are plain text; no place table.';

-- [MVP]
CREATE TABLE school_contact (
  id        bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  school_id bigint NOT NULL REFERENCES school(id),
  role      text   NOT NULL CHECK (role IN ('PRINCIPAL','MANAGEMENT','ACCOUNTS','OTHER')),
  name      text   NOT NULL,
  phone     text,
  email     text
);
CREATE INDEX school_contact_school_idx ON school_contact (school_id);
COMMENT ON TABLE school_contact IS '[MVP] Principal / management / accounts contacts of a School.';

-- [MVP] Organisation holidays (school_id null) and per-School holidays.
CREATE TABLE holiday (
  id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  holiday_date date   NOT NULL,
  name         text   NOT NULL,
  school_id    bigint REFERENCES school(id)
);
CREATE UNIQUE INDEX holiday_uk ON holiday (holiday_date, COALESCE(school_id, 0));
COMMENT ON TABLE holiday IS '[MVP] Non-working dates. school_id null = every School.';

-- [MVP] The working-days figure payroll divides by (one screen per School and year).
CREATE TABLE school_month_days (
  school_id    bigint        NOT NULL REFERENCES school(id),
  year         smallint      NOT NULL CHECK (year BETWEEN 2000 AND 2100),
  month        smallint      NOT NULL CHECK (month BETWEEN 1 AND 12),
  working_days numeric(4,1)  NOT NULL CHECK (working_days >= 0 AND working_days <= 31 AND (working_days * 2) = trunc(working_days * 2)),
  updated_by   bigint        NOT NULL REFERENCES app_user(id),
  updated_at   timestamptz   NOT NULL DEFAULT now(),
  PRIMARY KEY (school_id, year, month)
);
COMMENT ON TABLE school_month_days IS '[MVP] One figure per School and month, in half-day steps. Default shown as a hint = days in month - weekly offs - holidays. History is in audit_log.';

-- =====================================================================
-- 3. CONTRACTS AND PLACEMENT  (3 tables)  [MVP]
-- =====================================================================

-- [MVP] The MoU. status: PENDING (awaiting approval), APPROVED, ENDED.
CREATE TABLE school_contract (
  id                   bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  school_id            bigint        NOT NULL REFERENCES school(id),
  teacher_count        smallint      NOT NULL CHECK (teacher_count > 0),
  start_date           date          NOT NULL,
  end_date             date          NOT NULL,
  fee_mode             text          NOT NULL CHECK (fee_mode IN ('UNIFORM','PER_POSITION')),
  uniform_monthly_fee  numeric(12,2) CHECK (uniform_monthly_fee >= 0),
  signed_on            date,
  signatory_name       text,
  signatory_title      text,
  document_file_id     bigint        REFERENCES stored_file(id),    -- current signed PDF; older versions via stored_file.replaces_file_id
  status               text          NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPROVED','ENDED')),
  approved_by          bigint        REFERENCES app_user(id),       -- Director or Zone Manager (D5)
  approved_on          date,
  incentive_owner_id   bigint,                                      -- Manager who owns the won prospect (incentive earner)
  incentive_owner_kind text          NOT NULL DEFAULT 'MANAGER' CHECK (incentive_owner_kind = 'MANAGER'),
  prospect_id          bigint,                                      -- FK added below (phase 2 table)
  created_by           bigint        NOT NULL REFERENCES app_user(id),
  created_at           timestamptz   NOT NULL DEFAULT now(),
  CONSTRAINT contract_dates_ck CHECK (end_date >= start_date),
  CONSTRAINT contract_fee_ck CHECK ((fee_mode = 'UNIFORM') = (uniform_monthly_fee IS NOT NULL)),
  CONSTRAINT contract_approval_ck CHECK ((status = 'PENDING') OR (approved_by IS NOT NULL AND approved_on IS NOT NULL)),
  CONSTRAINT contract_id_school_uk UNIQUE (id, school_id),
  CONSTRAINT contract_owner_fk FOREIGN KEY (incentive_owner_id, incentive_owner_kind) REFERENCES employee (id, kind)
);
-- At most one live (not ended) contract per School. A new MoU first ends the old one in the same transaction.
CREATE UNIQUE INDEX school_contract_one_live_uk ON school_contract (school_id) WHERE status <> 'ENDED';
COMMENT ON TABLE school_contract IS '[MVP] MoU. PENDING/APPROVED/ENDED only (no five-state machine). Signed PDF optional with an app warning. A School with no live contract cannot be billed.';

-- [MVP] One row per Teacher seat. Fee used only when fee_mode = PER_POSITION.
CREATE TABLE contract_position (
  id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  contract_id bigint   NOT NULL,
  school_id   bigint   NOT NULL,
  position_no smallint NOT NULL CHECK (position_no > 0),
  label       text,
  monthly_fee numeric(12,2) CHECK (monthly_fee >= 0),
  CONSTRAINT position_contract_fk FOREIGN KEY (contract_id, school_id) REFERENCES school_contract (id, school_id),
  CONSTRAINT position_uk UNIQUE (contract_id, position_no),
  CONSTRAINT position_id_school_uk UNIQUE (id, school_id)
);
COMMENT ON TABLE contract_position IS '[MVP] Seats of a contract. school_id repeated so placement can be proven to be in the same School.';

-- [MVP] A Teacher in a position over a date range. Transfers and re-maps add rows; nothing is overwritten.
CREATE TABLE placement (
  id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  employee_id   bigint   NOT NULL,
  employee_kind text     NOT NULL DEFAULT 'TEACHER' CHECK (employee_kind = 'TEACHER'),
  position_id   bigint   NOT NULL,
  school_id     bigint   NOT NULL,
  start_date    date     NOT NULL,
  end_date      date,                       -- null = still placed
  end_reason    text     CHECK (end_reason IN ('TRANSFER','REPLACEMENT','NEW_MOU','EXIT','CONTRACT_END','OTHER')),
  created_by    bigint   NOT NULL REFERENCES app_user(id),
  created_at    timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT placement_teacher_fk FOREIGN KEY (employee_id, employee_kind) REFERENCES employee (id, kind),
  CONSTRAINT placement_position_fk FOREIGN KEY (position_id, school_id) REFERENCES contract_position (id, school_id),
  CONSTRAINT placement_dates_ck CHECK (end_date IS NULL OR end_date >= start_date),
  -- a Teacher has one School at a time, a seat holds one Teacher at a time
  CONSTRAINT placement_teacher_no_overlap EXCLUDE USING gist
    (employee_id WITH =, daterange(start_date, end_date, '[]') WITH &&),
  CONSTRAINT placement_position_no_overlap EXCLUDE USING gist
    (position_id WITH =, daterange(start_date, end_date, '[]') WITH &&)
);
CREATE INDEX placement_school_idx ON placement (school_id, start_date);  -- IDX: who is in this School on a date (billing, attendance, scope)
COMMENT ON TABLE placement IS '[MVP] Dated placement. Billing and pay read it by date, so transfers need no other bookkeeping. Salary starts at first placement (D2); training Teachers have none (D1).';

-- =====================================================================
-- 4. ATTENDANCE AND LEAVE  (4 tables)  [MVP]
-- =====================================================================

-- [MVP] Teacher and Manager leave in one table.
CREATE TABLE leave_request (
  id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  employee_id      bigint  NOT NULL REFERENCES employee(id),
  leave_type       text    NOT NULL CHECK (leave_type IN ('CASUAL','SICK','PERSONAL','OTHER','LOP')),
  from_date        date    NOT NULL,
  to_date          date    NOT NULL,
  from_half_day    boolean NOT NULL DEFAULT false,
  to_half_day      boolean NOT NULL DEFAULT false,
  days             numeric(4,1) NOT NULL CHECK (days > 0),   -- working days, computed by the app from weekly offs and holidays
  reason           text,
  status           text    NOT NULL DEFAULT 'PENDING'
                     CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED','REVOKED')),
  same_day         boolean NOT NULL DEFAULT false,            -- recorded for today: auto-approved and flagged (spec 040)
  recorded_by      bigint  REFERENCES app_user(id),           -- set when a Manager records it on behalf of a Teacher
  decided_by       bigint  REFERENCES app_user(id),
  decided_at       timestamptz,
  decision_reason  text,
  created_at       timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT leave_dates_ck CHECK (to_date >= from_date),
  CONSTRAINT leave_decision_ck CHECK (status IN ('PENDING','CANCELLED') OR (decided_by IS NOT NULL AND decided_at IS NOT NULL)),
  CONSTRAINT leave_reject_reason_ck CHECK (status <> 'REJECTED' OR length(btrim(coalesce(decision_reason,''))) > 0)
);
CREATE INDEX leave_employee_idx ON leave_request (employee_id, from_date);              -- IDX: overlap check, my leave
CREATE INDEX leave_pending_idx  ON leave_request (created_at) WHERE status = 'PENDING'; -- IDX: approvals inbox
COMMENT ON TABLE leave_request IS '[MVP] Leave for Teachers (decided by Manager) and Managers (decided by Director). No balances. Every Teacher leave day is a payroll deduction (spec 040). No-overlap among live requests is an app check.';

-- [MVP] One current mark per Teacher per date.
CREATE TABLE attendance_mark (
  id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  employee_id      bigint  NOT NULL,
  employee_kind    text    NOT NULL DEFAULT 'TEACHER' CHECK (employee_kind = 'TEACHER'),
  school_id        bigint  NOT NULL REFERENCES school(id),   -- School the Teacher was placed in that day
  mark_date        date    NOT NULL,
  status           text    NOT NULL CHECK (status IN ('PRESENT','ABSENT','LEAVE','HALF_DAY','TRAINING')),
  leave_request_id bigint  REFERENCES leave_request(id),
  note             text,                                     -- e.g. Principal confirmation note
  marked_by        bigint  NOT NULL REFERENCES app_user(id),
  marked_at        timestamptz NOT NULL DEFAULT now(),
  updated_by       bigint  REFERENCES app_user(id),
  updated_at       timestamptz,
  CONSTRAINT attendance_teacher_fk FOREIGN KEY (employee_id, employee_kind) REFERENCES employee (id, kind),
  CONSTRAINT attendance_mark_uk UNIQUE (employee_id, mark_date),
  CONSTRAINT attendance_leave_ck CHECK (status = 'LEAVE' OR leave_request_id IS NULL)
);
CREATE INDEX attendance_school_date_idx ON attendance_mark (school_id, mark_date);  -- IDX: Manager month grid, billing, payroll
COMMENT ON TABLE attendance_mark IS '[MVP] Daily mark. Absent/Leave = unpaid day, Half day = half unpaid, derived in payroll (no stored LOP flag). Corrections go to audit_log. The month lock is enforced by the service, not a trigger. A mark must lie inside a placement of school_id (service rule).';

-- [MVP] Manager check-in / check-out.
CREATE TABLE manager_checkin (
  id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  employee_id   bigint  NOT NULL,
  employee_kind text    NOT NULL DEFAULT 'MANAGER' CHECK (employee_kind = 'MANAGER'),
  work_date     date    NOT NULL,
  day_type      text    NOT NULL CHECK (day_type IN ('OFFICE','FIELD','LEAVE')),
  purpose       text,
  check_in_at   timestamptz,
  check_in_lat  numeric(9,6) CHECK (check_in_lat BETWEEN -90 AND 90),
  check_in_lng  numeric(9,6) CHECK (check_in_lng BETWEEN -180 AND 180),
  check_out_at  timestamptz,
  check_out_lat numeric(9,6) CHECK (check_out_lat BETWEEN -90 AND 90),
  check_out_lng numeric(9,6) CHECK (check_out_lng BETWEEN -180 AND 180),
  edited_by     bigint  REFERENCES app_user(id),            -- Admin correction of a forgotten check-in/out
  edit_reason   text,
  CONSTRAINT checkin_manager_fk FOREIGN KEY (employee_id, employee_kind) REFERENCES employee (id, kind),
  CONSTRAINT checkin_uk UNIQUE (employee_id, work_date),
  CONSTRAINT checkin_field_ck CHECK (day_type <> 'FIELD' OR length(btrim(coalesce(purpose,''))) > 0),
  CONSTRAINT checkin_times_ck CHECK (check_out_at IS NULL OR (check_in_at IS NOT NULL AND check_out_at >= check_in_at)),
  CONSTRAINT checkin_present_ck CHECK (day_type = 'LEAVE' OR check_in_at IS NOT NULL),
  CONSTRAINT checkin_edit_ck CHECK (edited_by IS NULL OR length(btrim(coalesce(edit_reason,''))) > 0)
);
COMMENT ON TABLE manager_checkin IS '[MVP] One row per Manager per day. Location is browser geolocation at check-in/out only. "Not checked in yet today" is a dashboard query.';

-- [MVP] Month lock for Teacher attendance and Manager attendance (payroll input).
CREATE TABLE month_lock (
  id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  kind          text     NOT NULL CHECK (kind IN ('TEACHER','MANAGER')),
  year          smallint NOT NULL CHECK (year BETWEEN 2000 AND 2100),
  month         smallint NOT NULL CHECK (month BETWEEN 1 AND 12),
  is_locked     boolean  NOT NULL,
  locked_by     bigint   REFERENCES app_user(id),
  locked_at     timestamptz,
  reopened_by   bigint   REFERENCES app_user(id),
  reopened_at   timestamptz,
  reopen_reason text,
  CONSTRAINT month_lock_uk UNIQUE (kind, year, month),
  CONSTRAINT month_lock_state_ck CHECK (
    (is_locked AND locked_by IS NOT NULL AND locked_at IS NOT NULL)
    OR (NOT is_locked AND reopened_by IS NOT NULL AND length(btrim(coalesce(reopen_reason,''))) > 0))
);
COMMENT ON TABLE month_lock IS '[MVP] Absent row = unlocked. Reopen needs a reason. Earlier lock/unlock cycles remain in audit_log.';

-- =====================================================================
-- 5. SUBSTITUTION  (1 table)  [MVP]
-- =====================================================================

-- [MVP] One row per absent Teacher per date: requirement + assignment + day mark + pay snapshot.
CREATE TABLE substitution (
  id                    bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  absent_employee_id    bigint  NOT NULL,
  absent_kind           text    NOT NULL DEFAULT 'TEACHER' CHECK (absent_kind = 'TEACHER'),
  school_id             bigint  NOT NULL REFERENCES school(id),
  absence_date          date    NOT NULL,
  leave_request_id      bigint  REFERENCES leave_request(id),
  status                text    NOT NULL DEFAULT 'OPEN'
                          CHECK (status IN ('OPEN','ASSIGNED','MANAGER_COVER','CANCELLED')),
  substitute_id         bigint,
  substitute_kind       text    NOT NULL DEFAULT 'SUBSTITUTE' CHECK (substitute_kind = 'SUBSTITUTE'),
  assigned_by           bigint  REFERENCES app_user(id),
  assigned_at           timestamptz,
  school_informed       boolean NOT NULL DEFAULT false,
  school_informed_note  text,
  day_status            text    CHECK (day_status IN ('PRESENT','ABSENT')),
  day_marked_by         bigint  REFERENCES app_user(id),
  day_marked_at         timestamptz,
  day_rate              numeric(12,2) CHECK (day_rate >= 0),        -- snapshot of employee.day_rate when marked PRESENT
  paid_in_run_id        bigint,                                     -- FK added after payroll_run; set when a payroll run pays the day
  created_at            timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT substitution_absent_fk FOREIGN KEY (absent_employee_id, absent_kind) REFERENCES employee (id, kind),
  CONSTRAINT substitution_substitute_fk FOREIGN KEY (substitute_id, substitute_kind) REFERENCES employee (id, kind),
  CONSTRAINT substitution_requirement_uk UNIQUE (absent_employee_id, absence_date),
  CONSTRAINT substitution_assigned_ck CHECK ((status = 'ASSIGNED') = (substitute_id IS NOT NULL)),
  CONSTRAINT substitution_assigned_by_ck CHECK (status NOT IN ('ASSIGNED','MANAGER_COVER') OR (assigned_by IS NOT NULL AND assigned_at IS NOT NULL)),
  CONSTRAINT substitution_day_ck CHECK (
    (day_status IS NULL AND day_marked_by IS NULL AND day_rate IS NULL AND paid_in_run_id IS NULL)
    OR (status = 'ASSIGNED' AND day_status IS NOT NULL AND day_marked_by IS NOT NULL AND day_marked_at IS NOT NULL
        AND (day_status = 'ABSENT' OR day_rate IS NOT NULL))),
  CONSTRAINT substitution_paid_ck CHECK (paid_in_run_id IS NULL OR day_status = 'PRESENT')
);
-- A Substitute covers one School per day.
CREATE UNIQUE INDEX substitution_no_double_booking_uk ON substitution (substitute_id, absence_date) WHERE status = 'ASSIGNED';
CREATE INDEX substitution_open_idx ON substitution (school_id, absence_date) WHERE status = 'OPEN';  -- IDX: open requirements
COMMENT ON TABLE substitution IS '[MVP] Requirement, assignment, "School informed", day mark and rate snapshot in one row. Pay = PRESENT days with paid_in_run_id null, picked up by the payroll run (so a day marked after approval is paid in the next run automatically). Credit goes to the substitute, not the absent Teacher.';

-- =====================================================================
-- 6. PAYROLL  (3 tables)  [MVP]
-- =====================================================================

-- [MVP] One run per month.
CREATE TABLE payroll_run (
  id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  year         smallint NOT NULL CHECK (year BETWEEN 2000 AND 2100),
  month        smallint NOT NULL CHECK (month BETWEEN 1 AND 12),
  status       text     NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','APPROVED')),
  started_by   bigint   NOT NULL REFERENCES app_user(id),
  started_at   timestamptz NOT NULL DEFAULT now(),
  computed_at  timestamptz,
  approved_by  bigint   REFERENCES app_user(id),
  approved_at  timestamptz,
  note         text,
  CONSTRAINT payroll_run_month_uk UNIQUE (year, month),
  CONSTRAINT payroll_run_approved_ck CHECK ((status = 'APPROVED') = (approved_by IS NOT NULL AND approved_at IS NOT NULL))
);
COMMENT ON TABLE payroll_run IS '[MVP] Admin starts, Director approves. APPROVED is immutable (trigger on payroll_line) and locks the month. A person left out is paid via an adjustment in the next run (no supplementary run).';

ALTER TABLE substitution ADD CONSTRAINT substitution_paid_run_fk FOREIGN KEY (paid_in_run_id) REFERENCES payroll_run(id);

-- [MVP] One line per person per run.
CREATE TABLE payroll_line (
  id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  run_id            bigint   NOT NULL REFERENCES payroll_run(id),
  employee_id       bigint   NOT NULL REFERENCES employee(id),
  monthly_salary    numeric(12,2),            -- salary in effect; null for Substitutes
  working_days      numeric(4,1),
  payable_days      numeric(4,1),
  lop_days          numeric(4,1),
  day_value         numeric(12,2),            -- salary / working days
  earned_pay        numeric(12,2),
  lop_amount        numeric(12,2),
  substitute_days   smallint,                 -- Substitutes: PRESENT days paid in this line
  substitute_rate   numeric(12,2),
  adjustments_total numeric(12,2) NOT NULL DEFAULT 0,
  net_pay           numeric(12,2),
  flag_reason       text,                     -- why the person could not be computed (never blocks the run)
  calc_detail       jsonb,                    -- per-School / per-placement breakdown for the payslip
  paid_status       text     NOT NULL DEFAULT 'UNPAID' CHECK (paid_status IN ('UNPAID','PAID')),
  paid_on           date,
  paid_mode         text     CHECK (paid_mode IN ('BANK','CASH','UPI','OTHER')),
  paid_reference    text,
  CONSTRAINT payroll_line_uk UNIQUE (run_id, employee_id),
  CONSTRAINT payroll_line_flag_ck CHECK ((flag_reason IS NOT NULL) = (net_pay IS NULL)),
  CONSTRAINT payroll_line_net_ck CHECK (
    net_pay IS NULL
    OR net_pay = coalesce(earned_pay,0) - coalesce(lop_amount,0) + adjustments_total
                 + coalesce(substitute_days * substitute_rate, 0)),
  CONSTRAINT payroll_line_paid_ck CHECK ((paid_status = 'PAID') = (paid_on IS NOT NULL AND paid_mode IS NOT NULL))
);
COMMENT ON TABLE payroll_line IS '[MVP] Payslip data (printable page). net = earned - LOP + adjustments (+ substitute days x rate). Only paid_* columns may change once the run is APPROVED.';

-- Freeze lines of an approved run (payment columns stay editable).
CREATE FUNCTION payroll_line_freeze() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE run_status text; n payroll_line;
BEGIN
  SELECT status INTO run_status FROM payroll_run WHERE id = COALESCE(NEW.run_id, OLD.run_id);
  IF run_status = 'APPROVED' THEN
    IF TG_OP = 'UPDATE' THEN
      n := NEW;
      n.paid_status := OLD.paid_status; n.paid_on := OLD.paid_on;
      n.paid_mode := OLD.paid_mode;     n.paid_reference := OLD.paid_reference;
      IF n IS NOT DISTINCT FROM OLD THEN RETURN NEW; END IF;
    END IF;
    RAISE EXCEPTION 'payroll run is APPROVED: line is frozen (% refused)', TG_OP;
  END IF;
  RETURN COALESCE(NEW, OLD);
END $$;
CREATE TRIGGER payroll_line_freeze BEFORE INSERT OR UPDATE OR DELETE ON payroll_line
  FOR EACH ROW EXECUTE FUNCTION payroll_line_freeze();

-- [MVP] Incentives, reimbursements, corrections and manual lines. Signed amount.
CREATE TABLE payroll_adjustment (
  id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  employee_id      bigint        NOT NULL REFERENCES employee(id),
  kind             text          NOT NULL CHECK (kind IN ('INCENTIVE','REIMBURSEMENT','CORRECTION','MANUAL')),
  amount           numeric(12,2) NOT NULL CHECK (amount <> 0),
  description      text          NOT NULL,
  for_year         smallint      NOT NULL,                 -- run month it is meant for
  for_month        smallint      NOT NULL CHECK (for_month BETWEEN 1 AND 12),
  status           text          NOT NULL DEFAULT 'APPROVED' CHECK (status IN ('PENDING','APPROVED','REJECTED','APPLIED')),
  school_id        bigint        REFERENCES school(id),     -- INCENTIVE: the School that was won
  expense_claim_id bigint,                                  -- REIMBURSEMENT: FK added after expense_claim
  approved_by      bigint        REFERENCES app_user(id),
  approved_on      date,
  applied_run_id   bigint        REFERENCES payroll_run(id),
  created_by       bigint        REFERENCES app_user(id),   -- null = created by the system on MoU approval
  created_at       timestamptz   NOT NULL DEFAULT now(),
  CONSTRAINT adjustment_applied_ck CHECK ((status = 'APPLIED') = (applied_run_id IS NOT NULL)),
  CONSTRAINT adjustment_approved_ck CHECK (status IN ('PENDING','REJECTED') OR (approved_by IS NOT NULL AND approved_on IS NOT NULL)),
  CONSTRAINT adjustment_incentive_ck CHECK (kind <> 'INCENTIVE' OR (school_id IS NOT NULL AND amount > 0)),
  CONSTRAINT adjustment_reimb_ck CHECK (kind <> 'REIMBURSEMENT' OR (expense_claim_id IS NOT NULL AND amount > 0))
);
-- One incentive per School, ever (requirement R27). A reversal is a separate CORRECTION row.
CREATE UNIQUE INDEX adjustment_one_incentive_per_school_uk ON payroll_adjustment (school_id) WHERE kind = 'INCENTIVE' AND status <> 'REJECTED';
CREATE UNIQUE INDEX adjustment_one_per_claim_uk ON payroll_adjustment (expense_claim_id) WHERE expense_claim_id IS NOT NULL;
CREATE INDEX adjustment_open_idx ON payroll_adjustment (for_year, for_month) WHERE status IN ('PENDING','APPROVED');  -- IDX: pulled in by the run
COMMENT ON TABLE payroll_adjustment IS '[MVP] Input lines for a run. INCENTIVE is PENDING until Director/Zone Manager approve (D5); other kinds are created APPROVED. Applied adjustments keep applied_run_id. Reversal of an incentive = negative CORRECTION.';

-- =====================================================================
-- 7. EXPENSES  (3 tables)
-- =====================================================================

-- [MVP]
CREATE TABLE expense_claim (
  id                    bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  claimant_id           bigint        NOT NULL REFERENCES employee(id),
  claim_date            date          NOT NULL,
  amount                numeric(12,2) NOT NULL CHECK (amount > 0),
  category              text          NOT NULL CHECK (category IN
                          ('RENT_UTILITIES','TRAVEL_FUEL','TRAINING_STAY','MARKETING','OFFICE_SUPPLIES','OTHER')),
  category_other_reason text,
  zone_id               bigint        REFERENCES zone(id),
  head_office           boolean       NOT NULL DEFAULT false,
  reference_note        text,                          -- free-text visit / drive / batch reference
  no_bill_reason        text,
  status                text          NOT NULL DEFAULT 'SUBMITTED' CHECK (status IN ('SUBMITTED','APPROVED','REJECTED','PAID')),
  decided_by            bigint        REFERENCES app_user(id),
  decided_at            timestamptz,
  reject_reason         text,
  paid_via              text          CHECK (paid_via IN ('PAYROLL','DIRECT')),
  paid_on               date,
  paid_reference        text,
  submitted_at          timestamptz   NOT NULL DEFAULT now(),
  CONSTRAINT claim_area_ck CHECK ((zone_id IS NULL) = head_office),
  CONSTRAINT claim_other_ck CHECK (category <> 'OTHER' OR length(btrim(coalesce(category_other_reason,''))) > 0),
  CONSTRAINT claim_decision_ck CHECK (status = 'SUBMITTED' OR (decided_by IS NOT NULL AND decided_at IS NOT NULL)),
  CONSTRAINT claim_reject_ck CHECK (status <> 'REJECTED' OR length(btrim(coalesce(reject_reason,''))) > 0),
  CONSTRAINT claim_paid_ck CHECK ((status = 'PAID') = (paid_via IS NOT NULL AND paid_on IS NOT NULL))
);
CREATE INDEX expense_claim_status_idx ON expense_claim (status, claim_date);   -- IDX: approvals inbox, monthly report
CREATE INDEX expense_claim_claimant_idx ON expense_claim (claimant_id);        -- IDX: My Expenses
COMMENT ON TABLE expense_claim IS '[MVP] Claim. Resubmission after rejection edits the row back to SUBMITTED (audit_log keeps the story). "Bills or a no-bill reason" is a service rule (it spans expense_bill). No approval limit (D11).';

ALTER TABLE payroll_adjustment ADD CONSTRAINT adjustment_claim_fk FOREIGN KEY (expense_claim_id) REFERENCES expense_claim(id);

-- [MVP] up to 5 per claim (service rule), jpg/png/pdf up to 10 MB (checked at upload).
CREATE TABLE expense_bill (
  id       bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  claim_id bigint NOT NULL REFERENCES expense_claim(id),
  file_id  bigint NOT NULL REFERENCES stored_file(id),
  CONSTRAINT expense_bill_uk UNIQUE (claim_id, file_id),
  CONSTRAINT expense_bill_file_uk UNIQUE (file_id)
);
COMMENT ON TABLE expense_bill IS '[MVP] Bill files of a claim.';

-- [PHASE 2] Budget per Zone (or head office) per month, for budget vs actual.
CREATE TABLE expense_budget (
  id        bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  zone_id   bigint        REFERENCES zone(id),          -- null = head office
  year      smallint      NOT NULL,
  month     smallint      NOT NULL CHECK (month BETWEEN 1 AND 12),
  amount    numeric(12,2) NOT NULL CHECK (amount >= 0)
);
CREATE UNIQUE INDEX expense_budget_uk ON expense_budget (COALESCE(zone_id, 0), year, month);
COMMENT ON TABLE expense_budget IS '[PHASE 2] Optional budget per Zone/month (open question 8: maybe unnecessary).';

-- =====================================================================
-- 8. BILLING  (5 tables + 1 view)  [MVP]
-- =====================================================================

-- [MVP] Monthly invoice per School. Immutable once raised.
CREATE TABLE invoice (
  id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  invoice_no   text     NOT NULL,
  school_id    bigint   NOT NULL,
  contract_id  bigint   NOT NULL,
  year         smallint NOT NULL CHECK (year BETWEEN 2000 AND 2100),
  month        smallint NOT NULL CHECK (month BETWEEN 1 AND 12),
  raised_on    date     NOT NULL,
  due_date     date     NOT NULL,
  total_amount numeric(12,2) NOT NULL CHECK (total_amount >= 0),
  note         text,
  raised_by    bigint   NOT NULL REFERENCES app_user(id),
  CONSTRAINT invoice_no_uk UNIQUE (invoice_no),
  CONSTRAINT invoice_month_uk UNIQUE (school_id, year, month),
  CONSTRAINT invoice_contract_fk FOREIGN KEY (contract_id, school_id) REFERENCES school_contract (id, school_id),
  CONSTRAINT invoice_due_ck CHECK (due_date >= raised_on)
);
CREATE INDEX invoice_due_idx ON invoice (due_date);                       -- IDX: overdue / ageing
CREATE TRIGGER invoice_immutable BEFORE UPDATE OR DELETE ON invoice
  FOR EACH ROW EXECUTE FUNCTION forbid_change();
COMMENT ON TABLE invoice IS '[MVP] One invoice per School per month (D6: fee per mapped Teacher, pro-rated only for joining/transfer/exit; absences do not reduce it). No status column: open/part/paid/overdue are derived (view invoice_balance). A wrong invoice is cancelled with a full credit adjustment.';

-- [MVP]
CREATE TABLE invoice_line (
  id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  invoice_id    bigint        NOT NULL REFERENCES invoice(id),
  placement_id  bigint        REFERENCES placement(id),    -- null for a manual line (e.g. termly School)
  description   text          NOT NULL,
  from_date     date,
  to_date       date,
  monthly_fee   numeric(12,2) CHECK (monthly_fee >= 0),
  billable_days smallint,
  days_in_month smallint,
  amount        numeric(12,2) NOT NULL CHECK (amount >= 0),
  CONSTRAINT invoice_line_dates_ck CHECK (to_date IS NULL OR from_date IS NULL OR to_date >= from_date)
);
CREATE INDEX invoice_line_invoice_idx ON invoice_line (invoice_id);       -- IDX: invoice print
CREATE TRIGGER invoice_line_immutable BEFORE UPDATE OR DELETE ON invoice_line
  FOR EACH ROW EXECUTE FUNCTION forbid_change();
COMMENT ON TABLE invoice_line IS '[MVP] One line per placement (or manual line). Snapshot of fee and days at raise time.';

-- [MVP] Money received from a School. A reversal is a new row with a negative amount.
CREATE TABLE payment (
  id                  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  school_id           bigint        NOT NULL REFERENCES school(id),
  amount              numeric(12,2) NOT NULL CHECK (amount <> 0),
  paid_on             date          NOT NULL,
  mode                text          NOT NULL CHECK (mode IN ('BANK','CHEQUE','UPI','CASH','OTHER')),
  reference           text,
  reverses_payment_id bigint        REFERENCES payment(id),
  note                text,
  recorded_by         bigint        NOT NULL REFERENCES app_user(id),
  recorded_at         timestamptz   NOT NULL DEFAULT now(),
  CONSTRAINT payment_reversal_ck CHECK ((reverses_payment_id IS NULL) = (amount > 0)),
  CONSTRAINT payment_reversal_uk UNIQUE (reverses_payment_id)
);
CREATE TRIGGER payment_append_only BEFORE UPDATE OR DELETE ON payment
  FOR EACH ROW EXECUTE FUNCTION forbid_change();
COMMENT ON TABLE payment IS '[MVP] Append-only collections. Unallocated balance = amount - sum(allocations).';

-- [MVP]
CREATE TABLE payment_allocation (
  id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  payment_id bigint        NOT NULL REFERENCES payment(id),
  invoice_id bigint        NOT NULL REFERENCES invoice(id),
  amount     numeric(12,2) NOT NULL CHECK (amount <> 0),
  CONSTRAINT payment_allocation_uk UNIQUE (payment_id, invoice_id)
);
CREATE INDEX payment_allocation_invoice_idx ON payment_allocation (invoice_id);   -- IDX: invoice balance
CREATE TRIGGER payment_allocation_append_only BEFORE UPDATE OR DELETE ON payment_allocation
  FOR EACH ROW EXECUTE FUNCTION forbid_change();
COMMENT ON TABLE payment_allocation IS '[MVP] Splits a payment across invoices. Same sign as its payment; the service checks sum(allocations) <= payment and that payment and invoice belong to the same School.';

-- [MVP] Credit note / agreed deduction (also used to cancel an invoice).
CREATE TABLE invoice_adjustment (
  id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  invoice_id bigint        NOT NULL REFERENCES invoice(id),
  amount     numeric(12,2) NOT NULL CHECK (amount > 0),
  reason     text          NOT NULL,
  created_by bigint        NOT NULL REFERENCES app_user(id),
  created_at timestamptz   NOT NULL DEFAULT now()
);
CREATE INDEX invoice_adjustment_invoice_idx ON invoice_adjustment (invoice_id);
CREATE TRIGGER invoice_adjustment_append_only BEFORE UPDATE OR DELETE ON invoice_adjustment
  FOR EACH ROW EXECUTE FUNCTION forbid_change();
COMMENT ON TABLE invoice_adjustment IS '[MVP] Credit adjustments reduce the invoice balance. Append-only.';

-- Derived balance: replaces the stored status. Overdue = balance > 0 AND due_date < current_date.
CREATE VIEW invoice_balance AS
SELECT i.id AS invoice_id, i.school_id, i.year, i.month, i.due_date, i.total_amount,
       COALESCE(a.credits, 0)  AS credits,
       COALESCE(p.received, 0) AS received,
       i.total_amount - COALESCE(a.credits, 0) - COALESCE(p.received, 0) AS balance
FROM invoice i
LEFT JOIN (SELECT invoice_id, sum(amount) AS credits  FROM invoice_adjustment GROUP BY invoice_id) a ON a.invoice_id = i.id
LEFT JOIN (SELECT invoice_id, sum(amount) AS received FROM payment_allocation GROUP BY invoice_id) p ON p.invoice_id = i.id;
COMMENT ON VIEW invoice_balance IS '[MVP] Derived balance per invoice (not a table). Ageing buckets 0-30/31-60/61-90/90+ are a query over this view by School, Zone (via school) and Manager.';

-- =====================================================================
-- 9. RECRUITMENT  (6 tables)  [PHASE 2]
-- =====================================================================

CREATE TABLE college (
  id                 bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name               text NOT NULL,
  city               text,
  placement_officer  text,
  placement_phone    text,
  placement_email    text,
  principal_name     text,
  principal_phone    text,
  principal_email    text,
  notes              text,
  CONSTRAINT college_name_uk UNIQUE (name)
);
COMMENT ON TABLE college IS '[PHASE 2] College with its two contacts as columns.';

CREATE TABLE drive (
  id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  college_id   bigint NOT NULL REFERENCES college(id),
  drive_date   date   NOT NULL,
  venue        text,
  interviewers text,
  season       text,
  status       text   NOT NULL DEFAULT 'PLANNED' CHECK (status IN ('PLANNED','DONE','CANCELLED')),
  created_by   bigint NOT NULL REFERENCES app_user(id)
);
CREATE INDEX drive_college_idx ON drive (college_id, drive_date);
COMMENT ON TABLE drive IS '[PHASE 2] Campus drive. Date changes are audit_log entries.';

CREATE TABLE candidate (
  id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  drive_id      bigint NOT NULL REFERENCES drive(id),
  full_name     text   NOT NULL,
  phone         text   NOT NULL,
  email         text,
  qualification text,
  outcome       text   NOT NULL DEFAULT 'REGISTERED'
                  CHECK (outcome IN ('REGISTERED','SELECTED','WAITLISTED','REJECTED','OFFERED','ACCEPTED','DECLINED')),
  outcome_note  text,
  employee_id   bigint,                               -- Teacher created on acceptance (exactly once)
  employee_kind text   NOT NULL DEFAULT 'TEACHER' CHECK (employee_kind = 'TEACHER'),
  CONSTRAINT candidate_employee_fk FOREIGN KEY (employee_id, employee_kind) REFERENCES employee (id, kind),
  CONSTRAINT candidate_employee_uk UNIQUE (employee_id),
  CONSTRAINT candidate_phone_uk UNIQUE (drive_id, phone),
  CONSTRAINT candidate_accepted_ck CHECK ((outcome = 'ACCEPTED') = (employee_id IS NOT NULL))
);
COMMENT ON TABLE candidate IS '[PHASE 2] Candidate per drive (single entry or CSV). Outcome history is audit_log. A waitlisted candidate is promoted by changing outcome.';

CREATE TABLE offer (
  id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  candidate_id   bigint        NOT NULL REFERENCES candidate(id),
  monthly_salary numeric(12,2) NOT NULL CHECK (monthly_salary >= 0),
  terms          text,
  deadline       date          NOT NULL,
  status         text          NOT NULL DEFAULT 'ISSUED' CHECK (status IN ('ISSUED','ACCEPTED','DECLINED','EXPIRED','REPLACED')),
  issued_by      bigint        NOT NULL REFERENCES app_user(id),
  issued_on      date          NOT NULL DEFAULT current_date,
  decided_on     date,
  CONSTRAINT offer_decided_ck CHECK (status IN ('ISSUED','REPLACED') OR decided_on IS NOT NULL)
);
CREATE UNIQUE INDEX offer_one_live_uk ON offer (candidate_id) WHERE status IN ('ISSUED','ACCEPTED');
COMMENT ON TABLE offer IS '[PHASE 2] Job offer. A changed package replaces the old offer manually (old becomes REPLACED). EXPIRED is set by a person or inferred from deadline in queries; no scheduler.';

CREATE TABLE induction_batch (
  id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name          text     NOT NULL,
  start_date    date     NOT NULL,
  end_date      date     NOT NULL,
  seat_limit    smallint NOT NULL CHECK (seat_limit > 0),
  status        text     NOT NULL DEFAULT 'PLANNED' CHECK (status IN ('PLANNED','RUNNING','COMPLETED')),
  signed_off_by bigint   REFERENCES app_user(id),
  signed_off_on date,
  CONSTRAINT batch_dates_ck CHECK (end_date >= start_date),
  CONSTRAINT batch_name_uk UNIQUE (name)
);
COMMENT ON TABLE induction_batch IS '[PHASE 2] One-month induction batch.';

CREATE TABLE induction_member (
  batch_id      bigint   NOT NULL REFERENCES induction_batch(id),
  employee_id   bigint   NOT NULL,
  employee_kind text     NOT NULL DEFAULT 'TEACHER' CHECK (employee_kind = 'TEACHER'),
  days_attended smallint NOT NULL DEFAULT 0 CHECK (days_attended >= 0),
  signed_off    boolean  NOT NULL DEFAULT false,
  PRIMARY KEY (batch_id, employee_id),
  CONSTRAINT induction_teacher_fk FOREIGN KEY (employee_id, employee_kind) REFERENCES employee (id, kind)
);
COMMENT ON TABLE induction_member IS '[PHASE 2] Seat in a batch; sign-off makes the Teacher ACTIVE (seat limit is a service rule). Training is unpaid (D2).';

-- =====================================================================
-- 10. TRAINING CALENDAR  (2 tables)  [PHASE 2]
-- =====================================================================

CREATE TABLE training_session (
  id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  session_date date   NOT NULL,
  topic        text   NOT NULL,
  trainer      text,
  venue        text,
  audience     text   NOT NULL DEFAULT 'TEACHERS' CHECK (audience IN ('TEACHERS','MANAGERS','ALL')),
  notes        text,
  created_by   bigint NOT NULL REFERENCES app_user(id)
);
CREATE INDEX training_session_date_idx ON training_session (session_date);
COMMENT ON TABLE training_session IS '[PHASE 2] Monthly/refresher session. Admin copies last month; no pattern generator.';

CREATE TABLE training_attendee (
  session_id  bigint  NOT NULL REFERENCES training_session(id),
  employee_id bigint  NOT NULL REFERENCES employee(id),
  attended    boolean,                                     -- null = not yet recorded
  PRIMARY KEY (session_id, employee_id)
);
COMMENT ON TABLE training_attendee IS '[PHASE 2] Invited people. attended = true writes a TRAINING attendance_mark (Teachers) in the same transaction.';

-- =====================================================================
-- 11. MARKETING  (2 tables)  [PHASE 2]
-- =====================================================================

CREATE TABLE prospect (
  id                 bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  school_name        text   NOT NULL,
  zone_id            bigint NOT NULL REFERENCES zone(id),
  owner_id           bigint NOT NULL,
  owner_kind         text   NOT NULL DEFAULT 'MANAGER' CHECK (owner_kind = 'MANAGER'),
  city               text,
  pincode            text   CHECK (pincode ~ '^[0-9]{6}$'),
  contact_name       text,
  contact_phone      text,
  contact_email      text,
  expected_teachers  smallint CHECK (expected_teachers > 0),
  stage              text   NOT NULL DEFAULT 'PROSPECT' CHECK (stage IN
                       ('PROSPECT','CONTACTED','VISIT','FOLLOW_UP','INTERESTED','NEGOTIATION','FINAL_STAGE','MOU','ACTIVE','ON_HOLD','LOST')),
  proposed_fee       numeric(12,2) CHECK (proposed_fee >= 0),
  proposed_terms     text,
  lost_reason        text,
  final_approved_by  bigint REFERENCES app_user(id),
  final_approved_on  date,
  school_id          bigint REFERENCES school(id),           -- set when the School record is created from the won prospect
  created_at         timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT prospect_owner_fk FOREIGN KEY (owner_id, owner_kind) REFERENCES employee (id, kind),
  CONSTRAINT prospect_lost_ck CHECK (stage <> 'LOST' OR length(btrim(coalesce(lost_reason,''))) > 0),
  CONSTRAINT prospect_won_ck CHECK (stage NOT IN ('MOU','ACTIVE') OR final_approved_by IS NOT NULL)
);
CREATE INDEX prospect_owner_idx ON prospect (owner_id, stage);
COMMENT ON TABLE prospect IS '[PHASE 2] Prospect with proposal terms inline (edit in place; audit_log keeps old values). Stage change history is audit_log.';

ALTER TABLE school_contract ADD CONSTRAINT contract_prospect_fk FOREIGN KEY (prospect_id) REFERENCES prospect(id);

CREATE TABLE prospect_activity (
  id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  prospect_id   bigint NOT NULL REFERENCES prospect(id),
  kind          text   NOT NULL CHECK (kind IN ('VISIT','CALL','PROPOSAL_MEETING','FOLLOW_UP')),
  planned_for   date   NOT NULL,
  status        text   NOT NULL DEFAULT 'PLANNED' CHECK (status IN ('PLANNED','DONE','MISSED','RESCHEDULED','CANCELLED')),
  done_on       date,
  outcome       text,
  created_by    bigint NOT NULL REFERENCES app_user(id),
  CONSTRAINT activity_done_ck CHECK (status <> 'DONE' OR (done_on IS NOT NULL AND length(btrim(coalesce(outcome,''))) > 0))
);
CREATE INDEX prospect_activity_idx ON prospect_activity (prospect_id, planned_for);
COMMENT ON TABLE prospect_activity IS '[PHASE 2] Visit/call/meeting. Outcome mandatory when DONE. Overdue = PLANNED and planned_for < today (query).';

-- =====================================================================
-- 12. TASKS  (2 tables)  [PHASE 2]
-- =====================================================================

CREATE TABLE task (
  id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  title             text   NOT NULL,
  description       text,
  task_type         text   NOT NULL CHECK (task_type IN
                      ('SCHOOL_VISIT','FOLLOW_UP','CONTENT','RECRUITMENT','TRAINING','TEACHER_ASSIGNMENT',
                       'OBSERVATION','PRINCIPAL_FEEDBACK','SUBSTITUTE_ARRANGEMENT','PERFORMANCE_ISSUE','PERSONAL','OTHER')),
  assignee_id       bigint NOT NULL,
  assignee_kind     text   NOT NULL DEFAULT 'MANAGER' CHECK (assignee_kind = 'MANAGER'),
  school_id         bigint REFERENCES school(id),
  teacher_id        bigint REFERENCES employee(id),
  needs_improvement boolean NOT NULL DEFAULT false,         -- performance issue flag
  due_date          date   NOT NULL,
  status            text   NOT NULL DEFAULT 'ASSIGNED' CHECK (status IN ('ASSIGNED','IN_PROGRESS','COMPLETED','VERIFIED','CANCELLED')),
  completion_note   text,
  completed_at      timestamptz,
  late_reason       text,
  verified_by       bigint REFERENCES app_user(id),
  verified_at       timestamptz,
  created_by        bigint NOT NULL REFERENCES app_user(id),
  created_at        timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT task_assignee_fk FOREIGN KEY (assignee_id, assignee_kind) REFERENCES employee (id, kind),
  CONSTRAINT task_completed_ck CHECK (status NOT IN ('COMPLETED','VERIFIED') OR
      (completed_at IS NOT NULL AND length(btrim(coalesce(completion_note,''))) >= 10)),
  CONSTRAINT task_verified_ck CHECK ((status = 'VERIFIED') = (verified_by IS NOT NULL AND verified_at IS NOT NULL))
);
CREATE INDEX task_assignee_idx ON task (assignee_id, status, due_date);  -- IDX: My Tasks, overdue list
COMMENT ON TABLE task IS '[PHASE 2] The one task engine. Other modules create tasks by calling a function. Overdue is a query (not completed and due_date < today); a late completion needs late_reason (service rule). Verifier <> assignee is a service rule (spans employee.user_id).';

CREATE TABLE task_evidence (
  id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  task_id    bigint NOT NULL REFERENCES task(id),
  file_id    bigint NOT NULL REFERENCES stored_file(id),
  CONSTRAINT task_evidence_uk UNIQUE (task_id, file_id),
  CONSTRAINT task_evidence_file_uk UNIQUE (file_id)
);
COMMENT ON TABLE task_evidence IS '[PHASE 2] Up to 5 evidence files per task (service rule).';

-- =====================================================================
-- 13. PERFORMANCE  (2 tables)  [PHASE 2]
-- =====================================================================

CREATE TABLE observation (
  id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  teacher_id       bigint NOT NULL,
  teacher_kind     text   NOT NULL DEFAULT 'TEACHER' CHECK (teacher_kind = 'TEACHER'),
  school_id        bigint NOT NULL REFERENCES school(id),
  observer_id      bigint NOT NULL,
  observer_kind    text   NOT NULL DEFAULT 'MANAGER' CHECK (observer_kind = 'MANAGER'),
  observed_on      date   NOT NULL,
  -- six areas, 1..5. Area names are a proposal; the specs say "several areas".
  rating_classroom_management smallint CHECK (rating_classroom_management BETWEEN 1 AND 5),
  rating_lesson_delivery      smallint CHECK (rating_lesson_delivery BETWEEN 1 AND 5),
  rating_student_engagement   smallint CHECK (rating_student_engagement BETWEEN 1 AND 5),
  rating_subject_knowledge    smallint CHECK (rating_subject_knowledge BETWEEN 1 AND 5),
  rating_communication        smallint CHECK (rating_communication BETWEEN 1 AND 5),
  rating_punctuality          smallint CHECK (rating_punctuality BETWEEN 1 AND 5),
  comments         text,
  principal_feedback text,
  suggested_result text   CHECK (suggested_result IN ('SUITABLE','NEEDS_IMPROVEMENT','UNSUITABLE')),
  status           text   NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','SUBMITTED')),
  created_at       timestamptz NOT NULL DEFAULT now(),
  submitted_at     timestamptz,
  CONSTRAINT observation_teacher_fk FOREIGN KEY (teacher_id, teacher_kind) REFERENCES employee (id, kind),
  CONSTRAINT observation_observer_fk FOREIGN KEY (observer_id, observer_kind) REFERENCES employee (id, kind),
  CONSTRAINT observation_submitted_ck CHECK (status = 'DRAFT' OR (
      submitted_at IS NOT NULL
      AND rating_classroom_management IS NOT NULL AND rating_lesson_delivery IS NOT NULL
      AND rating_student_engagement IS NOT NULL AND rating_subject_knowledge IS NOT NULL
      AND rating_communication IS NOT NULL AND rating_punctuality IS NOT NULL))
);
CREATE INDEX observation_teacher_idx ON observation (teacher_id, observed_on);
COMMENT ON TABLE observation IS '[PHASE 2] Classroom observation. Editable while DRAFT; after SUBMITTED add a note via performance_followup. Attachments are stored_file.observation_id (max 5).';

ALTER TABLE stored_file ADD CONSTRAINT stored_file_observation_fk FOREIGN KEY (observation_id) REFERENCES observation(id);

CREATE TABLE performance_followup (
  id                      bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  observation_id          bigint NOT NULL REFERENCES observation(id),
  issue_summary           text   NOT NULL,
  corrective_action       text,
  followup_due_on         date,
  status                  text   NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','DONE')),
  followup_observation_id bigint REFERENCES observation(id),
  task_id                 bigint REFERENCES task(id),
  CONSTRAINT followup_done_ck CHECK (status <> 'DONE' OR followup_observation_id IS NOT NULL OR corrective_action IS NOT NULL)
);
CREATE INDEX performance_followup_obs_idx ON performance_followup (observation_id);
COMMENT ON TABLE performance_followup IS '[PHASE 2] Issue, corrective action and follow-up observation date. "Unsuitable" opens a transfer_request from the UI.';

-- =====================================================================
-- 14. TRANSFERS  (1 table)  [PHASE 2]
-- =====================================================================

CREATE TABLE transfer_request (
  id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  kind              text   NOT NULL CHECK (kind IN ('TRANSFER','REPLACEMENT')),
  employee_id       bigint NOT NULL,
  employee_kind     text   NOT NULL DEFAULT 'TEACHER' CHECK (employee_kind = 'TEACHER'),
  from_school_id    bigint NOT NULL REFERENCES school(id),
  to_school_id      bigint REFERENCES school(id),
  to_position_id    bigint REFERENCES contract_position(id),      -- chosen at approval
  replacement_id    bigint,
  replacement_kind  text   NOT NULL DEFAULT 'TEACHER' CHECK (replacement_kind = 'TEACHER'),
  effective_date    date   NOT NULL,
  reason_category   text   NOT NULL CHECK (reason_category IN ('PERFORMANCE','SCHOOL_REQUEST','TEACHER_REQUEST','OTHER')),
  reason            text,
  status            text   NOT NULL DEFAULT 'REQUESTED' CHECK (status IN ('REQUESTED','APPROVED','REJECTED','CANCELLED')),
  requested_by      bigint NOT NULL REFERENCES app_user(id),
  requested_at      timestamptz NOT NULL DEFAULT now(),
  decided_by        bigint REFERENCES app_user(id),
  decided_at        timestamptz,
  reject_reason     text,
  CONSTRAINT transfer_teacher_fk FOREIGN KEY (employee_id, employee_kind) REFERENCES employee (id, kind),
  CONSTRAINT transfer_replacement_fk FOREIGN KEY (replacement_id, replacement_kind) REFERENCES employee (id, kind),
  CONSTRAINT transfer_target_ck CHECK (
    (kind = 'TRANSFER' AND to_school_id IS NOT NULL AND to_school_id <> from_school_id)
    OR (kind = 'REPLACEMENT' AND replacement_id IS NOT NULL AND replacement_id <> employee_id)),
  CONSTRAINT transfer_decision_ck CHECK (status IN ('REQUESTED','CANCELLED') OR (decided_by IS NOT NULL AND decided_at IS NOT NULL)),
  CONSTRAINT transfer_reject_ck CHECK (status <> 'REJECTED' OR length(btrim(coalesce(reject_reason,''))) > 0)
);
CREATE UNIQUE INDEX transfer_one_open_uk ON transfer_request (employee_id) WHERE status = 'REQUESTED';
COMMENT ON TABLE transfer_request IS '[PHASE 2] One open request per Teacher. Approval (Director or leaving School''s Zone Manager, D5) ends the old placement the day before effective_date and inserts the new one in one transaction; the dated placement rows ARE the history. Replacement = two placements in one request.';

-- =====================================================================
-- 15. CONTENT  (2 tables)  [PHASE 2]
-- =====================================================================

CREATE TABLE content_item (
  id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  code           text   NOT NULL,
  title          text   NOT NULL,
  category       text,
  subject        text,
  author_user_id bigint NOT NULL REFERENCES app_user(id),
  created_at     timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT content_code_uk UNIQUE (code)
);
CREATE INDEX content_item_author_idx ON content_item (author_user_id);   -- IDX: My Content
COMMENT ON TABLE content_item IS '[PHASE 2] Content record. State is derived from the latest version.';

CREATE TABLE content_version (
  id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  content_id     bigint   NOT NULL REFERENCES content_item(id),
  version_no     smallint NOT NULL CHECK (version_no > 0),
  file_id        bigint   NOT NULL REFERENCES stored_file(id),
  submitted_at   timestamptz NOT NULL DEFAULT now(),
  review_status  text     NOT NULL DEFAULT 'PENDING' CHECK (review_status IN ('PENDING','APPROVED','RETURNED')),
  reviewed_by    bigint   REFERENCES app_user(id),
  reviewed_at    timestamptz,
  review_comment text,
  CONSTRAINT content_version_uk UNIQUE (content_id, version_no),
  CONSTRAINT content_version_file_uk UNIQUE (file_id),
  CONSTRAINT content_review_ck CHECK ((review_status = 'PENDING') = (reviewed_by IS NULL AND reviewed_at IS NULL)),
  CONSTRAINT content_return_ck CHECK (review_status <> 'RETURNED' OR length(btrim(coalesce(review_comment,''))) > 0)
);
COMMENT ON TABLE content_version IS '[PHASE 2] Numbered versions with the file and the review outcome; old versions kept. Single 100 MB cap at upload.';

-- =====================================================================
-- Seed: default settings (rows, not code, so Admin can change them)
-- =====================================================================
INSERT INTO setting (key, value, description) VALUES
  ('incentive_amount',           '5000', 'Manager incentive per School won (rupees)'),
  ('payment_due_days',           '30',   'Invoice due date = raised date + this many days'),
  ('substitute_default_day_rate','800',  'Default day rate when a Substitute has none'),
  ('weekly_offs',                '7',    'Organisation weekly offs, ISO weekday numbers, comma separated (7 = Sunday)'),
  ('login_max_failures',         '5',    'Failed sign-ins before lockout'),
  ('login_lockout_minutes',      '30',   'Lockout duration'),
  ('session_hours',              '12',   'Session lifetime, renewed on activity'),
  ('attendance_back_days',       '3',    'Days back a Teacher may mark own attendance');
