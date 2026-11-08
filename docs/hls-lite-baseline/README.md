# HLS Lite: requirements baseline v2

Status: baseline for a new, separate project. Written 2026-11-08 from `docs/simplified-requirements.md` plus the owner's answers to the review questions.
Everything here is in scope for release 1. There is no "phase 2".

## Files

| File | Purpose | Spec Kit use |
|------|---------|--------------|
| `01-requirements.md` | All requirements, one testable statement each, grouped by module (ids `PLT-`, `MST-`, ...) | Source for `/speckit-specify` |
| `02-decisions-and-assumptions.md` | Every owner decision with its source, plus assumptions still to confirm | Read before `/speckit-clarify` |
| `03-business-rules.md` | Pay, leave, billing, substitution, statuses, with worked examples | Test data for payroll and billing specs |
| `04-roles-permissions.md` | Six roles, scope rules, default permission matrix (editable) | Seed data and the access-model spec |
| `05-data-model.md` | 52-table logical model with key columns and constraints | Input to `/speckit-plan` |
| `06-architecture-and-nfr.md` | Stack, observability, security, operations | Input to `/speckit-constitution` and `/speckit-plan` |
| `07-constitution-draft.md` | Draft project principles | Paste into `/speckit-constitution` |
| `08-spec-roadmap.md` | 21 specs in build order, each with a ready-to-paste feature description | Drive `/speckit-specify` one by one |

## How to use in the new project

1. Create the repo. Copy this folder to `docs/baseline/`.
2. Run `/speckit-constitution` with `07-constitution-draft.md` as input.
3. Resolve the "Assumptions to confirm" list in `02-decisions-and-assumptions.md` with the owner. Do this before specs 10 (payroll) and 11 (billing); the other specs can start earlier.
4. For each spec in `08-spec-roadmap.md`, in order: `/speckit-specify` (paste the description), `/speckit-clarify`, `/speckit-plan` (tell it to follow `05` and `06`), `/speckit-tasks`, `/speckit-analyze`, `/speckit-implement`.
5. Keep `03-business-rules.md` worked examples as automated tests. These money rules are the costly ones to get wrong.

## What changed from the earlier simplified draft

- Six roles (System Engineer added) with an **editable** permission matrix.
- Payroll: Admin runs and finalises, Director only acknowledges.
- Substitute day rate is entered per assignment.
- Pay and billing use actual calendar days of the month. The per-School working-days table is gone.
- Everything is in release 1: recruitment, training, marketing, tasks, observations, transfers, content.
- Expense budgets and GST are out. Expenses report is a monthly total by Zone and category.
- Stack fixed: React PWA, Java 25 with Spring Boot, PostgreSQL, full logging, metrics and observability.
