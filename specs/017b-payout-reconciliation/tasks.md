# Tasks: Substitute Teachers, Day-Rate Pay and Reconciliation

**Input**: `specs/017b-payout-reconciliation/` (spec.md, plan.md, research.md, data-model.md, contracts/, quickstart.md)

**Status**: Not started. Q1 to Q8 answered by the owner; N1 to N6 assumed on option A (clarification-questions.md). If an N answer differs, revisit: N1 T008 to T010; N2 T014; N3 T017 to T020; N4 T043 to T044; N5 T045 to T046.

Format: `- [ ] T### [P] [US#] description with path`. `[P]` = parallel (different files). Backend root `backend/src/main/java/com/hls`, tests `backend/src/test/java/com/hls`, web `frontend/src`. This list includes amendment tasks for 005, 008, 010, 013a, 013b, 017a and identity; none is done yet.

## Phase 1: Setup

- [ ] T001 Re-check the highest Flyway migration on `origin/main` and open branches; take the next free decade slots (V160, V161 if V150 is still highest) and update plan.md and data-model.md
- [ ] T002 [P] Verify in code the items of research.md R11 (013b line working and supplementary eligibility; designation on a date in bulk; Active Substitute filter in `teacher.api`; count of existing credits held by Teachers) and record the findings in research.md
- [ ] T003 [P] Create package skeleton `substitution/payout/{domain,service,listener,job,web}` and add it to the module boundary test (`ModulithBoundaryTest` or equivalent), asserting `payroll` has no dependency on `substitution`

## Phase 2: Foundational (blocks all stories)

- [ ] T004 Migration `V160__create_substitute_days.sql`: `substitute_day`, `substitute_month_lock`, `substitute_day_event` exactly as data-model.md (unique `(requirement_id, substitute_teacher_id)`, `day_value numeric(3,2)`, `attendance varchar(12)` in NOT_MARKED/PRESENT/ABSENT, `reopen_reason varchar(500)`, check that a mark has `marked_by` and `marked_at`, append-only trigger on events)
- [ ] T005 [P] Migration test `substitution/payout/PayoutMigrationTest.java` (unique key, check constraint, trigger refuses update and delete of events)
- [ ] T006 Migration `V161__substitute_engagement_and_day_rates.sql`: `teacher.engagement varchar(10) NOT NULL DEFAULT 'REGULAR'` check in ('REGULAR','SUBSTITUTE') and `designation_day_rate` (`amount numeric(10,2) CHECK > 0`, `seq` identity, append-only trigger)
- [ ] T007 [P] Migration test for V161 (default REGULAR, rate check, trigger) in `payroll/DesignationDayRateMigrationTest.java`
- [ ] T008 Amendment 005: `engagement` on the Teacher entity, DTOs, create/update/read in `teacher/`; setting SUBSTITUTE clears `user_id`; audit the change; Admin and Director only
- [ ] T009 [P] Amendment identity: refuse OTP/sign-in and account creation for a Substitute-type Teacher; no Teacher role grant; tests in `identity/`
- [ ] T010 [P] Teacher form and list in `frontend/src/features/teachers/`: engagement field (Admin, Director), badge, no account fields for a Substitute; Vitest
- [ ] T011 Amendment 013a: `payroll.api.DayRates` and impl, `designation_day_rate` repository, endpoints `GET/POST /api/salary-structures/designation-day-rates` (Admin, Director; amount > 0; effective date), audit entity `DESIGNATION_DAY_RATE`, history; tests per role
- [ ] T012 [P] Web: Salary Structures → Designation day rates screen in `frontend/src/features/salary-structures/` (list, add, history, DD/MM/YYYY, Rupee grouping); Vitest
- [ ] T013 Amendment 008: exclude Substitute-type Teachers from the grid, rollups, self-marking, lock blockers and missed-mark lists via `teacher.api`; tests that lock is unaffected by substitutes
- [ ] T014 Amendment 017a: availability offers and accepts only Active Substitute-type Teachers (server re-check); tests in `substitution/internal/AvailabilityServiceTest`
- [ ] T015 Amendment 010/017a: send "substitute assigned/ended" to the School's Zone Manager (else Admin and Director) instead of the substitute; remove the Teacher "My Substitutions" page and menu item (or never show them for a Substitute); tests
- [ ] T016 Permission module `SUBSTITUTE_PAYOUTS(VIEW, EDIT, APPROVE, EXPORT)`: Admin and Director all four; Manager VIEW and EDIT; `PermissionEligibility` excludes Teacher and System; navigation item PAYROLL & FINANCE "Substitute Payouts" (VIEW); access-model tests

## Phase 3: User Story 2 - Zone Manager records day attendance (P1)

**Goal**: a day record per allocation, marked Present/Absent by the Zone Manager, locked per substitute and month. **Independent test**: quickstart steps 3 and 4.

- [ ] T017 [P] [US2] Domain `SubstituteDay`, `DayStatus` (14 values of data-model.md), `DayEvent`, `MonthLock` and repositories in `substitution/payout/domain/`
- [ ] T018 [US2] `DayService` in `substitution/payout/service/`: create the day on `SubstitutionAssigned` (read the live credit through `SubstituteCredits` for day value and absent Teacher), cancel on `SubstitutionEnded`, upsert idempotently, write events and `ChangeRecorder` audit
- [ ] T019 [US2] Listeners `AssignedListener` and `EndedListener` in `substitution/payout/listener/`
- [ ] T020 [US2] Marking: `PUT /days/{id}/attendance` (Edit; refuse future date, locked month, cancelled day, stale `version`), Zone scope through `ScopeQueries`, 404 out of scope; no substitute self-marking path exists
- [ ] T021 [US2] Lock and reopen: `POST /months/{teacherId}/{yyyy-MM}/lock`, `/reopen` (Approve; month ended; no live NOT_MARKED day, `409` lists them; reopen reason max 500), bulk `POST /months/lock?month=`; audit
- [ ] T022 [P] [US2] API and service tests: marking by Admin, Director, Zone A, Zone B (404), Teacher and System; future date; lock refusals; reopen; history (`DayApiTest`)
- [ ] T023 [US2] Web: mark Present/Absent in the 017a Substitutions requirement screen and a day list "Substitute attendance" (`frontend/src/features/substitutions/`), lock and reopen actions for Admin and Director; Vitest and keyboard check

## Phase 4: User Story 1 - Paid for the month through the run (P1) MVP

**Goal**: one line per substitute and month, days times designation rate, no salary. **Independent test**: quickstart steps 5.

- [ ] T024 [US1] Payroll ports `DayRateWork`, `DayRatePay` and `CorrectionRaising` in `payroll/api/` per contracts/payroll-substitute-work-port.md (with `DayRates` from T011)
- [ ] T025 [US1] `DayRateWorkImpl` in `substitution/payout/service/`: `presentDays` (Present, live, month locked, Active Substitute), `notReady`, `unpaidPresentDays`, fixed query count
- [ ] T026 [US1] Amendment 013b: Substitute payroll person source in `payroll/run/internal/`: line earned = sum of day value x rate on each date (designation on the date from `designation.api`, rate from `DayRates`), rounded by the run, deduction 0, no salary row; no line for no Present day; "cannot pay" with reason for no designation or rate; "not ready" for unlocked months
- [ ] T027 [US1] Amendment 013b: frozen working lists each day (day id, School, date, day value, rate, amount); run recompute drops cancelled days; approved run immutable (SC-003)
- [ ] T028 [US1] Amendment 013b: payslip and CSV rows "N days x ₹R" (one row per rate) in `PayslipRenderer`; keep manual `SUBSTITUTE_CREDIT` refused with the text "substitutes are paid by day rate, spec 017b"
- [ ] T029 [P] [US1] Payroll unit tests: 2.5 days x ₹800, rate change mid-month, half day, no Present day no line, no rate cannot pay, rounding
- [ ] T030 [P] [US1] Integration test `SubstitutePayFlowIT`: allocate, mark, lock, compute, submit, approve; each day PAID; recompute gives one line; regression run of 013b, 015, 030 (a run with no substitute unchanged)
- [ ] T031 [US1] `DayRatePayImpl` in payroll and `RunListener` in `substitution/payout/listener/` for `PayrollRunApproved`/`PayrollRunReturned`: set run, month, `paid_amount`, status PAID or back to LOCKED_WAITING_RUN

## Phase 5: User Story 3 - Views, totals, export (P1)

**Goal**: list, totals, substitute month view, CSV. **Independent test**: quickstart step 8.

- [ ] T032 [P] [US3] API tests per role and scope for days, summary, substitute view, export (`PayoutApiTest`)
- [ ] T033 [US3] `PayoutQueryService` in `substitution/payout/service/`: filters (month or range, substitute, School, Zone, status), paging, sorting, totals over the whole filter, batch lookups
- [ ] T034 [US3] `PayoutController`: `GET /days`, `/substitutes/{id}`, `/summary`, `/days/{id}` with `actions[]`
- [ ] T035 [US3] `GET /export` CSV with formula neutralising, DD/MM/YYYY dates, numeric Rupee amounts; test rows equal the list (SC-007)
- [ ] T036 [P] [US3] Web: `frontend/src/features/substitute-payouts/` list, filters, status chips, totals footer, substitute month view, detail with history; Indian grouping; route guard
- [ ] T037 [US3] Web tests (Vitest): list, filters, empty/error states, keyboard use

## Phase 6: User Story 4 - Mismatches, cancellation after payment, Corrections (P1)

**Goal**: flagged days and the Admin Correction. **Independent test**: quickstart step 6.

- [ ] T038 [US4] `DayStatusDeriver` implementing the table in data-model.md, with `DayStatusDeriverTest` covering every row
- [ ] T039 [P] [US4] Integration tests: cancel/absent while draft (recompute drops), while awaiting approval (waits; returned vs approved), after approval (CORRECTION_NEEDED, run unchanged)
- [ ] T040 [US4] Mismatch rules and `GET /mismatches` (cannot pay, changed, correction needed, differing amount, back-pay waiting) with `reason` and `nextStep`
- [ ] T041 [US4] `POST /days/{id}/correction` (Admin): validate state, `amount`, `reason` (max 500), `409` if already raised, call `CorrectionRaising` with `SUBSTITUTE-CORRECTION:<dayId>`; CORRECTION_RAISED then CORRECTED on the applied event; audit; test
- [ ] T042 [P] [US4] Web: Mismatches tab and "Raise correction" dialog (prefilled, editable, Admin only) with Vitest

## Phase 7: User Story 6 - Back-pay of everything on record (P1)

**Goal**: all recorded engaged days paid; closed months by supplementary run. **Independent test**: quickstart step 7.

- [ ] T043 [US6] Amendment 013b: a supplementary run for a month includes Substitutes returned by `DayRateWork.unpaidPresentDays` even if absent from the primary run; test on a month with an approved primary run
- [ ] T044 [P] [US6] Integration test: day marked Present after the primary run is BACKPAY_WAITING then paid by the supplementary run; original month kept
- [ ] T045 [US6] `MigrationService` and `POST /migration/run` (Admin, idempotent): one day record per existing live credit, attendance PRESENT, `source = MIGRATED`, `needs_review = true`; credits of non-Substitute Teachers listed "settle first" and not created payable; returns counts
- [ ] T046 [P] [US6] Tests: migration idempotent, no double count, settle-first list, bulk lock of migrated months; web screen for the settle-first list and review flag in `features/substitute-payouts/`

## Phase 8: User Story 5 - Repair job and audit (P2)

**Independent test**: quickstart step 9.

- [ ] T047 [P] [US5] Tests: missing day record recreated once; ended allocation not markable; run data missed by event repaired; second run no change; concurrent runs no duplicate
- [ ] T048 [US5] `PayoutReconcileJob` (daily, virtual thread, advisory lock) comparing live credits, day records and `DayRatePay`, via the same services; `POST /reconcile?month=`; each repair an event with cause JOB
- [ ] T049 [US5] Audit entity types and Change History views for days, locks, rates and engagement; test the Director sees day, old and new value, actor or "reconcile job"

## Phase 9: Polish and cross-cutting

- [ ] T050 Constitution PATCH 2.5.17 (renumber at merge): Substitute Payouts row, navigation item, Teachers note, Sync Impact entry in `.specify/memory/constitution.md`
- [ ] T051 Update `docs/spec-roadmap.md` rows 013a, 013b, 017 and 005 with the amendments; mark 017b implemented
- [ ] T052 [P] Postman requests for payouts, day rates and the engagement field in the repo's Postman collection
- [ ] T053 [P] Dev seed: a Substitute designation with a rate, two Substitutes, days in each status
- [ ] T054 Selenium end-to-end walkthrough of quickstart steps 1 to 9 as an e2e test; results in `specs/017b-payout-reconciliation/quickstart-results.md`
- [ ] T055 Accessibility, dark mode and phone-width pass of the new screens (quickstart step 11)
- [ ] T056 Full backend and web test runs; fix regressions in 005, 008, 013a, 013b, 015, 017a, 030

## Requirement coverage

FR-001 T006, T008. FR-002 T009. FR-003 T014. FR-004 T015. FR-005/006 T011, T026. FR-007 T018. FR-008 T020. FR-009 T021. FR-010 T018, T019. FR-011/012 T026 to T028. FR-013 T026. FR-014 T043. FR-015 T045, T043. FR-016 T018 (no record for Manager cover). FR-017 T038. FR-018 T033. FR-019 T040. FR-020 T041. FR-021 T048. FR-022 T034 to T037. FR-023 T016, T020, T021, T041. FR-024 T049. FR-025 T003, T024. SC-001 T030, T047; SC-002 T033, T032; SC-003 T027, T039; SC-004 T039; SC-006 T022, T009; SC-007 T035; SC-008 T028; SC-009 T045, T046.

## Dependencies and order

- Phase 1, then Phase 2 (T004 before T017; T006 before T008 and T011; T011 before T026; T014 and T016 before Phase 3).
- US2 (T017 to T023) before US1 (needs marked, locked days). T024 and T025 before T026; T026 before T027 and T028; T031 after T030's flow exists.
- US3 needs US2 records; US4 needs US1; US6 needs US1 and US4's deriver; US5 needs all service methods.
- Polish last.

## Parallel examples

- After T004 and T006: T005, T007, T008, T009, T011 together. After T034: T036 and T032 together. After T031: T039 and T044 together.

## Implementation strategy

MVP: Phases 1 and 2, US2 (attendance) and US1 (pay), which already pays substitutes by the day. Then US3 (visibility), US4 (mismatches and Corrections), US6 (back-pay), US5 (repair). Counts: 56 tasks; Setup 3, Foundational 13, US2 7, US1 8, US3 6, US4 5, US6 4, US5 3, Polish 7.
