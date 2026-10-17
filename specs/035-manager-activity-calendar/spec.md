# Feature Specification: Manager Activity Calendar

**Feature Branch**: `035-manager-activity-calendar`

**Created**: 2026-10-18

**Status**: Draft

**Input**: User description: "035-manager-activity-calendar: Manager and Director read-only calendar of planned versus completed activity across campus drives (016), marketing visits and meetings (023), training (024, not built yet) and tasks (025), per Manager and area, with missed and rescheduled items, built on the planned-activity contract C1."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - The Director Sees What Every Manager Planned and Did (Priority: P1) 🎯 MVP

The Director (or Admin) opens the Manager Activity Calendar for a month and sees, day by day, what the Managers planned: campus drives, school visits, calls and meetings, and tasks due. Each item shows the Manager, the place or School, the kind of activity and where it stands: still planned, done, missed or cancelled. A summary at the top says how many activities were planned, how many were done, how many were missed and how many were moved to another date, so the Director can see in one look whether the field work is happening. The Director can narrow the view to one Manager, one Zone (area), one kind of activity or one status. Nothing on this screen can be changed; to act on an item, the Director opens it in its own screen.

**Why this priority**: The Director's deck asks for "planned versus completed" across all field work. Today the Director has to open the marketing calendar, the drives list and the task list one by one. This is the whole value of the spec.

**Independent Test**: With drives, visits and tasks entered for two Managers in two Zones (some done, one in the past still open, one moved to a new date), open the calendar as the Director for that month and check that every item appears once under the right Manager, Zone and day with the right status, and that the summary counts match the items.

**Acceptance Scenarios**:

1. **Given** Manager A has a school visit on the 5th (done), a campus drive on the 8th (held), and a task due on the 10th (open, today is the 12th), **When** the Director opens the month, **Then** the 5th shows the visit as Done, the 8th the drive as Done, the 10th the task as Missed, and the summary shows 3 planned, 2 done, 1 missed.
2. **Given** a visit was moved from the 14th to the 20th, **When** the Director views the month, **Then** the visit appears on the 20th marked "Rescheduled", and the Director can see it was first planned for the 14th.
3. **Given** the Director chooses one Zone, **When** the calendar refreshes, **Then** only activities of that Zone's Managers or Schools appear and the summary recounts for that Zone.
4. **Given** an item in the calendar, **When** the Director selects it, **Then** a read-only detail shows the Manager, kind, date, place, status and any reschedule history, with a link to the screen that owns it; the calendar offers no way to complete, move or cancel it.
5. **Given** a month with no activity, **When** the Director opens it, **Then** the calendar says there is nothing planned and shows zero counts rather than an error.

---

### User Story 2 - A Manager Sees Their Own Plan Against What Is Done (Priority: P1)

A Manager opens the same calendar and sees only their own activities (and nothing of other Managers): the drives they attend, the visits and meetings they own or attend, and the tasks assigned to them. Each day shows what was planned and what happened, and the summary shows their own planned, done, missed and rescheduled counts for the month. This helps the Manager see what is slipping before the Director does.

**Why this priority**: Managers are accountable for the same numbers the Director sees; the view must be the same shape for them, limited to their own scope.

**Independent Test**: Sign in as a Zone Manager with a mix of activities and as a second Manager; confirm each sees only their own and that the counts differ accordingly.

**Acceptance Scenarios**:

1. **Given** a Zone Manager with 4 activities this month, **When** they open the calendar, **Then** they see exactly those 4, with their own counts.
2. **Given** a visit owned by another Manager of the same Zone, **When** this Manager opens the calendar, **Then** it is not shown.
3. **Given** a visit that this Manager attends but another Manager created, **When** the month is shown, **Then** it appears for this Manager (attendee) as well as for the creator.
4. **Given** a Manager who tries to ask for another Manager's calendar (by choosing a different Manager or Zone), **When** the request is made, **Then** it is refused or returns nothing, never the other Manager's items.

---

### User Story 3 - Missed and Rescheduled Items Are Easy to Find (Priority: P1)

The Director needs to spot slippage quickly. A "Needs attention" list shows, for the chosen period and filters, every missed activity (the date passed and the work was not done) and every rescheduled one, grouped by Manager, with how many days late or how far it was moved. A Manager that keeps moving or missing visits stands out. Choosing an item opens its read-only detail.

**Why this priority**: The deck asks for "missed and rescheduled" specifically; without a focused list the Director has to scan the whole month.

**Independent Test**: Create three missed items for Manager A, one rescheduled item for Manager B and one clean month for Manager C; the list shows A with 3 missed, B with 1 rescheduled and does not list C.

**Acceptance Scenarios**:

1. **Given** items missed in the period, **When** the Director opens "Needs attention", **Then** each is listed with its Manager, Zone, kind, original date and days late, oldest first.
2. **Given** an item rescheduled twice, **When** it is listed, **Then** it shows both moves and counts as one rescheduled item, not two.
3. **Given** a missed item is later completed or cancelled in its own screen, **When** the list is next opened, **Then** it no longer counts as missed (completed late is shown as Done, with a "done late" mark).
4. **Given** no missed or rescheduled items, **When** the list opens, **Then** it says so.

---

### User Story 4 - Choose the Period, the Person and the Kind (Priority: P2)

The user can switch between a month view (a grid by day) and a list view, move to the previous or next month, jump to today, and filter by Manager (Director and Admin only), Zone, kind of activity (Campus drive, School visit, Call, Meeting, Task, Training) and status (Planned, Done, Missed, Cancelled). Filters and the chosen month survive a refresh of the screen in the same session. A day with many items shows the first few and "more" that opens the day's full list. When two items of one Manager fall on the same day the calendar shows both and does not call it an error.

**Why this priority**: Makes the view usable at the real volume of work (many Managers, many items a day), but the MVP works with the month and one filter.

**Independent Test**: Load a month with 40 items for 6 Managers; apply Manager, Zone, kind and status filters in turn and check counts and rows each time.

**Acceptance Scenarios**:

1. **Given** the filter "Task, Missed", **When** applied, **Then** only missed tasks remain in both the grid and the summary.
2. **Given** a day with 9 items, **When** shown in the grid, **Then** a few appear with "6 more" that opens the full day list.
3. **Given** the Director clears all filters, **When** the calendar refreshes, **Then** all Managers' items for the month appear again.

---

### User Story 5 - Training Appears When It Exists (Priority: P3)

Training sessions (spec 024) are meant to show on the same calendar, assigned Managers with expected versus attended. Spec 024 is not built yet. Until it is, the calendar simply has no training items, the kind filter shows "Training: not available yet" instead of an empty choice that looks like "no training happened", and nothing else breaks. When 024 provides its planned activities through the same contract, training items appear with no change to how the calendar works.

**Why this priority**: Keeps the view complete for the future without blocking the first release.

**Independent Test**: With no training source available, the calendar loads normally with the other three kinds and a clear note that training is not yet included; adding a stand-in training source with one session shows that session on its date.

**Acceptance Scenarios**:

1. **Given** spec 024 is not built, **When** the calendar opens, **Then** it loads, shows drives, visits and tasks, and a short note says training is not included yet.
2. **Given** a training source exists later, **When** the calendar opens, **Then** sessions appear as the kind Training with their assigned Managers, without another change to this screen.
3. **Given** one source (for example tasks) cannot be read, **When** the calendar opens, **Then** the other kinds still show and a visible note says which kind could not be loaded and that the counts are partial.

---

### Edge Cases

- **Today and the day boundary**: an item dated today and still open is "Planned", not "Missed"; it becomes Missed from the next day (the same rule the marketing calendar already uses). A day is judged in the organization's local date.
- **Multi-day drives**: a campus drive of three dates shows on each of its dates and counts as one activity per date in the grid but as one drive in the summary counts. The drive is Done when it is held; a drive cancelled before it is held shows as Cancelled on each date.
- **Cancelled items**: shown in the calendar (struck through) so a gap is explained, counted as Cancelled, and excluded from planned, done and missed counts.
- **Tasks have no planned date other than their due date**: a task due on the 10th that is completed on the 12th is Done late; a task still open after its due date is Missed even if its owner has recorded a reason for being late. A task whose due date was edited is shown on its new date; whether it counts as Rescheduled depends on whether its date change is recorded (see Assumptions).
- **Shared activities**: a visit with two attendee Managers appears once under each in a per-Manager view but is counted once in organization-wide totals.
- **Unowned or unplaced items**: an item with no School or place shows "No place recorded"; an item whose owner has no Zone appears under "No Zone" for the Director and Admin only.
- **Deactivated Manager**: their past items remain visible for the periods they were active; open future items still show so the Director can reassign them in their own screens.
- **A Zone with more than one Manager**: the Zone filter shows all of its Managers; a Manager sees their own items only, never the Zone-mates' (a deliberate choice, see Assumptions).
- **Large range**: the calendar covers one month at a time; a longer range is refused with a clear message in the list view (the Needs attention list may cover up to a quarter).
- **Stale data**: the calendar reflects the owning screens at the time it is loaded; a refresh shows later changes. There is no editing here, so there are no edit conflicts.
- **Time zone**: all dates are plain calendar dates with no time of day; the calendar never moves an item across midnight.

## Requirements *(mandatory)*

### Functional Requirements

**The calendar**

- **FR-001**: The system MUST provide a read-only Manager Activity Calendar showing, for a chosen month, every planned activity of the kinds Campus drive, School visit, Call, Meeting and Task, and Training when it exists, on its date.
- **FR-002**: Each item MUST show its kind, date, owner Manager, place or School, status, a link to the owning screen and, for a rescheduled item, the earlier date.
- **FR-003**: Each item MUST be shown with one of four outcome statuses: Planned (open, date today or later), Done (completed, held or verified), Missed (open and the date has passed), and Cancelled. A Done item completed after its date MUST carry a "done late" mark.
- **FR-004**: The calendar MUST show a summary for the current filters: activities planned, done, missed, cancelled and rescheduled, in total and by kind. "Planned" in the summary means all non-cancelled activities dated in the period, so planned equals done plus still open plus missed.
- **FR-005**: The calendar MUST read its items only through the planned-activity contract (C1) of the owning specs and MUST NOT keep its own copy of activities or change any of them.
- **FR-006**: The calendar MUST provide both a month grid and a list of the same items, with the same filters.

**Missed and rescheduled**

- **FR-007**: The system MUST decide Missed from the item's date and its status, using one rule for all kinds: not done, not cancelled, and dated before today.
- **FR-008**: The system MUST show an item as Rescheduled when its owning module records that it was moved to another date, showing the original and current dates; an item moved more than once counts once.
- **FR-009**: The system MUST provide a "Needs attention" list of Missed and Rescheduled items, grouped by Manager, with days late or days moved, oldest first.
- **FR-010**: A Missed item that is later completed or cancelled MUST stop counting as missed on the next load.

**Filters and scope**

- **FR-011**: Users MUST be able to filter by Manager (Director and Admin only), Zone, kind and status; filters combine, and the summary and the lists always follow them.
- **FR-012**: A Manager MUST see only their own activities: those they own, attend (visits, drives) or are assigned (tasks). Their Manager and Zone filters MUST NOT widen what they see.
- **FR-013**: The Director and Admin MUST see all Managers' activities, org-wide.
- **FR-014**: The calendar MUST determine an item's Zone (area) from the item's own School or prospect Zone when it has one, otherwise from the Manager's current Zone assignment; items with neither appear under "No Zone".
- **FR-015**: Teachers and System accounts MUST have no access to the calendar.

**Resilience**

- **FR-016**: While training (spec 024) has no planned activities to offer, the calendar MUST work with the other kinds and say that training is not included yet.
- **FR-017**: If one source of activities cannot be read, the calendar MUST still show the others, name the missing kind, and mark the counts as partial.
- **FR-018**: Opening an empty month MUST show zero counts and an empty-state message.

**Audit and privacy**

- **FR-019**: The calendar MUST NOT write any business data; opening it needs no audit entry beyond the platform's normal access logging.
- **FR-020**: The calendar MUST show only what the user could already see in each owning screen; it MUST NOT show task evidence, notes, attachments, outcome text or incentive data. The detail is limited to the fields in FR-002.
- **FR-021**: Selecting an item MUST open a read-only detail and a link to the owning screen, which the user can follow only if their role may open it there.

### Key Entities *(include if feature involves data)*

- **Planned Activity (as read)**: an item from C1 with its kind, owning record, owner Manager, date, place and status. Not stored here.
- **Calendar Item**: a Planned Activity with its derived outcome status (Planned, Done, Missed, Cancelled), "done late" mark, Zone and, where recorded, its earlier dates.
- **Calendar Summary**: counts of planned, done, missed, cancelled and rescheduled for the chosen filters, by kind.
- **Needs-attention Entry**: a Missed or Rescheduled Calendar Item with days late or moved, grouped by Manager.
- **Activity Source**: one of campus drives (016), marketing visits and meetings (023), tasks (025), training (024, future), each with whether it was read.

No new stored data is expected: this is a read model over the existing sources.

## Role & Permission Impact *(mandatory — Constitution Principles II–IV)*

| Role     | Menu (section → item) | Default actions | Data scope |
| -------- | --------------------- | --------------- | ---------- |
| Admin    | OPERATIONS → Activity Calendar | View | Org-wide |
| Director | OPERATIONS → Activity Calendar | View | Org-wide |
| Manager (Zone Manager) | MY WORK → My Activity Calendar (same screen, own items) | View | Own activities only (owned, attended or assigned), never Zone-mates' |
| Teacher  | none | none | None |
| System   | none (System MUST NOT see business data) | none | None |

**New permission key**: `ACTIVITY_CALENDAR` (View only; no Create, Edit, Delete, Approve or Process action exists). Seeded as above; Teacher and System are not eligible. Only Admin, Director and System edit the matrix. The Manager's own-scope rule is enforced by data scope, not by the key. The constitution's Default role access matrix gets an Activity Calendar row when this spec merges. Following an item to its owning screen is governed by that screen's permission (`MARKETING`, `RECRUITMENT`, `TASKS`), so a user without it sees the read-only detail but no link.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A Director can see every Manager's planned versus completed activity for a month in one screen, with no need to open the drives, marketing or task screens.
- **SC-002**: For a prepared month with known items (done, open, missed, cancelled, rescheduled, a multi-day drive, a visit with two attendees, an unplaced item), the shown statuses and summary counts match the hand-counted figures exactly, with no item counted twice.
- **SC-003**: A Director can find every missed item for a Zone from "Needs attention" in under 30 seconds, without scanning the grid.
- **SC-004**: A Manager never sees another Manager's items, proved by a test with two Managers in one Zone and two in different Zones across every filter combination.
- **SC-005**: Opening a month of up to 500 activities across 20 Managers shows results in under 3 seconds on a normal office connection.
- **SC-006**: With training not built, the calendar loads and states that training is not included; with one source failing, the other kinds still load and the counts are marked partial.
- **SC-007**: A change made in an owning screen (complete a visit, move a drive date, finish a task) shows in the calendar on the next load, with no separate update step.
- **SC-008**: No action on the calendar changes any activity; a test proves that no write is possible from it.

## Assumptions

These defaults were chosen without asking the user; each point marked is **to confirm in /speckit-clarify**.

- **Where the real code differs from the documents** (the code was followed):
  - The roadmap says each source exposes planned activities with "id, kind, owner Manager, date, place or School, status". The real contract C1 is the record `PlannedActivity(kind, id, ownerUserId, date, place, status)`; it has no Zone or area, no completed-on date and no reschedule history. Zone must be derived (FR-014).
  - The real marketing feed returns **only activities still in the PLANNED status** (completed and cancelled visits are left out); the owner is the **creator**, and attendees are matched only when a single user is asked for. "Missed" and "Rescheduled" exist only in the marketing calendar's own screen data, not in C1.
  - The real campus drive feed returns every status (planned, held, cancelled), one entry per drive date, with the Manager who scheduled it as owner (interviewers are matched only when a user is asked for).
  - The real task feed returns every non-cancelled task by its due date with its status (assigned, acknowledged, in progress, completed, verified); it has no moved-date record. "Overdue" is derived for tasks.
  - Therefore **planned versus completed cannot be built on C1 as it stands**: the marketing feed must also return completed and cancelled visits and say whether a visit was rescheduled and from which date; the task feed should say when a task was completed and whether its due date was moved; the drive feed should state any date change if one is recorded. These are required amendments to 023, 025 and 016 for the plan (**to confirm in /speckit-clarify**); this read-only spec cannot work around them.
  - The attendance helper that lists a day's activities for a Field day (spec 032) is internal to attendance; 035 reads the same three feeds through their public interfaces and does not reuse it.
  - Training (024) has no code or contract yet. The web Marketing Calendar already shows the user's drives beside visits with Missed and Rescheduled marks and a "mine" toggle; this spec generalizes that to all four kinds and to all Managers.
- **Statuses**: Done means a visit or meeting completed, a drive held, a task completed or verified. A completed-but-not-yet-verified task counts as Done (verification stays in the tasks screen). To confirm in /speckit-clarify.
- **Task rescheduling**: a task counts as Rescheduled only if its due-date change is recorded by spec 025 (an amendment as above); otherwise tasks never show as rescheduled. To confirm in /speckit-clarify.
- **Zone (area)**: an item with a School or prospect uses that Zone first; otherwise the owner Manager's current Zone assignment (a Manager may have several). To confirm in /speckit-clarify.
- **Attendee visibility**: a visit appears for every attendee Manager as well as the creator; totals count it once. To confirm in /speckit-clarify.
- **Scope of a Zone Manager**: strictly own items; a Zone Manager does not see Zone-mates' items, even though specs 023 and 025 let a Zone's Managers see prospects. To confirm in /speckit-clarify.
- **Mobile**: the first release is the web screen. Managers already see visits and tasks on the phone (spec 034), unchanged here; a Director mobile calendar is a later spec. To confirm in /speckit-clarify.
- **Menu**: Admin and Director under OPERATIONS, Manager under MY WORK; the Director dashboard (spec 028) may link here later, but no dashboard tile is part of this spec.
- **Not included**: travel claims (015), field-day linking (032, already built) and the Director dashboard (028) read the same contract but are separate specs; export to file, reminders and any editing are not part of this spec.
- **Time**: one organization time zone; dates only. The week starts on Monday.
- **Volume**: tens of Managers and some hundreds of activities a month, so no paging is needed within a month.
