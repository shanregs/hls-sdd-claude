# Implementation Plan: Salary Structures and Pay Policy

**Branch**: `013a-salary-structures` | **Date**: 2026-10-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/013a-salary-structures/spec.md`

## Summary

Adds the `payroll` bounded context (Constitution Principle VII) with its first half: the rules payroll (013b) will
read. Nothing here computes a payslip.

- **Salary structure**: an append-only list of fixed monthly salaries per designation (spec 005a), each with an
  effective date, note, who and when. The salary in effect on a date is the latest row on or before it, ties broken by
  the row recorded last.
- **Pay policy**: append-only dated versions holding the loss-of-pay divisor rule, the half-day fraction, the
  rounding rule and the payroll month. Version 1 is seeded by the migration (calendar month, working-day divisor,
  half = 0.5, nearest rupee).
- **One public interface, `PayRules`** (`payroll.api`), answering four questions for 013b and reports: the salary in
  effect for a person on a date, the loss-of-pay value of a day, the payable days of a month, and the unpaid days
  of a month. Every answer is a sealed result that carries a stated reason when it cannot be given (never zero,
  never a divide by zero).
- **Screens**: OPERATIONS → Salary Structures for Admin and Director (designation list with the salary in effect and a
  "people without a salary" flag, per-designation history and record form, pay policy and its history).
- **Clarified rules (spec session 2026-10-08)**: rounding only on the monthly total; a mid-month salary change values
  each unpaid day at the salary of that day; a Teacher without a record salary has "no salary", never the designation's;
  `SALARY_STRUCTURES` has `VIEW` and `CREATE` only; a policy version cannot start before the first day of the current
  month.

Prerequisites that are **not built yet** (see research.md section 1): spec 005a (designations), amendment A3 to spec
009 (Loss-of-Pay leave type), amendment A2 to spec 008 (loss-of-pay flag per day), and a public working-calendar interface in `attendance` (new here, amendment A5 to
spec 008). This plan implements the last one and consumes the other two through interfaces; tasks.md orders the work
so 005a lands first.

## Technical Context

**Language/Version**: Java 25 (Spring Boot 4.1.1-based); TypeScript 5.7 with React 19. Unchanged.

**Primary Dependencies**: Spring Web, Spring Data JPA, Spring Security, Spring Modulith, Flyway, ArchUnit. Frontend:
React Router, MUI. No new dependency.

**Storage**: PostgreSQL via one migration (next free number at implementation time; `V24` today unless 005a takes it)
creating `designation_salary` and `pay_policy`. Money is `NUMERIC(12,2)`. Append-only is enforced by a trigger that
refuses `UPDATE` and `DELETE`, the same device spec 012's migration uses. Details in [data-model.md](./data-model.md).

**Testing**: JUnit 5, Spring Boot Test, Testcontainers (`IntegrationTestBase`). Rules first, as unit tests on the pure
calculators (`SalaryResolver`, `LossOfPayCalculator`, `PayableDaysCalculator`, `UnpaidDayClassifier`) with the
hand-computed tables of SC-001 (at least 10 cases) and SC-003 (full month, mid-month joiner, transfer between Schools
with different calendars, exit, holiday month). Per-role tests on every endpoint (Admin and Director allowed; Zone
Manager, Teacher, System refused, SC-004). A migration test that the append-only trigger refuses an `UPDATE` and a
`DELETE` made directly in SQL (SC-002). Audit-entry assertions for every change. ArchUnit and Modulith rules
(`PayrollModuleRulesTest`). Frontend: Vitest + Testing Library with a fixture per role, axe in both themes.

**Target Platform**: Browser; single Spring Boot deployable.

**Project Type**: Web application (frontend + backend).

**Performance Goals**: the designation list loads in under 1 s (one query for latest rows, one for people counts);
`PayRules` supports a bulk form so 013b can run 300 people in a fixed number of queries (asserted in a query-count
test); `salariesOn` for 300 people completes in under 2 s.

**Constraints**: the backend refuses every request without `SALARY_STRUCTURES` and fails closed; the actor comes from
the JWT; no screen shows an individual person's pay (FR-016), so the only per-person figures are returned by
`PayRules` to server code; every addition writes one audit entry in the same transaction; "today" is the business date
(Asia/Kolkata) via the shared business calendar, not UTC.

**Scale/Scope**: about 100 users, tens of designations, a salary row a few times a year; 2 tables, about 8 endpoints,
3 screens (list, designation history and form, pay policy).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle I (Operational Truth Must Be Auditable)** - PASS. Every salary and policy row is audited through
  `ChangeRecorder` (who, when, prior and new value, in the same transaction). Rows are never edited or deleted, and a
  database trigger makes that true outside the application too.
- **Principle II (Five Fixed Roles, Configurable Permissions)** - PASS. No new role. Module `SALARY_STRUCTURES`
  (`VIEW`, `CREATE`) seeded to Admin and Director only, runtime-editable, ineligible for Manager, Teacher and System (only Admin and Director can hold it). The
  Default role access matrix gets a "Salary Structures" row at merge. The constitution lists Edit among Director's
  default actions, but this module declares no `EDIT` because rows are append-only, so the seed matches the spec.
- **Principle III (Data Scope)** - PASS. The data is organization-wide for Admin and Director and not reachable by any
  other role, so no per-record scope is needed. `PayRules` answers only server callers (013b) and is not exposed over
  HTTP.
- **Principle IV (Role-Based Experience)** - PASS. Server-driven navigation item, hidden actions, ₹ with Indian
  grouping, DD/MM/YYYY, WCAG 2.2 AA, loading, empty and error states, phone width.
- **Principle V (Payroll Formula-Driven)** - PASS, and this spec is its foundation: salary, loss-of-pay value, payable
  days and rounding are computed from the data model and one policy, never typed per month.
- **Principle VII (Modular Monolith)** - PASS with three additive interfaces: `payroll` reads `teacher.api`,
  `organization.api`, `schoolbilling.api`, `attendance.api` and (once built) the 005a designation interface, and
  exposes only `payroll.api`. A new `attendance.api.WorkingCalendar` is added so `payroll` never reads attendance
  tables or reimplements weekly-off rules. ArchUnit and Modulith verify no cycle and no access to `internal`.
- **Principle VIII (Concurrency)** - not applicable here; the bulk `PayRules` forms are shaped so 013b's virtual-thread
  batch can call them per person without shared mutable state (the calculators are pure and stateless).
- **Principle IX (Reliability, Testability)** - PASS, requires the tests above, rules first; the work changes pay
  logic, so the formulas in research.md section 4 get a financial review before 013b.
- **Principle X (Security)** - PASS. Amount, date, note and policy values validated server-side (positive, two
  decimals, at most 9,999,999.99; fraction strictly between 0 and 1; note length-limited and shown as plain text).
- **Principles VI, XI** - not applicable.

No violations requiring justification. Complexity Tracking is not needed.

**Post-design re-check (after data-model.md and contracts)**: unchanged, all PASS. The `PayRules` interface
carries no personal pay screens; the HTTP API exposes designation rates and the policy only.

## Project Structure

### Documentation (this feature)

```text
specs/013a-salary-structures/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   ├── salary-structures-api.md   # HTTP, for the screens
│   └── pay-rules-interface.md     # Java interface, for 013b
├── checklists/requirements.md
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/src/main/java/com/hls/
├── identity/
│   ├── permissions/PermissionModule.java       # + SALARY_STRUCTURES(VIEW, CREATE)
│   ├── permissions/PermissionEligibility.java  # Teacher and System excluded
│   ├── permissions/PermissionMatrixService.java    # + seed Admin and Director VIEW, CREATE
│   └── accessmodel/{NavigationCatalog,AccessModelService}.java  # + OPERATIONS -> Salary Structures, org-wide
├── attendance/api/WorkingCalendar.java         # new: working days of a date range for a School or the default
├── attendance/internal/WorkingCalendarImpl.java   # over CalendarService and the weekly-off rules
└── payroll/
    ├── api/                                    # PayRules, SalaryInEffect, LossOfPayValue, PayableDays,
    │                                           #   UnpaidDays, PayPolicyView (sealed results carry reasons)
    ├── internal/                               # DesignationSalary, PayPolicy + repositories;
    │                                           #   SalaryStructureService, PayPolicyService (record, history, validation),
    │                                           #   SalaryResolver, LossOfPayCalculator, PayableDaysCalculator,
    │                                           #   UnpaidDayClassifier (pure), PayRulesImpl, MissingSalaryReport,
    │                                           #   PayrollDevSeeder
    └── web/                                    # SalaryStructureController, PayPolicyController

backend/src/main/resources/db/migration/V24__create_payroll_rule_tables.sql   # number confirmed at implementation
backend/src/test/java/com/hls/payroll/          # resolver, loss of pay, payable days, unpaid days, policy, append-only
                                                #   trigger, scope/authorization, audit, public interface, module rules
backend/src/test/java/com/hls/attendance/       # WorkingCalendarTest

frontend/src/features/payroll/
├── SalaryStructuresPage.tsx       # designation list with salary in effect and the missing-salary flag
├── DesignationSalaryPage.tsx      # history and the record form
├── PayPolicyPage.tsx              # current policy, history, new version form
└── salaryStructuresApi.ts
frontend/src/App.tsx                                  # + guarded routes under /operations/salary-structures

docs/postman/                       # new folder "Salary Structures"
docs/spec-roadmap.md                # 013a row status; amendment A5 to 008
.specify/memory/constitution.md     # Salary Structures row in the Default role access matrix (patch note)
```

**Structure Decision**: Web application (unchanged layout). One new backend module with the `api` / `internal` /
`web` split, one migration, additive changes to `identity` and `attendance`, and one new frontend feature folder
(shared with 013b).

### Delivery slices (for tasks.md, each independently demoable)

1. **Foundation**: permissions, navigation, migration with the append-only triggers and the seeded policy v1, module
   skeleton, `WorkingCalendar` in attendance.
2. **Salary structure (US1)**: service, resolver, endpoints, the list and the record form.
3. **Pay policy (US2)**: policy service and validation, endpoints, the policy page.
4. **Payroll interface (US3)**: the pure calculators, `PayRulesImpl`, the hand-computed tables.
5. **History and gaps (US4)**: designation history, missing-salary flag, Teacher-designation note.
6. **Polish**: demo data, docs, Postman, a11y, roadmap and constitution row.

## Complexity Tracking

No violations. Table intentionally omitted.
