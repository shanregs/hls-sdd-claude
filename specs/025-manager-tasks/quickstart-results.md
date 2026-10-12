# Quickstart Results: Manager Tasks

Run on 2026-10-12 from the worktree.

- Backend: `com.hls.tasks.*` 29 tests pass (lifecycle, scope, authorization for every endpoint and role, deadline job idempotency and escalation, C2 idempotency and refusal, 023 listener, C1 feed, history append-only, notification text, ArchUnit module rules). `AttendanceAccessModelTest` updated for the new menu items (it also lacked the Salary Structures item from 013a).
- Frontend: `src/features/tasks` 16 Vitest tests pass including axe checks in light and dark; `tsc -b`, eslint and prettier clean.
- Full backend suite: 994 tests, 5 failures at the first run. Fixed: two menu tests (mine) and one notification URL in my test. Remaining and unrelated: `LeaveAttendanceTest.applyAndRemoveAreRefusedInALockedMonth` (depends on the current weekday; fails alone too) and `PayRulesCalendarTest` transfer case (passes alone; order or date dependent).
- Not done: browser walkthrough and timing check (SC-006), manual accessibility pass.
