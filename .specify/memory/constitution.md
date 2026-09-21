# HLS Teacher Management System Constitution

## Core Principles

### I. Operational Truth Must Be Auditable
The system shall treat attendance, salary, school receivables, expenses, and training records as operational truth, not as convenience fields. Every financial or attendance change must be traceable to the actor, timestamp, and prior value through an immutable audit trail.

### II. Role-Scoped Access and Manager Ownership
Access is governed by role and operational scope: the Director sees the full organization, managers operate only within assigned schools and teachers, teachers access only their own records, and the System Assistant/Admin role is restricted to master-data maintenance and reconciliation workflows, not payroll approval authority. Data scoping must prevent cross-manager leakage and must enforce clear ownership for collections, payouts, and approvals.

### III. Payroll, School Receivables, and Margin Must Be Formula-Driven
The system shall compute payroll, school receivables, and margin from the canonical data model rather than re-entered spreadsheet formulas. Monthly salary must be derived from attendance, HLS-offered salary, school contract rate, and rounding rules; receivables must be generated from active teacher assignments and contract terms; and margin must be visible, explainable, and flagged when school payments are missing or delayed.

### IV. Training and Substitution Are Part of the Same Operating Model
Training days, onboarding a teacher to a specific school, a recurring monthly training calendar, and placing a substitute teacher for a regular teacher's coverage are not secondary features. The system must support onboarding, training calendar, training attendance, training history, and substitution assignment/payout reconciliation as first-class operational workflows, on equal footing with regular school-based attendance and payroll.

### V. Architecture Is a Modular Monolith With Enforced Boundaries
The system is one deployable Spring Boot application organized into packages per bounded context: `identity`, `organization`, `teacher`, `school`, `schoolbilling`, `attendance`, `payroll`, `expense`, `training`, `substitution`, `recruitment` (with a `marketing` sub-package), `reporting`, `audit`. Ownership is split as follows — `teacher` owns Teacher master data (profile, bank details, HLS-offered salary, status); `school` owns School master data (profile, branch, assigned manager reference); `schoolbilling` owns the Teacher–School–Manager Contract (contracted rate, billing terms) plus receivables, and reads Teacher/School master data only through their public APIs; `organization` owns Manager records and the Manager–School assignment that Principle II's scoping is enforced against; `audit` is the single append-only store that mechanically implements Principle I — no module writes audit history directly to its own tables; `reporting` owns no transactional data and may only read other modules through their public APIs or published events. Cross-module calls happen only through a module's public interface; ArchUnit and Spring Modulith verification enforcing this must fail the build on violation. A module may only be extracted into its own service later if scale or team size actually justifies it — this constitution does not permit microservices by default.

### VI. Concurrency Uses Structured, Virtual-Thread Batch Processing
Payroll computation and attendance rollups are triggered on demand by Admin/Director from the web or mobile app — there is no fixed monthly schedule, since attendance corrections can arrive late. Once triggered, this work must use JDK virtual-thread-per-task (or structured concurrency) rather than sequential per-teacher loops or hand-rolled thread pools. Request-path concurrency stays on Spring's default model.

### VII. Reliability, Testability, and Incremental Delivery Over Spreadsheet Workarounds
The product must replace fragile spreadsheet operations with a robust digital workflow built for auditability, concurrency safety, and explainable reports. New features must be developed with test coverage for calculation logic, access rules, and integration boundaries; changes that affect payroll or reconciliation must be reviewed against the current financial logic before release.

### VIII. Security, Identity, and Observability Are Non-Negotiable
API authentication uses JWT (short-lived access token, rotating refresh token); mobile login for teachers uses OTP via an India SMS gateway; RBAC is combined with attribute-based scoping so every endpoint enforces both role and manager/school ownership, not role alone. Secrets (signing keys, DB credentials, gateway keys) never enter source control. Structured logging with request correlation, metrics, and basic alerting (disk, DB pool exhaustion, failed payroll runs) are built in from the first module, not retrofitted after launch.

### IX. Recruitment and Marketing Are Tracked to Outcome
Campus recruitment drives (college, date, venue, interviewers) track candidates through interview → offer → acceptance → onboarding linkage, as a full workflow, not just a calendar. Marketing/sales calendar entries track outreach activity to its outcome (contract signed / follow-up needed / declined), kept intentionally lightweight for v1 and deepened later only if volume justifies it.

## Additional Constraints

- Technology stack: React web app for desktop/tablet workflows, React Native mobile app for teachers/managers/director — **Android-first for the initial release**, with iOS as a follow-on phase once Android is stable in production — Java 25+, Spring Boot 4.1.1-based backend, and a relational database (finalized via ADR-0002; PostgreSQL is the working default).
- Deployment & scale: single EC2/VM instance in an India region (e.g. AWS ap-south-1), Docker Compose packaging, sized for an initial user base under 100 growing ~30% per 12 months. No load balancer, second app instance, or service extraction until sustained CPU/IO contention is observed.
- Currency and localization: Indian Rupees (₹), dates in DD/MM/YYYY, and financial reporting aligned to the current operating patterns used by HLS.
- Financial integrity: school payment, teacher payout, expense, and substitution records must support partial payments, split disbursements, approval workflows, and explicit reconciliation notes.
- Data model: Teachers, schools, contracts, attendance records, training calendar, training sessions & participations, HLS office venues, school payments, pay slips, substitutions, expenses, campus recruitment drives, candidates & job offers, marketing/sales calendar entries, and audit logs must be represented as normalized relational entities with historical continuity rather than overwritten spreadsheet cells.
- Offline readiness: mobile attendance capture and expense capture must function with temporary offline storage and later sync when connectivity is restored.
- Security: RBAC, manager scoping, and audit logs are mandatory for all financial actions and attendance edits.
- Export: school attendance and salary-to-be-disbursed data must be exportable (CSV/PDF).

## Development Workflow

1. Requirements and acceptance criteria are derived from the HLS operating model and must remain traceable to real business behavior.
2. Calculation-heavy features such as payroll formulas, balance reconciliation, and attendance rollups must be developed with unit tests before production integration.
3. Authorization and data scoping logic must be validated in tests for each role and manager boundary.
4. Changes affecting payroll, schools, or audits require a review of both the formula and the audit trail implications, and of module-boundary compliance (Principle V).
5. Production issues or data anomalies that affect pay, collections, or staffing decisions must be handled as operational incidents with reversible correction workflows rather than silent overwrites.

## Governance

This constitution governs all design and implementation decisions for the HLS Teacher Management System. Any feature that conflicts with auditability, payroll correctness, manager scoping, module-boundary discipline, or financial transparency is non-compliant and must be revised before approval.

All major changes to the business model, data schema, payroll logic, or access control must be documented, reviewed, and versioned. The system design must prioritize operational trust over convenience, especially in payment, payroll, and attendance processes where errors directly affect teachers, managers, and schools.

**Version**: 1.5.0 | **Ratified**: 2026-09-18 | **Last Amended**: 2026-09-21

<!-- Amendment 1.4.0: Principle V's package list now names `roster` as the bounded context owning Teacher master data and the Teacher–School–Manager Contract, and clarifies that `schoolbilling` owns School master data. Prompted by a dependency review showing Attendance, School Payment and Substitution all require Teacher, School and Contract master data to exist first — see the reordered module sequence in docs/HLS SDD Implementation Plan & Deliverables Tracker.md. No prior principle was removed or weakened. -->
<!-- Amendment 1.5.0: Principle V's package list is refined based on docs/hls-teacher-management-architecture.md. `roster` is split into `teacher` (Teacher master data) and `school` (School master data), each independently owned; `schoolbilling` narrows to the Teacher–School–Manager Contract plus receivables, reading Teacher/School master data only through their public APIs. Three modules are added: `organization` (Manager records and the Manager–School assignment that Principle II's scoping runs against), `reporting` (a read-only cross-module dashboard/export layer with no transactional data of its own), and `audit` (the single append-only store that mechanically implements Principle I, replacing ad hoc per-module audit tables). No prior principle was removed or weakened; this is a refinement of module boundaries, not a change to what the system must do. -->
