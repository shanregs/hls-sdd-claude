# Implementation Plan: Manager Incentives

**Branch**: `spec/030-manager-incentives` | **Date**: 2026-10-27 | **Spec**: [spec.md](spec.md)

## Summary

A new backend module `incentive` (Spring Modulith) creates one incentive per School when the School's MoU is approved
(`schoolbilling.api.MouDecided`, state read through `MouApprovals`), for the owner of the won prospect
(`recruitment.api.ProspectOwners`) when that owner is a payroll Manager. The Director or the School's Zone Manager approves or
rejects it. Approval offers it to payroll through a new `payroll.api.IncentiveIntake` (an additive amendment A to 013b, same
pattern as 015's `ReimbursementIntake`, reusing the adjustment kind `INCENTIVE` that `V53` already allows and the event
`PayrollAdjustmentApplied`). A listener on that event marks the incentive "paid in run". A MoU that stops counting as approved
(`MouSubmitted` with `resubmitted`, or a rejection) holds the incentive by withdrawing the offer. A daily reconcile job repairs
missed events. The web app gets a feature `features/incentives` (list, detail, my incentives, no-earner list) and one OPERATIONS item.
See [research.md](research.md).

Read from the code, not from the planning documents (research.md section 1): `MouDecided` carries no approval date, and a MoU
shown as "approved (before approval step)" has none either; `AdjustmentService` refuses the kind `INCENTIVE` for manual
recording and must keep doing so; payroll cannot read MoU state, so "hold" has to be done by withdrawing the offer before a run
takes it; and a run awaiting approval cannot release an adjustment, so a hold that arrives then waits for the run.

## Technical Context

- Java 25, Spring Boot, Spring Modulith, JPA, Flyway, PostgreSQL (Testcontainers), React + Vite + Vitest.
- Migration: `V130__create_incentives.sql`. On `main` the highest is V120 (022); V95 is 029, V100 031, V110 024, V90 015, V80 027,
  V70, V60. V130 is the next decade slot; re-check against merged branches at implementation. No payroll migration: `V53` already
  allows `INCENTIVE` and has `source_ref`.
- Payroll amendment A (013b): `IncentiveIntakeImpl` in `payroll.run.internal`, `payroll.api.IncentiveIntake`; a small change in
  `AdjustmentService` so manual recording of `INCENTIVE` stays refused; the payslip label already exists (`PayslipRenderer`).
- Money: `BigDecimal`, two decimals, Rupees; amount frozen on the incentive (`amount` column).
- Setting: new `SettingKey.INCENTIVE_AMOUNT` (`incentive.amount-per-school`, group SYSTEM, default 5000, range 0 to 100000).
- Constraints: one incentive per School (unique `school_id`), append-only `incentive_event`, no cross-module foreign keys, audit
  through `ChangeRecorder`, 404 for anything out of scope, one decision per incentive (row lock plus version), business dates through `Clock`.
- Scale: a few hundred Schools a year; lists page with batch lookups (names, Zone Managers) not per-row queries.
- Job: one daily reconcile job (events can be missed); it is idempotent.

## Constitution Check

| Principle | How it is met |
| --- | --- |
| I Auditable | Earn, approve, reject, hold, release, apply and no-earner records write `incentive_event` (append-only trigger) and Change History through `ChangeRecorder` with old and new values; approver and earner both named, plus the self-approved flag. |
| II Five roles, RBAC | New module `MANAGER_INCENTIVES(VIEW, APPROVE, EXPORT)`. Seeded Admin `VIEW, EXPORT`; Director `VIEW, APPROVE, EXPORT`; Manager `VIEW, APPROVE`. `PermissionEligibility`: `APPROVE` not eligible for Admin (D5); nothing eligible for Teacher or System. |
| III Data scope | Admin and Director org-wide. A Manager sees own incentives and, as Zone Manager, those of Schools in their Zone (`ScopeQueries`); approval only in the Zone. Anything else answers 404. |
| IV Role-based UI | A server-sent `actions` list on each row; one OPERATIONS item "Incentives" (requires `VIEW`); a Director approvals-inbox entry; buttons follow the grants. |
| V Formula-driven | Amount comes from a setting and is frozen per incentive; the pay run's net pay is still computed by payroll; 030 only supplies a signed amount. No pay rule is duplicated. |
| VI Training and substitution | Not touched. |
| VII Modular monolith | New module `incentive` depends only on `schoolbilling.api`, `recruitment.api`, `organization.api`, `payroll.api`, `school.api` (audit), `settings` and `notification` events. `payroll` gains an api interface, no dependency on `incentive`. `reporting` reads a new `incentive.api.IncentiveSummary`. No cycle. |
| VIII Concurrency | Request path plus one daily job. Decision takes a pessimistic row lock and checks `version`; a second decider gets 409 naming the first. Offer is idempotent per source reference, so retries and the job cannot double pay. Virtual threads for the job as elsewhere. |
| IX Testability | Unit tests for earning rules and state machine; API tests per endpoint, role and scope (SC-002, SC-004); integration tests for the pay run flow (SC-003, SC-007) and duplicate events (SC-001); regression on 013b and 015 intakes. |
| X Security | Server re-checks grant and scope on each call; free text capped (reason 500); no file upload; CSV neutralises formula cells; the amount cannot be sent by the client. |
| XI Recruitment and marketing tracked to outcome | Strengthens it: a closed School is tracked to the Manager's reward. 023 is not changed. |

Constitution change: PATCH 2.5.15 (the next patch after 2.5.14 of spec 022 on main; renumber to whichever of the parallel specs
merges first). The Default role access matrix gains an Incentives row and OPERATIONS navigation gains "Incentives". No principle changes.
Gate result: pass before research and after design, no violations.

## Project Structure

### Documentation

```text
specs/030-manager-incentives/
  spec.md  plan.md  research.md  data-model.md  quickstart.md  tasks.md
  clarification-questions.md  checklists/requirements.md
  contracts/ incentive-api.md  module-interfaces.md  payroll-incentive-intake.md
```

### Source (backend)

```text
backend/src/main/java/com/hls/incentive/
  api/        IncentiveSummary (read for 028/014), IncentiveEarned (event, for notification)
  internal/   Incentive, NoEarnerRecord, IncentiveEvent (entities); repositories;
              EarningService, DecisionService, PayrollLink, HoldService, ReconcileJob,
              IncentiveQueries, IncentiveController, IncentiveCsv, listeners
backend/src/main/java/com/hls/payroll/api/IncentiveIntake.java            (amendment A to 013b)
backend/src/main/java/com/hls/payroll/run/internal/IncentiveIntakeImpl.java
backend/src/main/resources/db/migration/V130__create_incentives.sql
```

Frontend: `frontend/src/features/incentives/` (list, detail drawer, my incentives, no-earner tab), router and navigation entries.

## Phases

- **Phase 0** research.md: decisions on events versus reads, hold mechanism, closure date, legacy MoUs, month rule.
- **Phase 1** data-model.md, contracts, quickstart.md.

## Complexity Tracking

None. The only cross-spec change is the additive payroll intake (amendment A); no existing behaviour changes.
