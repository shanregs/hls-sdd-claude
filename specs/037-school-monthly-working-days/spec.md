# Feature Specification: School Monthly Working Days (School Master Data)

**Feature Branch**: `037-school-monthly-working-days`

**Created**: 2026-10-12

**Status**: Draft

**Input**: User description: "School Monthly Working Days master data. Every school has a different monthly calendar that determines the number of working days in each month (e.g. one school counts the full 30 days for a 30-day month and 31 for a 31-day month, another counts fewer). We need master data holding, for each school and each month, the number of working days, so the figure can be looked up for any school. Provide a separate screen in School Master Data showing a table / data grid of all schools' monthly working days with full CRUD (create, view, edit, delete), plus the backend (module, API, migration, permissions, navigation, audit) to support it."

## Clarifications

Decided by default while writing (no one was available to answer); each is also listed under Assumptions and should be confirmed.

- Q: Is this master a replacement for the School working calendar of spec 008, an override of it, or a source of the monthly figure? → A: It is the **source of the monthly figure only**. The calendar of spec 008 (weekly off days and non-working dates) keeps deciding *which dates* are working days; this master states *how many working days* a School counts in a month for pay. Neither replaces the other.
- Q: What happens when a School has no row for a month? → A: The figure falls back to the count derived from the spec 008 calendar for that School and month. A consumer is always told which of the two it received.
- Q: Which values are valid? → A: From 0 up to the number of days in that calendar month, in steps of half a day.
- Q: Who may see and change it? → A: Admin: View, Create, Edit, Delete. Director: View, Create, Edit (no Delete, as for other School master data). Manager, Teacher and System have no screen; Managers and Teachers see the figure only through the screens of the modules that use it.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Keep a School's Monthly Working Days (Priority: P1) 🎯 MVP

An Admin or Director opens MASTER DATA → School Working Days and records, for a School and a month, how many working days that School counts (for example 30 for a 30-day month for one School, 26 for another). They can correct the figure later and, as Admin, remove a row that was entered by mistake. Each School and month has at most one figure.

**Why this priority**: Without the stored figure nothing else in this feature has value. It is the whole master-data record.

**Independent Test**: Add 26 working days for School A in March, reopen the row and see 26; change it to 25.5 and see the change; try to add March for School A a second time and be refused; as Admin delete the row and see the month return to "not set".

**Acceptance Scenarios**:

1. **Given** a School with no figure for a month, **When** an Admin or Director saves a valid number of working days for it, **Then** the figure is stored, shown in the grid, and the change is audited.
2. **Given** a School and month that already have a figure, **When** someone tries to add another for the same School and month, **Then** it is refused with a message that names the existing row and offers to edit it.
3. **Given** a 30-day month, **When** a figure above 30, below 0, or not a whole or half day is entered, **Then** it is refused and the allowed range for that month is named. A figure of 0 and a figure equal to the days in the month are both accepted.
4. **Given** an existing row, **When** an Admin or Director edits its figure, **Then** the new figure replaces the old one, the audit entry records the old and the new value, and two people editing the same row at once never silently overwrite each other (the later save is told the row changed).
5. **Given** an existing row, **When** an Admin deletes it, **Then** after confirmation the row is removed, the audit entry keeps what was removed, and the month falls back to the calendar-derived count.
6. **Given** a Director, **When** they look at a row, **Then** Delete is not offered to them (unless the permission matrix was changed to grant it).
7. **Given** a School that is later deactivated, **When** its rows are viewed, **Then** they remain visible and editable for historical months.

---

### User Story 2 - See All Schools' Monthly Working Days in a Data Grid (Priority: P1)

The screen shows a table of every School's monthly working days so a person can scan, compare and correct many Schools quickly. They filter by School (and Zone) and by year, search by School name, and sort. Add, edit and delete happen in the grid itself, with a confirmation before a row is deleted.

**Why this priority**: The request is explicitly for a grid screen with full CRUD; it is how the data is actually maintained.

**Independent Test**: With rows for several Schools across two years, filter to one year and one School and see only those rows; add a row inline; edit another inline and cancel without saving; delete a third and confirm.

**Acceptance Scenarios**:

1. **Given** rows for several Schools, **When** the screen opens, **Then** it shows the current year, one row per School per month that has a figure, with School, Zone, month, working days, days in the month, and last changed by and when.
2. **Given** the grid, **When** the user filters by School, Zone or year, or searches by School name, **Then** only matching rows are shown and the filters survive moving to the next page.
3. **Given** a row, **When** the user chooses to edit it, **Then** the figure becomes editable in place with inline validation, and Save and Cancel; Cancel leaves the stored figure untouched.
4. **Given** the add action, **When** the user picks a School and a month and enters a figure, **Then** a new row appears in the grid without leaving the screen; a School and month that already have a figure cannot be picked again.
5. **Given** the delete action on a row, **When** it is chosen, **Then** a confirmation names the School and month and states that the month will use the calendar-derived count; nothing is removed until it is confirmed.
6. **Given** the grid is loading, empty, or the request failed, **When** it is shown, **Then** it shows, respectively, a loading state, an empty state that explains how to add the first row (the hint hidden if the user cannot add), and an error state with a retry; a failure to save a row keeps the user's entry and says what went wrong.
7. **Given** a user without View, **When** they reach the screen by its address, **Then** they see the not-authorized page, and the menu item is not shown to them.
8. **Given** a phone-width screen, **When** the grid is shown, **Then** there is no horizontal page scroll (the grid scrolls inside itself or shows rows as cards).

---

### User Story 3 - Other Modules Read the Figure Through a Published Interface (Priority: P1)

Payroll, Manager attendance and the attendance monthly rollups need "how many working days does School X count in month M". They ask one published interface and receive the stored figure when there is one, otherwise the calendar-derived count, together with which of the two it is. They never read this module's tables.

**Why this priority**: The master only matters if the consumers use it, and one place that answers keeps every module agreeing on the same figure.

**Independent Test**: Ask for a School and month with a stored figure, for one without, and for a School with no calendar override; confirm the stored figure, the fallback count and its source label are returned; ask for many Schools and a year in one request.

**Acceptance Scenarios**:

1. **Given** a stored figure of 26 for School A in March, **When** a consumer asks for it, **Then** it receives 26 labelled as stored, even if the calendar would give 27.
2. **Given** no stored figure for School B in March, **When** a consumer asks, **Then** it receives the count derived from School B's calendar (its weekly offs and non-working dates, or the default calendar) labelled as calendar-derived.
3. **Given** a request for several Schools and months, **When** it is answered, **Then** the number of lookups does not grow with the number of Schools.
4. **Given** a stored figure of 0, **When** a consumer asks, **Then** it receives 0 labelled as stored; the consumer decides what a month with no working days means (payroll already never divides by zero, spec 013a).
5. **Given** a figure that is changed, **When** a consumer asks again, **Then** it receives the new figure; a month that a consumer has already finalised is not recomputed silently (see Edge Cases).
6. **Given** a consumer module acting for a person who has no permission to see School working days (for example payroll run by a user with Process), **When** it asks, **Then** the lookup still succeeds, because it is a module-to-module read and the permission applies to the screen, not to the lookup.

---

### User Story 4 - Audit Trail and History of Every Change (Priority: P2)

Every creation, edit and deletion is recorded in the audit trail with who did it, in which role, when, the School and month, and the old and new figures, so a pay dispute can be answered. A row can show its history.

**Why this priority**: The figure drives pay and attendance (Constitution Principle I), but the screen works without a history view, so the history view is P2. The recording itself is required from the start of P1.

**Independent Test**: Create, edit, then delete a row and see three audit entries with the right values; open the row's history from the grid and see the same.

**Acceptance Scenarios**:

1. **Given** a create, edit or delete, **When** it succeeds, **Then** exactly one audit entry is written with actor, role, time, School, month, old value and new value; a refused change writes none.
2. **Given** a row, **When** the user opens its history, **Then** they see its changes newest first, including a deletion.
3. **Given** the audit trail, **When** anyone tries to change an entry, **Then** it is refused (existing audit rules, spec 003).

---

### User Story 5 - Fill a Year Quickly (Priority: P3, optional)

When a School is set up, an Admin or Director can fill all twelve months of a year in one step by copying the figures of another year for that School, instead of twelve separate additions.

**Why this priority**: A convenience; every figure can be entered one by one without it. It is included only because twelve rows per School per year is a lot of typing, and it may be left for a later release without affecting the rest.

**Independent Test**: Choose School A, copy 2026 to 2027, and see new rows with the same figures for months that had none, with months that already had a row left unchanged and reported.

**Acceptance Scenarios**:

1. **Given** a School with figures for a year, **When** the user copies them to another year, **Then** rows are created for months that have none, existing rows are left untouched and listed in the result, and a figure that exceeds the days in the target month (for example 29 working days copied into February) is not copied and is listed for the user to enter.
2. **Given** the copy, **When** it completes, **Then** one audit entry per row created is written.

---

### Edge Cases

- The spec 008 calendar changes (a holiday is added) after a figure was stored: the stored figure does not change; it is a deliberate statement for pay. The screen shows the calendar-derived count next to the stored figure so a difference is visible.
- A figure is changed for a month that payroll or Manager attendance has already used or locked: the change is allowed (corrections happen) and is audited; consumers that have locked or finalised a month keep the figure they used, and this feature never recomputes a finalised month by itself. How a consumer refreshes an open month is that consumer's rule.
- A figure is added for a past month or a month ahead: allowed within the range stated under Assumptions.
- A School is deactivated: rows are kept; a School that still has rows cannot be removed from the master data (consistent with keeping history).
- A fraction other than a half (for example 25.3) or a negative number is entered: refused with the allowed values named.
- The same row is edited by two people at once: the later save is refused with a message that the row changed, showing the current figure.
- A Manager's Zone or School assignment changes: no effect; this master is organization-wide data keyed only by School and month.
- A Teacher moves between Schools in a month: not this feature's concern; consumers decide which School's figure applies to a person on a date.
- A user filters to a year with no rows: the empty state is shown.

## Requirements *(mandatory)*

### Functional Requirements

**The master record**

- **FR-001**: The system MUST store, for each School and each calendar month (year and month), one figure: the number of working days that School counts in that month.
- **FR-002**: At most one figure MUST exist per School and month; adding a second MUST be refused and name the existing one.
- **FR-003**: A figure MUST be at least 0 and at most the number of days in that calendar month (leap years respected), in whole or half days; anything else MUST be refused with the allowed range named.
- **FR-004**: A figure MUST belong to an existing School; a figure for an unknown School MUST be refused.
- **FR-005**: Rows MUST be kept when their School is deactivated, and a School that has rows MUST NOT be removable from the master data.

**Relationship to the working calendar and fallback**

- **FR-006**: This master MUST be the source of the *monthly count only*. The School working calendar of spec 008 (weekly off days, holidays and other non-working dates) MUST remain the only authority on *which dates* are working days, and MUST NOT be changed, replaced or contradicted by this master.
- **FR-007**: When no row exists for a School and month, the figure MUST fall back to the count of working dates derived from that School's calendar (its override if it has one, otherwise the default calendar) for that month.
- **FR-008**: Every answer MUST say whether the figure is *stored* or *calendar-derived*, so a consumer and a person can tell.
- **FR-009**: Deleting a row MUST return that month to the fallback; it MUST NOT leave a gap or a zero.
- **FR-010**: Storing a figure MUST NOT alter, recompute or lock any attendance mark, leave balance or payroll figure by itself.

**Consumers and the published interface**

- **FR-011**: The module MUST publish one read interface that returns the figure for a School and month, for many Schools and months in one call, and for a whole year; its answers MUST carry the source label of FR-008, and the number of lookups MUST NOT depend on the number of Schools asked for.
- **FR-012**: Other modules (payroll per spec 013a and the monthly run of 013b, Manager attendance per spec 032, and the attendance monthly rollups of spec 008) MUST read the figure only through that interface and MUST NOT read this module's tables. Adopting the figure is tracked as an amendment to each of those specs and is not part of this feature's delivery; until each adopts it, it keeps its current calculation, so nothing changes silently.
- **FR-013**: The interface MUST be usable by module code without the person-level screen permission of FR-018, and MUST NOT expose any person's data, only School, month, figure and source.
- **FR-014**: Consumers MUST receive the same figure for the same School and month at the same moment.

**Screen and CRUD**

- **FR-015**: A separate screen MASTER DATA → School Working Days MUST list the figures of all Schools in a data grid with: School, Zone, year and month, working days, days in the month, the calendar-derived count for reference, last changed by and when, and the row actions the user may use.
- **FR-016**: The grid MUST support filtering by School, Zone and year, searching by School name, sorting, and paging, with filters that persist across pages; the default view MUST be the current year.
- **FR-017**: Users with the Create permission MUST be able to add a row from the grid, choosing a School and a month that has no figure yet; users with Edit MUST be able to change a figure in place with inline validation and a Cancel; users with Delete MUST be able to delete a row after a confirmation that names the School and month and states the fallback.
- **FR-018**: Actions a user may not perform MUST be hidden, not disabled; the menu item MUST appear only to users with View; a deep link without View MUST show the not-authorized page.
- **FR-019**: The grid MUST show loading, empty and error states, MUST keep a user's unsaved entry when a save fails, and MUST have no horizontal page scroll at phone width.
- **FR-020**: Months MUST be shown by name and any date in DD/MM/YYYY, per the application's locale.
- **FR-021**: A save of an edited row MUST fail with a clear message, and show the current figure, if someone else changed the row since it was loaded.

**Audit, permissions and scope**

- **FR-022**: Every create, edit and delete MUST be written to the audit trail (Principle I) with actor, role(s), time, School, month, and the old and new figure; a deleted row's last figure MUST be kept in its entry. Refused changes write nothing.
- **FR-023**: A row's change history MUST be viewable from the grid by users with View.
- **FR-024**: The backend MUST enforce the permission on every endpoint independently of the menu (Principle X) and MUST fail closed.
- **FR-025**: The figures are organization-wide data: Admin and Director see all Schools, and no Zone or School scoping applies because Managers do not have the screen. If a Manager is later granted View, scoping to their assigned Schools MUST apply (Principle III).
- **FR-026**: System MUST have no access to this screen or its data.

**Optional**

- **FR-027** *(optional, P3)*: Users with Create MAY copy one year's figures for a School to another year; existing rows MUST NOT be overwritten, figures that do not fit the target month MUST be skipped and reported, and each created row MUST be audited.

### Key Entities

- **School Monthly Working Days**: a figure for one School and one calendar month: the working days counted (0 to the days in the month, whole or half), plus who last changed it and when. Unique per School and month. Kept even when the School is deactivated.
- **Working Days Answer**: what a consumer receives: School, month, the figure, and the source (stored or calendar-derived). Not stored.
- **School Working Calendar (spec 008)**: weekly off days and non-working dates per School or default. Unchanged; supplies the fallback count.
- **Audit Entry (spec 003)**: the record of each create, edit and delete.

## Role & Permission Impact *(mandatory — Constitution Principles II–IV)*

| Role     | Menu (section → item) | Default actions | Data scope |
| -------- | --------------------- | --------------- | ---------- |
| Admin    | MASTER DATA → School Working Days | View, Create, Edit, Delete | Org-wide |
| Director | MASTER DATA → School Working Days | View, Create, Edit (no Delete) | Org-wide |
| Manager  | none | none by default; may be granted View later, then limited to assigned Schools | Assigned (only if granted) |
| Teacher  | none | none | None |
| System   | none | none (System MUST NOT see School business data) | None |

**New permission keys**: module `SCHOOL_WORKING_DAYS` with `SCHOOL_WORKING_DAYS.{VIEW,CREATE,EDIT,DELETE}`. Seeded true for Admin (all four) and Director (View, Create, Edit); false for Manager, Teacher and System. Runtime-editable by Admin, Director and System through Role & Permission Management; that rule is not changed by this spec. The Constitution's default role access matrix gains a School Working Days row (a PATCH amendment, as for earlier master-data specs), and the navigation model gains the MASTER DATA item.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An Admin or Director can add the figure for one School and month in under 30 seconds from opening the screen.
- **SC-002**: A full year for one School can be entered in under 3 minutes (under 1 minute with the optional copy, if delivered).
- **SC-003**: 100% of attempts to store a figure outside 0 to the days in the month, a non-half fraction, or a duplicate School and month are refused, with the reason named.
- **SC-004**: 100% of creates, edits and deletes have an audit entry showing who, when, and the old and new figure; 0 refused changes have one.
- **SC-005**: For every School and month, a consumer receives either the stored figure or the calendar-derived count, labelled; there is no School and month for which no answer exists.
- **SC-006**: Once payroll, Manager attendance and the attendance rollups adopt the figure, they show the same working days for the same School and month in 100% of checked cases.
- **SC-007**: A user without View never sees the menu item and gets the not-authorized page on a deep link; users without Create, Edit or Delete never see those actions (checked per role).
- **SC-008**: With 500 Schools and 6,000 rows for a year, a filtered page of the grid appears in under 2 seconds.
- **SC-009**: Two people saving the same row at once never lose an update silently: the second is always told.

## Assumptions

- **Source of the count, not the calendar.** The spec 008 calendar keeps deciding which dates are working; this master only states the monthly count a School counts, which can differ from the calendar-derived count (for example a School that counts all 30 days of a 30-day month while its calendar excludes Sundays). It is neither a replacement nor a date-level override. *To confirm.*
- **Fallback when no row:** the calendar-derived count (FR-007). A missing row is not an error and not zero. *To confirm.*
- **Values:** 0 to days-in-month in steps of 0.5 (the half day matches the half-day treatment in specs 008, 009 and 013a). Whole days only could be chosen instead. *To confirm.*
- **Permissions:** Admin full, Director without Delete (consistent with spec 005 School master data), Manager none by default. *To confirm.*
- **Hard delete is allowed** for a row (Admin only), because the audit trail keeps the history and the fallback restores a defined answer; rows are not versioned like salaries (spec 013a) because the figure is a master value, not a pay agreement. *To confirm.*
- **Spec number 037:** the Spec Kit sequence would give 033, but the roadmap reserves 033 to 036 (mobile and related specs). 037 is used to avoid a clash, and the roadmap gains a row for it. *To confirm.*
- **Where it lives:** next to Schools in the School master data of spec 005, as an addition to it. Whether it is a sub-area of the school module or a module of its own is a planning decision.
- **Range of months:** any month from the earliest year of operation to five years ahead is accepted; the exact limits are a planning detail.
- **Consumers adopt later:** specs 013a/013b, 032 and 008 are amended separately to read the figure through the interface; this spec delivers the master, screen and interface only. Until then their present calculation continues unchanged.
- **Optional items:** copy of a year (FR-027, User Story 5) may be deferred without affecting the rest. Import from a file, month-level bulk edit and notification on change are out of scope.
- **Mobile apps** do not show this screen; Managers see the figure only inside attendance and payroll results.
- Depends on: spec 005 (Schools), 002 (navigation and permissions), 003 (audit), 008 (calendar fallback). Constitution v2.5.3.
