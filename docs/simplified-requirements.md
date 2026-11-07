# HLS simplified requirements for a new, smaller implementation

Written 2026-11-07. Input: `docs/project-requirements-implemented.md` (read it first for the module list and spec ids). This is a recommendation for the owner, not a decision. Where a rule is dropped, the manual or spreadsheet fallback is stated, so you can judge the cost. Nothing here changes the current code.

## 1. Scale and what it means for the design

| Dimension | Planned size |
| --- | --- |
| Teachers | at most 200 |
| Managers | 15 to 20, plus Admin(s) and Directors (a handful) |
| Schools | 50 |
| Zones | 10 |
| Content creators | a few |
| Substitute Teachers | 1 to 2 per Zone, about 10 to 20 in all, no login |
| Total users who sign in | about 250 |

Worked numbers: 200 attendance marks a day is about 4,400 rows a month, 53,000 a year. Payroll is about 250 lines a month. Invoices are at most 50 a month. Even ten years of data stays under a million rows in the largest table. Concurrent users peak at perhaps 100, mostly Teachers marking attendance in the same half hour. One small server with PostgreSQL handles this without tuning.

Design consequences, stated as rules for the new build:

1. One PostgreSQL database, one schema. No second store.
2. No caching layer. Compute every screen and report from live queries. `docs/caching.md` and the snapshot cache are not carried over.
3. No async work, event bus, outbox or Spring Modulith event registry. A module calls another module as a plain method in the same transaction. A notification is a row inserted by the same code that causes it. Anything "derived" (overdue, absent today, not billed) is a query, never a stored flag or a scheduled job.
4. No read models, report framework, `ReportSource` beans or published-figure parity rules. A report is a SQL query and a CSV button.
5. No pagination or performance specs beyond sensible defaults: list screens page at 50 rows only because a screen of 5,000 is unusable, not for speed. No load tests, no 10k-row pagination test.
6. One deployment: a single application (monolith, one deployable) behind one web server, plus the database and a folder for uploaded files. No containers orchestration, no queue, no separate worker. A nightly database dump and file-folder copy is the whole operations story.
7. Simple code structure: packages per area, direct calls between them, a handful of shared helper classes. Strict module-boundary tests (ArchUnit rules, published interfaces such as `PayRules`, `ReportSource`, C1/C2/C3 contracts) are dropped; they exist to keep 22 modules independent, which a team this size does not need.
8. Roles are a fixed list of five, with permissions written in code and shown as one table (section 3). No editable permission matrix, no access-model API.
9. Mobile: one responsive web app used on phones (a progressive web app), not a separate React Native build. Section 2 explains the trade.
10. Keep what scale does not touch: money correctness, append-only financial history, audit of money and people changes, scope by Zone, locked months. Those are about trust and not about load.

Monitoring, alerting and tracing: a health check URL and the server log. Nothing else.

## 2. Decision per module and spec

Key: KEEP (as is), SIMPLIFY (how), MERGE (into which), DROP, DEFER (phase 2 or later). "Fallback" is what people do instead.

### Platform and access

| Spec | Decision | What exactly, and why at this scale |
| --- | --- | --- |
| 001 Identity | SIMPLIFY | Keep phone/username plus password and SMS one-time code (Teachers forget passwords; 250 users makes SMS cost small). Drop rotating-refresh reuse detection and "list/end my sessions" screens: use one session token of about 12 hours renewed on activity, and an Admin "sign out user" button. Keep lockout (5 tries, 30 min) as constants. Keep the rule that failures reveal nothing. |
| 002 Access model, shell | SIMPLIFY | Hard-code five roles and a role x capability table in code. Drop the editable matrix screen, the per-user access-model API, runtime matrix change history. Keep role-specific menus, route guards and one dashboard per role. Reason: the roles will not change weekly; a code change is cheaper than a configuration engine. |
| 003 Audit | SIMPLIFY | One `audit_log` table (who, when, what, entity, before/after JSON), written by one helper for changes to people, assignments, attendance corrections, money, approvals and settings. Login failures: log lines plus "last login" on the user. Drop the four-view audit area, API Access trail with device location, per-view exports, and indefinite-retention guarantees (keep 3 years). One search screen for Admin. |
| 004 User/role management | SIMPLIFY | Users list, create, tick roles, deactivate, reset password. Keep the "cannot deactivate yourself / last Admin" guard as a simple check. |
| 010 Notifications | SIMPLIFY | Keep an in-app bell with a plain `notification` table for about 10 events: leave requested/decided, substitution needed/assigned, task assigned, expense decided, payroll ready/approved, MoU approved, transfer requested/decided. Drop 90-day purge job, mobile polling spec, per-spec event catalogue. Dashboards carry the "needs you" counts anyway. |
| 011 System settings | SIMPLIFY | One key-value `setting` table with a simple Admin page (incentive amount, payment due days, substitute day rate default, working-day rules, lockout constants only if wanted). Drop the System role, health dashboard, security-policy screens and range-validated eleven-setting framework. |
| 039 User themes | DROP | One theme with good contrast. Fallback: none needed. |

### Master data and calendar

| Spec | Decision | What exactly, and why |
| --- | --- | --- |
| 005 Master data | KEEP, trimmed | Zones, Schools (with contacts), Managers (assigned to Zones; each School has one Manager, defaulting to its Zone's Manager), Teachers (never deleted, status active/in training/exited). Drop the `place` table with PIN bulk import: School has city and pincode text. Drop Manager-assignment history tables (the audit log records changes). Scope query becomes one helper: Manager sees their Zones' Schools and Teachers. |
| 005a Designations | MERGE into 005 | A `designation` lookup (name, kind, optional suggested salary, substitute day rate), plus employee id, joining and exit dates as plain columns. Drop dated designation history, the "missing before payroll" checker and employee-id claim table. Fallback: Admin reviews the Teacher list for blanks before payroll. |
| 037 School monthly working days | SIMPLIFY | Keep the figure, because payroll needs it (salary / working days). One table: school, year, month, working days; one screen per School and year (the 038 layout). Default = days in month minus weekly offs minus holidays, shown as a hint. Drop copy-year, Deleted-rows view, change-history screens and per-month partial-save reporting (all or nothing save). |
| 038 Year entry | MERGE into 037 | It is the only screen the simplified 037 needs. |
| 008 (calendar part) | KEEP | Org-wide weekly offs and holiday list (one `holiday` table) with an optional per-School weekly off. Drop PDF download of the calendar. |

### Attendance and leave

| Spec | Decision | What exactly, and why |
| --- | --- | --- |
| 008 Teacher attendance | SIMPLIFY | Keep: Teacher marks own day (today and previous 3 days), Manager marks and corrects in scope, Admin/Director anywhere, fixed codes (Present, Absent, Leave, Half day, Training day), monthly grid, CSV, month lock and reopen with reason. Drop: configurable status codes, per-mark history table (use audit log), optimistic-concurrency tokens, stored month rollup and its event table (compute from marks), optional Principal confirmation, absence "event" (becomes a direct call to substitution). Fallback for Principal confirmation: a note field on the mark. |
| 009 Leave | SIMPLIFY | Types Casual, Sick, Personal, Other, Loss-of-Pay. Teacher applies; Manager approves or rejects with reason; approval writes Leave marks. No balances (already true). |
| 040 Same-day flexibility | MERGE into 009 and 017 | Keep: leave dated today is auto-approved and flagged; Manager can record it for a Teacher; Manager covering a class is a substitution row with kind "Manager cover". Drop: splitting requests that run past today (apply separate requests), encrypted offline queue, the Manager-same-day-task/visit one-step logging (just create and complete in one form), the same-day month report. Rule kept: every Teacher leave day is a deduction in payroll. |
| 032 Manager attendance | SIMPLIFY | Keep: check-in and check-out with mandatory location (browser geolocation over HTTPS), day type Office/Field/Leave, Field day needs a purpose text, Manager leave decided by Director, month grid and lock feeding payroll. Drop: field-day link to the planned-activity feed (C1), photo, forgotten-check-in request workflow (Admin edits the day with a reason), missed-check-in alert job (dashboard lists "no check-in yet today"), regularisation table. Manager leave uses the same `leave_request` table as Teachers. |

### Contracts, Teachers in Schools, substitution

| Spec | Decision | What exactly, and why |
| --- | --- | --- |
| 012 School contracts | KEEP, trimmed | Contract (Teacher count, start, end, monthly fee same-for-all or per position), signing details, one active contract per School, Teacher placement to a position with history, new MoU re-maps. Drop the "MoU pending" placeholder contract and automatic interim-placement conversion (new build has no legacy). A School without a contract simply cannot be billed and shows in a "no contract" list. |
| 031 MoU documents | MERGE into 012 | Upload signed PDF as a stored file on the contract (all versions kept), `approved_by/approved_on` columns, Director or Zone Manager approves (D5). Drop the five-state approval machine (draft/pending/approved/approved-without-document/rejected): use pending and approved, document optional with a warning. |
| 012b MoU fee terms, GST | DROP | 50 Schools on a monthly per-Teacher fee (D10). Per-term/annual billing and GST are for an accountant tool. Fallback: if a School is billed per term, Admin raises a monthly invoice anyway or enters the term total as a manual invoice line with a note; GST invoices are produced in the accountant's software from the CSV export. |
| 017a Substitution | SIMPLIFY | Absence or approved leave creates a requirement row (a direct call). With only 1 to 2 Substitutes per Zone, "find an available Teacher" is a short list of that Zone's Substitutes not already booked that day. Manager assigns, reassigns, or marks "Manager covers". Record "School informed" as a checkbox and note. Drop: candidate ranking, "at most one day covered" availability rules, release/withdraw event choreography (re-open requirement if leave is revoked), "My Substitutions" page. |
| 017b Day-rate payout | MERGE into 017a and payroll | Same `substitution` row carries the day's attendance (Present/Absent, marked by Zone Manager or Admin). Pay = Present days x the Substitute's day rate (a column on the person, snapshot onto the row) as a payroll line. Drop: per-designation rate history, monthly lock for substitute days (payroll approval is the lock), mismatch/cancelled-after-paid state machine and back-pay job. A day marked after payroll is paid via an adjustment line in the next run. |
| 027 Transfer and replacement | SIMPLIFY | One `transfer_request` (Teacher, from, to, effective date, reason, optional replacement Teacher, status). Approved by Director or the leaving School's Zone Manager (D5); approval ends the old placement the day before and starts the new one in one transaction. Drop participants table, reason-category list (free text plus 4 categories), movement assignment tables, and the cross-checks with attendance (a plain rule: no change for days already marked). |

### Recruitment, training, marketing

| Spec | Decision | What exactly, and why |
| --- | --- | --- |
| 016 Campus recruitment | SIMPLIFY | Keep colleges (with contacts as columns), drives, candidates (single entry plus CSV import), interview outcome, job offer with status, acceptance creates the Teacher "in training", induction batch with attendance sign-off to make Active. Drop: superseding-offer chains and auto-expiry (a new offer replaces the old one manually), outcome history tables (audit log), pipeline dashboard beyond simple counts, offer-letter generator beyond a printable page. Training stays unpaid (D2). Fallback: offer letter in Word as today if the printable one is not liked. |
| 024 Training calendar | SIMPLIFY | Sessions (date, topic, trainer, who is invited) with an attendee list and attended flag; attendance for Training day goes to attendance. Drop: standing-pattern generator, skip/stop patterns, registration with seat limits, non-compliance exceptions, completion-history screens, reminders. Fallback: Admin copies last month's session; non-attendance is a filter on the attendee list. |
| 023 School marketing and prospects | SIMPLIFY | Prospect with stage, owner Manager, expected Teachers and proposed terms as columns; `prospect_activity` for visits, calls, meetings with status and mandatory outcome; stage list/board; Director or Zone Manager approves Final Stage, then the MoU form opens pre-filled. Drop: proposal revision history (edit in place, audit log keeps old values), overdue-notice table (a query), demand vs supply screen (a two-number tile), clash display with drives. |

### Tasks, performance, content

| Spec | Decision | What exactly, and why |
| --- | --- | --- |
| 025 Manager tasks | SIMPLIFY | Keep the one task engine: assignee, due date, status Assigned/In progress/Completed/Verified, completion note, evidence files, overdue as a query with required reason, Director sees overdue list. Other modules create tasks by calling a function. Drop: Acknowledged state, scheduled daily escalation job (Director dashboard shows tasks overdue more than N days), C1 feed, event publication. |
| 026 Observation and performance | SIMPLIFY | One `observation` record: Teacher, School, date, ratings (1 to 5 on six areas), comments, Principal feedback as text, attachments, optional result. A performance issue is a task with a "needs improvement" flag plus a follow-up observation date. "Unsuitable" is a button that opens a transfer request. Drop: Principal-permission record, plan table, root/version chains for edited observations (edit while Draft; after Submit add a note), separate Principal-feedback and issue-link tables. |
| 029 Content management | SIMPLIFY | Keep Content Contributor role, content item, numbered versions with the file, reviewer approve or return with comment. Drop the optional Manager task link and approvals-inbox tile. Size limits can be a single 100 MB cap; videos beyond that go on a shared drive with a link. |

### Money

| Spec | Decision | What exactly, and why |
| --- | --- | --- |
| 013a Salary structures | SIMPLIFY | Keep: fixed monthly salary per person, dated history, never overwritten (D12). Pay rules become constants in one `PayRules` class: LOP per unpaid day = salary / working days of that School-month; pro-rata for joining and exit; training unpaid; Teacher paid only for placed days (D13). Drop: dated pay-policy versions and screens, designation default as its own versioned table (a column), staff_employment table. |
| 013b Payroll run | KEEP, simplified | The most valuable module. Keep: Admin starts a run for an ended month, per-person line (earned pay, LOP, adjustments), recompute while Draft, Director approves, approved run is locked, corrections as next-month adjustments, payment status entered manually, CSV for the accountant. Simplify: payslip as a printable page (no PDF library); one run per month with a supplementary run replaced by "add a manual adjustment line in next month"; Substitute lines, incentives and reimbursements arrive as adjustment lines. |
| 030 Manager incentives | MERGE into 013b | When a MoU is approved, create a pending payroll adjustment of the incentive amount (setting) for the prospect owner, with a unique rule of one per School. Director/Zone Manager approval (D5) is a single "Approve" on that adjustment. Drop the incentive tables, programme, events, no-earner list and un-approval state machine. Fallback: reversal is a negative adjustment. |
| 015 Expenses | SIMPLIFY | Claim with date, amount, category (6), Zone or Head office, bills (up to 5), status Submitted/Approved/Rejected(reason)/Paid, Admin or Director approves (no limit, D11), reimbursed through payroll adjustment or marked paid. Travel/Training links become optional free-text references. Drop: Draft and withdrawn states, duplicate warning, idempotent-submit keys, links to visits/drives/batches, cost per School/Teacher computations. DEFER: budget vs actual per Zone (phase 2, a small `expense_budget` table). |
| 036 Mobile expenses | MERGE into 015 | The web form on a phone with the camera as file input. Drop HEIC/WebP conversion code (phone browsers upload JPEG), offline capture and later sync. Fallback: take the photo, submit when signal returns. |
| 022 School billing | SIMPLIFY | Monthly invoice per School from mapped Teachers at the position fee, pro-rated only for joining/transfer/exit (D6), immutable once raised. Payments with allocation to invoices (part or full); an `invoice_adjustment` row serves as credit note. Ageing buckets by School, Zone and Manager as a query. Drop: automatic overdue follow-up tasks and escalation (dashboard lists overdue invoices per Manager; the Director reviews weekly), receipt reversal chain (reverse = new negative payment), the `due_balance` view-as-contract (it may stay as a view). Keep "not billed" list. |

### Reporting and dashboard

| Spec | Decision | What exactly, and why |
| --- | --- | --- |
| 014a Reports framework | DROP the framework, KEEP two reports | Attendance/LOP and Payroll register stay as plain pages with filters and CSV. No `ReportSource`, no "not available" per-column degradation, no export-audit entries. |
| 014b School/margin/collections | SIMPLIFY | Keep Margin (fee minus Teacher pay, D14: Admin, Director, Zone Manager for own Schools) and Collections/ageing. Margin splits pay by placement days. Drop the School report (on the School page: Teachers, attendance %, current contract). |
| 014c People/expenses reports | SIMPLIFY | Keep Expenses by Zone and category. Drop Recruitment, Teacher and Manager-productivity reports: counts appear on the recruitment page, the Teacher list and the Director dashboard. Fallback: export lists to CSV and pivot in a spreadsheet. |
| 028 Management dashboard | SIMPLIFY | Director/Admin: about 10 tiles from live queries (Teachers absent today, Managers not checked in, pending approvals by kind, open substitutions, overdue tasks, overdue invoices, payroll month status, expenses pending, prospects by stage, joiners ready to place). Manager: own Schools' absences, pending leave, own tasks. Drop zone/period filter engine and the "add a tile later without rework" framework. |
| 035 Activity calendar | DROP | A month list of one's visits and tasks sorted by date (inside 023 and 025) answers "planned vs done". Fallback: Director filters the Manager's visits/tasks list. |

### Mobile track

| Spec | Decision | What exactly, and why |
| --- | --- | --- |
| 018 Android foundation | DROP (as native) | Replace with a responsive PWA. At 250 users on Android phones, a second codebase (React Native, versioning gate, store releases, device location audit on every call) costs more than it saves. Browser geolocation covers check-in. Exception: if Play Store presence or background features are required, see section 7. Drop the API Access location trail. |
| 019, 020, 021 | MERGE into web | Attendance, leave and notification pages designed phone-first. No separate spec. |
| 033, 034, 036 | MERGE into web | Same pages (check-in, visits, tasks, expenses) used on a phone. |

### Totals

Of the 44 specs and amendments listed: KEEP (trimmed) 3 (005, 012, 013b), SIMPLIFY about 24, MERGE into another 11 (005a, 038, 031, 017b, 030, 036, 040, 019, 020, 021, and 033/034 as web pages), DROP 5 (012b, 018 as a native app, 035, 039, the 014a framework). One item is DEFER (expense budgets). These counts are my own tally and approximate.

## 3. Roles and permissions

Five roles replace six. System is dropped (Admin handles technical setup; the owner does not need a separate technical identity at this size). A user may hold more than one role. Permissions are written in code.

| Capability | Admin | Director | Manager | Teacher | Content Creator |
| --- | --- | --- | --- | --- | --- |
| Users, roles, settings, holidays, working days, audit log | Yes | View audit | - | - | - |
| Zones, Schools, designations, Manager and Teacher records | Edit | Edit | Own Schools: view, edit contacts | Own profile | - |
| Teacher salary and pay data | Edit | Edit | - | Own payslip | - |
| Contracts and positions, MoU document | Create, map | Create, map, approve | Own Schools: view, map Teachers | - | - |
| Teacher attendance | Any | Any, lock | Own Schools: mark, correct | Own: mark (today and 3 days) | - |
| Leave (Teacher) | Decide, record | Decide, record | Decide in scope | Apply, cancel | - |
| Manager check-in, leave | Correct | Decide leave, view | Own | - | - |
| Substitution: assign, mark days | Yes | Yes | Own Zone | - | - |
| Recruitment (colleges, drives, candidates) | Yes | Yes, issues offers | Own Zone | - | - |
| Training sessions | Yes | Yes | View, attend | Attend | - |
| Prospects and visits | View | Yes, approves Final Stage | Own | - | - |
| Tasks | Create, verify | Create, verify | Own tasks | - | - |
| Observations | View | View | Create (own Schools) | - | - |
| Transfers | Raise | Raise, approve | Raise, approve for leaving School | - | - |
| Payroll | Process | Approve | - | - | - |
| Expenses | Approve | Approve | Raise own | Raise own (Teachers' commute and food) | - |
| Billing and collections | Yes | Yes | Own Schools: view dues, record collection if desired | - | - |
| Reports and dashboard | All | All incl. margin | Own scope; margin for own Schools; no payroll | Own | - |
| Content | Review | Review | Create | - | Create, submit own |

Justification: five roles map to five real jobs (office, owner, field, classroom, content). A configurable matrix is worth it when roles change often or customers differ. Here the owner can ask for a change and a developer edits one table. Scope is always: Admin and Director all; Manager their Zones; Teacher themselves; Content Creator their own items.

## 4. Simplified core workflows

### 4.1 Teacher onboarding and school assignment
1. Admin/Director add a College, then a Drive (date, venue, interviewers).
2. Manager or Admin add Candidates (manually or CSV) and record the outcome (selected, waitlisted, rejected).
3. Director issues an Offer (monthly salary, terms, deadline). Candidate accepts (recorded by Admin).
4. System creates the Teacher "in training" (employee row plus a salary history row dated from first placement, D2) and adds them to the next induction batch.
5. After the batch, Admin/Director mark attendance and sign-off: Teacher becomes Active (ready to place).
6. Admin/Director/Zone Manager map the Active Teacher to a vacant position in a School's current contract (one current School per Teacher). The first placement date is the pay start date.
Fallback: waiting-list and joining-ratio statistics are read from counts on the Drive page.

### 4.2 Daily attendance and leave
1. Teacher marks own day from the phone (today and up to 3 days back); the Manager can mark or correct for anyone in scope; every correction goes to the audit log.
2. Teacher applies for leave; the Manager approves or rejects with a reason. Approval writes Leave marks. Leave for today is approved at once and flagged.
3. Any absence or approved leave on a working day creates a substitution requirement (4.4).
4. After month end Admin locks the month. Reopening needs a reason.
5. Managers check in and out with location; a Field day needs a purpose; Director decides Manager leave.
Fallback for offline schools: the Teacher tells the Manager, who marks it. There is no offline sync.

### 4.3 Monthly payroll
1. Admin locks Teacher and Manager attendance for the month and confirms the School working-day figures.
2. Admin starts the run. For each person: salary in effect / working days x payable days (or minus LOP days x day value), plus adjustments (incentive, reimbursement, Substitute day pay, last month's corrections). People that cannot be computed are listed with a reason and do not stop the run.
3. Admin fixes causes and recomputes while Draft. Director reviews each line and approves; the month is locked.
4. Admin prints payslips (browser print), exports CSV for the accountant, pays outside the system and enters paid date, mode and reference.
5. Mistakes found later become adjustment lines in the next month's run.

### 4.4 Substitutes and their day-rate pay
1. An absence or approved Teacher leave creates a requirement row for that Teacher, School and date, and notifies the Manager.
2. The Manager sees the Zone's 1 to 2 Substitutes not already booked and assigns one; or marks "Manager covers"; or leaves it open (School told, with note).
3. After the day the Zone Manager (or Admin) marks the Substitute Present or Absent.
4. At payroll each Substitute gets one line: Present days x day rate. Substitutes have no salary, no placement, no login. The billing invoice to the School is unchanged by substitutes (D6).

### 4.5 School contracts, invoices and collections
1. Prospect becomes won (4.8). Admin/Director record the MoU: Teacher count, dates, fee (same or per position), signatories; upload the signed PDF; Director or Zone Manager approve it.
2. Teachers are mapped to positions (4.1). A new MoU ends the old one and re-maps with no gap.
3. Each month Admin raises one invoice per School from the mapped Teachers (pro-rated only for joining, transfer, exit). Due date = invoice date + the due-days setting.
4. Payments are recorded with amount, date, mode and reference and allocated to one or more invoices. Agreed deductions are credit adjustments.
5. Ageing (0-30, 31-60, 61-90, 90+) by School, Zone and Manager is a page. The Manager sees overdue invoices of their Schools on their dashboard; the Director sees the whole list.
Fallback for follow-up: weekly call list from the overdue page; no automatic tasks.

### 4.6 Expenses
Manager (or Admin/Director/Teacher where allowed) submits a claim with bills. Admin or Director approves or rejects with reason. Approved claims are either reimbursed through the next payroll or marked paid. Monthly expenses by Zone and category is a report.

### 4.7 Transfers
Admin, Director or Zone Manager raise a request with an effective date (today or later) and reason. Director or the leaving School's Zone Manager approves. On approval the old placement ends the day before, the new one starts, billing and pay pick up the change because both read placements by date. Replacement is two placements in one request.

### 4.8 Manager visits, prospects and tasks
1. Manager adds a Prospect and plans visits (date, purpose). After a visit they record the outcome (mandatory) and move the stage.
2. At Final Stage the Director or Zone Manager approves; the MoU form (4.5) opens pre-filled.
3. When the MoU is approved the incentive adjustment is created for the owner (4.3).
4. Tasks: the Director or Admin create tasks for Managers (and the system creates follow-ups from observations and content); the Manager works them, adds a completion note and evidence; the Director or Admin verify. Overdue tasks show on the Director's dashboard; the Manager must give a reason when completing late.

### 4.9 Reports
Six pages with filters and CSV: Attendance and LOP, Payroll register, Margin, Collections and ageing, Expenses, and an optional People list export. Dashboard tiles answer the rest.

### 4.10 Content
Content Creator (or a Manager) creates an item, uploads the file as version 1, submits. Admin or Director approves, or returns with a comment; the creator uploads version 2. History lists all versions.

## 5. Proposed reduced requirement list

Notation: R = requirement. Each is one testable statement. MVP means needed to run payroll and billing for the first month.

### MVP

Platform
- R1 Users sign in with phone or username and password; Teachers and Managers may use an SMS code.
- R2 Five fixed roles; menus and actions follow the role table; data scope follows Zone/own rules.
- R3 Admin creates users, assigns roles, deactivates, resets passwords.
- R4 Changes to people, placements, attendance corrections, money and approvals are written to one audit log.
- R5 Admin edits a small settings table (incentive amount, payment due days, substitute default rate).
- R6 A user sees a notification bell for about 10 events.
- R7 Uploaded files (bills, MoU PDFs, evidence, content) are stored with name, type, size, uploader, time.

Master data
- R8 Admin/Director maintain Zones, Schools (with contacts) and Managers; each School has one Manager.
- R9 Admin/Director maintain Teachers (status, designation, employee id, joining/exit date) and their salary history.
- R10 Designations list with suggested salary and substitute day rate.
- R11 Holidays and weekly offs; per-School month working days (one screen per School and year).

Contracts
- R12 Contract per School with fees (same or per position); one active contract per School; signing details; signed PDF; approval by Director or Zone Manager.
- R13 Map Teachers to positions with start and end dates; a Teacher has one current School; a new MoU re-maps.

Attendance and leave
- R14 Teacher marks own attendance for today and 3 days back; Manager/Admin/Director correct.
- R15 Month grid, CSV, month lock and reopen with reason.
- R16 Teacher leave request, Manager decision (reason on reject), approval writes marks; same-day leave auto-approved.
- R17 Manager check-in and out with location, day type, Director decides Manager leave, month lock.

Substitution
- R18 Absence or approved leave creates a requirement; Manager assigns a Zone Substitute or marks Manager cover.
- R19 Zone Manager/Admin mark the Substitute's day Present or Absent.

Money
- R20 Fixed monthly salary per person with dated history.
- R21 Payroll run: compute, review, approve, lock, adjustments, payment status entry, payslip print, CSV.
- R22 LOP and pro-rata computed by constants: salary / working days x unpaid days; Teacher paid from first placement; training unpaid.
- R23 Substitute pay line: Present days x day rate.
- R24 Monthly invoice per School from mapped Teachers; immutable; due date; credit adjustment.
- R25 Payments with allocation to invoices; ageing by School/Zone/Manager.
- R26 Expense claims with bills, approval, reimbursement through payroll or marked paid.
- R27 Incentive adjustment created on MoU approval, once per School, approved by Director/Zone Manager.

Dashboard and reports
- R28 Director/Admin dashboard (about 10 tiles) and Manager dashboard.
- R29 Reports: Attendance/LOP, Payroll register, Margin, Collections/ageing, Expenses.

### Phase 2

- R30 Colleges, drives, candidates, offers, induction batches, Teacher created on acceptance.
- R31 Prospects, visits with mandatory outcome, stage pipeline, Final Stage approval opening the MoU form.
- R32 Task engine with evidence, overdue reason, verification, Director overdue view.
- R33 Teacher transfers and replacements with approval.
- R34 Classroom observation form with ratings, Principal feedback text, follow-up.
- R35 Training sessions with attendee list feeding Training-day attendance.
- R36 Content items with versions and review.
- R37 Expense budget vs actual per Zone.
- R38 Printable offer letter.
- R39 CSV import for candidates and Schools.

Phase 2 is later only because MVP covers money; none of it is large.

## 6. Simplified data model

Target: 51 tables, against 146 now (about 65 percent fewer). MVP needs about 34 of them.

| Area | Tables | Count |
| --- | --- | --- |
| Platform | `app_user` (roles as a column list, theme dropped), `otp_code`, `login_session`, `setting`, `audit_log`, `notification`, `stored_file` | 7 |
| Master data | `zone`, `school`, `school_contact`, `designation`, `employee` (Teacher, Manager, staff, Substitute: kind, designation, employee id, joining/exit, optional user_id, day rate), `employee_salary` (dated history, all kinds), `holiday`, `school_month_days` | 8 |
| Contracts | `school_contract` (includes MoU approval and document reference), `contract_position`, `placement` (Teacher to position with dates; transfers add rows) | 3 |
| Attendance and leave | `attendance_mark`, `manager_checkin`, `month_lock` (kind = TEACHER/MANAGER), `leave_request` (Teacher and Manager) | 4 |
| Substitution | `substitution` (requirement, assignment, School informed, day mark, day rate) | 1 |
| Recruitment | `college`, `drive`, `candidate`, `offer`, `induction_batch`, `induction_member` | 6 |
| Training | `training_session`, `training_attendee` | 2 |
| Marketing | `prospect` (proposal terms inline), `prospect_activity` | 2 |
| Tasks | `task`, `task_evidence` (or file link) | 2 |
| Performance | `observation`, `performance_followup` | 2 |
| Transfers | `transfer_request` | 1 |
| Payroll | `payroll_run`, `payroll_line`, `payroll_adjustment` (incentive, reimbursement, substitute pay, correction; unique incentive per School) | 3 |
| Expenses | `expense_claim`, `expense_bill`, `expense_budget` (phase 2) | 3 |
| Billing | `invoice`, `invoice_line`, `payment`, `payment_allocation`, `invoice_adjustment` | 5 |
| Content | `content_item`, `content_version` | 2 |
| Total | | 51 |

What merges and why:
1. Teacher, Manager, staff and Substitute become one `employee` table with a kind. This removes three salary stores (`designation_salary`, `person_salary`, `teacher_salary_history` become one dated `employee_salary`), the polymorphic `person_kind`/`person_id` columns that cannot carry a foreign key, `staff_employment`, `employee_id_claim` and the training/payroll person tables.
2. All per-module history and event tables (`*_history`, `*_event`, `change_history_entry`: about 25) become `audit_log`. Business history that matters to money stays as rows (placements, salary history, payroll lines).
3. Teacher and Manager leave share `leave_request`; Teacher and Manager month locks share `month_lock`; both attendance month rollups (`attendance_teacher_month`, `manager_month`) go, because totals are computed from marks.
4. The five substitution tables become one row per substitute day.
5. Marketing's 15 tables shrink to two: prospect with proposal columns inline, and activity.
6. Transfer's four tables become one request plus placement rows.
7. Incentive's four tables disappear into `payroll_adjustment`.
8. MoU documents and approval events become columns plus `stored_file`.
9. `place`, `employee_id_claim`, `event_publication*`, `marketing_setting`, `billing_setting`, `pay_policy`, `manager_attendance_setting`, `attendance_calendar_setting` become rows in `setting` or constants.
10. Real foreign keys everywhere. The current schema has 79 FKs across 145 tables because modules were kept apart. In a monolith with one database, declare every FK; it removes the "inferred by column name" problem listed in `docs/er-diagrams.md` section 5.

Naming: one convention (`created_by`, `created_at`, `*_id`), money as `numeric(12,2)`, dates as `date`, timestamps UTC, display IST and DD/MM/YYYY, rupees with Indian grouping.

### Intentionally not built

| Item | Judgement at this scale | Fallback |
| --- | --- | --- |
| GST and tax invoices (012b) | Not needed for 50 Schools and a monthly flat fee unless the owner is GST-registered and must issue tax invoices from this system (open question 3). | Accountant issues tax invoice from the exported dues CSV. |
| Per-term and annual billing | 012b deferred already; monthly is the real pattern. | Raise monthly; for a termly School, raise one manual invoice. |
| Native mobile app, offline sync, encrypted offline queues | A PWA covers online use. Offline is a real need only if Schools have poor signal. | Teacher tells Manager, who marks later; Manager records visit on return. |
| Device location on every API call | Privacy-heavy and a large audit table; no business value beyond check-in. | Location only at Manager check-in and check-out. |
| Configurable permission matrix | Five stable roles. | Developer edits code table. |
| Deep audit (four views, API trail, export audit, indefinite retention) | One audit table answers "who changed this". | Database dump covers history. |
| Read models, snapshot cache, report framework, activity calendar | Live queries over thousands of rows. | None needed. |
| Event bus, outbox, schedulers | Direct calls; derived values by query. | Dashboard lists replace alerts and escalation jobs. |
| Themes and accent colours | Cosmetic. | One theme. |
| Per-designation pay structures and policy versioning | HLS has no pay scale (D12). | Per-person salary history. |
| Rotating refresh-token reuse detection, session management UI | Low threat for 250 internal users. | Admin signs a user out. |
| Seat-limited registration, pattern-generated training | 200 Teachers, monthly session. | Copy last month. |
| Auto-escalation, overdue follow-up tasks | 50 Schools; a person can review a list. | Weekly review of overdue lists. |
| Duplicate-expense detection, idempotency keys | Low volume. | Approver spots duplicates; disable the Submit button after click. |
| PDF generation library | Browser print gives a payslip PDF. | Print to PDF. |

## 7. Risks and open questions for the owner

Risks:
1. Dropping the native app is the biggest call. Browser geolocation requires HTTPS and a user prompt; some low-end phones or in-app browsers block it; no background features and no push notifications. If Managers must be tracked when the app is closed, or Teachers have no signal in Schools, the PWA is not enough.
2. SMS one-time codes cost money and depend on a gateway; if the gateway fails Teachers cannot sign in. Mitigation: Admin can set a temporary password.
3. Payroll is computed from attendance and School working days. Wrong working-day figures give wrong pay; the simplified build removes the "attendance month final" machinery, so Admin discipline (lock before run) matters. Keep run recomputation while Draft.
4. Collapsing history tables into `audit_log` makes some questions harder (for example "what was this Teacher's placement on 12 March" must come from placement rows, not audit). Keep placement and salary as real dated rows.
5. Merging the roles System into Admin removes separation of duties: an Admin can see and change everything, including audit. At this size that is probably fine, but the owner should know.
6. Dropping automatic overdue follow-ups relies on people opening a page. If that fails in practice, cash collection slows. A weekly e-mail digest is cheap to add later.
7. The current system is 40 specs deep and some behaviour is subtle (for example D6 billing from assignments, D13 paid only for placed days, "every Teacher leave day is deducted"). A rewrite will re-learn these. Port the rules, not the code, and keep the 013b/022 test cases as a regression suite.

Open questions:
1. Do Teachers and Managers have usable mobile data and a recent Android or iPhone, and is a Play Store app mandatory? If not, confirm PWA.
2. Do Schools with poor signal exist in numbers? If yes, specify the minimum offline need (only attendance marking?).
3. Is HLS GST-registered, and must it issue GST invoices to Schools? If yes, 012b returns, at least a GST number and a tax line.
4. Do any Schools pay per term or annually?
5. Is the Director's approval needed on every payroll line, or only on the run total? (Line review is cheap to keep.)
6. Is Substitute day-rate one rate for all, per designation, or per person? This draft stores it per person with a default.
7. Should Managers appear in the same payroll run as Teachers (as now), or is staff pay done elsewhere? The current system includes both; including Managers requires Manager attendance (032).
8. Is the expense budget per Zone (phase 2) really needed, or is monthly total by Zone enough?
9. Does the owner want to migrate existing data (the current system, or the August spreadsheet)? A one-time import affects the table design.
10. Does any role need to see the other's money (Zone Manager margin per School, D14)? Kept as is; confirm.
11. Is "Principal feedback" a legal or contractual need, or just a note? Kept as text.
12. Is one Admin enough, or must two Admins not approve their own changes (four-eyes on payroll)? Currently Admin processes, Director approves.

Unsure about, in this draft: the exact statuses a Teacher can hold in the old system (the specs say four stored statuses; I did not enumerate them), whether the Manager and Substitute salary rules in 013a were fully applied everywhere, and the percentages in section 2 "Totals", which are my estimate from the spec list and not a measured size.
