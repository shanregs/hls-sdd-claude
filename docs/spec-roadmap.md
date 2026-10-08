# HLS Spec Roadmap (Constitution v2.3.1)

Revised 2026-10-05 after the Director overview deck (`docs/HLS-Operations_Management_System_Overview_For_Director.pptx`)
and `docs/HLS-core-business-flow.md`. Replaces the module sequence in `HLS SDD Implementation Plan & Deliverables
Tracker.md`.

All previous specs (001–011) and their code were removed on 2026-09-23. The old versions are still in git history
before branch `feature/000-fresh-start-rbac-ui`. We rebuild one spec at a time, through the full spec-kit path
(specify → clarify → plan → tasks → analyze → implement). Each spec MUST include the **Role & Permission Impact**
section (Constitution, Development Workflow §2). Spec numbers are identifiers, not the delivery order; the delivery
order is in "Delivery plan" below.

## 1. What the Director deck asks for, and where each part lives

The deck describes one platform with four lanes running through one lifecycle: **Recruitment** (college → assessment →
selection → offer → training), **School operations** (visits → MoU → Teacher allocation → monitoring), **Management
control** (tasks → visibility → alerts → reports → decisions) and **Attendance and payroll** (daily attendance → LOP →
salary → approval → payslip), with **Office expenses** and **School receivables** running alongside every stage.

| Deck module (slide) | Spec(s) | State today |
| --- | --- | --- |
| Manager control: areas, tasks, schedules, activity tracking (3, 8) | 005 (areas = Zones, Managers), 025 (tasks), 035 (planned-activity calendar view) | Areas done; tasks and calendar not started |
| School CRM: prospects, visits, follow-ups, MoU, active schools (3, 5) | 023, 012 | 012 implemented (PR open); 023 specified |
| Recruitment: colleges, drives, candidates, assessment, offers (3, 6) | 016 | Specified |
| Training: batches, attendance, completion, readiness (3, 6) | 016 (induction), 024 (recurring) | 016 specified; 024 not started |
| Teacher operations: assignments, substitutions, history (3, 7, 8) | 012, 017a, 027 | 012 implemented; others not started |
| Performance: observations, Principal feedback, corrective actions (3, 7) | 026 | Not started |
| Transfers: movement, replacement, history (3, 7) | 027 | Not started |
| Attendance: Teachers (9) | 008, 009, 019, 020 | Implemented |
| Attendance: **Managers** (check-in/out, Office/Field/Leave, field day linked to the calendar, Director approves leave, missed check-in alert) (9) | **032** (new), 033 (mobile) | **Gap: nothing exists** |
| Salary and payroll for Teachers **and Managers**, structures by designation, LOP, incentives, reimbursements, Director approval, month lock, payslips (10) | **013a, 013b** (re-scoped) | Not started |
| Office expenses: bill, category, area, approver, budget vs actual, cost per school and per Teacher (11) | 015 (re-scoped), 036 (mobile bill capture) | Not started |
| School receivables: invoices, fee terms, collections, ageing, follow-up and escalation (12) | **022** (re-scoped), 012b | Not started; 012 holds the contract and positions |
| Dashboard and reports: 12 tiles incl. attendance, payroll, expenses, receivables (14–16) | 028 (expanded), 014 (expanded) | Not started |
| Content: prepared, submitted, reviewed, approved (3, 14) | 029 | Not started |
| Incentive: ₹5,000 per closed School, paid through manager payroll (5, 10, 13) | 030 | Not started |

## 2. Where the specs and the deck disagree (found in this review)

1. **Manager attendance is missing.** Spec 008 covers Teachers only. The deck needs Manager check-in/out, day type,
   field-day linking, Director-approved leave and missed-check-in alerts, and it feeds Manager payroll and travel
   claims. → new spec **032**, with mobile check-in in **033**.
2. **Payroll covers two kinds of people.** Roadmap 013 was a Teacher "net salary and margin engine". The deck wants
   Teacher **and Manager** payroll, salary structures versioned by designation and effective date, academic cycle,
   pro-rata for joining, transfer and exit, LOP, incentives and reimbursements, Director approval, month lock,
   corrections as adjustments in the next run, and payslips. → split into **013a** (structures and policy) and
   **013b** (the payroll run). Margin stays as a Director-only report in 014 unless the Director drops it.
3. **Finance work is core, not "later".** The business-flow doc left payroll and billing out of its first version; the
   deck shows attendance, payroll, expenses and receivables as modules that run alongside every stage. The finance
   lane now runs in parallel with the operations lane (see Delivery plan).
4. **Receivables are invoice-based.** The deck has fee terms from the MoU (per Teacher per month, per term, or
   annual), an invoice raised and sent with a due date, credit notes, part and full payments allocated to invoices,
   ageing buckets (0–30, 31–60, 61–90, 90+), an overdue follow-up task for the School's Manager and escalation to the
   Director. The 022 input assumed a month-end bill by attendance. → **022** is re-scoped. **Decided (D6, D10):** assignment-based
   invoices with credit notes, monthly per Teacher only, no GST; the per-term and annual fee amendment (012b) is deferred.
5. **Expenses are a workflow.** Raise, upload bill, tag category, area and manager, review, Director approval, paid or
   reimbursed, posted to the monthly report; a rejection needs a reason and can be resubmitted; "Other" needs a
   reason; Travel and Fuel links to the school visit or college drive; Training Stay links to the batch; budget vs
   actual per area; cost per school acquired and per Teacher trained. Roadmap 015 was one line.
6. **Tasks are a platform.** Overdue invoices (022), follow-ups (023), corrective actions (026) and content (029) all
   create tasks with the same lifecycle (assigned, acknowledged, in progress, completed with evidence, verified;
   overdue needs a reason, follow-up, escalation). 025 must expose that as a shared interface.
7. **One planned-activity calendar.** A Manager field day must link to planned activities. Those live in four specs
   (016 drives, 023 visits and meetings, 024 training sessions, 025 tasks). They need one shared "planned activity"
   interface so 032, 015 (travel claims) and 028 read them the same way. New spec **035** is the Manager calendar view
   on top of that interface.
8. **Substitution credit.** The deck: an absence automatically raises a substitution requirement; substitute days are
   credited to the substitute, not the absent Teacher; if nobody is available the Manager covers the class. 017a and
   017b already exist; both now state this, and 008 publishes an absence event for it.
9. **Incentive flows through Manager payroll.** ₹5,000 per verified closure is added to the Manager's salary run, so
   030 depends on 013b, and 013b has an "incentives and reimbursements" input.
10. **Fee model and MoU in 012.** 012 stores the salary per position, monthly only. The deck allows per-term or annual
    fees. Not blocking the merge of 012 (monthly works, and D10 keeps version 1 monthly); the amendment 012b is deferred.
11. **Dashboard tiles missing from 028.** Attendance (Teachers absent today, Managers on leave, missed check-ins),
    Payroll (this month, computed, approved, paid), Expenses (spend vs budget, pending approvals), Receivables
    (billed, collected, outstanding, overdue Schools) were not in 028's scope.
12. **Mobile.** The deck's Manager works in the field: check-in and check-out, visit logging, task updates, bill photo
    capture. Today only Teacher attendance, leave and the foundation (018 to 020) exist on mobile. → mobile track
    **033 to 036**.

## 3. The roadmap

Status key: **Implemented** (merged), **PR open**, **In progress**, **Specified** (spec written, on a branch),
**Not started**.

| #   | Spec (branch / folder)          | Module(s)                   | Delivers (menus per role)                                                                                                                                                                                                                                                                                              | Depends on    | Status      |
| --- | ------------------------------- | --------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------- | ----------- |
| 001 | `001-identity-access`           | identity                    | Password sign-in (phone or username, any role) and one-time-code sign-in (any role), JWT access + rotating refresh, lockout, sessions, logout, password reset by SMS or email. Users with one or more of the 5 fixed roles, roles auto-detected at login. Login-history events emitted. Login screens built on the new design system foundations. | —             | Implemented (all 6 user stories, OTP rate limiting, axe checks; 36 backend + 20 frontend tests at the time) |
| 002 | `002-access-model-app-shell`    | identity, frontend shell    | Seeded role→permission matrix (module × action) and the per-user access model API (menus, actions, scope; union of roles). Router with route guards, "not authorized" page, left-nav + main layout, role dashboards (skeleton widgets), ACCOUNT → Profile / Logout. | 001           | Implemented (all 5 user stories; live-smoke-tested) |
| 003 | `003-audit`                     | audit                       | Append-only audit store (change history, user activity, login history). AUDIT menu for Admin and System: Audit Logs, User Activity, Login History, Change History. | 001, 002      | Implemented (all 4 user stories, including a 10k-row pagination load test) |
| 004 | `004-user-role-management`      | identity                    | SYSTEM / SYSTEM CONFIGURATION → User Management (create user, assign roles, activate/deactivate, reset) and Role & Permission Management (edit the matrix, audited, last-admin safeguard). Admin + System. | 002, 003      | Implemented (all 6 user stories; manual walkthrough pending) |
| 005 | `005-master-data`               | school, organization, teacher | MASTER DATA: Zone Management (the deck's "Area"); School Management (profile, zone, places, bulk import; Principal and management contacts); Manager records + Zone–Manager/School–Manager assignment and the scope queries later modules use; Teacher Management (profile, status, salary history). Manager dashboard. Teacher: ACCOUNT → My Profile. Teacher statuses stay 4; Available, Assigned, Under Review, Transferred and Replaced are derived views (D3). **Amendment A1 = spec 005a** (needed by 013a and 032): a designation list, and Manager designation, employee id and joining date and Teacher designation and employee id, as inputs to salary structures (input: `docs/spec-inputs/005a-designations.md`). The interim placement was replaced by contracts in 012. | 002, 003      | Implemented (all 9 user stories; manual walkthrough pending). A1 not started |
| 008 | `008-attendance`                | attendance                  | Teacher: MY ATTENDANCE → My Attendance, Attendance History. Manager: OPERATIONS → Teacher Attendance (assigned). Admin/Director: OPERATIONS → Attendance (grid, status codes, non-working calendar, month lock/reopen, rollups, CSV export). **Amendment A2:** publish an absence event (017a), flag LOP days for payroll, optional Principal confirmation field; salary attendance counts from the reporting date (payroll rule, no change here). Decided (D7): the Teacher marks their own day and the Manager can mark or correct any day in their Schools; Principal confirmation is an optional field the Manager records. Induction days are training-day marks with no School (016). | 005           | Implemented (merged in #4). A2 not started |
| 009 | `009-leave`                     | leave                       | Teacher: LEAVE → Apply Leave, My Leave History. Manager/Admin/Director: OPERATIONS → Leave Management (approve/reject in scope). Approved leave feeds attendance. **Amendment A3:** the seeded leave types are Casual, Sick, Personal and Other; the deck needs a Loss-of-Pay type that payroll treats as LOP (013a). Manager leave is a separate flow in 032. | 008           | Implemented (all 5 user stories; manual walkthrough pending). A3 not started |
| 010 | `010-notifications`             | notification                | NOTIFICATIONS for all business roles (Teacher: own). In-app notifications for leave decisions and attendance changes. Each later spec adds its own events (see section 5); SMS/push later. Escalation of overdue work comes with 025. | 002, 009      | Implemented (merged in #17) |
| 011 | `011-system-settings`           | settings                    | System role: SYSTEM DASHBOARD (health, users, recent audit), SYSTEM CONFIGURATION → System Settings, Security Settings (password/lockout/OTP/session policies). Also absorbs the three `hls.mobile.*` settings. Approval limits for expenses and payroll stay with their own specs (015, 013a). | 003, 004      | Not started |
| 012 | `012-school-contracts`          | schoolbilling               | The MoU between HLS and a School: number of Teachers, start/end, the salary the School pays (one amount for all, or one per position), signing details (date, School signatories, Zone Manager and/or Director). Teachers are mapped to positions; a new MoU re-maps them with no gap; a School with no MoU gets a "MoU pending" contract. OPERATIONS → School Contracts (Admin, Director; Zone Manager views and maps). Public `SchoolContracts` interface for 013, 022. A Teacher still in training cannot be mapped (D1). | 005 | **PR open** (#23, branch `feature/012-school-contracts`): 641 backend and 54 frontend test files green before the review fixes, `java-reviewer` and `database-reviewer` run and fixed, live API walkthrough done; keyboard/screen-reader pass and browser walkthrough pending (input: `docs/spec-inputs/012-school-contracts.md`) |
| 012b | `012b-mou-fee-terms`           | schoolbilling               | **Deferred (D10).** Version 1 keeps what 012 stores: a monthly salary per position, plain invoices, no GST. This amendment (billing basis per term or annual, payment due days, GST number and tax lines) is only built if the business later needs it. The one addition 022 still needs is **payment due days** on the invoice, a setting in 022 itself. | 012           | Deferred |
| 013a | `013a-salary-structures`       | payroll (policy)            | **New (split of 013).** SYSTEM CONFIGURATION or OPERATIONS → Salary Structures (Admin, Director): salary structure by designation and effective date (versioned, never overwritten), academic cycle (June to March/April), LOP policy, pro-rata rules (joining, transfer, exit), the rule that training is unpaid and salary starts on the School reporting date (D2). Teacher and Manager structures (input: `docs/spec-inputs/013a-salary-structures.md`). **Decided (D12):** a fixed monthly salary per designation, versioned by effective date (a Teacher's is the offered salary on their record); LOP deducts salary divided by the month's working days for each unpaid day; no pay components. **Decided (D13):** a Teacher is paid only for the days they are placed in a School, so vacation months follow the School calendar already in attendance; there is no separate academic-year window. | 005 (A1), 012 | Not started |
| 013b | `013b-payroll-run`             | payroll                     | **Re-scoped (split of 013).** OPERATIONS → Payroll (Admin: Process; Director: Approve). Monthly run from locked attendance (Teachers, 008; Managers, 032) plus structures (013a), LOP, incentives (030), approved reimbursements (015) and substitute credit (017b); Director approval, month lock after approval, corrections as adjustments in the next run, payslips (PDF), payment history per person, CSV export for the accountant. Virtual-thread batch (Principle VIII). **Decided (D8):** the system computes, the Director or Admin approves, payslips are issued and a CSV export is always available; payment happens outside the system and the payment status (paid, date, mode, reference) is entered manually. | 008, 009, 013a, 032; inputs: 012, 015, 030, 017b | Not started |
| 014 | `014-reports`                   | reporting                   | **Expanded.** REPORTS → Attendance (Teacher % and Manager %, LOP days), Payroll (cost), Teacher, School, recruitment, Manager productivity, Expenses (spend by area, category, cost per school and per Teacher), Collections (billed, collected, outstanding, ageing). Teacher Attendance and Leave Reports (Manager, assigned). Own reports (Teacher). Margin (School fee minus Teacher pay) per Teacher, School and month, visible to Admin, Director and, for their own Schools, the Zone Manager (D14). | 008, 009, 013b, 015, 022 | Not started |
| 015 | `015-expenses`                  | expense                     | **Re-scoped.** OPERATIONS → Expenses. Workflow: raise, upload bill/receipt, tag category, area and Manager, review, approval by the Admin or Director with **no amount limit** (D11: employees claim travel, food and purchases; the Admin or Director gets the details, and approved claims are used in salary), paid or reimbursed, posted to the monthly report; reject needs a reason and can be resubmitted. Categories: Rent and Utilities, Travel and Fuel (linked to a visit or drive), Training Stay (linked to a batch), Marketing, Office Supplies, Other (reason mandatory). Budget vs actual per area, pending approvals and ageing, cost per school acquired and per Teacher trained. Approved reimbursements feed 013b. | 005, 010; links: 016, 023, 035 | Not started |
| 016 | `016-campus-recruitment`        | recruitment, training (induction) | **High priority.** Recruitment Calendar (drives to colleges), candidates and speaking assessment, Job Offers with a package, accepted offer creates the Teacher "in training", one-month induction batch with attendance and sign-off, then active = ready to deploy. Dashboard of drives, candidates, offers and joiners per college and season; joining ratio; offer letter with tracked statuses (D4); optional food/accommodation capture, which becomes Training Stay expenses in 015; drives are planned activities (C1). Salary history starts at the first School assignment (D2); training is unpaid. | 005 (010 optional) | **Implemented on branch** `feature/016-campus-recruitment` (worktree `hls-016-recruitment`), PR not yet opened: backend (recruitment, training, TeacherRegistry, attendance A5, 012 event A4, School contacts A8), frontend, tests and demo data done. Decisions: induction is unpaid (stipend removed); the first salary entry is written automatically from the accepted offer at first placement; structured assessment scores; training stay cost lives only in 015; the dashboard funnel runs through placement with the joining ratio; a college keeps a placement officer and a principal, and a School a principal and an accountant |
| 017 | `017-substitution`              | substitution                | **Split.** **017a** workflow: an absence raises a requirement automatically, the Manager is alerted, available Teachers are checked, a substitute is assigned, the School is informed, or the Manager covers the class personally. Substitute days are credited to the substitute. **017b** substitute payout reconciliation into payroll. | 017a: 008 (A2), 010, 012; 017b: 008, 013b | Not started (017a is MVP Priority 1) |
| 018 | `018-android-app-foundation`    | mobile (React Native Android client), identity, audit | Android-first React Native app for Teacher, Manager and Director: login and logout (reusing 001), role-based main menus from the server navigation model (002), and device location captured only at the moment of an API call and recorded for every request in a new AUDIT → API Access trail (Admin and System), plus on Login History and User Activity. | 001, 002, 003, 008 | Implemented on branch `feature/018-android-app-foundation` (backend 56 new tests, web 275, mobile 197 green); manual device pass, retention period and privacy-notice wording pending |
| 019 | `019-mobile-attendance`         | mobile                      | Android screens for Teacher attendance (monthly view, mark, history), Manager Teacher Attendance, Holiday Calendar view, on 008 unchanged. Offline capture is a later spec. | 008, 018 | Implemented (merged in #15; device pass pending) |
| 020 | `020-mobile-leave`              | mobile                      | Android screens for leave: Apply Leave, My Leave History, Leave Management for Manager and Director, Pending count widget, on 009 unchanged. | 009, 018, 019 | Implemented (device pass pending) |
| 021 | `021-mobile-notifications`      | mobile                      | Android bell and list on the 010 API. | 010, 018 | **Implemented on branch** `feature/021-mobile-notifications` (worktree `hls-021-mobile`), PR not yet opened; app-only (`specs/021-mobile-notifications/`, input `docs/spec-inputs/021-mobile-notifications.md`): header bell with the unread count (30 s foreground polling, none in the background), Notifications list with Unread only, open and follow links to My Leave History, Leave Management and My Attendance on the linked month, mark one or all read, delete, clear read; 792 app tests pass; device pass pending |
| 022 | `022-school-billing`            | schoolbilling               | **Re-scoped to receivables.** OPERATIONS → School Billing. Invoice derived from the MoU fee terms (012b) and the active assignments in the period **Decided (D6):** the invoice is the contract salary for each Teacher assigned in the period (pro-rated only for joining, transfer and exit); absences do not reduce it automatically and an agreed deduction is a credit note. It is raised and sent with a due date (days after invoice date, a setting); replacements and substitutes keep billing continuous; credit notes for agreed deductions; receipts (amount, date, mode, reference) allocated to invoices, part or full, append-only with reversals; balance, **ageing 0–30, 31–60, 61–90, 90+ by School, area and Manager**; overdue invoice creates a follow-up task for the School's Manager (025) and escalates to the Director after defined days; month close by Admin after attendance lock; public interface for 013b, 014. No GST in version 1 (D10). Earlier draft: `docs/spec-inputs/022-school-billing-draft/`. | 008, 012, 010, 025 (follow-up tasks) | Not started (input rewritten for D5 to D15: `docs/spec-inputs/022-school-billing.md`) |
| 023 | `023-school-marketing-mou`      | recruitment.marketing (+ schoolbilling hand-off) | **High priority.** School prospects, marketing calendar of visits and meetings, pipeline (Prospect, Contacted, Visit, Follow-up, Interested, Negotiation, Final Stage, MoU, Active; won/lost), visit status flow and a mandatory outcome, activity plan with missed and rescheduled activities, proposed MoU terms (Teacher count, same or per-Teacher salary, fee basis from 012b), hand-off to record the signed MoU in 012, won Schools waiting for Teachers (demand) vs recruits ready to deploy (supply, 016). Visits are planned activities (C1) and field work for 032; a Final Stage School needs management review before MoU. Closing a School triggers the incentive (030). | 005, 003 (hand-off needs 012) | **Implemented on branch** `feature/023-school-marketing-mou` (worktree `hls-023-marketing`, rebased on 016), PR not yet opened: backend (prospects, visits, attachments through the new shared `files` module, pipeline, Final Stage review, proposals, win and School creation, overdue flag and notification, settings, dashboard, calendar with drives), frontend, tests, demo data, and the 012 amendments A6 (occupancy) and A7 (MoU form pre-filled from the proposal). Decisions: the deck's stages plus On Hold and Lost, with a Final Stage review by the Director or Zone Manager; the incentive goes to the prospect's owner; visit statuses with a mandatory outcome; visit attachments built now as a shared file store; follow-ups become tasks in 025 |
| 024 | `024-training-calendar`         | training                    | Weekend refresher sessions and the standing monthly training calendar: Admin/Director schedule, assign Teachers and Managers, expected vs attended, non-compliance exceptions, completion history. Sessions are planned activities (C1). | 008, 016 | Not started |
| 025 | `025-manager-tasks`             | tasks, notification         | **Platform spec.** Manager task management and accountability: assigned, acknowledged, in progress, completed with evidence, verified; overdue needs a reason, follow-up, escalation to the Director; Manager and management task lists (today, upcoming, overdue). Exposes **C2** (create a follow-up task from another module) for 022 (overdue invoices), 023, 026 and 029, and tasks are planned activities (C1). Task types from the deck: school visit, follow-up, content preparation and submission, recruitment, training, Teacher assignment, observation, Principal feedback, substitute arrangement. | 005, 010 | Not started (MVP Priority 1) |
| 026 | `026-teacher-observation-performance` | observation, performance | Classroom observation form, Principal feedback (with Principal permission recorded where required), performance issues, corrective action and follow-up observation (good: continue; needs improvement: feedback, correction, follow-up; unsuitable: transfer or replacement). | 012, 025 (C2) | Not started (MVP Priority 1) |
| 027 | `027-teacher-transfer-replacement` | assignment (schoolbilling) | Teacher transfer and replacement: School request, reason, approval, replacement Teacher, effective date, history. Transfers and replacements must keep School billing continuous (022) and pro-rate pay (013a). | 012, 026 | Not started (MVP Priority 1; approved by the Director or the Zone Manager, D5) |
| 028 | `028-management-dashboard`      | reporting                   | **Expanded.** One screen for the Director: Managers (today's tasks, visits, pending, overdue), Schools (prospects, visits, follow-ups, MoUs, active), Recruitment, Teachers (active, absent, assigned, under review, exits), Performance, Operations (substitutions, transfers, replacements), Content, KPIs (conversion, joining ratio, closures, Manager performance), plus **Attendance** (Teachers absent today, Managers on leave, missed check-ins), **Payroll** (this month: computed, approved, paid), **Expenses** (spend vs budget, pending approvals, by area), **Receivables** (billed, collected, outstanding, overdue Schools), and a Director **approvals inbox** (Manager leave, expenses, payroll, MoU, incentives, transfers). Each earlier spec ships its own widgets meanwhile. | 012, 016, 023, 025, 026, 032, 013b, 015, 022 | Not started (MVP Priority 1) |
| 029 | `029-content-management`        | content                     | Manager content tasks, versioned submissions, review and approval; the actual content file is captured (the deck's "content prepared without output submitted" gap). | 005, 025 | Not started (Priority 2) |
| 030 | `030-manager-incentives`        | incentive                   | ₹5,000 per closed School: eligibility from a verified MoU, approval, and payment through Manager payroll (013b). **Decided (D9):** the incentive is earned when the signed MoU is recorded and approved, and is added to that month's Manager payroll. **Decided (D5):** the Director or the Zone Manager approves it (a Manager may approve one they earned; the audit trail records who). | 012, 023, 013b | Not started (Priority 2; becomes core once payroll is built) |
| 031 | `031-mou-documents`             | schoolbilling               | Signed MoU document upload and the MoU approval step (approved by the Director or the Zone Manager, D5). | 012 | Not started (Priority 2) |
| 032 | `032-manager-attendance`        | attendance (manager), leave | **New.** Manager: daily check-in and check-out, day type (Office, Field: school visit, college drive, training; Leave), a field day must link to planned activities (C1), leave request to Director approval, missed check-in raises an alert, **Decided (D15):** location is required at check-in and check-out and a photo is optional. Director/Admin: Manager attendance grid, monthly lock feeding Manager payroll and travel claims. Reuses the 008 rollup and lock patterns for Managers. | 005 (A1), 010; links: 016, 023, 024, 025 | Not started (input: `docs/spec-inputs/032-manager-attendance.md`) |
| 033 | `033-mobile-manager-field`      | mobile                      | **New.** Android: Manager check-in/check-out with device location, field-day activity selection, today's plan. Uses 032 unchanged. | 032, 018 | Not started |
| 034 | `034-mobile-visits-tasks`       | mobile                      | **New.** Android: log a School visit (people met, discussion, outcome, follow-up), see and update tasks with evidence. Uses 023 and 025 unchanged. | 023, 025, 018 | Not started |
| 035 | `035-manager-activity-calendar` | reporting (read model)      | **New.** Manager and Director view of planned versus completed activity across drives (016), visits (023), training (024) and tasks (025), per Manager and area, with missed and rescheduled items. Read-only, built on C1. | 016, 023, 024, 025 | Not started |
| 036 | `036-mobile-expenses`           | mobile                      | **New.** Android: raise an expense with a bill photo, category and linked visit or drive; offline capture and later sync. Uses 015 unchanged. | 015, 018 | Not started |

## 4. Cross-cutting contracts (to put in the specs that own them)

- **C1 Planned-activity feed.** 016 drives, 023 visits and meetings, 024 sessions and 025 tasks each expose a read
  interface of planned activities (id, kind, owner Manager, date, place or School, status). 032 (field-day links), 015
  (travel claims), 035 and 028 read it. Each owning spec adds the interface in its plan.
- **C2 Follow-up task creation.** 025 exposes "create a task for a user, linked to a record, with a due date and an
  escalation rule". 022 (overdue invoice), 023 (follow-up), 026 (corrective action), 029 (content) call it.
- **C3 Approval pattern.** Approver, amount limit, status (pending, approved, rejected with reason, resubmitted), audit
  entry. Used by 015, 013b, 030, 031, 027 and 032 (Manager leave). The first spec that needs it defines it (015);
  later specs reuse it. 028's approvals inbox reads it.
- **C4 Pay inputs.** 013b reads locked attendance (008 Teachers, 032 Managers), structures (013a), incentives (030),
  reimbursements (015), substitute credit (017b), and the first-assignment date (012) for the reporting-date rule.
- **C5 Notification events** (010): see section 5.
- **C7 Shared file storage.** Built inside 023 (visit attachments) as a reusable capability: file name, type, size,
  who added it and when, never changed, removable only by Admin or Director with the removal recorded. 015 (expense
  bills), 031 (MoU documents) and 032 (check-in photos) reuse it instead of building their own.
- **C6 Conventions** kept from the constitution: ₹ with Indian grouping, DD/MM/YYYY, append-only money entries,
  corrections as new entries, scope on every list, audit on every change.

## 5. Notification events each spec adds to 010

008 absence → 017a; 017a substitute needed, assigned, School informed; 022 invoice due, overdue (Manager), escalated
(Director); 023 visit due, missed; 025 task assigned, overdue, escalated; 026 observation due; 027 transfer or
replacement requested and decided; 032 missed check-in, Manager leave requested and decided; 015 expense awaiting
approval, rejected; 013b payroll ready for approval, approved, payslip issued; 030 incentive approved.

## 6. Changes required to existing specs and plans

| Spec | Change | When |
| --- | --- | --- |
| 005 | **A1** Manager designation, employee id, joining date; Teacher designation. Needed by 013a and 032. | Before 013a and 032 |
| 008 | **A2** absence event, LOP flag per day, optional Principal confirmation (D7: no change to who marks). | Before 017a and 013b |
| 012 | **A4** publish an event when a Teacher is first mapped to a School position, so 016 can write the Teacher's first salary entry from the accepted offer without 012 depending on recruitment. Small; ships with 016. | Done on the 016 branch |
| 009 | **A3** add a Loss-of-Pay leave type (seeded types are Casual, Sick, Personal, Other); no other change. | Before 013b |
| 010 | New events per section 5; escalation arrives with 025. | With each spec |
| 011 | Absorb the `hls.mobile.*` settings; no payroll or expense limits (they stay with 013a and 015). | Whenever |
| 012 | Merge PR #23 as it is (monthly fees work); fee terms stay monthly (D10, 012b deferred); MoU approval and document stay in 031; the "assigned versus active once the Teacher reports" question is resolved by D2 (the first assignment is the reporting date). | Merge now |
| 013 | Split into 013a and 013b; Manager payroll added; margin moves to a report (014). | Before specify |
| 014 | Add the deck's reports (section 3 row). | When 014 starts |
| 015 | Re-scope as the full workflow; add budgets per area. | Before specify |
| 016 | Add C1 interface; Training Stay hands cost data to 015; training is unpaid. Clarify next. | In the clarify |
| 017 | Absence is automatic; substitute credit; Manager covers if nobody is available. | Before specify |
| 018 to 020 | No change; the mobile track (033, 034, 036) builds on them. | — |
| 022 | Rewrite the input for assignment-based invoices (D6), credit notes, ageing, follow-up tasks (025) and escalation; no GST (D10). | Before its specify |
| 023 | Add C1 interface; visits as field work for 032; proposed terms are the monthly salary per position. Clarify next. | In the clarify |
| 025 | Make it a platform: C1 and C2 interfaces, escalation to the Director. | Before specify |
| 028 | Add the four finance and attendance tiles and the approvals inbox. | Before specify |
| 030 | Depends on 013b; earned on MoU approval (D9). | Before specify |

## 7. Delivery plan

The business-flow doc's MVP ordered operations first; the deck makes finance a core lane. Five lanes now run in
parallel, each owned by a worktree or branch, merged one spec at a time with a rebase between merges.

| Wave | Operations lane | Recruitment and School lane | Finance lane | Management and mobile |
| --- | --- | --- | --- | --- |
| **0 (done)** | 001–005, 008–010 | — | — | 018–020 |
| **1 (now)** | merge **012** (PR #23); settle D5 to D14 | **016** and **023**: clarify, plan, tasks, implement | write 013a/013b, 022, 015 inputs; do A1 (005) | 025 specify (platform for C2) |
| **2** | 017a substitution (after A2), 026 observation, 032 manager attendance | 016 and 023 finish; hand-off 023 → 012 | 013a salary structures, 022 receivables | 025 implement |
| **3** | 027 transfer and replacement, 035 activity calendar | 024 training calendar | 013b payroll run (needs 032), 015 expenses, 030 incentives | 028 dashboard, 033 mobile check-in |
| **4** | 017b payout reconciliation | 029 content, 031 MoU documents | 014 reports | 034, 036 mobile, 021 mobile notifications, 011 settings |

Critical path to a payroll the Director can approve: **012 → A1 → 013a → 032 → 013b**, with 008 (done) and 015 and 030
as inputs. Critical path to the Director dashboard: **012, 016, 023, 025, 026, 032 → 028**.

Merge conflicts to watch: almost every spec adds permission keys and menu items to the seeded matrix and navigation
model; merge one at a time and rebase between merges. 016 reads and writes Teacher records of 005 through its public
interface only; 023 reads Schools and Zones of 005 and contracts of 012 through public interfaces only.

## 8. Decisions

Taken from the business-flow doc (2026-10-05):

- **D1** A Teacher still in training cannot be mapped to a School position (012). Induction sign-off makes the Teacher
  active, which is the gate. A trained Teacher may then wait unplaced for any length of time; they receive no salary
  until they are mapped to a School (D2).
- **D2** Salary starts when the Teacher reports to the School (first School assignment), not at offer acceptance. The
  deck adds: training is unpaid, and attendance counts for salary only from that date.
- **D3** The 4 stored Teacher statuses stay; Available, Assigned, Under Review, Transferred, Replaced are derived views.
- **D4** Spec 016 generates the offer letter and tracks its status.

Taken with the Director on 2026-10-05 (answers to the deck's "To confirm before build" and items found in this review):

| # | Decision | Affects |
| --- | --- | --- |
| D5 | The **Director or the Zone Manager** approves a signed MoU, a Teacher transfer or replacement, and a Manager incentive. A Manager may approve an incentive they earned; the audit trail records the approver. | 031, 027, 030 |
| D6 | The School **invoice is derived from active assignments** in the period (contract salary per assigned Teacher, pro-rated only for joining, transfer and exit). Absences are not deducted automatically; an agreed deduction is a **credit note**. This replaces the earlier "bill by captured attendance" idea. | 022 |
| D7 | The **Teacher marks their own attendance**; the Manager can mark or correct any day in their Schools; Principal confirmation is an optional field the Manager records. No change to what is built. | 008 A2, 017a |
| D8 | Payroll is **computed, approved (Director or Admin) and issued as payslips in the system**, with a CSV export. **Payment happens outside** the system; the payment status is entered manually. | 013b |
| D9 | The ₹5,000 incentive is earned **when the signed MoU is recorded and approved**, and paid in that month's Manager payroll. | 030, 013b |
| D10 | Fee model for version 1: **monthly per Teacher only, no GST.** Per-term or annual fees and GST are deferred (012b). | 012, 022 |
| D11 | Expenses have **no approval limit**. Employees claim travel, food and purchases; the **Admin or Director** reviews and approves; approved claims are tracked and used in salary as reimbursements. | 015, 013b |
| D12 | **Fixed monthly salary per designation**, versioned by effective date. **LOP = salary divided by the month's working days, per unpaid day.** No pay components. | 013a, 013b |
| D13 | Teachers are **paid only for days they are placed in a School**; vacation months follow the School calendar. No separate academic-year pay window. | 013a |
| D14 | A **margin report** (School fee minus Teacher pay) is wanted, visible to Admin, Director and the Zone Manager for their own Schools. | 014 |
| D15 | Manager check-in and check-out **require the device location**; a **photo is optional**. | 032, 033 |

Consequences already built into the rows above: 022 no longer depends on 012b or on attendance; 012b is deferred;
013b needs a manual "payment status" record per payslip; 015 has a single approver level; 014 scopes margin by Zone
Manager; 030 depends on the MoU approval step (031) or, until 031 exists, on the MoU being recorded in 012.

Still open: none from the deck. Questions will come up in each spec's `/speckit-clarify`.

## 9. Notes

- Each spec adds its own permission keys to the seeded matrix and its own menu items to the server-provided navigation
  model. Every spec after 002 plugs into the shell; none of them hard-codes menus.
- Every role dashboard starts as a skeleton in 002. Later specs add widgets.
- 005 consolidates zones/schools, manager scoping and teachers into one Master Data module (decision 2026-09-24). The
  deck's "Area" is a Zone.
- 018 is the first mobile spec. It adds no new business rules: the app is another client of the same APIs, scope rules
  and permission matrix as the web app. A System-only heat map of action locations (web admin area) and offline
  capture, geo and photo evidence (deferred from 008) land in later mobile or admin specs.
- 016 and 023 replace the earlier combined HR-calendars spec. Campus recruitment (016) supplies the Teachers; school
  marketing (023) wins the Schools and hands each signed MoU to 012. Recurring training moved to 024.
- Marketing team members use the five fixed roles (Admin, Director, Manager); a dedicated role would be a constitution
  change, raised as a `/speckit-clarify` question in 023. The Principal is not a system user; Principal confirmation
  and feedback are recorded against a School contact.
- 021 (mobile notifications) is in its own worktree `hls-021-mobile`. 012b, 013a and 013b, and 017a and 017b, are
  sub-specs of one number so the numbering stays stable.
- The deck is a Director presentation. Where it and the business-flow doc differ (finance priority, invoice basis),
  this roadmap follows the deck and records the question as a decision above.
