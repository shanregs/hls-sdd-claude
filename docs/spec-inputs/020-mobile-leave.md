# 020 Mobile Leave: `/speckit-specify` input

2026-10-05. Roadmap row 020. Depends on 009 (leave, server side) and 018 (Android app foundation);
019 (mobile attendance) supplies the shared month, date and refusal pieces. It adds screens to the
Android app; it adds no new server rules.

## Feature description (paste as the argument to `/speckit-specify`)

020-mobile-leave: Leave screens for the HLS Android app, built on the app shell, sign-in and
server-driven menus of spec 018, the shared attendance screens of spec 019 and the leave rules and APIs
of spec 009, which stay unchanged.

1. **Teacher: Apply Leave.** The Teacher chooses a leave type (the server's active list), a first and last
   date, an optional half day at the start or the end, and a reason. Before submitting, the app shows the
   server's preview: how many working days the request covers, which days are weekly offs, holidays or
   before the placement, and any problem. Submitting creates a Pending request. Refusals from the server
   (overlap with another request, more than 30 days back, locked month, longer than 90 days, not placed,
   no working day) are shown in plain language and the entered request is kept.
2. **Teacher: My Leave History.** The Teacher's own requests, newest first, filterable by status, each with
   its dates, working days, status, who decided it and the note or reason. The Teacher can cancel a request
   when the server offers Cancel (a Pending one, or an Approved one that has not started).
3. **Manager and Director: Leave Management.** The requests in the user's scope (a Manager: assigned
   Teachers only; a Director: organization-wide), Pending first with a count of Pending requests, a filter
   by status, and a detail view showing the days the request would mark. The user can approve (with an
   optional note), reject (reason required) or revoke an Approved request (reason required), exactly when
   the server offers that action. Conflicts (days set by a supervisor, locked month, already decided,
   changed meanwhile) are shown in plain language and the screen reloads the request.
4. **Home.** The Pending-request count appears on the Manager and Director Home as a tap-through widget,
   replacing the "coming soon" card for that section.
5. The menu entries appear only because the server's access model offers them (spec 018): Apply Leave
   and My Leave History for Teachers, Leave Management for Managers and Directors. Nothing is chosen by
   role name in the app, and what each button may do comes from the request's `allowedActions`.
6. Every call carries the device location or its reason, as in spec 018, and every change is audited by
   the server as in spec 009. Approved leave appears in My Attendance (spec 019) with no extra work.

Out of scope: attachments such as medical certificates, leave balances or entitlements (not in spec 009),
push notifications (spec 010 follow-on), offline capture, Admin and System use (web only), editing a
submitted request (cancel and apply again), CSV or PDF export, and iOS.

## Points for `/speckit-clarify`

- Does the Teacher need to see a calendar while choosing dates (with holidays and weekly offs marked), or
  are two date fields and the server preview enough?
- On Leave Management, should Approve and Reject ask for confirmation, or act on one tap with the note or
  reason entered first?
- Should the Manager be able to filter by Teacher or School on the phone, or only by status and month?
- When the Pending count changes while the screen is open, is it refreshed only on open and pull-to-refresh?
- Is the Home widget a count only, or also the three oldest Pending requests?
