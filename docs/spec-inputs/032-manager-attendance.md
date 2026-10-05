# 032 Manager Attendance and Field Days: `/speckit-specify` input

2026-10-05. Roadmap row 032 (new, from the Director deck slide 9). Depends on 005 and **005a** (Manager designation and
joining date), 010 (alerts) and, for the field-day links, the planned-activity interfaces (contract C1) of 016, 023, 024
and 025. Mobile screens are **033**. Feeds **013b** (Manager payroll) and 015 (travel claims). Decisions D5, D12 and D15
in `docs/spec-roadmap.md` apply.

## Feature description (paste as the argument to `/speckit-specify`)

032-manager-attendance: Daily attendance for Managers, kept the way the deck describes it: check in, check out, say
what kind of day it was, and leave that the Director approves. Module `attendance` (Manager part), separate from the
Teacher attendance of spec 008.

1. **Check-in and check-out.** On a working day a Manager checks in and later checks out. Each records the time and
   the device location, which is required (D15); a photo may be added but is not required. One check-in per day; a
   missing check-out is flagged for the day and can be completed with a reason.
2. **Day type.** The Manager chooses at check-in: **Office**, **Field** (school visit, college drive or training), or
   **Leave**. A field day must link to one or more planned activities from the calendar (a visit from 023, a drive from
   016, a session from 024 or a task from 025), through the shared planned-activity interface. Until that interface
   exists the Manager enters a purpose text instead, and the link becomes required when it does.
3. **Manager leave.** A Manager requests leave (type, dates, reason); the Director or Admin approves or rejects it
   with a reason on rejection. Approved leave marks the days as Leave. A day with no check-in and no approved leave is
   an unpaid absence (LOP) for payroll (D12).
4. **Missed check-in alert.** If a Manager has not checked in by a cut-off time (a setting) on a working day without
   approved leave, the Manager, the Director and the Admin are notified (spec 010), once per day, and the day shows as
   a missed check-in on the Director dashboard.
5. **Working days.** From the holiday calendar and weekly offs of spec 008 (the default calendar; a Manager has no
   School).
6. **Views.** A Manager sees their own month (status per day, field-day links, leave) and today's plan. Admin and
   Director see a grid of all Managers by month, filter by area, the list of missed check-ins, and Managers on leave
   today. A Manager's day can be corrected by an Admin or Director with a reason, audited.
7. **Month lock.** An Admin locks a Manager's month (or all Managers) after corrections; locking freezes the figures
   payroll reads; a reopen needs a reason (same pattern as spec 008).
8. **Public interface** `ManagerAttendanceReadApi` for payroll and reports: working days, days worked, leave days,
   unpaid days, field days, and whether the month is locked.
9. New permission modules (Manager: own; Admin and Director: all); every check-in, correction, leave decision and lock
   is audited (spec 003). Teacher and System have no access.

Out of scope: the Android screens (033), geofencing and distance checks, shifts and overtime, the salary itself
(013b), travel claims (015), and Teacher attendance (008).

## Points for `/speckit-clarify`

- The cut-off time for a missed check-in, and the working-hours rule (a late check-in, a half day).
- Can a Manager check in for a past day (a regularization request), and who approves it?
- Is the photo stored (it needs a file store the project does not have yet) or deferred to a later spec?
- Does a Field day with no linked activity get refused, or flagged for review?
- Who approves the leave of the Director, and of a Manager when the Director is away (Admin)?
- Does the Admin lock all Managers at once, or one Manager at a time?
- What does a Manager see if they have both a leave day and a recorded check-in?
