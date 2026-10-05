---

description: "Task list for feature implementation"
---

# Tasks: School Contracts and Billing

**Input**: Design documents from `/specs/012-school-contracts-billing/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/schoolbilling-api.md,
quickstart.md, and **specs 001-005, 008, 009 and 010 implemented and merged** (this feature adds permission
modules and navigation to 002's matrix and catalog, takes over the Teacher placement of 005, reads attendance
(008), and notifies through 010).

**Tests**: included as first-class tasks. Constitution Principle IX requires calculation logic to be unit
tested before integration, per-role and per-scope tests on every endpoint, and UI tests per role. The spec's
invariants (append-only payments, derived balances, one overdue notification, the placement migration) are only
trustworthy with tests. Calculation and balance unit tests come before the services that use them.

**Organization**: grouped by user story in priority order (spec.md US1-US6). US2 (moving the placement) comes
before US1 (contracts and screens) because the assignment rows and the `TeacherPlacementSource` interface are
what US1's assignment rules sit on, and because it is the riskiest change: it is proven before anything is
built on it.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1-US6; absent for Setup/Foundational/Polish

## Path Conventions

Backend `backend/src/main/java/com/hls/{schoolbilling,teacher,attendance,notification,identity}/...`, tests
under `backend/src/test/java/com/hls/...`; migrations `backend/src/main/resources/db/migration`; frontend
`frontend/src/features/{schoolbilling,teachers}/...`. Run one Maven test class at a time
(`mvn -o test -Dtest=...`); the machine is slow.

---

## Phase 1: Setup

- [ ] T001 Create the `schoolbilling` module skeleton: `backend/src/main/java/com/hls/schoolbilling/{api,internal,web}/` with `package-info.java` (the `api` package a `@NamedInterface`) and `backend/src/test/java/com/hls/schoolbilling/SchoolBillingModuleRulesTest.java` (ArchUnit: nothing outside `schoolbilling` uses `schoolbilling.internal`; `teacher` does not depend on `schoolbilling`; no module reads billing tables)
- [ ] T002 Create `frontend/src/features/schoolbilling/` with `schoolBillingApi.ts` (types and calls for every endpoint in contracts/schoolbilling-api.md; money as strings, months as `YYYY-MM`)

---

## Phase 2: Foundational (blocking prerequisites)

- [ ] T003 Write `backend/src/main/resources/db/migration/V20__create_schoolbilling_tables.sql` per data-model.md: tables `contract` (id uuid PK, school_id uuid not null, state varchar(12) check in ('RATE_PENDING','ACTIVE','CANCELLED'), basis varchar(12) null check in ('PER_TEACHER','LUMP_SUM'), rate numeric(12,2) null, lump_sum numeric(12,2) null, covered_teachers int null, cycle varchar(10) not null default 'MONTHLY' check = 'MONTHLY', starts_on date not null, ends_on date null, version bigint not null, created_by, created_at; checks: PER_TEACHER requires rate > 0 and null lump_sum and covered_teachers, LUMP_SUM requires lump_sum > 0 and covered_teachers >= 1 and null rate, RATE_PENDING has basis, rate, lump_sum and covered_teachers all null; ends_on null or >= starts_on; gist exclusion: no two non-CANCELLED contracts of one school overlap in dates), `contract_assignment` (id uuid PK, teacher_id, school_id, starts_on not null, ends_on null, status varchar(12) in ACTIVE/CANCELLED/CORRECTED, created_by, created_at; `ends_on >= starts_on`; the same gist exclusion as `teacher_placement` so one Teacher has no overlapping ACTIVE rows; indexes on school_id and teacher_id; no foreign keys to other modules), `billing_month` (month date PK, status varchar(8) in OPEN/CLOSED, closed_by, closed_at), `receivable` (id, school_id, month date, kind varchar(10) in GENERATED/ADJUSTMENT, version int, current boolean, amount numeric(12,2) signed, breakdown jsonb, reason text null required for ADJUSTMENT, generated_by, generated_at; partial unique index on (school_id, month) where kind = 'GENERATED' and current), `school_payment` (id, school_id, month date, kind in PAYMENT/REVERSAL, paid_on date, mode varchar(8) in BANK/CASH/CHEQUE/UPI, receiver_user_id, receiver_name varchar(120), amount numeric(12,2) check > 0, comment varchar(500) null, reverses_id uuid null, reason varchar(500) null required for REVERSAL, recorded_by, created_at; unique index on reverses_id; index on (school_id, month); a trigger that raises an error on UPDATE and DELETE), `overdue_flag` (school_id, month, flagged_at, cleared_at null; PK (school_id, month)), `billing_settings` (one row: overdue_days int default 7, expected_payment_day int default 5 check between 1 and 28, first_billable_month date, version). The same migration copies every `teacher_placement` row (all statuses, same ids and values) into `contract_assignment`, inserts one RATE_PENDING contract per School that has placements (starts_on = the earliest placement start, ends_on null), and sets `first_billable_month` to the first day of the month in which it runs
- [ ] T004 [P] Add permission modules `SCHOOL_CONTRACTS(VIEW, CREATE, EDIT)` and `SCHOOL_BILLING(VIEW, CREATE, EDIT, APPROVE, EXPORT)` to `identity/permissions/PermissionModule.java`; add both to `PermissionEligibility` so Teacher and System are excluded; seed defaults in `PermissionMatrixService`/`PermissionMatrixSeeder` per the spec's table (Admin: all actions on both; Director: SCHOOL_CONTRACTS VIEW, CREATE, EDIT and SCHOOL_BILLING VIEW, CREATE, EDIT, EXPORT; Manager: SCHOOL_CONTRACTS VIEW and SCHOOL_BILLING VIEW, CREATE, EDIT; Teacher and System none)
- [ ] T005 [P] Add navigation `OPERATIONS -> School Billing` (`/operations/school-billing`, SCHOOL_BILLING VIEW) to `identity/accessmodel/NavigationCatalog.java` and the data-scope entry `ASSIGNED` (Manager) / org-wide (Admin, Director) in `AccessModelService.java`
- [ ] T006 [P] Extend `PermissionEligibilityTest`, `NavigationSectionOrderTest` and the access-model tests under `backend/src/test/java/com/hls/identity/` for both modules (Teacher and System excluded) and the OPERATIONS item per role
- [ ] T007 [P] Add `backend/src/main/java/com/hls/teacher/api/TeacherPlacementSource.java` per contracts/schoolbilling-api.md (`spansOverlapping`, `teachersAssignedDuring`, `hasCurrentOrFutureAssignment`, `teacherCountsBySchool`, `assign`, `cancelPending`, `endForExit`), reusing `TeacherDirectory.PlacementSpan`
- [ ] T008 Entities and repositories in `schoolbilling/internal`: `Contract`, `ContractAssignment` (same shape and `isInEffectOn` rule as `TeacherPlacement`), `AssignmentStatus`, `BillingMonth`, `Receivable`, `SchoolPayment` (no setters, no update or delete methods), `OverdueFlag`, `BillingSettings`, each with its Spring Data repository; Hibernate only validates the schema

**Checkpoint**: migration applies on a database holding old-shaped placements; permissions and navigation seeded; entities validate against the schema.

---

## Phase 3: User Story 2 - Replace the Interim Placement (P1) 🎯 MVP

**Goal**: "which School is this Teacher in" comes from contract assignments, with the same current School, dates and history as before, and every "interim" label gone.

**Independent Test**: load old-shaped placements (current, scheduled, ended, cancelled, corrected), run `V20`, and compare Teacher and School screens and `TeacherDirectory` answers before and after: identical; the old table is gone after `V21`.

### Tests for User Story 2

- [ ] T009 [US2] `backend/src/test/java/com/hls/schoolbilling/PlacementMigrationTest.java`: build a database at version 19, insert placements of every status for several Teachers and Schools (one School with none), migrate to 20, and assert row count, ids, dates and status equal, one RATE_PENDING contract per School that had placements starting on its earliest placement date and none for the other, and `first_billable_month` set
- [ ] T010 [P] [US2] Move `backend/src/test/java/com/hls/teacher/` placement tests (those exercising `TeacherPlacementService`, the placement endpoints and `TeacherDirectory`) so they run against the new implementation without changing their assertions

### Implementation for User Story 2

- [ ] T011 [US2] `schoolbilling/internal/AssignmentService.java`: move the rules of `TeacherPlacementService` unchanged (`place` as `assign`, `cancelPending`, `endForExit`; dated rows, a future date schedules, "current" evaluated against today, rows never overwritten, `ACTIVE`/`CANCELLED`/`CORRECTED`, overlap refused with the same messages), plus the new rule: the School must have a contract (any state) covering the start date, otherwise refuse with "Create the contract for this School first."; audit through `ChangeRecorder` with entity `TEACHER_PLACEMENT` and the Teacher as entity id, as before
- [ ] T012 [US2] `schoolbilling/internal/TeacherPlacementSourceImpl.java` implementing `TeacherPlacementSource` over `ContractAssignmentRepository` (spans, teachers assigned during, current-or-future check, counts per School, and the three operations through `AssignmentService`)
- [ ] T013 [US2] Switch `teacher/internal/{TeacherDirectoryImpl,TeacherSchoolHooks,TeacherService,ManagerTeacherCountEnricher,TeacherScopeService,TeacherDevSeeder}.java` and `teacher/web/TeacherController.java` from `TeacherPlacementRepository`/`TeacherPlacementService` to `TeacherPlacementSource`; the endpoints `POST /api/v1/teachers/{id}/placements` and `DELETE /api/v1/teachers/{id}/placements/pending` keep their paths and bodies
- [ ] T014 [US2] Write `V21__drop_teacher_placement.sql` and delete `teacher/internal/{TeacherPlacement,TeacherPlacementRepository,TeacherPlacementService,PlacementStatus}.java`; update `AttendanceDevSeeder` and any attendance or leave test fixtures that created placements through the old classes
- [ ] T015 [P] [US2] Replace the "interim" wording by "assignment" in `teacher/api/TeacherView.java` (field comments and any label text) and in `frontend/src/features/teachers/{PlacementDialog,TeachersPage,MyTeacherProfile,teachersApi}.ts(x)` and their tests (`TeachersPage.test.tsx`, `MyTeacherProfile.test.tsx`, `frontend/src/account/ProfilePage.teacher.test.tsx`); show "no rate set" next to a School whose contract is RATE_PENDING once US1 provides it
- [ ] T016 [US2] Run `ApplicationModulesTest`, `SchoolBillingModuleRulesTest`, and the existing teacher, attendance and leave suites one class at a time; fix any fixture that still names the old table

**Checkpoint**: the app behaves as before with the new owner; Manager scope on Teachers is unchanged (spec 005 tests pass).

---

## Phase 4: User Story 1 - Record a School's Contract and Its Teachers (P1)

**Goal**: Admin and Director record and replace contracts; Managers assign Teachers for their Schools.

**Independent Test**: create a per-Teacher contract, assign two Teachers, replace the contract with a new rate, and see both contracts in the history with only the new one active; a Manager cannot create one.

### Tests for User Story 1

- [ ] T017 [P] [US1] `backend/src/test/java/com/hls/schoolbilling/ContractServiceTest.java`: per-Teacher and lump-sum creation, both shapes together refused, lump sum needs `covered_teachers >= 1`, new contract ends the old one the day before, overlap refused, a RATE_PENDING contract takes its rate in place, an ACTIVE contract that fed a receivable cannot be edited, a School with no Zone Manager refused, the responsible Manager read from `ManagerQueries` and never stored
- [ ] T018 [P] [US1] `backend/src/test/java/com/hls/schoolbilling/ContractApiTest.java`: per-role and per-scope matrix from contracts/schoolbilling-api.md (Admin and Director create and edit; Manager 403 on create, edit and end, but views own Schools; Manager B gets 404 for Manager A's School; Teacher and System 403)
- [ ] T019 [P] [US1] `backend/src/test/java/com/hls/schoolbilling/AssignmentApiTest.java`: Manager assigns for an own School and is refused for another's; no contract refuses; overlapping dates refused; an exited Teacher refused; the lump-sum "covers N" warning when more than N Teachers are assigned
- [ ] T020 [P] [US1] `frontend/src/features/schoolbilling/SchoolBillingPage.test.tsx`: contract history, create and replace forms (Admin) versus read-only (Manager), assignments list, validation messages, axe in both themes

### Implementation for User Story 1

- [ ] T021 [US1] `schoolbilling/internal/ContractService.java`: create (ends the current contract, one transaction, optimistic `version`), `setRate` for RATE_PENDING only, `end`, cancel before use; reads the School through `school.api.SchoolDirectory` and its Zone Manager through `organization.api.ManagerQueries.managerOfSchool`; audit entity `CONTRACT`; decimal amounts as `BigDecimal` scale 2
- [ ] T022 [US1] `schoolbilling/web/ContractController.java` for `/api/v1/billing/schools/{schoolId}/contracts`, `/contracts/{id}/rate`, `/contracts/{id}/end`, `/settings` (GET and PUT) with `PermissionGuard` on `SCHOOL_CONTRACTS`; scope through `ScopeQueries`; 404 for out-of-scope Schools; settings audited as `BILLING_SETTINGS`
- [ ] T023 [US1] `schoolbilling/web/AssignmentController.java` for `GET /api/v1/billing/schools/{schoolId}/assignments?month=` (the placement POST and DELETE stay in `TeacherController`, now guarded by `SCHOOL_BILLING` CREATE and EDIT as well as the existing Teacher permission); lump-sum warning returned in the response
- [ ] T024 [P] [US1] `frontend/src/features/schoolbilling/SchoolBillingPage.tsx`: School detail with contract history, create and replace forms (shown only with `SCHOOL_CONTRACTS` CREATE/EDIT), "no rate set" state, assignments for a month; route `/operations/school-billing/schools/:schoolId` guarded in `frontend/src/App.tsx`
- [ ] T025 [P] [US1] `frontend/src/features/schoolbilling/BillingSettingsTab.tsx`: overdue days and expected payment day (shown only with `SCHOOL_CONTRACTS` EDIT), version handling

**Checkpoint**: contracts and assignments work end to end for each role; no receivable yet.

---

## Phase 5: User Story 3 - A Monthly Receivable Appears Without Typing (P1)

**Goal**: expected amounts are generated from contracts and assignments, pro-rated by working days, with a stored breakdown and kept history.

**Independent Test**: a School on ₹15,000 per Teacher with 3 Teachers for the whole month shows ₹45,000 and a breakdown naming them; a fourth Teacher from the 14th is billed 13 of 26 working days; the earlier figure stays as superseded.

### Tests for User Story 3

- [ ] T026 [P] [US3] `backend/src/test/java/com/hls/schoolbilling/ReceivableCalculatorTest.java` (pure unit tests, written first): full month, part month (13 of 26 is half), lump sum equals the lump sum whatever the Teacher count, contract change mid-month bills each part on its own contract, Teacher moved between Schools, no contract or RATE_PENDING gives no receivable and a "no rate" marker, rounding half-up to paise once per Teacher line and the total equals the sum of the lines, months before `first_billable_month` produce nothing (FR-010a)
- [ ] T027 [P] [US3] `backend/src/test/java/com/hls/attendance/BillingDaysTest.java`: `billingDays` counts working days per School from the day plan, adds unplaced days that the default calendar treats as working days to the month total, and a Teacher who joins on the 14th gets 13 of 26
- [ ] T028 [P] [US3] `backend/src/test/java/com/hls/schoolbilling/ReceivableApiTest.java`: calculate for all Schools and for one; recalculation supersedes and keeps the old row; one CURRENT GENERATED row per School and month (partial unique index); adjustment needs a reason and an open month; a closed month refuses recalculation; per-role and per-scope matrix (Manager 403 on calculate and adjust)

### Implementation for User Story 3

- [ ] T029 [P] [US3] Add `BillingDays billingDays(UUID teacherId, YearMonth month)` and `record BillingDays(Map<UUID,Integer> workingDaysBySchool, int workingDaysInMonth)` to `attendance/api/AttendanceReadApi.java` and implement it in `attendance/internal` over `RollupCalculator.plan` (WORKING days per School; NOT_PLACED days counted into `workingDaysInMonth` only when not a default weekly off or non-working date)
- [ ] T030 [US3] `schoolbilling/internal/ReceivableCalculator.java`: pure class; inputs contract periods, assignments and each Teacher's `BillingDays`; output lines and total as `BigDecimal` scale 2; deterministic ordering of lines; no Spring dependencies
- [ ] T031 [US3] `schoolbilling/internal/ReceivableService.java`: `calculate(month)` (all Schools in the caller's scope, per-School work on virtual-thread-per-task via structured concurrency, one transaction per School), `calculate(schoolId, month)`, `adjust(...)`; supersede rule; breakdown JSON per data-model.md; creates the `billing_month` row; refuses before `first_billable_month` and in a CLOSED month; recalculates the affected School after an assignment or contract change in an open month; audit entity `RECEIVABLE`
- [ ] T032 [US3] `schoolbilling/web/BillingController.java`: `POST /months/{month}/calculate`, `POST /schools/{schoolId}/months/{month}/calculate`, `POST /schools/{schoolId}/months/{month}/adjustments`, `GET /schools/{schoolId}/months/{month}` (current receivable with breakdown, history, adjustments, payments, balance) guarded by `SCHOOL_BILLING` EDIT/VIEW
- [ ] T033 [P] [US3] School detail month view in `frontend/src/features/schoolbilling/SchoolBillingPage.tsx`: receivable with its breakdown, superseded versions, adjustment form (Admin and Director), Calculate button; DD/MM/YYYY and ₹ Indian grouping helpers reused from `frontend/src/features/common`

**Checkpoint**: receivables generate and explain themselves; US1 and US2 still pass.

---

## Phase 6: User Story 4 - Record Payments and See the Balance (P2)

**Goal**: append-only payments with reversals and advances, and a computed balance.

**Independent Test**: against ₹45,000 record ₹4,500 and ₹9,000 and see ₹31,500; reverse the first and see ₹36,000 with both entries listed; no edit or delete exists.

### Tests for User Story 4

- [ ] T034 [P] [US4] `backend/src/test/java/com/hls/schoolbilling/BalanceServiceTest.java` (unit, first): balance = Σ current receivables (including adjustments) up to the month − Σ payments + Σ reversals; carry-forward across months; advance (negative balance) and a payment against a month with no receivable; a reversal in a later month than the payment
- [ ] T035 [P] [US4] `backend/src/test/java/com/hls/schoolbilling/PaymentApiTest.java`: record with each mode, several per month, reverse needs a reason, a payment reverses at most once (409), no PUT or DELETE (405), a direct SQL UPDATE or DELETE fails on the trigger, duplicate warning flag but saved, amount must be positive with two decimals, Manager records only for own Schools and gets 404 otherwise, Teacher and System 403, a reassigned Manager loses sight of the history (clarification 4) while `receiver_name` stays on old payments
- [ ] T036 [P] [US4] `frontend/src/features/schoolbilling/PaymentForm.test.tsx`: fields and validation, duplicate warning shown, reverse dialog asks for a reason, no Edit or Delete controls, advance label for a negative balance

### Implementation for User Story 4

- [ ] T037 [US4] `schoolbilling/internal/BalanceService.java`: balance per School and month and carried forward, computed in SQL sums; negative means advance
- [ ] T038 [US4] `schoolbilling/internal/PaymentService.java`: `record` (receiver defaults to the recorder, `receiver_name` copied as text, month may have no receivable yet), `reverse` (reason required, once only), duplicate detection by School, month, amount and date; clears an `overdue_flag` in the same transaction when the balance reaches zero; audit entity `SCHOOL_PAYMENT`
- [ ] T039 [US4] `schoolbilling/web/PaymentController.java`: `POST /schools/{schoolId}/months/{month}/payments`, `POST /payments/{id}/reverse`, `GET /schools/{schoolId}/payments?month=` guarded by `SCHOOL_BILLING` CREATE/VIEW, scope through `ScopeQueries`
- [ ] T040 [P] [US4] `frontend/src/features/schoolbilling/PaymentForm.tsx` and the payments list in `SchoolBillingPage.tsx`: record dialog, reverse dialog, balance and advance display

**Checkpoint**: money in and out reconciles; append-only proven in API and database.

---

## Phase 7: User Story 5 - Expected, Collected and Outstanding at a Glance (P2)

**Goal**: totals by Manager and School for Admin, Director and Manager (scoped), with export, the public interface for later specs, and month close.

**Independent Test**: three Schools under two Managers with some payments: organization totals equal the sum of the Managers and of the Schools; each Manager sees only theirs; closing a month is refused until every assigned Teacher's attendance is locked.

### Tests for User Story 5

- [ ] T041 [P] [US5] `backend/src/test/java/com/hls/schoolbilling/OverviewApiTest.java`: totals, `byManager` and `bySchool` each sum to the totals to the paisa, two-Manager scope boundary in list, totals, search and export, `noRateSchools` listed, System and Teacher 403, empty month returns an empty overview
- [ ] T042 [P] [US5] `backend/src/test/java/com/hls/schoolbilling/MonthCloseTest.java`: close refused with the names of Teachers whose attendance is not locked, refused while the previous billable month is open, only Admin may close (Director and Manager 403), after closing recalculation is refused and an adjustment lands in the next open month
- [ ] T043 [P] [US5] `backend/src/test/java/com/hls/schoolbilling/SchoolBillingPublicApiTest.java`: `rateOf`, `figures`, `billedLine` (lump sum gives an equal share per assigned Teacher) and `isClosed` return the same numbers as the screens; ArchUnit test that no other module reads billing tables
- [ ] T044 [P] [US5] `frontend/src/features/schoolbilling/BillingOverviewPage.test.tsx`: role fixtures (Admin and Director full, Manager scoped, Close button only for Admin), empty and error states, axe in both themes

### Implementation for User Story 5

- [ ] T045 [US5] `schoolbilling/internal/OverviewService.java`: totals and the per-Manager and per-School breakdown from the same rows, grouped by each School's current Zone Manager from `ManagerQueries`; scope through `ScopeQueries`; `BillingExporter.java` writes the CSV with cells guarded against formula injection
- [ ] T046 [US5] `schoolbilling/internal/MonthCloseService.java`: close as described in research.md section 6 using `AttendanceReadApi.isLocked` per assigned Teacher; sets CLOSED and freezes CURRENT receivables; audit entity `BILLING_MONTH`
- [ ] T047 [US5] `schoolbilling/api/{SchoolBilling,TeacherRate,SchoolMonthFigures,TeacherBilledLine}.java` per contracts/schoolbilling-api.md and `schoolbilling/internal/SchoolBillingImpl.java`
- [ ] T048 [US5] Add `GET /overview`, `GET /overview/export` and `POST /months/{month}/close` to `schoolbilling/web/BillingController.java` (close guarded by `SCHOOL_BILLING` APPROVE)
- [ ] T049 [P] [US5] `frontend/src/features/schoolbilling/BillingOverviewPage.tsx`: month picker, three totals, per-Manager and per-School tables, no-rate and overdue lists, Export (with EXPORT), Close month (with APPROVE); route `/operations/school-billing` in `frontend/src/App.tsx`

**Checkpoint**: Admin, Director and Manager see consistent figures; spec 013 can read billing through the public interface.

---

## Phase 8: User Story 6 - Overdue Payments Raise an Alert (P3)

**Goal**: one notification per overdue School and month to its Zone Manager and the Directors; flag clears on payment.

**Independent Test**: with the threshold at 7 days and a balance unpaid 8 days after the expected date, the School is flagged and its Manager and the Directors are notified once; paying in full clears the flag.

### Tests for User Story 6

- [ ] T050 [P] [US6] `backend/src/test/java/com/hls/schoolbilling/OverdueJobTest.java`: flags exactly when the balance is above zero more than `overdue_days` after the expected payment day of the next month, publishes `SchoolPaymentOverdue` once per School and month (second run sends nothing), clears on full payment, a changed threshold applies on the next run, job off in `IntegrationTestBase`
- [ ] T051 [P] [US6] `backend/src/test/java/com/hls/notification/BillingNotificationTest.java`: the Zone Manager and every active Director get one `PAYMENT_OVERDUE` notification with the School, month and balance in the text and a link to `/operations/school-billing/schools/{id}`; none for System; none when the School has no Manager and no Director (nothing created, no failure)

### Implementation for User Story 6

- [ ] T052 [US6] `schoolbilling/api/SchoolPaymentOverdue.java` and `schoolbilling/internal/OverdueJob.java` (daily, property `hls.billing.overdue.enabled`, off in `IntegrationTestBase`, uses `@EnableScheduling` from spec 010); writes `overdue_flag` and publishes the event in one transaction
- [ ] T053 [US6] `notification/internal/BillingEventListener.java` and `PAYMENT_OVERDUE` in `notification/api/NotificationType.java` (and its check constraint, by a new migration only if the constraint lists types), message text in `MessageFactory`; recipients through `RecipientResolver` (Zone Manager of the School, then every active Director)
- [ ] T054 [P] [US6] Overdue list on `BillingOverviewPage.tsx` with the days overdue per School

**Checkpoint**: alerts fire once and clear; the full spec is functional.

---

## Phase 9: Polish and Cross-Cutting Concerns

- [ ] T055 [P] `schoolbilling/internal/BillingDevSeeder.java`: demo contracts for Demo School One (₹15,000 per Teacher) and Demo School Two (rate pending), idempotent, runs only with the demo flag; one sample payment for the current month
- [ ] T056 [P] Role-by-role UI test of the OPERATIONS menu and every action visibility (Admin, Director, Manager, Teacher, System) in `frontend/src/features/schoolbilling/` and `frontend/src/app/` navigation tests
- [ ] T057 [P] Accessibility pass on the three screens (keyboard only, screen reader names for amounts and status chips, focus after dialogs) and phone-width layout; record results in `specs/012-school-contracts-billing/quickstart-results.md`
- [ ] T058 [P] Add a "School Billing" folder to the Postman collection in `docs/postman/HLS API/` (contracts, assignments, receivables, payments, overview, close) and refresh `docs/db/schema-v21.sql`
- [ ] T059 [P] Update `docs/spec-roadmap.md`: row 012 status; correct the 008, 009 and 010 rows (implemented and merged); mention that placement now lives in `schoolbilling`
- [ ] T060 [P] Add a "School Billing" row to the Default role access matrix in `.specify/memory/constitution.md` with a Sync Impact Report patch note (Principle II wording unchanged)
- [ ] T061 [P] Update `docs/running-locally.md` with the contract and billing demo data, and `docs/caching.md` only if a reference table is cached
- [ ] T062 Walk through every scenario in `quickstart.md` on the local app (database on Docker port 5433), including the placement comparison before and after `V20`, and write the outcomes to `specs/012-school-contracts-billing/quickstart-results.md`
- [ ] T063 Run the full backend suite and the full frontend suite once (one Maven class at a time if the machine struggles), then open the PR (it depends on nothing unmerged)

---

## Dependencies and Execution Order

- Phase 1 then Phase 2 first. Phase 2's T003 blocks every table-using task; T004 to T007 are independent of each other.
- **US2 (Phase 3) before US1 (Phase 4)**: the moved assignment service and `TeacherPlacementSource` are what contract rules and the assignment endpoints sit on. Within US2, T011 to T014 are in order; T009, T010 and T015 can be written alongside.
- US3 needs US1 (contracts) and T029 (`billingDays`). US4 needs US3 (months and receivables to pay against) but its calculation tests (T034) can start earlier. US5 needs US3 and US4. US6 needs US4 and US5 (balances and the `ManagerQueries` grouping).
- Frontend tasks marked [P] in a phase can start once that phase's API contract is fixed; they only need the typed client from T002.
- Polish after all stories; T062 and T063 last.

### Parallel opportunities

- Phase 2: T004, T005, T006, T007 together, then T008.
- US1: T017 to T020 together; T024 and T025 together after T022.
- US3: T026 to T028 and T029 together; T033 alongside T032.
- US4: T034 to T036 together; T040 alongside T039.
- US5: T041 to T044 together; T049 alongside T048.
- Polish: T055 to T061 together.

## Implementation Strategy

- **MVP**: Phases 1 to 4 (US2 and US1): the placement moves safely under contract assignments, and Admin and Director can record contracts. It changes no figure users see, and it unblocks everything else.
- **Value slice**: add US3 and US4 to get generated receivables and recorded payments (the replacement for the "SCHOOL PAYMENT" spreadsheet), then US5 for the Director's totals and the public interface specs 013 and 014 need, then US6.
- Land the placement move first and keep the old suites green before any billing logic: if anything is going to break, it breaks there.
