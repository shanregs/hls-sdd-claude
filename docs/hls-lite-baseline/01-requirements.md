# Requirements (release 1: everything in scope)

One testable statement each. Rules and numbers are in `03-business-rules.md`. Roles are in `04-roles-permissions.md`.
Scale: 200 Teachers, 15-20 Managers, 50 Schools, 10 Zones, 10-20 Substitutes, about 250 sign-in users.

## Platform (PLT)
- PLT-01 Users sign in with phone or username and password. SMS one-time codes are not in this release; Admin or System Engineer sets a temporary password for a user who forgets theirs.
- PLT-02 Five failed sign-ins lock the account for 30 minutes. Failure messages never reveal whether the user exists.
- PLT-03 A session lasts about 12 hours and renews on activity. Admin can sign a user out.
- PLT-04 Admin sets a temporary password for a user who cannot sign in.
- PLT-05 Admin or System Engineer creates users, assigns one or more roles, deactivates users, resets passwords. The last Admin and oneself cannot be deactivated.
- PLT-06 An Admin-edited permission matrix (role x permission) controls menus, routes and API actions. Changes take effect on the next request and are audited.
- PLT-07 The matrix cannot remove the "manage permissions" permission from Admin.
- PLT-07a The matrix rejects granting the System Engineer role any payroll, salary, billing or HR-record permission, and the rejected attempt is audited. The check lives in the permission-save code, not only the screen.
- PLT-08 Data scope rules (Admin and Director all; Manager the Schools in their Zones; Teacher self; Content Creator own items) are enforced in code on every query and are not editable.
- PLT-09 One `audit_log` records who, when, what, entity, before and after for changes to people, assignments, attendance corrections, money, approvals, settings and permissions.
- PLT-10 Admin and Director search the audit log by user, entity and date. Audit entries are kept 3 years.
- PLT-11 A settings page edits: incentive amount, payment due days, overdue-task days, lockout constants.
- PLT-12 Users see a notification bell. Notifications are rows inserted in the same transaction as the event that causes them.
- PLT-13 Notification events: leave requested, leave decided, substitution needed, substitution assigned, task assigned, expense decided, payroll finalised, payroll acknowledged, MoU approved, transfer requested, transfer decided, induction sign-off.
- PLT-14 Uploaded files are stored with name, type, size, uploader, time and are downloadable only by users who may see the owning record.
- PLT-15 The UI is a responsive web app installable as a PWA, usable on phones, with light and dark themes only if free from the component library.
- PLT-16 Dates display as DD/MM/YYYY in IST; money in rupees with Indian digit grouping.
- PLT-17 Every list screen pages at 50 rows.

## Master data (MST)
- MST-01 Admin and Director maintain Zones.
- MST-02 Admin and Director maintain Schools: name, address, city, pincode, Zone, Manager (defaults to a Zone Manager), optional weekly-off override, contacts with name, designation (for example Principal) and phone.
- MST-03 Each School has exactly one named responsible Manager, who approves its MoU, transfers and Teacher expense claims. Other Managers of the Zone can still view it and act on attendance, substitution and observations.
- MST-04 A Manager belongs to one or more Zones. A Zone can have several Managers.
- MST-05 Admin and Director maintain employees of kinds Teacher, Manager, Admin, Director, System Engineer, Content Creator and Substitute, with employee id, designation, joining and exit dates, and status.
- MST-06 Employees are never deleted.
- MST-07 Teacher status is Trainee, Active or Exited. Other kinds are Active or Exited.
- MST-08 Designations list has name and suggested salary.
- MST-09 Each employee has a dated salary history. Rows are never overwritten; a change adds a row with an effective date.
- MST-10 Substitutes belong to a Zone, have no login, no salary and no placement.
- MST-11 Admin maintains the holiday list and org-wide weekly offs. A School may override weekly offs.
- MST-12 Admin sets a School month figure (days) per School and month for invoicing. It defaults to the calendar days of that month and may be edited, for example to 30 for a 31-day month. One screen per School and year; save is all or nothing.

## Contracts and placements (CON)
- CON-01 A School contract holds Teacher count, start and end dates, monthly fee (same for all positions or per position), signatories and signed-PDF versions.
- CON-02 A School has at most one active contract.
- CON-03 A contract is Pending or Approved. Director or the School's Manager approves. The document is optional; approval without one shows a warning.
- CON-04 Admin, Director or the School's Manager maps an Active Teacher to a vacant position. A Teacher has at most one current placement.
- CON-05 Placements have start and end dates and are kept as history. The first placement date is the pay start date.
- CON-06 Approving a new contract ends the old one and re-maps Teachers with no gap.
- CON-07 A list shows Schools without an active contract.

## Attendance and leave (ATT)
- ATT-01 A Teacher marks own attendance for today and the previous 3 days using Present, Absent, Leave, Half day.
- ATT-02 A Manager marks and corrects Teachers at Schools in their Zones. Admin and Director do so anywhere. Every correction is audited with a reason.
- ATT-03 Attendance is not markable on weekly offs or holidays.
- ATT-04 A monthly grid per School shows attendance and exports CSV.
- ATT-05 Admin locks a month for Teachers; locking is refused while any placed working day is unmarked. Reopening needs a reason.
- ATT-06 A Teacher applies for leave of type Casual, Sick, Personal, Other or Loss-of-Pay. Every leave day is deducted.
- ATT-07 A Manager of the School's Zone decides leave; a rejection needs a reason. Approval writes Leave marks on working days only.
- ATT-08 Leave for today is auto-approved and flagged. A Manager can record leave on a Teacher's behalf.
- ATT-09 A Teacher may cancel a pending or future leave.
- ATT-10 Managers check in and out daily with browser location, a day type (Office, Field, Leave) and, for Field, a purpose text.
- ATT-11 Manager leave is requested by the Manager and decided by the Admin or Director.
- ATT-12 Admin edits a Manager's day with a reason (forgotten check-in).
- ATT-13 A month grid for Managers exports CSV. Admin locks the Manager month. Lock lists unresolved working days; each must be resolved first.
- ATT-14 The dashboard lists Managers with no check-in today.

## Substitution (SUB)
- SUB-01 An Absent or Leave mark on a placed Teacher's working day creates a substitution requirement and notifies the Manager.
- SUB-02 The Manager sees the Zone's Substitutes not booked that day and assigns one, or marks "Manager covers", or leaves it open.
- SUB-03 The Manager records "School informed" as a checkbox and a note.
- SUB-04 The assignment records the day rate for that Substitute, typed per assignment.
- SUB-05 After the day, a Manager of the Zone or Admin marks the Substitute Present or Absent.
- SUB-06 If attendance changes so the Teacher is not absent, the requirement reopens or cancels; a cancelled requirement with a Present Substitute is flagged to Admin.
- SUB-07 Present Substitute days become payroll lines (rate x 1 day each). A day marked after finalise goes into the next run as an adjustment.

## Money (PAY, BIL, EXP)
- PAY-01 Fixed monthly salary per person with dated history.
- PAY-02 Pay is computed by the formula in `03-business-rules.md` section 5 using calendar days of the month.
- PAY-03 Teachers are paid only for placed days; Trainees and unplaced Active Teachers have no line.
- PAY-04 Staff (Admin, Director, System Engineer, Content Creator) receive full salary unless a manual LOP adjustment exists.
- PAY-05 Admin starts one run for an ended month, only after Teacher and Manager months are locked.
- PAY-06 A run lists people who cannot be computed with a reason and does not stop.
- PAY-07 Admin recomputes while the run is Draft.
- PAY-08 Admin finalises the run; it becomes locked. The Director is notified.
- PAY-09 The Director acknowledges the run with an optional comment. Acknowledgement does not block payment.
- PAY-10 Adjustments (Incentive, Reimbursement, Substitute pay, Correction, Manual LOP, Other) have a reason and creator.
- PAY-11 Corrections after finalise go into the next month as adjustment lines.
- PAY-12 Payment status per line (paid date, mode, reference) is entered manually.
- PAY-13 A run exports CSV for the accountant; a payslip is a printable page.
- PAY-14 An Incentive adjustment is created when a MoU is approved: once per School, earner is the prospect owner or else the School's Manager, amount from the setting at that time.
- PAY-15 Teachers and Managers can view only their own payslips.
- BIL-01 Admin raises one invoice per School per month from placements, per the formula in `03-business-rules.md` section 8, using the School month figure for that month.
- BIL-02 An invoice is immutable once raised; corrections are credit or debit adjustments.
- BIL-03 Due date = invoice date + the due-days setting.
- BIL-04 Admin records payments (amount, date, mode, reference) and allocates them to one or more invoices.
- BIL-05 A payment can be reversed only by a negative payment.
- BIL-06 Ageing buckets (0-30, 31-60, 61-90, 90+) are shown by School, Zone and Manager.
- BIL-07 A "not billed" list shows Schools with placements but no invoice for a finished month.
- BIL-08 Managers can view dues and overdue invoices for Schools in their Zones but cannot record payments by default.
- BIL-09 An invoice carries a credit line for each uncovered day: a working day on which the placed Teacher was Absent or on Leave and no Substitute was marked Present and no Manager covered. Credit = position fee / School month figure. Raising the invoice is refused while a Substitute assignment in the month has no day mark.
- EXP-01 Any employee with the permission raises a claim: date, amount, category (6), Zone or Head office, up to 5 bills, optional reference text.
- EXP-02 Status is Submitted, Approved, Rejected (reason) or Paid.
- EXP-03 Approvers follow the hierarchy in `03-business-rules.md` section 13; nobody approves their own claim.
- EXP-04 An approved claim is reimbursed via a payroll adjustment or marked paid.
- EXP-05 The Submit button disables after click to avoid duplicates.
- EXP-06 A report shows monthly totals by Zone and category.

## Recruitment and training (REC, TRN)
- REC-01 Admin and Director maintain Colleges with contacts.
- REC-02 Admin, Director and Managers create Drives (date, venue, interviewers) and Candidates, one at a time or by CSV.
- REC-03 Interview outcome: selected, waitlisted, rejected.
- REC-04 Director issues an Offer (salary, terms, deadline). A new offer replaces the old manually.
- REC-05 Admin records acceptance; this creates the employee as Teacher/Trainee and adds her to an induction batch.
- REC-06 The Zone Manager marks induction attendance; Admin or Director sign off the batch and members become Active.
- REC-07 A printable offer letter page exists.
- REC-08 The Drive page shows counts of selected, waitlisted, offered, accepted and joined.
- TRN-01 Admin and Managers create training sessions: date, topic, trainer, venue, audience (new joiners or ongoing Teachers). Several can run on one day.
- TRN-02 A session has an invited-attendees list.
- TRN-03 The Zone Manager marks each attendee Attended or Not.
- TRN-04 For Active Teachers, Attended writes a Training-day mark (paid); Not attended writes Absent.
- TRN-05 For Trainees, attendance is recorded for sign-off and never paid.
- TRN-06 A filter lists invited but not attended people.

## Marketing, tasks, observation, transfers, content (MKT, TSK, OBS, TRF, CNT)
- MKT-01 A Manager adds Prospects: School name, contact, stage, expected Teachers, proposed terms.
- MKT-02 A Manager plans visits and calls (date, purpose) and records the outcome, which is mandatory.
- MKT-03 Stages are tracked on a list and a board.
- MKT-04 Director or the School's Manager approves Final Stage; the contract form opens pre-filled.
- MKT-05 Overdue activity is a query shown on the Manager's dashboard.
- MKT-06 A tile shows prospects by stage and expected Teachers vs vacant positions.
- TSK-01 Admin or Director creates tasks for a Manager; the system also creates tasks from observations and content.
- TSK-02 Status moves Assigned, In progress, Completed, Verified. Completion needs a note; evidence is optional.
- TSK-03 Completing past due date requires a reason.
- TSK-04 Admin or Director verifies; the Director sees overdue tasks.
- OBS-01 A Manager enters an observation for a Teacher at a School in their Zones: date, six ratings (1 to 5), comments, School feedback note, attachments, optional result.
- OBS-02 Draft is editable; after Submit only notes can be added.
- OBS-03 "Needs improvement" creates a follow-up task with a follow-up date.
- OBS-04 "Unsuitable" opens a transfer request.
- TRF-01 A transfer request holds Teacher, from, to, effective date, reason (free text plus 4 categories) and an optional replacement Teacher.
- TRF-02 Admin, Director or the Manager raises it. Director or the leaving School's Manager approves.
- TRF-03 Approval ends the old placement the day before and starts the new one in one transaction.
- TRF-04 A transfer cannot change days already marked in attendance.
- CNT-01 A Content Creator or Manager creates a content item.
- CNT-02 Each upload is a numbered version.
- CNT-03 Admin or Director approves or returns a version with a comment.
- CNT-04 All versions are listed; the file size cap is 100 MB.

## Dashboard and reports (RPT)
- RPT-01 Director and Admin dashboard of live tiles: Teachers absent today, Managers not checked in, pending approvals by kind, open substitutions, overdue tasks, overdue invoices, payroll month status, expenses pending, prospects by stage, joiners ready to place, Schools without a contract.
- RPT-02 Manager dashboard: absences at Schools in their Zones, pending leave, open substitutions, own tasks, overdue invoices of Schools in their Zones, activities due.
- RPT-03 Teacher dashboard: own attendance, leave, payslip, training sessions, notifications.
- RPT-04 Reports with filters and a CSV button: Attendance/LOP, Payroll register, Margin, Collections and ageing, Expenses by Zone and category, People list.
- RPT-05 Margin = fee billed minus Teacher pay allocated by placement days. Visible to Admin and Director only.
- RPT-06 Payroll reports are visible to Admin and Director only.
- RPT-07 The School page shows Teachers, attendance percentage and current contract.

## Non-functional (NFR)
- NFR-01 About 100 concurrent users and 1M rows in the largest table meet response times under 2 seconds at the 95th percentile with no tuning beyond indexes.
- NFR-02 Structured logs, metrics, traces and health checks are produced as in `06-architecture-and-nfr.md`.
- NFR-03 Nightly database dump and uploaded-file copy go off-site; a restore is tested quarterly.
- NFR-04 All transport is HTTPS. Passwords are hashed with a modern algorithm.
- NFR-05 Every money rule in `03-business-rules.md` has automated tests built from the worked examples.
