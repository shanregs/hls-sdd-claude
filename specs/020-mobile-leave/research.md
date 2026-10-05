# Research: Mobile Leave

Decisions taken before design, checked against the real code of spec 009 (`backend/.../leave/`,
`MyLeaveController`, `LeaveManagementController`, `LeaveRequestService`, `LeaveDecisionService`,
`LeaveViewFactory`, the navigation catalog), its contract (`specs/009-leave/contracts/leave-api.md`), the web
`PendingLeaveWidget`, and the app after specs 018 and 019 (`mobile/`).

## 1. No server changes

- **Decision**: app-only. Endpoints used, all from spec 009 and already authorised for the roles:
  Teacher `GET /api/v1/me/leave/types`, `POST /api/v1/me/leave/preview`, `POST /api/v1/me/leave`,
  `GET /api/v1/me/leave?status=&page=&size=`, `POST /api/v1/me/leave/{id}/cancel`;
  supervisor `GET /api/v1/leave?status=&page=&size=`, `GET /api/v1/leave/{id}`,
  `POST /api/v1/leave/{id}/approve|reject|revoke`.
- **Rationale**: the server owns every rule (limits, overlap, locks, scope, atomic approval, audit).
- **Alternatives considered**: a mobile-specific summary endpoint; rejected, as in spec 019.

## 2. The real shapes (they differ from the contract document in places)

- **Finding**: the code, not the contract document, is the truth:
  - A request view carries `id, teacherId, teacherName, schoolId, schoolName, leaveType, firstDate,
    lastDate, halfDayStart, halfDayEnd, workingDays, reason, status (PENDING|APPROVED|REJECTED|CANCELLED),
    decidedByName, decidedAt, decisionNote, cancelledBy ("TEACHER" or other), createdAt, version,
    allowedActions`. `allowedActions` is `["CANCEL"]` for the Teacher on a Pending request, or an Approved
    one whose first day is after the business date; for supervisors `["APPROVE","REJECT"]` when Pending and
    `["REVOKE"]` when Approved; otherwise empty.
  - The Teacher list is a page `{content, page, size, totalElements}`; no status means all statuses, newest
    first. Page size is capped at 100.
  - The supervisor list is `{content, page, size, totalElements, pendingCount}`; **no status means Pending
    only**, so there is no "All" on Leave Management. Statuses are upper case.
  - The supervisor detail is `{request, days, problems}`: `days` are `{date, value}` for the days approving
    would mark; `problems` (strings such as `days set by a supervisor: 2026-01-15` or `month locked:
    2026-01`) appear only for a Pending request.
  - Errors use `{ "reason": "..." }`: 400 invalid input, 404 not found (also out of scope), 409 conflict.
- **Decision**: `mobile/src/api/leaveApi.ts` types follow the code; the contract file of this feature
  records them.

## 3. The preview lists only counted days

- **Finding**: `POST /me/leave/preview` returns `{workingDays, days:[{date, value}], problems:[text]}`. `days`
  holds only the counted working days; it has no `kind`, so it does not say why another date in the range is
  not counted. (The 009 contract document shows a `kind`; the code does not.)
- **Decision**: the app shows the server's working-day total and counted days as given. For the other dates of
  the range it shows "Not counted", and says "Weekly off" or "Holiday" only when the Holiday Calendar of spec
  019 (`getCalendar`, `holidayModel`) shows that date as such. Counts are never recomputed in the app, and a
  date the app cannot explain simply reads "Not counted" (for example before the placement).
- **Rationale**: it keeps the spec's goal (the Teacher understands the count) without changing the server.
- **Alternatives considered**: ask for a `kind` on the preview (a server change, out of scope for an app-only
  spec); show only the total (loses the explanation).

## 4. The reason is required by the server (clarification Q1 corrected 2026-10-05)

- **Finding**: `evaluate` adds the problem "Give a reason." (invalid input) when the reason is empty, so
  preview lists it and submit answers 400. Clarification Q1 (2026-10-05) chose "optional", on the belief that
  spec 009 only made a reason mandatory for Reject and Revoke.
- **Decision (applied, spec corrected 2026-10-05)**: keep spec 009 unchanged and make the reason **required in the app**: Submit stays
  disabled and the field is marked required until text is entered. This is a one-line spec correction
  (FR-001, scenario 1) and removes a certain refusal. If the user prefers the spec as clarified, the app
  shows "Give a reason." from the preview, which also works; tasks should wait for this choice.
- **Rationale**: the spec says the app must not enforce anything the server does not; here the server does
  enforce it, so showing it up front is the same rule, not a new one. It also fits Q4, since "a missing
  field" is already checked in the app.

## 5. Choosing dates without a new package

- **Decision**: an in-app **date chooser** (a sheet with the month picker of spec 019 and the month's days as
  a list or grid) rather than a native date-picker package. The allowed months are the previous, current and
  next year, from a new `leave` range in `monthRange.ts` using the server clock helper.
- **Rationale**: no new native module means no rebuild or Expo config change; the pieces and their
  accessibility tests already exist from spec 019; the 30-day look-back is left to the server's preview.
- **Alternatives considered**: `@react-native-community/datetimepicker` (native, needs a rebuild, and its look
  differs between Android versions); typed text dates (error-prone on a phone).

## 6. Plain wording for the leave refusals

- **Finding**: the server texts include: "Give a reason."; "Choose a leave type."; "Choose the first and last
  date."; "The last date cannot be before the first date."; "A request can cover at most 90 days. Split it
  into two."; "Leave can start at most 30 days in the past. Ask your Manager to record older days."; "You are
  not placed at a School on these dates, so you cannot apply for leave."; "None of these dates is a working
  day for you."; "A single working day can be a half day at the start or at the end, not both."; "These
  dates overlap your pending|approved request from X to Y."; "Attendance is locked for a month in this range
  (month locked: YYYY-MM)."; on cancel: "This leave has already started. Ask your Manager to revoke it.",
  "This request was already cancelled.", "A rejected request cannot be cancelled."; on decisions: "This
  request was cancelled [by the Teacher].", "...already approved.", "...already rejected.", "Only a pending
  request can be decided.", "Only an approved request can be revoked.", "A reason is required.", "The note
  can be at most 500 characters.", "This request now overlaps another live leave request of the Teacher.",
  "days set by a supervisor: ...", "month locked: ...", "Leave cannot be removed: month locked: ...", and
  "This record was changed by someone else. Reload and try again." (stale version).
- **Decision**: a small `leaveMessages.ts` (like `refusalMessages.ts` of spec 019) rewords the few that are
  terse (`days set by a supervisor: ...`, `month locked: ...`, stale version) and shows every other server
  text as given. Tasks must re-count the real reasons from the code when writing the refusal tests.

## 7. The Pending count and the Home widget

- **Finding**: the web widget calls `GET /api/v1/leave?status=PENDING&size=1` and reads `pendingCount`.
- **Decision**: the app does the same, only when the user's menu offers `/operations/leave`; a failure shows
  "could not be loaded" in the widget alone. Tapping it opens Leave Management with Pending selected. The
  count is loaded when Home opens and after returning from a decision, not on a timer.
- **Rationale**: one existing call, no new endpoint, and the menu (not a role name) decides who sees it.

## 8. Confirmations and reasons

- **Decision**: Cancel, Approve, Reject and Revoke each open a confirmation dialog (`ReasonDialog`): Approve
  with an optional note, Reject and Revoke with a required reason (up to 500 characters, with a remaining
  counter), Cancel with no text. The dialog sends only on confirm, disables itself while sending, and keeps
  its text if the server refuses.
- **Rationale**: spec clarification Q2 puts every decision on the detail screen; one dialog component serves
  all four actions.

## 9. Navigation inside Leave Management and after submit

- **Decision**: Leave Management shows the list, and a row opens the detail inside the same screen, using the
  `useInnerBack` helper of spec 019 so Android Back returns to the list with its filter kept. After a
  successful submit the shell opens My Leave History (clarification Q3) and the history screen shows a short
  "Request submitted" message and reloads from the server, so the new request appears at the top.
- **Rationale**: no new navigation library; the shell already switches screens by route.

## 10. Scope and testing approach

- **Decision**: tests copy the real shapes (research §2) in `leaveFixtures.ts`; the fake server serves the
  routes and applies submits, cancels and decisions to its state so a reload shows the result. Scope is
  tested with two Managers' Teachers where the server answers for the first Manager only; a 404 detail shows
  "Not found" with no data. Allowed-action tests prove a button shows exactly when `allowedActions` lists it,
  whatever the dates, using a wrong phone clock.
