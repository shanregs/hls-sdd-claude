# Feature Specification: Designations and Employment Details

**Feature Branch**: `005a-designations`

**Created**: 2026-10-08

**Status**: Draft

**Input**: User description: "005a-designations: Designations and employment details for Managers and Teachers (amendment A1 to spec 005). A Designation is a named job title that salary structures are attached to (spec 013a). An Admin or Director keeps the list under MASTER DATA → Designations: name, whether it applies to Teachers or Managers, and active or retired. A Manager record gains a designation, an employee id (unique, optional until set), a joining date and, when the Manager is made inactive, an exit date. A Teacher record gains a designation and an optional employee id. The Teachers and Managers screens show and edit these fields (Admin and Director; a Zone Manager sees them but cannot change them). Every change is audited. Public interfaces expose the designation, employee id and joining and exit dates so 013a and 032 never read the tables. Full source: docs/spec-inputs/005a-designations.md."

## Clarifications

### Session 2026-10-08

- Q: Does a person's designation keep a dated history? → A: For Managers, yes: each change is a new dated row and the designation on a date is the latest row on or before it. For Teachers, only the current designation is kept (the audit log keeps earlier ones), because a Teacher's pay follows their own recorded salary.
- Q: Can a Manager's designation be removed once set? → A: No. It can only be changed to another active designation; "missing" means never set. A wrong entry is corrected by a newer row.
- Q: What decides who may change a person's designation, employee id and joining or exit dates? → A: The `DESIGNATIONS` `EDIT` permission (Admin and Director by default); the Manager and Teacher edit permissions of spec 005 do not cover these fields.
- Q: How are Managers made inactive before this spec, who have no exit date, handled? → A: They are flagged "exit date missing" and counted in the missing-details list; an Admin or Director enters the date afterwards, and nothing blocks the record.
- Q: Can a Manager's exit date be later than the day they are made inactive? → A: Yes, up to 90 days ahead (a notice period); the Manager is inactive from the day recorded and the exit date is the last day they are paid.
- Q: What effective date may a Manager's first designation have when no joining date is set? → A: Any date up to today; the not-before-this-month rule applies only to later changes.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Keep the List of Designations (Priority: P1) 🎯 MVP

An Admin or Director opens MASTER DATA → Designations and sees the job titles in use: each with its name, whether it
is for Teachers or for Managers, whether it is active or retired, and how many people hold it. They add a designation,
rename one, retire one that is no longer offered, and bring a retired one back. A retired designation stays on the
people who already have it and cannot be chosen for anyone new.

**Why this priority**: Salary structures (spec 013a) and Manager attendance (spec 032) attach to a designation. Nothing
else here can be set until the list exists.

**Independent Test**: Add "Primary Teacher" for Teachers and "Zone Manager" for Managers, rename one, retire the other,
and see that the retired one is still shown on a person who holds it but is missing from the choice offered for new
people.

**Acceptance Scenarios**:

1. **Given** the Designations screen, **When** an Admin adds a designation with a name and "Teachers" or "Managers",
   **Then** it is listed as active with zero people and can be chosen for people of that kind.
2. **Given** a designation, **When** an Admin renames it, **Then** every person holding it shows the new name and the
   audit log shows the old and new names.
3. **Given** a designation held by people, **When** an Admin retires it, **Then** it stays on those people, shows as
   retired, and is no longer offered when choosing a designation for someone.
4. **Given** a retired designation, **When** an Admin reactivates it, **Then** it is offered again.
5. **Given** the form, **When** the name is empty, longer than 80 characters, or the same as another designation of the
   same kind (ignoring capital letters and extra spaces), **Then** it is refused and the problem is named.
6. **Given** a designation that people hold, **When** anyone tries to change its kind (Teachers or Managers) or delete
   it, **Then** it is refused; a designation is never deleted.
7. **Given** a Zone Manager, Teacher or System user, **When** they look for Designations, **Then** the menu item is not
   shown and the page is refused.

---

### User Story 2 - Record a Manager's Designation and Employment Dates (Priority: P1)

An Admin or Director opens a Manager and sets the Manager's designation, an employee id, and the joining date (the
first day the Manager is employed, which is not the date they were given a Zone). When a Manager is made inactive
the exit date is recorded with it. A Zone Manager can see these fields on their own record and on others' but cannot
change them.

**Why this priority**: A Manager's pay (spec 013a) starts from the joining date, ends at the exit date, and is the
salary of their designation. These facts do not exist anywhere today.

**Independent Test**: Set a designation, employee id and joining date on a Manager, make the Manager inactive with an
exit date, and see all four on the Manager's record, in the audit log, and read-only for a Zone Manager.

**Acceptance Scenarios**:

1. **Given** a Manager record, **When** an Admin sets a designation, **Then** only active designations for Managers
   are offered, and the Manager shows the chosen one.
2. **Given** a Manager, **When** an Admin enters an employee id, **Then** it is saved; an id already used by any other
   Manager or Teacher (ignoring capital letters and spaces at the ends) is refused and names the person who has it.
3. **Given** a Manager, **When** a joining date is entered, **Then** it is saved; it may be in the past or the future
   but not after the Manager's exit date.
4. **Given** a Manager being made inactive, **When** the Admin confirms, **Then** an exit date is asked for (today by
   default), it cannot be before the joining date or more than 90 days after today, and it is saved with the change; the
   Manager is inactive from that moment, and the exit date is the last day they are paid.
5. **Given** an inactive Manager made active again, **When** the Admin confirms, **Then** the exit date is cleared and
   the earlier exit date remains in the audit log.
6. **Given** an existing Manager with none of these fields, **When** their record is opened, **Then** it still works,
   and it shows "designation missing" and "joining date missing".
7. **Given** a Zone Manager, **When** they open a Manager record, **Then** they see the fields but have no way to
   change them, and a direct request to change them is refused.
8. **Given** any change to these fields, **When** the audit log is opened, **Then** it shows who, when, and the prior
   and new values.
9. **Given** a Manager with a designation, **When** an Admin records a new one with a later effective date, **Then**
   both rows stay in the Manager's history, the earlier unchanged, and the designation on any date is the latest row on
   or before it; an effective date before the first day of the current month is refused, except for the Manager's first
   designation, which may start on the joining date (or on any date up to today when no joining date is set).

---

### User Story 3 - Record a Teacher's Designation and Employee Id (Priority: P1)

An Admin or Director opens a Teacher and sets the Teacher's designation and, optionally, an employee id. A Zone Manager
sees them for Teachers in their Schools but cannot change them. A Teacher's reporting date is not stored here: it is
the first School assignment (spec 012).

**Why this priority**: The designation is the default shown when a Teacher's salary is first set, and payroll reports
group by it. Teachers are the largest group.

**Independent Test**: Set a designation and an employee id on a Teacher, see them on the Teachers screen, see a Zone
Manager read them for a Teacher in their School and fail to change them, and see an existing Teacher with neither
still working.

**Acceptance Scenarios**:

1. **Given** a Teacher record, **When** an Admin sets a designation, **Then** only active designations for Teachers are
   offered, and the Teacher shows the chosen one.
2. **Given** a Teacher, **When** an employee id is entered or cleared, **Then** it is saved; a duplicate of any other
   Teacher's or Manager's id is refused.
3. **Given** an existing Teacher with no designation, **When** their record is opened, **Then** it works as before and
   shows "designation missing".
4. **Given** a Zone Manager, **When** they open a Teacher in their School, **Then** they see the fields read-only; for a
   Teacher outside their scope the record is not found, as before.
5. **Given** a Teacher, **When** they open My Profile, **Then** they do not see an employee id or designation field they
   can edit.
6. **Given** a Teacher who has exited, **When** an Admin opens the record, **Then** the designation and employee id are
   still shown and can still be corrected.
7. **Given** any change, **When** the audit log is opened, **Then** it shows who, when, and the prior and new values.

---

### User Story 4 - Find What Is Missing Before Payroll (Priority: P2)

An Admin or Director can see at a glance which Managers and Teachers have no designation, and which Managers have no
joining date, so the gaps are closed before payroll and salary structures are used.

**Why this priority**: Payroll cannot pay someone who has no designation or no joining date. Finding the gap early is
cheaper than finding it during a run. The structure works without it.

**Independent Test**: With some Managers and Teachers missing the fields, see counts and a filter on the Managers,
Teachers and Designations screens that list exactly those people.

**Acceptance Scenarios**:

1. **Given** Managers and Teachers without a designation, **When** the Designations screen is opened, **Then** it shows
   how many of each kind are missing one, linking to a list of those people.
2. **Given** the Managers screen, **When** it is filtered by "missing details", **Then** it lists Managers with no
   designation, no joining date, or inactive with no exit date; an Admin can enter the exit date afterwards.
3. **Given** the Teachers screen, **When** it is filtered by "missing designation", **Then** it lists exactly those
   Teachers.
4. **Given** every person has the fields, **When** the screens are opened, **Then** no flag is shown and the counts are
   zero.

---

### Edge Cases

- A designation is retired while a person holds it: the person keeps it and it still shows; it cannot be assigned to
  anyone else. Changing the person to another designation is allowed, and they cannot be changed back to the retired one
  unless it is reactivated.
- A designation is renamed: the change shows everywhere at once; no person record needs updating.
- Two designations with the same name for different kinds (for example "Coordinator" for Teachers and for Managers):
  allowed, because the kind is part of the identity.
- An employee id that differs only by capital letters or spaces at the ends from another: treated as the same and
  refused.
- A Manager with no joining date is saved and works; it only shows as missing. Nothing blocks the record.
- A joining date later than today: accepted (a planned joiner).
- A Manager's exit date earlier than the joining date: refused.
- A Teacher moves between kinds (a Teacher becomes a Manager): a person has one designation of their own kind; the
  old record is not changed by this spec, and a new Manager record starts with no designation.
- A Manager is promoted: a new designation is recorded with its effective date; the earlier one stays in the history
  and applies to dates before the new one. A Teacher's designation change is not dated: only the current designation is
  kept (the audit log keeps the earlier ones).
- Two designation changes for one Manager on the same effective date: both are kept; the one recorded last is in
  effect.
- A bulk import of Teachers (spec 005) that has no designation column: still accepted; the new fields stay empty.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST let an Admin or Director add a designation with a name (1 to 80 characters) and a kind
  (Teachers or Managers), and list all designations with kind, status (active or retired) and the number of people who
  hold each.
- **FR-002**: The system MUST refuse a designation whose name is empty, over 80 characters, or the same as another
  designation of the same kind ignoring capital letters and extra spaces.
- **FR-003**: The system MUST let an Admin or Director rename, retire and reactivate a designation. It MUST NOT allow
  deleting a designation or changing the kind of one that has ever been held by a person.
- **FR-004**: The system MUST offer only active designations of the matching kind when choosing one for a person, and
  MUST keep a retired designation on the people who already hold it.
- **FR-005**: The system MUST let an Admin or Director set or change a Manager's designation, and set, change or clear
  a Manager's employee id and joining date, and a Teacher's designation and employee id. A Manager's designation, once
  set, MUST NOT be cleared: it can only be changed to another active designation, and a wrong entry is corrected by a
  newer row.
- **FR-006**: The system MUST keep a Manager's designation as dated history: each change is a new row with an
  effective date, earlier rows are never edited or deleted, and the designation on a date is the latest row with an
  effective date on or before it (the one recorded last when several share a date). A change's effective date MUST NOT
  be earlier than the first day of the current month, except a Manager's first designation, which may start on their
  joining date, or on any date up to today when no joining date is set. A Teacher's designation is the current one only.
- **FR-007**: The system MUST keep an employee id unique across all Managers and Teachers together, ignoring capital
  letters and spaces at the ends; the id is optional, and when entered it MUST be 1 to 20 characters of letters, digits
  and hyphens.
- **FR-008**: The system MUST record a Manager's exit date when the Manager is made inactive (default today, not before
  the joining date when there is one, and not more than 90 days after today; it is the last day the Manager is paid, and may
  be later than the day they were made inactive) and clear it, keeping the earlier value in the audit log, when the Manager is made
  active again.
- **FR-009**: The system MUST allow a joining date on a Manager at any date, and MUST refuse one later than the exit
  date.
- **FR-010**: The system MUST keep existing Managers and Teachers working with none of the new fields, and MUST show
  "designation missing" and, for a Manager, "joining date missing" where they are empty, and "exit date missing" for a
  Manager who is inactive without one (including Managers made inactive before this spec).
- **FR-011**: The system MUST show the designation, employee id and (for a Manager) joining and exit dates on the
  Managers and Teachers screens to Admin, Director, and a Zone Manager within their scope, and MUST allow only Admin and
  Director to change them, refusing others on the server and not only in the screen. Changing any of these fields
  requires the `DESIGNATIONS` `EDIT` permission; the Manager and Teacher edit permissions of spec 005 do not cover them,
  so a Zone Manager who can edit a Teacher's profile still cannot change the Teacher's designation or employee id.
- **FR-012**: The system MUST audit every change to a designation and to these fields with who, when, and the prior and
  new values.
- **FR-013**: The system MUST show Admin and Director, on the Designations, Managers and Teachers screens, the number
  and list of people missing a designation (and Managers missing a joining date, or inactive without an exit date).
- **FR-014**: The system MUST NOT show or let a Teacher edit an employee id or designation on My Profile, and MUST NOT
  expose these fields to the System role.
- **FR-015**: The system MUST make the designation, employee id, joining date and exit date of any Manager or Teacher,
  and the designation list, available to other modules through public interfaces, so no other module reads the
  underlying tables.

### Key Entities *(include if feature involves data)*

- **Designation**: a named job title for Teachers or for Managers, with a status (active or retired), who created it
  and when. Never deleted; kind fixed once held.
- **Manager Employment Details** (added to the Manager record of spec 005): employee id, joining date and exit date.
- **Manager Designation Row**: a designation held by a Manager from an effective date, with who recorded it and when.
  Append-only; many rows per Manager form the history.
- **Teacher Employment Details** (added to the Teacher record of spec 005): designation and optional employee id. No
  reporting date: that is the first School assignment (spec 012).
- **Employee Id**: an optional label unique across Managers and Teachers together.

## Role & Permission Impact *(mandatory — Constitution Principles II–IV)*

| Role     | Menu (section → item) | Default actions | Data scope |
| -------- | --------------------- | --------------- | ---------- |
| Admin    | MASTER DATA → Designations; Managers and Teachers screens show the new fields | View, Create, Edit | Org-wide |
| Director | MASTER DATA → Designations; Managers and Teachers screens show the new fields | View, Create, Edit | Org-wide |
| Manager (Zone Manager) | none new; sees the new fields on the Managers and Teachers screens | View (read-only) | Assigned Zones, Schools and their Teachers, as in spec 005 |
| Teacher  | none | none | None |
| System   | none (System MUST NOT see business data) | none | None |

**New permission keys**: `DESIGNATIONS` (`VIEW`, `CREATE`, `EDIT`; the designation list). Seeded to Admin and Director
only; Teacher and System are not eligible, and a Zone Manager may be granted `VIEW` (never `CREATE` or `EDIT`) later in
Role & Permissions. `DESIGNATIONS` `EDIT` also gates changing a person's designation, employee id, joining date and exit
date; the Manager and Teacher edit permissions of spec 005 do not. The constitution's Default role access matrix gets a Designations row when this spec merges.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An Admin can add a designation and set it on a person in under 1 minute.
- **SC-002**: Every change to a designation, designation of a person, employee id, joining date and exit date has an
  audit entry with who, when, and the prior and new values; checked for each field.
- **SC-003**: A duplicate employee id (including one differing only by capital letters or end spaces) is refused 100%
  of the time, including two requests made at the same moment.
- **SC-004**: Zone Manager, Teacher and System attempts to change these fields or the designation list are refused on
  the server for every endpoint, proved by a test for each endpoint; the menu item is absent for all three.
- **SC-005**: With existing Managers and Teachers that have none of the new fields, every feature of specs 005, 008, 009
  and 012 behaves exactly as before; their existing tests pass unchanged.
- **SC-006**: In one screen, an Admin sees every Manager and Teacher missing a designation, and every Manager missing a
  joining date, with the count matching the list.
- **SC-007**: Reading a designation, employee id, joining date or exit date through the public interface for 300 people
  takes a fixed number of queries and returns in under 2 seconds.
- **SC-008**: For a Manager with three designation rows, the designation on any date matches the hand-computed answer,
  including two rows on one date and a change in the middle of a month; checked on a table of at least 6 cases.

## Assumptions

- This spec is amendment A1 to spec 005 in `docs/spec-roadmap.md` and is a prerequisite for 013a (salary structures)
  and 032 (Manager attendance). Spec 005 (Managers, Teachers, scope) is implemented and merged.
- The first list of designations is entered by an Admin or Director after release; none is fixed here. A development
  seed provides a few so screens can be tried.
- The employee id is typed by hand, is optional, and is not generated; a generated scheme can be added later.
- A Manager's exit date is added to the Manager record because spec 013a pays a Manager "until an exit date" and the
  Manager record today has only an active flag. It is set when a Manager is made inactive.
- A Teacher's exit and status dates are already kept by spec 005's status machine; nothing is added for them.
- Showing a Teacher their own designation and employee id, and importing designations in bulk, are out of scope.
- Salary amounts (013a), bank details, documents, leave balances and the Teacher status machine are out of scope.
- Dates are shown as DD/MM/YYYY (Constitution, Additional Constraints).
- The exit date is defaulted, not asked for, because a Manager is made inactive in User Management (spec 004), which this spec does not change: when the account is deactivated the exit date becomes today (audited), and an Admin or Director corrects it afterwards within the FR-008 limits.
- The Managers screen is reached by Admin and Director by default; a Zone Manager reads a Manager's fields wherever the Manager view is granted to them.
