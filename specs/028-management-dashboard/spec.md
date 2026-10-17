# Feature Specification: Management Dashboard

**Feature Branch**: `028-management-dashboard`

**Created**: 2026-10-18

**Status**: Draft

**Input**: User description: "028-management-dashboard: the Director's (and Admin's) management overview, one screen answering 'what needs attention today' across Managers, Schools, Teachers, recruitment, attendance, leave, payroll, plus tiles for expenses, receivables, training, observations and incentives as those specs arrive." MVP priority 1. Module `reporting` (reads other modules only through their public interfaces and owns no transactional data).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - One Screen for Today (Priority: P1) 🎯 MVP

The Director (or an Admin) opens the Dashboard and sees, on one screen and without choosing anything, tiles grouped by area: Managers, Schools, Recruitment, Teachers and operations, Attendance and leave, Payroll. Each tile shows one or two figures for today or the current month, says what the figure means, and opens the screen where the user can act on it. A tile whose figure needs a module that is not built yet is either absent or shown as "not available yet", never as zero.

**Why this priority**: The Director asked for a single page that answers "what is pending, what is overdue, who is absent" the moment the system opens. Today the Admin and Director dashboards are skeletons with placeholder boxes, so this is the missing front door.

**Independent Test**: Sign in as the Director with demo data, open the Dashboard, and see each built area's figures equal the counts on the screens they link to; open a tile and land on the matching list with the same filter applied.

**Acceptance Scenarios**:

1. **Given** the Director, **When** they open the Dashboard, **Then** the built areas appear as tiles with a figure each, grouped under area headings, and the page shows when the figures were taken.
2. **Given** a tile, **When** the user selects it, **Then** they go to the screen that lists the same records (for example the Manager tasks list filtered to overdue), and the list's count equals the tile's figure.
3. **Given** a tile whose source module is not built, **When** the Dashboard loads, **Then** the tile is absent, or shown as "not available yet" with the name of what is missing; it is never shown as 0.
4. **Given** the user lacks permission for the screen behind a tile, **When** the Dashboard loads, **Then** the tile is not shown at all (not disabled) and its figure is not sent to the browser.
5. **Given** one area's figures cannot be read, **When** the Dashboard loads, **Then** that area shows an error with a retry and every other area still shows.

---

### User Story 2 - Managers: Tasks, Visits, Check-ins and Leave (Priority: P1)

The Director sees how the Managers are doing today: tasks due today, overdue, awaiting verification and escalated (spec 025); visits planned, completed and missed for the period (spec 023); Managers who have not checked in past the cut-off and Managers on leave today (spec 032); Manager leave and forgotten check-ins awaiting a decision (spec 032). A Manager-by-Manager table lets the Director compare Managers on the same figures and filter to one Zone.

**Why this priority**: "Where is each Manager, what are they to do today, what is pending, what is overdue" is the first question in the Director's deck.

**Independent Test**: With a Manager who has an overdue task, a missed visit and no check-in past the cut-off, the Manager tile counts and the Manager's row each show those, and each number links to a list that contains that Manager.

**Acceptance Scenarios**:

1. **Given** tasks in several states, **When** the Managers area loads, **Then** it shows today, upcoming, overdue, awaiting verification and escalated task counts across all Managers, matching the task list.
2. **Given** a working day past the cut-off, **When** the area loads, **Then** it shows how many Managers have no check-in and lists them, and how many are on leave today.
3. **Given** the Zone filter set to one Zone, **When** the area loads, **Then** every figure covers only the Managers of that Zone.
4. **Given** the Manager-by-Manager table, **When** the Director sorts by overdue tasks, **Then** the Managers with the most overdue tasks come first and each row opens that Manager's task list.
5. **Given** pending Manager leave or forgotten-check-in requests, **When** the area loads, **Then** their count is shown and links to the decision screen.

---

### User Story 3 - Schools, Marketing and Recruitment (Priority: P1)

The Director sees the School pipeline and the recruitment funnel at a glance: prospects by stage (prospect, contacted, visit, follow-up, interested, negotiation, Final Stage, MoU, active), Final Stage prospects awaiting review, visits planned, completed and missed, Schools won and lost with the win rate, demand (vacant positions of Schools with a live MoU) against supply (recruits ready to deploy), and the recruitment totals from drives to joiners with the joining ratio.

**Why this priority**: School acquisition and Teacher supply are the two sides of the business; both already have data (specs 023 and 016) and nowhere to be seen together.

**Independent Test**: The School and recruitment tiles show the same totals as the Marketing dashboard and the Recruitment dashboard for the same period.

**Acceptance Scenarios**:

1. **Given** prospects at various stages, **When** the Schools area loads, **Then** it shows the count at each stage and the number awaiting Final Stage review, equal to the Pipeline screen.
2. **Given** a period selected (default the current month), **When** the area loads, **Then** visits planned, completed and missed and the Schools won and lost for that period are shown.
3. **Given** won Schools with vacant positions and recruits ready to deploy, **When** the area loads, **Then** demand, supply and the shortfall are shown; when supply is not available the shortfall says so rather than showing a number.
4. **Given** recruitment activity, **When** the area loads, **Then** drives held, candidates interviewed, offers issued and accepted and joiners are shown with the joining ratio, equal to the Recruitment dashboard totals.
5. **Given** a ratio whose denominator is zero, **When** it is shown, **Then** it reads "no data yet", never 0 percent.

---

### User Story 4 - Teachers and Operations (Priority: P1)

The Director sees the Teachers by status (in training, active, on leave, exited), the Teachers away today (absent or on approved leave), the substitution requirements open today with the covered and uncovered ones, and the pending Teacher leave requests with a link to Leave Management.

**Why this priority**: Absent Teachers and uncovered classes are the daily emergency the Director must see first.

**Independent Test**: Mark a Teacher absent today with an open substitution requirement; the Teachers tile shows one away, the Operations tile shows one open requirement, and each opens the matching list.

**Acceptance Scenarios**:

1. **Given** Teachers of all four statuses, **When** the area loads, **Then** the count per status is shown and equals the Teacher list filtered by that status.
2. **Given** Teachers with an absence or approved leave today, **When** the area loads, **Then** the count of Teachers away today is shown and links to the attendance view for today.
3. **Given** substitution requirements for today, **When** the area loads, **Then** open, assigned and uncovered counts are shown and link to the Substitutions list.
4. **Given** the Teacher statuses "Available", "Assigned", "Under Review", "Transferred" and "Replaced" in the planning documents, **When** the Dashboard shows Teachers, **Then** only the four stored statuses and counts derivable today are shown; the others appear only when their specs exist.

---

### User Story 5 - Payroll This Month (Priority: P1)

The Director sees where this month's payroll is: whether the run has been computed, is awaiting approval, is approved, how much has been paid and is still owed, and whether the month's attendance is locked for Teachers and for Managers.

**Why this priority**: Payroll approval is a Director action with a deadline; the Director needs to see it waiting.

**Independent Test**: With a payroll run awaiting approval, the Payroll tile shows that state and total and opens the run.

**Acceptance Scenarios**:

1. **Given** a payroll run for the month, **When** the area loads, **Then** its state (draft, awaiting approval, approved, cancelled) and total are shown, and the tile opens the run.
2. **Given** no run for the month, **When** the area loads, **Then** it says "no run yet" and shows whether attendance for the month is locked for Teachers and for Managers.
3. **Given** an approved run, **When** the area loads, **Then** the amount paid so far and the amount still owed are shown.
4. **Given** the user lacks the Payroll permission, **When** the Dashboard loads, **Then** no payroll figure or amount reaches them.

---

### User Story 6 - Approvals Inbox (Priority: P2)

A single "Waiting for you" list shows the decisions that need the Director or an Admin: Manager leave, forgotten check-ins, Final Stage prospects, payroll runs awaiting approval, tasks awaiting verification. Each entry shows who asked, what, since when, and opens the screen where it is decided. Expenses, incentives and transfers join the list when their specs exist.

**Why this priority**: It turns the Dashboard from something to read into somewhere to start work, but each decision already has its own screen, so the screens come first.

**Independent Test**: With one pending Manager leave and one payroll run awaiting approval, the inbox lists two entries, each linking to its decision screen; after the decision the entry leaves the list on refresh.

**Acceptance Scenarios**:

1. **Given** items waiting, **When** the inbox loads, **Then** it lists them, oldest first, with the kind, the person, the date asked and the age, and the total per kind.
2. **Given** an entry, **When** the user selects it, **Then** they go to the decision screen for that item.
3. **Given** a decision made elsewhere, **When** the Dashboard is refreshed, **Then** the entry is gone.
4. **Given** a request the user made themselves, **When** the inbox loads, **Then** it is not offered to them to decide.
5. **Given** a kind of decision the user may not take, **When** the inbox loads, **Then** that kind is absent.

---

### User Story 7 - Add a Tile Later Without Rework (Priority: P2)

When a later spec ships (015 expenses, 022 school billing, 024 training, 026 observations, 027 transfers, 030 incentives), its tile appears on the Dashboard by that spec registering its own figures, with no change to the other tiles, the layout or the reading rules.

**Why this priority**: Six specs will add tiles; the Dashboard must absorb them cheaply.

**Independent Test**: Add a test tile in the test setup; it appears under its area for a user who holds its permission, is absent for one who does not, and no other tile changes.

**Acceptance Scenarios**:

1. **Given** a new tile registered with an area, a permission, a link and a way to read its figure, **When** the Dashboard loads, **Then** it appears in that area for permitted users.
2. **Given** a registered tile whose source fails, **When** the Dashboard loads, **Then** only that tile shows an error.
3. **Given** modules not built, **When** no tile is registered for them, **Then** the Dashboard shows nothing for them (or one "coming later" note, per FR-007).

---

### User Story 8 - Filter by Zone and Period (Priority: P2)

The Director and Admin can narrow the whole Dashboard to one Zone, and set the period (this month by default) for figures that are per period.

**Why this priority**: The deck's area-wise view; useful, but the whole-organization picture comes first.

**Independent Test**: Choose one Zone; every tile that can be split by Zone changes to that Zone's figures, and tiles that cannot be split say so.

**Acceptance Scenarios**:

1. **Given** a Zone chosen, **When** the Dashboard reloads, **Then** every Zone-aware tile shows only that Zone's figures.
2. **Given** a tile that is not Zone-aware (for example payroll totals), **When** a Zone is chosen, **Then** the tile is labelled "organization-wide" so it is not mistaken for the Zone's figure.
3. **Given** a month chosen, **When** the Dashboard reloads, **Then** per-period tiles show that month and "today" tiles still show today.

---

### User Story 9 - A Manager's Own Dashboard Stays Scoped (Priority: P2)

A Manager's Dashboard shows the same kinds of tiles, but only for their Zones, Schools and Teachers and their own tasks, visits and check-in; it never shows other Managers, payroll or organization-wide figures.

**Why this priority**: The Manager already has a dashboard of Zone, School and Teacher counts (spec 005); this brings it in line without opening anything up.

**Independent Test**: A Manager sees only figures that equal what their scoped lists show; the Manager-by-Manager table, payroll and approvals tiles are absent.

**Acceptance Scenarios**:

1. **Given** a Manager, **When** they open the Dashboard, **Then** every figure covers only their assigned Zones, Schools and Teachers and their own tasks and visits.
2. **Given** a Manager, **When** they request a Dashboard figure for another Zone directly, **Then** it is refused.
3. **Given** a user holding both Manager and Director roles, **When** they open the Dashboard, **Then** they see the union (the Director's wider scope), never a role choice.

---

### Edge Cases

- A figure with no data source today (expenses, receivables, training, observations, transfers, incentives) is never shown as 0; it is absent or "not available yet" (FR-007).
- A user with several roles sees the union of their tiles and the widest scope; a tile is shown once.
- On a weekly off or holiday, attendance tiles say "not a working day" instead of showing everyone as missing.
- A Manager has no joining date, or a Teacher has no School: they are not counted as missed or absent; the area states how many people it could not count.
- A month whose attendance is not locked yet: payroll figures state the month is still open.
- A tile's figure and its list differ because a record changed in between: the Dashboard shows when its figures were taken and offers Refresh.
- Many tiles or a large Zone: each area appears as it arrives; a slow area never blocks the others.
- A mobile user opens Home: the Director's mobile Home shows the permitted tile figures in a single column; the rest of the mobile Home is unchanged.
- Phone-width browser or dark mode: no horizontal scroll, figures stay readable.
- An Admin who is also the requester of a leave request: that request is not in their inbox to decide (FR-014).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST provide a Dashboard for Admin and Director that shows, on one screen, tiles grouped under areas: Managers, Schools and marketing, Recruitment, Teachers and operations, Attendance and leave, Payroll, and the approvals inbox.
- **FR-002**: Every tile MUST show its figure, a plain label saying what it counts and for what period (today, this month, all time), and a link to the screen that lists the same records with the same filter; the count on that screen MUST equal the tile's figure.
- **FR-003**: The Dashboard MUST read only through the public read interfaces of the owning modules (never their tables), own no transactional data, and apply the caller's permission and data scope to every figure on the server.
- **FR-004**: The system MUST show for Managers: tasks due today, upcoming, overdue, awaiting verification and escalated (spec 025); Managers with no check-in past the cut-off and Managers on leave today (spec 032); Manager leave and forgotten-check-in requests awaiting a decision; and a Manager-by-Manager comparison on the same figures, sortable, with a Zone filter.
- **FR-005**: The system MUST show for Schools and marketing the prospects at each stage, Final Stage prospects awaiting review, visits planned, completed and missed for the period, Schools won and lost with the win rate, and demand against supply with the shortfall (spec 023); the same figures as the Marketing dashboard for the same period.
- **FR-006**: The system MUST show for Recruitment the drives held, candidates interviewed, offers issued and accepted, joiners and the joining ratio, and the number ready to deploy (spec 016); the same figures as the Recruitment dashboard.
- **FR-007**: The system MUST never present a missing source as zero. A tile for a module not built (expenses, receivables, training, observations, transfers and replacements, incentives, content) MUST be absent or marked "not available yet"; a figure that cannot be computed (such as a ratio with no denominator) MUST say so. This MUST hold equally for a built source that fails to answer.
- **FR-008**: The system MUST show for Teachers the count by stored status (in training, active, on leave, exited), the Teachers away today (absence or approved leave) and pending Teacher leave requests; and for operations the substitution requirements open, assigned and uncovered today.
- **FR-009**: The system MUST show for Payroll this month the state of the run (none, draft, awaiting approval, approved, cancelled), its total, the amount paid and the amount still owed, and whether attendance for the month is locked for Teachers and for Managers; these figures MUST be reachable only by users holding the Payroll permission.
- **FR-010**: The system MUST treat "today" as the business date in Asia/Kolkata, and on a weekly off or a holiday MUST report attendance tiles as "not a working day" rather than as absences or missed check-ins.
- **FR-011**: The system MUST provide an approvals inbox listing, oldest first, the decisions waiting for the user (Manager leave, forgotten check-ins, Final Stage prospects, payroll runs awaiting approval, tasks awaiting verification), each with the kind, the person, the date asked and its age, linking to the decision screen, with a total per kind; a kind the user may not decide is absent.
- **FR-012**: The system MUST let Admin and Director narrow the Dashboard to one Zone and set a period (default the current month); tiles that cannot be split by Zone MUST be labelled organization-wide when a Zone is chosen.
- **FR-013**: The system MUST make the Dashboard extensible: a tile is described by its area, label, period kind, required permission and scope, link and way of reading its figure, registered by the owning module's spec; adding one MUST NOT change other tiles, and a tile that fails MUST fail alone.
- **FR-014**: The system MUST NOT offer a user a decision on their own request in the inbox.
- **FR-015**: The system MUST apply data scope: a Manager's Dashboard covers only their Zones, Schools, Teachers and their own tasks, visits and check-in, with no Manager-by-Manager table, no payroll and no organization-wide figure; Teacher and System dashboards are unchanged by this spec; for a user with several roles the Dashboard is the union, each tile once.
- **FR-016**: The system MUST replace the placeholder boxes on the Admin and Director dashboards with these tiles, keep the existing Manager, Teacher and System dashboard content, and keep the server-provided menu and permissions as the only source of what appears (no role name tested in the screen).
- **FR-017**: The system MUST show the time the figures were taken, offer Refresh, and show each area as soon as it is ready, with consistent loading, empty and error states and a retry per area.
- **FR-018**: The system MUST show the same tile figures on the mobile Home for a user whose menu offers them, in a single column, without changing the rest of the mobile Home.
- **FR-019**: The system MUST state amounts in Indian Rupees with Indian digit grouping and dates as DD/MM/YYYY, support light and dark themes, work at phone width without horizontal scroll, and be operable by keyboard with figures a screen reader can read.
- **FR-020**: The system MUST NOT expose any figure, name or amount beyond what the caller's permission and scope allow, even to a request made directly to the server. Reading the Dashboard is not audited.
- **FR-021**: The system SHOULD let Admin and Director choose which available tiles they see and in what order, remembered per user, with the default set when nothing is chosen (lowest priority; see Assumptions).

### Key Entities *(include if feature involves data)*

- **Tile**: a registered description of one figure: area, label, period kind (today, month, all time), required permission, Zone-aware or not, link, and a way to read the figure. Owned by the spec that provides the figure; the Dashboard owns none of the data.
- **Tile Result**: the figure for one user at one moment: a number, a pair of numbers, or a state ("no data yet", "not available yet", "not a working day", error), with the time taken.
- **Approval Item**: a decision waiting for a user: kind, requester, subject, date asked, link.
- **Dashboard Preference** (per user, FR-021): the chosen tiles and their order. The only data the Dashboard stores.

## Role & Permission Impact *(mandatory — Constitution Principles II–IV)*

| Role | Menu (section → item) | Default actions | Data scope |
| ---- | --------------------- | --------------- | ---------- |
| Admin | Dashboard (the existing item, now the management overview) | View; each tile appears per the permission of the module behind it (Payroll, Manager Attendance, Marketing, Recruitment and so on) | Org-wide |
| Director | Dashboard (same) | View; all management tiles; inbox for the decisions the Director holds (Approve on Manager Leave, Marketing Final Stage review, Payroll, Manager Tasks) | Org-wide |
| Manager (Zone Manager) | Dashboard (existing) with scoped tiles | View; only tiles for modules the Manager already reaches (Teachers, Schools, own tasks, own visits, own check-in); no Manager comparison, payroll or inbox | Assigned Zones, Schools and Teachers; own tasks and visits |
| Teacher | Dashboard (existing: own attendance, leave, notifications) | Unchanged | Own |
| System | SYSTEM DASHBOARD (existing) | Unchanged; no business data | None |

**Permission keys**: no new menu module is needed; the existing Dashboard item is used. Each tile requires the View permission of the module that owns its figure (so editing the role-permission matrix changes which tiles a role sees), and each inbox kind requires the Approve or Process permission of its module. The constitution's Default role access matrix is unchanged.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The Director can answer "who is absent today, which Managers have not checked in, what is overdue and what is waiting for my approval" from the Dashboard alone, in under 30 seconds from sign-in.
- **SC-002**: The built areas are fully shown within 3 seconds at the current organization size (under 100 users), and a slow area never delays another.
- **SC-003**: For a prepared demo data set, every built tile's figure equals the count on the screen it links to, proved by a test per tile.
- **SC-004**: A Manager sees no figure outside their Zones, Schools, Teachers and own tasks; Teacher and System see no management tile; proved by a test per endpoint and per role.
- **SC-005**: No tile ever shows 0 for a source that does not exist or did not answer, proved by a test with each source missing and each source failing.
- **SC-006**: A new tile from a later spec appears for permitted users without any change to other tiles, proved by a test tile added in the test setup.
- **SC-007**: A user without the Payroll permission cannot obtain any payroll figure by any request, proved by a test.
- **SC-008**: Every decision in the approvals inbox is reachable in one click, and a decided item leaves the inbox on the next refresh.

## Assumptions

- **Real code differs from the planning documents, and the code is followed.** There is no `reporting` module in the code yet. The Admin and Director dashboards are skeletons (placeholder boxes plus the pending-Teacher-leave count); the Manager dashboard (spec 005) shows Zone, School and Teacher counts; the mobile Home is a generic home with cards for leave, the Manager's day, visits and tasks, shown by what the server's menu offers. The marketing dashboard (023), recruitment dashboard (016) and system dashboard already exist as their own screens with their own endpoints; this spec reuses their figures and does not recompute them.
- **Figures that exist today and are used**: the task summary (today, upcoming, overdue, awaiting, escalated, done; 025); marketing visits, stages, won, lost, win rate, demand and supply (023); recruitment counts and joining ratio (016); Manager missed check-ins, on leave today, pending leave and forgotten check-in requests, and the Manager month figures through the Manager attendance read interface (032); Teacher status, attendance and leave roll-ups, away-today and the pending Teacher leave count (005, 008, 009); substitution requirements (017a); payroll runs with states draft, awaiting approval, approved and cancelled, with payments and owed (013b); month locks.
- **Figures that depend on specs not built, so absent or "not available yet"**: expenses, budget vs spend and pending expense approvals (015); billed, collected, outstanding and overdue Schools (022; spec 012 has contracts and occupancy but no invoices); training today and non-compliance (024); observations and performance issues (026); transfers, replacements and the Teacher views Available, Assigned, Under Review, Transferred and Replaced (027; the code stores only four statuses); incentives (030); content (029); and inbox kinds for expenses, incentives, transfers and MoU documents (031). The roadmap's 028 scope for these is met by registering their tiles later (FR-013).
- The roadmap lists 028's dependencies as 012, 016, 023, 025, 026, 032, 013b, 015 and 022; 026, 015 and 022 are not built. 028 is built on what exists and those dependencies become "tile added by that spec", not blockers. To confirm in /speckit-clarify.
- The approvals inbox is read-only aggregation: deciding stays on each module's own screen. The shared approval pattern (C3, spec 015) does not exist yet, so the inbox collects what each module reports rather than reading one approvals store. To confirm in /speckit-clarify.
- The default tile set and order follow the areas in the user stories; per-user choice and order (FR-021) is the lowest priority and is dropped first if the build is tight. To confirm in /speckit-clarify.
- Figures are read when the page loads or is refreshed (no live push) and the page shows when they were taken; no auto-refresh interval is promised. To confirm in /speckit-clarify.
- Admin and Director see the same Dashboard, differing only where the permission matrix differs (for example the Final Stage review is the Director's by default, locking Manager months the Admin's). To confirm in /speckit-clarify.
- Mobile: only the Home tiles for Director and Admin are in scope (FR-018), reusing the server's tile results. To confirm in /speckit-clarify.
- Manager performance comparison is limited to figures that exist: tasks, visits, check-ins and Schools won per owner. Incentives, observations and content contributions arrive with their specs. The Manager activity calendar (spec 035) is a separate screen.
- Out of scope: downloadable and scheduled reports and exports (spec 014), charts and trends over time beyond the month picker, editing any figure, the tiles of unbuilt modules themselves, the Teacher and System dashboards, and push notifications.
