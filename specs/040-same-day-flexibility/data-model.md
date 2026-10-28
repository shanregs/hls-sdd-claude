# Data Model: Same-Day Flexibility Without Prior Approval

One migration, **`V150__same_day_flags.sql`**. No table is created, no existing column changes meaning, no row is
rewritten (every existing row reads `same_day = false`).

## Changes

| Table | New column | Meaning |
| ----- | ---------- | ------- |
| `leave_request` | `same_day boolean not null default false` | The request was recorded for today and approved at once (spec FR-001). |
| `leave_request` | `linked_request_id uuid null` (FK to `leave_request.id`) | On the **later part** of a split request: the same-day part it belongs to (D2). Null otherwise. |
| `substitution_requirement` | `same_day boolean not null default false` | The Manager cover was recorded on the duty date (D6). Cleared when the requirement leaves `MANAGER_COVER`. |
| `task` | `same_day boolean not null default false` | The task was logged on the spot or marked done on the spot (D7). |
| `marketing_activity` | `same_day boolean not null default false` | The visit or activity was logged for today without a plan (D7). |
| `task`, `marketing_activity` | `client_key uuid null`, unique where not null | The phone's `Idempotency-Key` for an offline same-day log; a repeat finds the first record (contract section 6). |

Indexes: a partial index `WHERE same_day` on each table's date column (`first_date`, `duty_date`, `due_on`,
`activity_date`), because the filter and the counts read only flagged rows.

No flag on `attendance_mark`: the day's mark is a leave mark made by a request, and the request is flagged; the replaced
Absent value stays in the existing mark history.

## Entities (from the spec)

- **Same-day mark** - the boolean above. Changes no status and starts no review.
- **Same-day leave request** - a `leave_request` with `status = APPROVED`, `same_day = true`, `created_by_user_id` = the
  recorder (the Teacher or the Manager, Admin or Director), `decided_by_user_id` = the same user,
  `decision_note = 'same-day, auto-accepted'`, `decided_at` = the time of recording.
- **Manager substitution entry** - a `substitution_requirement` in `MANAGER_COVER` with `cover_user_id`, plus its
  `substitution_history` rows. Not a table of its own.
- **Same-day task / visit** - a `task` (final status `VERIFIED`, or `IN_PROGRESS`) or a `marketing_activity`
  (`COMPLETED`) with `same_day = true`.

## State and rules

**Leave request** (spec 009 machine, unchanged): `PENDING -> APPROVED | REJECTED | CANCELLED`; `APPROVED -> CANCELLED`.
New: a request may be *created* `APPROVED`. Cancelling a same-day request the same day is allowed (FR-016); the existing
`cancelOwn` rule that refuses cancelling an approved request whose first date is today is relaxed for `same_day = true`
requests when the cancelling user is the Teacher, the recorder, or a supervisor in scope, and only on the same business
day. The cancel withdraws the marks (`attendance.remove`), which publishes `TeacherAbsenceWithdrawn`; the substitution
requirement and any Manager cover entry are cancelled by `AbsenceListener` with history kept.

**Split** (D2): both rows share teacher and school; `linked_request_id` is on the later (Pending) row. The overlap
constraint is satisfied because the ranges are disjoint (`today..today` and `tomorrow..last`).

**Substitution requirement** (spec 017a machine, unchanged): `OPEN | ASSIGNED | MANAGER_COVER | CANCELLED`.
`same_day` is set in `cover()` and cleared in `assign()` / `release()` / `cancel()`, so it always describes the current
outcome; the history keeps the earlier ones.

**Task** (spec 025 machine): the path `ASSIGNED -> ACKNOWLEDGED -> IN_PROGRESS -> COMPLETED -> VERIFIED` is unchanged. A
same-day task enters it at `IN_PROGRESS`, or crosses it in one transaction ending at `VERIFIED`. History rows are written
for each step so audit shows a normal trail.

**Marketing activity** (spec 023 machine): `PLANNED -> COMPLETED | CANCELLED`; a same-day visit is created at
`COMPLETED`.

## Validation

- Dates are never taken from the client for the online path; the server uses `BusinessDate.today()`. The offline path is
  the exception described in research.md D7.
- Same-day leave needs a placement on today (existing check), a working day (existing `LeaveCounter`), no live overlap,
  an unlocked month, and no supervisor-set mark other than L or Absent (D4).
- A task or visit is for the caller only; an assignee id in the body is ignored for same-day calls (the contract does not
  accept one).
- Completion note: 10 to 2000 characters when saving a task as completed.

## Audit

No new audit table. Each write adds to the audit event it already emits (`LeaveAudit.created/status`,
`SubstitutionAudit.status`, `TaskRecorder.change`, `ChangeRecorder.record`) the detail `sameDay=true; recordedBy=<user>;
for=<person>; auto-accepted`. The mark history of a replaced Absent is the existing attendance history.

## Reporting counts

For a month (and an optional Zone): per Manager and per School, the number of same-day leave requests (days), Manager
covers (days, half days as 0.5), same-day tasks, and same-day visits. Read-only, from the `SameDayCounts` interface of
each module; nothing is stored.
