---

description: "Task list for feature implementation"
---

# Tasks: Expenses

**Input**: Design documents from `/specs/015-expenses/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/expenses-api.md,
contracts/payroll-reimbursement.md, quickstart.md, and **specs 003 (audit), 005 (zones), 010 (notifications), 013b
(payroll run), 016 (training batches), 023 (marketing, and the `files` module) and 025 (tasks) implemented and merged**
(they are on `main`).

**Tests**: included as first-class tasks. Constitution Principle IX requires per-role tests on every endpoint, UI tests per
role, unit tests before calculation logic, and a financial review for anything that moves money. The budget, ageing and
cost arithmetic is tested before it is written, against hand-computed tables.

**Organization**: grouped by user story (spec.md US1-US7). Order follows the dependencies: Foundation, US1 (raise a claim)
with US7 (My Expenses), US2 (links), US3 (review), US4 (payment and payroll), US5 (budget), US6 (pending, costs, export),
Polish.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1-US7; absent for Setup/Foundational/Polish

## Path Conventions

Backend `backend/src/main/java/com/hls/expenses/{api,internal,web}/...`, tests under
`backend/src/test/java/com/hls/expenses/...`; migration `backend/src/main/resources/db/migration`; frontend
`frontend/src/features/expenses/...`. Run one Maven test class at a time (`mvn -o test -Dtest=...`) with
`run_in_background` for slow ones; Docker must be running for integration tests.

---

## Phase 1: Setup

- [ ] T001 Verify the prerequisites before writing code, and STOP and report if any is missing (do not stub): `files.api.FileStore` (`store`, `list`, `open`, `remove` with a reason), `training.api.InductionBatches.batch(UUID)`, `recruitment.api.{DriveActivities,MarketingActivities}.plannedBetween`, `tasks.api.TaskActivities.plannedBetween`, `school.api.{SchoolDirectory,ChangeRecorder,CallerContext}`, `payroll.run.internal.{AdjustmentService,PayAdjustment}` with `source_ref` and the `REIMBURSEMENT` kind, and `Prospect.wonAt`. Record the four items of research.md section 12 (Admin and Director as payroll people; completers per batch; exact C1 kind strings for marketing and drive links; whether a Manager may pick any Zone) in the PR description with what was found.
- [ ] T002 Create the package skeletons `backend/src/main/java/com/hls/expenses/{api,internal,web}/` with `package-info.java`, and `backend/src/test/java/com/hls/expenses/ExpensesModuleRulesTest.java` (ArchUnit, same shape as `TasksModuleRulesTest`: nothing outside uses `expenses.internal` or `expenses.web`; `expenses` reads other modules only through their `api` packages and the shared `school.api` helpers)
- [ ] T003 [P] Create `frontend/src/features/expenses/expensesApi.ts` with types and calls for every endpoint in contracts/expenses-api.md (money as decimal strings, dates as ISO strings, `allowedActions` as a string union, labels for the six categories and the six statuses)

---

## Phase 2: Foundational (blocking prerequisites)

- [ ] T004 Write `backend/src/main/resources/db/migration/V54__create_expenses.sql` per data-model.md, quoting its constraints exactly. `expense_claim`: `amount numeric(14,2) NOT NULL` with check `amount > 0`; `category` check in (`RENT_UTILITIES`, `TRAVEL_FUEL`, `TRAINING_STAY`, `MARKETING`, `OFFICE_SUPPLIES`, `OTHER`); `status` check in (`DRAFT`, `SUBMITTED`, `APPROVED`, `REJECTED`, `PAID`, `PAID_BY_COMPANY`); `paid_by` check in (`CLAIMANT`, `COMPANY`); `paid_how` (`PAYROLL`, `OUTSIDE`) not null only when `status = 'PAID'`; `(link_kind IS NULL) = (link_ref IS NULL)`; `note varchar(1000)`, `reason varchar(500)`, `no_bill_reason varchar(500)`, `link_label varchar(200)`, `paid_reference varchar(100)`; `UNIQUE (claimant_id, client_request_id)`; indexes `(claimant_id, expense_date DESC)`, `(status, submitted_at)`, `(zone_id, expense_date)`, `(category, expense_date)`; `version bigint`. Trigger `expense_claim_paid_immutable` refusing any change to claimant, date, amount, category, zone, paid_by, link columns or status once `status IN ('PAID','PAID_BY_COMPANY')`. `expense_bill` (file_id uuid with no cross-module foreign key, `name varchar(255)`, `removal_reason varchar(500)` required when `removed_at` is set), `expense_claim_event` (append-only, trigger refuses update and delete; event values as listed in data-model.md), `expense_area_budget` (`amount numeric(14,2) >= 0`, unique on `coalesce(zone_id, '00000000-0000-0000-0000-000000000000'::uuid), month`) and `expense_budget_change` (append-only)
- [ ] T005 [P] Add the permission modules `EXPENSES(VIEW, CREATE, EDIT, APPROVE, PROCESS, EXPORT)` and `MY_EXPENSES(VIEW, CREATE, EDIT, DELETE)` to `identity/permissions/PermissionModule.java`; in `PermissionEligibility` make `EXPENSES` Admin and Director only (a new restricted map and a check in `actionsFor`, as for payroll; mind the `Map.of` limit of 10 pairs) and `MY_EXPENSES` every role except System; add both to the business-module set so System can never hold them; seed in `PermissionMatrixService` per the spec's table (Admin: EXPENSES View, Create, Edit, Approve, Process, Export; Director: EXPENSES View, Create, Edit, Approve, Export; Admin, Director, Manager and Teacher: MY_EXPENSES View, Create, Edit, Delete); scopes in `AccessModelService` (EXPENSES org-wide, MY_EXPENSES own)
- [ ] T006 [P] Add navigation to `identity/accessmodel/NavigationCatalog.java`: OPERATIONS → "Expenses" `/operations/expenses` (`EXPENSES` VIEW) for Admin and Director, and OPERATIONS → "My Expenses" `/operations/my-expenses` (`MY_EXPENSES` VIEW) for Admin, Director, Manager and Teacher; make sure the OPERATIONS section appears for a Teacher whose only item is My Expenses
- [ ] T007 [P] Extend `PermissionEligibilityTest` and the navigation and access-model tests under `backend/src/test/java/com/hls/identity/` for both modules (System refused by the matrix; Manager and Teacher refused `EXPENSES`; the menu items per role, including Teacher)
- [ ] T008 [P] Add the audited entity types `EXPENSE_CLAIM`, `EXPENSE_BUDGET`, `EXPENSE_EXPORT` to `audit` `AuditVisibility` mapped to `EXPENSES.VIEW`, plus a test `backend/src/test/java/com/hls/expenses/ExpenseAuditVisibilityTest.java` (a claimant sees none of these audit entries)
- [ ] T009 [P] Add `EXPENSE_SUBMITTED`, `EXPENSE_DECIDED` and `EXPENSE_REVERSED` to `notification/api/NotificationType.java` (names well under the 60-character column), texts in `notification/internal/MessageFactory.java`, and `notification/internal/ExpenseEventListener.java` listening to `ExpenseSubmitted` and `ExpenseDecided` (submit and resubmit go to every active Admin and Director; approve, reject and reverse go to the claimant); the event records live in `expenses/api/`
- [ ] T010 [P] `backend/src/test/java/com/hls/expenses/ExpensesSchemaTest.java`: a second claim with the same (claimant, client_request_id) is refused; amount 0 and negative refused; a link kind without a ref refused; a paid or paid-by-company claim cannot have amount, date, category, zone, paid_by, link or status changed; `expense_claim_event` and `expense_budget_change` refuse update and delete; two budgets for the same area and month refused including Head office (null zone)
- [ ] T011 Entities and repositories in `expenses/internal`: `ExpenseClaim` (with `@Version`), `ExpenseBill`, `ClaimEvent`, `AreaBudget`, `BudgetChange` and their Spring Data repositories; Hibernate only validates the schema; money is `BigDecimal` scale 2
- [ ] T012 `expenses/internal/ExpenseInput.java`: free text is trimmed plain text; note at most 1000, reasons at most 500, reference at most 100 characters; amount positive with at most two decimals (refuse `0.505`); a date helper for "not in the future and not older than 60 days" in Asia/Kolkata; category and paid-by parsers; reasons are required where stated
- [ ] T013 `expenses/web/ExpensesExceptionAdvice.java` (`@RestControllerAdvice` for the module's web package, the `{"reason": ...}` body of `TaskExceptionAdvice`: 400, 403, 404, 409, upload too large 400, optimistic lock 409) and `backend/src/test/java/com/hls/support/ExpensesTestBase.java` (users for each role, a Zone, a helper to build a claim, to attach a jpg bill, to submit and to approve, set `hls.files.directory`)
- [ ] T014 `expenses/internal/AllowedActions.java`: computes the `allowedActions` list of a claim from the viewer's grants (`PermissionGuard.isGranted`), ownership and status (rules in contracts/expenses-api.md), plus `backend/src/test/java/com/hls/expenses/AllowedActionsTest.java` with a table (claimant on Draft, Rejected, Submitted, Approved, Paid; Admin on own and others' claims; Director; user holding both roles on own claim never gets APPROVE, REJECT or REVERSE)

**Checkpoint**: migration applies; permissions and navigation seeded; schema and triggers refuse what they must; the module rules test passes.

---

## Phase 3: User Story 1 - Raise an Expense Claim with its Bill (Priority: P1) and User Story 7 - My Expenses (Priority: P1) 🎯 MVP

**Goal**: a claimant raises a claim with a bill, saves a Draft or submits, withdraws, corrects and resubmits, and tracks all of their claims with what they are still owed.

**Independent Test**: as a Manager raise a Travel and Fuel claim of 850.00 dated yesterday with one jpg bill, submit it, see it Submitted; another Manager gets "not found"; the owed total, filters and month filter work.

### Tests (write first)

- [ ] T015 [P] [US1] `backend/src/test/java/com/hls/expenses/ClaimValidationTest.java`: amount 0 and negative, 0.50 accepted, 0.505 refused; date in the future, 61 days back refused and exactly 60 days accepted; category Other without a reason refused; no bill and no `noBillReason` refused, with the reason accepted and shown as "No bill"; a `.docx` file, an 11 MB file, a sixth bill refused with the reason and the claim unchanged; Draft saves without these checks but Submit enforces them
- [ ] T016 [P] [US1] `backend/src/test/java/com/hls/expenses/ClaimLifecycleApiTest.java`: create Draft, edit, delete, submit, withdraw back to Draft, Rejected edit and resubmit as the same claim; only Draft and Rejected are editable; Submitted, Approved and Paid refuse edits; a stale `version` answers 409
- [ ] T017 [P] [US1] `backend/src/test/java/com/hls/expenses/ClaimRetryAndDuplicateTest.java`: the same `clientRequestId` twice returns one claim (SC-010) and a different body with the same id is refused; same claimant, date, amount and category as a non-Rejected claim returns 409 with `matches` until `confirmDuplicate` is true, and the confirmation is in the claim history; a Rejected claim does not count as a duplicate
- [ ] T018 [P] [US7] `backend/src/test/java/com/hls/expenses/MyExpensesApiTest.java`: only own claims, newest first, status and month filters; `owed` equals the sum of own Approved personally paid claims not yet paid and excludes company-paid, Paid and Rejected; Manager A cannot read Manager B's claim or bill (404); Teacher reaches only `/my-expenses`; System refused everywhere (SC-008)

### Implementation

- [ ] T019 [US1] `expenses/internal/BillService.java`: add a bill through `FileStore.store(actor, "EXPENSE_CLAIM", claimId, name, bytes)` only for extensions jpg, jpeg, png, pdf and at most 10 MB, at most 5 live bills per claim, a bill on a Submitted claim cannot be replaced by the claimant, download opens through `FileStore.open` after the claim's access check, removal by `EXPENSES.APPROVE` with a required reason through `FileStore.remove` (kept as "bill removed" with the reason); writes `BILL_ADDED` and `BILL_REMOVED` events and audit
- [ ] T020 [US1] `expenses/internal/ClaimService.java`: create (idempotent on `clientRequestId`), edit, delete Draft, submit, withdraw, correct and resubmit; submission rules of FR-005; duplicate check of FR-008 with the recorded confirmation; area must be an active Zone or Head office (null); status guards so only Draft and Rejected are editable; every change writes an event and audit in the same transaction and publishes `ExpenseSubmitted` on submit and resubmit
- [ ] T021 [US7] `expenses/internal/MyExpensesQueries.java`: own claims paged with status and month filters, owed total as one aggregate, detail with bills, link, history, `allowedActions`
- [ ] T022 [US1] `expenses/web/MyExpensesController.java`: the `/my-expenses` endpoints of contracts/expenses-api.md (list, create, get, put, delete, submit, withdraw, bills upload and download); guard each with `PermissionGuard.require(CallerContext.roles(jwt), PermissionModule.MY_EXPENSES, ...)`; the claimant comes from `CallerContext.userId(jwt)` only
- [ ] T023 [P] [US1] `frontend/src/features/expenses/ClaimFormPage.tsx`: create and edit form (date, amount, category, area select with active Zones and Head office, note, paid by, bills with type and size checks, "no bill" reason, Other reason), save Draft and Submit, duplicate warning dialog with confirm, a generated `clientRequestId` kept for the life of the form; every input labelled; errors name the rule
- [ ] T024 [P] [US7] `frontend/src/features/expenses/MyExpensesPage.tsx` and `ClaimDetailPage.tsx`: list with status and month filters, owed total at the top, detail with bills, link, history, rejection reason and the buttons from `allowedActions` only; loading, empty and error states; rupees with Indian digit grouping and DD/MM/YYYY
- [ ] T025 [US1] Register routes `/operations/my-expenses`, `/operations/my-expenses/new` and `/operations/my-expenses/:id` in `frontend/src/App.tsx` under `RouteGuard` for `MY_EXPENSES`
- [ ] T026 [P] [US1] `frontend/src/features/expenses/__tests__/MyExpenses.test.tsx`: form validation messages, bill type and size checks, duplicate dialog, owed total, per-role rendering (Manager, Teacher, Admin), axe in light and dark

**Checkpoint**: a claimant raises, submits, withdraws, corrects and tracks claims; retries create one claim; privacy holds.

---

## Phase 4: User Story 2 - Link a Claim to the Work it Belongs To (Priority: P1)

**Goal**: Travel and Fuel may link to the claimant's activity; Training Stay must link to an induction batch; the link is kept as at submission.

**Independent Test**: Training Stay without a batch is refused, with a batch accepted; a Travel and Fuel claim linked to the Manager's own visit shows kind, date and place, and still does after the visit is cancelled.

- [ ] T027 [P] [US2] `backend/src/test/java/com/hls/expenses/ClaimLinkTest.java`: Training Stay needs a batch (refused otherwise) and stores the batch name and dates; Travel and Fuel link optional ("not linked" shown); only the claimant's own activities or those on the claim date are offered; a category that takes no link refuses one; after the activity is cancelled or the batch removed the claim still shows the stored kind, date and label (FR-011)
- [ ] T028 [US2] `expenses/internal/LinkResolver.java`: options for Travel and Fuel from `DriveActivities`, `MarketingActivities` and `TaskActivities` (`plannedBetween` for the claimant as owner on the claim date, plus the claimant's own), and for Training Stay from `InductionBatches`; validates a chosen link belongs to the offered set and snapshots `link_kind`, `link_ref`, `link_date`, `link_label`; the marketing and drive kind strings are held in one constant class `LinkKinds` (used later by the cost measure)
- [ ] T029 [US2] Wire `LinkResolver` into `ClaimService` create, edit and submit and add `GET /my-expenses/link-options?category=&date=` to `MyExpensesController`
- [ ] T030 [P] [US2] Link selector in `frontend/src/features/expenses/ClaimFormPage.tsx` (shown only for the two categories, "not linked" allowed for Travel and Fuel, batch required for Training Stay) and the link line on `ClaimDetailPage.tsx`; extend `__tests__/MyExpenses.test.tsx`

**Checkpoint**: links work and survive cancellation of what they point to.

---

## Phase 5: User Story 3 - Review, Approve or Reject (Priority: P1)

**Goal**: Admin and Director review the queue, approve or reject, nobody decides their own claim, and a rejected claim returns for correction.

**Independent Test**: submit as a Manager; the Director approves; reject another without a reason (refused) then with one; the Manager corrects and resubmits; the history shows both; an Admin cannot approve their own claim.

- [ ] T031 [P] [US3] `backend/src/test/java/com/hls/expenses/ClaimDecisionTest.java`: approve and reject record approver and time and notify the claimant; reject needs a reason; a rejected claim resubmits as the same claim and keeps the earlier decision in history; amount, category, date, area and bill refused on Approved; the second of two concurrent decisions answers 409 naming who decided and when (FR-016)
- [ ] T032 [P] [US3] `backend/src/test/java/com/hls/expenses/SelfDecisionTest.java` (SC-004): an Admin cannot approve, reject or reverse their own claim, a Director can; a Director's claim is decided by an Admin; a user holding both Admin and Director roles cannot decide a claim they raised
- [ ] T033 [P] [US3] `backend/src/test/java/com/hls/expenses/ExpenseAuthorizationTest.java`: every `/expenses` endpoint refuses Manager, Teacher and System with 403 and `/my-expenses` refuses System; Admin and Director are org-wide; the review list shows the area's remaining budget for the claim's month
- [ ] T034 [US3] `expenses/internal/DecisionService.java`: approve, reject and reverse as a conditional update on status plus `@Version`; refuse the claimant's own claim; reject requires a reason; a company-paid claim goes to `PAID_BY_COMPANY` in the same transaction; events, audit and `ExpenseDecided`; reversal rules are completed in US4 (T043)
- [ ] T035 [US3] `expenses/internal/ReviewQueries.java` and `expenses/web/ExpenseReviewController.java`: `GET /expenses`, `GET /expenses/{id}`, `POST /expenses/{id}/{approve,reject}`, bill download and `DELETE /expenses/{id}/bills/{billId}`; list rows carry age and the area's remaining budget (read from `BudgetQueries` once T049 exists; until then the field is null)
- [ ] T036 [P] [US3] `frontend/src/features/expenses/ExpensesReviewPage.tsx`, `DecisionDialog.tsx` (approve, reject with a required reason) and `RemoveBillDialog.tsx`; the queue is oldest first, with age, claimant, category, area, amount, bills, link and note; bills open in the page; focus returns to the row after a dialog closes
- [ ] T037 [US3] Register routes `/operations/expenses` and `/operations/expenses/:id` in `frontend/src/App.tsx` under `RouteGuard` for `EXPENSES`
- [ ] T038 [P] [US3] `frontend/src/features/expenses/__tests__/ExpensesReview.test.tsx`: queue, approve and reject flows, reason required, buttons hidden for the claimant's own claim, Manager and Teacher get no route, axe in both themes

**Checkpoint**: claims are decided once, never by their raiser, and the history is complete.

---

## Phase 6: User Story 4 - Pay Back: Reimbursed with Salary or Recorded as Paid (Priority: P1)

**Goal**: an approved personally paid claim reaches the claimant's next pay run as a reimbursement exactly once, or is recorded as paid outside; company-paid claims never reach payroll; an approval can be reversed until it is paid.

**Independent Test**: approve a 1,200.00 claim paid by the Manager, start the Manager's run and see a 1,200.00 reimbursement; cancel the run (claim still Approved), run again, approve the run: "Paid with payroll". A second claim recorded as paid by transfer is no longer offered. Reversal of each paid claim is refused.

- [ ] T039 [US4] Additive payroll interface per contracts/payroll-reimbursement.md: `payroll/api/ReimbursementIntake.java` (`offer`, `withdraw`, `status`, `OfferStatus`), `payroll/api/PayrollAdjustmentApplied.java`, `payroll/run/internal/ReimbursementIntakeImpl.java` using `PayAdjustment` with kind `REIMBURSEMENT`, positive amount, `source_ref` = claim id, idempotent per source, withdraw refused when the run holding it is awaiting approval or the adjustment is applied; `ApprovalService.approve` publishes `PayrollAdjustmentApplied` once per applied adjustment with a `source_ref`, in the approval transaction. Add a short amendment note to `specs/013b-payroll-run/spec.md` and `contracts/payroll-run-api.md` (reserved kind is created by 015 through this interface). Extend `PayrollRunModuleRulesTest` if it needs to allow the new `api` classes
- [ ] T040 [P] [US4] `backend/src/test/java/com/hls/payroll/run/ReimbursementIntakeTest.java`: offer is idempotent; the adjustment attaches to a draft run at compute and shows as a Reimbursement line; cancel releases it to WAITING; withdraw works while WAITING or held by a draft and is refused when awaiting approval or applied; approval marks it APPLIED and publishes one event; a frozen approved run is unchanged by any of this (existing freezing tests still pass)
- [ ] T041 [P] [US4] `backend/src/test/java/com/hls/expenses/ClaimPaymentTest.java` (SC-005): approve offers a personally paid claim once; run, cancel, re-run, approve gives exactly one run and status Paid with payroll with the month; record paid outside (date, mode in `CASH`, `BANK_TRANSFER`, `UPI`, `OTHER`, reference at most 100 characters) withdraws the offer and payroll stops offering it; a claim can never be both; a company-paid claim becomes Paid by company at approval and never reaches payroll; a claim whose claimant has left stays Approved and can be recorded paid outside; a claim dated in an already-approved month goes to the next run
- [ ] T042 [P] [US4] `backend/src/test/java/com/hls/expenses/ClaimReversalTest.java`: reverse needs a reason, returns to Submitted with the reversal in history, withdraws the offer and notifies the claimant; refused when the run awaiting approval holds it, when applied, when Paid, when Paid by company, and for the reverser's own claim
- [ ] T043 [US4] `expenses/internal/PaymentService.java`: on approval of a personally paid claim call `ReimbursementIntake.offer` (kind from the claimant's payroll person; if no payroll person exists record `OFFERED_TO_PAYROLL` as skipped with the reason and leave the claim Approved for outside payment); `recordPaid` (Admin, `EXPENSES.PROCESS`) withdraws the offer first and refuses if that fails; `reverse` completes the rules of T042 using `ReimbursementIntake.status`; `PayrollSettlementListener` handles `PayrollAdjustmentApplied` for kind `REIMBURSEMENT` idempotently per source, setting Paid with payroll, `payroll_run_id` and `payroll_month`, with event and audit
- [ ] T044 [US4] Add `POST /expenses/{id}/reverse` and `POST /expenses/{id}/record-paid` to `ExpenseReviewController`; claim detail returns the payroll state (`NONE`, `WAITING`, `HELD_BY_DRAFT`, `HELD_AWAITING_APPROVAL`, `APPLIED`) so a client can explain why an action is unavailable
- [ ] T045 [P] [US4] `frontend/src/features/expenses/ReverseDialog.tsx` and `RecordPaidDialog.tsx`, and the payment block on `ClaimDetailPage.tsx` (status, how paid, month, reference, owed to a person with no pay run); `__tests__/ExpensesPayment.test.tsx` covering Admin versus Director actions and the disabled reasons, axe in both themes

**Checkpoint**: approval has a financial effect, exactly once, through payroll or outside it.

---

## Phase 7: User Story 5 - Budget versus Actual per Area (Priority: P2)

**Goal**: a monthly budget per area, with actual, pending, remaining and an over marker, and a breakdown by category and claimant.

**Independent Test**: set 20,000.00 for a Zone and month, approve 12,000.00 and leave 5,000.00 pending: budget 20,000, actual 12,000, pending 5,000, remaining 8,000; approve 10,000.00 more: over by 2,000.00.

- [ ] T046 [US5] `backend/src/test/java/com/hls/expenses/BudgetVersusActualTest.java`, written before the code and failing, with hand-computed tables (SC-006): claims in every status (only Approved, Paid and Paid by company count as actual; Submitted is pending; Draft and Rejected count nowhere), counted by expense date; an area with no budget returns `budget = null` and the actual; a Head office claim; a rejected-then-approved claim counts once; a reversed approval moves from actual to pending; over-budget approval still succeeds; percent used with zero budget returns no percentage; amounts in rupees and paise
- [ ] T047 [P] [US5] `backend/src/test/java/com/hls/expenses/BudgetApiTest.java`: set and change a budget (Admin, Director), history keeps old amount, new amount, who and when; Manager and Teacher refused; month closed in payroll does not matter; amount negative refused; detail lists the claims behind actual by category and by claimant
- [ ] T048 [US5] `expenses/internal/BudgetService.java` (set and change with a `BudgetChange` row and audit `EXPENSE_BUDGET`) and `expenses/internal/BudgetQueries.java` (SQL aggregates grouped by area, category, claimant; no per-claim loads)
- [ ] T049 [US5] `expenses/web/ExpenseBudgetController.java`: `GET /expenses/budgets`, `GET /expenses/budgets/detail`, `PUT /expenses/budgets`, `GET /expenses/budgets/{id}/history`; fill the review list's remaining-budget field from `BudgetQueries` (completing T035)
- [ ] T050 [P] [US5] `frontend/src/features/expenses/BudgetVersusActualPage.tsx` (month picker, a row per area and total, "No budget set", over marker not by colour alone, drill-down by category and claimant, set budget dialog) and `__tests__/BudgetVersusActual.test.tsx`; route `/operations/expenses/budgets`

**Checkpoint**: the Director sees spend against intent per area and month.

---

## Phase 8: User Story 6 - Pending Approvals, Ageing, Costs and Export (Priority: P2)

**Goal**: pending approvals by age, cost per School acquired and per Teacher trained, a monthly CSV, and the exposed read interface for other modules.

**Independent Test**: with 30,000.00 of approved Marketing and linked Travel and Fuel claims and three Schools won in the period, see 10,000.00 per School; with 60,000.00 of Training Stay for a batch of 12 completers, 5,000.00 per Teacher; an empty period shows the cost and "No Schools acquired".

- [ ] T051 [P] [US6] Additive reads: `recruitment/api/WonSchools.java` with `recruitment/marketing/internal/WonSchoolsImpl.java` (reads `Prospect.wonAt` as a business date in Asia/Kolkata, optional Zone), and in `training/api/InductionBatches.java` `completedTeachers(UUID batchId)` and `endingBetween(LocalDate, LocalDate)` if no equivalent exists, with tests in `backend/src/test/java/com/hls/recruitment/marketing/WonSchoolsTest.java` and `backend/src/test/java/com/hls/training/InductionBatchesInterfaceTest.java`
- [ ] T052 [P] [US6] `backend/src/test/java/com/hls/expenses/PendingAgeingTest.java`: buckets 0-2, 3-7, 8-14 and over 14 days by submission date with counts and totals, oldest first, resubmission restarts the age; boundaries on day 2/3, 7/8, 14/15
- [ ] T053 [P] [US6] `backend/src/test/java/com/hls/expenses/CostMeasureTest.java` (SC-007) with hand-computed fixtures: cost per School = approved Marketing plus Travel and Fuel linked to marketing visits, meetings, calls or campus drives dated in the period, over Schools won in the period; Rent and Utilities, Office Supplies, Other and unlinked or task-linked Travel and Fuel excluded; no wins gives state `NO_SCHOOLS` with the cost; cost per Teacher = approved Training Stay of batches ending in the period over completers; none gives `NO_TEACHERS`; narrowing by Zone or batch; results rounded half-up to two decimals
- [ ] T054 [P] [US6] `backend/src/test/java/com/hls/expenses/ClaimCsvTest.java`: one row per claim with the columns of FR-025, month filter by expense date, formula characters (`=`, `+`, `-`, `@`) neutralised in text cells, requires `EXPENSES.EXPORT`, writes an `EXPENSE_EXPORT` audit entry, Manager refused
- [ ] T055 [US6] `expenses/internal/PendingQueries.java`, `expenses/internal/CostMeasureService.java` (uses `LinkKinds`, `WonSchools`, `InductionBatches`; returns cost, divisor, state and the claims behind it) and `expenses/internal/ClaimCsvWriter.java`
- [ ] T056 [US6] `expenses/api/ExpenseQueries.java` and `expenses/internal/ExpenseQueriesImpl.java` per contracts/payroll-reimbursement.md (`owedTo`, `approvedSpend`, `pending`, `costPerSchool`, `costPerTeacher`) with `OwedClaim`, `SpendRow`, `PendingSummary`, `CostMeasure` records; a test `backend/src/test/java/com/hls/expenses/ExpenseQueriesInterfaceTest.java` proving the figures equal the HTTP views
- [ ] T057 [US6] `expenses/web/ExpenseReportController.java`: `GET /expenses/pending`, `GET /expenses/costs/per-school`, `GET /expenses/costs/per-teacher`, `GET /expenses/export`
- [ ] T058 [P] [US6] `frontend/src/features/expenses/CostMeasuresPage.tsx` (period picker, the two measures with cost, divisor and claims behind, "No Schools acquired" and "No Teachers trained yet" states), the pending and ageing block on `ExpensesReviewPage.tsx`, an Export button for Admin and Director, and `__tests__/CostMeasures.test.tsx`; route `/operations/expenses/costs`

**Checkpoint**: management sees waiting claims, their age and the two cost measures; other specs can read one source.

---

## Phase 9: Polish and Cross-Cutting Concerns

- [ ] T059 `expenses/internal/ExpensesDevSeeder.java` (inert unless `hls.seed.demo-data=true`, idempotent, ordered after the people, zone and marketing seeders): claims in every status for the demo Manager, Teacher and Admin with a generated jpg bill, a budget for the demo Zone for the current month, one approved personally paid claim for the demo Manager waiting for payroll; goes through the real services; extend `DevSeedTest` with an assertion; never changes existing claims
- [ ] T060 [P] Postman: add `postman/HLS API/015 Expenses/` with a request for every endpoint of contracts/expenses-api.md in subfolders (My Expenses, Review, Budgets, Reports), variables for claim, bill and budget ids, and update `postman/README.md`
- [ ] T061 [P] Accessibility pass for the new screens: focus order, labels on every input, status not by colour alone (including the over-budget marker), table headers, dialog focus return, bill preview alternatives; fix what axe or review finds
- [ ] T062 [P] Update `docs/spec-roadmap.md` (015 row: Implemented, listing the extras: payroll `ReimbursementIntake`, `WonSchools`, `V54`) and `.specify/memory/constitution.md` default role access rows for Expenses and My Expenses as a patch note, as for spec 032
- [ ] T063 [P] Hand-off note for spec 036: update `docs/spec-inputs/036-mobile-expenses.md` where it differs (jpg conversion, `clientRequestId`, Teacher My Expenses, `allowedActions`)
- [ ] T064 Run the full backend suite and the full frontend suite, one class at a time first if the machine is slow; record the counts; a machine-sleep timeout is not a failure but must be re-run alone and noted
- [ ] T065 Walk every scenario of quickstart.md that can be run, map each to its automated test in `specs/015-expenses/quickstart-results.md`, and complete the financial review (Principle IX): list the seven points with the fixtures that demonstrate each and the owner's confirmation status; state honestly what was not done (manual browser walkthrough, screen-reader check, Postman not run, human timing of SC-001 and SC-002)

---

## Dependencies and Execution Order

- Phase 1 then Phase 2 (blocking). Within Phase 2: T004 first; T005 to T010 are parallel after it; T011 to T014 follow T004.
- **US1 and US7 (Phase 3)** depend on Phase 2 only and are the MVP.
- **US2 (Phase 4)** depends on US1 (claim form and service).
- **US3 (Phase 5)** depends on US1; its remaining-budget field is completed by T049.
- **US4 (Phase 6)** depends on US3 (approval) and on specs 013b already merged; T039 (payroll interface) can start in parallel with US3.
- **US5 (Phase 7)** depends on US3 (statuses) and can run in parallel with US4.
- **US6 (Phase 8)** depends on US2 (stored link kinds) and US3; T051 can start any time after Phase 2.
- Polish needs everything it documents; T065 last.

## Parallel Opportunities

- Phase 2: T005 to T010 together.
- US1: tests T015 to T018 together; frontend T023, T024, T026 together once T022 exists.
- US3: tests T031 to T033 together.
- US4: T039 with the US3 work; tests T040 to T042 together.
- US5 and US4 in parallel once US3 is merged.
- US6: T051 to T054 together.

## Implementation Strategy

1. **MVP**: Phase 1, Phase 2, Phase 3 (US1 and US7). A claimant can already replace the ledger spreadsheet.
2. Add US2 and US3 so claims are linked and decided; this is the first version the Director can use.
3. Add US4 so approval moves money through payroll; this is the point where the financial review starts.
4. Add US5 and US6 for management views.
5. One PR per phase, opened against `main`; squash-merged stacks conflict, so never open a PR against a side branch.

Financial review (T065) is the gate before the feature is called done.
