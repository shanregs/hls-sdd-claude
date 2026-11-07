# HLS Operations Management System: requirements as implemented

Written 2026-11-07 from `specs/*/spec.md` (001 to 040 including 005a, 012b, 013a/b, 014a/b/c, 017a/b), `docs/spec-roadmap.md`, `docs/HLS-core-business-flow.md`, `docs/HLS Teacher Management System — Requirements.md` and `docs/er-diagrams.md`. It describes what the specs say the system does, not a code audit. Where the roadmap marks a spec as deferred (012b) or as still awaiting a walkthrough, the module says so. Spec ids are in brackets. Decisions D1 to D15 are in `docs/spec-roadmap.md` section 8.

HLS places trained Teachers in partner Schools under a signed MoU. Its Managers (one per Zone) market to Schools, recruit and train Teachers, watch quality and collect fees. The Director and Admin run payroll, billing and approvals. The system is a Spring Boot modular monolith (PostgreSQL, 146 tables), a React web app and an Android app.

## 1. Roles

The constitution fixes six roles (five in specs 001/002, the sixth added by 029). A user may hold several; permissions are the union, set in an editable module x action matrix (002/004). Data scope is per Zone and School for Managers, and "own" for Teachers.

| Role | Who | Scope | Main capabilities |
| --- | --- | --- | --- |
| Admin | Office/IT staff | All data | Master data, user management, contracts, attendance lock, payroll process, billing, working days, audit |
| Director | Owner/management | All data | Everything Admin does except user setup; approvals (payroll, expenses, MoU, incentives, transfers, Manager leave), dashboard, margin, offers |
| Manager (Zone Manager) | Area coordinator | Own Zones, Schools, Teachers | Teacher attendance and leave in scope, visits and prospects, tasks, observations, drives, expenses, check-in/out, content authoring |
| Teacher | Placed or in-training Teacher | Own records | Own attendance, leave, profile, expenses (mobile), training registration |
| System | Technical operator | No business data | Audit, system settings, health, user and role setup |
| Content Contributor | Document/content creator | Own content | Create and submit content (029); sees nothing else |

A Substitute-type Teacher (017b) has no login; a Zone Manager records the days for them.

## 2. Module dependency summary

```
identity(001,004) -> access model(002) -> audit(003), settings(011), notification(010)
master data(005,005a): Zone, School, Manager, Teacher, designation
  -> attendance(008) -> leave(009,040) -> substitution(017a) -> payout(017b)
  -> contracts(012,031) -> billing(022) -> collections reports(014b)
  -> recruitment(016) -> training(024)
  -> marketing(023) -> contracts(012) -> incentives(030) -> payroll(013b)
tasks(025) is the shared task engine: used by 022, 023, 026, 029
files(shared store) is used by 015, 023, 025, 026, 029, 031, 032
salary structures(013a) + attendance(008,032,037) + 017b + 015 + 030 -> payroll(013b) -> reports(014) -> dashboard(028)
transfer(027) changes placements (012) and touches 008, 017a, 022, 013a
mobile (018-021,033,034,036) is a client of the same APIs
```

Cross-module contracts: C1 planned-activity feed (016, 023, 024, 025 -> 032, 035, 015), C2 follow-up task creation (025), C3 approval pattern, C4 pay inputs, C7 shared file storage.

## 3. Modules

### 3.1 Identity and sign-in [001, 004]
Purpose: authenticate every user and manage accounts.
Actors: all; Admin/System manage users.
1. Sign in with phone number or username plus password, or with a one-time SMS code; roles detected at sign-in.
2. Short-lived access token with rotating renewal token; reuse of a used renewal token ends the session.
3. Lockout after 5 failed attempts for 30 minutes; at most 3 code requests per phone per minute.
4. Password reset by code; user lists and ends own sessions; System lists all sessions.
5. Admin/System create users, assign roles, deactivate/reactivate, set another user's password.
Rules: one unique phone per user; password at least 10 characters; failures never reveal whether an account exists; the last Admin can never be removed or locked out (atomic check); deactivation kills all sessions.
Data: user, one-time code, session, role assignment. Deployment seeds one Admin and one System user. Depends on: none.

### 3.2 Access model and app shell [002]
Purpose: one permission matrix and role-aware navigation.
1. Seeded role-to-permission matrix by module and action, editable at runtime by Admin/Director/System, every edit audited.
2. A per-user access model API (menus, actions, scope) computed at each sign-in and refresh.
3. Frontend renders only authorised menus and actions; route guards; "not authorised" page; per-role dashboards.
4. Responsive navigation, light/dark theme.
Rules: unauthorised items are invisible; System has no business-module access; a matrix edit that leaves no user with the permission is refused.
Data: permission matrix. Depends on: 001.

### 3.3 Audit [003]
Purpose: append-only history. Actors: Admin, System.
1. Login history, change history, user activity and a unified Audit Logs feed, each searchable, filterable, paged and exportable to CSV.
2. Every module writes entries for its changes (actor, time, before/after). API Access trail with device location for mobile (018).
Rules: entries are immutable and kept forever. Data: four audit tables. Depends on: 001, 002.

### 3.4 Notifications [010, 021]
Purpose: in-app notices for business events.
1. Events create a notification in the same transaction as the cause (leave decisions, attendance changes and locks, substitution needs, task assigned/overdue/escalated, expense, payroll and incentive steps, transfers, missed check-ins).
2. Bell with unread count, list, mark read, delete; mobile bell polls every 30 seconds.
Rules: 90-day retention; text carries nothing the reader could not already see. No SMS or push yet. Data: notification. Depends on: 002.

### 3.5 System settings and themes [011, 039]
Purpose: runtime configuration.
1. Eleven settings (password length, lockout, OTP throttle, session days, mobile version gate, incentive amount) with ranges, defaults and change history.
2. System dashboard: health, user counts, failed sign-ins, recent audit.
3. User themes: four presets, light/dark/device mode, own accent colour checked for WCAG AA contrast, also on the phone.
Data: system_setting; theme preference per user. Depends on: 003, 004.

### 3.6 Master data: Zones, Schools, Managers, Teachers, designations [005, 005a]
Purpose: the registry everything else hangs on.
Actors: Admin/Director write; Manager views and edits own Schools; Teacher views own profile.
1. Zones (called Areas in the deck) with Places (name and PIN); bulk place import.
2. Schools with profile, Zone, Principal and management contacts; cannot be deactivated while Teachers are placed.
3. Manager records for users with the Manager role, assigned to Zones and one Manager per School, with assignment history.
4. Teacher records (never deleted), four stored statuses (the specs do not list them in the sections read) with Available, Assigned, Under Review, Transferred, Replaced derived (D3).
5. Teacher salary history (rupees, effective dates), visible to Admin/Director only.
6. Designation list; Manager designation, joining and exit dates, employee id (unique across Managers and Teachers).
7. A shared scope query tells every module which Zones, Schools and Teachers a Manager may see.
8. Engagement type Regular or Substitute (017b).
Data: zone, place, school, school_contact, manager, assignments, teacher, teacher_salary_history, designation. Depends on: 002, 003.

### 3.7 School working days [037, 038]
Purpose: the number of working days each School counts per month (used for pay and attendance).
1. Admin grid per School and month (0 to days in month, half days), add/edit/delete with history and a Deleted view; copy one year to another.
2. Year-entry screen: twelve months, one Save, valid months saved, refused months reported by name.
3. Published interface returns the stored figure or the calendar-derived count, labelled.
Rules: one figure per School and month; year within current plus or minus 5. Data: school_working_days and history. Depends on: 005, 008.

### 3.8 Teacher attendance [008, 019]
Purpose: daily presence, the basis of pay.
1. Teacher marks own day (today and previous 3 days); Manager marks and corrects in scope; Admin/Director any (D7).
2. Status codes (Present, Leave, Training day and others) and a non-working calendar (organisation default plus per-School weekly offs and holidays); holiday calendar visible to all, PDF download.
3. Monthly rollup per Teacher; month grid for supervisors; CSV export.
4. Admin/Director lock a month and reopen it with a reason.
5. Absence event to substitution (017a), a loss-of-pay flag per mark, optional Principal confirmation (A2).
Rules: one current mark per Teacher per date, tied to the School the Teacher is placed in; an explicit mark beats the calendar; locked months refuse edits; optimistic concurrency. Data: attendance mark, history, month, month event, calendar tables. Depends on: 005, 012. Mobile: month calendar, history, Manager review (019, online only).

### 3.9 Leave [009, 020, 040]
Purpose: Teacher leave requests.
1. Placed Teacher applies (type, dates, half days); types Casual, Sick, Personal, Other, Loss-of-Pay.
2. Manager/Admin/Director approve or reject (reason mandatory on reject) and can revoke.
3. Approval writes Leave marks to attendance atomically.
4. Same-day leave (040): recorded for today it is approved at once and flagged; a request running past today is split.
Rules: working days computed from School weekly offs and holidays; no overlap; no leave balance; payroll deducts every Teacher leave day whatever the type (040). Data: leave_request. Depends on: 008, 010.

### 3.10 Manager attendance [032, 033]
Purpose: Manager check-in/out for pay and field accountability.
1. Check in and out once a day with mandatory device location and optional photo; day type Office or Field (Field needs a purpose or a linked planned activity, C1).
2. Forgotten check-in or missing check-out requests and completion by Admin/Director.
3. Manager leave decided by Admin or Director; missed-check-in alert.
4. Month view, grid, corrections, month lock feeding payroll; attended training days count as worked.
Data: manager_day, activity links, manager_month, manager_leave_request, regularisation. Depends on: 005, 010, 025. Mobile: check-in screens (033).

### 3.11 School contracts and MoU [012, 031, 012b deferred]
Purpose: the agreement between HLS and a School, and the mapping of Teachers to it.
Actors: Admin/Director create; Zone Manager maps Teachers.
1. Contract with Teacher count, start/end, monthly salary (same for all or per position), signing details; at most one active contract per School; a new MoU ends the old one with re-mapping and no gap; "MoU pending" placeholder.
2. Map a Teacher to a vacant position; a Teacher has one current School; scheduled moves.
3. Contract status list (active, ends soon, pending, none).
4. Signed MoU document upload (versions kept), approval by Director or Zone Manager (D5), "approved without document" state (031).
5. 012b (billing basis per term/annual, payment due days, GST, tax lines) is specified but deferred (D10).
Rules: a Teacher in training cannot be mapped (D1); salary starts at first School assignment (D2). Data: contract, position, assignment, mou_document, approval event. Depends on: 005. Public interface used by payroll, billing, incentives.

### 3.12 Substitution and day-rate pay [017a, 017b, 040]
Purpose: cover for absent Teachers.
1. An absence or approved leave raises one requirement per Teacher and date; Manager (else Admin/Director) notified.
2. Candidate list of free Substitute-type Teachers; assign, reassign, release; School informed is recorded; or the Manager covers (kept as Manager cover entry, 040).
3. Credit goes to the substitute, not the absent Teacher.
4. 017b: Substitute has no salary, placement or login; day record per allocation, marked Present/Absent by Zone Manager/Admin/Director; monthly lock; pay is days x the designation's daily rate in the monthly payroll; cancellations after payment become Admin Corrections.
Data: requirement, allocation, substitute_day, events, month lock, designation_day_rate. Depends on: 008, 009, 012, 013a/b.

### 3.13 Campus recruitment and induction [016]
Purpose: hire Teachers from colleges.
1. College list with placement officer and principal contacts; campus drives (dates, venue, interviewers).
2. Candidates per drive, single or sheet import; interview outcome history; waitlist can be promoted.
3. Director issues a Job Offer (package, terms, deadline); a changed package is a new superseding offer; offers expire.
4. Acceptance creates the Teacher "in training" exactly once; one-month induction batch (seat limit, attendance, sign-off) makes them Active and ready to deploy.
5. Pipeline dashboard: drives, candidates, offers, joiners, joining ratio per college and season; optional food/accommodation capture becomes Training Stay expense.
Rules: training is unpaid (D2); drives are planned activities (C1). Data: 21 tables (college, drive, candidate, offer, batch, enrolment and histories). Depends on: 005, 015.

### 3.14 Training calendar [024]
Purpose: recurring refresher training for Teachers and Managers.
1. Weekend refresher and monthly sessions; standing pattern (for example second Saturday) generating sessions idempotently; skip a month with reason.
2. Teachers register within seat limits; Admin/Director assign people by group.
3. Attendance recorded and written to attendance (training day); non-compliance exceptions; completion history; reminders.
Data: 13 tables. Depends on: 008, 016.

### 3.15 School marketing and prospects [023, 034]
Purpose: win Schools.
Actors: Admin, Director, Zone Manager.
1. Prospects with contact and expected Teachers; stage pipeline (Prospect, Contacted, Visit, Follow-up, Interested, Negotiation, Final Stage, MoU, Active, On Hold, Lost) with history.
2. Activities: visit, call, proposal meeting, follow-up with status and mandatory outcome; marketing calendar; missed and rescheduled; overdue notices.
3. Proposed MoU terms with revisions kept.
4. Director or Zone Manager approves Final Stage, which makes the prospect won and pre-fills the MoU form (012).
5. Demand (won Schools waiting) versus supply (recruits ready) view.
Rules: a Final Stage prospect needs management review. Closing a School triggers the incentive (030). Data: marketing_* tables (about 15). Mobile: visit log (034).

### 3.16 Manager tasks [025, 034]
Purpose: one task engine with accountability.
1. Task types (school visit, follow-up, content, recruitment, training, Teacher assignment, observation, Principal feedback, substitute arrangement); lifecycle Assigned, Acknowledged, In progress, Completed, Verified.
2. Completion needs a note of 10+ characters and up to 5 evidence files; verifier cannot be the assignee.
3. Overdue (derived) needs a reason and can create a follow-up; daily check escalates to the Director.
4. Other modules create tasks through C2 (overdue invoices, follow-ups, corrective actions, content). Manager may create personal tasks.
Data: task, history, evidence. Mobile: My Tasks with photo evidence (034).

### 3.17 Teacher observation and performance [026]
Purpose: quality monitoring.
1. Classroom observation form: ratings 1 to 5 across several areas, comments, up to 5 attachments, Draft/Submitted, suggested result as a guide.
2. Principal feedback with optional permission record; "not held" reasons.
3. Performance issue, corrective action, follow-up observation; planned observations are tasks; unsuitable Teachers are referred to transfer/replacement.
Data: 10 tables. Depends on: 005, 012, 025, 027.

### 3.18 Transfer and replacement [027]
Purpose: move Teachers between Schools without breaking billing and pay.
1. Admin/Director/Zone Manager raise a transfer or replacement with reason category and effective date.
2. Director or leaving School's Zone Manager approves; rejection needs a reason.
3. Approval ends the old placement the day before and starts the new one atomically; history is never overwritten; one open request per Teacher.
Data: movement_request, participant, assignment, history. Depends on: 012, 008, 017a.

### 3.19 Salary structures [013a]
Purpose: pay rules.
1. Fixed monthly salary per person, versioned by effective date, append-only (Teacher's on the record, Manager and staff in 013a); designation default is only a suggestion (D12).
2. Pay policy: LOP per unpaid day equals salary divided by the month's working days; pro-rata for joining, transfer, exit; Teacher paid only for days placed in a School (D13).
3. `PayRules` interface answers payable days, day value and salary on a date.
Data: person_salary, designation_salary, pay_policy, staff_employment. Depends on: 005a, 012, 008.

### 3.20 Payroll run [013b]
Purpose: monthly pay for Teachers, Managers, staff and Substitutes.
Actors: Admin processes; Director approves.
1. Admin starts a run for an ended month; includes everyone with payable days whose attendance month is final; a person who cannot be computed is flagged, never blocks the run.
2. Line = earned pay less LOP, plus adjustments (incentive, reimbursement, substitute pay, corrections); compute again while Draft.
3. Director/Admin approve; approved run is immutable and locks the month; corrections go to the next run as adjustments; supplementary run for people left out.
4. PDF payslips, payment status recorded manually (paid, date, mode, reference), CSV export for the accountant (D8).
Data: 13 tables (run, line, adjustment, payslip, paid month, events). Depends on: 008, 009, 013a, 032, 015, 017b, 030.

### 3.21 Manager incentive [030]
Purpose: reward closing a School.
1. When a MoU becomes approved, one incentive (default Rs 5,000, a setting) is created automatically for the prospect owner; at most one per School ever.
2. Director or Zone Manager approves or rejects (D5); approved amounts enter Manager payroll as an adjustment.
3. Not-earned list for Schools without owner or won prospect; reversal if MoU un-approved.
Data: incentive, program, events. Depends on: 012, 023, 031, 013b.

### 3.22 Expenses [015, 036]
Purpose: office and field expenses with bills.
1. Manager/Admin/Director raise a claim: date, amount, category (Rent and Utilities, Travel and Fuel, Training Stay, Marketing, Office Supplies, Other with reason), area (Zone or Head office), bills (jpg/png/pdf up to 10 MB, 5 files or a no-bill reason).
2. Statuses Draft, Submitted, Approved, Rejected (reason, resubmit), Paid (with payroll or recorded paid); no approval limit (D11).
3. Travel links to a visit or drive, Training Stay to a batch; duplicate warning; idempotent submission.
4. Budget versus actual per area; pending approvals with ageing; cost per School acquired and per Teacher trained; My Expenses.
Data: claim, bill, events, budget. Mobile: bill photo capture with offline queue and HEIC/WebP conversion (036).

### 3.23 School billing and receivables [022]
Purpose: invoice Schools and collect.
1. Admin/Director raise one monthly due per School per month: each mapped Teacher at the position fee, pro-rated only for joining/transfer/exit; absences do not reduce it (D6); immutable once raised.
2. Due date = raised date plus payment due days setting; statuses open, part paid, paid, cancelled; overdue derived.
3. Collections (amount, date, mode, reference) allocated to dues, append-only with reversals; credit notes for agreed deductions.
4. Balance and ageing buckets 0-30, 31-60, 61-90, 90+ by School, Zone and Manager; overdue creates a follow-up task for the Manager and escalates to the Director.
5. Not-billed list (Teachers with no position, Schools with no MoU). Monthly per Teacher, no GST (D10).
Data: 15 tables (due, line, collection, allocation, adjustment, follow-up, setting, view due_balance).

### 3.24 Reports [014a, 014b, 014c]
Purpose: read-only management reports on one framework.
1. Filters (month, Zone, School, Teacher, Manager), caller scope applied before counting, CSV with header rows, every export audited.
2. 014a: Attendance and LOP, Payroll cost, My reports. 014b: Margin (fee minus Teacher pay, D14), Collections, School. 014c: Recruitment, Teacher, Manager productivity, Expenses.
Rules: figures come from owners' published interfaces and must equal the owning screens; unlocked months are labelled; a failing source shows "not available" for its column. Zone Manager sees margin but never payroll. No tables of its own.

### 3.25 Management dashboard and activity calendar [028, 035]
Purpose: one screen for the Director.
1. Tiles for Managers, Schools and marketing, Recruitment, Teachers, Performance, Operations, Content, Attendance, Payroll, Expenses, Receivables; each with figure, period label and link; zone and period filters.
2. Approvals inbox (Manager leave, expenses, payroll, MoU, incentives, transfers).
3. Activity calendar: planned versus done, missed, rescheduled across drives, visits, training and tasks, per Manager (read-only on C1).
Rules: reads through public interfaces only; Managers see a scoped dashboard. No tables of its own.

### 3.26 Content management [029]
Purpose: control content preparation and output.
1. Content record (ID, title, category, subject, author); optional Manager task via C2.
2. Submission requires the actual file (video 500 MB, audio 100 MB, PDF and Word 25 MB); each submission is a numbered version, old versions kept.
3. Reviewer approves or returns with mandatory comment; history; management search.
4. Content Contributor role with its own dashboard and My Content.
Data: 4 tables. Depends on: 025, files.

### 3.27 Android app [018 to 021, 033, 034, 036]
React Native client for Teacher, Manager and Director using the same APIs: sign-in, server-driven menus, device location recorded on every API call (AUDIT trail), attendance (019), leave (020), notifications (021), Manager check-in and plan (033), visits and tasks (034), expenses with offline capture (036), same-day offline queue (040). Everything else is online only.

## 4. Data footprint

146 tables across 22 backend modules; only 79 real foreign keys. Largest: recruitment 21 (including marketing), attendance 17, schoolbilling 15, training 13, payroll 13, performance 10 (`docs/er-diagrams.md`). Overlap noted there: three salary stores, two leave tables, two monthly-summary patterns, polymorphic person_kind columns, many per-module history tables.
