# Implementation Plan: School Billing and Receivables

**Branch**: `022-school-billing` | **Date**: 2026-10-26 | **Spec**: [spec.md](spec.md)

## Summary

An internal receivables ledger in the existing `schoolbilling` module (package `com.hls.schoolbilling`, api / internal /
web). Each School-month gets one immutable **monthly due** whose lines are derived from the ACTIVE Teacher assignments
(through `TeacherPlacementSource`, `SchoolContracts`, `WorkingCalendar` and `SchoolWorkingDays`), reduced only by
append-only **adjustments** and **collections** (with allocations and reversals). Balances and ageing are computed, never
stored. A daily job creates one follow-up task per overdue due through `TaskCreation` (spec 025) and relies on 025's own
escalation to tell the Director. A new `SCHOOL_BILLING` permission module, an OPERATIONS → School Billing screen group
in the React app, a public `schoolbilling.api.SchoolReceivables` interface for 013b, 014 and 028, and two billing
settings complete it. Nothing is sent to a School and no invoice document exists.

## Technical Context

- Java 25, Spring Boot, Spring Modulith, JPA, Flyway, PostgreSQL (Testcontainers in tests), React + Vite + Vitest. Maven needs `JAVA_HOME` set to JDK 25.
- Migration: `V120__create_school_receivables.sql`. Highest on main is V90 (015); 031 plans V100 and 024 plans V110, so V120 avoids collisions.
- Constraints: dues, lines, adjustments, collections and allocations are append-only (database trigger refuses UPDATE and DELETE on the ledger tables; a due's status is the only derived value and is not stored, apart from a `cancelled_*` pair written once); no cross-module foreign keys; audit through `ChangeRecorder`; dates through `WorkingCalendar.businessToday()`; money `numeric(14,2)`, rounding HALF_UP at the line.
- Performance: overview and ageing for 500 Schools with 12 months of dues under 3 seconds (SC-008), by set-based queries with one read per month and no per-School query.

## Constitution Check

| Principle | How it is met |
| --- | --- |
| I Auditable | Every raise, cancel, adjustment, collection, allocation, reversal and setting change goes to Change History through `ChangeRecorder`; ledger tables are append-only by trigger; each line stores its working. |
| II Five roles, RBAC | New module `SCHOOL_BILLING` (View, Create, Edit, Approve, Export), seeded grants, editable in the matrix; Teacher and System ineligible. |
| III Data scope | Admin and Director org-wide; Zone Manager sees only Schools of their Zone, derived per request through `ScopeQueries`; others get 404; totals, ageing and CSV use the same filter. |
| IV Role-based UI | Menu item School Billing under OPERATIONS from the navigation catalog; actions shown only when granted. |
| V Formula-driven receivables | Amounts are computed from active assignments and contract salaries; margin inputs are exposed by `SchoolReceivables`; unpaid months are flagged. |
| VII Modular monolith | `schoolbilling` reads `teacher.api`, `attendance.api`, `organization.api`, `school.api`, `tasks.api`, `identity` only through their api packages; `notification` reads the new `schoolbilling.api` events. ArchUnit test extended. |
| VIII Concurrency | Unique index on (school, month) for non-cancelled dues; the loser of a race gets 409. Optimistic locking is not needed for append-only rows; a per-School advisory lock guards allocation against double-spending a collection. |
| IX Testability | Unit tests of the proration, API tests per role and scope, job idempotency, ArchUnit, seeded-volume test, frontend tests. |
| X Security | Every endpoint re-checks grant and scope server-side; CSV export escapes formula injection; no file uploads. |

**Constitution change**: PATCH bump (next free after 2.5.11 at merge time): the Default role access matrix gains a School Billing row (Admin and Director: View, Create, Edit, Approve, Export; Manager: View, Export and Create for collections, own Zone only). No principle changes.

## Project Structure

```text
specs/022-school-billing/ spec.md plan.md research.md data-model.md quickstart.md tasks.md contracts/
backend/src/main/java/com/hls/schoolbilling/
  api/       SchoolReceivables, MonthlyDueRaised, MonthlyDueOverdue (events), package-info (existing)
  internal/  MonthlyDue, DueLine, DueAdjustment, Collection, CollectionAllocation, repositories,
             DueCalculator (proration), DueService, AdjustmentService, CollectionService, LedgerQueryService,
             AgeingService, OverdueJob, SchoolReceivablesImpl, BillingSettings, BillingDevSeeder
  web/       ReceivablesController, CollectionsController, BillingReportController
backend/src/main/java/com/hls/notification/internal/ReceivablesEventListener.java (+ MessageFactory texts)
backend/src/main/resources/db/migration/V120__create_school_receivables.sql
frontend/src/features/schoolbilling/receivables/ (BillingOverviewPage, SchoolLedgerPage, RaiseDuesDialog,
  CollectionDialog, AdjustmentDialog, AgeingTable, billingApi, __tests__)
```

## Design Decisions

See [research.md](research.md). Highlights: the Zone Manager's follow-up task and the Director escalation both come from 025
(`TaskCreation.createFollowUp` with `escalateAfterDays` set from the setting), so 022 owns no escalation state; a due's status
(open, part paid, paid) and "overdue" are derived from the ledger, only cancellation is stored; the proration numerator is the
School calendar's working days inside the assignment and the month, the denominator the School's month working days (037);
a full-month assignment is exact salary with no division.

## Phases

1. Migration, entities, `SCHOOL_BILLING` module and eligibility, navigation, access-model scope, settings keys.
2. DueCalculator and raise / cancel (US1, US2); "not billed" list.
3. Collections, allocations, reversals, adjustments (US3, US4).
4. Ledger queries, ageing, scope, CSV (US5).
5. OverdueJob, task creation, notifications (US6).
6. `SchoolReceivables` public interface (US7), then frontend, demo data, Postman, results, roadmap and constitution row.
