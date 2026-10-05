# Data Model: Campus Recruitment, Job Offers and Induction (spec 016)

`V22__create_recruitment_tables.sql` creates the tables below and relaxes one attendance column. No foreign keys cross
a module boundary (ids are plain UUIDs); foreign keys inside `recruitment` or inside `training` are allowed. Money is
`NUMERIC(12,2)`. Phones are stored normalized (digits only, last 10) next to the entered text.

## recruitment

| Table | Columns (constraints) |
| --- | --- |
| `college` | id PK; name varchar(160) not null; city varchar(120) not null; active boolean default true; version; created_by, created_at. **Unique** `(lower(name), lower(city))`. |
| `college_contact` | college_id FK; role varchar(16) in PLACEMENT_OFFICER, PRINCIPAL; name varchar(160) not null; phone varchar(20); email varchar(200). PK (college_id, role): one contact of each role. |
| `campus_drive` | id PK; college_id FK; season_label varchar(40) null; venue varchar(200); status varchar(10) in PLANNED, HELD, CANCELLED; cancel_reason varchar(300) (required when CANCELLED); scheduled_by uuid not null; version; created_at. |
| `campus_drive_date` | drive_id FK, drive_date date; PK (drive_id, drive_date). At least one row per drive (service rule). |
| `campus_drive_interviewer` | drive_id FK, user_id uuid; PK (drive_id, user_id). |
| `candidate` | id PK; drive_id FK; name varchar(160) not null; phone varchar(20) not null; phone_key varchar(10) not null (normalized); email varchar(200); degree varchar(120); study_year varchar(40); notes varchar(500); outcome varchar(10) null in SELECTED, WAITLISTED, REJECTED; outcome_by uuid; outcome_at timestamptz; teacher_id uuid null (set on acceptance); created_by, created_at; version. **Unique** `(drive_id, phone_key)`. |
| `candidate_outcome_history` | id PK; candidate_id FK; outcome varchar(10); note varchar(300); changed_by uuid; changed_at timestamptz. Insert-only. |
| `assessment_score` | id PK; candidate_id FK; assessment_no int (1, 2, ... per candidate); criterion varchar(14) in SPEAKING, ENGLISH, COMMUNICATION; score smallint check between 1 and 5; remarks varchar(300); assessed_by uuid; assessed_at timestamptz. Insert-only; the highest `assessment_no` is current. |
| `job_offer` | id PK; candidate_id FK; role varchar(60) not null (Trainee / English Trainer); phone_key varchar(10) not null (the candidate's normalized phone, for the one-open-offer-per-person rule); monthly_salary numeric(12,2) check > 0; allowances varchar(300); terms varchar(1000); expected_joining date; offer_date date not null; response_deadline date not null (>= offer_date); status varchar(10) in DRAFT, ISSUED, ACCEPTED, DECLINED, EXPIRED, SUPERSEDED; supersedes_id uuid null; decline_reason varchar(300); issued_by uuid, issued_at; decided_by uuid, decided_at; teacher_id uuid null; version; created_by, created_at. **No stipend column** (induction is unpaid). |

Indexes and rules on `job_offer`: partial **unique** on `(phone_key)` where `status IN ('DRAFT','ISSUED')` (at most one open offer per person, even across two drives); partial **unique** on `(phone_key)` where `status = 'ACCEPTED'`; a trigger rejects `UPDATE`
of `role`, `monthly_salary`, `allowances`, `terms`, `expected_joining`, `offer_date` and `response_deadline` when
`OLD.status <> 'DRAFT'`; index on `(status, response_deadline)` for the expiry job.

## training

| Table | Columns (constraints) |
| --- | --- |
| `induction_batch` | id PK; name varchar(120) not null; starts_on, ends_on date (`ends_on >= starts_on`); trainer varchar(160); venue_type varchar(8) in PHYSICAL, VIRTUAL; venue varchar(200); seat_limit int check >= 1; status varchar(10) in PLANNED, RUNNING, COMPLETED, CANCELLED; version; created_by, created_at. |
| `induction_enrolment` | id PK; batch_id FK; teacher_id uuid not null; starts_on, ends_on date (copied from the batch); enrolled_by, enrolled_at; result varchar(14) null in COMPLETED, NOT_COMPLETED; remarks varchar(300); signed_by, signed_at; follow_up varchar(10) null in NEXT_BATCH, RELEASED. **Unique** `(batch_id, teacher_id)`; gist exclusion on `(teacher_id =, daterange(starts_on, ends_on, '[]') &&)` for enrolments whose `result` is null or COMPLETED, so a Teacher is never in two batches on overlapping dates. |
| `induction_absence` | id PK; enrolment_id FK; absent_on date; reason varchar(300) not null; recorded_by, recorded_at. **Unique** `(enrolment_id, absent_on)`. A present day has no row here: it is a training-day mark in 008. |

## school contacts in the same migration (A8, spec 005)

`school_contact (school_id, role in PRINCIPAL/ACCOUNTANT, name not null, phone, email)`, PK `(school_id, role)`, owned by the `school` module (the foreign key to `school` is inside that module). Exposed by `GET` and `PUT /api/v1/schools/{id}/contacts`; audit entity `SCHOOL`, fields `principal` and `accountant`.

## attendance change in the same migration (A5)

`ALTER TABLE attendance_mark ALTER COLUMN school_id DROP NOT NULL;` plus a check that a null School is allowed only for
the training status code. The history table already allows a null School.

## Derived values (not stored)

- **Ready to deploy**: Teacher status ACTIVE, a COMPLETED enrolment, no current assignment (spec 012).
- **Candidate stage** for the funnel: interviewed, assessed (has scores), selected, offered (an offer exists),
  accepted, inducted (COMPLETED enrolment), placed (current assignment), active (Teacher status).
- **Joining ratio** per college and season: candidates with an ACCEPTED offer divided by candidates SELECTED.
- **Drive status shown**: "held, no candidates" when PLANNED, all dates past, and no candidates.

## Audit entries (via `ChangeRecorder`)

`COLLEGE`, `CAMPUS_DRIVE`, `CANDIDATE` (create, outcome, import summary), `ASSESSMENT`, `JOB_OFFER` (draft, issue,
supersede, accept, decline, expire), `TEACHER_FROM_OFFER` (the acceptance, with the new Teacher id),
`INDUCTION_BATCH`, `INDUCTION_ENROLMENT`, `INDUCTION_ATTENDANCE`, `INDUCTION_SIGNOFF`, `TEACHER_FIRST_SALARY`.
