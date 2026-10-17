# Contract: Screens (Android)

Each screen is reached only from the server's menu (route in brackets). A route the registry does not know is not shown. No screen
is chosen by role name; a button appears only when the server's grants and flags say so. Every screen has loading, empty, error
and "no connection" states with Try again, works in light and dark themes with 48 dp targets and TalkBack labels, and loads its
data from the server each time it opens or is refreshed.

## Visits [`/marketing/calendar`]

- **List**: the month picker of spec 019 (previous, current and next year), a status filter (All, Planned, Completed, Cancelled,
  Missed), and the month's visits grouped "Today", "Later" and "Earlier" (later in date order, earlier newest first). A row shows
  the target's name, the date, the kind in words and the status in words (Missed in its own style), and a "moved" mark when
  `rescheduled`. A "Plan a visit" button when the menu grants Create. An empty month says so and points to Plan a visit.
- **Detail** (inside the screen; Back closes it): target, date, kind, attending colleagues, notes; for a completed visit the outcome and
  follow-up date (with "follow-up date has passed" when the server says so); for a cancelled one the reason; "Moved from" dates; the
  attachments (name, type and size, a thumbnail for images). Buttons, shown when the menu grants Edit and the stored status is
  planned: Complete visit, Reschedule, Cancel visit. Add photo is shown when the menu grants Create and the visit has fewer than 10
  files.
- **Complete visit**: Outcome (required, with "who was met, what was discussed and the next action" as the hint, 1000 characters left),
  Notes (1000), an optional follow-up date, optional photos ("none required"). Send is disabled while the outcome is blank. After the
  server confirms, the photos upload one by one; the visit shows Completed whatever happens to them, and a failed photo is named with
  the server's reason and can be added again from the detail.
- **Plan a visit**: a prospect search (typing filters, 25 at a time), the kind (visit, call, proposal meeting, follow-up), the date,
  optional colleagues, optional notes. Send is disabled without a prospect and a date.
- **Reschedule**: a date chooser; Send is disabled until a date is chosen. **Cancel visit**: a required reason (300 characters left).
  Both ask nothing else; the server's refusals are shown with the entries kept.

## Tasks [`/operations/tasks`]

- **List**: a view chooser (Today, Upcoming, Overdue, Awaiting verification, Done, All) with the counts the server returns, and the
  tasks of the chosen view, 25 at a time with Load more. A row shows the title, the type, the due date, the status in words, "N days
  overdue" and "Escalated" when the server says so. An "Add a task for me" button when the menu grants Create and the server lists
  the caller as an assignee.
- **Detail** (inside the screen; Back closes it): title, type, description, due date, place, linked record, who assigned it, status,
  the overdue reason, the completion note and verification, the follow-up task links, the history (oldest first, with who and when),
  and the evidence (name, type, size, thumbnail for images, "n of 5").
- **Steps**, each shown only when the matching `actions` flag is true: Acknowledge; Start; Give a reason (reason of at least 5
  characters with "N more needed", optional follow-up date); Complete (note of at least 10 characters with "N more needed");
  Add photo (camera or gallery). Verify, send back, edit and cancel are never shown.
- **Add a task for me**: type (the server's list), title (3 to 120 characters), optional description, due date, optional place. Send is
  disabled until type, title and date are given; the task is assigned to the caller.

## Home cards (the Home screen)

- **Visits today**: "N visits today" (planned visits for today), shown when the menu offers `/marketing/calendar`; opens the visits
  screen on the current month.
- **Tasks**: "N overdue, M due today", shown when the menu offers `/operations/tasks`; opens the tasks screen on Overdue.
- Each card loads and fails on its own ("could not be loaded" with a way to try again); a user whose menu offers neither sees neither.

## Words

Statuses: Planned, Completed, Cancelled, Missed; Assigned, Acknowledged, In progress, Completed, Verified, Cancelled. Kinds: Visit, Call,
Proposal meeting, Follow-up. Task types come from the server's labels. Dates read DD/MM/YYYY. A refusal is the server's `reason`; the
app's own texts are only "No connection. Nothing was sent.", the length hints, and the empty and error states.
