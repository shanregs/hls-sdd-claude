# Tasks: Substitute Payout Reconciliation

**Input**: `specs/017b-payout-reconciliation/` (spec.md, plan.md, research.md, data-model.md, contracts/, quickstart.md)

**Status**: Not started. Written on the recommended defaults (clarification-questions.md, all option A). If the owner answers differently, revisit T006, T007, T012, T018, T020 first.

Format: `- [ ] T### [P] [US#] description with path`. `[P]` = can run in parallel (different files). Backend root `backend/src/main/java/com/hls`, tests `backend/src/test/java/com/hls`, web `frontend/src`.

## Phase 1: Setup

- [ ] T001 Re-check the highest Flyway migration on `origin/main` and open branches; use the next free decade slot (V160 if V150 is still highest) for `backend/src/main/resources/db/migration/V16x__create_substitute_payouts.sql` and update plan.md, data-model.md
- [ ] T002 [P] Verify in code the three open items of research.md R10 (PayRules value for an unplaced Teacher; existing month-lock read; payroll Correction created from another module) and record the findings in research.md
- [ ] T003 [P] Create package skeleton `substitution/payout/{domain,service,listener,job,web}` and add it to the module boundary tests in `backend/src/test/java/com/hls/ModulithBoundaryTest.java` (or the repo's equivalent)

## Phase 2: Foundational (blocks all stories)

- [ ] T004 Write migration `V16x__create_substitute_payouts.sql`: tables `substitute_payout` and `substitute_payout_event` exactly as data-model.md (unique `(requirement_id, substitute_teacher_id)`, `day_value numeric(3,2)`, `amount numeric(12,2)`, `mismatch_reason varchar(200)`, `source_ref varchar(100) NOT NULL`, append-only trigger on events, indexes)
- [ ] T005 [P] Migration test `backend/src/test/java/com/hls/substitution/payout/PayoutMigrationTest.java` (unique key, trigger refuses update and delete of events)
- [ ] T006 Add `SettingKey.SUBSTITUTE_PAYOUT_START_MONTH` (`substitution.payout-start-month`, SYSTEM group, first-of-month date, default the migration month) in `settings` and its tests
- [ ] T007 Payroll amendment B: add `payroll.api.SubstituteCreditIntake` per contracts/payroll-substitute-credit-intake.md and `SubstituteCreditIntakeImpl` in `payroll/run/internal/`, reusing the status derivation of `ReimbursementIntakeImpl`/`IncentiveIntakeImpl`; keep `AdjustmentService` refusing manual `SUBSTITUTE_CREDIT` with reason text "arrives through spec 017b"
- [ ] T008 [P] Payroll tests for the intake: offer idempotent, replace unapplied amount, withdraw false when awaiting approval or applied, draft takes and releases, approval publishes `PayrollAdjustmentApplied` with kind `SUBSTITUTE_CREDIT` in `backend/src/test/java/com/hls/payroll/run/SubstituteCreditIntakeTest.java`; regression run of 013b, 015 and 030 intake tests
- [ ] T009 Payroll month-lock read and Admin Correction raising (`payroll.api.MonthLock`, `payroll.api.CorrectionRaising`) if T002 shows they are missing, with tests
- [ ] T010 Domain: `SubstitutePayout` entity, `PayoutStatus` enum (12 values of data-model.md), `PayoutEvent` entity and repositories in `substitution/payout/domain/`
- [ ] T011 Permission module `SUBSTITUTE_PAYOUTS(VIEW, EDIT, EXPORT)` seeded Admin VIEW,EDIT,EXPORT; Director VIEW,EXPORT; Manager VIEW; `PermissionEligibility` excludes Teacher and System; navigation item PAYROLL & FINANCE "Substitute Payouts" (VIEW); access-model tests in `backend/src/test/java/com/hls/identity/`
- [ ] T012 `PayoutPricing` in `substitution/payout/service/`: day value from `PayRules.lossOfPayValueOn` times credit day value, rounded with `PayPolicyView`; returns amount or the reason (never zero offered)
- [ ] T013 `PayoutStatusDeriver` implementing the table of data-model.md with unit tests `PayoutStatusDeriverTest` covering every row

## Phase 3: User Story 1 - A substitute day is paid in the next pay run (P1) MVP

**Goal**: a live credit in a locked month on or after the start month becomes one waiting adjustment, is taken by the draft run, and becomes "Paid in run" on approval.
**Independent test**: quickstart steps 1 to 3.

- [ ] T014 [P] [US1] Integration test: assign, lock month, reconcile, compute run, approve, status PAID with run month and amount (`SubstitutePayoutPayFlowIT`)
- [ ] T015 [P] [US1] Integration test: 0.50 day amount, repeat reconcile and recompute give one adjustment, month not locked gives WAITING_LOCK, before start month gives BEFORE_START
- [ ] T016 [US1] `PayoutService.syncCredit(requirementId, substituteId)` in `substitution/payout/service/PayoutService.java`: read the live credit through `SubstituteCredits`, upsert the record (lock row), derive and store the status, offer through `SubstituteCreditIntake` with `sourceRef`, write event and audit via `ChangeRecorder`
- [ ] T017 [US1] Listener `AssignedListener` for `SubstitutionAssigned` calling `syncCredit` (idempotent) in `substitution/payout/listener/`
- [ ] T018 [US1] Listener `AdjustmentAppliedListener` for `PayrollAdjustmentApplied` kind `SUBSTITUTE_CREDIT`: store run, month, `paid_amount`, status PAID
- [ ] T019 [US1] Pay statement label: show School, `DD/MM/YYYY` and day value on payslip and run line from the adjustment reason (check `PayslipRenderer` label) with a test

## Phase 4: User Story 2 - Admin and Director see every credit (P1)

**Goal**: list, totals, filters, detail, CSV.
**Independent test**: quickstart steps 7 and 8.

- [ ] T020 [P] [US2] API tests per role and scope (Admin, Director, Zone A and B Manager, Teacher, System) for list, summary, detail, export in `PayoutApiTest`
- [ ] T021 [US2] `PayoutQueryService` with filters (month or range, Teacher, School, Zone, status), paging, sorting, totals over the whole filter and batch name lookups (no per-row queries) in `substitution/payout/service/`
- [ ] T022 [US2] `PayoutController` endpoints `GET /`, `/summary`, `/{id}` per contracts/payout-api.md with `ScopeQueries` Zone scope, 404 out of scope, server-sent `actions`
- [ ] T023 [US2] CSV export (`GET /export`) with formula neutralising, `DD/MM/YYYY` dates, numeric Rupee amounts, same filters; test that rows equal the list (SC-007)
- [ ] T024 [P] [US2] Web: `frontend/src/features/substitute-payouts/` list page with filters, status chips, totals footer, Indian grouping, `DD/MM/YYYY`; route guard and menu item from the server model
- [ ] T025 [P] [US2] Web: detail drawer with status history, run, adjustment and correction link
- [ ] T026 [US2] Web tests (Vitest) for list, filters, empty/error states, keyboard use; accessibility check

## Phase 5: User Story 3 - Mismatches and corrections (P1)

**Goal**: voids handled by state, mismatch list, Admin correction.
**Independent test**: quickstart steps 4 to 6.

- [ ] T027 [P] [US3] Integration tests: void while waiting or in draft run (withdraw, VOIDED_BEFORE_PAYMENT); void while awaiting approval (VOIDED_RUN_AWAITING then after approval VOIDED_AFTER_PAYMENT, or back to before payment on return); approved run never changed (SC-003)
- [ ] T028 [P] [US3] Integration test: cannot price (no salary), then salary added and reconcile offers it; substitute left organization listed as not on pay run
- [ ] T029 [US3] `EndedListener` for `SubstitutionEnded` calling `PayoutService.voidCredit` (withdraw offer; keep waiting if false; flag after approval) in `substitution/payout/listener/`
- [ ] T030 [US3] Mismatch rules (cannot price, not on pay run, voided after payment, amount differs from current pricing, offer missing past a run that included the substitute) and `GET /mismatches` with `reason` and `nextStep`
- [ ] T031 [US3] `POST /{id}/correction` (Edit, Admin only): validate state, `amount` and `reason` (max 500), `409` if already raised, call `CorrectionRaising`, status CORRECTION_RAISED, then CORRECTED on the applied event; audit; test
- [ ] T032 [P] [US3] Web: Mismatches tab and "Raise correction" dialog (prefilled, editable, Admin only)
- [ ] T033 [US3] Web tests for mismatches tab and correction dialog

## Phase 6: User Story 4 - Teacher sees own substitute pay (P2)

**Independent test**: quickstart step 7 (Teacher).

- [ ] T034 [P] [US4] API test: `/mine` returns only own, no absent Teacher name, no amount before PAID, 404 for other ids
- [ ] T035 [US4] `GET /mine` endpoint and mapping to WAITING, IN_NEXT_PAYSLIP, PAID
- [ ] T036 [US4] Web: pay status column in the 017a My Substitutions page (`frontend/src/features/substitutions/`) and test

## Phase 7: User Story 5 - Repair job and audit (P2)

**Independent test**: quickstart step 9.

- [ ] T037 [P] [US5] Tests: missing offer restored once; stale adjustment withdrawn; second run changes nothing; two concurrent runs no duplicate
- [ ] T038 [US5] `PayoutReconcileJob` (daily, virtual thread, advisory lock) re-reading live credits through `SubstituteCredits` and all records, calling the same `PayoutService` methods; `POST /reconcile` (Edit) for a month; records each repair as an event with cause JOB
- [ ] T039 [US5] Audit entity types and Change History views for payouts; test that the Director sees credit, old and new status, amount, actor or "reconcile job"

## Phase 8: Polish and cross-cutting

- [ ] T040 Constitution PATCH 2.5.17 (renumber at merge): Default role access matrix row Substitute Payouts, navigation item, Sync Impact note in `.specify/memory/constitution.md`
- [ ] T041 Update `docs/spec-roadmap.md` row 017 to implemented, and the 013b row with "Amendment B (spec 017b)"
- [ ] T042 [P] Postman requests for the endpoints in the repo's Postman collection
- [ ] T043 [P] Seed demo data for the walkthrough (credits in each status) in the dev seed
- [ ] T044 Selenium end-to-end walkthrough of quickstart steps 1 to 9 as an e2e test, results into `specs/017b-payout-reconciliation/quickstart-results.md`
- [ ] T045 Accessibility, dark mode and phone-width pass of the new screens; quickstart step 11
- [ ] T046 Full backend and web test runs; fix regressions in 013b, 015, 030, 017a

## Requirement coverage (from the analysis pass)

FR-001/002/003: T012, T016, T015. FR-004/005: T007, T008. FR-006: T019. FR-007/008/009: T013, T016, T029, T027. FR-010: T021. FR-011: T030. FR-012/013: T031. FR-014/015: T021 to T026. FR-016/017: T011, T020, T022. FR-018/019/020: T038, T039, T037. FR-021: T003. SC-001: T014, T015, T037; SC-002: T021, T020; SC-003: T027; SC-004: T029; SC-006: T020, T034; SC-007: T023; SC-008: T019.

## Dependencies and order

- T024 and T025 (web) need the endpoints T021 and T022; T036 needs T035.
- Phase 1 then Phase 2 (T007 to T009 payroll before T016). T004 before T010. T012 and T013 before T016.
- US1 (T014 to T019) is the MVP and is needed by all others. US2 needs US1 records. US3 needs US1 and US2 list endpoints. US4 and US5 need only US1.
- Polish last.

## Parallel examples

- After T004: T005, T006, T010, T011 together. After T016: T024, T025 and T034 together. In US3: T027 and T028 together.

## Implementation strategy

MVP: Phases 1, 2 and US1, which already pays substitutes. Then US2 (visibility), US3 (voids and corrections), US4, US5. Counts: 46 tasks; Setup 3, Foundational 10, US1 6, US2 7, US3 7, US4 3, US5 3, Polish 7.
