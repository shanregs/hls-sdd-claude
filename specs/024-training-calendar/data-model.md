# Data Model: Training Calendar

Migration `V110__create_training_calendar.sql` (newest on main is V90; spec 031 plans V100, so V110 here; re-check at implementation). Every table has
`id uuid` primary key. No foreign key crosses a module: teacher, manager, user and School ids are plain uuids; foreign keys
inside the `training` tables are real. Dates are business dates (Asia/Kolkata); times are local `time` values with no
conversion. Nothing in this module stores pay, a stipend or a cost (FR-030). Induction tables from spec 016 are unchanged.

## training_pattern

| Column | Type | Notes |
| --- | --- | --- |
| rule_kind | text | NTH_WEEKDAY, DAY_OF_MONTH |
| nth | smallint | 1 to 4, or 5 for "last" (NTH_WEEKDAY only) |
| weekday | smallint | 1 (Monday) to 7 (Sunday) (NTH_WEEKDAY only) |
| day_of_month | smallint | 1 to 31, clamped to the month's last day (DAY_OF_MONTH only) |
| starts_at, ends_at | time | `ends_at > starts_at` |
| topic | text | up to 200 |
| trainer_name, trainer_contact | text | name up to 120, contact up to 160, free text (no managed list) |
| venue_kind | text | PHYSICAL, VIRTUAL |
| venue | text | place or https link, up to 300 |
| description | text | up to 1000, optional |
| default_group_kind | text | null, INDIVIDUAL is not allowed here; SCHOOL, MANAGER, ZONE, ALL_TEACHERS, ALL_MANAGERS, ALL |
| default_group_ref | uuid | School, Manager or Zone id for those kinds, else null |
| start_month | date | first day of the first month |
| end_month | date | first day of the last month, null while open-ended |
| version | bigint | optimistic lock |
| created_by, created_at, updated_at | uuid, timestamptz, timestamptz | |

Checks: `rule_kind = 'NTH_WEEKDAY'` implies nth and weekday set and day_of_month null; `DAY_OF_MONTH` the reverse;
`end_month >= start_month`; a group kind needing a ref has one. Unique partial index on
`(rule_kind, nth, weekday, day_of_month, starts_at) WHERE end_month IS NULL` (probable duplicate, refused).

## training_pattern_skip

`pattern_id` (FK), `month date` (first day), `reason text` (required, up to 300), `skipped_by`, `skipped_at`. Unique
`(pattern_id, month)`.

## training_session

| Column | Type | Notes |
| --- | --- | --- |
| session_type | text | WEEKEND, MONTHLY |
| topic | text | up to 200 |
| session_date | date | |
| starts_at, ends_at | time | `ends_at > starts_at` |
| trainer_name, trainer_contact | text | free text |
| venue_kind, venue | text | PHYSICAL or VIRTUAL; place text or https link |
| description | text | optional |
| seat_limit | int | null means no limit; must be at least the number of current Registered when lowered |
| status | text | DRAFT, PUBLISHED, HELD, CANCELLED, SKIPPED |
| pattern_id | uuid | FK to `training_pattern`, null for a one-off session |
| pattern_month | date | first day of the month the pattern generated this row for |
| hand_edited | boolean | true after any edit of a generated session |
| skip_reason, cancel_reason | text | for SKIPPED and CANCELLED |
| held_at | timestamptz | when the first attendance was saved (status HELD) |
| version | bigint | optimistic lock |
| created_by, created_at, updated_at | uuid, timestamptz, timestamptz | |

Checks: `session_type = 'MONTHLY'` is the only type a pattern generates; `status = 'SKIPPED'` implies `pattern_id` not null.
Unique `(pattern_id, pattern_month) WHERE pattern_id IS NOT NULL` (FR-005). Indexes: `(session_date)`, `(status, session_date)`.

### Status transitions

```
DRAFT --publish--> PUBLISHED --first attendance saved--> HELD
DRAFT --delete--> (row removed)
PUBLISHED --cancel (reason)--> CANCELLED
PUBLISHED --move / edit--> PUBLISHED   (history row)
(generated) PUBLISHED --skip (reason)--> SKIPPED --unskip--> PUBLISHED
```

HELD and CANCELLED are final; a HELD session can only have its attendance corrected. A generated session is created PUBLISHED.
Only DRAFT sessions can be deleted (Delete permission).

## training_session_history (insert-only; trigger refuses UPDATE and DELETE)

`session_id` (FK), `field` (`date`, `time`, `trainer`, `venue`, `topic`, `status`, `seat_limit`), `before_value`, `after_value`,
`reason`, `changed_by`, `changed_at`. The C1 `originalDate` is the `before_value` of the oldest `date` row. A date or trainer
move writes one row per changed field in one transaction.

## training_registration

`session_id` (FK), `teacher_id`, `status` (REGISTERED, CANCELLED), `registered_by` (user), `registered_at`, `cancelled_at`,
`cancelled_by`, `version`. Unique `(session_id, teacher_id)`: registering again after a cancellation flips the same row back
(each change is in Change History). Seat limit is checked under a pessimistic lock on the session row.

## training_assignment

| Column | Type | Notes |
| --- | --- | --- |
| session_id | uuid | FK |
| person_kind | text | TEACHER, MANAGER |
| person_id | uuid | teacher id or manager id |
| source | text | INDIVIDUAL, GROUP |
| group_kind, group_ref | text, uuid | what was picked for a GROUP source (SCHOOL, MANAGER, ZONE, ALL_TEACHERS, ALL_MANAGERS, ALL) |
| status | text | REQUIRED, REMOVED |
| assigned_by, assigned_at | uuid, timestamptz | |
| removed_by, removed_at, removed_reason | uuid, timestamptz, text | set on removal (also by the exit clean-up) |
| cannot_attend_note | text | the person's "I cannot attend" message, up to 300; informs the Admin, changes nothing |
| version | bigint | |

Unique `(session_id, person_kind, person_id)`: assigning a person twice is one obligation; assigning a removed person flips
the row back to REQUIRED. A group assignment is a snapshot (no link to later group changes).

## training_attendance_entry

| Column | Type | Notes |
| --- | --- | --- |
| session_id | uuid | FK |
| person_kind, person_id | text, uuid | TEACHER or MANAGER |
| basis | text | ASSIGNED, REGISTERED, WALK_IN (a person both assigned and registered is ASSIGNED) |
| outcome | text | PRESENT, ABSENT, EXCUSED |
| day_value | numeric(3,2) | 1.00 or 0.50 for PRESENT, else null |
| reason | text | required for ABSENT and EXCUSED, up to 300 |
| on_leave | boolean | approved leave found on the session date when saved (reason is "On leave"); never an exception |
| certificate_note | text | visible to the person, up to 300 |
| internal_remark | text | never shown to the person, up to 500 |
| sync_state | text | TEACHER rows: NOT_NEEDED (not Present), WRITTEN, CLASH, PENDING; Manager rows: NOT_APPLICABLE |
| clash_note | text | description of the school mark that blocked the 008 write, when CLASH |
| recorded_by, recorded_at | uuid, timestamptz | last change |
| version | bigint | optimistic lock |

Unique `(session_id, person_kind, person_id)`: exactly one outcome per person (US4 scenario 1). Check: `outcome = 'PRESENT'`
implies day_value is 1.00 or 0.50; `basis = 'WALK_IN'` implies outcome PRESENT.

## training_attendance_history (insert-only)

`entry_id` (FK), `action` (CREATED, CORRECTED), `outcome`, `day_value`, `reason`, `changed_by`, `changed_at`. Every save of an
entry whose value changed adds a row (FR-018). The 008 mark has its own history in 008.

## training_exception

| Column | Type | Notes |
| --- | --- | --- |
| session_id | uuid | FK |
| person_kind, person_id | text, uuid | |
| entry_id | uuid | FK to the attendance entry that raised it |
| status | text | OPEN, CLOSED, WITHDRAWN |
| closure_kind | text | EXCUSED, MADE_UP, ACKNOWLEDGED (CLOSED only) |
| closure_note | text | required for EXCUSED and ACKNOWLEDGED, up to 300 |
| makeup_session_id | uuid | MADE_UP only; a later session where the person is recorded Present |
| raised_at | timestamptz | |
| closed_by, closed_at | uuid, timestamptz | |
| version | bigint | |

Unique `(session_id, person_kind, person_id)`. WITHDRAWN is set when the entry is corrected away from ABSENT; a later
correction back to ABSENT reopens the same row (OPEN).

### Exception state transitions

```
(Absent saved, assigned, not on leave) --> OPEN
OPEN --close(EXCUSED | MADE_UP | ACKNOWLEDGED)--> CLOSED
OPEN or CLOSED --entry corrected away from Absent--> WITHDRAWN
WITHDRAWN --entry corrected back to Absent--> OPEN
```

## training_reminder_sent

`session_id`, `person_kind`, `person_id`, `days_before` (smallint), `sent_at`. Unique on the first four: a reminder goes out
once per person and offset. Cleared for the session when it is moved.

## Derived, not stored

- **Expected / attended / absent / excused / walk-in / no-show counts** of a session and its attended-versus-expected rate for
  assigned people.
- **Attendance not recorded**: status PUBLISHED and `session_date + attendance_due_days < today`.
- **Holiday notice**: session date is not a working day in the default calendar (`WorkingCalendar`).
- **Group not fully assigned**: people the pattern's group resolves to today who have no assignment on the session.
- **Repeat marker**: OPEN or ACKNOWLEDGED exceptions in the window for the same person, at or over the threshold.
- **Training history line**: union of the person's assignments, registrations and entries by session, plus the induction entry.
- **Yearly attended versus expected** (research.md section 10).
- **Status given to C1**: PLANNED (PUBLISHED), HELD, CANCELLED.

## Integrity rules

- A person has at most one assignment, one registration and one attendance entry per session (unique constraints).
- A registered person who is not assigned and is recorded ABSENT is shown as a no-show; no exception row is written.
- A Teacher with status IN_TRAINING or EXITED cannot be assigned or registered (checked on write); an assignment of an exited
  person to a not-held session is removed by the daily job.
- A Held session cannot be moved, re-dated, re-assigned or have its trainer changed; attendance can be corrected.
- No row ever stores a pay or cost field. Training cost lives in Expenses (015).
- The session history, attendance history and exception closure are never edited, only appended.
