# Implementation Plan: Substitute Teachers: Day Attendance, Day-Rate Pay and Reconciliation

**Branch**: `spec/017b-payout-reconciliation` | **Date**: 2026-11-01 (reworked after the owner's answers) | **Spec**: [spec.md](spec.md)

**Status**: Q1 to Q8 answered by the owner. N1 to N6 (clarification-questions.md) are assumed on option A, pending owner confirmation.

## Summary

A substitute becomes a Teacher of engagement type Substitute: no salary, no placement, no login. The `substitution` module (017a) gets a
package `substitution.payout` with one **substitute day** record per live allocation, marked Present or Absent by the Zone Manager (or Admin
or Director), locked per substitute and month by Admin or Director. Payroll owns a port `payroll.api.DayRateWork` that `substitution`
implements; the 013b run gains a "Substitute" payroll person source: one line per substitute and month, earned = sum of day value times the
designation's daily rate on each date (rate history is new in 013a: `designation_day_rate`, `payroll.api.DayRates`). Reconciliation reads what
the runs hold through `payroll.api.DayRatePay` and shows every day's status, mismatches and "correction needed", with an Admin Correction
(013b) and a daily repair job. A go-live migration turns existing 017a credits into day records (Attended, migrated) and back-pays them:
open months in their run, approved months through 013b supplementary runs. The web app gets `features/substitute-payouts`, day marking in the
017a Substitutions screen, a designation day rate screen, and the Teacher form's engagement field. See [research.md](research.md) and
[contracts/amendments-to-other-specs.md](contracts/amendments-to-other-specs.md).

## Technical Context

- Java 25, Spring Boot, Spring Modulith, JPA, Flyway, PostgreSQL (Testcontainers), React + Vite + Vitest. No new library.
- Migrations (numbers to re-check): on `main` the highest is V150 (040); V130 incentives, V140 preferences. This spec plans V160 (`substitute_day`, `substitute_day_event`, `substitute_month_lock`) and V161 (`designation_day_rate`, `teacher.engagement`), or one file if the repo prefers. **Numbers must be re-checked against `origin/main` and open branches at implementation.**
- Amendments to merged specs: 005 (engagement), 013a (day rates), 013b (Substitute source, port, payslip), 017a (pool, day record, notifications, no Teacher page), 008 (exclude substitutes), identity (no account). Detail in contracts/amendments-to-other-specs.md.
- Money `BigDecimal`, two decimals, Rupees; rounding by the run's policy.
- Constraints: unique `(requirement_id, substitute_teacher_id)` day record; append-only day events and rate history; no cross-module foreign keys; audit through `ChangeRecorder`; 404 for out-of-scope; business dates through `Clock`.
- Scale: tens to low hundreds of substitute days a month; lists page with batch lookups.
- Job: one daily reconcile (virtual thread, Principle VIII), idempotent, advisory lock, row lock per day.

## Constitution Check

| Principle | How it is met |
| --- | --- |
| I Auditable | Marks, corrections of marks, lock, reopen (with reason), status changes, repairs, migration, rate changes and Corrections write events and Change History with old and new values and actor or "reconcile job". |
| II Fixed roles, RBAC | New module `SUBSTITUTE_PAYOUTS(VIEW, EDIT, APPROVE, EXPORT)`: Admin all four; Director all four; Manager VIEW and EDIT (own Zone). Teacher and System not eligible. Day rates edited under the 013a Salary Structures right. No new role. |
| III Data scope | Admin, Director org-wide; Zone Manager by the School's Zone (`ScopeQueries`); a Substitute has no access; else 404. |
| IV Role-based UI | Server-sent `actions` per row; one PAYROLL & FINANCE item requiring VIEW; mark buttons only where EDIT and scope allow; DD/MM/YYYY and Indian Rupee grouping; WCAG 2.2 AA. |
| V Formula-driven | Pay = day value times the designation's rate on each date, computed by payroll from stored attendance; nothing re-entered; margin and billing untouched. |
| VI Training and Substitution | Fulfils "substitution assignment and payout reconciliation". |
| VII Modular monolith | `substitution.payout` implements a payroll-owned port; `payroll` never depends on `substitution`; designation read through `designation.api`; `attendance` depends on neither (it only excludes the engagement type through `teacher.api`). Acyclic graph kept. |
| VIII Concurrency | Marks and lock take a row lock plus `version`; the job runs one at a time; payroll recompute reads the port inside its own transaction. |
| IX Testability | Unit (status derivation, pay calculation with rate change, half days), API per endpoint, role and scope, integration with a real run (compute, approve, change after approval, supplementary), migration and idempotency tests, regression for 013b, 015, 030, 017a, 008. |
| X Security | Server re-checks grant and scope; Substitute accounts impossible; reasons capped at 500; CSV neutralises formula cells; amounts never accepted from the client except the bounded Admin Correction. |

Constitution change: PATCH 2.5.17 (the next after 2.5.16 of spec 030; renumber to whichever lands first): Default role access matrix gains a
Substitute Payouts row; the Teachers entry notes that a Substitute-type Teacher has no account; navigation gains PAYROLL & FINANCE
"Substitute Payouts". No principle changes. Gate result: pass before research and after design, no violations.

## Project Structure

### Documentation (this feature)

```text
specs/017b-payout-reconciliation/
  spec.md  clarification-questions.md  plan.md  research.md  data-model.md  quickstart.md  tasks.md
  contracts/ payout-api.md  payroll-substitute-work-port.md  amendments-to-other-specs.md
  checklists/requirements.md
```

### Source Code

```text
backend/src/main/java/com/hls/substitution/payout/        (day record, marking, lock, reconcile, migration, CSV, controller)
backend/src/main/java/com/hls/payroll/api/                 (DayRateWork, DayRates, DayRatePay, CorrectionRaising)
backend/src/main/java/com/hls/payroll/run/internal/        (Substitute source, day-rate line, payslip rows, DayRates impl)
backend/src/main/java/com/hls/teacher/                     (engagement type; no account)
backend/src/main/resources/db/migration/V160, V161         (numbers to re-check)
frontend/src/features/substitute-payouts/, features/salary-structures (day rates), features/substitutions (mark day), features/teachers (type)
```

**Structure Decision**: day records stay in `substitution` (owner of allocation); payroll owns the pay rules and the port; the rate lives with the other pay rules in `payroll` (013a convention).
