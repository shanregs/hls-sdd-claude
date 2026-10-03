# Feature Specification: Master Data (Zones, Schools, Managers, Teachers)

**Feature Branch**: `005-master-data`

**Created**: 2026-10-03

**Status**: Draft

**Input**: User description: "005-master-data: MASTER DATA module in one spec (modules school, organization, teacher). Zone Management; School Management (profile, zone, places, bulk import); Manager records plus Zone–Manager and School–Manager assignment (a School's Manager must be one of its Zone's Managers) and the scope queries later modules use; Teacher Management (profile, status, salary history, interim Teacher–School assignment, documented as temporary). Manager dashboard shows assigned Zones/Schools/Teachers. Teacher: ACCOUNT → My Profile. Depends on 002-access-model-app-shell and 003-audit. Per docs/spec-roadmap.md and Constitution v2.3.0 (Principles II, III, VII)."

## Clarifications

### Session 2026-10-03

- Q: Should each School record which Place it is located in, in addition to its Zone? → A: Yes,
  required. Each School is in exactly one Place and its Zone is that Place's Zone; moving a School
  means choosing another Place.
- Q: When a Manager edits a School assigned to them, which parts of the School record may they
  change? → A: Only the contact person, contact phone, and address. The name, Place, and billing
  contact stay Admin/Director only.
- Q: Which status changes may an Admin or Director make on a Teacher record? → A: In training →
  active, active ↔ on leave, and any status → exited. Exit is final: an exited Teacher is never
  reinstated, and a returning person gets a new Teacher record. Any other change is rejected.
- Q: When a bulk-imported Place row has the same name and PIN code as a Place already in that Zone,
  what should the import do? → A: Skip the row and report it as "already exists" (the same name and
  PIN code in the same Zone counts as a repeat). Places that merely share a name or PIN code with a
  different one are still added.
- Q: When an Admin or Director places or moves a Teacher to a School, can they choose the effective
  date, and how far back or ahead? → A: They choose the date, past or future. A future-dated move
  takes effect automatically on that date. A past date may not be earlier than the start of the
  Teacher's current placement, so history never overlaps and has no gaps.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Maintain Zones and Their Places (Priority: P1) 🎯 MVP

An Admin or Director creates the Zones the business operates in (for example a district, or a
sub-district when a district is split) and records the Places (towns, cities, villages, each with a
name and PIN code) that make up each Zone, so every School can later be placed in a Zone.

**Why this priority**: Every other record in this spec hangs off a Zone — Schools belong to one,
Managers are assigned to them, and Manager scope is derived from them. Nothing else can be set up
without it.

**Independent Test**: As Admin, create a Zone, add several Places to it, rename the Zone, then look
a Place up by PIN code and by name and confirm it reports the right Zone.

**Acceptance Scenarios**:

1. **Given** an Admin on Zone Management, **When** they create a Zone with a name, **Then** it
   appears in the Zone list with zero Places, zero Schools, and zero Managers.
2. **Given** a Zone, **When** an Admin adds a Place with a name and PIN code, **Then** the Place is
   listed under that Zone and can be found by PIN code or by name.
3. **Given** two Places that share a name or a PIN code, **When** an Admin adds the second one,
   **Then** it is accepted (real geography does not guarantee uniqueness of either), and a lookup
   returns every match, not just the first. (Bulk import differs only in skipping an exact repeat of
   name, PIN code and Zone; see User Story 8.)
4. **Given** a Zone that has Schools or Managers attached, **When** an Admin tries to delete it,
   **Then** the system refuses and says what still depends on it. (Zones with nothing attached may
   be deleted.) A Place that has Schools in it can be neither deleted nor moved to another Zone.
5. **Given** a Manager, Teacher, or System user, **When** they try to open Zone Management by menu
   or direct link, **Then** no menu item is shown and the direct route shows "not authorized."

---

### User Story 2 - Maintain Schools and Place Them in a Zone (Priority: P1)

An Admin or Director adds a School (name, address, contact person and phone, billing contact), puts
it in exactly one Place (which fixes its Zone), and keeps its profile up to date. A Manager can view and edit only the
Schools assigned to them.

**Why this priority**: Schools are the unit teachers are placed in and the unit the later
attendance, leave, and billing modules scope by.

**Independent Test**: As Admin, create a School in a Place, edit its profile, move it to a Place in
another Zone, and confirm the change; as an unassigned Manager, confirm it is neither listed nor
reachable.

**Acceptance Scenarios**:

1. **Given** an Admin on School Management, **When** they create a School with its profile and one
   Place, **Then** it appears in the School list showing that Place and the Place's Zone, with no
   Manager yet. A School cannot be created without a Place.
2. **Given** a School, **When** an Admin edits its profile fields, **Then** the change is saved and
   recorded in Change History.
3. **Given** a School with an assigned Manager, **When** an Admin moves it to a Place whose Zone that
   Manager does not cover, **Then** the system refuses and explains that the School's Manager must
   cover the School's Zone (resolve by reassigning the Manager first). Moving a School to another
   Place in the same Zone is always allowed.
4. **Given** a Manager, **When** they open School Management, **Then** they see only Schools
   assigned to them, and a direct link to any other School shows "not authorized" or "not found"
   without revealing whether it exists.
5. **Given** a School with active Teachers placed in it, **When** an Admin tries to deactivate it,
   **Then** the system refuses until those Teachers are moved or exited.
6. **Given** a Manager viewing a School assigned to them, **When** they edit it, **Then** they can
   change only the contact person, contact phone, and address; the name, Place, and billing contact
   are shown read-only (or hidden) and any attempt to submit a change to them is refused by the
   system.

---

### User Story 3 - Manage Manager Records and Assign Them to Zones and Schools (Priority: P1)

An Admin or Director creates a Manager record for a Manager user, assigns the Manager to one or more
Zones, and then assigns each School in those Zones to one of the Zone's Managers. A Zone may have
several Managers when its workload is split.

**Why this priority**: The Manager's data scope — the single biggest security rule outside
authentication (Constitution Principle III) — is derived entirely from these assignments. Without it
Managers see nothing.

**Independent Test**: Create two Managers in one Zone with three Schools; assign two Schools to the
first Manager and one to the second; confirm each Manager sees only their own Schools and the
Teachers in them, and that assigning a School to a Manager who does not cover its Zone is refused.

**Acceptance Scenarios**:

1. **Given** a user holding the Manager role, **When** an Admin creates a Manager record for them,
   **Then** the Manager appears in the Manager list with no Zones and no Schools.
2. **Given** a Manager and a Zone, **When** an Admin assigns the Manager to the Zone, **Then** the
   Zone lists that Manager and the Manager's Zones include it. A Zone may have more than one
   Manager, and a Manager may cover more than one Zone.
3. **Given** a School in a Zone, **When** an Admin assigns it a Manager who does not cover that Zone,
   **Then** the system refuses with a clear message and changes nothing.
4. **Given** a Manager who has Schools assigned in a Zone, **When** an Admin tries to remove the
   Manager from that Zone, **Then** the system refuses until those Schools are reassigned.
5. **Given** an assignment change, **When** it is saved, **Then** the previous and new assignment
   are recorded in Change History with the actor and time, and the previous assignment remains
   visible in the assignment history (nothing is overwritten).
6. **Given** a user without the Manager role, **When** an Admin tries to create a Manager record
   for them, **Then** the system refuses.

---

### User Story 4 - Manage Teachers, Their Status, and Their Interim School Placement (Priority: P1)

An Admin or Director creates a Teacher record (name, contact details, status) and places the Teacher
in a School. A Manager can view and edit only Teachers placed in their assigned Schools. A Teacher's
status moves through in training, active, on leave, and exited.

**Why this priority**: Teachers are the people the whole product exists to manage; attendance,
leave, and payroll all attach to them.

**Independent Test**: As Admin, create a Teacher, place them in a School, change their status to
active, then move them to another School; confirm the Manager of the first School loses sight of
them and the Manager of the second gains it.

**Acceptance Scenarios**:

1. **Given** an Admin on Teacher Management, **When** they create a Teacher with name, contact
   details, and a status, **Then** the Teacher appears in the Teacher list.
2. **Given** a Teacher with no School, **When** an Admin places them in a School, **Then** the
   Teacher's accountable Manager is that School's Manager, and that Manager now sees the Teacher.
3. **Given** a Teacher placed in a School, **When** an Admin moves them to another School with an
   effective date of today or earlier, **Then** the old placement is kept as history ending the day
   before, the new one becomes current, and the previous Manager no longer sees the Teacher.
   A date earlier than the start of the current placement is refused.
   3a. **Given** a move with a future effective date, **When** it is saved, **Then** it shows as
   scheduled, nothing changes until that date, and on that date the new School and its Manager
   become current automatically (no one needs to sign in again or re-save). Until then an Admin or
   Director can change or cancel the scheduled move.
4. **Given** a Teacher, **When** an Admin or Director changes their status along an allowed path
   (in training → active, active ↔ on leave, or any status → exited), **Then** the change is saved
   with its effective date and recorded in Change History. Any other change (for example active →
   in training, or anything from exited) is rejected with a clear message and nothing changes.
   An exited Teacher cannot be placed in a School again; a returning person is entered as a new
   Teacher record.
5. **Given** a Manager, **When** they open Teacher Management, **Then** they see only Teachers
   placed in their assigned Schools, and a direct link to any other Teacher shows "not authorized"
   or "not found."
6. **Given** a Teacher placed in a School whose Manager has since been changed, **When** the
   School's Manager is reassigned, **Then** the Teacher's accountable Manager follows automatically
   with no per-Teacher edit.
7. **Given** the placement is described to users, **When** they view a Teacher's placement,
   **Then** it is labelled as an interim assignment, since a later billing/contract feature will
   replace it.

---

### User Story 5 - Manager Sees Only Their Assigned Data Everywhere (Priority: P1)

Every list, search, count, and direct link for Schools and Teachers is filtered to what the signed-in
Manager is assigned to. The system also exposes a single, reusable way to ask "which Zones, Schools,
and Teachers is this Manager responsible for?" so later modules (attendance, leave, payroll,
reports) can apply the same rule without re-implementing it.

**Why this priority**: A leak here is a data-protection failure across the whole product, and every
later module depends on this one answer being correct and consistent.

**Independent Test**: With two Managers in different Zones, call every list, search, and detail
view as each; confirm neither ever sees the other's Schools or Teachers, including through filters,
exports, counts, and guessed identifiers.

**Acceptance Scenarios**:

1. **Given** two Managers with different Schools, **When** each lists Schools and Teachers, **Then**
   each sees only their own and the totals reflect only their own.
2. **Given** a Manager, **When** they request a School or Teacher by identifier that is not
   theirs, **Then** the response is the same as for a record that does not exist.
3. **Given** a Director or Admin, **When** they list Schools and Teachers, **Then** they see all of
   them.
4. **Given** a user holding both the Manager role and the Director role, **When** they list Schools,
   **Then** they see the union of what each role allows (organization-wide, because Director is).
5. **Given** a Manager whose assignments change, **When** they next load any list, **Then** the
   result reflects the new assignments without signing in again.

---

### User Story 6 - Manager Dashboard Shows Assigned Zones, Schools, and Teachers (Priority: P2)

A Manager's dashboard shows the Zones, Schools, and Teachers assigned to them, with counts and a
way to open each list.

**Why this priority**: It turns the Manager's skeleton dashboard (spec 002) into something useful
and proves the scope answer in a visible place, but the lists in User Stories 2–4 already work
without it.

**Independent Test**: Sign in as a Manager with two Zones, five Schools, and twelve Teachers
assigned; confirm the dashboard shows exactly those counts and links to the filtered lists.

**Acceptance Scenarios**:

1. **Given** a Manager with assignments, **When** they open their dashboard, **Then** it shows the
   number of assigned Zones, Schools, and Teachers.
2. **Given** a Manager with no assignments, **When** they open their dashboard, **Then** it shows an
   empty state explaining that nothing has been assigned yet.
3. **Given** a user with the Manager and Director roles, **When** they open their dashboard,
   **Then** the Manager widgets show the Manager's assigned figures alongside the Director's
   organization-wide widgets, clearly labelled.

---

### User Story 7 - Teacher Views Their Own Profile (Priority: P2)

A Teacher opens ACCOUNT → My Profile and sees their own details: name, contact details, status, and
current School placement. They cannot see or reach any other Teacher.

**Why this priority**: It is the Teacher's only master-data surface, and a basic self-service
promise, but it depends on User Stories 2 and 4 existing first.

**Independent Test**: Sign in as a Teacher who has a record and a placement and confirm My Profile
shows exactly their own details; confirm a Teacher with no record sees a clear message rather than
an error.

**Acceptance Scenarios**:

1. **Given** a Teacher user linked to a Teacher record, **When** they open My Profile, **Then** they
   see their own name, contact details, status, and current School.
2. **Given** a Teacher user, **When** they try to open any Teacher or School management screen by
   menu or direct link, **Then** nothing is shown and the route says "not authorized."
3. **Given** a Teacher user with no linked Teacher record yet, **When** they open My Profile,
   **Then** they see a message that their profile has not been set up, with no error.
4. **Given** a Teacher, **When** they view My Profile, **Then** salary details are not shown to
   them.

---

### User Story 8 - Bulk-Import Places (Priority: P2)

An Admin or Director adds many Places to a Zone at once by pasting or uploading a list (name and PIN
code per row), and gets a per-row report of what was added and what was rejected and why.

**Why this priority**: Onboarding hundreds of towns and villages one at a time is impractical, but
the product works without it.

**Independent Test**: Import a list of 20 rows where 3 are invalid (missing PIN code, unknown
Zone) and 2 repeat existing Places; confirm 15 are added, 2 are reported as already existing, 3 are
reported with row number and reason, and nothing from the bad rows is created. Import the same list
again and confirm nothing new is created.

**Acceptance Scenarios**:

1. **Given** a list of valid rows, **When** an Admin imports it, **Then** every row becomes a Place
   in the stated Zone and the report shows all rows succeeded.
2. **Given** a list with some invalid rows, **When** it is imported, **Then** valid rows are added,
   invalid rows are reported with their position and reason, and one bad row never blocks the rest.
3. **Given** a row with the same name and PIN code as a Place already in that Zone, or as an
   earlier row in the same list, **When** the list is imported, **Then** that row is skipped and
   reported as "already exists" (not as an error), so re-running a corrected file never creates
   duplicates; rows that merely share a name or a PIN code with a different Place are still added.
4. **Given** an empty list or a list over the maximum size, **When** it is submitted, **Then** the
   whole import is rejected up front with a clear message and nothing is created.
5. **Given** a Manager, Teacher, or System user, **When** they try to import, **Then** it is
   refused.

---

### User Story 9 - Record and Review a Teacher's Salary History (Priority: P2)

An Admin or Director records the salary HLS offers a Teacher, with the date it takes effect. The
system keeps every change as history, shows the current salary, and can answer "what was this
Teacher's salary on a given past date?" Salary history is never edited in place.

**Why this priority**: Payroll (a later spec) needs a trustworthy, dated salary history, but nothing
in this spec's other stories depends on it.

**Independent Test**: Record a salary of ₹20,000 effective 1 April, then ₹22,000 effective 1
October; confirm "as of 15 June" returns ₹20,000, "as of 15 October" returns ₹22,000, and both rows
remain listed.

**Acceptance Scenarios**:

1. **Given** a Teacher, **When** an Admin records a salary amount and effective date, **Then** a new
   history row is added and becomes the current salary if it is the latest effective one.
2. **Given** several history rows, **When** a user asks for the salary on a past date, **Then** the
   amount from the row in effect on that date is returned.
3. **Given** a recorded salary, **When** a correction is needed, **Then** it is made by adding a new
   row (the earlier row stays), never by overwriting.
4. **Given** a Manager or Teacher, **When** they try to view or record salary, **Then** it is
   refused and no salary appears in any screen or export they can reach.
5. **Given** each salary change, **When** it is saved, **Then** Change History records the actor,
   time, and the previous and new amounts.

---

### Edge Cases

- What happens when a Zone is renamed? Its Schools, Managers, and Places stay attached; History
  shows the old and new name.
- What happens when a School's Manager must change because the Manager is removed from the Zone?
  Refused until the School is reassigned (User Story 3, scenario 4); the system never leaves a
  School whose Manager does not cover its Zone.
- What happens when a user holding the Manager role is deactivated or loses the Manager role (spec
  004)? Their Manager record keeps its history, but they are shown as inactive and any School
  still assigned to them is flagged as needing a new Manager; their data scope is empty immediately
  while they lack the role or the account is inactive, decided from their current roles and state, not
  from their token.
- What happens when a Teacher is placed in a School that has no Manager yet? Allowed; the Teacher
  has no accountable Manager until the School has one, and only Admin/Director can see them.
- What happens when a Teacher exits? Their placement ends on the exit date, the record stays
  forever (never deleted) for attendance, leave, and payroll history, and the exit cannot be undone.
  Their link to a user account is released so the account can be linked to a new record if the
  person returns.
- What happens when a Teacher with a scheduled future placement exits first? The scheduled
  placement is cancelled. What happens when a School with scheduled incoming Teachers is
  deactivated? The deactivation is refused until those scheduled moves are changed or cancelled.
- What happens when two records are edited at the same time by different people? The second save is
  refused with a message that the record changed, rather than silently overwriting.
- What happens when a PIN code is not six digits? The Place or row is rejected with a clear reason.
- What happens when a School or Teacher search matches nothing? An empty state is shown, not an
  error.
- What happens when a Teacher's linked user is deactivated? The Teacher record stays and keeps its
  placement; only sign-in is affected.

## Requirements *(mandatory)*

### Functional Requirements

**Zones and Places**

- **FR-001**: Admin and Director MUST be able to create, rename, list, and view Zones, and delete a
  Zone only when no Schools, Managers, or Places depend on it.
- **FR-002**: Admin and Director MUST be able to add Places (name and six-digit PIN code) to a Zone,
  and find Places by PIN code or by name; names and PIN codes are not unique, and a lookup MUST
  return every match.
- **FR-003**: Admin and Director MUST be able to bulk-import Places into a Zone from a list, with
  each row validated independently and a per-row report of added, skipped as "already exists"
  (same name and PIN code in the same Zone as an existing Place or an earlier row), or rejected with
  a reason; the whole import MUST be rejected up front, creating nothing, if the list is empty or
  exceeds 5,000 rows.

**Schools**

- **FR-004**: Admin and Director MUST be able to create, edit, list, view, and deactivate Schools,
  each with a name, address, contact person and phone, billing contact, and exactly one Place; the
  School's Zone is always the Zone of its Place and is never set independently.
- **FR-005**: The system MUST refuse to deactivate a School while active Teachers are placed in it.
- **FR-005a**: The system MUST refuse to delete a Place, or to move it to another Zone, while any
  School is located in it.
- **FR-006**: A Manager MUST be able to view the Schools assigned to them and edit only their
  contact person, contact phone, and address; they MUST NOT change a School's name, Place, or
  billing contact, and MUST NOT create, deactivate, or reassign Schools. The system enforces this
  field limit independently of what the screen shows.

**Managers and assignments**

- **FR-007**: Admin and Director MUST be able to create a Manager record only for a user holding the
  Manager role, and view and list Manager records with their Zones, Schools, and Teacher counts.
- **FR-008**: Admin and Director MUST be able to assign a Manager to one or more Zones; a Zone MAY
  have more than one Manager.
- **FR-009**: Admin and Director MUST be able to assign each School to exactly one Manager, who MUST
  be one of the Managers covering that School's Zone; any change that would break this (assigning a
  non-covering Manager, moving a School to a Place in a Zone its Manager does not cover, or removing a Manager
  from a Zone that still has Schools assigned to them) MUST be refused as a single unit with no
  partial effect and a plain-language reason. Concurrent requests that touch the same Manager's
  assignments MUST be evaluated one at a time, so the invariant cannot be broken by two requests that
  each pass the check.
- **FR-010**: Assignment changes MUST keep history: prior assignments are retained with their end
  date and are never overwritten or deleted.

**Teachers**

- **FR-011**: Admin and Director MUST be able to create, edit, list, and view Teacher records with
  name, contact details, and a status of in training, active, on leave, or exited, and to change
  status with an effective date along these paths only: in training → active, active ↔ on leave,
  and any status → exited. Exit is final; any other change MUST be refused with no change.
- **FR-012**: Admin and Director MUST be able to place a Teacher in a School and move them to
  another with a chosen effective date, keeping every prior placement as history. A date in the
  past may not precede the start of the Teacher's current placement; a future date schedules the
  move, which takes effect automatically on that date and can be changed or cancelled by Admin or
  Director until then. Placements for one Teacher never overlap and never leave a gap. The
  placement is an interim stand-in for the future Teacher–School contract and MUST be labelled as
  interim wherever it is shown.
- **FR-013**: A Teacher's accountable Manager MUST be derived from the Manager of the School they
  are placed in on the date being considered (today for scope), and MUST follow automatically when
  that School's Manager changes or a scheduled placement takes effect.
- **FR-014**: Teacher records MUST never be deleted; an exited Teacher keeps their record and
  history, cannot be placed in a School again, and cannot be reinstated. A returning person is
  entered as a new Teacher record.
- **FR-015**: A Manager MUST be able to view and edit only Teachers placed in their assigned
  Schools, seeing a Teacher's placement history only for Schools assigned to them; they MUST NOT create
  Teachers or change their School placement.
- **FR-016**: A Teacher user MUST be linked to at most one Teacher record, and one Teacher record to
  at most one user; when a Teacher exits, the link is released.

**Salary history**

- **FR-017**: Admin and Director MUST be able to record a salary change (amount in Indian Rupees
  and an effective date) for a Teacher; every change is a new history row and existing rows are
  never edited or deleted.
- **FR-018**: The system MUST report a Teacher's current salary and their salary as of any past
  date from the history.
- **FR-019**: Salary amounts and history MUST be visible and recordable only by Admin and Director,
  including in lists, searches, exports, and API responses; Managers and Teachers MUST NOT receive
  them.

**Data scope, shared queries, and enforcement**

- **FR-020**: Every list, search, count, and detail view of Schools and Teachers, and every audit
  export that includes master-data entries, MUST be
  filtered by the caller's data scope (Admin and Director organization-wide; Manager assigned only;
  Teacher own only; System none), independently of what the screen shows. A request for a record
  outside the caller's scope MUST be indistinguishable from a request for a record that does not
  exist. This spec adds no new export of Schools or Teachers.
- **FR-021**: The system MUST offer later modules one shared way to ask which Zones, Schools, and
  Teachers a given user may access (the union across the user's roles), so no other module
  re-implements scoping.
- **FR-022**: Role changes and assignment changes MUST take effect in data scope on the next request
  without the affected user signing in again.

**Dashboards and profile**

- **FR-023**: The Manager dashboard MUST show the number of assigned Zones, Schools, and Teachers
  with links to the corresponding lists, and an empty state when nothing is assigned.
- **FR-024**: A Teacher user MUST be able to see their own name, contact details, status, and
  current School under ACCOUNT → My Profile, and nothing about any other Teacher.

**Audit and consistency**

- **FR-025**: Every create, edit, status change, assignment change, placement change, salary change,
  and deactivation MUST be recorded in Change History or User Activity through the audit module
  with the actor, time, and previous and new values; no module in this spec writes its own audit
  tables.
- **FR-026**: Concurrent edits to the same record MUST NOT silently overwrite each other; the later
  save is refused with a clear message.
- **FR-027**: All list screens MUST support search, pagination, and consistent loading, empty, and
  error states, and every dialog or form MUST show inline validation.

### Key Entities

- **Zone**: a named grouping of Places and Schools (a district, or a sub-district when split). Has
  one or more Managers over time. Never silently deleted while anything depends on it.
- **Place**: a town, city, or village with a name and PIN code, belonging to one Zone. Neither name
  nor PIN code is unique. Schools are located in Places.
- **School**: a customer site with a profile (name, address, contact person and phone, billing
  contact), exactly one Place (and through it exactly one Zone), at most one current Manager, and an
  active/inactive state.
- **Manager**: a record for a user holding the Manager role, with the Zones they cover and the
  Schools assigned to them. Their data scope is derived from these assignments.
- **Zone–Manager Assignment** and **School–Manager Assignment**: dated, never-overwritten links
  that keep history; a School's Manager must always cover the School's Zone.
- **Teacher**: a person placed with Schools, with name, contact details, status (in training,
  active, on leave, exited), and optionally one linked user account. Never deleted.
- **Teacher Placement**: an interim, dated link from a Teacher to a School with a start date (and an
  end date once superseded) that keeps every prior placement, may be scheduled for a future date,
  never overlaps another placement of the same Teacher, and is to be replaced by the future
  Teacher–School contract.
- **Salary History Entry**: an amount in rupees with an effective date for one Teacher; append-only.

## Role & Permission Impact *(mandatory — Constitution Principles II–IV)*

| Role     | Menu (section → item) | Default actions | Data scope |
| -------- | --------------------- | --------------- | ---------- |
| Admin    | MASTER DATA → Zones, Schools, Managers, Teachers | All (View, Create, Edit, Delete); Teacher salary: View, Create | Org-wide |
| Director | MASTER DATA → Zones, Schools, Managers, Teachers | View, Create, Edit; Teacher salary: View, Create | Org-wide |
| Manager  | MASTER DATA → Schools, Teachers; Dashboard (assigned figures) | View on assigned Schools; Edit limited to a School's contact person, contact phone, and address; View and Edit on assigned Teachers; no Create, Delete, or salary | Assigned (Zones → Schools → Teachers) |
| Teacher  | ACCOUNT → My Profile | View own profile (no salary) | Own |
| System   | none | none | None (System MUST NOT see teacher or school business data) |

**New permission keys**: modules `ZONES`, `SCHOOLS`, `MANAGERS`, `TEACHERS`, and `TEACHER_SALARY`.
Seeded (runtime-editable): `ZONES.{VIEW,CREATE,EDIT,DELETE}`, `SCHOOLS.{VIEW,CREATE,EDIT,DELETE}`,
`MANAGERS.{VIEW,CREATE,EDIT}`, `TEACHERS.{VIEW,CREATE,EDIT}` and `TEACHER_SALARY.{VIEW,CREATE}` true
for Admin; the same minus `DELETE` for Director; `SCHOOLS.{VIEW,EDIT}` and `TEACHERS.{VIEW,EDIT}`
true for Manager (assigned scope); everything false for Teacher and System. Teacher's My Profile uses
the existing `ACCOUNT_PROFILE` module (spec 002). Only Admin, Director, and System may edit the
role→permission matrix; this spec does not change that.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An Admin can set up a Zone, add a School to it, assign a Manager, and place a Teacher
  in that School in under 10 minutes, with no developer help.
- **SC-002**: In testing across at least two Managers with different assignments, 100% of School and
  Teacher lists, searches, counts, exports, and direct links return only the caller's own data; no
  test finds a leaked record.
- **SC-003**: 100% of attempts to assign a School to a Manager who does not cover its Zone, or to
  remove a Manager from a Zone that still has their Schools, are refused with no partial change.
- **SC-004**: A bulk import of 1,000 Places reports per-row results in under 30 seconds, and a bad
  row never prevents any valid row from being added.
- **SC-005**: A salary lookup for any past date returns the correct amount in 100% of tested
  history sequences, including dates before the first entry (reported as "no salary recorded").
- **SC-006**: 100% of create, edit, status, assignment, placement, and salary changes appear in the
  audit trail within 5 seconds with actor, time, and previous and new values.
- **SC-007**: A Manager sees their assigned counts on the dashboard within 3 seconds of opening it,
  and a change to their assignments is visible on the next page load without signing in again.
- **SC-008**: 100% of attempts by Teacher or System to reach any Zone, School, Manager, or Teacher
  management screen, or by Manager or Teacher to reach salary data, are refused.
- **SC-009**: Every master-data screen is usable by keyboard alone and passes the same
  accessibility checks as earlier screens in both light and dark themes.

## Assumptions

- Places follow the earlier Zone/Place model recorded in the project tracker: a Zone is a named set
  of Places (town, city, or village identified by name and a six-digit PIN code); no street-level
  address is stored on a Place.
- "Bulk import" in this spec means importing Places; bulk import of Schools or Teachers is out of
  scope. The first version accepts a pasted list of rows; file upload parsing can follow later.
- A Manager record is separate from the user account (spec 001/004) and links to exactly one user
  holding the Manager role; users and roles themselves are still created and edited in spec 004.
- A Teacher record may exist before the Teacher has a user account; linking the two is an Admin or
  Director action and is optional. Creating the user account itself stays in spec 004.
- Teacher status values are: in training, active, on leave, exited, with the allowed changes in
  FR-011 (exit is final). Leave balances and approvals belong to the later leave spec; "on leave"
  here is only a status label.
- Bank details are deliberately excluded and belong to the later payroll spec.
- Salary is a single HLS-offered amount in Indian Rupees per effective date; school contract rates,
  margin, and payslips belong to later specs.
- The Teacher–School placement is interim: one current School per Teacher at a time, kept with full
  history, and replaced by the Teacher–School–Manager contract in the later school-billing spec.
- Managers cannot see Teacher salary by default (the most conservative reading of the role matrix);
  the matrix is runtime-editable if the business decides otherwise.
- Director cannot delete a Zone (the `ZONES.DELETE` grant is Admin-only); deactivating or reactivating a
  School is an Edit and Director may do it.
- Deactivated Schools, Managers, and Teachers are kept, shown as inactive, and excluded from default
  pickers; nothing in this spec hard-deletes a record except an unused Zone or an unused Place (one
  with no Schools in it).
- Specs 002 (access model and shell) and 003 (audit) are already implemented and reused as-is;
  spec 004's user management is the source of Manager and Teacher user accounts.
- Dates are shown as DD/MM/YYYY and amounts in rupees with Indian digit grouping.
