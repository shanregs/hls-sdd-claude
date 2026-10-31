# Implementation Plan: Payroll Run

**Branch**: `013b-payroll-run` | **Date**: 2026-10-13 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/013b-payroll-run/spec.md`

## Summary

The monthly pay run: one place that turns locked attendance and spec 013a's salary rules into a net pay per person,
shows the working, takes it through approval, and then keeps it unchangeable.

- **Compute**: an Admin starts a run for an ended month. A background batch (one virtual thread per person, Principle
  VIII) asks the payroll module's own pay rules for each payroll person's salary rows, payable dates, divisor, unpaid
  days and policy, and stores one line per person with every figure it used (the frozen working). A person who cannot be
  paid (attendance month not locked, no joining date, no salary, no working days) becomes a line with a reason and no
  amounts; nobody blocks anybody.
- **Pay rules gain one method**: `PayRules.earnedPay(person, month)`, the mirror of `totalLossOfPay`, so the divisor of
  every day (including a Teacher who changed School mid-month) comes from one place and earned pay and the deduction can
  never disagree about it.
- **Review and approval**: draft, awaiting approval, approved, cancelled. The Admin recomputes a draft and sees what
  changed; the Director approves or returns with a reason; the submitter cannot approve. Approval stores payslips,
  marks adjustments applied and records the people paid, in one transaction.
- **Frozen by construction**: an approved run reads nothing live. Its lines and their working are stored; a database
  trigger refuses any change to the lines of an approved run; payslips are PDFs stored at approval.
- **Adjustments**: signed, reasoned amounts against a person, waiting until a run takes them. Kind `Correction` is
  usable now; `Incentive`, `Reimbursement` and `Substitute credit` exist as reserved values nothing creates.
- **Supplementary runs**: for the people not yet paid for a month, after the primary run is approved. A database key
  makes "a person is paid once per month" a fact, not a convention.
- **Payment, export, history**: manual payment details per line with a correction history; CSV of any run; per-person
  history across runs.
- **Screens**: OPERATIONS → Payroll for Admin and Director: runs list, run page (progress, lines, cannot-be-paid, owed),
  line working, adjustments, payment, payslip downloads, export.

Decisions already made in the spec session (2026-10-13): people left out are paid through a supplementary run; payslips
are visible to Admin and Director only.

## Technical Context

**Language/Version**: Java 25 (Spring Boot 4.1.1-based); TypeScript 5.7 with React 19. Unchanged.

**Primary Dependencies**: Spring Web, Data JPA, Security, Spring Modulith, Flyway, ArchUnit, Spring scheduling. Frontend:
React Router, MUI. **One new dependency: Apache PDFBox 3.0.x (Apache License 2.0)** for the payslip PDF; no PDF library
exists in the project today (research.md section 7). One font file (Noto Sans, SIL OFL) is bundled so the rupee sign
prints.

**Storage**: PostgreSQL through migration `V53` (highest on main is `V52`). Payslip PDFs go into the shared `files`
module (type pdf, at most 10 MB, `FileStore`). Details in [data-model.md](./data-model.md).

**Testing**: JUnit 5, Spring Boot Test, Testcontainers (`IntegrationTestBase`). The new arithmetic
(`PayRules.earnedPay`, the net-pay assembly) is tested first against hand-computed tables covering a full month, a
joiner, an exit, a mid-month raise, a half day, Loss-of-Pay leave, a Manager with unpaid days and a Director (SC-001).
Per-role tests on every endpoint; the freezing tests (holiday, reopened month, backdated salary, policy change after
approval, SC-003); the supplementary-run and exactly-once tests; submit/approve separation; the trigger test; the
payslip and CSV tests; ArchUnit (`PayrollRunModuleRulesTest`); frontend Vitest per role with axe in both themes.

**Target Platform**: Browser (web screens). No mobile in this spec.

**Project Type**: Web application (frontend + backend), single Spring Boot deployable.

**Performance Goals**: a run for 1,000 people is computed in under 2 minutes (SC-002) while other screens stay
responsive; the run page loads 1,000 lines in a fixed number of queries; the working of one line opens in under 500 ms;
approval of a 1,000-person run, including payslips, finishes in under 60 seconds.

**Constraints**: backend refuses everything outside the caller's permission and fails closed; the actor always comes from
the JWT; money is `BigDecimal` with fixed scale and never a floating-point value; an approved run is immutable at the
database as well as in the services; every state change is audited in the same transaction; the batch is safe if the
node restarts mid-run (research.md section 3).

**Scale/Scope**: up to about 1,000 payroll people (the real size is tens to low hundreds); about 22 endpoints; 4 screens
and 3 dialogs; 8 new tables; 1 new library.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Auditable** - PASS. Start, recompute, submit, return, approve, cancel, each adjustment recorded or withdrawn, each
  payment entry or correction, each export and the issue of payslips write an audit entry in the same transaction. A run
  keeps an event history with reasons; payment corrections are append-only.
- **II. Five fixed roles, configurable permissions** - PASS. No new role. One permission module `PAYROLL` with actions
  `VIEW`, `PROCESS`, `APPROVE`, `EXPORT` (all four are existing actions), seeded and runtime-editable, eligible for Admin
  and Director only. Defaults: Admin View, Process, Export; Director View, Approve, Export.
- **III. Data scope** - PASS. Org-wide for the two roles that hold it; nobody else has access at all (no Own scope,
  because a person's own pay view is out of scope).
- **IV. Role-based experience** - PASS. Server-driven menu item OPERATIONS → Payroll; actions hidden by permission;
  loading, empty, error and "computing" states; rupees with Indian digit grouping; DD/MM/YYYY; WCAG 2.2 AA, tested in both
  themes; the working screen reads without leaving the page (SC-005).
- **V. Payroll is formula-driven** - PASS. Every figure is derived from rules and records; the only typed money is an
  adjustment, which carries a kind and a reason. Nothing is typed per month per person.
- **VI. Unaffected**.
- **VII. Modular monolith** - PASS with one additive interface. The run lives in `payroll` (`payroll.run` internal
  packages) and reaches other modules only through their `api` packages: `attendance.api` (`AttendanceReadApi.isLocked`,
  `ManagerAttendanceReadApi`), `files.api` (`FileStore`), `notification` and `audit` through events. `PayRules` gains
  `earnedPay` in `payroll.api`. Nothing reads another module's tables. ArchUnit verifies.
- **VIII. Concurrency** - PASS and is the reason for the design. The batch uses `Executors.newVirtualThreadPerTaskExecutor()`
  with a semaphore sized to the connection pool; one task per person; each task is read-only and returns an immutable
  result; the coordinating thread alone writes. No hand-rolled thread pool. Request-path code stays on Spring's default
  model. Payslip rendering at approval uses the same executor.
- **IX. Reliability, testability, financial review** - PASS by design, and this spec is exactly the case the principle
  names. The formulas are reviewed at the end: [quickstart.md](./quickstart.md) section "Financial review" lists the
  points the payroll owner must confirm (earned-pay formula, rounding direction of earned pay, divisor for a Teacher who
  changed School). Tests first for all arithmetic.
- **X. Security** - PASS. Pay is personal and sensitive: endpoints are authorised server-side per action; payslips and
  CSV are served only with the permission; the CSV writer neutralises spreadsheet formulas in text cells; the reason and
  reference fields are length-limited plain text; payslip PDFs are generated from stored data, never from a request
  parameter; salary figures are never returned to a caller without `PAYROLL.VIEW`; the audit trail records every export.
- **XI. Unaffected**.

No violations requiring justification. The one new dependency is recorded under Technical Context and research.md
section 7. Complexity Tracking is not needed.

**Post-design re-check (after data-model.md and contracts)**: unchanged, all PASS. Two design risks are named and
handled: a run that fails or the node restarting mid-compute (research.md section 3), and the approval transaction that
must store up to 1,000 payslips atomically (research.md section 8).

## Project Structure

### Documentation (this feature)

```text
specs/013b-payroll-run/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   ├── payroll-run-api.md          # HTTP, for the screens
│   └── pay-rules-earned-pay.md     # the one addition to the PayRules interface, and the events
├── checklists/requirements.md
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/src/main/java/com/hls/
├── identity/
│   ├── permissions/PermissionModule.java       # + PAYROLL(VIEW, PROCESS, APPROVE, EXPORT)
│   ├── permissions/PermissionEligibility.java  # Admin and Director only
│   ├── permissions/PermissionMatrixService.java    # seeds
│   └── accessmodel/{NavigationCatalog,AccessModelService}.java  # + OPERATIONS → Payroll, org-wide scope
├── audit/ (AuditVisibility)                    # + PAYROLL_RUN, PAYROLL_ADJUSTMENT, PAYROLL_PAYMENT, PAYROLL_EXPORT → PAYROLL.VIEW
├── payroll/
│   ├── api/                                    # PayRules + earnedPay, EarnedPay (sealed), PayrollRunSubmitted,
│   │                                           #   PayrollRunApproved, PayrollRunReturned
│   ├── internal/PayRulesImpl.java              # + earnedPay (same Computed as totalLossOfPay)
│   └── run/
│       ├── internal/                           # PayRun, PayRunLine, LineAdjustment, PayAdjustment, Payslip,
│       │                                       #   PaymentEntry, PaidMonth, RunEvent (+ repositories);
│       │                                       #   RunService (start, cancel, submit, return, approve),
│       │                                       #   RunComputation (the batch), LineCalculator (pure net-pay assembly),
│       │                                       #   RecomputeDiff, AdjustmentService, PaymentService, OwedService,
│       │                                       #   PayslipRenderer (PDFBox), PayslipService, RunCsvWriter,
│       │                                       #   PersonHistoryService, PayrollRunDevSeeder
│       └── web/                                # PayRunController, PayRunLineController, PayAdjustmentController,
│                                               #   PayPaymentController, PayslipController, PayrollHistoryController
├── notification/internal/PayrollRunEventListener.java   # + types in NotificationType
└── (no change to attendance, leave, files)

backend/src/main/resources/
├── db/migration/V53__create_payroll_run.sql
└── fonts/NotoSans-Regular.ttf, NotoSans-Bold.ttf        # payslip font (OFL)

backend/pom.xml                                 # + org.apache.pdfbox:pdfbox 3.0.x

backend/src/test/java/com/hls/payroll/run/      # calculator, earnedPay, run lifecycle, freezing, adjustments,
                                                #   supplementary, payment, payslip, csv, trigger, authorization, audit,
                                                #   concurrency, module rules
backend/src/test/java/com/hls/payroll/PayRulesEarnedPayTest.java

frontend/src/features/payroll/run/
├── payrollRunApi.ts
├── PayrollRunsPage.tsx             # runs list, start a run (primary or supplementary)
├── PayrollRunPage.tsx              # header, progress, totals, lines, cannot-be-paid, owed, actions
├── RunLineWorkingDialog.tsx        # the frozen working of one line, payslip download
├── AdjustmentsPage.tsx             # record, list, withdraw; reserved kinds shown disabled
├── PaymentDialog.tsx               # paid, date, mode, reference, history
├── PersonPayrollHistory.tsx        # a person's runs across months
├── ReturnRunDialog.tsx, CancelRunDialog.tsx
└── __tests__/
frontend/src/App.tsx                # + guarded routes /operations/payroll, /operations/payroll/:runId, /operations/payroll/adjustments

postman/HLS API/013b Payroll Run/   # new folder
docs/spec-roadmap.md                # 013b row status; constitution default-access rows for Payroll (patch note)
```

**Structure Decision**: Web application (unchanged layout). The run is a sub-package of the existing `payroll` module,
because the pay rules, the policy and the salary rows it needs are `payroll`-internal and must not be re-exported to
another module just to be read once (Principle VII). Reads of attendance go through `attendance.api`; nothing else is
new at module level.

### Delivery slices (for tasks.md, each independently demoable)

1. **Foundation**: permission module, navigation, migration `V53` with the immutability trigger, `PayRules.earnedPay`
   with hand-computed tests, the pure `LineCalculator`.
2. **Prepare the run (US1)**: start, the background batch, progress, lines, cannot-be-paid, recompute with diff, cancel.
3. **Review and approve (US2) and freezing (US3)**: working screen, submit, return, approve, separation of duties, the
   freezing tests, payslip issue at approval (PDF engine included, since approval issues payslips).
4. **Adjustments (US4)**.
5. **Supplementary runs and owed (US5)**.
6. **Payslips download (US6)**, **payment (US7)**, **export (US8)**, person history.
7. **Polish**: dev seed, Postman, accessibility, roadmap, constitution rows, the financial review and quickstart results.

## Complexity Tracking

No violations. Table intentionally omitted.

## Amendments

- **Amendment A (spec 030, Manager incentives).** Additive: `payroll.api.IncentiveIntake` (`offer`, `withdraw`, `status`,
  `OfferStatus`) and `IncentiveIntakeImpl` in `payroll.run.internal`, the same shape as 015's `ReimbursementIntake`. It
  creates and withdraws `INCENTIVE` adjustments for a payroll Manager, idempotent per source reference
  (`INCENTIVE:<incentive id>`); a draft run takes them, approval applies them and publishes `PayrollAdjustmentApplied`
  with kind `INCENTIVE`. The status derivation is shared with the reimbursement intake (`IntakeStatus`), with no change
  to 015. `AdjustmentService` still refuses manual recording of `INCENTIVE`, and its refusal now says the kind arrives
  only through the incentive intake. No migration (V53 already allows the kind and the source reference). Run
  behaviour for runs without incentives is unchanged.
