# Quickstart results: Substitution Workflow (spec 017a)

Run on 2026-10-11 on branch `feature/017a-substitution`. Every step was checked by an automated test (Testcontainers
PostgreSQL, Vitest); **nobody has clicked through the screens in a browser**.

| Step | Result | Evidence |
| --- | --- | --- |
| Absence raises one requirement and one alert; repeat does nothing; half day; withdrawn then reopened; no Manager goes to Admin and Director; leave raises one per working day; weekly off raises nothing | Pass | `RequirementRaisingTest` (9) |
| Absence/withdrawn events, loss-of-pay flag (absence, casual leave, Loss-of-Pay leave), public loss-of-pay read incl. bulk | Pass | `AbsenceEventAndLossOfPayTest` (8) |
| Principal confirmation record, change, clear, scope, locked month, audit, status change clears it | Pass | `PrincipalConfirmationTest` (5) |
| Candidates, assign, refuse unavailable, stale version, one-day cap with two half days, reassign, release, Manager cover, School informed, withdrawn absence, append-only history, audit, credits | Pass | `SubstitutionWorkflowTest` (14) |
| Permission and scope per endpoint and role; My Substitutions own only, no absent name; menu and data scope | Pass | `SubstitutionAuthorizationTest` (6), `PermissionEligibilityTest` |
| Module rules | Pass | `SubstitutionModuleRulesTest` (4), `ApplicationModulesTest` |
| Screens: list, detail (assign, disabled unavailable, informed prefill, cancelled), My Substitutions | Pass (component level) | `Substitution.test.tsx` (7) |

Full suite: backend 1012 tests; failures left are not from this work: `LeaveAttendanceTest.applyAndRemoveAreRefusedInALockedMonth`
fails the same way on `origin/main` (date-dependent), `PayRulesCalendarTest` is flaky (passes alone), and the other
failures found were fixed (old expectations of the navigation and route tables; a notification count test).
Frontend: 544 of 545 pass; `marketing.test.tsx` (spec 023) times out under load and is unrelated.

Not done: browser walkthrough and keyboard/screen-reader pass (T025), Postman folder (T026).
