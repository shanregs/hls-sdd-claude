# 034 Mobile Visits and Tasks: `/speckit-specify` input

2026-10-08. Roadmap row 034 (new). Depends on 023 (School marketing, visits), 025 (Manager tasks) and 018
(Android app foundation). Wave 4 in the delivery plan: do not run `/speckit-specify` until 025 is specified
and its contracts exist (023 is already specified and implemented on its branch). It adds screens to the
Android app and no new server rules.

## Feature description (paste as the argument to `/speckit-specify`)

034-mobile-visits-tasks: Visit and task screens for the Manager in the HLS Android app, built on the app
shell, sign-in and server-driven menus of spec 018, the School visit rules and APIs of spec 023 and the task
rules and APIs of spec 025, which stay unchanged.

1. **My visits.** The Manager sees their planned visits and meetings (today, upcoming, past) with the School
   or prospect, time, purpose and status, filtered by status.
2. **Log a visit.** For a visit, the Manager moves it through the server's status flow and records people
   met, discussion, a mandatory outcome and any follow-up, with attachments (photos) through the shared file
   store of 023. A visit cannot be closed without the outcome the server requires.
3. **Plan a visit.** The Manager plans a new visit to a School or prospect (date, time, purpose), and can
   reschedule or mark one missed with a reason, as 023 allows.
4. **Follow-ups.** A follow-up set on a visit appears as a task (025 contract C2); the Manager sees it in
   their task list.
5. **My tasks.** Today, upcoming and overdue tasks. The Manager acknowledges a task, marks it in progress
   and completes it with evidence (a note and photos). An overdue task asks for a reason, as 025 requires.
   Escalation and verification stay server-side; the app shows their results.
6. **Task and visit detail.** History of status changes, who assigned it, due date, linked record, evidence.
7. **Home.** Counts for today's visits and overdue tasks replace the "coming soon" cards for the Manager.
8. Menu entries appear only because the server's access model offers them (spec 018); what each button may
   do comes from the server's `allowedActions`. Refusals are shown in plain language and the entered data is
   kept. Every call carries the device location or its reason, as in spec 018.

Out of scope: creating tasks for other users and verifying completed tasks (web, Director and Admin),
pipeline stage management and Final Stage review (web), proposals and MoU recording (web), offline capture
(a later spec), video attachments, push notifications, and iOS.

## Role & Permission Impact (to carry into the spec)

Manager: own visits and tasks (and Zone scope where 023 defines it). Director: read of the same lists where
the web app allows it. Admin, Teacher and System: no mobile screens. No new permission keys.

## Points for `/speckit-clarify`

- Should visit logging be possible without signal and sync later, or is offline strictly out for now?
- Maximum attachment count and size per visit or task on a phone, and is compression done in the app?
- Does starting a visit capture location automatically (feeds 032 field-day evidence), or only on request?
- Can a Manager create an ad-hoc task for themselves, or only act on assigned tasks?
- Are prospects visible on the phone beyond the visit's own context?
- What does the Director see on mobile: the same lists, or only counts?
