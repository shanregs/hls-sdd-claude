# Tasks: School Billing and Receivables

Format: `- [ ] Tnnn [P?] [Story] description with path`. Stories: US1 raise a monthly due, US2 status and cancellation, US3 collections, US4 adjustments, US5 balances and ageing, US6 overdue follow-up, US7 public interface. Backend root `backend/src/main/java/com/hls/schoolbilling`; tests under `backend/src/test/java/com/hls/schoolbilling`; frontend `frontend/src/features/schoolbilling/receivables`.

## Phase 1: Setup

- [x] T001 Migration `backend/src/main/resources/db/migration/V120__create_school_receivables.sql`: tables `monthly_due`, `due_line`, `due_adjustment`, `collection`, `collection_allocation`, `overdue_follow_up` exactly as data-model.md (money `numeric(14,2)`, `reference varchar(30) unique`, `cancel_reason varchar(300)`, `mode` check BANK/CASH/CHEQUE/UPI, `amount > 0` checks), partial unique index (school_id, month) where cancelled_at is null, append-only triggers refusing UPDATE and DELETE (write-once cancellation on `monthly_due`, write-once `closed_on` on `overdue_follow_up`).
- [x] T002 Add `SCHOOL_BILLING` to `identity/permissions/PermissionModule.java`, `PermissionEligibility.java` (Admin, Director, Manager only; business module; Teacher and System ineligible), `PermissionMatrixService.java` seeded grants, `identity/accessmodel/AccessModelService.java` scope (ORG_WIDE Admin/Director, ASSIGNED Manager) and a navigation item OPERATIONS → School Billing in `NavigationCatalog.java`.
- [x] T003 [P] Add settings `billing.payment_due_days` (default 15, 1 to 120) and `billing.escalation_days` (default 7, 2 to 120) to `settings/internal/SettingKey.java` and a `BillingSettings` reader in `internal/BillingSettings.java`.
- [x] T004 [P] Entities and repositories in `internal/`: MonthlyDue, DueLine, DueAdjustment, Collection, CollectionAllocation, OverdueFollowUp with Spring Data repositories (set-based queries for balances per due and per School).

## Phase 2: Foundational

- [x] T005 `internal/DueCalculator.java`: lines per Teacher span from `TeacherPlacementSource.spansOverlapping`, `SchoolContracts.contractsOverlapping` and `positionOf`; full month gives exact salary, else `salary x worked/monthDays` HALF_UP using `WorkingCalendar.workingDays` and `SchoolWorkingDays.forMonth` (research R1, R2); returns lines plus the not-billed reasons MOU_PENDING, NO_POSITION, NO_ACTIVE_CONTRACT.
- [x] T006 [P] Unit tests `DueCalculatorTest`: whole month, joined day 11 of 26, transfer split across two Schools adds to one month, exit mid-month, replacement in same position, absence ignored, zero-working-day month, rounding.
- [x] T007 `internal/LedgerQueryService.java`: balance of a due (total minus active adjustments minus allocations), status and overdue derivation, advance credit; scope helper using `ScopeQueries` (research R9).
- [x] T008 Extend the ArchUnit/Modulith test so `schoolbilling` reads other modules only through api packages and only `notification` reads `schoolbilling.api` events.

## Phase 3: US1 Raise a monthly due (P1)

Independent test: School with three mapped Teachers, one mid-month join; the due matches a hand calculation, an absence changes nothing, the unmapped Teacher is listed as not billed.

- [x] T009 [US1] `internal/DueService.java` raise: one School and month, refuses when a non-cancelled due exists (409 with the existing due), refuses a month that has not started and a month without working days, writes lines, reference `MD-YYYYMM-<seq>`, `due_on = raised_on + billing.payment_due_days`, Change History through `ChangeRecorder`, publishes `api/MonthlyDueRaised`.
- [x] T010 [US1] Bulk raise `POST /dues/raise` (each School in its own transaction, skipped list with reasons, not-billed list) and `GET /not-billed` in `web/ReceivablesController.java` per contracts/receivables-api.md, with permission guard and scope on every endpoint.
- [x] T011 [P] [US1] API tests `RaiseDueApiTest`: totals, duplicate raise 409, concurrent raise (one wins), not-billed reasons, Zone Manager cannot raise (403), Teacher/System refused, month rules.

## Phase 4: US2 Status, due date and cancellation (P1)

Independent test: raise, cancel with a reason, raise a replacement; both listed, only the replacement counts.

- [x] T012 [US2] `DueService.cancel`: reason required (up to 300), refused while allocations exist, write-once cancellation, audit; `GET /dues`, `GET /dues/{id}` with lines, working, derived status and overdue in `ReceivablesController`.
- [x] T013 [P] [US2] Tests: due date equals raised date plus setting, status transitions derived, cancelled stays visible and excluded from totals and from the one-per-month rule, Change History entries.

## Phase 5: US3 Collections and allocations (P1)

Independent test: one collection pays one due fully and another partly; reversing restores balances.

- [x] T014 [US3] `internal/CollectionService.java`: record (amount > 0, `received_on` not in future, mode, reference, receiver, comment), explicit or oldest-due-first allocation, excess as advance credit, School advisory lock, over-allocation refused, duplicate warning 409 unless `confirmDuplicate` (research R6, R7).
- [x] T015 [US3] Reverse a collection (reason required, negative allocations, original stays) and `POST /collections/{id}/allocate` for advance credit; `web/CollectionsController.java` per contract; Zone Manager limited to own Schools (404 otherwise).
- [x] T016 [P] [US3] Tests `CollectionApiTest`: part/full allocation, advance credit, duplicate warning and confirm, reversal, concurrency on one due, scope refusal, audit rows.

## Phase 6: US4 Adjustments (P2)

Independent test: adjustment lowers balance, reversal restores it, over-balance refused.

- [x] T017 [US4] `internal/AdjustmentService.java` and endpoints (`POST /dues/{id}/adjustments`, `POST /adjustments/{id}/reverse`): amount > 0, reason up to 300 required, never above the balance, Admin and Director only, audited; a due reaching zero balance closes its follow-up (hook into T027).
- [x] T018 [P] [US4] Tests: balance arithmetic, refusal above balance, reversal, Zone Manager 403, task closes when cleared.

## Phase 7: US5 Balances and ageing (P2)

Independent test: seeded dues and collections give hand-calculated buckets; Zone Manager sees only own Schools in every figure and the export.

- [x] T019 [US5] `internal/AgeingService.java`: buckets NOT_YET_DUE, 0 to 30, 31 to 60, 61 to 90, over 90 days past due by School, Zone and Zone Manager in one grouped query; `GET /overview`, `GET /ageing`, `GET /schools/{id}/ledger`.
- [x] T020 [US5] CSV export `GET /export` (same scope, formula-injection guard, research R13) in `web/BillingReportController.java`.
- [x] T021 [P] [US5] Tests: bucket boundaries (30/31, 60/61, 90/91), totals equal sum of dues less adjustments and collections, Zone Manager scope on every endpoint including export and after a Zone Manager change (past months), seeded-volume timing (500 Schools x 12 months under 3 s).

## Phase 8: US6 Overdue follow-up and escalation (P2)

Independent test: overdue due creates one task and no duplicates; paying closes it; no Zone Manager notifies Director and Admin; 025's check escalates.

- [x] T022 [US6] `internal/OverdueJob.java` daily on the business date per research R8: `TaskCreation.createFollowUp` (type FOLLOW_UP, source `SCHOOL_DUE_OVERDUE`, sourceKey due id, `escalateAfterDays` = escalation setting minus days overdue, minimum 1), `overdue_follow_up` row, `reassign` when the Zone Manager changed.
- [x] T023 [US6] Close the task on payment or cancellation (`TaskCreation.complete` / `cancel`) from CollectionService, AdjustmentService and DueService; set `closed_on`.
- [x] T024 [US6] No-Zone-Manager path: publish `api/MonthlyDueOverdue(noManager=true)`; `notification/internal/ReceivablesEventListener.java` and `MessageFactory` text for the new type `SCHOOL_DUE_UNASSIGNED` to Director and Admin (widen the notification type check if needed).
- [x] T025 [P] [US6] Tests `OverdueJobTest`: one task per due across repeated and missed runs, escalation date equals due date plus escalation days, paid and cancelled close the task, reassignment, no-manager notification once, closed dues ignored.

## Phase 9: US7 Public interface (P3)

Independent test: interface figures equal the screens; cancelled due contributes nothing.

- [x] T026 [US7] `api/SchoolReceivables.java` and `internal/SchoolReceivablesImpl.java` per contracts/school-receivables-interface.md (bulk, constant query count).
- [x] T027 [P] [US7] Tests `SchoolReceivablesTest`: teacher amounts, school months, balances, cancelled excluded, no entry for months without a due.

## Phase 10: Frontend

- [x] T028 `frontend/src/features/schoolbilling/receivables/billingApi.ts` and route/menu wiring for OPERATIONS → School Billing (hide actions not granted).
- [x] T029 [P] `BillingOverviewPage.tsx` (month picker, totals, per area, Zone Manager, School rows, not-billed list, Raise dues dialog `RaiseDuesDialog.tsx`) and `AgeingTable.tsx`, CSV button.
- [x] T030 [P] `SchoolLedgerPage.tsx` (dues with lines and working, adjustments, collections, advance credit), `CollectionDialog.tsx` (allocation, duplicate warning), `AdjustmentDialog.tsx`, cancel and reverse with required reason; Indian grouping and DD/MM/YYYY.
- [x] T031 [P] Vitest per role (Admin, Director, Zone Manager, no access for Teacher), keyboard and dark-mode checks at phone width, in `__tests__/`.

## Phase 11: Polish and wrap-up

- [x] T032 Dev demo data `internal/BillingDevSeeder.java` (a contract School with a mid-month Teacher, an overdue due, a collection), Postman folder, `quickstart-results.md`.
- [x] T033 Roadmap row 022 and constitution PATCH row for School Billing in the Default role access matrix (next free patch number at merge), spec 028 receivable tile note; full backend and frontend test run (JDK 25).

## Dependencies

T001 to T004, then T005 to T008; US1 (T009 to T011) before everything else; US2 and US3 follow US1; US4 follows US3; US5 needs US1 to US4 data; US6 needs US2 to US4 hooks; US7 needs US1 to US3; frontend after the API it calls; polish last.

## Parallel examples

T003 with T004; T006 with T005 tests; T011, T013, T016, T018, T021, T025, T027 are test tasks that run beside the next story; T029 to T031 once T028 exists.

## Strategy

MVP is US1 + US2 + US3 (raise, track, collect). Then US4 and US5, then US6, US7 and the frontend polish.
