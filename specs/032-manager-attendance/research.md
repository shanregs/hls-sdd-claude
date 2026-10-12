# Research: Manager Attendance and Field Days (spec 032)

Every open point from the spec is settled here. No `NEEDS CLARIFICATION` remains.

## 1. What already exists, and what does not

- **Exists and is used**: the working calendar of spec 008 through `attendance.api.WorkingCalendar` (added by 013a);
  `organization.api.ManagerQueries` (`managerByUserId`, `employment` = joining and exit dates, `allManagers`,
  `currentSchoolIds`); the shared `files.api.FileStore` (jpg or png, at most 10 MB, checked by content); spec 010's
  event-driven notifications (`NotificationType`, `RecipientResolver.activeAdminsAndDirectors`); spec 009's leave types
  and `LeaveTypes.isLossOfPay`; the existing scheduler (`@Scheduled`, as `OfferExpiryJob` does); `ChangeRecorder` for
  audit.
- **Does not exist**: the planned-activity interface (contract C1) of specs 016, 023, 024 and 025 (only 016 and 023 are
  built, and neither exposes it). **Decision**: a Field day carries a `purpose` text now; the link table is added in the
  spec that publishes the interface, and the spec's rule then becomes "link required unless flagged". Nothing here
  blocks on it.

## 2. Where it lives

- **Decision**: attendance of Managers is a sub-package of `attendance` (`attendance.manager.{internal,web}`), separate
  from the Teacher tables and screens of spec 008. **Manager leave is a sub-package of `leave`** (`leave.manager`).
- **Rationale**: an existing ArchUnit rule says `attendance` must not depend on `leave` (leave depends on attendance).
  A Manager's leave request needs the leave types, so the request flow belongs in `leave`. It reaches attendance through
  `attendance.api.ManagerLeaveAttendance` (apply and remove leave days, the same shape as `LeaveAttendance` for
  Teachers). The attendance record stores whether a leave day is unpaid at the moment it is applied, so attendance never
  needs to ask `leave` about a type.
- **Alternatives**: put the whole flow in `attendance` and copy the leave types (two catalogs); relax the ArchUnit rule
  (a cycle between the modules).

## 3. The rules, stated as formulas

Let `E(m)` be a Manager's employment span (joining date to exit date, inclusive, either end open) and `W(m, month)` the
default-calendar working days of the month inside `E(m)`. For each date `d` in `W`, with `row(d)` the Manager's record:

| Case | Worked | Leave (paid) | Unpaid |
| --- | --- | --- | --- |
| `row` has a check-in, no leave | 1 | 0 | 0 |
| `row` has full-day leave, paid type | 0 | 1 | 0 |
| `row` has full-day leave, Loss-of-Pay type | 0 | 0 | 1 |
| half-day leave and a check-in | 0.5 | 0.5 if paid, else 0 | 0.5 if Loss-of-Pay, else 0 |
| half-day leave and no check-in | 0 | 0.5 if paid, else 0 | 0.5 absence, plus 0.5 if Loss-of-Pay |
| no `row`, or a `row` with neither a check-in nor leave | 0 | 0 | 1 |

- A check-in without a check-out is a worked day (clarification 1). A check-in on a weekly off or a holiday, or outside
  `E(m)`, is recorded but is not in `W`, so it changes no figure.
- **Dates not yet over**: a date after today, and today itself until the day ends, is not counted as unpaid (it is
  "pending"); the figures for a month in progress say so. Payroll reads a month only after it is locked, which needs the
  month to have ended.
- **No joining date**: `W` cannot be computed, so the answer is `NoJoiningDate` and gives no unpaid days (clarification
  4); check-in and leave are refused.
- **Field days** are counted separately and are worked days.
- The rules live in one pure class (`ManagerMonthCalculator`) given the records, the working dates, the span and today's
  date; it reads no clock and no repository, so the same inputs always give the same figures.

## 4. Field days and activity links

- **Decision**: a Field day needs a non-empty purpose (up to 500 characters) now. When the planned-activity interface
  exists, a Field day may instead carry one or more links, and a purpose-only Field day is accepted and flagged "not
  linked" (spec FR-007). The flag column (`unlinked`) is created now and is set only once the interface exists.
- **Rationale**: the spec fixes both rules; building the flag now avoids a migration later.

## 5. Location, photo, and time

- **Decision**: the client sends latitude, longitude and optionally an accuracy in metres. The server checks the ranges
  (latitude -90 to 90, longitude -180 to 180) and stores them with the check-in and again with the check-out. No distance
  or geofence rule (out of scope). The time of a check-in, check-out or correction is the server clock converted to the
  business time zone through the shared `BusinessCalendar`; a device time is never read.
- **Photo**: a second request after the check-in sends the file to `FileStore.store(actor, "MANAGER_DAY", dayId, ...)`;
  the check-in never waits on it and is valid without it. The file store's limits apply (jpg or png, 10 MB), and only the
  Manager, Admin and Director may open it.
- **Privacy**: a location is shown only on a day's detail to the Manager, Admin and Director. Grids and exports carry
  status only. Retention follows the audit retention; no separate purge is added here.
- **Alternatives**: trust the device time (spoofable); require a photo (the deck does not).

## 6. Month lock, and pending requests in another module

- **Decision**: locking follows spec 008's pattern: a month state per Manager (`OPEN` or `LOCKED`) with an event log
  (lock, reopen, reason). Locking is allowed only after the month has ended and refused while any leave request or
  forgotten-check-in request touching the month is pending. It stores the month's figures (`frozen_*` columns) so payroll
  reads what was locked.
- **How the lock sees leave requests without depending on `leave`**: `attendance.api.PendingManagerRequests` is an SPI
  returning the pending requests that touch a month. `leave.manager` implements it for leave; attendance adds its own
  forgotten-check-in requests. The lock service collects every implementation (`ObjectProvider`) and refuses with the
  list. This is the same inversion `school.api.SchoolDeactivationGuard` already uses.
- **Writes into a locked month** (check-in, completion, correction, regularization approval, leave apply or remove) are
  refused by one `requireOpen(manager, month)` check shared by every writer; leave applying days across a locked month
  fails the whole decision.
- A reopen needs a reason, clears the frozen figures and writes an event; locking again freezes the new figures.

## 7. The missed check-in alert

- **Decision**: a scheduled job runs every 2 minutes. After the cut-off time of a working day it finds Managers who are
  employed that day, have no record row with a check-in, no approved leave covering the day, and no row in
  `manager_missed_alert`; for each it inserts the alert row (unique on manager and date) and publishes one
  `ManagerCheckInMissed` event. The notification module turns the event into notifications for the Manager and for every
  active Admin and Director (clarification: every one of them, by role).
- **Rationale**: the unique key makes it safe if the job runs twice or on two instances, and it answers within the 5
  minutes of SC-005. A check-in after the alert makes the day "late" and removes it from the missed list (the list is
  computed from the rows, not from the alert table).
- **Cut-off and late time** are rows in `manager_attendance_setting` (a single row, versioned like other settings);
  changing them applies from the next evaluation and is audited. Defaults 10:30 and 09:30.
- **Alternatives**: one timer per Manager (many timers); a database job (no code review and no tests).

## 8. Forgotten check-ins (regularization)

- **Decision**: a Manager may request a check-in for any past day of an unlocked month with a reason, a proposed time and
  a day type (clarification 2). An Admin or Director approves or rejects (reason on rejection). Approval writes the day as
  worked with the proposed time, marked `added_later`, and keeps the request as the record of why. The proposed time must
  be on the requested date and not in the future.
- **Rationale**: the server clock rule (section 5) forbids backdating through the normal check-in, so a past day needs a
  decision by someone else.

## 9. Corrections and history

- **Decision**: a correction by an Admin or Director (with a reason) changes the day's fields and writes an append-only
  history row holding the prior values; the first history row holds the original. A correction may add a missing
  check-out, add or change a check-in or day type, or clear a mistaken day. It is refused in a locked month.
- A day is never deleted; clearing sets it back to "no check-in" with the history kept.

## 10. Payroll integration (changes to spec 013a)

- `ManagerAttendanceReadApi.month(managerId, month)` returns `Found` (figures and the lock state), `NoJoiningDate`, or
  `Unknown` (no such Manager). `PayRulesImpl` maps `Found` to `UnpaidDays.Found` with each unpaid fraction, and the other
  two to `UnpaidDays.Unavailable` with the stated reason (never an empty `Found`).
- For an **Admin or Director** (`Kind.STAFF`), `PayRules` returns `UnpaidDays.Found` with an empty list by rule
  (clarification: paid in full), and `totalLossOfPay` is `Found` with zero. This is a stated answer, not "not available".
- A month in progress yields `Found` with `pending` dates; payroll (013b) decides to read only locked months.

## 11. Notifications

New `NotificationType` values: `MANAGER_CHECK_IN_MISSED`, `MANAGER_LEAVE_REQUESTED`, `MANAGER_LEAVE_DECIDED`,
`MANAGER_REGULARIZATION_REQUESTED`, `MANAGER_REGULARIZATION_DECIDED`, `MANAGER_DAY_CORRECTED`. Events are published by
the writers inside the transaction (as spec 010 does) so a notification commits or rolls back with the change.

## 12. Deferred on purpose

Planned-activity links (needs contract C1), geofence or distance checks, shifts and overtime, an export of the grid, the
Android screens (033).
