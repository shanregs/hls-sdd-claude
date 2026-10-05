# 019 Mobile Attendance: `/speckit-specify` input

2026-10-04. Roadmap row 019. Depends on 008 (attendance, server side) and 018 (Android app
foundation). It adds screens to the Android app; it adds no new server rules.

## Feature description (paste as the argument to `/speckit-specify`)

019-mobile-attendance: Attendance screens for the HLS Android app, built on the app shell, sign-in and
server-driven menus of spec 018 and the attendance rules and APIs of spec 008, which stay unchanged.

1. **Teacher: My Attendance.** A monthly calendar of the signed-in Teacher's own attendance. It shows
   the current month by default and lets the Teacher pick any month of the current year. Each day shows
   its state (marked with its status, weekly off, holiday, not placed, future, unmarked). The Teacher can
   mark Present, Absent or another allowed status, with a whole or half day and an optional note, on
   today or one of the previous 3 days, exactly as the web allows. Refusals from the server (locked
   month, set by a supervisor, no placement, too old, future) are shown in plain language.
2. **Teacher: Attendance History.** Read-only view of earlier months, with the same month picker.
3. **Holiday Calendar (every role).** A read-only calendar of non-working dates and weekly offs. It shows
   the current month by default and lets the user pick any month of the current year.
4. **Manager: Teacher Attendance.** A list of the Manager's assigned Teachers for a chosen month, with a
   rollup per Teacher, and a month view per Teacher in which the Manager can mark, correct or clear a day
   (any unlocked date inside a placement). Only assigned Teachers are ever shown.
5. The menu entries appear only because the server's access model offers them (spec 018): My Attendance
   and Attendance History for Teachers, Teacher Attendance for Managers, Holiday Calendar for everyone.
   Nothing is chosen by role name in the app.
6. Every call carries the device location or its reason, as in spec 018, and every mark is audited by
   the server as in spec 008.

Out of scope: offline capture and later sync, photo or geo-tag evidence, push notifications, leave
requests, Admin/Director grids and month lock or reopen (web only), CSV or PDF export, and iOS.

## Points for `/speckit-clarify`

- Which statuses can a Teacher choose on the phone (all allowed by spec 008, or a short list)?
- Does the Manager screen need a search box, or is the assigned list short enough without one?
- When a mark fails because the month is locked or was set by a supervisor, should the screen offer a
  "ask my Manager" shortcut, or only explain?
- Is the Holiday Calendar a yearly overview as on the web, or monthly only on a phone?
- Should the month picker allow only the current year (as requested), or also the previous year for
  History?
