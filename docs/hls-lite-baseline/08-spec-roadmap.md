# Spec roadmap (21 specs, build order)

All specs are in release 1. The order only sequences the work; nothing is deferred.
Each description is ready to paste into `/speckit-specify`. Always add: "Follow docs/baseline (requirements, business rules, roles, data model, NFR)." Requirement ids refer to `01-requirements.md`.
Before specs 10 and 11, confirm the high-risk assumptions A-01 to A-05 in `02-decisions-and-assumptions.md`.

| # | Spec | Depends on | Requirements |
|---|------|-----------|--------------|
| 01 | Project skeleton and observability | none | NFR-02, 03, 04 |
| 02 | Authentication and users | 01 | PLT-01 to 05 |
| 03 | Access model: editable permission matrix and app shell | 02 | PLT-06 to 08, 15, 16, 17 |
| 04 | Audit, settings, notifications, files | 02 | PLT-09 to 14 |
| 05 | Master data: Zones, Schools, designations, employees, salary history | 03, 04 | MST-01 to 10 |
| 06 | Calendar: holidays and weekly offs | 05 | MST-11 |
| 07 | School contracts, MoU and placements | 05 | CON-01 to 07 |
| 08 | Teacher attendance and leave | 06, 07 | ATT-01 to 09 |
| 09 | Manager attendance and leave | 05, 06 | ATT-10 to 14 |
| 10 | Substitution | 08 | SUB-01 to 07 |
| 11 | Salary and payroll | 08, 09, 10 | PAY-01 to 15 |
| 12 | Billing and collections | 07 | BIL-01 to 08 |
| 13 | Expenses | 11 | EXP-01 to 06 |
| 14 | Recruitment and induction | 05, 07, 08 | REC-01 to 08 |
| 15 | Training sessions | 08, 14 | TRN-01 to 06 |
| 16 | Marketing: prospects and visits (with incentive) | 07, 11 | MKT-01 to 06, PAY-14 |
| 17 | Tasks | 03 | TSK-01 to 04 |
| 18 | Observations | 07, 17 | OBS-01 to 04 |
| 19 | Transfers | 07, 08 | TRF-01 to 04 |
| 20 | Content management | 03, 04 | CNT-01 to 04 |
| 21 | Dashboards and reports | all above | RPT-01 to 07 |

Order note: build 01 to 13 first to get a running system that can pay people and bill Schools. Specs 14 to 21 then add the remaining modules. Dashboard tiles for later modules are added by those specs; spec 21 completes the reports.

## Descriptions

**01 Project skeleton and observability.** Create the Java 25 Spring Boot backend and React PWA frontend in one repo, with Flyway, Testcontainers PostgreSQL, structured JSON logging with correlation ids, Micrometer metrics, OpenTelemetry tracing, liveness/readiness endpoints, a `BusinessClock` for IST business dates, CI, and an off-site nightly backup script. Done when a blank app builds, deploys, shows health and emits logs, metrics and traces.

**02 Authentication and users.** Users sign in with phone or username and password, or with an SMS one-time code for Teachers and Managers. Five failures lock for 30 minutes; failures reveal nothing. Sessions last about 12 hours and renew on use. Admin creates users, assigns roles, deactivates, resets passwords, sets temporary passwords and signs users out. The last Admin and oneself cannot be deactivated.

**03 Access model and app shell.** Six roles. An Admin page edits the role x permission matrix (seeded defaults from `04-roles-permissions.md`). Permission is checked on every API call, then scope in code. Admin cannot remove their own `permissions.manage`. Role-specific menus and route guards; responsive PWA shell with page size 50; IST DD/MM/YYYY and Indian number formatting.

**04 Audit, settings, notifications, files.** One audit log written by a single helper, with a search screen and 3-year retention. A key-value settings page (incentive amount, due days, overdue-task days, lockout constants). A notification bell with the 12 events in PLT-13, inserted in the causing transaction. File upload and download with access checks.

**05 Master data.** Zones, Schools with contacts (including designation such as Principal), designations with suggested salary, employees of seven kinds with statuses (Teacher: Trainee, Active, Exited), Manager-to-Zone assignments, Substitutes per Zone, and append-only dated salary history. Employees are never deleted. Each School has one responsible Manager.

**06 Calendar.** Org-wide weekly offs, holiday list, optional per-School weekly off. Provide a single "is working day" function used by all later specs.

**07 School contracts, MoU and placements.** Contract with Teacher count, dates, fee (same or per position), signatories, signed PDF versions, Pending/Approved states, approval by Director or the School's Manager. One active contract per School. Map Active Teachers to vacant positions with dated placement history; a new contract re-maps with no gap. List Schools without a contract.

**08 Teacher attendance and leave.** Teacher marks own day (today and 3 days back); Manager corrects at own Schools; Admin and Director anywhere; every correction audited. Codes Present, Absent, Leave, Half day, Training day. Monthly grid with CSV. Month lock refused while placed working days are unmarked; reopen needs a reason. Leave requests of five types, all deducted, decided by the Manager, auto-approved if dated today, only working days counted.

**09 Manager attendance and leave.** Managers check in and out with browser location and a day type (Office, Field with purpose, Leave). Director decides Manager leave. Admin edits days with a reason. Month grid, CSV and lock; lock lists unresolved working days. Dashboard list of Managers not checked in today.

**10 Substitution.** An Absent or Leave mark on a placed working day creates a requirement and notifies the Manager. Manager assigns a Zone Substitute not booked that day (typing the day rate), marks Manager cover, or leaves it open with "School informed" and a note. After the day a Manager of the Zone or Admin marks Present or Absent. Handle reopen and cancel when attendance changes. Present days feed payroll.

**11 Salary and payroll.** Implement the pay formula and examples P1 to P10 exactly. Admin runs a month after locks, recomputes in Draft, finalises (locks), Director is notified and acknowledges. Adjustment lines of six kinds. Substitute pay lines. Staff roles are paid full salary unless a manual LOP line exists. Manual payment status, CSV for the accountant, printable payslip, Teachers and Managers see only their own payslip.

**12 Billing and collections.** Monthly invoice per School from placements using the pro-rata formula and examples B1 to B5. Immutable invoices, adjustments, payments with allocation across invoices, reversal by negative payment, ageing by School, Zone and Manager, "not billed" list. Managers see dues of own Schools.

**13 Expenses.** Claim with date, amount, one of 6 categories, Zone or Head office, up to 5 bills. Approval by hierarchy, never self-approval. Approved claims are reimbursed via a payroll adjustment or marked paid. Monthly total by Zone and category.

**14 Recruitment and induction.** Colleges, drives, candidates (single and CSV), interview outcome, offers by the Director, acceptance recorded by Admin creates a Teacher in Trainee status in an induction batch. Zone Manager marks induction attendance; Admin or Director signs off and members become Active. Printable offer letter. Trainees never get pay.

**15 Training sessions.** Sessions with date, topic, trainer, venue and audience (new joiners or ongoing Teachers); several can run at once. The Zone Manager marks attendance. For Active Teachers, attended writes a paid Training-day mark and not attended writes Absent (LOP). Trainee attendance feeds induction sign-off only.

**16 Marketing: prospects and visits.** Prospects with stage, owner, expected Teachers and proposed terms; visits, calls and meetings with mandatory outcome; list and board; Final Stage approval opens a pre-filled contract form. On contract approval create the once-per-School Incentive adjustment for the earner.

**17 Tasks.** One task engine: assignee Manager, due date, four statuses, completion note, evidence files, late reason, Director overdue view, other modules create tasks by calling a function.

**18 Observations.** The Manager enters an observation of a Teacher at an assigned School: six ratings 1 to 5, comments, School feedback note, attachments, optional result. Editable in Draft; notes only after Submit. "Needs improvement" creates a follow-up task; "Unsuitable" opens a transfer request.

**19 Transfers.** Request with Teacher, from, to, effective date, reason, optional replacement. Raised by Admin, Director or Manager; approved by Director or the leaving School's Manager. Approval ends the old placement the day before and starts the new in one transaction. Days already marked cannot change.

**20 Content management.** Content items with numbered versions, review by Admin or Director with approve or return and comment, 100 MB cap, full version history.

**21 Dashboards and reports.** Director/Admin, Manager and Teacher dashboards from live queries. Reports with filters and CSV: Attendance/LOP, Payroll register, Margin (Admin and Director only), Collections and ageing, Expenses by Zone and category, People list. School page with Teachers, attendance percentage and current contract.
