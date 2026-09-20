# HLS SDD Implementation Plan & Deliverables Tracker

2026-09-18 · @Someone

A working plan to build the HLS Teacher Management System as a modular monolith using GitHub spec-kit's Spec-Driven Development workflow, structured as the learning vehicle for SDD itself. Companion to the [requirements doc](https://claude.ai/code/artifact/665d38e4-4058-47eb-8ffb-b2c9a66f236e).

## 1. Objective & Approach

The primary objective is to learn Spec-Driven Development properly, using this project as the vehicle rather than the goal in itself. Two rules follow from that and govern everything below: use the **full path** (`constitution → specify → clarify → plan → checklist → tasks → analyze → implement → converge`) on every module rather than the shortcut, and treat a wrong result as a signal to fix the spec or plan first — not the generated code directly.

Architecture is settled as a **modular monolith**: one Spring Boot application (Java 25+, Spring Boot 4.1.1-based), with hard package boundaries per bounded context enforced by ArchUnit, and an explicit extraction path to microservices later if scale or team size ever justifies it.

Sequencing rule: a throwaway warm-up feature first (to learn the workflow safely), then the modules in corrected data-dependency order — Identity/Access; then master data (Teacher Master Data → School Master Data → Teacher–School Contract, since Attendance, School Payment and Substitution all transact against an existing Contract); then Attendance and School Payment as siblings (both depend only on Contract, not on each other); then Payroll (highest risk — depends on both Attendance and School Payment, done once the rhythm is familiar), Expenses, Training, Substitution — then integration and deployment. *(Reordered 2026-09-21: a dependency review found Teacher, School and Contract master data had no dedicated module in the original sequence and were silently assumed to pre-exist before Attendance — see Section 7.)*

## 2. Environment & Tooling Setup

- [ ] Install Python 3.11+ and [uv](https://docs.astral.sh/uv/)
- [ ] `uv tool install specify-cli`
- [ ] `specify init --integration claude` in the project repo root, confirm `.specify/` and `.claude/` are created
- [ ] Claude Code plugin installed and working in IntelliJ against this repo
- [ ] Git branching workflow adopted: one short-lived feature branch per spec-kit module, merge only after `/speckit.converge` passes
- [ ] Repo skeleton created: `specs/`, `adr/`, `design/`, `tasks/` folders alongside `.specify/`
- [ ] PostgreSQL or MySQL running locally for development (match the choice recorded in ADR-0002)

## 3. Constitution Principles (`/speckit.constitution`) — v1.4.0, ratified 2026-09-18, amended 2026-09-21

Run once, before Module 0, and encode (full text kept in `.specify/memory/constitution.md`):

1. **Platform**: Java 25+, Spring Boot 4.1.1-based backend.
2. **Architecture**: modular monolith; one package per bounded context (`identity`, `roster`, `schoolbilling`, `attendance`, `payroll`, `expense`, `training`, `substitution`, recruitment (with a marketing sub-package)); `roster` owns Teacher master data and the Teacher–School–Manager Contract, `schoolbilling` owns School master data plus receivables; cross-module calls only through a module's public interface, enforced by ArchUnit rules that fail the build on violation. No microservices by default — extraction only if scale or team size later justifies it.
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
| 2 | Constitution | `/speckit.constitution` capturing the nine principles in Section 3 | Done (v1.4.0) | 2026-09-29 |
| 3 | Identity & Access module | Login, roles (Director/Manager/Admin/Teacher/AO), per-manager data scoping | Not started | 2026-10-10 |
| 4 | Teacher Master Data module | Teacher profile, bank details, HLS-offered salary, status (in training / active / on leave / exited) | Not started | 2026-10-17 |
| 5 | School Master Data module | School profile, contracted rate, billing contact, assigned manager | Not started | 2026-10-24 |
| 6 | Teacher–School Contract module | Links Teacher + School + Manager, start/end date, contracted school salary, HLS-offered salary — the reference data Attendance, School Payment and Substitution all transact against | Not started | 2026-10-31 |
| 7 | Attendance module | Daily capture, status codes, monthly rollups (Section 3 of requirements); depends on modules 4–6 | Not started | 2026-11-14 |
| 8 | School Payment module | Expected vs. received tracking, balance, partial payments (Section 4); depends on modules 5–6, sibling of Attendance (not sequenced after it by data dependency, built after here for team-pace reasons) | Not started | 2026-11-28 |
| 9 | Payroll / Payout module | Net Salary + Margin engine, payslips, rounding rules (Section 5); depends on modules 6, 7 and 8 | Not started | 2026-12-19 |
| 10 | Expense module | Manager expense claims, categorization, approval threshold (Section 6) | Not started | 2027-01-02 |
| 11 | Training module | Weekend + recurring monthly training calendar, teacher/manager assignment with attendance obligation, one-month onboarding (Section 8, updated 2026-09-18) | Not started | 2027-01-16 |
| 12 | Substitution module | Substitute assignment and payout reconciliation (Section 7); depends on modules 6 and 7 | Not started | 2027-01-30 |
| 13 | Integration & UAT | All modules wired together, Director/Manager walkthrough against real August data | Not started | 2027-02-13 |
| 14 | EC2/VM Deployment | Docker Compose stack live in `ap-south-1` (or equivalent), TLS, backups, CI/CD | Not started | 2027-02-27 |
| 15 | Campus Recruitment (Campus Hiring) | Campus recruitment calendar + candidate/offer tracking through to onboarding, including the marketing sub-package for school outreach (Sections 9 & 9a) | Not started | 2027-03-13 (estimate) |
| 16 | Marketing & Sales Calendar | Outreach calendar + outcome logging, lightweight for v1 — sub-package of recruitment, specified alongside it (Section 9a) | Not started | 2027-03-27 (estimate) |

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
| Master data (Teacher, School, Contract) shipped | 2026-10-31 | The reference data every transactional module depends on exists and is correctly modeled |
| First transactional module (Attendance) shipped | 2026-11-14 | The workflow holds up on actual HLS domain rules once real dependencies are in place |
| Payroll module shipped | 2026-12-19 | You can apply SDD rigor to the highest-risk, formula-heavy module |
| Integration & UAT complete | 2027-02-13 | All modules work together against real historical data |
| Live on EC2/VM | 2027-02-27 | The system is actually usable by the Director, managers and teachers |

## 7. Risks & Open Items

- The open questions logged in the requirements doc (Section 12 — the `S` attendance code, negative-margin handling, fixed-salary staff modeling, substitution rules) should each be resolved during that module's `/speckit.clarify` step, not deferred silently into the implementation.
- Biggest workflow risk for a beginner: skipping `/speckit.analyze` because it feels like overhead — budget time for it explicitly in the Payroll module especially, since that's where a silent spec/plan mismatch is most costly.
- ADR-0002 (DB choice) and ADR-0003 (deployment target) are referenced above but not yet written — write both before Module 0 starts, since the constitution depends on them.
- Target dates in Sections 4 and 6 are estimates set before you've run a single module — revisit them after the warm-up feature and Module 0, when you have a real sense of pace.
- **Resolved 2026-09-21**: the original module order (Identity → Attendance → School Payment → Payroll → Expenses → Training → Substitution) implicitly assumed Teacher, School and Contract master data already existed before Attendance could be built. There was no dedicated module for any of the three. The tracker (Sections 1, 3, 4, 6) and the constitution (Principle V, now v1.4.0 — added the `roster` bounded context for Teacher/Contract and clarified `schoolbilling` owns School master data) have been corrected to insert Teacher Master Data, School Master Data and Teacher–School Contract as their own modules ahead of Attendance, and to note that Attendance and School Payment are dependency siblings (both need only Contract) rather than strictly sequential.
