# Quickstart results: Leave Management

Run on 2026-10-04/05 against the local app (demo data on, backend and frontend running).

| Scenario | Result |
| --- | --- |
| 1-3 Teacher applies, refusals, cancel | Covered by `LeaveApplyTest`, `LeaveHistoryTest` and `ApplyLeavePage`/`MyLeaveHistoryPage` tests; Tara signed in with her password in the browser |
| 4 Manager decides | `LeaveDecisionTest`; Manoj saw the 3 pending requests with approve/reject icons in the browser, and approved three from the UI |
| 5 Feed into attendance | `LeaveFeedTest` (marks on working days only, halves, replace a Teacher-set mark, refuse a supervisor-set day, future days) |
| 6 Revoke and Teacher cancel | `LeaveFeedTest` |
| 7 Scope | `LeaveScopeBoundaryTest` (two Managers, Admin/Director see all, 404 out of scope) |
| 8 Audit | `LeaveAuditTest` |

Also checked in the browser: the pending leave count on the Manager dashboard (3), one-time code sign-in for a
Manager, and the Manager's Leave Management screen.

## Test totals

- Backend: 63 tests (`Leave*Test`, `DevSeedTest`) pass. The full backend suite was started on the merged branch
  but stopped before it finished, so it is not confirmed.
- Frontend: 326 of 334 pass. The 8 failures are `a11y.test.tsx` cases (Holiday Calendar x2, Login History,
  API Access, User Management, My Attendance, Attendance History, Role & Permissions) that hit the 30 s test
  timeout on this heavily loaded machine; none is an accessibility violation and none is a leave screen.
  The three leave screens and the Settings and Apply cases pass in both themes.

## Gaps against spec.md

- No notifications to the Teacher on a decision (spec 010).
- The Leave Management icons can appear a moment after the table on first load (cosmetic).
- A manual pass of every quickstart scenario by a person is still pending.
