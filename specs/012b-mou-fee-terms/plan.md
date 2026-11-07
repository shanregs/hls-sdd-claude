# Implementation Plan: MoU Fee Terms (Billing Basis and GST)

**Branch**: `spec/012b-mou-fee-terms` | **Date**: 2026-11-07 | **Spec**: [spec.md](spec.md)

## Summary

An amendment inside the existing `schoolbilling` module (specs 012 and 022). The contract gains write-once **fee terms**: a billing basis (Monthly, Per term, Annual), its billing periods, a fee per position per period, and GST details. The raise-due service of 022 is generalised from a month to a **billing period**, the proration denominator becomes the sum of the School's month working days over the period (spec 037), and a due on a GST contract stores a taxable value, tax lines and a total. A gapless per-financial-year invoice sequence, a printable tax invoice view, a monthly tax summary and margin figures on the taxable value complete it. Payment due days remain the 022 setting. Existing contracts and dues are migrated as Monthly, no GST, with no value change.

## Technical Context

- Java 25, Spring Boot, Spring Modulith, JPA, Flyway, PostgreSQL (Testcontainers), React + Vite + Vitest. Maven needs `JAVA_HOME` set to JDK 25.
- Migration: `V170__contract_fee_terms.sql` (highest planned on main is V161; renumber at merge if another lands first).
- Money `numeric(14,2)`, rounding HALF_UP at each line and each tax line. The amount in words is a pure function with unit tests (lakh, crore).
- Constraints: fee terms, periods, tax lines and invoice numbers are write-once (the existing 012 and 022 triggers are extended); no cross-module foreign keys; audit through `ChangeRecorder`; dates through `WorkingCalendar.businessToday()`.
- No new module, no new permission module, no new menu item.

## Constitution Check

| Principle | How it is met |
| --- | --- |
| I Auditable | Fee terms, HLS tax settings, raises, invoice numbers, cancellations and credit notes go to Change History; tax lines and numbers are write-once by trigger. |
| II Fixed roles, RBAC | No new permission module. Record terms uses `SCHOOL_CONTRACTS` create; tax summary uses `SCHOOL_BILLING` export; settings use the existing System Settings permission. |
| III Data scope | Zone Manager sees own Schools' terms, dues and invoices only; scope through `ScopeQueries` on every new endpoint; out-of-scope ids give 404. |
| IV Role-based UI | Existing menu items; new actions shown only when granted. |
| V Formula-driven receivables and margin | Amounts, tax and monthly equivalents are computed from contract terms and assignments, never typed. Margin is explainable and excludes tax. |
| VII Modular monolith | All new code in `schoolbilling`; settings read through the `settings` api; `notification`, `reports` and `payroll` keep reading only `schoolbilling.api`. ArchUnit test unchanged. |
| VIII Concurrency | Invoice number allocated by a locked counter row per financial year inside the raise transaction (gapless); the unique (school, period) index keeps one due per period. |
| IX Testability | Unit tests for proration, tax split, rounding, GSTIN check, financial year and words; API tests per role and scope; concurrency test for numbering; migration test (before and after sums). |
| X Security | Every endpoint re-checks grant and scope; CSV export escapes formula injection; GSTIN is validated and is not personal data. |

**Constitution change**: none. No principle changes, no new matrix row, no new navigation item, so no version bump and no Sync Impact entry. If the owner asks (Q-list) for a separate permission to see the tax summary, add a PATCH entry (next free number after the latest merged one) and a matrix row at that time.

**Amendments to earlier specs** (done by this spec's tasks, as notes at the top of each file): `specs/012-school-contracts/spec.md` and `data-model.md` (cycle values, fee terms), `specs/022-school-billing/spec.md` (Assumptions and FR-022), and the roadmap row 012b status.

## Project Structure

```text
specs/012b-mou-fee-terms/ spec.md plan.md research.md data-model.md quickstart.md tasks.md clarification-questions.md contracts/
backend/src/main/java/com/hls/schoolbilling/
  api/       SchoolContracts (adds feeTerms, billingPeriods), SchoolReceivables (adds taxableAmount, taxOfMonth), BillingPeriod, FeeTerms
  internal/  ContractFeeTerms, BillingPeriodCalculator, TaxCalculator, GstinValidator, InvoiceNumbering,
             DueCalculator (period denominator), DueService (periods, tax), AdjustmentService (tax),
             TaxInvoiceService, TaxSummaryService, HlsTaxSettings
  web/       ContractsController (fee terms), ReceivablesController (period raise), TaxInvoiceController, TaxSummaryController
backend/src/main/resources/db/migration/V170__contract_fee_terms.sql
frontend/src/features/schoolbilling/ (contracts form: BillingBasisSection, GstSection; receivables: RaisePeriodDialog,
  TaxLines, TaxInvoicePage, TaxSummaryPage; tests)
```

## Design Decisions

See [research.md](research.md). Highlights: a billing period is a derived value for Monthly and Annual and a stored row for Per term; a due keys on (School, contract, period start); tax is computed once at raise time and stored; margin reads the monthly equivalent of the taxable line; due days are read from the existing `BillingSettings` and nothing new is added for them.

## Complexity Tracking

No constitution violations. Largest risk is legal correctness of the tax invoice (Q3 to Q6); the build waits for the owner and accountant on those, and User Story 5 can be dropped without touching the rest.
