# Feature Specification: School Working Days — Enter a Whole Year on One Screen

**Feature Branch**: `038-working-days-year-entry`

**Created**: 2026-10-16

**Status**: Draft

**Input**: User description: "School working days: enter a whole year on one screen. Extends spec 037 (School monthly working days, already merged). A 'year entry' screen where an authorised user picks one School and one year and sees all 12 months at once, each with an editable working-days field (0 to 31 in half-day steps, same validation as 037), showing the stored figure if one exists and the calendar-derived count as a hint. A single Save creates or updates only the months the user changed. Save all valid months and report the rest per month (stale version, finalised month notice, validation failure), keeping what the user typed for failed months. Same permissions as 037, same audit/history and optimistic-locking behaviour per month, works at 375 px without horizontal page scroll. One School at a time only; no multi-School grid."

## Clarifications

Decided by default while writing (no one was available to answer); each is also listed under Assumptions.

- Q: Is this a new master or a new way to maintain the figures of spec 037? → A: A **new way to maintain the same figures**. It stores nothing of its own. A figure entered here is the same record as one added in the School Working Days grid, with the same rules, audit and history.
- Q: When some months cannot be saved, are the others still saved? → A: **Yes.** All valid months are saved; each month that could not be saved is reported by name, and what the user typed in it is kept (decided with the requester on 2026-10-16).
- Q: Does the screen cover one School or several? → A: **One School and one year at a time.** A grid of many Schools is out of scope (decided with the requester on 2026-10-16).
- Q: Can this screen remove a stored figure? → A: **No.** Deleting stays where spec 037 put it (the grid, Admin only, with confirmation). A blank field means "nothing entered": it saves nothing and never deletes.

### Session 2026-10-16

- Q: When a month already holds exactly the figure the user typed (stored by someone else, or by the user's own earlier Save that timed out), is it a success or a conflict? → A: **Success.** It counts as "unchanged". Only a stored figure that **differs** from the typed one is refused as changed or already existing (FR-010, FR-011, FR-014).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Enter or Correct All Twelve Months of a Year in One Go (Priority: P1) 🎯 MVP

An Admin or Director opens MASTER DATA → School Working Days → Enter a year, picks a School and a year, and sees January to December in one list. Months that already have a figure show it; the others are empty and show the calendar-derived count as a hint. The user types the figures they want, presses Save once, and every month they changed is stored.

**Why this priority**: This is the whole request. Twelve separate additions in the grid are what the screen replaces.

**Independent Test**: Pick School A and a year with no figures; type 12 valid figures; press Save once; see 12 stored rows in the School Working Days grid. Reopen the screen, change two months, Save, and see only those two rows changed.

**Acceptance Scenarios**:

1. **Given** a School and year with no stored figures, **When** the user types figures for some or all months and saves, **Then** a figure is created for each month that has an entry, months left blank are not created, and the screen then shows the stored figures.
2. **Given** a School and year where some months have stored figures, **When** the screen opens, **Then** each of those months shows its stored figure, and each month without one is empty and shows its calendar-derived count as a hint, labelled as a hint and not as a stored figure.
3. **Given** the user has changed only some months, **When** they save, **Then** only the changed months are written; untouched months are left exactly as they were (no new version, no audit entry).
4. **Given** nothing has been changed, **When** the user looks at Save, **Then** it is not available and the screen says there is nothing to save.
5. **Given** a month whose field is changed back to the value it was loaded with, **When** the user saves, **Then** that month counts as unchanged.
6. **Given** a successful save, **When** it completes, **Then** the screen says how many months were created, how many updated and how many were unchanged, and shows the stored figures with their new versions so a second save works without reloading.
7. **Given** a blank field for a month that has a stored figure, **When** the user saves, **Then** nothing happens to that month: the stored figure stays and the screen shows it again.

---

### User Story 2 - Partial Save: Good Months Saved, Problem Months Reported (Priority: P1)

When some months cannot be saved, the others are still saved, and the user is told exactly which months failed and why, with what they typed still in place so they can fix it and save again.

**Why this priority**: A year screen is only usable if one bad month does not throw away eleven good ones. It is part of the first release.

**Independent Test**: Enter 12 figures where one is 31 for a 30-day month and another month was changed by someone else since the screen opened; Save; see 10 stored, 2 reported by month name with their reasons, and the two typed values still in the fields.

**Acceptance Scenarios**:

1. **Given** a figure outside 0 to the days in that month, or not a whole or half day, **When** the user saves, **Then** that month is refused with the allowed range named, every other valid month is saved, and the typed value stays in the field.
2. **Given** a month whose stored figure was changed by someone else since the screen was opened to a figure **different** from the one the user typed, **When** the user saves a change to it, **Then** that month is refused as changed (if the stored figure now equals the typed one, it is reported as unchanged), the current stored figure and who changed it are shown beside it, the user's typed value is kept, and every other month is saved. Nothing is silently overwritten.
3. **Given** a month that someone else created since the screen was opened, while this user is trying to create it, **When** they save and the stored figure **differs** from the typed one, **Then** that month is refused as already existing, the existing figure is shown, and the user's value is kept for them to apply or discard; if the stored figure **equals** the typed one, the month is reported as unchanged and counts as saved.
4. **Given** a month whose row was deleted by someone else since the screen was opened, **When** the user saves a change to it, **Then** that month is refused with a message that the row was deleted, and it is not recreated by this save.
5. **Given** a month the user's permissions do not allow them to change (for example a figure exists and they have Create but not Edit), **When** the screen is shown, **Then** that month is read-only and cannot be edited, and the backend refuses it if attempted anyway.
6. **Given** a save with several failures, **When** it completes, **Then** the screen lists the failed months in calendar order with one reason each, moves focus to the first failed month, and states how many months were saved and how many were not.
7. **Given** a failed save of the whole request (network or server error), **When** it ends, **Then** every typed value is kept, nothing is shown as saved, and the user can retry; a retry never creates a duplicate, and months the first attempt had already stored are reported as unchanged, not as conflicts.
8. **Given** the fixed notice of spec 037 ("Months already finalised in payroll or attendance keep the figure they used."), **When** the screen is shown, **Then** it appears beside Save, once, for the whole screen.

---

### User Story 3 - The Right Permissions, and It Works on a Phone (Priority: P1)

The screen follows the same access rules as the School Working Days grid, and it is comfortable to use at phone width.

**Why this priority**: The figures drive pay (Constitution Principle I). A second way to change them must not become a way around the rules.

**Independent Test**: Sign in as each role and check the menu and the screen; call the save endpoint as a role without the permission and see it refused; open the screen at 375 px width and see no horizontal page scroll.

**Acceptance Scenarios**:

1. **Given** a user with View but neither Create nor Edit, **When** they open the screen, **Then** they see the twelve months and hints read-only, with no Save.
2. **Given** a user with Create and Edit (Admin, Director by default), **When** they open the screen, **Then** every month is editable and Save is offered when something changed.
3. **Given** a user with Create but not Edit (or the reverse), **When** they open the screen, **Then** only the months they may change are editable: empty months need Create, months with a stored figure need Edit.
4. **Given** a user without View, **When** they reach the screen by its address, **Then** they see the not-authorized page, and the menu entry is not shown to them. The backend refuses every request from them regardless of what the screen shows.
5. **Given** a user who is not allowed to see a School (when scoping applies, spec 037 FR-025), **When** they try to pick it or call the endpoint for it, **Then** it is refused as for the grid.
6. **Given** a phone-width window (375 px), **When** the screen is shown, **Then** the twelve months are laid out so that the page has no horizontal scroll, every field and the Save button are reachable and large enough to tap, and the failed-month messages are readable without sideways scrolling.
7. **Given** a keyboard-only user, **When** they use the screen, **Then** they can move through the twelve fields in month order, save, and reach the first failed month, and each field has a label that names its month.

---

### User Story 4 - Fill the Year Faster (Priority: P2)

Two helpers speed up typing: "Use calendar counts" fills the empty months with the hint values for the user to review, and "Same figure for all empty months" fills them with one number the user chooses. Neither saves anything by itself.

**Why this priority**: The screen is useful without them; they cut the typing for the common case where most months are the same or follow the calendar.

**Independent Test**: With a year that has two stored figures, press "Use calendar counts" and see the ten empty months filled with their hints and the two stored months untouched; press Save and see ten new rows.

**Acceptance Scenarios**:

1. **Given** empty months, **When** the user chooses "Use calendar counts", **Then** each empty month is filled with its hint value, months that already have a stored figure or a typed value are not changed, and nothing is saved until the user presses Save.
2. **Given** empty months, **When** the user chooses "Same figure for all empty months" and enters a number, **Then** each empty month for which the number is valid (for example not 30 in February) is filled; months for which it is not valid are left empty and listed.
3. **Given** helper-filled values, **When** the user changes or clears one, **Then** the screen treats it like any typed value.

---

### User Story 5 - Reach the Screen, and Go Back to the Grid (Priority: P2)

The screen is reachable from the School Working Days grid and from the menu, opens on the School and year the user was looking at, and the grid shows the new figures afterwards.

**Why this priority**: A convenience that makes the two screens feel like one feature.

**Independent Test**: In the grid, filter to School A and 2027, choose "Enter a year", and see School A and 2027 selected; save; return to the grid and see the new rows.

**Acceptance Scenarios**:

1. **Given** the grid is filtered to one School and year, **When** the user chooses "Enter a year", **Then** the year screen opens with that School and year selected; with no filter it opens with the School picker empty and the current year.
2. **Given** unsaved changes, **When** the user changes the School or year, or leaves the screen, **Then** they are asked to confirm discarding the changes; nothing is discarded without confirmation.
3. **Given** a successful save, **When** the user returns to the grid, **Then** it shows the new and changed figures without a manual refresh.

---

### Edge Cases

- A School and year with no figures at all and no calendar for the School: the hints come from the default calendar (spec 037 FR-007); every month still shows a hint.
- Two people use the screen for the same School and year at the same time: each month is protected independently; the second person to save a month someone else changed is told for that month only.
- A leap year: February offers up to 29 days and a hint for 29; in other years up to 28.
- The user enters 0: valid, stored as 0 (a month with no working days), never confused with blank.
- A decimal comma or other non-numeric text (for example "25,5" or "abc"): refused for that month with the allowed values named; the other months are unaffected.
- A year outside the allowed window (current year minus 5 to plus 5, spec 037 FR-029): the year cannot be picked; a request for it is refused.
- A School is deactivated: its rows stay editable for historical months, as in spec 037.
- The user's session expires mid-edit: the typed values are not lost on the screen, the user is asked to sign in again, and a repeated Save after signing in does not create duplicates.
- The same Save sent twice (double tap, slow network, or a retry after a dropped connection): the second does nothing extra; a month already stored with the typed figure by the first is not written again and is reported as unchanged (success).
- A month saved here and the same month edited in the grid at the same moment: the same one-writer-wins rule as everywhere else; the loser is told.
- The calendar changes while the screen is open: the hints shown are as of when the screen was loaded; a stored figure is never altered by a calendar change.
- A Manager is later granted access: scoping to their assigned Schools applies, as in spec 037.

## Requirements *(mandatory)*

### Functional Requirements

**The screen**

- **FR-001**: The system MUST provide a screen, reached from MASTER DATA → School Working Days and from the grid's "Enter a year" action, where the user picks one School and one year and sees all twelve months of that year at once.
- **FR-002**: Each month MUST show: its name, its number of days, an entry field, its stored figure if one exists, and its calendar-derived count shown as a hint that is visibly different from a stored figure.
- **FR-003**: The School picker MUST offer only Schools the user may see; the year picker MUST offer only the years allowed by spec 037 FR-029 (current year minus 5 to plus 5), defaulting to the current year.
- **FR-004**: The screen MUST NOT hold figures of its own: every stored figure shown or saved is a record of the spec 037 master, with the same uniqueness (one per School and month), the same range (0 up to the days in that month, in whole or half days, leap years respected) and the same rules for School and year.
- **FR-005**: Months and dates MUST be shown by month name and DD/MM/YYYY, per the application's locale.

**Saving**

- **FR-006**: A single Save MUST write only the months whose entry differs from what was loaded: it creates a figure for a month with none, and updates a month that has one. Unchanged months MUST be left untouched, with no new version and no audit entry.
- **FR-007**: Save MUST be unavailable while nothing has changed, and the screen MUST say so.
- **FR-008**: A blank entry MUST mean "nothing entered": it MUST save nothing and MUST NOT delete a stored figure. Removing a stored figure remains a spec 037 action (grid, Admin by default, with confirmation).
- **FR-009**: Each month MUST be saved independently, so that months that are valid and unchallenged are stored even when other months fail. The system MUST NOT roll back saved months because another month failed.
- **FR-010**: Each month's update MUST use the same optimistic-lock check as a single edit in the grid (spec 037 FR-021): if the stored figure changed since the screen loaded it, and now differs from the typed figure, that month is refused and the current figure and who changed it are returned; if it now equals the typed figure, the month is reported as unchanged and nothing is written.
- **FR-011**: A month created by someone else since the screen loaded MUST be refused as already existing, with its current figure shown, when that figure differs from the typed one; when it equals the typed one the month MUST be reported as unchanged and count as saved; a month deleted by someone else MUST be refused as deleted and MUST NOT be recreated by this save.
- **FR-012**: The result of a Save MUST report, for each of the twelve months: created, updated, unchanged, or not saved with a reason (outside the allowed range or not a half day; year not allowed; changed by someone else; already exists; deleted by someone else; not permitted), and MUST return the current stored figures and versions so the screen can continue without a reload.
- **FR-013**: The screen MUST keep what the user typed in every month that was not saved, MUST list the failed months in calendar order with one reason each, MUST show the stored figure beside a changed-by-someone-else month, and MUST move focus to the first failed month.
- **FR-014**: A save that fails as a whole (network, server or expired session) MUST keep every typed value, MUST NOT show anything as saved, and MUST be safe to retry: repeating the same Save MUST NOT create a duplicate row or a duplicate audit entry, and months already stored with the typed figure MUST be reported as unchanged, not as conflicts.
- **FR-015**: The screen MUST always show the fixed notice of spec 037 FR-028 beside Save, once for the whole screen: "Months already finalised in payroll or attendance keep the figure they used." It MUST NOT query payroll or attendance.
- **FR-016**: A save MUST NOT alter, recompute or lock any attendance mark, leave balance or payroll figure by itself (spec 037 FR-010).

**Helpers**

- **FR-017**: The screen MUST offer "Use calendar counts", which fills every empty, untyped month with its hint value, and "Same figure for all empty months", which fills every empty, untyped month with one number the user enters where that number is valid for the month. Neither MUST save anything, change a stored figure, or overwrite a value the user typed. Months that could not be filled MUST be listed.

**Permissions, scope and audit**

- **FR-018**: The screen and its save MUST use the existing `SCHOOL_WORKING_DAYS` permissions of spec 037 and add no new permission key: View to open the screen and read; Create to enter a figure for a month with none; Edit to change a stored figure. The same rules apply by default as in spec 037 (Admin: View, Create, Edit, Delete; Director: View, Create, Edit; Manager, Teacher, System: none), and they remain editable in Role & Permission Management.
- **FR-019**: A month the user may not change MUST be shown read-only (hidden or disabled actions are not offered); the menu entry and the "Enter a year" action MUST appear only to users with View; a deep link without View MUST show the not-authorized page.
- **FR-020**: The backend MUST enforce, per month, the permission needed (Create for a new figure, Edit for a stored one) independently of the screen, MUST fail closed, and MUST refuse a month the user may not change without affecting the months they may (Principle X).
- **FR-021**: Data scope MUST be as in spec 037 FR-025 and FR-026: organization-wide for Admin and Director; if a Manager is granted access, limited to their assigned Schools; System has no access.
- **FR-022**: Every figure created or updated by a save MUST be recorded exactly as if it had been saved from the grid (spec 037 FR-022): actor, time, School, month, and old and new figure, with the row history updated. A month that is unchanged or refused MUST write nothing.
- **FR-023**: A figure stored here MUST be visible immediately in the School Working Days grid, in its row history, and through the published interface for other modules (spec 037 FR-011), with no difference from one stored in the grid.

**Layout and use**

- **FR-024**: The screen MUST show loading, error-with-retry and loaded states; MUST ask for confirmation before discarding unsaved changes (changing School or year, or leaving the screen); and MUST NOT lose typed values on a failed save.
- **FR-025**: At phone width (375 px) the screen MUST have no horizontal page scroll, with the months laid out so that each field, the hint, any error message and Save are fully reachable and tappable.
- **FR-026**: The screen MUST be usable by keyboard in month order, and each entry field MUST have an accessible name that includes its month (and year).

### Key Entities

- **School Monthly Working Days (spec 037)**: the figure for one School and one calendar month, with its version, and who last changed it and when. This feature reads and writes the same records and adds no new entity.
- **Year Entry View**: what the screen shows for one School and one year: for each of the twelve months, the stored figure and its version if any, and the calendar-derived hint. Not stored.
- **Year Save Result**: what a Save returns: for each month, created, updated, unchanged or not saved with a reason, plus the current stored figures. Not stored.
- **Audit Entry (spec 003)**: one entry per figure created or updated, as for the grid.

## Role & Permission Impact *(mandatory — Constitution Principles II–IV)*

| Role     | Menu (section → item) | Default actions | Data scope |
| -------- | --------------------- | --------------- | ---------- |
| Admin    | MASTER DATA → School Working Days (Enter a year) | View, Create, Edit (Delete is a grid action) | Org-wide |
| Director | MASTER DATA → School Working Days (Enter a year) | View, Create, Edit | Org-wide |
| Manager  | none | none by default; if granted, limited to assigned Schools | Assigned (only if granted) |
| Teacher  | none | none | None |
| System   | none | none (System MUST NOT see School business data) | None |

**New permission keys**: none. The screen uses `SCHOOL_WORKING_DAYS.{VIEW,CREATE,EDIT}` from spec 037. It is a sub-screen of the existing School Working Days menu item, so the navigation model and the Constitution's default role access matrix need no change.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An Admin or Director can enter a full year of figures for one School in under 1 minute from opening the screen (against under 3 minutes by single additions, spec 037 SC-002), and in under 30 seconds when most months are filled with the helpers.
- **SC-002**: When any number of months fail, 100% of the other valid months are still stored, and 100% of failed months are listed by name with a reason and keep the user's typed value.
- **SC-003**: 0 stored figures are silently overwritten: every month changed by someone else since the screen loaded is refused and reported (checked with two simultaneous users).
- **SC-004**: 100% of figures created or updated here have an audit entry (who, when, School, month, old and new figure), the same as from the grid; 0 unchanged or refused months have one.
- **SC-005**: 100% of attempts to store a figure outside 0 to the days in the month, or not a whole or half day, are refused for that month with the reason named.
- **SC-006**: Repeating the same Save never creates a duplicate row or duplicate audit entry, and reports 0 conflicts for months already stored with the typed figure.
- **SC-007**: A user without View never sees the menu entry or the "Enter a year" action and gets the not-authorized page on a deep link; users without Create or Edit never get an editable field for a month they cannot change (checked per role), and the backend refuses it anyway.
- **SC-008**: At 375 px width there is no horizontal page scroll, and all twelve fields and Save can be reached and used without sideways scrolling (checked in a real browser).
- **SC-009**: The screen for one School and year appears in under 2 seconds, and a Save of all twelve months completes in under 3 seconds.

## Assumptions

- **Same data as spec 037.** This feature adds no table and no permission; it is a second way to maintain the existing figures. Spec 037 must be in place (it is merged).
- **Spec number 038:** the next number after 037, as requested.
- **Blank never deletes.** A stored figure can only be removed in the grid (spec 037); the year screen can create and change, not delete. *To confirm.*
- **Hints are advisory.** The calendar-derived count is a hint for the user and is never stored unless the user keeps it in a field and saves (the helper fills fields; it does not save).
- **Partial save, not all-or-nothing** (decided with the requester on 2026-10-16). Months stored before a failure stay stored.
- **One School at a time** (decided with the requester on 2026-10-16). A multi-School grid and import from a file are out of scope.
- **Finalised months:** as in spec 037, the screen shows a fixed notice and never queries payroll or attendance; consumers keep the figure they already used for a finalised month.
- **Mobile apps** do not show this screen.
- **Consumers** (payroll, Manager attendance, attendance rollups) are unaffected: they read the figure through the spec 037 interface and see figures stored here exactly like any other.
- Depends on: spec 037 (the master, rules, permissions, audit, published interface), 005 (Schools), 002 (navigation and permissions), 003 (audit), 008 (calendar fallback for hints). Constitution v2.5.3.
