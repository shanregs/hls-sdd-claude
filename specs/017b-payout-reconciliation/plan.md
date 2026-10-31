# Implementation Plan: Substitute Payout Reconciliation

**Branch**: `spec/017b-payout-reconciliation` | **Date**: 2026-11-01 | **Spec**: [spec.md](spec.md)

**Status**: Planned on the recommended defaults of `clarification-questions.md` (Q1 to Q8, option A), awaiting owner answers.

## Summary

A new package `substitution.payout` inside the existing `substitution` module keeps one payout record per credit (substitute and
requirement) and drives it with the 017a events (`SubstitutionAssigned`, `SubstitutionEnded`) and a daily reconcile job. When a
credit's month is ended and locked (read through payroll's public interface) and on or after the go-live month, it prices the day
with `payroll.api.PayRules` and offers it through a new additive `payroll.api.SubstituteCreditIntake` (amendment B to 013b, the
same shape as `ReimbursementIntake` and `IncentiveIntake`), using the reserved adjustment kind `SUBSTITUTE_CREDIT`. A listener on
`PayrollAdjustmentApplied` marks the record "paid in run". Voids withdraw the offer while the run is a draft, wait while a run
awaits approval, and flag "correction needed" after approval; the Admin raises a Correction from the row through a payroll
public call. The web app gets `features/substitute-payouts` (list, totals, mismatches, detail, CSV) and one PAYROLL & FINANCE
item; My Substitutions (017a) gains a pay-status column. See [research.md](research.md).

## Technical Context

- Java 25, Spring Boot, Spring Modulith, JPA, Flyway, PostgreSQL (Testcontainers), React + Vite + Vitest. No new library.
- Migration: `V160__create_substitute_payouts.sql`. On `main` the highest is V150 (040 same-day flags); V130 incentives, V140 user
  preferences. V160 is the next decade slot. **Re-check against `origin/main` and open branches at implementation; the number may change.** No payroll migration: `V53` already allows `SUBSTITUTE_CREDIT` and has `source_ref`.
- Payroll amendment B (013b): `SubstituteCreditIntakeImpl` in `payroll.run.internal`, `payroll.api.SubstituteCreditIntake`, a small
  `AdjustmentService` change so manual recording of `SUBSTITUTE_CREDIT` stays refused with the updated text, a public
  `payroll.api.MonthLock` read (is the month ended and locked for this person) if none exists, and `CorrectionRaising` (raise a linked Correction on behalf of the Admin). Payslip label already reserved.
- Setting: `SettingKey.SUBSTITUTE_PAYOUT_START_MONTH` (`substitution.payout-start-month`, SYSTEM group, default the migration month).
- Money `BigDecimal`, two decimals, Rupees. Amount frozen on the payout record at offer; refreshed while unapplied.
- Constraints: unique `(requirement_id, substitute_teacher_id)`, append-only `substitute_payout_event`, no cross-module foreign
  keys, audit through `ChangeRecorder`, 404 for anything out of scope, business dates through `Clock`.
- Scale: tens to low hundreds of credits a month; lists page with batch lookups of names and Zones.
- Job: one daily reconcile (virtual thread, Principle VIII), idempotent, row lock per record.

## Constitution Check

| Principle | How it is met |
| --- | --- |
| I Auditable | Offer, withdraw, refresh, event-driven status change, repair and correction write `substitute_payout_event` (append-only trigger) and Change History with old and new values and actor or "reconcile job". |
| II Fixed roles, RBAC | New module `SUBSTITUTE_PAYOUTS(VIEW, EDIT, EXPORT)`. Seeded Admin `VIEW, EDIT, EXPORT`; Director `VIEW, EXPORT`; Manager `VIEW`. Teacher and System not eligible. |
| III Data scope | Admin and Director org-wide; Zone Manager by School Zone through `ScopeQueries`; Teacher only own credits through My Substitutions; else 404. |
| IV Role-based UI | Server-sent `actions` per row; one PAYROLL & FINANCE item requiring `VIEW`; amount hidden for Teacher until run approved. DD/MM/YYYY and Indian Rupee grouping. |
| V Formula-driven | The amount comes from the existing pay rules (day value); no rate duplicated; margin and billing untouched. |
| VI Training and Substitution | Fulfils "substitution assignment and payout reconciliation". |
| VII Modular monolith | `substitution.payout` reads `payroll.api`, `attendance`/`organization` apis for names and Zones, `settings`; `payroll` gains an api interface and does not depend on `substitution`. 014b and others read `substitution.api` only. |
| VIII Concurrency | Events and the job lock the payout row; the offer is idempotent per source reference; the job runs on virtual threads, one at a time (advisory lock). |
| IX Testability | Unit (state machine, pricing choices), API per endpoint/role/scope, integration with a real pay run (offer, apply, void after approval), duplicate event and job idempotency, regression on 013b/015/030 intakes. |
| X Security | Server re-checks grant and scope; reasons capped at 500; CSV neutralises formula cells; the client never sends an amount except in the Admin correction (bounded, audited). |

Constitution change: PATCH 2.5.17 (the next after 2.5.16 of spec 030; renumber to whichever parallel patch lands first): Default
role access matrix gains a Substitute Payouts row, navigation gains PAYROLL & FINANCE "Substitute Payouts". No principle changes.
The Teacher endpoint `GET /api/substitute-payouts/mine` needs no grant beyond sign-in as a Teacher (like My Substitutions in 017a) and returns only the caller's own records.
Gate result: pass before research and after design, no violations.

## Project Structure

### Documentation (this feature)

```text
specs/017b-payout-reconciliation/
  spec.md  clarification-questions.md  plan.md  research.md  data-model.md  quickstart.md  tasks.md
  contracts/ payout-api.md  payroll-substitute-credit-intake.md
  checklists/requirements.md
```

### Source Code

```text
backend/src/main/java/com/hls/substitution/payout/   (domain, service, listeners, reconcile job, web controller, CSV)
backend/src/main/java/com/hls/payroll/api/SubstituteCreditIntake.java (+ MonthLock, CorrectionRaising if missing)
backend/src/main/java/com/hls/payroll/run/internal/SubstituteCreditIntakeImpl.java
backend/src/main/resources/db/migration/V160__create_substitute_payouts.sql   (number to re-check)
frontend/src/features/substitute-payouts/  and a status column in the 017a My Substitutions page
```

**Structure Decision**: stay inside `substitution` (017a) so the credit and its payout share a module owner; payroll stays unaware.
