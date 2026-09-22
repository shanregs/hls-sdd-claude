# HLS SDD Implementation Plan & Deliverables Tracker

2026-09-18 · @Someone

A working plan to build the HLS Teacher Management System as a modular monolith using GitHub spec-kit's Spec-Driven Development workflow, structured as the learning vehicle for SDD itself. Companion to the [requirements doc](https://claude.ai/code/artifact/665d38e4-4058-47eb-8ffb-b2c9a66f236e).

## 1. Objective & Approach

The primary objective is to learn Spec-Driven Development properly, using this project as the vehicle rather than the goal in itself. Two rules follow from that and govern everything below: use the **full path** (`constitution → specify → clarify → plan → checklist → tasks → analyze → implement → converge`) on every module rather than the shortcut, and treat a wrong result as a signal to fix the spec or plan first — not the generated code directly.

Architecture is settled as a **modular monolith**: one Spring Boot application (Java 25+, Spring Boot 4.1.1-based), with hard package boundaries per bounded context enforced by ArchUnit, and an explicit extraction path to microservices later if scale or team size ever justifies it.

Sequencing rule: a throwaway warm-up feature first (to learn the workflow safely), then the modules in corrected data-dependency order — Identity/Access, with Organization (Manager records + Manager–School scoping) and Audit (the append-only store) built alongside it as foundational, cross-cutting modules; then master data (Teacher Master Data → School Master Data → SchoolBilling, since SchoolBilling's Contract now reads Teacher/School only through their public APIs rather than owning that data itself); then Attendance and School Payment as siblings (both depend only on Teacher/School/SchoolBilling, not on each other); then Payroll (highest risk — depends on both Attendance and SchoolBilling, done once the rhythm is familiar), Expenses, Training, Substitution — then Reporting (depends on most other modules' public APIs, so it trails them), then integration and deployment. *(Reordered 2026-09-21: a dependency review found Teacher, School and Contract master data had no dedicated module in the original sequence and were silently assumed to pre-exist before Attendance — see Section 7. Further refined 2026-09-21: constitution v1.5.0 split `roster` into `teacher`/`school` and added `organization`, `reporting` and `audit` as their own modules — see Section 7.)*

## 2. Environment & Tooling Setup

- [ ] Install Python 3.11+ and [uv](https://docs.astral.sh/uv/)
- [ ] `uv tool install specify-cli`
- [ ] `specify init --integration claude` in the project repo root, confirm `.specify/` and `.claude/` are created
- [ ] Claude Code plugin installed and working in IntelliJ against this repo
- [ ] Git branching workflow adopted: one short-lived feature branch per spec-kit module, merge only after `/speckit.converge` passes
- [ ] Repo skeleton created: `specs/`, `adr/`, `design/`, `tasks/` folders alongside `.specify/`
- [ ] PostgreSQL or MySQL running locally for development (match the choice recorded in ADR-0002)

## 3. Constitution Principles (`/speckit.constitution`) — v1.5.0, ratified 2026-09-18, amended 2026-09-21

Run once, before Module 0, and encode (full text kept in `.specify/memory/constitution.md`):

1. **Platform**: Java 25+, Spring Boot 4.1.1-based backend.
2. **Architecture**: modular monolith; one package per bounded context (`identity`, `organization`, `teacher`, `school`, `schoolbilling`, `attendance`, `payroll`, `expense`, `training`, `substitution`, recruitment (with a marketing sub-package), `reporting`, `audit`); `teacher` owns Teacher master data, `school` owns School master data, `schoolbilling` owns the Teacher–School–Manager Contract plus receivables (reading Teacher/School only via their public APIs), `organization` owns Manager records and Manager–School scoping, `reporting` is read-only across other modules' public APIs/events, and `audit` is the single append-only store implementing Principle I; cross-module calls only through a module's public interface, enforced by ArchUnit/Spring Modulith rules that fail the build on violation. No microservices by default — extraction only if scale or team size later justifies it.
3. **Data**: relational DB, PostgreSQL as the working default pending ADR-0002; financial tables (SchoolPayment, TeacherPayout, Expense) are append-friendly with audit/history rows, never silent overwrites.
4. **Concurrency**: virtual-thread-per-task (or structured concurrency) for payroll computation and attendance rollups, both triggered on demand by Admin/Director — no fixed monthly schedule, and no hand-rolled thread pools in the request path.
5. **Testing**: test-first for any module touching money (payroll, margin, school payments) or attendance-derived pay; acceptance tests trace directly to a spec's Given/When/Then.
6. **Deployment target**: single EC2/VM in `ap-south-1` (or equivalent India region), Docker Compose, sized for <100 users growing \~30%/12 months — no premature horizontal scaling or orchestration.
7. **Training & Substitution parity**: both are first-class operational workflows, on equal footing with regular school attendance and payroll — not secondary features.
8. **Recruitment & Marketing Are Tracked to Outcome**: campus recruitment drives (college, date, venue, interviewers) track candidates through interview → offer → acceptance → onboarding linkage, as a full workflow, not just a calendar; marketing/sales calendar entries track outreach activity to its outcome (contract signed / follow-up / declined), modeled as a recruitment.marketing sub-package rather than its own bounded context, and kept intentionally lightweight for v1.
9. **Security, Identity & Observability**: JWT (short-lived access + rotating refresh) for API auth, OTP-based mobile login for teachers, RBAC combined with attribute-based manager/school scoping on every endpoint, secrets kept out of source control, and structured logging + metrics + basic alerting (disk, DB pool, failed payroll runs) from day one — not retrofitted after launch.

These nine become the checks `/speckit.analyze` and `/speckit.converge` hold every module's spec/plan/code against.

## 4. Deliverables Tracker

One row per spec-kit-driven deliverable, in corrected data-dependency build order (see Section 1). Target dates are placeholder estimates (roughly two weeks per real module, one week per lighter-weight master-data module) — adjust to your real pace once Module 0 and the warm-up feature tell you how long a full path actually takes.

| # | Phase / Module | Key Deliverable | Status | Target Date |
| --- | --- | --- | --- | --- |
| 0 | Setup & Tooling | `specify-cli` installed, repo scaffolded (`specs/`, `.specify/`, `.claude/`), Claude Code working | Done | 2026-09-22 |
| 1 | Warm-up feature | One trivial feature (System Status Page) run end-to-end through all 9 spec-kit steps, purely to learn the workflow | Spec drafted | 2026-09-26 |
| 2 | Constitution | `/speckit.constitution` capturing the nine principles in Section 3 | Done (v1.5.0) | 2026-09-29 |
| 3 | Identity & Access module | Login, roles (Director/Manager/Admin/Teacher/AO), per-manager data scoping | Done — full spec/plan/tasks/implement/analyze cycle run 2026-09-21/22; backend (Spring Security, JWT, OTP, lockout, sessions) and frontend done; `ManagerScopeGuard` now calls Organization's real `AccountabilityQueries` (its temporary stand-in was retired 2026-09-22 alongside module 4 — see Section 7) | 2026-10-10 |
| 4 | Organization module | Manager records and the Manager–School assignment that all other modules' scoping checks read; depends on Identity | Done — full spec/plan/tasks/implement/analyze cycle run 2026-09-21/22; assign/reassign/history/portfolio/unassigned all implemented and tested (backend unit + Testcontainers integration + frontend `AssignmentsPage`), Identity's stand-in retired in favor of this module's real `AccountabilityQueries`/`AccountabilityCommands` | 2026-10-17 |
| 5 | Audit module | The single append-only audit-event store implementing Principle I; depends only on Identity (actor identity) | Done — full spec/plan/tasks/implement cycle run 2026-09-22; `AuditWriter`/`AuditReader` public API, append-only `audit_entry` table (no update/delete method anywhere, mirroring Identity's own `AuthAuditEntryRepository` pattern), Director/Admin-only history endpoint, and frontend `AuditHistoryPage` all implemented and tested (backend unit + Testcontainers integration + frontend); no financial/attendance module exists yet to call it for real, so the write path is proven by tests calling `AuditWriter` directly (research.md §1) — future modules (Payroll, School Payment, Expense, Substitution) will call it once built | 2026-10-24 |
| 6 | Teacher Master Data module | Teacher profile, bank details, HLS-offered salary, status (in training / active / on leave / exited) | Not started | 2026-10-31 |
| 7 | School Master Data module | School profile, billing contact, assigned manager | Not started | 2026-11-07 |
| 8 | SchoolBilling module | Teacher–School–Manager Contract (rate, billing terms) plus School Payment/receivables tracking, balance, partial payments (Sections 4–5 of requirements); reads Teacher/School only via their public APIs; depends on modules 6–7 | Not started | 2026-11-21 |
| 9 | Attendance module | Daily capture, status codes, monthly rollups (Section 3 of requirements); depends on modules 6–7 and Organization | Not started | 2026-12-05 |
| 10 | Payroll / Payout module | Net Salary + Margin engine, payslips, rounding rules (Section 5); depends on Attendance and SchoolBilling | Not started | 2026-12-26 |
| 11 | Expense module | Manager expense claims, categorization, approval threshold (Section 6) | Not started | 2027-01-09 |
| 12 | Training module | Weekend + recurring monthly training calendar, teacher/manager assignment with attendance obligation, one-month onboarding (Section 8, updated 2026-09-18) | Not started | 2027-01-23 |
| 13 | Substitution module | Substitute assignment and payout reconciliation (Section 7); depends on Teacher Master Data and Attendance | Not started | 2027-02-06 |
| 14 | Reporting module | Director/Manager/Teacher dashboards and CSV/PDF export, read-only across other modules' public APIs/events; depends on most prior modules, so it trails them | Not started | 2027-02-20 |
| 15 | Integration & UAT | All modules wired together, Director/Manager walkthrough against real August data | Not started | 2027-03-06 |
| 16 | EC2/VM Deployment | Docker Compose stack live in `ap-south-1` (or equivalent), TLS, backups, CI/CD | Not started | 2027-03-20 |
| 17 | Campus Recruitment (Campus Hiring) | Campus recruitment calendar + candidate/offer tracking through to onboarding, including the marketing sub-package for school outreach (Sections 9 & 9a) | Not started | 2027-04-03 (estimate) |
| 18 | Marketing & Sales Calendar | Outreach calendar + outcome logging, lightweight for v1 — sub-package of recruitment, specified alongside it (Section 9a) | Not started | 2027-04-17 (estimate) |

Update the Status dropdown as you go — this table is the single place to see where the project actually stands.

## 5. Definition of Done (per module)

A module only moves to Done in the tracker above when all of the following hold:

1. `/speckit.specify` through `/speckit.converge` have all run for it, in order, with no step skipped.
2. Every task in `/speckit.tasks` traces to a specific line in the spec — no task exists that isn't answering a spec requirement.
3. Every generated test traces to a Given/When/Then from the spec or from `/speckit.clarify` — not invented after the fact from the implementation.
4. `/speckit.analyze` reports no unresolved conflicts between spec, plan and tasks.
5. ArchUnit boundary tests pass — the module hasn't reached into another module's internals.
6. For financial modules (Payroll, School Payment, Expenses): tests were written before the implementation, and at least one test exercises the edge case already flagged in the requirements doc (e.g. missing school payment producing negative margin).
7. You have personally reviewed the code diff in IntelliJ, not just accepted it.

## 6. Milestones

| Milestone | Target | What it proves |
| --- | --- | --- |
| Constitution + warm-up complete | 2026-09-29 | You can run all 9 spec-kit steps without getting lost |
| Foundational modules (Identity, Organization, Audit) shipped | 2026-10-24 | The cross-cutting scoping and audit fabric every other module reads/writes through exists first |
| Master data (Teacher, School, SchoolBilling) shipped | 2026-11-21 | The reference data every transactional module depends on exists and is correctly modeled |
| First transactional module (Attendance) shipped | 2026-12-05 | The workflow holds up on actual HLS domain rules once real dependencies are in place |
| Payroll module shipped | 2026-12-26 | You can apply SDD rigor to the highest-risk, formula-heavy module |
| Integration & UAT complete | 2027-03-06 | All modules work together against real historical data |
| Live on EC2/VM | 2027-03-20 | The system is actually usable by the Director, managers and teachers |

## 7. Risks & Open Items

- The open questions logged in the requirements doc (Section 12 — the `S` attendance code, negative-margin handling, fixed-salary staff modeling, substitution rules) should each be resolved during that module's `/speckit.clarify` step, not deferred silently into the implementation.
- Biggest workflow risk for a beginner: skipping `/speckit.analyze` because it feels like overhead — budget time for it explicitly in the Payroll module especially, since that's where a silent spec/plan mismatch is most costly.
- ADR-0002 (DB choice) and ADR-0003 (deployment target) are referenced above but not yet written — write both before Module 0 starts, since the constitution depends on them.
- Target dates in Sections 4 and 6 are estimates set before you've run a single module — revisit them after the warm-up feature and Module 0, when you have a real sense of pace.
- **Resolved 2026-09-21**: the original module order (Identity → Attendance → School Payment → Payroll → Expenses → Training → Substitution) implicitly assumed Teacher, School and Contract master data already existed before Attendance could be built. There was no dedicated module for any of the three. The tracker (Sections 1, 3, 4, 6) and the constitution (Principle V, now v1.4.0 — added the `roster` bounded context for Teacher/Contract and clarified `schoolbilling` owns School master data) have been corrected to insert Teacher Master Data, School Master Data and Teacher–School Contract as their own modules ahead of Attendance, and to note that Attendance and School Payment are dependency siblings (both need only Contract) rather than strictly sequential.
- **Resolved 2026-09-21 (later same day)**: `docs/hls-teacher-management-architecture.md` was added as a detailed target-architecture reference and proposed a richer module breakdown than Principle V had — splitting `roster` into standalone `teacher` and `school` modules, and adding `organization`, `reporting` and `audit` as their own bounded contexts. The constitution has been amended to v1.5.0 to adopt this breakdown (see the amendment note in `.specify/memory/constitution.md`), and this tracker's Sections 1, 3, 4 and 6 have been updated to match: Organization and Audit are now built alongside Identity as foundational modules; SchoolBilling absorbs the former separate "Teacher–School Contract" and "School Payment" rows into one module; Reporting is added as its own module, sequenced late since it depends on most other modules' public APIs. Separately, an editing pass had briefly reverted this file and the requirements doc to pre-v1.4.0 content (microservices, JDK 21+, Spring Boot 3.5.x, no roster/schoolbilling split) — that revert was discarded in favor of the ratified constitution before this amendment was applied.
- **Resolved 2026-09-22**: Identity & Access (module 3) was implemented before Organization (module 4), even though the table above still lists Identity first — this held up fine in practice. Identity's own `/speckit-clarify` pass (2026-09-21) had already decided Manager-scope checks must read Organization live rather than duplicate its data, which meant Identity's `ManagerScopeGuard` needed *something* to call before Organization exists. The implementation used a clearly-marked local stand-in (`OrganizationAccountabilityStandIn` / `InMemoryOrganizationStandIn`, in `backend/src/main/java/com/hls/identity/internal/`) matching Organization's published contract, with a documented removal plan for when module 4 shipped. (Note: an earlier version of this entry also mentioned a planned `identity.api.CurrentUserResolver`/`organization.internal.CurrentUserResolver` port pair — that design was dropped during Organization's 2026-09-22 re-plan, in favor of both modules independently reading Spring Security's `@AuthenticationPrincipal Jwt` directly, which avoids the identity↔organization dependency cycle that port would otherwise have risked.) **Action taken**: Organization's `AccountabilityQueries`/`AccountabilityCommands` were implemented for real (spec 003 tasks.md T001–T028), then `ManagerScopeGuard` was swapped from the stand-in to the real interface and `OrganizationAccountabilityStandIn`/`InMemoryOrganizationStandIn` were deleted (T032). Doing this surfaced two Spring Modulith gaps that had been latent since Identity's own module boundary was never previously exercised by a real cross-module Spring bean dependency: (1) neither module's `api` package was annotated `@NamedInterface`, so Spring Modulith treated both as fully internal and rejected the new dependency; (2) `IdentityModuleTest`'s `@ApplicationModuleTest` used the default `BootstrapMode.STANDALONE`, which can no longer boot now that `ManagerScopeGuard` has a real bean dependency on Organization — switched to `BootstrapMode.DIRECT_DEPENDENCIES`. Both are fixed; the full ArchUnit + Spring Modulith verification suite (`ArchitectureTest`, `ApplicationModulesTest`, `IdentityModuleTest`, `OrganizationModuleTest`) and the full backend test suite (52/52) pass.
- Also discovered during Identity's implementation, worth knowing before building module 4 (or any later module): Spring Boot 4.1.1 split several things this project's plan.md assumed were bundled — Flyway's Spring glue is now a separate `spring-boot-flyway` artifact (not just `flyway-core`), MockMvc's test support moved to `spring-boot-starter-webmvc-test`, and the auto-configured `ObjectMapper` is now Jackson **3** (`tools.jackson.databind.ObjectMapper`, not the classic `com.fasterxml.jackson.databind.ObjectMapper`). All three are already fixed in `backend/pom.xml` for Identity, but every later module's own dependency additions should expect the same fine-grained module split.
