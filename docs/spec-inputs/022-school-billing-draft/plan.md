# Implementation Plan: School Contracts and Billing

**Branch**: `012-school-contracts-billing` | **Date**: 2026-10-05 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/012-school-contracts-billing/spec.md`

## Summary

Adds the `schoolbilling` bounded context (Constitution Principle VII) and retires the interim Teacher–School
placement of spec 005:

- **Contracts** per School (per-Teacher rate or lump sum, never both; monthly; start and end date), versioned by
  ending one and starting the next. Only Admin and Director create or change them.
- **Assignments**: `schoolbilling` takes over the dated Teacher–School rows (`contract_assignment`). `teacher`
  keeps asking "where is this Teacher?" through a new interface in `teacher.api` that `schoolbilling`
  implements, so `TeacherDirectory`, attendance, leave and the Teachers screen do not change. A migration
  copies every placement (same ids) and creates a "rate pending" contract for each School that had some.
- **Receivables** generated on demand per School and month from the active contract and the assignments,
  pro-rated by the Teacher's working days from attendance, with a stored breakdown, kept versions and adjustment lines.
- **Payments**: append-only entries (several per month, reversal entries, advances), a duplicate warning, and
  the balance computed from receivables and payments.
- **Views**: an overview (expected, collected, outstanding by Manager and School), a School month view and a
  settings tab; CSV export; a public `SchoolBilling` interface for specs 013, 014 and 017.
- **Month close** by an Admin once every assigned Teacher's attendance is locked; **overdue** detection by a
  daily job that publishes an event the `notification` module turns into one notification per School and month.

New code touches `identity` (permissions, navigation), `teacher` (the placement interface), `attendance` (a
small `billingDays` read on `AttendanceReadApi`), `notification` (one listener) and adds the frontend feature `schoolbilling`.

## Technical Context

**Language/Version**: Java 25 (Spring Boot 4.1.1-based); TypeScript 5.7 with React 19. Unchanged.

**Primary Dependencies**: Spring Web, Spring Data JPA, Spring Security, Spring Modulith, Flyway, ArchUnit;
`@EnableScheduling` already on from spec 010. Virtual-thread-per-task for the all-Schools calculation
(Principle VIII). Frontend: React Router, MUI. No new dependency.

**Storage**: PostgreSQL via `V20__create_schoolbilling_tables.sql` (contract, contract_assignment,
billing_month, receivable, school_payment, overdue_flag, billing_settings; copies the placements) and
`V21__drop_teacher_placement.sql`. Money is `NUMERIC(12,2)`. A trigger blocks update and delete on
`school_payment`. No cross-module foreign keys. Details in [data-model.md](./data-model.md).

**Testing**: JUnit 5, Spring Boot Test, Testcontainers (`IntegrationTestBase`). Unit tests for the
calculation (full month, part month by working days, lump sum, contract change mid-month, rounding) and the
balance before any integration. Per-role and per-scope tests on every endpoint (Manager A against Manager B's
School). A migration test that loads old-shaped placements and compares the result. Append-only tests (API
and the trigger). Month close tests with open and locked attendance. Overdue tests (once only, clears on
payment). ArchUnit and Modulith rules (`SchoolBillingModuleRulesTest`). Frontend: Vitest + Testing Library
with a fixture per role, axe in both themes.

**Target Platform**: Browser; single Spring Boot deployable.

**Project Type**: Web application (frontend + backend).

**Performance Goals**: overview for 200 Schools, 5 years of months and 50,000 payment rows loads in under 2 s
(index on `(school_id, month)` for receivables and payments, sums in SQL); calculating all Schools for a month
completes in under 30 s on the demo machine; School month view under 500 ms.

**Constraints**: scope is applied in every query through `ScopeQueries` (lists, totals, exports); a School
outside the scope is a 404; amounts never go through floating point; the calculation is deterministic (the same
inputs give the same figure and breakdown); a payment, reversal, contract change, recalculation and close each
write one audit entry in the same transaction; closing refuses with a list rather than a bare error.

**Scale/Scope**: about 100 users, tens of Schools, hundreds of Teachers; 7 tables, about 25 endpoints, 3
screens (overview, School detail, settings) plus the Teachers screen wording change.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle I (Operational Truth Must Be Auditable)** - PASS. Every contract, assignment, recalculation,
  adjustment, payment, reversal, close and settings change is audited through `ChangeRecorder`. Payments and
  receivable versions are never edited or deleted; corrections are new rows. The database trigger is an extra
  guard.
- **Principle II (Five Fixed Roles, Configurable Permissions)** - PASS. No new role. Modules
  `SCHOOL_CONTRACTS` and `SCHOOL_BILLING` are seeded per the spec's table, runtime-editable, ineligible for
  Teacher and System. The constitution's Default role access matrix gets a "School Billing" row at merge.
- **Principle III (Data Scope)** - PASS. `ScopeQueries` on every endpoint; Manager sees only current Schools
  (clarification 4); totals and exports use the same filter. Tests per boundary.
- **Principle IV (Role-Based Experience)** - PASS. Server-driven navigation item, hidden actions, DD/MM/YYYY,
  ₹ with Indian grouping, WCAG 2.2 AA, loading, empty and error states, phone width.
- **Principle V (Formula-Driven)** - PASS and central. Receivables come from contracts and assignments, never
  typed; the breakdown makes each figure explainable; missing rates are listed ("no rate set") rather than
  silently zero.
- **Principle VII (Modular Monolith)** - PASS with one structural change: `teacher.api` gains an interface that
  `schoolbilling` implements, so `teacher` does not depend on `schoolbilling`. `schoolbilling` depends on
  `teacher.api`, `school.api`, `organization.api`, `attendance.api` and public `identity` types; `notification`
  listens to `schoolbilling.api` events. ArchUnit and Modulith verify no cycle and no access to `internal`.
- **Principle VIII (Concurrency)** - PASS. Calculate-all is on demand and uses virtual-thread-per-task; the
  request path stays on the default model. The overdue job is a daily check, not payroll-like processing.
- **Principle IX (Reliability, Testability)** - PASS, needs the tests listed above, calculation first.
- **Principle X (Security)** - PASS. Actor from the JWT; amounts validated server-side (positive, two
  decimals, upper bound); free text length-limited and shown as plain text; CSV cells guarded against formula
  injection; no payment details beyond mode and a comment (no card or account numbers).
- **Principles VI, XI** - not applicable.

No violations requiring justification. Complexity Tracking is not needed. (Moving the placement table
between modules is the largest risk; it is handled by the interface, same-id migration and a migration test.)

## Project Structure

### Documentation (this feature)

```text
specs/012-school-contracts-billing/
├── plan.md              # This file (/speckit-plan command output)
├── research.md         # Phase 0 output
├── data-model.md       # Phase 1 output
├── quickstart.md       # Phase 1 output
├── contracts/
│   └── schoolbilling-api.md
├── checklists/requirements.md
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/src/main/java/com/hls/
├── identity/
│   ├── permissions/PermissionModule.java       # + SCHOOL_CONTRACTS, SCHOOL_BILLING
│   ├── permissions/PermissionEligibility.java  # Teacher and System excluded
│   ├── permissions/PermissionMatrixService.java    # + seed per spec table
│   └── accessmodel/{NavigationCatalog,AccessModelService}.java  # + OPERATIONS -> School Billing, scope Assigned
├── teacher/
│   ├── api/TeacherPlacementSource.java         # new interface (implemented by schoolbilling)
│   ├── internal/                               # TeacherPlacement, TeacherPlacementRepository,
│   │                                           #   TeacherPlacementService, PlacementStatus REMOVED;
│   │                                           #   TeacherDirectoryImpl, TeacherSchoolHooks, TeacherService,
│   │                                           #   ManagerTeacherCountEnricher, TeacherScopeService use the interface
│   └── web/TeacherController.java              # placement endpoints call the interface
├── attendance/api/AttendanceReadApi.java       # + billingDays(teacherId, month); implemented over RollupCalculator.plan
├── notification/internal/BillingEventListener.java   # overdue -> Manager and Directors
├── notification/api/NotificationType.java      # + PAYMENT_OVERDUE
└── schoolbilling/
    ├── api/                                    # SchoolBilling, TeacherRate, SchoolMonthFigures,
    │                                           #   TeacherBilledLine, SchoolPaymentOverdue
    ├── internal/                               # Contract, ContractAssignment, BillingMonth, Receivable,
    │                                           #   SchoolPayment, OverdueFlag, BillingSettings + repositories;
    │                                           #   ContractService, AssignmentService (moved placement rules),
    │                                           #   ReceivableCalculator (pure), ReceivableService, PaymentService,
    │                                           #   BalanceService, OverviewService, MonthCloseService,
    │                                           #   OverdueJob, BillingExporter, BillingDevSeeder
    └── web/                                    # BillingController, ContractController, PaymentController

backend/src/main/resources/db/migration/V20__create_schoolbilling_tables.sql
backend/src/main/resources/db/migration/V21__drop_teacher_placement.sql
backend/src/test/java/com/hls/schoolbilling/        # calculation, balance, scope, authorization, append-only,
                                                    #   close, overdue, migration, module rules
backend/src/test/java/com/hls/teacher/              # existing placement tests move to the interface

frontend/src/features/schoolbilling/
├── BillingOverviewPage.tsx       # totals, per Manager and School, overdue and no-rate lists
├── SchoolBillingPage.tsx         # contract history, assignments, months, payments
├── BillingSettingsTab.tsx
└── schoolBillingApi.ts
frontend/src/features/teachers/{PlacementDialog,TeachersPage,MyTeacherProfile}.tsx   # "interim" wording -> assignment
frontend/src/App.tsx                                  # + guarded routes under /operations/school-billing

docs/postman/                       # new folder "School Billing"
docs/spec-roadmap.md                # 012 row: status; 008, 009, 010 rows corrected
.specify/memory/constitution.md     # School Billing row in the Default role access matrix (patch note)
```

**Structure Decision**: Web application (unchanged layout). One new backend module with the `api` /
`internal` / `web` split, two migrations, the placement code moved out of `teacher.internal`, additive
changes to `identity`, `attendance` and `notification`, and one new frontend feature folder.

### Delivery slices (for tasks.md, each independently demoable)

1. **Foundation**: permissions, navigation, migration `V20`, module skeleton, `TeacherPlacementSource` and the
   moved assignment service; the existing placement tests pass unchanged (US2).
2. **Contracts (US1)**: contract service, endpoints, contract and assignment screens.
3. **Receivables (US3)**: `billingDays`, the calculator (unit tests first), generation, breakdown view.
4. **Payments and balance (US4)**: append-only payments, reversals, advances, duplicate warning.
5. **Overview and public interface (US5)**: totals, scope, export, `SchoolBilling`, month close.
6. **Overdue and polish (US6)**: job, event, notification listener, demo seed, docs, Postman, a11y, `V21`.

## Complexity Tracking

No violations. Table intentionally omitted.
