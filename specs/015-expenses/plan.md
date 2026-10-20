# Implementation Plan: Expenses

**Branch**: `015-expenses` | **Date**: 2026-10-20 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/015-expenses/spec.md`

## Summary

Office expenses as a workflow with a bill on every claim, one approval step, a defined route to payment, and the
budget and cost views the Director asked for.

- **New module `expenses`** (`expenses.api`, `expenses.internal`, `expenses.web`). It owns claims, bills (links to the
  shared file store), the claim history, area budgets and their change history. It reads other modules only through
  their `api` packages.
- **Claim lifecycle**: Draft, Submitted, Approved, Rejected, Paid (with payroll, or recorded outside), Paid by company.
  The claimant edits only Draft and Rejected; a decision is a conditional update on the status plus `@Version`, so the
  second of two approvers is told who decided first (FR-016). Nobody decides, rejects or reverses their own claim
  (FR-015), enforced in the service, not the screen.
- **Bills** are kept in the shared `files` module (`FileStore`) under owner type `EXPENSE_CLAIM`. `expenses` allows
  jpg, png and pdf only (the store also accepts docx, xlsx and txt), counts at most 5 live bills, and records removals
  with a reason.
- **One submission, never two (FR-009)**: the claimant's client sends a `clientRequestId`; a unique key on
  (claimant, clientRequestId) makes a retry return the first claim. This is what spec 036's offline sync relies on.
- **Payment through payroll without a second typing**: when a personally paid claim is approved, `expenses` records a
  waiting `REIMBURSEMENT` adjustment in payroll through a **new additive interface in `payroll.api`** (the current
  `AdjustmentService` is internal and rejects the reserved kind). Payroll's existing attach, detach and apply behaviour
  then gives exactly the spec's rules: a cancelled draft run releases the amount, an approved run settles it. Payroll
  publishes a small per-adjustment event on approval so the claim becomes "Paid with payroll". This is a documented,
  additive amendment to 013b (see [contracts/payroll-reimbursement.md](./contracts/payroll-reimbursement.md)).
- **Budgets and measures** are computed on read from the claims: budget per area per calendar month, actual by expense
  date, pending, remaining, over marker; ageing buckets; cost per School acquired and cost per Teacher trained with the
  claims behind them. Two small additive read interfaces are needed in other modules (won Schools in a period; completed
  Teachers of one batch).
- **Exposed interface (FR-026)**: `expenses.api` gives payroll, reports (014), the dashboard (028) and the Android app
  (036) one source for owed claims, approved spend, pending counts and cost measures.
- **Screens**: OPERATIONS → Expenses (Admin, Director: review queue, claim page, budget versus actual, pending and
  ageing, cost per School and per Teacher, record payment, export) and OPERATIONS → My Expenses (all four people roles).

## Technical Context

**Language/Version**: Java 25 (Spring Boot 4.1.1-based); TypeScript 5.7 with React 19. Unchanged.

**Primary Dependencies**: Spring Web, Data JPA, Security, Spring Modulith, Flyway, ArchUnit. Frontend: React Router, MUI.
**No new dependency.**

**Storage**: PostgreSQL through migration `V54` (highest on main is `V53`). Bills in the shared `files` module
(`FileStore`, 10 MB each). Details in [data-model.md](./data-model.md).

**Testing**: JUnit 5, Spring Boot Test, Testcontainers (`IntegrationTestBase`). Arithmetic first, against hand-computed
tables: budget versus actual (claims in every status, an area with no budget, Head office, a rejected-then-approved
claim, SC-006), ageing buckets, cost per School and per Teacher with zero divisors (SC-007). Per-role tests on every
endpoint (SC-008); self-approval including a user holding both roles (SC-004); double-decision race; the run, cancel and
re-run payroll test proving a claim is in exactly one run or paid outside, never both (SC-005); immutability after paid
(SC-009); retry creating one claim (SC-010); ArchUnit (`ExpensesModuleRulesTest`); frontend Vitest per role with axe in
both themes.

**Target Platform**: Browser (web screens). The server contract is shaped for the Android app (spec 036); no mobile
screens here.

**Project Type**: Web application (frontend + backend), single Spring Boot deployable.

**Performance Goals**: review queue and My Expenses lists page in under 1 second at 5,000 claims; budget versus actual
for a month, all areas, under 1 second (aggregate queries, no per-claim loads); detail with bills opens in under 500 ms.

**Constraints**: backend refuses everything outside the caller's permission and fails closed; the actor always comes
from the JWT; money is `BigDecimal` scale 2, never floating point, and positive on a claim; a paid claim is immutable at
the service and by a database trigger on the money and identity columns; every state change, bill change, budget change
and export is audited in the same transaction; amounts and dates are never taken from the client for a decision.

**Scale/Scope**: tens of claimants, a few thousand claims a year; about 24 endpoints; 6 screens and 4 dialogs; 5 new
tables; 3 additive interfaces in other modules.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Auditable** - PASS. Create, edit, submit, withdraw, decide, reverse, bill add and removal, payment, budget change
  and export all write an audit entry (`ChangeRecorder`) in the same transaction; claim history is append-only.
- **II. Five fixed roles, configurable permissions** - PASS. No new role. Two new permission modules: `EXPENSES` (VIEW,
  CREATE, EDIT, APPROVE, PROCESS, EXPORT; Admin and Director only) and `MY_EXPENSES` (VIEW, CREATE, EDIT, DELETE;
  Admin, Director, Manager, Teacher). All actions already exist. System is not eligible for either. Seeded as the spec's
  table and editable at runtime.
- **III. Data scope** - PASS. Claimants reach only their own claims (Own); Admin and Director are org-wide on `EXPENSES`.
  Out-of-scope claims answer "not found". The spec's decision that Zone Managers do not see Zone spend is kept.
- **IV. Role-based experience** - PASS. Server-driven menu items; every claim is returned with the actions allowed for the
  viewer (FR-031); loading, empty and error states; rupees with Indian digit grouping; DD/MM/YYYY; WCAG 2.2 AA tested in
  both themes; phone-width layout.
- **V. Payroll is formula-driven** - PASS. Payroll receives a reimbursement only as a reasoned, sourced adjustment created
  by an approval, never typed again; amount is the approved claim amount.
- **VI. Unaffected**.
- **VII. Modular monolith** - PASS with additive interfaces named in the plan: `payroll.api.ReimbursementIntake` and an
  applied event; `recruitment.api` won-Schools read; `training.api` completed Teachers of one batch. `expenses` reads
  `files.api`, `school.api` (zones, `ChangeRecorder`, `CallerContext`), `recruitment.api`/`tasks.api` planned activities
  (C1), `training.api.InductionBatches`, `notification` and `audit` through events. Nothing reads another module's tables.
  ArchUnit verifies.
- **VIII. Concurrency** - PASS. No background batch. Two approvers on one claim are serialised by `@Version` and a status
  guard; budget views are read-only aggregates. Request-path code stays on Spring's default model.
- **IX. Reliability, testability, financial review** - PASS by design. This spec moves money to payroll, so a financial
  review is part of done: [quickstart.md](./quickstart.md) section "Financial review" lists the points the owner must
  confirm (what counts as actual, which statuses count as pending, cost measure definitions, reimbursement exactly once).
  Tests first for all arithmetic.
- **X. Security** - PASS. Bills are served only after the claim's access check; type checked by extension and content;
  CSV writer neutralises spreadsheet formulas; free text is length-limited plain text; `clientRequestId` cannot be used to
  read another claimant's claim; no amount or name beyond the caller's scope is returned.
- **XI. Unaffected**.

No violations requiring justification. Complexity Tracking is not needed.

**Post-design re-check (after data-model.md and contracts)**: unchanged, all PASS. Risks named and handled: the payroll
amendment must be accepted by 013b without changing frozen runs (research.md section 3); "became active" is read as
"won" (research.md section 6).

## Project Structure

### Documentation (this feature)

```text
specs/015-expenses/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   ├── expenses-api.md              # HTTP, for the screens and the Android app
│   └── payroll-reimbursement.md     # the additive interface and event in payroll, and expenses.api
├── checklists/requirements.md
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/src/main/java/com/hls/
├── identity/
│   ├── permissions/PermissionModule.java       # + EXPENSES(...), MY_EXPENSES(...)
│   ├── permissions/PermissionEligibility.java  # EXPENSES: Admin, Director; MY_EXPENSES: not System
│   ├── permissions/PermissionMatrixService.java    # seedExpenses()
│   └── accessmodel/{NavigationCatalog,AccessModelService}.java  # + OPERATIONS → Expenses, My Expenses; scopes
├── audit/ (AuditVisibility)                    # + EXPENSE_CLAIM, EXPENSE_BUDGET, EXPENSE_EXPORT → EXPENSES.VIEW
├── expenses/
│   ├── api/                                    # ExpenseQueries (owed, spend, pending, cost measures), ExpenseSubmitted,
│   │                                           #   ExpenseDecided (events), OwedClaim, SpendRow, CostMeasure
│   ├── internal/                               # ExpenseClaim, ExpenseBill, ClaimEvent, AreaBudget, BudgetChange (+ repos);
│   │                                           #   ClaimService (create, edit, submit, withdraw, resubmit), DecisionService
│   │                                           #   (approve, reject, reverse), PaymentService (record paid, settle with payroll),
│   │                                           #   BillService, LinkResolver (C1 + batch), BudgetService, BudgetQueries,
│   │                                           #   PendingQueries, CostMeasureService, ClaimCsvWriter, ExpenseQueriesImpl,
│   │                                           #   PayrollSettlementListener, ExpensesDevSeeder
│   └── web/                                    # MyExpensesController, ExpenseReviewController, ExpenseBudgetController,
│                                               #   ExpenseReportController, ExpensesExceptionAdvice
├── payroll/api/                                # + ReimbursementIntake, PayrollAdjustmentApplied (additive)
├── payroll/run/internal/                       # ReimbursementIntakeImpl (uses AdjustmentService/PayAdjustment);
│                                               #   ApprovalService publishes PayrollAdjustmentApplied
├── recruitment/api/WonSchools.java             # additive: Schools won in a period (reads Prospect.wonAt)
├── training/api/InductionBatches.java          # additive: completedTeachers(batchId)
└── notification/                               # + EXPENSE_SUBMITTED, EXPENSE_DECIDED, EXPENSE_REVERSED types,
                                                #   MessageFactory texts, ExpenseEventListener

backend/src/main/resources/db/migration/V54__create_expenses.sql

backend/src/test/java/com/hls/expenses/         # claims, links, decision, payment/payroll, budget, ageing, cost,
                                                #   csv, authorization, audit and notification, immutability, retry,
                                                #   module rules
backend/src/test/java/com/hls/support/ExpensesTestBase.java

frontend/src/features/expenses/
├── expensesApi.ts
├── MyExpensesPage.tsx              # list, owed total, filters
├── ClaimFormPage.tsx               # create/edit, bills, link, duplicate warning
├── ClaimDetailPage.tsx             # claim, bills, link, history, allowed actions
├── ExpensesReviewPage.tsx          # review queue, ageing summary
├── BudgetVersusActualPage.tsx      # month, areas, drill-down, set budget
├── CostMeasuresPage.tsx            # per School acquired, per Teacher trained
├── DecisionDialog.tsx, ReverseDialog.tsx, RecordPaidDialog.tsx, RemoveBillDialog.tsx
└── __tests__/
frontend/src/App.tsx                # + guarded routes /operations/expenses/*, /operations/my-expenses/*

postman/HLS API/015 Expenses/       # new folder; postman/README.md updated
docs/spec-roadmap.md                # 015 row; constitution default-access rows for Expenses (patch note)
specs/013b-payroll-run/             # amendment note: 015 reimbursement intake (additive)
```

**Structure Decision**: Web application (unchanged layout). Expenses is its own module because it has its own entities,
screens and permission keys and is read by four other specs (014, 028, 036, 013b). It does not live in `payroll`:
payroll must not own claims, only receive the amounts they produce.

### Delivery slices (for tasks.md, each independently demoable)

1. **Foundation**: permission modules and eligibility, navigation, migration `V54` with the immutability trigger,
   exceptions, `ExpensesTestBase`.
2. **Raise a claim with its bill (US1, US7)**: create, draft, submit, withdraw, bills, validation, duplicate warning,
   retry safety, My Expenses with the owed total.
3. **Links (US2)**: planned-activity and induction-batch links, snapshot kept.
4. **Review (US3)**: queue, approve, reject, resubmit, self-decision rule, double-decision race.
5. **Payment (US4)**: payroll intake and event (additive, with a 013b amendment note), settle, record paid outside, paid
   by company, reversal.
6. **Budget versus actual (US5)**, **pending and ageing, cost measures, export (US6)**, the exposed `ExpenseQueries`.
7. **Polish**: dev seed, Postman, accessibility, roadmap, constitution rows, the financial review and quickstart results.

## Complexity Tracking

No violations. Table intentionally omitted.
