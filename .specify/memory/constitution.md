<!--
Sync Impact Report
==================
Version change: 1.7.0 → 3.0.0 (MAJOR fresh start to 2.0.0, then three MINOR clarifications:
2.1.0, 2.2.0, and 2.3.0, then one PATCH: 2.3.1, then two MINOR: 2.4.0 and 2.5.0, then PATCHes 2.5.1 to 2.5.16,
then MAJOR 3.0.0 for spec 029: a sixth fixed role)

Context: All prior specs (001–011) and their implementation code were removed. Spec numbering
restarts at 001. The new spec sequence is tracked in docs/spec-roadmap.md.

Modified principles:
- 3.0.0 (MAJOR, spec 029, decided by the owner 2026-10-26): Principle II is redefined from
  "Five Fixed Roles" to "Six Fixed Roles". The sixth role is **Content Contributor**: a self-service-style
  role for people who author content but are not Managers. It holds Dashboard (own), Content (own: View, Create,
  Edit; never Approve), Notifications (own) and Account, and nothing else; web only (no mobile sign-in).
  Principle II (list of roles, role bullets), Principle III (data scope: own authored or submitted content),
  Principle IV ("All six roles", Contributor dashboard and menu), the Default role access matrix (a Content
  row and a Content Contributor column; every other row is "—" for the new role) and Development Workflow item 2
  ("each of the six roles") change. Specs 001, 002, 003, 004, 010 and 018 are affected (see specs/029-content-management/plan.md).
  Version is MAJOR because the governance rule treats a redefined principle as MAJOR; 2.1.0 had only allowed
  new roles "in future".
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
  - 2.5.12 (PATCH, spec 024): the Default role access matrix gains three rows. Training (the calendar, sessions, patterns,
    assignments, exceptions, history, CSV export): Admin and Director hold View, Create, Edit, Delete and Export; a Manager
    holds View (sessions that include them or their Teachers, their Teachers' exceptions, never the ability to close one);
    Teacher and System have none. Training Attendance (record who attended): Admin and Director View and Edit for anyone; a
    Manager View and Edit for the Teachers placed under them only. Training Registration (an open weekend session): a Teacher
    holds View, Create and Edit for their own registration only. The navigation model gains the OPERATIONS items Training
    Calendar and Training Exceptions (Admin, Director) and My Training (Manager), and the MY ATTENDANCE items Upcoming
    Training and My Training History (Teacher). Principle VII holds: the feature is the `training.calendar` package of the
    `training` module; Teacher attendance is written only through `attendance.api.TrainingAttendance` after a read through
    `AttendanceReadApi`, Manager attendance and the 035 calendar read training only through `attendance.api.ManagerTrainingDays`
    and `training.api.TrainingActivities`, and `attendance` does not depend on `training`. Number 2.5.12 follows 2.5.11 of
    spec 028; renumber to whichever patch is next when this merges.
  - 2.5.8 (PATCH, spec 027): the Default role access matrix gains a Transfers and Replacements row. Admin holds View, Create and
    Edit (never Approve); Director and Manager hold View, Create, Edit and Approve; nobody decides a request they raised. A Manager
    reads requests where the leaving or receiving School is theirs and acts only for the leaving School; Teacher and System have no
    access. The navigation model gains the OPERATIONS item Transfers & Replacements. Principle VII holds: the feature is the
    `transfer` module, which changes placements only through `schoolbilling.api.PlacementMoves` and reads the other modules through
    their api packages; only `notification` reads `transfer.api`. Number kept after 2.5.7; the next patch after whichever of the
    parallel specs merges first.
  - 2.5.16 (PATCH, spec 030): the Default role access matrix gains an Incentives row (Manager incentives, ₹5,000 per
    closed School by default). Admin holds View and Export; the Director holds View, Approve and Export; a Manager holds View
    for own incentives and Approve for the Schools in their own Zone (Zone Manager); Teacher and System hold nothing. The
    amount is the system setting `incentive.amount-per-school`, edited by Admin under System Settings. The navigation model
    gains the PAYROLL & FINANCE item Incentives (Admin, Director, Manager). Principle VII holds: `incentive` reads other
    modules only through their api packages, and approved incentives reach payroll only through `payroll.api.IncentiveIntake`.
    Number follows 2.5.14; 2.5.15 is taken by spec 014b; renumber at merge if another PATCH lands first.
  - 2.5.15 (PATCH, spec 014b): the Default role access matrix gains three rows. Margin report (School fee less Teacher pay),
    Collections report (billed, collected, outstanding, ageing) and School report (one row per School): Admin and Director
    hold View and Export; a Manager holds View and Export for own Schools only (Assigned); Teacher and System hold none. The
    Zone Manager sees the Margin per School and per Teacher but still has no payroll report. Principle VII holds: the three
    reports only read, through the owning modules' api packages. Number follows 2.5.14 of spec 022; renumber at merge if
    another PATCH lands first.
  - 2.5.14 (PATCH, spec 022): the Default role access matrix gains a School Billing row (the internal receivables ledger:
    monthly dues derived from active assignments, adjustments, collections, ageing). Admin and Director hold View, Create, Edit,
    Approve and Export org-wide; a Zone Manager holds View, Create (record collections) and Export for their own Schools only and
    never Edit or Approve; Teacher and System hold nothing. The navigation model gains the OPERATIONS item School Billing. Principle
    VII holds: `schoolbilling` owns two ports in its api package, `BillingCalendar` (implemented by `attendance`) and
    `OverdueTasks` (implemented by `tasks`), so it depends on neither and the module graph stays acyclic; `notification` reads
    `schoolbilling.api` events. Number kept after 2.5.13; the next patch after whichever of the parallel specs merges first. No
    principle changes.
  - 2.5.13 (PATCH, spec 031): the Default role access matrix School Contracts row gains Approve: the Director holds it
    for every School and the Manager for their own Schools; Admin never holds it (Admin uploads, replaces, removes and
    submits the signed MoU document, which needs Edit). Teacher and System have no access. The navigation model gains the
    OPERATIONS item MoU Approvals (Director, Manager). Principle VII holds: the feature stays in the `schoolbilling`
    module, which reaches `files` only through `files.api`; other modules read the approval state through
    `schoolbilling.api.MouApprovals`; only `notification` listens to its events. No principle changes.
  - 2.5.11 (PATCH, spec 028): the Default role access matrix is unchanged; the dashboard grants each tile
    through the View grant of the module that owns its figure (a Manager's own-row tiles through the My Manager Attendance View
    grant), so no module is added to the matrix. Principle VII holds: the feature is the `reporting` module. It owns no
    transactional data and exposes only an SPI in `reporting.api` (`TileSource`, `ManagerColumns`, `ApprovalSource`); each owning
    module implements it in its own `internal` package and reads its own data, and `reporting` reads nothing but that SPI, the
    permission guard and the zone scope. A source that fails or is slow shows one error tile, never a zero. No principle
    changes.
  - 2.5.10 (PATCH, spec 035): the Default role access matrix gains an Activity Calendar row (read-only view of planned
    versus done, missed and rescheduled drives, visits, calls, meetings and tasks). Admin and Director hold View for every
    Manager; a Manager holds View for their own activities only; Teacher and System have no access and cannot be granted
    any. The navigation model gains the OPERATIONS item Activity Calendar (Admin, Director) and My Activity Calendar
    (Manager). Principle VII holds: the feature is the `activitycalendar` module, which reads `recruitment`, `tasks`,
    `organization`, `school` and `identity` through their api packages only; nothing reads `activitycalendar`. No principle
    changes.
  - 2.5.9 (PATCH, spec 015): the Default role access matrix gains two rows. Expenses (review, approve,
    pay, budgets, cost measures, export): Admin holds View, Create, Edit, Approve, Process and Export; Director holds the same
    but never Process (recording a payment outside payroll); Manager, Teacher and System have no access and cannot be granted
    any. My Expenses (a person's own claims): Admin, Director, Manager and Teacher hold View, Create, Edit and Delete on their
    own claims only; System has none. Nobody approves, rejects or reverses a claim they raised, even holding both approver roles.
    The navigation model gains the OPERATIONS items Expenses (Admin, Director) and My Expenses (Admin, Director, Manager,
    Teacher). Principle VII holds: the feature is the `expenses` module; payroll gains the additive `payroll.api`
    `ReimbursementIntake` and `PayrollAdjustmentApplied`, and `recruitment` and `training` gain read-only `WonSchools` and batch
    reads; only `notification` reads `expenses.api` events. No principle changes.
  - 2.5.7 (PATCH, spec 026): the Default role access matrix gains a Teacher Performance row (observations, Principal feedback,
    performance issues, corrective actions and referrals). Admin, Director and Manager hold View, Create and Edit; nobody holds
    Approve or Delete, because the transfer decision belongs to spec 027. A Manager reaches only the Teachers placed at their
    Schools and the records they wrote; Teacher and System have no access and cannot be granted any, and a Teacher never sees
    their own observations. The navigation model gains the OPERATIONS item Teacher Performance for Admin, Director and Manager.
    Principle VII holds: the feature is the `performance` module, which reads `teacher`, `organization`, `school`, `tasks`, `files`
    and `designation` through their api packages only; only `notification` (and later specs 027 and 028) read `performance.api`.
    No principle changes.
  - 2.5.6 (PATCH, spec 013b): the Default role access matrix row Payroll is made specific: Admin holds View, Process and
    Export; Director holds View, Approve and Export; Manager, Teacher and System have no access. Process starts,
    computes again, cancels and submits a run and records adjustments and payments; Approve returns or approves it, and
    whoever submitted a run can never approve it. Payslips and the CSV export are read from the stored run only, so an
    approved run never changes. Principle VII holds: the monthly run is the `payroll.run` sub-package, which reads
    attendance, Manager attendance and the calendar only while a draft is computed, through their public APIs. No
    principle changes.
  - 2.5.5 (PATCH, spec 032): the Default role access matrix gains three rows. Manager Attendance (the grid, corrections,
    the month lock and the check-in times): Admin holds View, Create, Edit and Process; Director holds View, Create and
    Edit but never Process, so only an Admin locks or reopens a Manager's month. Manager Leave (the decisions on
    Managers' leave and forgotten check-ins): Admin and Director hold View and Approve, and nobody decides their own
    request. My Manager Attendance (a Manager's own check-in with a location, month, forgotten check-ins and leave):
    a Manager holds View, Create and Delete on their own record only. Teacher and System have no access to any of the
    three. The navigation model gains MY ATTENDANCE (Check-in, My Month, My Leave) for the Manager and OPERATIONS items
    Manager Attendance and Manager Leave for Admin and Director. Principle VII holds: Manager attendance is the
    `attendance.manager` sub-package and Manager leave the `leave.manager` sub-package; `attendance` still never
    depends on `leave` (the month lock consults `attendance.api.PendingManagerRequests`, which leave implements),
    and payroll reads Manager attendance only through `attendance.api.ManagerAttendanceReadApi`. No principle changes.
  - 2.5.4 (PATCH, spec 037): the Default role access matrix gains a School Working Days row (the working days each
    School counts in each calendar month). Admin holds View, Create, Edit and Delete; Director holds View, Create and Edit
    (no Delete, as for other School master data); a Manager may be granted View only and is then limited to assigned
    Schools; Teacher and System have no access. The navigation model gains the MASTER DATA item School Working Days.
    Principle VII is unchanged: the feature is the `attendance.workingdays` sub-package of `attendance` and publishes
    `attendance.api.SchoolWorkingDays`, which payroll, Manager attendance and the attendance rollups adopt in later
    amendments. No principle changes.
  - 2.5.3 (PATCH, spec 011): the Default role access matrix row System & Security Settings gains Admin (View, Edit)
    alongside System, since Admin holds full operational authority; Director stays off by default and may be granted
    it. The `settings` module (Principle VII) keeps eleven runtime values and `identity` reads them through its own
    `RuntimeSettings` interface. No principle changes.
  - 2.5.3 (PATCH, spec 025): the Default role access matrix gains a Manager Tasks row. Admin and Director create, edit
    (reassign, move the due date, cancel), act on and verify tasks (View, Create, Edit, Approve); the Manager sees and
    acts on only the tasks assigned to them (View, Create for self, Edit) and can never be granted Approve; Teacher and
    System have no access. Principle VII gains the `tasks` module (task lifecycle, the C2 creation interface and the C1
    planned-activity feed); only `notification` reads its api package. No principle changes.
  - 2.5.2 (PATCH, spec 013a): Principle VII's `payroll` entry now describes its first half (salary structures by
    person (Managers and staff; a Teacher's stays in spec 005), the suggested default per designation and the pay
    policy, and the one public `PayRules` interface that the monthly payroll of spec 013b reads;
    it reads the `teacher`, `organization`, `designation`, `attendance`, `leave` and `schoolbilling` api packages and
    nothing reads its tables), and the Default role access matrix gains a Salary Structures row. Admin and Director view and record
    salaries and policy versions (View, Create); rows are append-only, so there is no Edit; Manager (Zone Manager),
    Teacher and System have no access and cannot be granted it. A Teacher's own salary stays under Teacher Salary
    (spec 005). This module is also the only place, besides Teacher Salary, where an individual's pay is shown (the
    People list), and only to its holders. No principle changes.
  - 2.5.1 (PATCH, spec 005a): Principle VII's module list gains `designation` (the list of designations, the
    employee-id registry and the holder-count SPI that `organization` and `teacher` implement; it depends on neither),
    and the Default role access matrix gains a Designations row. Admin and Director keep the list and the designation,
    employee id and joining or exit dates of Managers and Teachers (View, Create, Edit); the Zone Manager (Manager)
    may be granted View only, and sees those fields on the Managers and Teachers screens within scope; Teacher and
    System have no access. No principle changes.
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

### II. Six Fixed Roles, Configurable Permissions (RBAC)

The system has exactly six roles: **Admin, Director, Manager, Teacher, System, Content Contributor**
(the sixth added by spec 029). Roles are fixed. Users cannot create, rename, or delete them. A further
role is a constitution amendment.

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
- **Content Contributor** authors content (spec 029) and has a self-service scope: only the content they
  authored or submitted, their notifications, and their profile. By default a Content Contributor MUST NOT
  reach any other module, any Zone, School, Teacher or Manager data, payroll, expenses, user
  management, system settings or reports, and cannot be granted them. The role has no mobile sign-in.
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
- **Content Contributor**: only content they authored or submitted.
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

All six roles use **one shared application framework**. The interface each user sees is derived
from their roles, permissions, and scope. After login the application MUST load, in order: the
role-based dashboard, the role-based navigation, and the role-based permissions.

- **Layout**: a persistent left navigation panel, about 25–30% of the width on desktop and
  collapsible, plus a main content area of about 70–75%. On tablet and phone the navigation becomes
  a drawer. There is no horizontal page scroll at phone width.
- **Navigation sections** for staff roles: *Dashboard*, *MASTER DATA*, *OPERATIONS*, *RECRUITMENT* (Campus Drives, Candidates, Offers,
  Induction, Dashboard), *MARKETING* (Prospects, Calendar, Pipeline, Dashboard, Settings), *REPORTS*, *SYSTEM*, *ACCOUNT* (Profile, Logout). The System role uses *SYSTEM DASHBOARD*,
  *SYSTEM CONFIGURATION*, *AUDIT*, *ACCOUNT*. The Teacher role uses self-service sections:
  *Dashboard*, *MY ATTENDANCE* (My Attendance, Attendance History), *LEAVE* (Apply Leave,
  My Leave History), *NOTIFICATIONS*, *ACCOUNT* (My Profile, Logout). The Content Contributor role uses
  *Dashboard*, *OPERATIONS* (My Content), *NOTIFICATIONS*, *ACCOUNT*. Sections with no authorized
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
  my notifications), System (system health, users, audit activity), Content Contributor (my content by
  status). A user with several roles sees
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
- `designation`: the list of designations (job titles for Teachers or Managers), the employee-id registry (unique
  across Managers and Teachers) and an SPI through which `organization` and `teacher` report who holds each
  designation. A Manager's dated designation history lives in `organization`; a Teacher's current designation in
  `teacher`. `designation` depends on neither.
- `school`: School master data and **Zones**, including each School's current Zone and Places.
- `schoolbilling`: the Teacher–School–Manager contract (rate, billing terms) and receivables. It
  reads Teacher and School data only through their public APIs.
- `attendance`: daily capture, status codes, monthly rollups, and month locks.
- `leave`: leave requests, balances, and approvals. It feeds attendance and payroll through public
  APIs and events.
- `payroll`: payroll computation, salary processing, and payslips. Its first half (spec 013a) is the rules: each
  person's own salary (Managers and staff; HLS has no pay scale), the suggested default per designation and the pay
  policy, all append-only, and the one public `PayRules` interface that the
  monthly run reads. It reads `teacher`, `organization`, `designation`, `attendance`, `leave` and `schoolbilling`
  through their public APIs only, and no other module reads its tables.
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
  | Designations                    | ✓     | ✓        | View only if granted (reads the fields on people in scope) | — | — |
  | School Working Days (monthly working days per School) | View, Create, Edit, Delete | View, Create, Edit | View only if granted (Assigned Schools) | — | — |
  | Salary Structures (each person's salary, designation defaults, pay policy) | View, Create | View, Create | — | — | — |
  | Substitutions (requirements, assign, cover, School informed) | View, Edit | View, Edit | View, Edit (their Schools) | My Substitutions: View (own) | — |
  | Manager Tasks                   | View, Create, Edit, Approve | View, Create, Edit, Approve | Own tasks (View, Create for self, Edit to act on them; never Approve) | — | — |
  | School Contracts (MoU)          | ✓ (no Approve) | ✓ (incl. Approve) | Assigned (view; maps Teachers; Approve for own Schools) | — | — |
  | Recruitment (drives, candidates) | ✓    | ✓        | Assigned (reads all; writes own drives) | — | — |
  | Marketing (prospects, visits)   | ✓     | ✓        | Assigned (own Zones; also reviews Final Stage) | — | — |
  | Offers                          | View  | ✓        | View     | —       | —      |
  | Induction                       | ✓     | ✓        | —        | —       | —      |
  | Attendance                      | ✓     | ✓        | Assigned | Own     | —      |
  | Leave Management                | ✓     | ✓        | Assigned | Own     | —      |
  | Manager Attendance (grid, corrections, month lock, check-in times) | View, Create, Edit, Process | View, Create, Edit (never Process, the lock) | — (own record under My Manager Attendance) | — | — |
  | Manager Leave (decide Managers' leave and forgotten check-ins) | View, Approve | View, Approve | — | — | — |
  | My Manager Attendance (own check-in, month, forgotten check-ins, leave) | — | — | Own (View, Create, Delete) | — | — |
  | Payroll (the monthly pay run, payslips, payment records, CSV export) | View, Process, Export | View, Approve, Export (never Process) | — | — | — |
  | Transfers and Replacements (requests, approval, movement history) | View, Create, Edit (never Approve) | View, Create, Edit, Approve | Assigned (View, Create, Edit, Approve; read where the leaving or receiving School is theirs, act for the leaving School) | — | — |
  | School Billing (monthly dues, adjustments, collections, ageing, CSV export) | View, Create, Edit, Approve, Export | View, Create, Edit, Approve, Export | Assigned (View, Export; Create records collections for own Schools; never Edit or Approve) | — | — |
  | Teacher Performance (observations, Principal feedback, issues, referrals) | View, Create, Edit | View, Create, Edit | Assigned (View, Create, Edit; the Teachers placed at their Schools and the records they wrote) | — (never sees their own) | — |
  | Activity Calendar (read-only planned vs done) | View | View | Own activities (View) | — | — |
  | Training (calendar, sessions, patterns, assignments, exceptions, history, CSV export) | View, Create, Edit, Delete, Export | View, Create, Edit, Delete, Export | Assigned (View; sessions that include them or their Teachers; never closes an exception) | — | — |
  | Training Attendance (record who attended a session) | View, Edit | View, Edit | Assigned (View, Edit; only the Teachers placed under them) | — | — |
  | Training Registration (register for an open weekend session) | — | — | — | Own (View, Create, Edit) | — |
  | Expenses (review, approve, pay, budgets, cost measures, CSV export) | View, Create, Edit, Approve, Process, Export | View, Create, Edit, Approve, Export (never Process) | — | — | — |
  | My Expenses (own claims, bills, links, what is owed) | Own (View, Create, Edit, Delete) | Own (View, Create, Edit, Delete) | Own (View, Create, Edit, Delete) | Own (View, Create, Edit, Delete) | — |
  | Salary Processing               | ✓     | ✓        | —        | —       | —      |
  | Content (authored content, versions, review, approval) | View, Create, Edit (Approve can be granted) | View, Create, Edit, Approve | Own (authored or submitted; View, Create, Edit; never Approve) | — | — |
  | Notifications                   | ✓     | ✓        | ✓        | Own     | —      |
  | Incentives (Manager incentive per closed School; Approve) | View, Export | View, Approve, Export | Own (View); Approve for own Zone's Schools | — | — |
  | Reports                         | ✓     | ✓        | Assigned | Own     | —      |
  | Margin report (School fee less Teacher pay; View, Export) | View, Export | View, Export | Assigned (own Schools; View, Export) | — | — |
  | Collections report (billed, collected, ageing; View, Export) | View, Export | View, Export | Assigned (own Schools; View, Export) | — | — |
  | School report (one row per School; View, Export) | View, Export | View, Export | Assigned (own Schools; View, Export) | — | — |
  | User Management                 | ✓     | —        | —        | —       | ✓      |
  | Role & Permissions              | ✓     | ✓        | —        | —       | ✓      |
  | System & Security Settings      | ✓     | —        | —        | —       | ✓      |
  | Audit Logs / Activity / Logins  | ✓     | —        | —        | —       | ✓      |

  **Content Contributor column** (sixth role, spec 029): every row above is — except Dashboard (Own, its
  own dashboard), Content (Own: View, Create, Edit; never Approve), Notifications (Own) and Account (Own).

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
2. Every spec MUST include a **Role & Permission Impact** section. For each of the six roles it
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

**Version**: 3.0.0 | **Ratified**: 2026-09-18 | **Last Amended**: 2026-10-26
