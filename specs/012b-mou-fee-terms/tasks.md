# Tasks: MoU Fee Terms (Billing Basis and GST)

Format: `- [ ] Tnnn [P?] [Story] description with path`. Stories: US1 record fee terms, US2 period dues, US3 GST tax lines, US4 credit notes and collections with tax, US5 tax invoice, US6 margin and tax summary, US7 existing data unchanged. Backend root `backend/src/main/java/com/hls/schoolbilling`; tests `backend/src/test/java/com/hls/schoolbilling`; frontend `frontend/src/features/schoolbilling`.

Gate: do not start T001 until the owner has answered Q1 to Q6 in `clarification-questions.md` (the defaults may be confirmed as they are). If Q5 is answered B, drop Phase 7.

## Phase 1: Setup

- [ ] T001 Migration `backend/src/main/resources/db/migration/V170__contract_fee_terms.sql` exactly as data-model.md: contract columns and widened `cycle` check, `contract_position.period_fee`, `contract_period`, `monthly_due` period, tax and invoice columns with backfill, `due_tax_line`, `due_adjustment` tax columns, `invoice_counter`, the new unique index, and extended write-once triggers.
- [ ] T002 [P] Add settings keys `billing.hls_gstin`, `billing.hls_state_code`, `billing.hls_legal_name`, `billing.hls_address`, `billing.sac_code`, `billing.gst_rate` (default 18.00, 0 to 28) in `settings/internal/SettingKey.java` and read them in `internal/HlsTaxSettings.java`. Do not add or change any due-days setting.
- [ ] T003 [P] Entities and repositories for `contract_period`, `due_tax_line`, `invoice_counter`; add the new columns to the contract, position, due and adjustment entities.

## Phase 2: Foundational

- [ ] T004 [P] `internal/GstinValidator.java` (15 characters, pattern, check character, state code table) with `GstinValidatorTest`.
- [ ] T005 [P] `internal/TaxCalculator.java` (intra-state CGST and SGST halves, inter-state IGST, HALF_UP per line, total) with `TaxCalculatorTest` including the spec examples (1,80,000 at 18% giving 16,200 + 16,200 and 32,400) and odd-paise rounding.
- [ ] T006 `internal/BillingPeriodCalculator.java`: Monthly, Annual (12-month spans cut at the end date) and Per term periods, period working days as the sum of `SchoolWorkingDays.forMonth`, monthly equivalent; with `BillingPeriodCalculatorTest`.
- [ ] T007 [P] `internal/InvoiceNumbering.java` (financial year from `businessToday()`, locked counter row, format `INV/2027-28/00001`) and `AmountInWords` (Indian system) with unit tests, including the 1 April rollover.
- [ ] T008 Extend the ArchUnit/Modulith test: no new cross-module dependency; settings read through the `settings` api only.

## Phase 3: US1 Record fee terms (P1)

Independent test: a Per term MoU with two terms and fees saves, shows monthly equivalents, and cannot be edited; a Monthly MoU is unchanged.

- [ ] T009 [US1] Extend the contract create service and `ContractsController` per contracts/fee-terms-api.md: basis, periods (overlap, gap and coverage validation), position fees, GST block with GSTIN validation and state code; fees written once; audit through `ChangeRecorder`; expose the extra fields on `SchoolContracts`.
- [ ] T010 [P] [US1] API tests `ContractFeeTermsApiTest`: valid Per term and Annual, overlapping and gapped terms refused, bad GSTIN refused, Monthly unchanged, no update path, Zone Manager reads but cannot create (403), Teacher and System refused, scope on read.
- [ ] T011 [US1] Frontend MoU form: `BillingBasisSection` (basis, term rows, fee per position, monthly equivalent) and `GstSection` (applies, GSTIN, unregistered); read-only display on the contract page; Vitest tests.

## Phase 4: US2 Period dues (P1)

Independent test: Term 1 raised with a mid-term joiner matches a hand calculation; duplicate refused; Monthly identical to 022.

- [ ] T012 [US2] Generalise `DueCalculator` and `DueService` from a month to a billing period (R1, R2): period key, fee per period, denominator over the period, one due per (School, contract, period), due date from the existing `billing.payment_due_days`, reference format per data-model.
- [ ] T013 [US2] `GET /periods` and period-aware `POST /dues` and `/dues/raise` in `ReceivablesController`; skipped reasons including missing working days and not started.
- [ ] T014 [P] [US2] Tests `PeriodDueApiTest` and `PeriodDueCalculatorTest`: whole term, joiner at day 40 of 130, exit after billing leaves the due unchanged, transfer between Schools, contract ends mid-term, concurrent raise (one wins), Monthly regression equals 022 results.
- [ ] T015 [US2] Frontend `RaisePeriodDialog` and period labels on the due list and School ledger; tests.

## Phase 5: US3 GST and tax lines (P1)

Independent test: the spec example gives CGST + SGST for same state and IGST otherwise; missing HLS settings refuse the raise.

- [ ] T016 [US3] Apply `TaxCalculator` in `DueService` for GST contracts: taxable value, tax lines, rate and state snapshot, total; refuse with the missing setting named; none for non-GST contracts.
- [ ] T017 [P] [US3] Tests `GstDueApiTest`: same state, other state, unregistered School (state from record), 0% rate, rate change leaves old dues, settings audit.
- [ ] T018 [US3] Frontend `TaxLines` on due detail and System Settings section for HLS tax details; tests.

## Phase 6: US4 Credit notes and collections with tax (P2)

- [ ] T019 [US4] `AdjustmentService`: taxable input, tax lines in the due's split, total checked against balance, reversal reverses tax lines; balances, ageing and allocations on totals (`LedgerQueryService`, `AgeingService`).
- [ ] T020 [P] [US4] Tests `GstAdjustmentApiTest` (spec example 10,000 taxable gives 11,800), cancel and reversal, collection pays the total, ageing on totals.

## Phase 7: US5 Tax invoice (P2, drop if Q5 = B)

- [ ] T021 [US5] `InvoiceNumbering` wired into the raise transaction; snapshot of HLS and School details on the due; `TaxInvoiceService` and `GET /dues/{id}/tax-invoice`; cancelled invoices keep their number.
- [ ] T022 [P] [US5] Tests `TaxInvoiceApiTest`: consecutive numbers under a 50-School bulk raise, no reuse after cancel, restart on 1 April, 404 for non-GST due and out-of-scope School.
- [ ] T023 [US5] Frontend `TaxInvoicePage` print view with print styles and words amount; tests.

## Phase 8: US6 Margin and tax summary (P2)

- [ ] T024 [US6] `SchoolReceivablesImpl`: `taxableAmount` (period spread over months, remainder in the last month) and `taxOfMonth`; balances stay on totals; update contract docs of 022's interface.
- [ ] T025 [US6] `TaxSummaryService`, `GET /tax-summary` and `.csv` (scope, formula-escaped CSV); frontend `TaxSummaryPage`.
- [ ] T026 [P] [US6] Tests `ReceivablesMarginTest` (months add up to the line, no tax in margin) and `TaxSummaryApiTest` (totals equal tax lines, Zone Manager scope).

## Phase 9: US7 Existing data and polish

- [ ] T027 [US7] Migration test `FeeTermsMigrationTest`: count and sum of dues, balances and ageing before and after the backfill are identical; a due raised afterwards on an old contract equals the 022 result.
- [ ] T028 Seed demo data (dev profile): a Per term GST contract, an Annual contract, HLS tax settings.
- [ ] T029 [P] Add amendment notes at the top of `specs/012-school-contracts/spec.md` and `data-model.md` and `specs/022-school-billing/spec.md` (Assumptions and FR-022) pointing to this spec; update the 012b row of `docs/spec-roadmap.md`.
- [ ] T030 [P] Postman requests and Selenium walkthrough for User Stories 1 to 6 under `e2e/`, following the existing walkthroughs.
- [ ] T031 Run the full backend and frontend suites, execute quickstart.md, record results in `quickstart-results.md`.

## Dependencies

T001 before everything; T004 to T007 before T009, T012, T016, T021; US1 before US2; US2 before US3, US4, US5, US6; US7 (T027) after T001 and again at the end.
