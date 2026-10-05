# Quickstart results: Campus Recruitment, Job Offers and Induction (spec 016)

Date: 2026-10-05. Branch `feature/016-campus-recruitment`.

**How these were checked.** Every scenario of [quickstart.md](./quickstart.md) is exercised end to end by the
backend integration tests below, which call the real HTTP API against PostgreSQL (Testcontainers) as each role, and
by the frontend component tests. The demo-data test (`DevSeedTest`) also starts the whole application with the demo
seeders. **A manual walk through the screens in a browser has not been done yet**, nor the keyboard-only,
screen-reader and phone-width passes; those stay open until someone runs them (below).

| Scenario | Result | Evidence |
| --- | --- | --- |
| 1. Drive and candidates (US1): schedule, import, outcomes, assessment, calendar | Pass | `DriveApiTest`, `CandidateImportTest` (10 rows, one invalid; same file again; 500 rows in chunks), `OutcomeAssessmentTest`, `RecruitmentAuditTest`; `recruitment.test.tsx` (drives page, own-drive controls, import result) |
| 2. Offers (US2): draft, issue, replace, decline, expiry, letter | Pass | `OfferApiTest` (one open offer, issued terms never change even by SQL, supersede, decline, role matrix, escaped letter), `OfferExpiryTest` |
| 3. Accept and first salary (US3) | Pass | `OfferApiTest` (one Teacher in training with no salary entry, twice gives one Teacher, existing Teacher refused, exited match needs confirmation), `FirstSalaryListenerTest` (one salary entry equal to the offer on the first assignment, none on a later move, none for a Teacher not from an offer), `TeacherFirstAssignedTest` |
| 4. Induction (US4) | Pass | `BatchRulesTest` (seat limit, last seat under concurrency, overlap by the database), `InductionAttendanceTest` (training-day marks with no School, history, locked month), `SignOffTest`, `ReadyToDeployTest`, `OfferAcceptedEnrolmentTest`, `UnplacedTrainingMarkReadersTest`, `TrainingAttendanceTest`, `RollupCalculatorTest` |
| 5. Dashboard (US5) | Pass | `DashboardTest`; `recruitment.test.tsx` |
| 6. Boundary | Pass | `RecruitmentModuleRulesTest`, `ApplicationModulesTest` |
| Amendment A8 (School contacts) | Pass | `SchoolContactApiTest`, `SchoolDialog.contacts.test.tsx` |

Accessibility: every new page and dialog is in the axe harness (`a11y.test.tsx`) in light and dark mode with no
critical violations.

## Still to do by hand

- Walk the six scenarios in a browser on the local app (database on port 5433, demo data) as Asha, Divya and Manoj.
- Keyboard-only pass over the drive, offer and induction dialogs; one screen-reader pass; phone-width check of the
  tables.

## Found while building

- The route guard allowed only exact menu routes, so detail pages (`/recruitment/drives/:id`, and spec 012's
  `/operations/school-contracts/schools/:id`) would have shown "not authorized" in the browser. Fixed: a page below an
  authorized menu route inherits it (`isAuthorizedPath`, with tests).
