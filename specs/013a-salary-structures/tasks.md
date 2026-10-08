---

description: "Task list for feature implementation"
---

# Tasks: Salary Structures and Pay Policy

**Input**: Design documents from `/specs/013a-salary-structures/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/salary-structures-api.md,
contracts/pay-rules-interface.md, quickstart.md, and **specs 001-005, 008, 009, 012 and 016 implemented and merged**.
Also needed, and **not built at the time of planning**: **spec 005a** (designations, the Manager joining and exit
dates) and **amendment A3 to spec 009** (the Loss-of-Pay leave type). T001 checks both before any code is written.

**Tests**: included as first-class tasks. Constitution Principle IX requires per-role tests on every endpoint, UI
tests per role, and unit tests before calculation logic. The spec's invariants (rows never change, one answer for the
same inputs, never zero for a missing salary, no day counted twice) are only trustworthy with tests. The pure
calculators are tested before they are written.

**Organization**: grouped by user story in priority order (spec.md US1-US4). The three P1 stories go in the order
US1 (salary structure), US2 (pay policy), US3 (the `PayRules` interface), because US3 reads the data of US1 and US2.
US4 (P2) adds the gap flag and the explanatory notes.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1-US4; absent for Setup/Foundational/Polish

## Path Conventions

Backend `backend/src/main/java/com/hls/{payroll,attendance,teacher,identity}/...`, tests under
`backend/src/test/java/com/hls/...`; migrations `backend/src/main/resources/db/migration`; frontend
`frontend/src/features/payroll/...`. Run one Maven test class at a time (`mvn -o test -Dtest=...`); the machine is slow.

---

## Phase 1: Setup

- [ ] T001 Verify the prerequisites before writing code: in `backend/src/main/java/com/hls/` confirm that 005a's designation interface exists (a Designation type, a designation and joining/exit date on `organization.api` Manager queries, a designation on `teacher.api.TeacherDirectory`, per `docs/spec-inputs/005a-designations.md`) and that spec 009 has a Loss-of-Pay leave type (amendment A3). If either is missing, STOP and report to the user which one; do not stub it. Record in `specs/013a-salary-structures/research.md` section 1 the actual interface names found, and whether 005a keeps designation history (needed for `designationOn(person, date)`; write the answer into spec.md Assumptions). Also confirm amendment A2 to spec 008 (a loss-of-pay flag per day on attendance, `docs/spec-roadmap.md` section 6) exists; record in research.md section 4 whether the unpaid-day classifier reads that flag or the status-code category. If A2 is also missing, STOP and report it
- [ ] T002 Create the `payroll` module skeleton: `backend/src/main/java/com/hls/payroll/{api,internal,web}/` with `package-info.java` (the `api` package a `@NamedInterface`) and `backend/src/test/java/com/hls/payroll/PayrollModuleRulesTest.java` (ArchUnit: nothing outside `payroll` uses `payroll.internal`; `payroll` uses only the `api` packages of `teacher`, `organization`, `schoolbilling`, `attendance`, `leave` and 005a's module; no module reads `designation_salary` or `pay_policy`)
- [ ] T003 [P] Create `frontend/src/features/payroll/` with `salaryStructuresApi.ts` (types and calls for every endpoint in contracts/salary-structures-api.md; amounts as strings)

---

## Phase 2: Foundational (blocking prerequisites)

- [ ] T004 Write the next free `backend/src/main/resources/db/migration/V{n}__create_payroll_rule_tables.sql` (check the highest existing number first; `V24` at planning time) per data-model.md. `designation_salary`: id uuid PK, designation_id uuid not null (no foreign key to another module), amount numeric(12,2) not null check `> 0` and `<= 9999999.99`, effective_on date not null, note varchar(500) null, recorded_by uuid not null, recorded_at timestamptz not null; index `(designation_id, effective_on DESC, recorded_at DESC)`. `pay_policy`: id uuid PK, effective_on date not null, lop_divisor varchar(20) not null check in ('WORKING_DAYS'), half_day_fraction numeric(4,3) not null check `> 0 and < 1`, rounding varchar(20) not null check in ('NEAREST_RUPEE','UP','DOWN'), pay_month varchar(20) not null check in ('CALENDAR_MONTH'), note varchar(500) null, recorded_by uuid null, recorded_at timestamptz not null; index `(effective_on DESC, recorded_at DESC)`. A `BEFORE UPDATE OR DELETE` trigger on both tables raises an exception (same device as `V20`). Insert policy version 1: `2000-01-01`, WORKING_DAYS, 0.500, NEAREST_RUPEE, CALENDAR_MONTH, recorded_by null
- [ ] T005 [P] Add permission module `SALARY_STRUCTURES(VIEW, CREATE)` (no `EDIT`: rows are append-only) to `identity/permissions/PermissionModule.java`; add it to `PermissionEligibility` with eligible roles Admin and Director only, so Manager, Teacher and System cannot be granted it; seed in `PermissionMatrixService`/`PermissionMatrixSeeder`: Admin and Director VIEW and CREATE only; Manager, Teacher and System none
- [ ] T006 [P] Add navigation `OPERATIONS -> Salary Structures` (`/operations/salary-structures`, SALARY_STRUCTURES VIEW, roles Admin and Director) to `identity/accessmodel/NavigationCatalog.java` with an order value after School Contracts, and the data-scope entry org-wide (Admin, Director) in `identity/accessmodel/AccessModelService.java`
- [ ] T007 [P] Extend `PermissionEligibilityTest`, the navigation tests and the access-model tests under `backend/src/test/java/com/hls/identity/` for the module (Teacher, System and Zone Manager excluded, and the matrix refuses a grant to them) and the OPERATIONS item per role
- [ ] T008 [P] Add `backend/src/main/java/com/hls/attendance/api/WorkingCalendar.java` (amendment A5 to spec 008): `LocalDate businessToday()`, `List<LocalDate> workingDays(UUID schoolIdOrNull, LocalDate from, LocalDate to)` (null school means the default calendar), and `Map<UUID, List<LocalDate>> workingDaysBySchool(Collection<UUID> schoolIds, LocalDate from, LocalDate to)` so a whole run is a fixed number of queries; implement it in `attendance/internal/WorkingCalendarImpl.java` over `CalendarService.rules()`, `nonWorkingDates` and the School override, and `BusinessCalendar.today()`; add `backend/src/test/java/com/hls/attendance/WorkingCalendarTest.java` (default weekly off, a School override, a holiday on a working day, a range crossing months, a School without an override uses the default, `from` after `to` gives an empty list)
- [ ] T009 Entities and repositories in `payroll/internal`: `DesignationSalary` and `PayPolicy` (no setters, insert-only; enumerations `LopDivisor`, `RoundingRule`, `PayMonth`) each with a Spring Data repository, including `findFirstByDesignationIdAndEffectiveOnLessThanEqualOrderByEffectiveOnDescRecordedAtDesc` and `findFirstByEffectiveOnLessThanEqualOrderByEffectiveOnDescRecordedAtDesc`; Hibernate only validates the schema
- [ ] T010 [P] `backend/src/test/java/com/hls/payroll/PayrollAppendOnlyTest.java`: a direct SQL `UPDATE` and a direct SQL `DELETE` on `designation_salary` and on `pay_policy` are each refused; an `INSERT` of a policy with `half_day_fraction` 0, 1 or 1.5, an unknown `rounding`, or a salary of 0 or -1 is refused by the check constraints (SC-002)
- [ ] T011 [P] Add the port `payroll/internal/PersonDirectory.java` and its adapter `PersonDirectoryImpl.java` over the interfaces confirmed in T001, exposing: designation of a Manager or Teacher on a date, Manager joining and exit dates, whether a designation exists and its kind (TEACHER or MANAGER) and retired flag, and the people holding a designation (counts only). The rest of `payroll` depends on this port, not on 005a's types

**Checkpoint**: migration applies and refuses `UPDATE` and `DELETE`; permission and navigation seeded; `WorkingCalendar` tested; entities validate against the schema.

---

## Phase 3: User Story 1 - Record the Salary of a Designation (P1) 🎯 MVP

**Goal**: an Admin or Director sees every designation with the salary in effect today, records a new salary with an
effective date, and the earlier row stays unchanged.

**Independent Test**: record ₹30,000 from 01/11/2026, then ₹32,000 from 01/02/2027, and see both in the history, the first
in effect in December 2026 and the second in February 2027, with the first row unchanged.

### Tests for User Story 1 (write first, they must fail)

- [ ] T012 [P] [US1] `backend/src/test/java/com/hls/payroll/SalaryResolverTest.java`: at least 10 cases on a table of rows: no row; one row after the date; one row on the date; the latest of several on or before the date; two rows on one date (the one recorded last wins); a change in the middle of a month (the day before and the day of); a row far in the past; rows added out of date order; a retired designation (SC-001)
- [ ] T013 [P] [US1] `backend/src/test/java/com/hls/payroll/SalaryStructureApiTest.java`: record and read back; the earlier row unchanged after a later one; same-date tie; refusals each with the problem named: amount zero, negative, `100.123` (more than two decimals), above 9,999,999.99, missing date, a date before the first day of the current business month (use a fixed `Clock` and test 00:30 IST on the 1st), note over 500 characters; unknown designation is 404; a retired designation is accepted; list shows "no salary yet" (`inEffect` null) before the first effective date; a list of 50 designations loads in one paged query and under 1 s
- [ ] T014 [P] [US1] `backend/src/test/java/com/hls/payroll/SalaryStructuresAuthorizationTest.java`: Admin and Director succeed on every endpoint of contracts/salary-structures-api.md; Zone Manager, Teacher and System get 403 on each (one assertion per endpoint) and have no menu item (SC-004)
- [ ] T015 [P] [US1] `backend/src/test/java/com/hls/payroll/SalaryStructureAuditTest.java`: each recorded salary writes one `DESIGNATION_SALARY` change entry (entity id = designation id, field `amount`, prior = the amount in effect on that date before, new = the recorded amount) with the actor, in the same transaction; a refused request writes none

### Implementation for User Story 1

- [ ] T016 [US1] `payroll/internal/SalaryResolver.java` (pure, no clock, no repository): given a list of rows and a date, returns the latest `effective_on <= date`, ties by latest `recorded_at`; empty when none
- [ ] T017 [US1] `payroll/internal/SalaryStructureService.java`: `list(kind, includeRetired)` (each designation with the salary in effect today, its kind and retired flag), `detail(designationId)` (salary in effect today and full history newest first), `record(actor, designationId, amount, effectiveOn, note)` validating amount `> 0`, at most two decimals, at most 9,999,999.99; `effectiveOn` present and not before the first day of the current month in the business time zone via `WorkingCalendar.businessToday()`; note at most 500 characters, stored as plain text; writes through `ChangeRecorder` in the same `@Transactional` method; uses `PersonDirectory` to check the designation exists
- [ ] T018 [US1] `payroll/web/SalaryStructureController.java` with `GET /api/v1/salary-structures/designations`, `GET .../designations/{designationId}`, `POST .../designations/{designationId}/salaries`; guard each with `PermissionGuard.require(roles, SALARY_STRUCTURES, VIEW|CREATE)`; actor from the JWT; errors in the house format (400/403/404); amounts as strings with two decimals
- [ ] T019 [P] [US1] `frontend/src/features/payroll/SalaryStructuresPage.tsx`: the designation list under OPERATIONS → Salary Structures (name, kind, salary in effect or "no salary yet", retired chip), with loading, empty and error states, ₹ with Indian grouping, DD/MM/YYYY, a phone-width layout; add the guarded route `/operations/salary-structures` in `frontend/src/App.tsx`
- [ ] T020 [US1] `frontend/src/features/payroll/DesignationSalaryPage.tsx`: the designation's history (newest first; amount, effective date, who, when, note) and the "record a salary" form with inline validation that names each refused problem; the form is hidden without `CREATE`; route `/operations/salary-structures/:designationId`
- [ ] T021 [P] [US1] Frontend tests in `frontend/src/features/payroll/__tests__/SalaryStructures.test.tsx` with a fixture per role (Admin, Director see the item and the form; Zone Manager, Teacher, System see neither and a deep link shows "not authorized"), plus an axe check in light and dark themes

**Checkpoint**: US1 works end to end; quickstart section 1 passes.

---

## Phase 4: User Story 2 - Set the Pay Policy (P1)

**Goal**: an Admin or Director sees and maintains the dated, versioned pay policy; each date uses the version in effect on it.

**Independent Test**: open the policy, see the standard rules, record a rounding change from an effective date, and see
the earlier version still applies to earlier dates.

### Tests for User Story 2 (write first, they must fail)

- [ ] T022 [P] [US2] `backend/src/test/java/com/hls/payroll/PayPolicyApiTest.java`: a new installation returns the seeded rules (working-day divisor, 0.500, nearest rupee, calendar month); a new version is added and the old one is unchanged; `policyOn(date)` picks the version in effect on each date and ties go to the one recorded last; refusals each with the problem named: `halfDayFraction` of 0, 1, `-0.5`, `1.5`, `0.5555` (more than three decimals), an unknown `rounding`, an unknown `lopDivisor` or `payMonth`, a missing `effectiveOn`, an `effectiveOn` before the first day of the current month, a note over 500 characters; a version that changes nothing is accepted
- [ ] T023 [P] [US2] Extend `SalaryStructuresAuthorizationTest` (T014) and add `PayPolicyAuditTest` in `backend/src/test/java/com/hls/payroll/`: Zone Manager cannot open the policy; each changed field writes a `PAY_POLICY` entry (entity id = policy id, prior = the value in the version in effect before, new = the recorded value) with the actor

### Implementation for User Story 2

- [ ] T024 [US2] `payroll/internal/PayPolicyService.java`: `current()`, `history()`, `record(actor, ...)` validating `half_day_fraction` strictly between 0 and 1 with at most three decimals, `rounding` one of NEAREST_RUPEE, UP, DOWN, `lop_divisor` WORKING_DAYS, `pay_month` CALENDAR_MONTH, `effectiveOn` present and not before the first day of the current business month, note at most 500 characters; audit through `ChangeRecorder` in the same transaction; `policyOn(LocalDate)` for `PayRulesImpl`
- [ ] T025 [US2] `payroll/web/PayPolicyController.java` with `GET /api/v1/salary-structures/policy` and `POST /api/v1/salary-structures/policy`, guarded by `SALARY_STRUCTURES` VIEW and CREATE
- [ ] T026 [US2] `frontend/src/features/payroll/PayPolicyPage.tsx`: the policy in effect, its full history, and the "new version" form (hidden without CREATE) with inline range validation; route `/operations/salary-structures/policy`; link from `SalaryStructuresPage.tsx`
- [ ] T027 [P] [US2] Frontend test `frontend/src/features/payroll/__tests__/PayPolicy.test.tsx`: per-role visibility, the refused-value messages, an axe check

**Checkpoint**: US2 works; quickstart section 3 passes.

---

## Phase 5: User Story 3 - Payroll Asks One Place for the Rules (P1)

**Goal**: `PayRules` answers, for a person and a date or month, the salary in effect, the value of a day of loss of pay,
the payable days, the unpaid days and the month total, identically every time, never zero for a missing salary.

**Independent Test**: for a fixed set of people and months (a full month, a mid-month joiner, a mid-month transfer, an
exit, a month with a holiday) the answers equal the hand-computed figures.

### Tests for User Story 3 (write first, they must fail)

- [ ] T028 [P] [US3] `backend/src/test/java/com/hls/payroll/LossOfPayCalculatorTest.java`: ₹26,000 over 26 working days is a full day ₹1,000 and a half day ₹500; a non-terminating day value (₹30,000 over 26) is kept unrounded; a half-day fraction other than 0.5; zero working days gives `NoWorkingDays` and no division; a month total rounds exactly once with NEAREST_RUPEE, UP and DOWN (3 unpaid days at ₹1,153.846 each = ₹3,461.54 rounds to ₹3,462 under NEAREST_RUPEE); a raise on the 16th values days before it and after it at their own day's salary (clarification 2)
- [ ] T029 [P] [US3] `backend/src/test/java/com/hls/payroll/PayableDaysCalculatorTest.java`: a full month; a Teacher whose first School assignment is on the 11th (earlier days, including training, not payable); a transfer between two Schools with different weekly offs (each span counted on its own School's calendar, no day twice and none lost, including the transfer day); a Teacher who exited mid-month; a Teacher between Schools (gap days not payable); a Manager who joined mid-month and one who exits mid-month on the default calendar; a month with a holiday (not counted)
- [ ] T030 [P] [US3] `backend/src/test/java/com/hls/payroll/UnpaidDayClassifierTest.java`: an absence with no approved leave is unpaid; a Loss-of-Pay leave day is unpaid; a half day counts as the half-day fraction; approved Casual or Sick leave is not unpaid; a holiday or weekly off is never unpaid; a non-payable date (before joining, after exit) is never unpaid; classification uses the status code **category**, not the letter
- [ ] T031 [P] [US3] `backend/src/test/java/com/hls/payroll/PayRulesTest.java` (integration, Testcontainers): the hand-computed fixtures of quickstart section 4 through the public interface, including: a Teacher with no recorded salary gives `NoSalary(TEACHER_HAS_NO_RECORDED_SALARY)` even when their designation has a salary (FR-005, clarification 3); a Manager with no designation, and one whose designation has no salary, each give a stated reason and never zero; `unpaidDays` for a Manager is `Unavailable`; `totalLossOfPay` is `Cannot` when any unpaid day has no salary; the same inputs give identical answers on repeat calls; the bulk forms equal the one-at-a-time forms; the bulk forms run in a fixed number of queries for 300 people (a query-count assertion) and `salariesOn` for 300 people completes in under 2 s
- [ ] T032 [P] [US3] `backend/src/test/java/com/hls/payroll/PayRulesBoundaryTest.java`: `payroll.api` exposes no HTTP endpoint (no controller returns a `PayRules` result); no payroll endpoint or screen model returns an individual person's pay (FR-016): every response type under `payroll.web` is checked by reflection for the absence of person ids and per-person amounts; `TeacherView` is unchanged (no salary member)

### Implementation for User Story 3

- [ ] T033 [US3] Add `salaryOn(UUID teacherId, LocalDate date)` returning `Optional<BigDecimal>` to `backend/src/main/java/com/hls/teacher/api/TeacherDirectory.java`, implemented in `teacher/internal/TeacherDirectoryImpl.java` over `SalaryHistoryRepository.findFirstByTeacherIdAndEffectiveOnLessThanEqualOrderByEffectiveOnDescCreatedAtDesc`; add a method comment that it is the unchecked, server-side read and is not exposed over HTTP (spec 005 FR-019 still applies to the endpoints); extend the teacher module's directory test
- [ ] T034 [P] [US3] `payroll/internal/LossOfPayCalculator.java` (pure): day and half-day value from a salary, a working-day count and a half-day fraction; the month total from a list of (unpaid day, salary on that day, working days of its month, day value), rounded once by the policy's rule (`HALF_UP` for NEAREST_RUPEE, `CEILING` for UP, `FLOOR` for DOWN)
- [ ] T035 [P] [US3] `payroll/internal/PayableDaysCalculator.java` (pure): from employment spans (a Teacher's ACTIVE placement spans, or a Manager's joining and exit dates) and a map of working dates per School (or the default), returns the payable dates, the spans with their working-day counts, and the working days of the month; each date belongs to at most one span
- [ ] T036 [P] [US3] `payroll/internal/UnpaidDayClassifier.java` (pure): from attendance marks and the payable dates, returns the unpaid days with their day values; uses spec 008 amendment A2's loss-of-pay flag if T001 found it, otherwise the status-code category (an absence with no approved leave); also reads the Loss-of-Pay leave type through the interface confirmed in T001
- [ ] T037 [US3] `payroll/api/` result and interface types exactly as in contracts/pay-rules-interface.md: `PayRules`, `SalaryInEffect`, `LossOfPayValue`, `PayableDays`, `Span`, `UnpaidDays`, `UnpaidDay`, `TotalLossOfPay`, `PayPolicyView`, `PersonRef`
- [ ] T038 [US3] `payroll/internal/PayRulesImpl.java`: wires `SalaryResolver`, `PayPolicyService`, `PersonDirectory`, `TeacherDirectory`, `SchoolContracts`/placements, `AttendanceReadApi`, `WorkingCalendar` and the calculators; bulk forms load placements, marks and calendars for all people in a fixed number of queries; a Manager's `unpaidDays` returns `Unavailable("Manager attendance is not recorded yet")`; no `@Transactional` write path; no clock read inside the calculators (dates are passed in); includes `totalLossOfPay` (FR-018)

**Checkpoint**: `PayRulesTest` passes against the hand-computed figures; quickstart section 4 passes.

---

## Phase 6: User Story 4 - See What Is in Effect and Why It Changed (P2)

**Goal**: Admin and Director find the full history of a designation and the policy, see every change in the audit log,
and are shown which designations leave people without a salary.

**Independent Test**: record three salaries for a designation, open its history to see all three in date order with who
and when; find the audit entries; see the flag on a designation that people use but that has no salary.

### Tests for User Story 4 (write first, they must fail)

- [ ] T039 [P] [US4] `backend/src/test/java/com/hls/payroll/MissingSalaryReportTest.java`: a Manager designation with Managers and no salary in effect is flagged with the number of Managers; once a salary is recorded the flag clears; a Teacher designation is flagged with the number of Teachers holding it who have no recorded salary of their own, not by the designation's own salary; a designation nobody holds is not flagged; a retired designation still held is flagged; the report uses a fixed number of queries
- [ ] T040 [P] [US4] Extend `SalaryStructureApiTest` (T013): the history shows every row newest first with amount, effective date, who, when and note; `teacherDefaultNote` is present on a Teacher designation and absent on a Manager designation; the audit entries for three recorded salaries appear in Audit → Change History for an Admin

### Implementation for User Story 4

- [ ] T041 [US4] `payroll/internal/MissingSalaryReport.java`: per designation, the count of people left without a salary, in two bulk queries through `PersonDirectory` (Managers holding it; Teachers holding it without a recorded salary today via `TeacherDirectory.salaryOn`); wire the result into `SalaryStructureService.list` as `missingSalary { flag, people, note }` and add `teacherDefaultNote` for Teacher designations
- [ ] T042 [US4] `frontend/src/features/payroll/SalaryStructuresPage.tsx`: show the flag with the number of people affected and the Teacher-designation note, make the flagged designations filterable; `DesignationSalaryPage.tsx`: show the who/when/note columns and the note for Teacher designations
- [ ] T043 [P] [US4] Frontend test `frontend/src/features/payroll/__tests__/MissingSalary.test.tsx`: the flag shows with the count, the Teacher note shows, nothing shows for a role without access

**Checkpoint**: all four stories work; quickstart section 6 passes.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T044 [P] `payroll/internal/PayrollDevSeeder.java` (dev profile only): a salary for each seeded designation, so the demo list shows values and one designation without a salary shows the flag; do not seed any Teacher's own salary beyond what spec 005's seeder already does
- [ ] T045 [P] Add a "Salary Structures" Postman folder under `docs/postman/` with the five endpoints of contracts/salary-structures-api.md and the refusals as examples
- [ ] T046 [P] Update `docs/spec-roadmap.md`: the 013a row status, amendment A5 to spec 008 (`WorkingCalendar`), and the note that `TeacherDirectory.salaryOn` was added
- [ ] T047 [P] Update `.specify/memory/constitution.md`: a "Salary Structures" row in the Default role access matrix (Admin and Director View and Create; Manager, Teacher and System none), with a patch entry in the Sync Impact Report
- [ ] T048 Run `PayrollModuleRulesTest` and `ApplicationModulesTest` (Spring Modulith verification) and fix any boundary violation; confirm `attendance` and `teacher` tests still pass unchanged
- [ ] T049 [P] Accessibility pass on the three screens: keyboard order, labels on every form field, error text announced, WCAG 2.2 AA contrast in light and dark themes, no horizontal scroll at phone width
- [ ] T050 Run the full quickstart.md walkthrough and record each step's outcome in `specs/013a-salary-structures/quickstart-results.md`; also time recording a salary (under 1 minute) and finding the salary in effect on a date from its history (under 30 seconds), SC-005
- [ ] T051 Financial review of the formulas in research.md section 4 against `docs/spec-roadmap.md` decisions D2, D12 and D13 (Constitution Principle IX, Development Workflow item 5); note the outcome in `quickstart-results.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: T001 first and blocking; if 005a or A3 is missing, nothing else starts
- **Foundational (Phase 2)**: after Setup; blocks every user story
- **US1 (Phase 3)**: after Foundational; the MVP
- **US2 (Phase 4)**: after Foundational; independent of US1 (separate table and service), shares `SalaryStructuresAuthorizationTest`
- **US3 (Phase 5)**: after US1 and US2 (it reads both tables through their services)
- **US4 (Phase 6)**: after US1 and US3 (`salaryOn` for the Teacher count)
- **Polish (Phase 7)**: after all stories

### Within Each Story

- Tests are written first and must fail
- Pure calculators before the services and the interface that use them
- Services before controllers; controllers before screens

### Parallel Opportunities

- T003 with T002; T005, T006, T007, T008, T010, T011 after T004 and T009 as noted
- In US1: T012 to T015 together; T019 with T016 to T018
- In US3: T028 to T032 together; T034 to T036 together after T033
- US1 and US2 can run in parallel after Phase 2

## Parallel Example: User Story 3

```text
# Tests first, together:
Task: "LossOfPayCalculatorTest in backend/src/test/java/com/hls/payroll/LossOfPayCalculatorTest.java"
Task: "PayableDaysCalculatorTest in backend/src/test/java/com/hls/payroll/PayableDaysCalculatorTest.java"
Task: "UnpaidDayClassifierTest in backend/src/test/java/com/hls/payroll/UnpaidDayClassifierTest.java"

# Then the pure calculators, together:
Task: "LossOfPayCalculator in backend/src/main/java/com/hls/payroll/internal/LossOfPayCalculator.java"
Task: "PayableDaysCalculator in backend/src/main/java/com/hls/payroll/internal/PayableDaysCalculator.java"
Task: "UnpaidDayClassifier in backend/src/main/java/com/hls/payroll/internal/UnpaidDayClassifier.java"
```

## Implementation Strategy

### MVP First (US1 only)

1. T001 to T003, then Phase 2
2. Phase 3 (US1): record and read salaries by designation
3. Stop and validate with quickstart section 1; this already gives payroll a salary to start from
4. Then US2 and US3, because 013b cannot start without the interface

### Incremental Delivery

1. Setup + Foundational: tables, permissions, working-calendar interface
2. US1: salaries; US2: policy; US3: the `PayRules` interface that 013b needs
3. US4: the gap flag, then Polish and the financial review

## Notes

- [P] tasks = different files, no dependencies
- Rows in `designation_salary` and `pay_policy` are never changed or removed; do not add an update or delete path to any layer
- A missing salary, designation or working day is always a stated reason, never zero (SC-007)
- Rounding is applied once, to the month total only
- Commit after each task or logical group
