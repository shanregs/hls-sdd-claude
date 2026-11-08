# Constitution draft (input to /speckit-constitution)

Project: HLS Lite, a workforce, school-contract, payroll and billing system for about 200 Teachers, 15-20 Managers and 50 Schools.

## Principles

1. **Money is correct and explainable.** Every pay and invoice figure is reproducible from stored inputs. Rules follow `docs/baseline/03-business-rules.md`. Its worked examples are automated tests written before the code. Amounts are `numeric(12,2)`, rounded half-up per component.
2. **History is append-only where it affects money.** Salary, placements, invoices, payments and finalised payroll lines are never overwritten. Corrections are new rows (adjustments, negative payments, credit notes).
3. **Everything that changes people, assignments, attendance corrections, money, approvals, settings or permissions writes to the audit log**, through one helper, in the same transaction.
4. **Scope is enforced on the server.** Every request passes a permission check (editable matrix) and then a scope check in code (Admin and Director all, Manager assigned Schools, Teacher self). Tests cover allowed, denied and out-of-scope cases for each endpoint.
5. **Business date is IST.** All "today", month and cut-off logic goes through one `BusinessClock`. Timestamps are stored in UTC. No raw `LocalDate.now()` in business code.
6. **Simple by design.** One monolith, one PostgreSQL schema, direct calls in one transaction. No cache, event bus, outbox, background business jobs, read models or report framework. Derived values are queries.
7. **Real foreign keys and database constraints** carry invariants (no overlapping placements, no double-booked Substitute, one incentive per School). Application checks are not a substitute.
8. **Specs describe behaviour, not code.** Each spec has prioritised user stories with acceptance scenarios, requirement ids from the baseline and measurable success criteria.
9. **Observable in production.** Structured logs with correlation ids, metrics, traces and health checks are part of the definition of done for each feature. No secrets, PII or salary data in logs.
10. **Usable on a phone first.** Screens are responsive and keyboard accessible. Teachers and Managers use the app on phones.
11. **Tests are real.** Integration tests run against PostgreSQL, not an in-memory substitute. Money and permission rules have unit tests. Main flows have browser end-to-end tests.
12. **Changes to money rules need an owner decision.** Add or alter an entry in `02-decisions-and-assumptions.md` first; then update the spec and tests.

## Technology constraints
React and TypeScript (PWA), Java 25 with latest GA Spring Boot, PostgreSQL latest stable community, Flyway migrations. Details in `06-architecture-and-nfr.md`.

## Workflow
Specs follow `08-spec-roadmap.md`. Each spec goes through specify, clarify, plan, tasks, analyze and implement, merged by pull request with all tests green.
