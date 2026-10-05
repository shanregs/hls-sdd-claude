<!--
Sync Impact Report
==================
Version change: 1.7.0 → 2.5.0 (MAJOR fresh start to 2.0.0, then three MINOR clarifications:
2.1.0, 2.2.0, and 2.3.0, then one PATCH: 2.3.1, then two MINOR: 2.4.0 and 2.5.0)

Context: All prior specs (001–011) and their implementation code were removed. Spec numbering
restarts at 001. The new spec sequence is tracked in docs/spec-roadmap.md.

Modified principles:
- I. Operational Truth Must Be Auditable → kept; now also covers permission-matrix changes,
  role assignments, and login history
- II. Role-Scoped Access and Manager Ownership → split and redefined as
  II. Five Fixed Roles, Configurable Permissions (RBAC) and
  III. Data Scope and Zone-Based Manager Ownership
  - Roles are now exactly ADMIN, DIRECTOR, MANAGER, TEACHER, SYSTEM
    (Accounts Officer and System Assistant removed)
  - Admin gains payroll/salary processing and approval authority (previously barred)
  - New SYSTEM role, separated from business data
  - 2.1.0: Principle II now notes Admin may create new roles in future, beyond the fixed five
    (an extensibility allowance, not a present capability — roles remain fixed today)
  - 2.2.0: Director joins Admin and System as an editor of the role→permission matrix (Principle
    II); the Director bullet and the Additional Constraints access matrix's Role & Permissions
    row are updated accordingly. Director still does not manage users or system settings by
    default.
- III. Payroll, School Receivables, and Margin → renumbered to V (unchanged in substance)
- IV. Training and Substitution → renumbered to VI (unchanged)
- V. Modular Monolith → renumbered to VII; adds `leave`, `notification`, `settings` modules;
  `identity` now owns the permission matrix and the per-user navigation model; Zone-ownership
  history notes (Amendments 1.6.0/1.7.0) folded into the text
- VI. Concurrency → renumbered to VIII (unchanged)
- VII. Reliability/Testability → renumbered to IX; adds per-role authorization and menu tests
- VIII. Security/Identity/Observability → renumbered to X; backend is the security control,
  never the hidden menu
  - 2.5.0 (MINOR, spec 023): Principle IV gains a *MARKETING* navigation section (Prospects, Calendar,
    Pipeline, Dashboard, Settings) after *RECRUITMENT*; Principle VII adds the shared `files` module (stored
    uploads, never changed, only removed) and names the `recruitment.marketing` sub-package's boundaries; the
    Default role access matrix gains a Marketing row. Admin, Director and the Zone Manager run prospects, visits
    and proposals (the Zone Manager only in the Zones they manage); the Director and the Zone Manager also review a
    Final Stage prospect (Admin does not by default); Admin and Director hold the settings. Teacher and System have
    no access.
  - 2.4.0 (MINOR, spec 016): Principle IV gains a *RECRUITMENT* navigation section (Campus Drives,
    Candidates, Offers, Induction, Dashboard) after *OPERATIONS*, and the Default role access matrix
    gains Recruitment, Offers and Induction rows. The Director runs all three; the Admin runs
    recruitment and induction and only views offers; the Zone Manager takes part in drives (writing
    only to drives they scheduled or attend) and views offers; Teacher and System have no access.
    Spec 016 also adds a principal and an accountant contact to a School (amendment A8 to spec 005).
    The MARKETING section follows with spec 023.
  - 2.3.1 (PATCH, spec 012): the Default role access matrix gains a School Contracts row (the MoU
    between HLS and a School). Admin and Director create and edit contracts; the Zone Manager (Manager)
    views the contracts of their Schools and maps Teachers to them; Teacher and System have no access.
    No principle changes.
  - 2.3.0: Principle X's login model is generalized — OTP sign-in is no longer Teacher-only; every
    user may sign in by phone+OTP or by password (identified by phone number or username).
    Password reset can deliver its code to phone or to a registered email, user's choice.
- IX. Recruitment and Marketing → renumbered to XI (unchanged)

Added sections:
- IV. Role-Based Experience in One Shared Application (UI/UX principle)
- Additional Constraints: UI/UX constraints; default role access matrix
- Development Workflow: mandatory "Role & Permission Impact" section in every spec

Removed sections:
- Amendment history comments for 1.4.0–1.7.0 (superseded by fresh start; kept in git history)

Templates requiring updates:
- .specify/templates/spec-template.md ⚠ pending: add a "Role & Permission Impact" section
  (dependent templates are not modified by the constitution command)
- .specify/templates/plan-template.md ⚠ pending: Constitution Check must list Principles I–XI
- .specify/templates/tasks-template.md ✅ no change required

Follow-up TODOs: none. No placeholders deferred.
-->

# HLS Teacher Management System Constitution

## Core Principles

### I. Operational Truth Must Be Auditable

Attendance, leave, salary, school receivables, expenses, and training records are operational
truth, not convenience fields. Every financial or attendance-affecting change MUST be traceable to
the actor, the actor's role(s), the timestamp, and the prior and new values through an immutable,
append-only audit trail. The same applies to access control: role assignments, permission-matrix
changes, and security-setting changes MUST be audited, and successful and failed logins MUST be
recorded as login history. Audit entries are never edited or deleted, including by Admin or System;
corrections appear as new entries.

### II. Five Fixed Roles, Configurable Permissions (RBAC)

The system has exactly five roles: **Admin, Director, Manager, Teacher, System**. Roles are fixed.
Users cannot create, rename, or delete them. Admin may create new roles in future.

- A user MAY hold more than one role. Their effective menus, actions, and data scope are the
  **union** of their roles' grants.
- Roles are identified automatically after authentication. The user MUST NOT select a role at
  login.
- Authorization follows the chain **User → Role(s) → Permissions → Data Scope → Available Menu →
  Available Actions**, enforced at two levels:
  - **Level 1, Menu (module) access**: whether a module is visible and reachable at all.
  - **Level 2, Action access**: which actions are allowed inside a module: View, Create, Edit,
    Delete, Approve, Process, Export.
- The role→permission matrix is seeded from code with the defaults in *Additional Constraints*. It
  is editable at runtime by **Admin, Director, and System** through Role & Permission Management.
  Each change is audited (Principle I). Editing MUST NOT allow any combination that removes the
  last user able to manage permissions.
- **Admin** has full operational authority: create, view, edit, delete, approve, process
  (including payroll and salary processing), and generate reports. Admin manages users and assigns
  roles.
- **Director** has organization-wide operational and reporting access, and maintains the
  role→permission matrix, but by default does not manage users or system settings.
- **Manager** acts only within their data scope (Principle III).
- **Teacher** has a self-service scope: only their own attendance, leave, notifications, and
  profile. By default a Teacher MUST NOT reach Zone, School, or other Teachers' management; payroll
  administration; user management; system settings; or organization-wide reports.
- **System** is separated from business operations. It covers system configuration, security
  settings, users, roles and permissions, and audit only. System MUST NOT see teacher, school,
  attendance, leave, or payroll business data.

*Rationale*: one fixed, small role set keeps the UI and tests tractable. A configurable matrix lets
the business adjust who may Delete or Approve without code changes.

### III. Data Scope and Zone-Based Manager Ownership

Every business record request is filtered by data scope as well as by permission:

- **Admin, Director**: organization-wide.
- **Manager**: only the Zone(s) assigned to them → the Schools assigned to them within those Zones
  → the Teachers accountable to those Schools. A Manager is never organization-wide by default. They
  MUST NOT read or act on another Manager's Schools or Teachers.
- **Teacher**: only records where they are the subject.
- **System**: no business data.

Zone model: each Zone has one or more Managers, with more than one when the Zone has enough
Schools to split the workload. Each School belongs to exactly one Zone and is managed by one of
that Zone's Managers. A Teacher's accountable Manager is *derived* from the School the Teacher is
contracted or assigned to. Until the `schoolbilling` Teacher–School contract exists, an interim
direct Teacher–School (or Teacher–Manager) assignment may stand in for that derivation. The
interim assignment MUST be documented as temporary in the spec that introduces it. Scoping MUST
prevent cross-manager and cross-zone leakage, including in list endpoints, search, exports,
dashboards, and reports.

### IV. Role-Based Experience in One Shared Application

All five roles use **one shared application framework**. The interface each user sees is derived
from their roles, permissions, and scope. After login the application MUST load, in order: the
role-based dashboard, the role-based navigation, and the role-based permissions.

- **Layout**: a persistent left navigation panel, about 25–30% of the width on desktop and
  collapsible, plus a main content area of about 70–75%. On tablet and phone the navigation becomes
  a drawer. There is no horizontal page scroll at phone width.
- **Navigation sections** for staff roles: *Dashboard*, *MASTER DATA*, *OPERATIONS*, *RECRUITMENT* (Campus Drives, Candidates, Offers,
  Induction, Dashboard), *MARKETING* (Prospects, Calendar, Pipeline, Dashboard, Settings), *REPORTS*, *SYSTEM*, *ACCOUNT* (Profile, Logout). The System role uses *SYSTEM DASHBOARD*,
  *SYSTEM CONFIGURATION*, *AUDIT*, *ACCOUNT*. The Teacher role uses self-service sections:
  *Dashboard*, *MY ATTENDANCE* (My Attendance, Attendance History), *LEAVE* (Apply Leave,
  My Leave History), *NOTIFICATIONS*, *ACCOUNT* (My Profile, Logout). Sections with no authorized
  item are omitted.
- **Only authorized items are rendered.** Menu items the user may not access are hidden, not shown
  disabled. Inside a screen, actions the user may not perform (buttons, row actions, bulk actions)
  are hidden too.
- **Server-driven**: the menu and action grants come from a server-provided navigation and
  permission model for the logged-in user. Per-role menus MUST NOT be hard-coded in the frontend.
- **Routing**: a real client-side router with route guards. Deep links are supported, and a deep
  link to an unauthorized route shows a "not authorized" page, never the screen.
- **Role dashboards**: Admin (complete organization management), Director (organization-wide
  overview and reports), Manager (assigned Schools and Teachers), Teacher (my attendance, my leave,
  my notifications), System (system health, users, audit activity). A user with several roles sees
  a dashboard combining their roles' widgets.
- **Visual quality**: one design system (component library plus design tokens for color, spacing,
  and type) used consistently across every screen. Light and dark themes are supported. Every
  screen has consistent loading, empty, and error states. Forms show inline validation. Lists have
  consistent tables with pagination, sorting, and filters.
- **Accessibility and locale**: WCAG 2.2 AA, full keyboard navigation, amounts in Indian Rupees
  (₹, Indian digit grouping), dates as DD/MM/YYYY.

*Rationale*: a single framework keeps the product coherent and cheaper to build. Server-driven
menus keep the UI consistent with the runtime-editable permission matrix (Principle II).

### V. Payroll, School Receivables, and Margin Must Be Formula-Driven

Payroll, school receivables, and margin MUST be computed from the canonical data model, not from
re-entered spreadsheet formulas. Monthly salary is derived from attendance (including approved
leave), HLS-offered salary, school contract rate, and rounding rules. Receivables are generated from
active teacher assignments and contract terms. Margin MUST be visible and explainable, and it is
flagged when school payments are missing or delayed.

### VI. Training and Substitution Are Part of the Same Operating Model

Training days, onboarding a teacher to a specific school, a recurring monthly training calendar,
and placing a substitute teacher for a regular teacher's coverage are first-class operational
workflows. They sit on equal footing with regular school attendance and payroll, including
training attendance, training history, and substitution assignment and payout reconciliation.

### VII. Architecture Is a Modular Monolith With Enforced Boundaries

The system is one deployable Spring Boot application with one package per bounded context:

- `identity`: users, authentication, role assignment, the role→permission matrix (Role &
  Permission Management), and the per-user navigation and permission model consumed by the UI.
- `organization`: Manager records, Zone–Manager assignment, and School–Manager assignment. A
  School's Manager MUST be one of its Zone's Managers. `organization` exposes the scope queries that
  every other module's data scoping uses, and reads Zone and School–Zone data through `school`'s
  public API.
- `teacher`: Teacher master data (profile, status, salary history, bank details).
- `school`: School master data and **Zones**, including each School's current Zone and Places.
- `schoolbilling`: the Teacher–School–Manager contract (rate, billing terms) and receivables. It
  reads Teacher and School data only through their public APIs.
- `attendance`: daily capture, status codes, monthly rollups, and month locks.
- `leave`: leave requests, balances, and approvals. It feeds attendance and payroll through public
  APIs and events.
- `payroll`: payroll computation, salary processing, and payslips.
- `notification`: in-app notifications, with SMS and push later.
- `files`: the shared store for uploaded files (visit photos now; bills, MoU documents and check-in photos later). A
  stored file is never changed, only removed with a reason; callers decide who may see a file.
- `expense`, `training`, `substitution`, `recruitment` (with a `marketing` sub-package).
- `settings`: system and security settings, owned by the System role.
- `reporting`: owns no transactional data. It reads other modules only through public APIs or
  published events, and applies Principle III scoping.
- `audit`: the single append-only store implementing Principle I, including change history, user
  activity, and login history. No module writes audit history to its own tables.

Cross-module calls go only through a module's public interface. ArchUnit and Spring Modulith
verification MUST fail the build on violation. A module may be extracted into its own service only
if scale or team size justifies it. Microservices are not permitted by default.

### VIII. Concurrency Uses Structured, Virtual-Thread Batch Processing

Admin or Director (or any role granted the *Process* action) triggers payroll computation, salary
processing, and attendance rollups on demand. There is no fixed monthly schedule, because
attendance corrections can arrive late. Once triggered, this work MUST use JDK
virtual-thread-per-task or structured concurrency, not sequential per-teacher loops or hand-rolled
thread pools. Request-path concurrency stays on Spring's default model.

### IX. Reliability, Testability, and Incremental Delivery Over Spreadsheet Workarounds

The product replaces fragile spreadsheet operations with a robust digital workflow. New features
MUST have test coverage for calculation logic, access rules, and integration boundaries. Access
rules are tested **per role and per scope boundary** on every endpoint. For every screen and
module, UI tests assert which menu items and actions each role sees. Changes that affect payroll or
reconciliation MUST be reviewed against the current financial logic before release.

### X. Security, Identity, and Observability Are Non-Negotiable

API authentication uses JWT: a short-lived access token and a rotating refresh token. Every user,
regardless of role, may sign in either with a one-time SMS code sent to their phone number through
an India SMS gateway, or with a password identified by either their phone number or a username.
Password sign-in requires a password to be set on the account; OTP sign-in requires only the phone
number and is always available. A forgotten password is reset with a one-time code sent to the
user's phone number or, if they have one registered, their email address — the user's choice.
**The backend is the security control.** Every
endpoint enforces both permission (Principle II) and data scope (Principle III) independently of
what the UI shows. Hiding a menu or button is never an authorization measure. Permission checks MUST
fail closed. Secrets (signing keys, DB credentials, gateway keys) never enter source control.
Structured logging with request correlation, metrics, and basic alerting (disk, DB pool exhaustion,
failed payroll runs) are built in from the first module.

### XI. Recruitment and Marketing Are Tracked to Outcome

Campus recruitment drives (college, date, venue, interviewers) track candidates through interview →
offer → acceptance → onboarding linkage as a full workflow. Marketing and sales calendar entries
track outreach to its outcome (contract signed, follow-up needed, declined). The marketing part is
kept intentionally lightweight for v1.

## Additional Constraints

- **Technology stack**: a React (TypeScript) web app for desktop and tablet; React Native for
  teachers, managers, and the director, Android-first with iOS as a follow-on phase; Java 25+;
  a Spring Boot 4.1.1-based backend; PostgreSQL; Flyway owns the schema and Hibernate only
  validates it.
- **Deployment and scale**: a single EC2/VM in an India region (for example ap-south-1), packaged
  with Docker Compose, sized for under 100 users growing about 30% per 12 months. No load balancer,
  second instance, or service extraction until sustained contention is observed.
- **Currency and localization**: Indian Rupees (₹), dates as DD/MM/YYYY, and reporting aligned to
  HLS's operating patterns.
- **Financial integrity**: school payment, teacher payout, expense, and substitution records support
  partial payments, split disbursements, approval workflows, and explicit reconciliation notes.
- **Data model**: users, role assignments, permission grants, zones, schools, teachers, contracts,
  attendance, leave, training, substitutions, payments, payslips, expenses, notifications,
  recruitment, and audit logs are normalized relational entities. They keep historical continuity
  and are never overwritten spreadsheet-style.
- **Offline readiness**: mobile attendance and expense capture work with temporary offline storage
  and sync later.
- **Export**: school attendance and salary-to-be-disbursed data are exportable (CSV/PDF), subject to
  the Export action and data scope.
- **Default role access matrix** (the Principle II seed; runtime-editable). ✓ means full access,
  *Assigned* means limited to Principle III scope, *Own* means self only, — means no access:

  | Module                          | Admin | Director | Manager  | Teacher | System |
  | ------------------------------- | ----- | -------- | -------- | ------- | ------ |
  | Dashboard                       | ✓     | ✓        | ✓        | ✓       | ✓ (system) |
  | Zone Management                 | ✓     | ✓        | —        | —       | —      |
  | School Management               | ✓     | ✓        | Assigned | —       | —      |
  | Teacher Management              | ✓     | ✓        | Assigned | —       | —      |
  | School Contracts (MoU)          | ✓     | ✓        | Assigned (view; maps Teachers) | — | — |
  | Recruitment (drives, candidates) | ✓    | ✓        | Assigned (reads all; writes own drives) | — | — |
  | Marketing (prospects, visits)   | ✓     | ✓        | Assigned (own Zones; also reviews Final Stage) | — | — |
  | Offers                          | View  | ✓        | View     | —       | —      |
  | Induction                       | ✓     | ✓        | —        | —       | —      |
  | Attendance                      | ✓     | ✓        | Assigned | Own     | —      |
  | Leave Management                | ✓     | ✓        | Assigned | Own     | —      |
  | Payroll                         | ✓     | ✓        | —        | —       | —      |
  | Salary Processing               | ✓     | ✓        | —        | —       | —      |
  | Notifications                   | ✓     | ✓        | ✓        | Own     | —      |
  | Reports                         | ✓     | ✓        | Assigned | Own     | —      |
  | User Management                 | ✓     | —        | —        | —       | ✓      |
  | Role & Permissions              | ✓     | ✓        | —        | —       | ✓      |
  | System & Security Settings      | —     | —        | —        | —       | ✓      |
  | Audit Logs / Activity / Logins  | ✓     | —        | —        | —       | ✓      |

  Default action grants:
  - **Admin**: all actions.
  - **Director**: View, Create, Edit, Approve, Process, and Export organization-wide.
  - **Manager on assigned Attendance**: View, Create, and Edit. Delete and Approve are off by
    default and configurable.
  - **Teacher on own Attendance**: View, Create, and Edit, only where the business process allows
    and only within unlocked periods. Never Delete or Approve.

## Development Workflow

1. Requirements and acceptance criteria are derived from the HLS operating model and remain
   traceable to real business behavior.
2. Every spec MUST include a **Role & Permission Impact** section. For each of the five roles it
   states the menu items the feature adds or uses (with navigation section), the actions granted by
   default, and the data scope applied.
3. Calculation-heavy features (payroll formulas, balance reconciliation, attendance rollups) are
   developed with unit tests before production integration.
4. Authorization and data scoping are validated in tests for each role and each manager/zone
   boundary. Menu and action visibility is validated in UI tests for each role.
5. Changes affecting payroll, schools, access control, or audit require review of the formula, the
   audit-trail implications, and module-boundary compliance (Principle VII).
6. Production issues or data anomalies affecting pay, collections, or staffing are handled as
   operational incidents with reversible correction workflows, never silent overwrites.
7. Specs are delivered one at a time in the order in `docs/spec-roadmap.md`, through the full
   spec-kit path (specify → clarify → plan → tasks → analyze → implement).

## Governance

This constitution governs all design and implementation decisions for the HLS Teacher Management
System. Any feature that conflicts with auditability, payroll correctness, role and permission
enforcement, data scoping, module-boundary discipline, or financial transparency is non-compliant
and MUST be revised before approval.

Amendments are documented in this file with a Sync Impact Report, reviewed, and versioned
semantically: MAJOR for principle removals or redefinitions, MINOR for new principles or materially
expanded guidance, PATCH for clarifications. Every plan's Constitution Check and every
`/speckit-analyze` run verifies compliance with Principles I–XI. The system prioritizes operational
trust over convenience, especially in payment, payroll, attendance, and access control.

**Version**: 2.5.0 | **Ratified**: 2026-09-18 | **Last Amended**: 2026-10-05
