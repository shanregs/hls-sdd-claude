---

description: "Task list for feature implementation"
---

# Tasks: Payroll Run

**Input**: Design documents from `/specs/013b-payroll-run/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/payroll-run-api.md,
contracts/pay-rules-earned-pay.md, quickstart.md, and **specs 008, 009, 013a, 023 (the `files` module) and 032
implemented and merged** (they are on `main`).

**Tests**: included as first-class tasks. Constitution Principle IX requires per-role tests on every endpoint, UI tests per
role, unit tests before calculation logic, and a financial review for anything that changes pay. The arithmetic
(`PayRules.earnedPay`, `LineCalculator`) is tested before it is written, against hand-computed tables.

**Organization**: grouped by user story (spec.md US1-US8). Order follows the dependencies: Foundation, US1 (prepare), US2
(review and approve) with US3 (freezing), US4 (adjustments), US5 (supplementary runs), US6 (payslip downloads), US7
(payment), US8 (export), Polish. US2 includes issuing payslips at approval, because approval issues them.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1-US8; absent for Setup/Foundational/Polish

## Path Conventions

Backend `backend/src/main/java/com/hls/payroll/{api,internal,run/internal,run/web}/...`, tests under
`backend/src/test/java/com/hls/payroll/...`; migration `backend/src/main/resources/db/migration`; frontend
`frontend/src/features/payroll/run/...`. Run one Maven test class at a time (`mvn -o test -Dtest=...`); the machine is
slow. Maven lives in the scratchpad if not on the path; Docker must be running for integration tests.

---

## Phase 1: Setup

- [X] T001 Verify the prerequisites before writing code, and STOP and report if any is missing (do not stub): in `backend/src/main/java/com/hls/` confirm `payroll.api.PayRules` (methods `payableDays` bulk, `salariesOn` bulk, `unpaidDays`, `totalLossOfPay`, `policyOn`), `payroll.internal.PersonDirectory` (`allTeachers`, `allManagers`, `allStaff`), `attendance.api.AttendanceReadApi.isLocked`, `attendance.api.ManagerAttendanceReadApi.months`, `files.api.FileStore` (accepts `pdf`, 10 MB), `notification.internal.RecipientResolver`, and the highest migration number (V52 on 2026-10-13, so V53; re-check open pull requests for one that claims V53 and renumber at merge time). Also answer by a throw-away test the open point of research.md section 4: what `payableDays` returns for a Teacher with no joining date and for a Manager with no joining date. Record the names and the answers in `specs/013b-payroll-run/research.md` section 4
- [X] T002 Add the PDF dependency: `org.apache.pdfbox:pdfbox` (latest 3.0.x release, Apache License 2.0; confirm the version and licence from the project's release notes before pinning) to `backend/pom.xml`; add `backend/src/main/resources/fonts/NotoSans-Regular.ttf` and `NotoSans-Bold.ttf` (SIL Open Font License, with the licence text beside them in `backend/src/main/resources/fonts/OFL.txt`); a smoke test `backend/src/test/java/com/hls/payroll/run/PdfSmokeTest.java` renders a line containing the rupee sign (U+20B9) and an Indian-grouped amount such as `₹1,25,000.00` and asserts the PDF opens and contains the text. If the font cannot print the rupee sign, STOP and report
- [X] T003 Create the package skeletons `backend/src/main/java/com/hls/payroll/run/{internal,web}/` with `package-info.java`, and `backend/src/test/java/com/hls/payroll/run/PayrollRunModuleRulesTest.java` (ArchUnit: `payroll.run` uses only the `api` packages of `attendance`, `files`, `audit`, `notification`, `identity`; nothing outside `payroll` uses `payroll.run.internal` or `payroll.run.web`; no module reads the `payroll_*` run tables or the run entities; `payroll.run` does not use another module's `internal`)
- [X] T004 [P] Create `frontend/src/features/payroll/run/payrollRunApi.ts` with types and calls for every endpoint in contracts/payroll-run-api.md (money as decimal strings, months as `yyyy-MM`, dates as ISO strings)

---

## Phase 2: Foundational (blocking prerequisites)

- [X] T005 Write `backend/src/main/resources/db/migration/V53__create_payroll_run.sql` per data-model.md, quoting its constraints exactly. `payroll_run`: month date (first day, before the current business month), kind text check in ('PRIMARY','SUPPLEMENTARY'), sequence int, status check in ('DRAFT','AWAITING_APPROVAL','APPROVED','CANCELLED'), compute_state check in ('QUEUED','RUNNING','DONE','FAILED'), progress_done and progress_total int, heartbeat_at, policy jsonb, total_earned, total_deduction, total_adjustments and total_net numeric(16,2), payable_count and cannot_pay_count int, recompute_summary jsonb, started_by/at, submitted_by/at, approved_by/at, cancelled_by/at/cancel_reason, version bigint; partial unique index on (month) where kind = 'PRIMARY' and status <> 'CANCELLED'; partial unique index on (month) where kind = 'SUPPLEMENTARY' and status in ('DRAFT','AWAITING_APPROVAL'); unique (month, kind, sequence); check approved_by is distinct from submitted_by; check approved status implies approved_by and approved_at. `payroll_run_line`: run_id FK, person_kind check in ('TEACHER','MANAGER','STAFF'), person_id, person_name, person_label, state check in ('PAYABLE','CANNOT_PAY'), reason (required when CANNOT_PAY), payable_days int, working_days numeric(6,2), unpaid_days numeric(6,2), salary, earned, deduction, adjustments, net numeric(14,2), working jsonb, fingerprint, warning, computed_at; unique (run_id, person_kind, person_id); check net = earned - deduction + adjustments; check CANNOT_PAY implies the amounts are null; indexes (run_id, state) and (person_kind, person_id). `payroll_run_line_adjustment`, `payroll_adjustment` (kind check in ('CORRECTION','INCENTIVE','REIMBURSEMENT','SUBSTITUTE_CREDIT'), amount non-zero numeric(14,2), reason varchar(500) not null, status check in ('WAITING','APPLIED','WITHDRAWN'), attached_run_id, applied_run_id, applied_line_id, source_ref), `payroll_run_event`, `payroll_payslip` (line_id unique), `payroll_payment_entry` (mode check in ('BANK_TRANSFER','CASH','UPI','CHEQUE','OTHER'), reference varchar(100)), `payroll_paid_month` (primary key (month, person_kind, person_id)). Triggers: lines and line adjustments refuse UPDATE and DELETE when the run is APPROVED; `payroll_run` refuses UPDATE when the old status is APPROVED and refuses DELETE unless DRAFT or CANCELLED; `payroll_run_event`, `payroll_payslip`, `payroll_payment_entry` refuse UPDATE and DELETE; `payroll_adjustment` refuses DELETE and refuses UPDATE of an APPLIED row
- [X] T006 [P] Add the permission module `PAYROLL(VIEW, PROCESS, APPROVE, EXPORT)` to `identity/permissions/PermissionModule.java`; in `PermissionEligibility` make it Admin and Director only and add it to the business-module set so System, Manager and Teacher cannot be granted it; seed in `PermissionMatrixService`: Admin gets View, Process, Export; Director gets View, Approve, Export
- [X] T007 [P] Add navigation to `identity/accessmodel/NavigationCatalog.java`: OPERATIONS → "Payroll" `/operations/payroll` (`PAYROLL` VIEW) for Admin and Director, ordered after Salary Structures (next free order number); add the org-wide data scope in `AccessModelService`
- [X] T008 [P] Extend `PermissionEligibilityTest` and the navigation and access-model tests under `backend/src/test/java/com/hls/identity/` for `PAYROLL` (Manager, Teacher and System refused by the matrix; the menu item per role)
- [X] T009 [P] Add the four audited entity types `PAYROLL_RUN`, `PAYROLL_ADJUSTMENT`, `PAYROLL_PAYMENT`, `PAYROLL_EXPORT` to `audit` `AuditVisibility` mapped to `PAYROLL.VIEW`, plus a test `backend/src/test/java/com/hls/payroll/run/PayrollAuditMappingTest.java` asserting every entity type this spec writes has a mapping (a missing mapping was found only in review in spec 032)
- [X] T010 [P] `backend/src/test/java/com/hls/payroll/run/PayRunSchemaTest.java`: a second non-cancelled PRIMARY run for a month is refused; a second open SUPPLEMENTARY run is refused; a cancelled run does not block a new one; `approved_by` equal to `submitted_by` is refused; a CANNOT_PAY line with an amount is refused; a line whose net is not earned - deduction + adjustments is refused; an adjustment of 0 is refused; a second `payroll_paid_month` row for the same month and person is refused; direct SQL UPDATE and DELETE on the lines of an APPROVED run, on `payroll_run_event`, `payroll_payslip` and `payroll_payment_entry`, and DELETE on `payroll_adjustment`, and UPDATE of an APPLIED adjustment are each refused
- [X] T011 `backend/src/test/java/com/hls/payroll/PayRulesEarnedPayTest.java`, written before the code and failing, with the hand-computed fixtures of contracts/pay-rules-earned-pay.md: a full month at a constant salary earns exactly the salary; a joiner on the 11th of a 20-working-day month; an exit on the 15th; a raise on the 16th (two salary rows, each date at its own); a half-day leave (earned unchanged); a Loss-of-Pay day; a Teacher who changed School mid-month with different divisors (the divisor of each date equals the one `totalLossOfPay` uses for the same date, asserted); a Manager with unpaid days; a Director; a date without a salary gives `Cannot` naming the date; a date with no working days gives `Cannot`; rounding applied once to the sum under NEAREST_RUPEE, UP and DOWN; same inputs same answer
- [X] T012 Add `EarnedPay` (sealed: `Found(rounded, unrounded, days)`, `Cannot(reason)`, record `DayValue(date, salary, workingDays, amount)`) to `payroll/api/` and `earnedPay(PersonRef, YearMonth)` to `PayRules`; implement in `payroll/internal/PayRulesImpl.java` from the same `Computed` object as `totalLossOfPay` so the divisor of a date is the same number; amounts at ten decimals unrounded, rounded once by `LossOfPayCalculator.round` with the policy of the month's last day
- [X] T013 [P] `backend/src/test/java/com/hls/payroll/run/LineCalculatorTest.java`, written before the class and failing: net = earned - deduction + adjustments for table cases including a negative adjustment, a zero net, a net that would be negative (reported, not clamped), amounts at two decimals; a person whose earned or deduction is `Cannot` gives a CANNOT_PAY result with that reason and no amounts, never zero; a fingerprint is the same for the same inputs and different when any input changes
- [X] T014 `payroll/run/internal/LineCalculator.java` (pure: the earned, deduction, adjustments and working inputs in; the line result, working JSON and fingerprint out; no repository, no clock) and `PersonResult` (immutable)
- [X] T015 Entities and repositories in `payroll/run/internal`: `PayRun`, `PayRunLine`, `LineAdjustment`, `PayAdjustment`, `Payslip`, `PaymentEntry`, `PaidMonth`, `RunEvent`; Hibernate only validates the schema; `PayRunLineRepository` has a paged query by run, state, kind and name search in one statement
- [X] T016 `payroll/run/internal/PayrollInput.java` (or extend the existing `payroll.internal.PayrollInput`): reason is trimmed plain text, required where stated, at most 500 characters; reference at most 100 characters; amount non-zero with at most two decimals; month parsed as `yyyy-MM`; one place for the messages, with dates shown DD/MM/YYYY and rupees with Indian digit grouping

**Checkpoint**: migration applies; permission and navigation seeded; `PayRules.earnedPay` and `LineCalculator` pass their tables; the schema and triggers refuse what they must.

---

## Phase 3: User Story 1 - Prepare the Pay Run for an Ended Month (Priority: P1) 🎯 MVP

**Goal**: an Admin starts a run for an ended month and gets one line per payroll person, with people who cannot be paid
listed apart with the reason; a draft can be recomputed with a diff, or cancelled.

**Independent Test**: one Teacher, one Manager and one Director with locked attendance and salaries give three lines equal
to the hand-computed ones; a fourth person with no salary is listed apart while the other three are unchanged.

- [X] T017 [P] [US1] `backend/src/test/java/com/hls/payroll/run/PayRunComputationTest.java`, failing first: a full set (Teacher, Manager, Director) gives three PAYABLE lines with the hand-computed earned, deduction, net, payable days, working days and unpaid days; a Teacher whose month is not locked, a Manager whose month is not locked, a Manager with no joining date, and a person with no salary on a payable date each give a CANNOT_PAY line with the specific reason and no amounts, and every other line is computed; a person with no payable date and no defect is not in the run; a failure thrown for one person becomes a CANNOT_PAY line "Could not be worked out" and the others finish; the counts and totals equal the sums of the lines
- [X] T018 [P] [US1] `backend/src/test/java/com/hls/payroll/run/PayRunLifecycleTest.java`, failing first: start for the current month and for a future month is refused; start for an ended month creates a DRAFT, `compute_state` QUEUED then DONE, progress counters reach the total; a second primary run for the month is refused while one is not cancelled; cancel a draft with a reason makes it CANCELLED, keeps its lines and events, and allows a new run; two simultaneous starts for one month create exactly one run (the other gets 409); cancel without a reason is refused; recompute of an approved run is refused
- [X] T019 [P] [US1] `backend/src/test/java/com/hls/payroll/run/PayRunRecomputeTest.java`, failing first: locking the missing attendance month and recomputing adds the person's line and leaves every other line row untouched (same fingerprint, same row); a changed salary changes only that line; the diff lists added, removed, changed (old and new net) and moved people with the reason; a draft does not change by itself when the calendar or attendance changes (FR-008, US3 scenario 4); recompute while computing is refused unless the heartbeat is older than 10 minutes
- [X] T020 [US1] `payroll/run/internal/RunComputation.java`: claims the run with a conditional update (QUEUED, or RUNNING with a stale heartbeat, to RUNNING), builds the candidate list from `PersonDirectory` (all Teachers, Managers and staff) per research.md section 4 including the explicit Manager-with-no-joining-date case, reads in bulk (`payableDays`, `salariesOn`, `ManagerAttendanceReadApi.months`), then runs one virtual-thread task per person with `Executors.newVirtualThreadPerTaskExecutor()` limited by a `Semaphore` sized to the connection pool; each task is read-only and returns an immutable `PersonResult` (lock state per FR-005, `unpaidDays`, `totalLossOfPay`, `earnedPay`, policy of the month's last day, the Manager lock-time working-day count for the warning of research.md section 5); a thrown exception becomes the CANNOT_PAY result; only the coordinating thread writes lines (batches of 100) and updates `progress_done` and the heartbeat; sets DONE or FAILED at the end; no hand-rolled pool, no shared mutable state
- [X] T021 [US1] `payroll/run/internal/RunService.java`: `start(actor, month, supplementary)` (ended-month rule, uniqueness handled by the partial indexes mapped to 409, creates the run and publishes the computation after commit, audit), `recompute` (draft only; replaces only lines whose fingerprint changed, stores the diff on the run and as an event, audit), `cancel` (draft only, reason required, detaches adjustments, audit), run totals and counts maintained from the lines; every transition under a row lock with the version checked
- [X] T022 [US1] `payroll/run/internal/RecomputeDiff.java`: the added, removed, changed and moved lists and their JSON form for `recompute_summary`
- [X] T023 [US1] `payroll/run/web/PayRunController.java` for `GET /runs`, `POST /runs` (202), `GET /runs/{id}`, `POST /runs/{id}/recompute` (202), `POST /runs/{id}/cancel`; `payroll/run/web/PayRunLineController.java` for `GET /runs/{id}/lines` (paged, filters) and `GET /runs/{id}/lines/{lineId}`; permission checks V and P per contracts/payroll-run-api.md; the actor from the JWT
- [X] T024 [P] [US1] `backend/src/test/java/com/hls/payroll/run/PayrollAuthorizationTest.java` (grown in later phases): the matrix of contracts/payroll-run-api.md for every endpoint built so far, Admin, Director, Manager, Teacher, System and no token; a Manager, Teacher or System receives 403 and no figure in the body
- [X] T025 [P] [US1] `backend/src/test/java/com/hls/payroll/run/PayRunScaleTest.java`: 1,000 generated people compute in under 2 minutes (SC-002) with an unrelated request answering during the run, and the connection pool never starves (the lines endpoint answers during the run in a fixed number of queries, asserted with Hibernate statistics)
- [X] T026 [US1] Frontend `PayrollRunsPage.tsx` (list of runs newest month first, "Start run" for a month with the primary or supplementary choice, refusal messages shown) and `PayrollRunPage.tsx` (header with status, totals and counts; progress bar polling every 2 seconds while computing and stopping when done or failed; lines table with kind, state and name filters and paging of 50; the "Cannot be paid in this run" section with reasons; Compute again with the change list; Cancel with a reason dialog `CancelRunDialog.tsx`); routes `/operations/payroll` and `/operations/payroll/:runId` guarded by `PAYROLL` VIEW in `frontend/src/App.tsx`; loading, empty, error and "computing" states; rupees with Indian digit grouping and DD/MM/YYYY; MUI v9 `Stack` takes gap, wrap and alignment through `sx`
- [X] T027 [P] [US1] Frontend tests `__tests__/PayrollRuns.test.tsx` and `PayrollRun.test.tsx`: the menu item and actions per role (Admin sees Start, Recompute, Cancel; Director sees none of them; others see nothing), the progress state, the cannot-be-paid section, the refusal messages, and axe in both themes (`runOnly` wcag2a, wcag2aa, wcag22aa)

**Checkpoint**: US1 works alone: an Admin can compute, recompute and cancel a run and see every line and every reason.

---

## Phase 4: User Story 2 - Review Line by Line and Approve (Priority: P1) and User Story 3 - An Approved Run Never Changes (Priority: P1)

**Goal**: the Admin reviews the working, submits; the Director approves or returns with a reason; the submitter cannot
approve; approval is atomic, issues payslips and records who is paid; nothing about an approved run ever changes.

**Independent Test**: submit as the Admin, approve as the Director; no figure can be edited afterwards, the same person
cannot submit and approve, and a holiday, a reopened month, a backdated salary and a policy change leave every figure,
payslip and export row identical.

- [X] T028 [P] [US2] `backend/src/test/java/com/hls/payroll/run/PayRunApprovalTest.java`, failing first: submit moves DRAFT to AWAITING_APPROVAL, the Director is notified, and recompute and adjustments are refused until it is returned; submit with a negative net line is refused naming the line; submit while computing is refused; return needs a reason, goes back to DRAFT, notifies the Admin with the reason, and the reason stays in the run's events; approve records who and when; a user holding both roles who submitted cannot approve (403, FR-014) and an Admin granted Approve cannot approve their own submission; after return and a new submit by another user the new submitter is recorded; approve of a run not awaiting approval is 409; every attempt to change a line, recompute, cancel or delete an approved run is refused
- [X] T029 [P] [US2] `backend/src/test/java/com/hls/payroll/run/PayRunConcurrencyTest.java`: two simultaneous approvals produce one approval and one 409; a return racing an approval leaves one consistent outcome; two simultaneous starts for a month (also supplementary) produce one run
- [X] T030 [P] [US3] `backend/src/test/java/com/hls/payroll/run/PayRunFreezingTest.java`, failing first: after approval, adding a holiday to the month, reopening and changing a locked Teacher month and a locked Manager month, recording a salary effective inside the month (backdated), and changing the pay policy each leave the run's lines, working, divisor, unpaid days, totals, payslip bytes and CSV rows identical to before (SC-003); the next run for the person is unaffected by the first run's data; an approved run's screen data is built without calling `PayRules`, attendance or the calendar (asserted with spies or by statement count)
- [X] T031 [P] [US3] `backend/src/test/java/com/hls/payroll/run/PayRunImmutabilityTriggerTest.java`: even through direct SQL, an approved run's lines, line adjustments and run row cannot be updated or deleted, while payment entries can still be added
- [X] T032 [US2] `payroll/run/internal/PayslipRenderer.java` (pure: a stored line and run in, PDF bytes out, A4, PDFBox with the bundled Noto Sans): person name and label, month, salary, payable days and working days, earned pay, each unpaid day with its value and the deduction, each adjustment line, net pay, approval date; rupees with Indian digit grouping, dates DD/MM/YYYY; a test `PayslipRendererTest.java` extracts the text and asserts every figure of the line is present and nothing is read live
- [X] T033 [US2] `payroll/run/internal/PayslipService.java` and `RunService.submit`, `returnToDraft`, `approve` per research.md sections 8 and 12: submit validation and `submitted_by`; return with a reason; approve validates under a row lock (awaiting, caller is not the submitter, version), renders the payslips of all PAYABLE lines in parallel on virtual threads outside any transaction, then in one short transaction re-locks and re-checks the version, stores each PDF through `FileStore.store(actor, "PAYSLIP", lineId, name, bytes)`, inserts `payroll_payslip` and `payroll_paid_month` rows (a key violation fails the approval with a clear message), marks attached adjustments APPLIED with the run and line ids, sets APPROVED with approver and time, writes the event and audit and publishes `PayrollRunApproved`; nothing is changed if any step fails
- [X] T034 [US2] Events in `payroll/api/` (`PayrollRunSubmitted`, `PayrollRunApproved`, `PayrollRunReturned`, no amounts or names) and `notification/internal/PayrollRunEventListener.java` with the notification types added to `notification/api/NotificationType.java` (the column is 60 characters since V50): submitted tells every user holding `PAYROLL.APPROVE`; approved and returned tell the submitter, returned with the reason; messages carry the month and the status; test `PayrollNotificationTest.java`
- [X] T035 [US2] Controller endpoints `POST /runs/{id}/submit`, `/return`, `/approve` in `PayRunController` with permissions P and A per contracts/payroll-run-api.md; extend `PayrollAuthorizationTest` for them
- [X] T036 [US2] The line working in `GET /runs/{id}/lines/{lineId}`: the full stored working (salary rows, payable dates, per-date divisors, each unpaid day with date, value and reason, amounts before and after rounding, the policy, adjustment lines, warning); for a draft it is the stored working of the last computation, never recomputed on read
- [X] T037 [US2] Frontend `RunLineWorkingDialog.tsx` (the working so a reviewer can reproduce the net from this screen alone, SC-005), submit button with the negative-net refusal shown, `ReturnRunDialog.tsx` (reason required), approve button shown only with Approve permission and hidden for the submitter with the reason shown; the approved state shown locked; extend `PayrollRun.test.tsx` per role, including that the Admin cannot approve and the Director cannot recompute; axe in both themes
- [X] T038 [P] [US2] Extend `PayrollAuditTest.java`: start, recompute, submit, return, approve and cancel each write an audit entry with actor, run, and reason where there is one, in the same transaction

**Checkpoint**: US2 and US3 work: a run can be reviewed, submitted, approved or returned; approval is atomic with payslips and the paid-month rows; nothing about an approved run can change.

---

## Phase 5: User Story 4 - Adjustments and Corrections for the Next Run (Priority: P2)

**Goal**: record signed, reasoned amounts against a person; the next draft run takes them as line items; applied ones are
immutable; the three later kinds are reserved.

**Independent Test**: record a +₹2,000 correction; start the next run and see it as its own line item in the net; approve;
see it applied and no longer available.

- [X] T039 [P] [US4] `backend/src/test/java/com/hls/payroll/run/PayAdjustmentTest.java`, failing first: record with a non-zero amount of at most two decimals, a kind and a reason of at most 500 characters is saved WAITING with who and when; amount 0, three decimals, an empty reason and a reserved kind (INCENTIVE, REIMBURSEMENT, SUBSTITUTE_CREDIT) each give 400 naming the problem; a compute or recompute copies each waiting adjustment of a person in the run into the line as an adjustment line, attaches it, and the net includes it; an adjustment of a person who cannot be paid stays waiting and is not applied; approval marks it APPLIED with the run and line; an applied adjustment cannot be edited, withdrawn or deleted; withdraw a waiting adjustment with a reason (kept in history, never applied); withdraw while attached to a draft removes the line's copy and recalculates the net by arithmetic and writes an event; withdraw while attached to a run awaiting approval is 409 with the reason; cancelling a draft detaches its adjustments; an adjustment recorded while a draft is open appears only after recompute and the run reports "waiting since computed"; a negative net after an adjustment blocks submit
- [X] T040 [US4] `payroll/run/internal/AdjustmentService.java` (record, list, withdraw, attach and detach, all audited as `PAYROLL_ADJUSTMENT` with prior and new values) and the attach step inside `RunComputation`; `payroll/run/web/PayAdjustmentController.java` for `GET /adjustments`, `POST /adjustments`, `POST /adjustments/{id}/withdraw` (V and P)
- [X] T041 [US4] Frontend `AdjustmentsPage.tsx` at `/operations/payroll/adjustments` (record form with kind, amount, reason; list with status; withdraw with a reason; the three reserved kinds listed disabled with the name of the spec that will create them) and the adjustment lines shown in the line working and totals; tests `__tests__/Adjustments.test.tsx` per role and axe in both themes
- [X] T042 [P] [US4] Extend `PayrollAuthorizationTest` and `PayrollAuditTest` for the adjustment endpoints and writes

**Checkpoint**: US4 works: a correction travels into the next run and is applied exactly once.

---

## Phase 6: User Story 5 - Pay the People Left Out Through a Supplementary Run (Priority: P2)

**Goal**: people left out of an approved run are shown as owed and are paid once through a supplementary run.

**Independent Test**: approve a run that left one person out; lock their attendance; run the supplementary run and see only
that person paid, exactly once, with the first run unchanged.

- [X] T043 [P] [US5] `backend/src/test/java/com/hls/payroll/run/SupplementaryRunTest.java` and `PaidMonthKeyTest.java`, failing first: the owed list of an approved run shows each left-out person with the stored reason and a live "resolved" hint; a supplementary run is refused while the primary is a draft or awaiting approval, while another supplementary run is open, and when nobody is owed; it contains only the people with no `payroll_paid_month` row for the month, computed by the same rules, with still-uncomputable people listed again with their reason; it goes through review, approval, freezing and payslips as any run; every person appears in exactly one approved run of a month (SC-007); two simultaneous approvals of runs containing the same person leave one paid and one failing with a clear message; a person computable in both is paid once, in the supplementary run
- [X] T044 [US5] `payroll/run/internal/OwedService.java` (owed = left-out lines of the month's approved runs minus `payroll_paid_month`; live resolved hint from the same checks as the computation, never an input to a figure) and the supplementary branch of `RunService.start` and `RunComputation` (candidate set from `payroll_paid_month`, `kind = SUPPLEMENTARY`, next `sequence`); `GET /runs/{id}/owed` in `PayRunController`
- [X] T045 [US5] Frontend: the owed panel on `PayrollRunPage.tsx` for approved runs and the "Start supplementary run" action on the month with its refusal messages; extend `PayrollRun.test.tsx`
- [X] T046 [P] [US5] Extend `PayrollAuthorizationTest` and `PayrollAuditTest` for the owed endpoint and the supplementary start

**Checkpoint**: US5 works: nobody is lost and nobody is paid twice.

---

## Phase 7: User Story 6 - Payslips (Priority: P2)

**Goal**: download a person's payslip or all of a run's, always the approved figures, only for Admin and Director.

**Independent Test**: approve a run, download a payslip, change the calendar, download again: identical; a person left out has none.

- [ ] T047 [P] [US6] `backend/src/test/java/com/hls/payroll/run/PayslipTest.java`, failing first: an approved run's payslip for a line is a PDF whose text carries the line's figures; the zip holds one payslip per paid line and none for a cannot-pay line; a draft or awaiting run returns 404 for both; a second download is byte-identical after a calendar change; a Manager, Teacher and System get 403 and no bytes; every payslip download is by line id of the given run (a line id of another run answers 404); a download writes no state
- [ ] T048 [US6] `payroll/run/web/PayslipController.java` for `GET /runs/{id}/lines/{lineId}/payslip` (`application/pdf`, `Content-Disposition` with a safe file name) and `GET /runs/{id}/payslips` (a zip streamed with `ZipOutputStream`), opening the stored PDF through `FileStore.open`; permission V; no cache headers that allow shared caching of pay data (`Cache-Control: no-store`)
- [ ] T049 [US6] Frontend: payslip download in `RunLineWorkingDialog.tsx` and "Download all payslips" on `PayrollRunPage.tsx` for approved runs only; extend tests
- [ ] T050 [P] [US6] Extend `PayrollAuthorizationTest` for the payslip endpoints

**Checkpoint**: US6 works.

---

## Phase 8: User Story 7 - Record Payment (Priority: P2)

**Goal**: the Admin records payment details per paid line; the run shows paid and unpaid counts and amounts; each person has a payroll history.

**Independent Test**: approve a run, mark two lines paid, see the counts and amounts update and each person's history show the entry; marking a line in a draft run is refused.

- [ ] T051 [P] [US7] `backend/src/test/java/com/hls/payroll/run/PayPaymentTest.java`, failing first: a paid entry with a date not in the future (business time zone), a mode from the list and a reference of at most 100 characters is saved with who and when; a future date, an unknown mode and a reference over 100 characters are each refused naming the field; a correction adds a new entry and keeps the old ones, and the run's amounts never change; a draft or awaiting run and a cannot-pay line refuse payment (409); the run's paid and unpaid counts and amounts equal the sums of the latest entries; a person's history lists each run they were in with month, run kind, net pay, status and the current payment entry
- [ ] T052 [US7] `payroll/run/internal/PaymentService.java` and `PersonHistoryService.java`; `payroll/run/web/PayPaymentController.java` for `PUT` and `GET /runs/{id}/lines/{lineId}/payment` and `payroll/run/web/PayrollHistoryController.java` for `GET /people/{kind}/{id}/history`; paid and unpaid figures added to the run summary; audited as `PAYROLL_PAYMENT`
- [ ] T053 [US7] Frontend `PaymentDialog.tsx` (paid, date, mode, reference, history of earlier entries), the paid and unpaid counts on `PayrollRunPage.tsx`, and `PersonPayrollHistory.tsx` linked from a line; tests per role and axe in both themes
- [ ] T054 [P] [US7] Extend `PayrollAuthorizationTest` and `PayrollAuditTest` for the payment and history endpoints

**Checkpoint**: US7 works.

---

## Phase 9: User Story 8 - Export for the Accountant (Priority: P3)

**Goal**: a CSV of any run at any time, stating the month and the run status, matching the screen and the payslips.

**Independent Test**: export a draft and an approved run; both carry the status; the approved rows match the screen and payslips; each export is audited.

- [ ] T055 [P] [US8] `backend/src/test/java/com/hls/payroll/run/PayRunCsvTest.java`, failing first: the first two rows state the month and the run status; one row per person including cannot-pay people with the reason and no amounts; the approved file's figures equal the line columns and the payslip figures for every person (SC-008); a name beginning with `=`, `+`, `-` or `@` is prefixed with an apostrophe while numeric cells are not changed; commas, quotes and line breaks in text are escaped; UTF-8 with a byte-order mark; an export is audited with who, which run and when; the file for an approved run is identical before and after a calendar change; the caller needs Export (a Director or Admin without it gets 403)
- [ ] T056 [US8] `payroll/run/internal/RunCsvWriter.java` per research.md section 14 (columns listed there) and `GET /runs/{id}/export` in `PayRunController` (permission E, `text/csv; charset=utf-8`, file name with the month and status, `Cache-Control: no-store`), audited as `PAYROLL_EXPORT`
- [ ] T057 [US8] Frontend: "Export CSV" on `PayrollRunPage.tsx` shown only with Export permission; extend tests
- [ ] T058 [P] [US8] Extend `PayrollAuthorizationTest` for the export endpoint

**Checkpoint**: US8 works.

---

## Phase 10: Polish & cross-cutting

- [ ] T059 `payroll/run/internal/PayrollRunDevSeeder.java` (inert unless `hls.seed.demo-data=true`, idempotent, ordered after the attendance and salary seeders): locks last month for the demo Teacher and Manager if the earlier seeders left them unlocked and gives the demo Director a salary, then leaves a computed draft run for last month so the screens have something to show; goes through the real services; extend `DevSeedTest` with an assertion; the seeder must not change an existing run
- [ ] T060 [P] Postman: add `postman/HLS API/013b Payroll Run/` with a request for every endpoint of contracts/payroll-run-api.md in subfolders (Runs, Lines, Adjustments, Payment, Payslips and export), variables for run and line ids, and update `postman/README.md`
- [ ] T061 [P] Accessibility pass for the new screens: focus order, labels on every input, the progress bar's `aria` values, status not by colour alone, table headers, dialog focus return; fix what axe or review finds
- [ ] T062 [P] Update `docs/spec-roadmap.md` (013b row: Implemented, with the extras listed: `PayRules.earnedPay`, `V53`, PDFBox) and `.specify/memory/constitution.md` default role access rows for Payroll (Admin View, Process, Export; Director View, Approve, Export; others none) as a patch note, as for spec 032
- [ ] T063 Run the full backend suite and the full frontend suite, one class at a time first if the machine is slow; record the counts; a machine-sleep timeout is not a failure but must be re-run alone and noted
- [ ] T064 Walk every scenario of quickstart.md that can be run, map each to its automated test in `specs/013b-payroll-run/quickstart-results.md`, and complete the financial review (Principle IX): list the seven points with the fixtures that demonstrate each and the owner's confirmation status; state honestly what was not done (manual browser walkthrough, screen-reader check, human timing of SC-005, Postman not run)
- [ ] T065 Update `specs/032-manager-attendance/quickstart-results.md` "Open findings for 013b" to say both findings are closed, with the test that proves each (`PayRunFreezingTest`, the locked-month rule in `PayRunComputationTest`)

---

## Dependencies & Execution Order

- Phase 1 then Phase 2 (T005 before T010 and T015; T011 before T012; T013 before T014; T012 and T014 before T020).
- US1 (T017-T027) needs Phase 2. It is the MVP.
- US2 and US3 (T028-T038) need US1. T032 (renderer) can start as soon as Phase 2 ends.
- US4 (T039-T042) needs US1; its approval step needs T033. US5 (T043-T046) needs US2. US6 (T047-T050) needs T032-T033.
  US7 (T051-T054) and US8 (T055-T058) need US2 and can run in parallel with each other and with US6.
- Polish needs everything it documents; T065 last.

## Parallel Opportunities

- Phase 2: T006, T007, T008, T009, T010 and T013 are independent files.
- US1: T017, T018, T019, T024, T025 (tests) together; T027 beside T026.
- After US2: US6, US7 and US8 are independent and can be done by separate people.

## Implementation Strategy

**MVP first**: Phases 1-2 and US1 give a working computed run that replaces the spreadsheet's arithmetic. Then US2 and US3
together (a run is not useful until it can be approved and frozen), then US4, US5, US6, US7, US8 in that order. Each phase
ends with its checkpoint and its own pull request, as in spec 032: Foundation, US1, US2+US3, US4, US5, US6, US7+US8,
Polish. Financial review (T064) is the gate before the feature is called done.
