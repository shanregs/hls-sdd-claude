# Contract: Screens (Android)

Each screen is reached only from the server's menu (route in brackets). A route the registry does not know is not shown. No
screen is chosen by role name; a button appears only when the menu item's `actions` grant it and the server's answer allows
it. Every screen has loading, empty and error states, a "no connection" state with Try again, and works in light and dark
themes with 48 dp targets and TalkBack labels.

## Home card (the Home screen, when the menu offers `/manager-attendance/me`)

Shows today's state from `GET /today`:

- **Not a working day** (weekly off or holiday) or **on approved full-day leave**: a one-line note, no Check in.
- **Not checked in** (working day): "Not checked in" with the cut-off help text and a Check in button that opens the
  Check-in screen.
- **Checked in**: "Checked in at 09:42, Field" (or Office), a Late mark if the server says so, the linked activities or
  purpose, the photo thumbnail if there is one, and a Check out button.
- **Checked out**: "Checked out at 18:05".
- **Plan**: today's planned activities, each showing kind, place and status; tapping one opens its details (there is no
  record screen for any kind yet, spec 034).
- **Flags**: each date in `missingCheckOutDates` is listed as "No check-out on 03/11/2026" with Complete check-out.

## Check-in [`/manager-attendance/me`]

1. Day type: Office or Field (radio), preselected Office.
2. Field: the planned activities as checkable rows (kind, place, status, in the server's order) and a purpose text field. At
   least one activity or a purpose is needed; the Check in button explains which if neither is given.
3. Photo (optional): Take a photo / Choose from gallery / Remove; after picking, a thumbnail and its file name, type and size.
4. Check in: reads the position, sends the request, then uploads the photo if one was picked. Result lines: "Checked in at
   <time from the server>", or the server's reason, or the location message. The form keeps every entry on a failure.
5. After checking in, the same screen shows today's status with Check out, Add photo (while the day has none), and the
   completion actions of Home.

Location messages (the app's own text): *Check-in needs your location. Allow location for HLS in your phone settings, then
try again.* (denied, with Open settings); *Turn on location (GPS) on your phone, then try again.* (services off); *Your
phone could not find its position. Move to an open area and try again.* (no fix). Nothing is sent in any of the three.

## Complete check-out (dialog, from Home, Check-in or My Month)

Time (HH:mm, on that day) and a required reason. Send is disabled while either is empty. A refusal (time not on that day,
a future time, a locked month) is shown with the server's reason and the entries stay.

## My Month [`/manager-attendance/me/month`]

- The month picker of spec 019 and the generalised month grid: one cell per day with the status colour and label.
- A figures strip from the server (working days, days worked, leave paid and unpaid, unpaid, field days) or the server's
  `figuresUnavailable` message; a "Locked" note when `locked`.
- A day sheet (as in spec 019) with times, Office or Field, purpose and linked activities, the photo thumbnail, leave, and
  the flags in plain words. Actions: Complete check-out on a `NO_CHECK_OUT` day; Ask to add a check-in on a past day with
  no check-in (hidden in a locked month).
- "Check-in requests" under the grid: the Manager's requests with status and decision reason, and Cancel on Pending ones.

## My Leave [`/manager-attendance/me/leave`]

- A request form: leave type (from the server's list, with the Loss-of-Pay type marked), first and last date, optional half
  day at the start and at the end, and a required reason. Submit is disabled until type, dates and reason are present and the
  last date is not before the first; every other rule is the server's. On success the list shows the new Pending request at
  the top with "Request submitted" and the form is cleared.
- The Manager's requests, newest first, each with type, dates, half days, status and, once decided, the decision and the
  reason; Cancel on a Pending request when the menu grants Delete.
