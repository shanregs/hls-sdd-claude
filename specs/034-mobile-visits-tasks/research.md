# Research: Mobile Visits and Tasks

Decisions for [plan.md](plan.md). Facts about the server were read from the 023 and 025 code and contracts
(`specs/023-school-marketing-mou/contracts/marketing-api.md`, `specs/025-manager-tasks/contracts/*`, `ActivityService`,
`AttachmentService`, `ActivityController`, `TaskController`, `TaskQueries`, `TaskService`, `NavigationCatalog`) and from the
running local server as the demo Manager on 2026-10-18; about the app from `mobile/src` after spec 033.

## 1. A visit is a marketing activity; its buttons follow the grants and the stored status

**Decision**: the visits screen calls `GET /api/v1/marketing/activities?from=&to=&mine=true` and the endpoints under
`/marketing/activities/{id}`. A visit's DTO has two statuses: `status` (stored: `PLANNED`, `COMPLETED`, `CANCELLED`) and
`effectiveStatus` (adds the derived `MISSED`). The app **shows** `effectiveStatus` and **offers** Complete, Reschedule and Cancel
only when the menu item grants Edit and the stored `status` is `PLANNED` (the server refuses the three on anything else,
`requirePlanned`); a Missed visit is still stored as `PLANNED`, so it can still be completed. Plan and photo upload need Create.
Complete, reschedule and cancel send the DTO's `version`; a stale one is a 409 whose reason is shown, after which the visit is
reloaded.

**Rationale**: unlike a task, a visit carries no `allowedActions`. Using the stored status is the same rule the server applies and
needs no clock or date arithmetic in the app.

**Alternatives considered**: hiding the buttons on a Missed visit (rejected: the server allows completing it, and a Manager often
logs a visit days later); asking the server to add action flags (rejected: 023 stays unchanged).

## 2. A task's buttons come from its `actions` object

**Decision**: `GET /api/v1/tasks/{id}` returns `actions {acknowledge, start, complete, overdueReason, addEvidence, verify, sendBack,
edit, cancel}` (booleans). The app shows Acknowledge, Start, Give a reason, Complete and Add photo exactly when the matching flag is
true, and never shows verify, send back, edit or cancel. The flags also encode the 5-photo limit (`addEvidence` is false at 5) and
the overdue rule (`complete` is false until an overdue reason is given). The menu's Edit grant is not checked separately for
tasks: the flags already say whether this caller may act, and the server refuses regardless.

**Rationale**: FR-014 and SC-006; verified against the running server (an overdue task with no reason showed acknowledge and
overdueReason true, complete false).

## 3. A visit is planned against a prospect; colleagues are optional

**Decision**: the Plan a visit form picks one **prospect** from `GET /api/v1/marketing/prospects?query=&page=&size=` (a typed search,
25 at a time, as the web's dialog does) and sends `prospectId`. A won prospect is the School (`won: true`, `schoolId` set), so "a
School or a prospect" is one list. `schoolId` is not used. Colleagues are optional: when none are chosen the server records the
Manager as the attendee; a chooser lists `GET /api/v1/recruitment/interviewers` (the staff list the web uses, 200 for a Manager).

**Rationale**: the web plans only against prospects; the prospect list is already scoped to the Manager's Zones; this avoids a
second list and a School endpoint.

**Alternatives considered**: a separate School list (rejected: the server's School check is the same as for the prospect and the
list adds nothing the prospect list lacks); no colleague chooser (rejected: FR-006 asks for it, and it is a small control).

## 4. The list is a month, grouped by date, filtered on the phone

**Decision**: the list loads one month with `from` and `to` set to its first and last day and `mine=true`, opening on the current
month and reaching the previous, current and next year with the month picker of spec 019 (`kind="leave"`, the same range). The
server returns every visit of the range at once, so a month keeps the response small. Grouping (today first, then later, then
earlier) uses only the visit's date against today's date in the business time zone, taken from the server-corrected clock; the
status shown, and the Missed label, are the server's. The status filter hides rows on the phone by `effectiveStatus`; the server
has no status parameter. The response's `drives` (campus drives from spec 016) are not shown: the spec covers visits.

**Rationale**: matches the clarified range, and the grouping is display order, not a business rule (the same use of the clock as the
default month of spec 019).

## 5. Completing a visit: one outcome, then photos

**Decision**: Complete visit sends `{outcome, notes?, followUpOn?, version}` to `POST /marketing/activities/{id}/complete`. The app
checks only that the outcome is not empty (trimmed) and limits outcome and notes to 1000 characters; the follow-up date is chosen
with the date chooser of spec 020 and must not precede the visit (the server's rule, shown from its reason). The photos picked are
uploaded one at a time to `POST /marketing/activities/{id}/attachments` (multipart `file`) **after** the completion succeeded;
a refused or failed photo is reported by name and the visit stays Completed. The follow-up task is created by the server; the
tasks list picks it up on its next load.

**Rationale**: spec 023's `CompleteRequest` has one `outcome` text ("who was met, what was discussed and the next action"), so the
input's separate fields do not exist; ordering completion before photos is the same rule as spec 033's check-in and photo.

## 6. Task steps and evidence

**Decision**: calls are `POST /tasks/{id}/acknowledge` and `/start` (no body), `/overdue-reason` `{reason, followUpOn?}`,
`/complete` `{note}` and `/evidence` (multipart `file`). The app checks the server's length rules because they need no data or
clock: an overdue reason of at least 5 characters, a completion note of at least 10, with "N more characters needed" until met;
everything else is the server's. Each call answers with the whole updated task, which replaces the detail on screen only after the
2xx. Evidence is capped at 5: the count shown is the number of evidence files the server returned; the add control follows
`actions.addEvidence`.

## 7. Adding a task for oneself is offered only when the server lists the caller as an assignee

**Decision**: Add a task is shown when the task menu item grants Create and `GET /tasks/assignees` contains the caller's own user id
(a Manager's list is only themselves); the request then sets `assigneeUserId` to that id. For a Director the list is every Manager
and the id is typically absent, so the control is hidden: no role name is read and no one can assign a task to someone else from the
phone. Body: `{type, title, description?, assigneeUserId, dueOn, place?}`; types from `GET /tasks/types`; title 3 to 120
characters; a due date in the past is the server's refusal.

**Alternatives considered**: showing Add task on the Create grant alone (rejected: a Director has the grant but the server would
refuse a task assigned to a non-Manager, and the form would assign to someone the spec excludes).

## 8. Home counts

**Decision**: the Home card for visits counts the visits returned by `GET /marketing/activities?from=<today>&to=<today>&mine=true`
whose `effectiveStatus` is `PLANNED`; the card for tasks shows `overdue` and `today` from `GET /tasks/summary`. Each card loads and
fails on its own, appears only when the menu offers its screen (route `/marketing/calendar` or `/operations/tasks`), and opens its
list: the visits screen on the current month, the tasks screen on the Overdue view (a route state, like the leave filter of spec
020).

## 9. Shared pieces and what changes in them

**Decision**: reuse as they are: `PhotoPicker` and `PickedPhoto`, `useAction`, `fetchBinary`, `upload`, `toBase64` (033), `MonthPicker`,
`monthRange` (019), `DateChooser`, `ReasonDialog` (020) and the inner-back registry of the shell. Add `media/PhotoSlots` (several photos
with a limit) and `media/AttachmentList` (name, type, size; a thumbnail from downloaded bytes for images, name only otherwise, since
documents can be added on the web). Generalise `useManagerActions` into `access/useMenuActions(route)`; the Manager hook delegates, so
nothing changes for spec 033. Add `businessToday()` to the server clock. Add `visits` and `tasks` to the screen registry, and
`taskView` to the shell's route state.

**Rationale**: the shapes already exist and are tested; copying them would duplicate the photo and permission rules.

## 10. Detail screens live inside their list screen

**Decision**: a visit or task opens as a detail inside the same screen (state, not a route), and the Android back button closes the
detail first through `useInnerBack`, as Leave Management does. The list keeps its month, filter and view while the detail is open.

## 11. Not in this spec

Opening a visit or task from a notification link (the server's link `/operations/tasks/{id}` shows full text today) is left for a
later change; so are documents as attachments, offline capture, campus drives on the calendar, and editing or removing a visit's
attachments (Admin and Director on the web).

## 12. Testing

**Decision**: extend the in-memory fake server with the marketing activity, prospect, colleague, attachment and file routes and the
task routes, with fixtures that copy the real shapes (`ActivityDto`, task `Row`/`Detail`/`Actions`/`Summary`). Contract tests assert
request shapes (bodies, version, multipart field), that completion precedes photo upload, that a failed photo never undoes its
action, and that no step is shown before a 2xx. A role-name scan and the accessibility test cover the new screens and cards.
