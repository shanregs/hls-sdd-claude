# Tasks: Manager Incentives

**Input**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/)
Format: `- [ ] T### [P] [US#] description (file)`. [P] = can run in parallel (different files). Stories: US1 earn, US2 approve, US3 pay, US4 lists and notifications, US5 hold.

## Phase 1: Setup

- [ ] T001 Create module package `com.hls.incentive` with `package-info.java` (Modulith named interface `api`) in `backend/src/main/java/com/hls/incentive/`
- [ ] T002 Re-check migration numbers on `origin/main` (V95 029, V100 031, V110 024, V120 022) and write `V130__create_incentives.sql`: `incentive_program` (seeded `starts_at = now()`), `incentive` (unique `school_id`, indexes), `no_earner` (unique `school_id`), `incentive_event` with append-only trigger (`backend/src/main/resources/db/migration/`)
- [ ] T003 [P] Add `SettingKey.INCENTIVE_AMOUNT` (`incentive.amount-per-school`, default 5000, range 0 to 100000) and its test (`settings/internal/SettingKey.java`)
- [ ] T004 [P] Add `PermissionModule.MANAGER_INCENTIVES(VIEW, APPROVE, EXPORT)`, eligibility (APPROVE not for Admin; none for Teacher and System), seeds (Admin VIEW, EXPORT; Director all; Manager VIEW, APPROVE), `NavigationCatalog` OPERATIONS item "Incentives", `AuditVisibility` mapping for `INCENTIVE` entities, and update the access-model tests

## Phase 2: Foundational - 013b amendment A (blocks US3 and US5)

- [ ] T005 Add `payroll.api.IncentiveIntake` (`offer`, `withdraw`, `status`, `OfferStatus`) per [contracts/payroll-incentive-intake.md](contracts/payroll-incentive-intake.md) (`backend/src/main/java/com/hls/payroll/api/IncentiveIntake.java`)
- [ ] T006 Implement `IncentiveIntakeImpl` in `payroll.run.internal`: offer (idempotent per `sourceRef`, MANAGER only, amount above zero, re-offer after withdrawal), withdraw (false when awaiting approval or applied), status; extract the status derivation shared with `ReimbursementIntakeImpl` without changing 015 behaviour
- [ ] T007 In `AdjustmentService` keep refusing manual `INCENTIVE` but change the reserved text to say it arrives only through the incentive intake; payslip label already exists (verify `PayslipRenderer`)
- [ ] T008 [P] Tests for the intake: offer twice gives one adjustment; draft run takes it; recompute and return keep one line; approval applies and publishes `PayrollAdjustmentApplied` kind INCENTIVE; withdraw refused when awaiting approval or applied; person who cannot be paid keeps it waiting; non-Manager refused (`backend/src/test/java/com/hls/payroll/...`)
- [ ] T009 [P] Regression: the existing 013b and 015 payroll and reimbursement tests still pass unchanged
- [ ] T010 Record amendment A in `specs/013b-payroll-run/` (a short "Amendments" note in `plan.md` or `research.md`) and in `docs/spec-roadmap.md` row 013b/030

## Phase 3: User Story 1 - Earn on MoU approval (P1)

- [ ] T011 [P] [US1] Entities and repositories: `Incentive`, `NoEarnerRecord`, `IncentiveEvent`, `IncentiveProgram` (`incentive/internal/`)
- [ ] T012 [US1] `EarningService`: given a School, read `MouApprovals.stateOn`, require `countsAsApproved`; if `approvedOn` is null record no-earner `APPROVED_BEFORE_APPROVAL_STEP` and stop; if `approvedOn` before `starts_at` record `BEFORE_GO_LIVE`; resolve owner via `ProspectOwners`, require an active payroll Manager (`ManagerQueries.managerByUserId`) else record `NO_PROSPECT`, `NO_OWNER` or `OWNER_NOT_MANAGER`; otherwise insert one incentive (amount from setting, closure date = business date of `approvedOn`, Zone) handling the unique-key conflict as "already earned"; resolve an earlier no-earner row; write event and Change History
- [ ] T013 [US1] Listener for `MouDecided` APPROVED (application-module listener, after commit) calling `EarningService`; publish `IncentiveEarned`
- [ ] T014 [US1] `ReconcileJob` (daily, virtual-thread friendly): re-run `EarningService` for Schools whose MoU counts as approved with non-null `approvedOn` after go-live and no incentive or no-earner row; idempotent
- [ ] T015 [P] [US1] Tests: earn once; duplicate event; re-approval after document change; later MoU; no prospect; no owner; owner is Director; owner left; legacy `APPROVED_BEFORE_APPROVAL_STEP` has no incentive and shows excluded; approval before go-live; amount change affects only new rows; reconcile creates a missed one and creates no duplicate; MoU approval still succeeds when 030 fails

## Phase 4: User Story 2 - Approval (P1)

- [ ] T016 [US2] `DecisionService`: approve and reject (row lock plus version, 409 naming the first decider), Director or Zone Manager of the School's Zone (`ManagerQueries.managerOfSchool`), self-approval allowed and flagged `approved_by_earner`, reject reason required (at most 500), Admin/Teacher/System refused, no Zone Manager means Director only; events and Change History naming earner and approver; publish `IncentiveDecided`
- [ ] T017 [US2] `IncentiveController` endpoints approve, reject per [contracts/incentive-api.md](contracts/incentive-api.md), with scope checks and 404 outside scope
- [ ] T018 [P] [US2] Tests per role and scope (SC-002, SC-004): Director, Zone Manager in and out of Zone, earner-as-Zone-Manager approves own (flag and audit), Admin refused, concurrent decisions record one, missing reason refused, Zone Manager changed while pending

## Phase 5: User Story 3 - Payment through payroll (P1)

- [ ] T019 [US3] `PayrollLink`: on approve call `IncentiveIntake.offer` (`sourceRef = INCENTIVE:<id>`, amount, reason "Manager incentive: closed School <name>") in the same transaction as the decision; a refusal keeps status APPROVED with a visible payroll status text and is retried by the reconcile job
- [ ] T020 [US3] Listener for `PayrollAdjustmentApplied` kind INCENTIVE: set `PAID_IN_RUN` with `run_id` and `run_month`, write event; idempotent
- [ ] T021 [US3] Show payroll status (`IncentiveIntake.status`) on detail and list
- [ ] T022 [P] [US3] Integration tests (SC-003, SC-007): approved incentive appears once in the next run with the Manager, counts in net pay once, applied at run approval; return and recompute keep one line; Manager who cannot be paid leaves it waiting and a later run takes it; run approved before the feature unchanged; Teachers' and other Managers' pay identical

## Phase 6: User Story 4 - Lists, notifications, dashboard (P2)

- [ ] T023 [P] [US4] `IncentiveQueries`: list with filters (status, Manager, Zone, month, q), sort, batch name lookups, scope (Admin and Director all; Zone Manager own Zone; Manager own), `actions` per row, `mine`, `summary` with totals per Manager and month
- [ ] T024 [P] [US4] `GET /no-earner` (Admin, Director) and `GET /export.csv` (EXPORT, formula-safe cells)
- [ ] T025 [P] [US4] Notification types `INCENTIVE_PENDING`, `INCENTIVE_DECIDED` and `IncentiveEventListener` in `notification` (recipients per [contracts/module-interfaces.md](contracts/module-interfaces.md)); tests
- [ ] T026 [P] [US4] `incentive.api.IncentiveSummary` implementation and the 028 tile plus Director approvals-inbox entry via the `reporting` SPI; tests
- [ ] T027 [US4] Frontend `features/incentives`: list with filters and totals, detail drawer with history and payroll status, approve and reject dialogs (reason), My Incentives, No-earner tab, CSV export, route and menu entry; loading, empty and error states; DD/MM/YYYY and Rupee format
- [ ] T028 [P] [US4] Frontend tests (Vitest) for list, filters, approve and reject flow, role-based buttons, empty states; accessibility tests (WCAG 2.2 AA, keyboard, phone width, dark mode)
- [ ] T029 [P] [US4] Scope tests for every endpoint including export and direct links (SC-004)

## Phase 7: User Story 5 - Hold when the MoU stops counting (P3)

- [ ] T030 [US5] `HoldService`: on `MouSubmitted` (any resubmit or reset) or `MouDecided` REJECTED for a School with an APPROVED incentive call `IncentiveIntake.withdraw`; true gives `HELD` (reason "MoU not approved"); false because the run awaits approval sets `mou_changed = true` and notifies the Director; false because applied changes nothing
- [ ] T031 [US5] On `MouDecided` APPROVED for a HELD incentive offer again (same source reference) and return to APPROVED; clear `mou_changed` when the MoU is approved again
- [ ] T032 [US5] Reconcile job also re-checks both directions (approved incentive with MoU not counting; held incentive whose MoU counts again)
- [ ] T033 [P] [US5] Tests: hold then run does not take it; re-approve and next run takes it; run awaiting approval keeps it with the flag and notice; already paid never changes; no second incentive is ever created

## Phase 8: Polish and documentation

- [ ] T034 [P] Demo data: a won prospect owned by a Manager, one School in each no-earner reason, incentives in each status (`NotificationDevSeeder`-style dev seeder)
- [ ] T035 [P] Postman requests for the incentive API (`postman/`)
- [ ] T036 Constitution PATCH 2.5.15: Sync Impact Report, Default role access matrix row "Incentives", OPERATIONS navigation (`.specify/memory/constitution.md`); renumber if another spec merges first
- [ ] T037 Update `docs/spec-roadmap.md` rows 030, 013b and 028 (status, amendment A, tile) and `docs/spec-inputs/030-manager-incentives.md` if decisions changed
- [ ] T038 Director reopen of a rejected incentive (`POST /{id}/reopen`, reason required, Director only, audited) so the rule "one incentive per School" still allows a corrected decision; tests
- [ ] T039 Run `quickstart.md` sections 1 to 9 and record results in `quickstart-results.md`; run the full backend and frontend test suites and the module-boundary (Modulith) verification

## Dependencies

- T001 to T004 first. T005 to T010 (amendment A) before US3 and US5; US1 and US2 can proceed in parallel with them.
- US1 (T011 to T015) before US2; US2 before US3; US3 before US5. US4 needs US1 and US2 (lists) and US3 (payroll status).
- T038 after T016. T036 and T037 last, with T039.

## MVP

US1, US2 and US3 with amendment A (T001 to T022) pay a Manager through payroll; US4 and US5 follow.
