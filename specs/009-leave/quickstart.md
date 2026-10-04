# Quickstart: Leave Management

Runnable checks that prove the feature end to end. Setup is in `docs/running-locally.md` (database,
backend with demo data, frontend). Demo logins: Tara Teacher `9800000004` (password, or a one-time code), Manoj
Manager `9800000003`, Asha Admin `9800000001`, Divya Director `9800000002`, Sunil System
`9800000005` (all `Password123!`).

## Prerequisites

- Specs 001 to 005 and 008 running locally; the demo flag on (the seeder adds sample requests).
- Flyway applies `V17` on backend start; check the log for `Migrating schema ... to version 17`.

## Scenarios

1. **Apply (US1)**: sign in as Tara, LEAVE → Apply Leave. Pick Casual, a range of four days that
   includes a Sunday and a holiday, tick half day at the end. The preview shows the working-day
   count (for example 2.5). Submit; it appears in My Leave History as Pending.
2. **Refusals (US1)**: try an overlapping range, a range of only Sundays, a start 31 days back, and
   a range in a locked month; each is refused with its reason.
3. **Cancel (US2)**: cancel the Pending request in My Leave History; status shows Cancelled.
4. **Decide (US3)**: Tara applies again. Sign in as Manoj, OPERATIONS → Leave Management: the Pending
   count shows 1. Reject without a reason (refused), then with a reason; Tara sees the reason.
   Apply again, then approve.
5. **Feed (US4)**: as Manoj open OPERATIONS → Teacher Attendance for the month: the covered working
   days show **L** (red), set by Manoj; holidays and Sundays are not marked; the rollup's leave
   total went up. Mark one of the days as Present by hand, then try to approve a new overlapping
   request on a day Manoj set to Present: refused, naming the days.
6. **Revoke and cancel (US4)**: Manoj revokes the approved request with a reason: the L marks go,
   except a day he changed by hand. As Tara, apply and have it approved for next week, then cancel
   it from My Leave History (first day still in the future): marks removed.
7. **Scope (US3, US5)**: sign in as a Manager with a different assignment (or Admin to confirm
   org-wide): the other Manager's Teachers' requests are absent; a direct `GET /api/v1/leave/{id}`
   returns 404.
8. **Audit (US5)**: as Asha, AUDIT → Change History: entries for each transition and for the marks.

## Automated

```powershell
cd backend; mvn -q test -Dtest="com.hls.leave.**,NavigationSectionOrderTest,PermissionEligibilityTest,ModuleBoundaryTest"
cd frontend; npx vitest run src/features/leave src/a11y
```

Expected: all green; the a11y cases for `ApplyLeavePage`, `MyLeaveHistoryPage` and
`LeaveManagementPage` pass in light and dark themes.
