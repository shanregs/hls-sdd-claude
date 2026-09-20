# HLS Teacher Management System — Requirements

2026-09-18 · @Someone

Requirements derived from the operational spreadsheet *Aug-26-HLS-School-attendance-salary-expense.xlsx*, which HLS currently uses to run attendance, school billing, teacher payouts, and staff expenses manually in Excel.

## 1. Overview & Purpose

HLS is a staffing and training company that supplies English teachers to schools on a contract basis. HLS recruits and onboards teachers, places them at partner schools, tracks their attendance both in the classroom and during HLS training programs, collects a per-teacher fee from each school, and pays each teacher a salary that HLS itself sets (typically lower than what the school pays, with the difference retained by HLS as margin).

Today this entire operation runs on a single monthly Excel workbook maintained by a System Assistant, with separate sheets for staff attendance, teacher salary computation, school payments received, and manager/staff expenses. The workbook shows the operation currently spans 60+ teachers, 8+ managers/coordinators, and 50+ partner schools, with monthly cash flow into the several-lakh range on both the receivable (school) and payable (teacher) sides.

The goal of this system is to digitize and automate this workflow end-to-end — attendance capture, salary computation, school billing, teacher payouts, training tracking, and expense management — through a responsive React web application (desktop/laptop/tablet) and a companion mobile app (for teachers, managers, and the director), backed by a Java Spring Boot modular-monolith backend and a relational database (MySQL/MariaDB/PostgreSQL).

## 2. Actors & Roles

| Role | Access | Key responsibilities in the spreadsheet today |
| --- | --- | --- |
| Director | Web + mobile, full access | Oversees all schools, managers and finances; approves salary offers and margins |
| Manager / Area Coordinator (e.g. the named managers seen in the sheets — Suresh, Nandhini, Karthikpriya, Amudhapriya, Sangeetha, Yashoda, Mangai, Punitha) | Web + mobile | Manages a group of teachers/schools, marks attendance, collects and hands over school payments, disburses teacher salaries, logs personal daily expenses, arranges substitute teachers |
| System Assistant / Admin (e.g. Sathyapriya — "IT SUPPORT") | Web only | Maintains master data (schools, teachers, contracts), consolidates the monthly attendance/salary/expense sheets, runs payroll calculations, produces reports |
| Accounts Officer / A.O. (e.g. Suresh in that role) and A.D. (e.g. Ilangovan) | Web | Reconciles school receivables vs. teacher payables, tracks bank/cash/GPay/cheque payment modes, manages float/balance carried forward |
| Teacher | Mobile only | Marks own attendance, views assigned school, views salary/payslip, attends and confirms training sessions, submits expense claims (commute, food) |

All roles other than the System Assistant/Admin need the mobile app; the web application is used by Director, Managers and the System Assistant from laptop, tablet or desktop. Role-based access control (RBAC) must restrict a Manager to their own assigned teachers/schools, while the Director and System Assistant see the full organization.

## 3. Attendance Management

The existing "STAFF ATTENDANCE REGISTER" is a per-month grid of every teacher (row) against every calendar day (column), with a status code per cell. The system must replicate and digitize this as a daily attendance capture screen/mobile check-in, with monthly and per-teacher rollups.

**Attendance codes observed in the sheet** (the system must support at least these, as a configurable enum):

- `P` — Present (taught at the assigned school that day)
- `L` — Leave
- `T` — Training day (attended an HLS training session instead of / in addition to school)
- Blank — non-working day (e.g. Sundays, holidays)
- `S` — seen occasionally, likely Sick leave or Substitute-covered (needs confirmation — see Open Questions)

**Per-teacher monthly rollups the system must compute automatically** (currently done manually per column in the sheet): Training Days (Total) vs. Training Days (Attended); Days Worked in School; Days Leave (in school); Overall Working Days (denominator, e.g. 26 in August); Final Total Attendance (a weighted count, which can include half-days — the sheet shows fractional values like 25.5 and 18.5).

**Functional requirements:**

1. Teachers mark their own daily attendance from the mobile app (with geo-tag/photo or school check-in code as an optional integrity control), or a Manager marks it on their behalf.
2. Attendance must be tagged to a specific school/branch assignment and a specific contract period, since a teacher can move between schools mid-month (seen in the data via "Handled By" changes).
3. Half-day attendance must be supported (fractional attendance values appear in the source data).
4. Training-day attendance is tracked separately from school attendance but rolls into the same monthly total.
5. A Manager or Admin can view/edit attendance for their teachers, with an audit trail of who marked/edited each entry and when.
6. Monthly attendance must lock after payroll is run for that month, with an explicit re-open/correction workflow.
7. Attendance data feeds directly into the salary computation module (Section 5) — no manual re-entry.

## 4. School Payment (Receivables) Management

The "SCHOOL PAYMENT" sheet tracks money HLS collects from each contracted school, per school per month.

**Fields observed:** School name, expected payment date, receiver (the manager who collected it), payment mode (Bank/Cash), amount received, number of teachers/faculties covered by that payment, running balance/shortfall, and free-text comments (e.g. partial payments, reasons for delay).

**Functional requirements:**

1. Each school is a master record with a contracted per-teacher rate (or a lump sum for N teachers), billing cycle (currently monthly), and one or more assigned teachers.
2. The system must generate an expected-receivable amount per school per month automatically, based on active teacher assignments and contracted rates — rather than a manager typing in a number.
3. Managers record actual payments received against that expected amount, capturing date, mode (Bank/Cash/Cheque/UPI), and receiver.
4. The system must compute and surface an outstanding balance per school automatically (the sheet currently tracks this as a manual "BALANCE" column, which several rows show as unresolved/carried forward).
5. Partial payments and part-payments split across dates (seen in comments like "4500+9000") must be supported as multiple payment entries against one invoice/month, not a single field.
6. Director/Admin dashboard should show total expected vs. total collected vs. outstanding, both organization-wide and per manager/per school.
7. Automated reminders/alerts for schools with overdue payments beyond a configurable threshold.

## 5. Teacher Salary & Payout

The attendance sheet's right-hand block already encodes the full salary computation HLS does per teacher per month; this must become an automated payroll engine.

**Observed computation logic:**

1. `School Salary (As per Contract)` — the fixed monthly rate the school has agreed to pay per teacher.
2. `FROM SCHOOL` — the actual amount received that month, pro-rated by attendance (e.g. a teacher present only 3 of 26 days shows a much lower "FROM SCHOOL" figure).
3. `HLS Salary Offered` — the full monthly salary HLS has offered the teacher (independent of, and usually lower than, the school's contracted rate).
4. `Net Salary (₹)` = `HLS Salary Offered ÷ Overall Working Days × Final Total Attendance` — i.e. attendance-prorated pay, confirmed by cross-checking the sheet's numbers (e.g. 17000 ÷ 26 × 22 = 14400).
5. `Net Salary Rounded` — the computed net salary rounded to the nearest whole rupee (or nearest 50, per several examples).
6. `Extra Amount` — the rounding difference retained/adjusted.
7. `Margin/Teacher` = `School-provided salary (FROM SCHOOL) − HLS Salary Offered` — HLS's profit per teacher per month; this can be **negative** when the school's payment record is missing or delayed for that month (seen as negative margins where FROM SCHOOL is blank/None), which the system must flag rather than silently show as a loss.
8. `Mode` and `Date paid` — how and when the teacher was actually paid (Bank/Cash/GPay/Cheque, with free-text notes on split payments across managers).
9. `Manager` — who disbursed/is accountable for that payment.

**Functional requirements:**

1. The payroll engine must compute Net Salary automatically from attendance data and the teacher's HLS-offered salary — no manual formula re-entry each month.
2. Support configurable rounding rules (nearest rupee / nearest 50) with the rounding delta tracked as its own ledger line, not lost.
3. Automatically compute and report per-teacher and aggregate margin (school rate vs. HLS payout), flagging any teacher/month where the school payment is missing, so margin can't silently go negative without visibility.
4. Support multiple payment modes and partial/split disbursement (e.g. one teacher's salary paid by two different managers in two tranches, as seen in the comments).
5. Maintain a payslip per teacher per month (auditable, downloadable/viewable from the teacher's mobile app).
6. Support non-teaching staff and manager salaries on the same payroll run (the sheet includes fixed manager salaries such as ₹20,000–₹30,000 with no attendance-based proration).
7. Track total organization-wide payable vs. paid, with a running total analogous to the sheet's TOTAL row (e.g. ₹1,409,500 school salary vs. ₹920,450 HLS payout vs. ₹157,986 margin for the sample month).

## 6. Manager & Staff Expense Management

Two overlapping expense sheets exist today: a daily grid ("EXPENSES", one row per calendar day, one column per manager, plus a `FUEL` and `DAILY TOTAL` column) and a detailed ledger ("Manager-Details of expenses") with SlNo, Date, Manager, Expense Nature, Amount and Comments.

**Functional requirements:**

1. Each manager/teacher can log an expense claim with date, category (e.g. Transport/Commute, Food, Fuel, Substitution payout, Observation visit, Miscellaneous), amount, free-text note, and optional receipt photo upload from the mobile app.
2. The system must auto-total expenses by manager per day, per month, and organization-wide (replacing the manual DAILY TOTAL column), and support a running "total expenses" figure like the sheet's own summary cell.
3. Some "expense" entries are actually substitution payments made in cash to a stand-in teacher (e.g. "SUBSTITUTION FOR SREEKEERTHI") — the system should let these be tagged and reconciled against the Substitution module (Section 7) rather than sitting only as an unlabeled expense line.
4. A large one-off cost (e.g. a ₹25,000 or ₹3,700 entry, or a vehicle-related “HYUNDAI” service note) should be distinguishable from routine daily commute/food expenses — support an approval workflow for expenses above a configurable threshold.
5. Director/Admin view: expense trends per manager, per category, and per month, to control costs (commute and food are the dominant small daily categories in the current data).
6. Expenses should be exportable/reportable alongside the payroll and school-payment numbers for a consolidated monthly P&L.

## 7. Substitute Teacher Management

The workbook has a dedicated (currently blank/placeholder) "Substitute" sheet, and the expense ledger separately shows ad-hoc substitution payments (e.g. "SUBSTITUTION FOR SREEKEERTHI", "SUBSTUTION FOR MADHUMITHA"), indicating substitution is tracked informally today.

**Functional requirements:**

1. When a regular teacher is on leave, a Manager can assign a substitute teacher (from the existing teacher pool or an external stand-in) to a specific school for specific date(s).
2. The substitution must record which school, which original teacher, which substitute, dates covered, and the payout agreed for the substitute.
3. Substitution payouts must flow into the payroll/expense ledger automatically, tagged by school and by the original teacher's contract, rather than as an unlabeled cash expense.
4. The system should track substitution frequency per teacher/school, since repeated substitution needs can signal a staffing or attendance problem worth surfacing to the Director.

## 8. Teacher Training & Onboarding

The attendance sheet's `T` code and the "Training Days (Total)" / "Training Days (Attended)" columns confirm that training days are tracked as part of regular monthly attendance, per the project's two named training programs: recurring **weekend refresher training** for existing teachers, and a **one-month induction program** for newly recruited teachers before they are placed at a school.

**Functional requirements:**

1. A training calendar module where Admin/Director schedule weekend training sessions and the one-month onboarding batches, each with a topic, trainer, and venue (physical or virtual).
2. New-recruit onboarding workflow: candidate application → interview/selection → enrollment in the one-month training batch → daily attendance and assessment during that month → certification/sign-off → eligibility to be contracted to a school.
3. Existing teachers are notified of and register for weekend training sessions via the mobile app; attendance is marked the same way as school attendance and rolls into the monthly "Training Days (Attended)" figure used in payroll.
4. Track training completion history per teacher (which sessions attended, any certifications), viewable by the Director and the teacher.
5. Optionally support a stipend or allowance for training days, separate from the school-attendance-based salary, since training days currently do not have a corresponding school payment.
6. In addition to weekend refresher sessions, the system must support a **recurring monthly training calendar** — a standing session (or set of sessions) scheduled every month, not just ad hoc weekend events, so training cadence doesn't depend on someone remembering to schedule it.
7. Admin/Director can **assign specific teachers and managers** to a given monthly session (not registration-only) — assignment creates an expected-attendance obligation for that person, distinct from the open weekend sessions anyone can register for.
8. **Managers are attendees too**, not just organizers — a monthly session can require manager attendance alongside teachers, and manager training attendance is tracked the same way as teacher attendance.
9. The system must track **assigned vs. attended** per person per monthly session, and surface non-compliance (assigned but did not attend) to the Director/Admin as an exception, mirroring the exception reporting in Section 10.

## 9. Teacher Recruitment (Campus Hiring)

HLS recruits new teachers partly through campus recruitment drives — visiting colleges to interview and make offers to candidates, who then go through the one-month induction (Section 8) before being contracted to a school. This gets full workflow treatment, not just a calendar, per an explicit decision to track outcomes end to end.

**Functional requirements:**

1. A **Campus Recruitment Calendar** where Director/Managers schedule drives: college/institution name, date(s), venue, and which HLS staff (interviewers) are attending.
2. Each drive records the **candidates registered/interviewed**, with a per-candidate interview outcome (selected / waitlisted / rejected).
3. Selected candidates get a **Job Offer** record: offered role, offered salary, offer date, response deadline, and status (offered → accepted / declined / expired).
4. An accepted candidate becomes a **Teacher record in "Recruited" status**, automatically linked to the next available one-month onboarding batch (Section 8) — no manual re-entry of candidate details into the Teacher record.
5. Director/Admin dashboard: drives scheduled vs. completed, candidates interviewed vs. offered vs. joined, per college and per recruitment season.
6. Historical record of past drives per college, useful for deciding which colleges to revisit.

## 9a. Marketing & Sales Calendar (sub-package of recruitment)

Beyond teacher recruitment, HLS also runs school-facing marketing and sales activity to win new contracts. This lives as a sub-package under the recruitment module (recruitment.marketing) rather than its own bounded context, and is scoped intentionally lightweight for v1: a calendar with outcome logging, not a full CRM.

**Functional requirements:**

1. A **Marketing/Sales Calendar** where Director/Managers log planned outreach activity: school/prospect name, activity type (visit, call, proposal meeting), date, and owner.
2. Each entry records an **outcome**: converted to contract (linked to the School record once signed), follow-up needed (with a next-action date), or declined.
3. A simple pipeline view — prospects by stage (contacted → meeting held → proposal sent → won/lost) — enough to see what's in progress without full CRM features.
4. This module can be deepened later if HLS's sales volume grows enough to justify it; v1 is a log with outcomes, not a sales-automation tool.

## 10. Core Data Entities

Derived from the columns actually present across the sheets:

- **User** — id, name, role (Director/Manager/Admin/Teacher/AccountsOfficer), contact, credentials, linked Teacher/Manager profile.
- **Teacher** — id, name, contact, bank details, current school assignment, HLS-offered salary, status (in training / active / on leave / exited).
- **School** — id, name/branch, contracted rate per teacher, billing contact, payment mode preference, assigned teachers, assigned manager.
- **Contract** — links Teacher + School + Manager, start/end date, contracted school salary, HLS-offered salary (so the margin is computed per contract, and history is preserved when a teacher moves schools).
- **AttendanceRecord** — teacher id, date, status code (Present/Leave/Training/Substitute), value (1, 0.5 for half-day), marked-by, school id at time of marking.
- **TrainingSession** — id, type (weekend / one-month onboarding), date(s), topic, trainer, venue, enrolled teachers.
- **SchoolPayment** — school id, month, expected amount, date received, amount received, mode, receiver (manager), balance, comments.
- **TeacherPayout (Payslip)** — teacher id, month, school salary, from-school amount, HLS salary offered, net salary, rounded net salary, extra amount, margin, mode, date paid, disbursing manager.
- **Substitution** — original teacher id, substitute id/name, school id, dates covered, agreed payout, linked payout/expense record.
- **Expense** — manager/teacher id, date, category, amount, comments, receipt attachment, approval status.
- **AuditLog** — entity, action, actor, timestamp, before/after values — needed given how often the source sheets show manual corrections and free-text reconciliation notes.
- **CampusRecruitmentDrive** — college/institution, date(s), venue, interviewers attending, linked candidates.
- **Candidate / JobOffer** — candidate details, interview outcome, offer terms (role, salary, deadline), acceptance status, link to the Teacher record once hired.
- **MarketingActivity (recruitment.marketing sub-package)** — prospect/school name, activity type, date, owner, outcome, link to School once converted to a contract.

## 11. Reporting & Dashboards

The workbook's TOTAL rows and free-text reconciliation notes show that consolidated visibility is currently manual and error-prone. The system should replace this with:

1. Director dashboard: total school receivables vs. collected vs. outstanding, total teacher payables vs. paid, aggregate margin, headcount by status (active/training/on leave), all filterable by month, manager, and school.
2. Manager dashboard: their own teachers' attendance status today, pending school collections, pending teacher payouts, their own logged expenses.
3. Teacher/mobile view: their own attendance calendar, latest payslip, upcoming training sessions, and expense claim status.
4. Monthly payroll and billing reports exportable as PDF/Excel (a familiar handover format for schools and for the Director's own review), mirroring the current sheet layout so the transition feels familiar.
5. Exception reports: teachers with attendance below a threshold, schools with overdue payments, teachers with negative or unusually low margin, expenses pending approval.

## 12. Technical & Non-Functional Requirements

**Frontend:** React (responsive) for the web app, usable from desktop, laptop and tablet by Director, Managers and the System Assistant; a mobile app (React Native, sharing components/logic with the web React app) for Teachers, Managers and the Director, covering attendance check-in, payslip view, training registration and expense claims. The mobile app ships Android-first for the initial release, with iOS support planned as a follow-on phase once the Android app is stable in production.

**Backend:** Java (25+) with Spring Boot (4.1.1-based), organized as a **modular monolith** — one deployable Spring Boot application with hard package boundaries per bounded context (`identity`, `roster`, `schoolbilling`, `attendance`, `payroll`, `expense`, `training`, `substitution`, `recruitment` with a `marketing` sub-package), cross-module calls only through a module's public interface, enforced by ArchUnit rules that fail the build on violation (e.g. attendance finalization triggering payroll calculation happens via an in-process call to payroll's public interface, not a message broker). No microservices by default — extraction of a module into its own service is deferred until scale or team size actually justifies it.

**Concurrency/multithreading considerations:** payroll computation is triggered on demand by Admin/Director from the web or mobile app — there is no fixed monthly schedule, since attendance corrections can arrive late. Once triggered, computing Net Salary and Margin across all active teachers is a natural candidate for parallel batch processing (e.g. a virtual-thread-per-task executor on Java 25+) rather than a sequential per-teacher loop, since it's a read-heavy, independent-per-teacher computation; monthly attendance rollups follow the same on-demand, parallelized pattern. Care is needed around idempotency (a re-triggered run must not double-process) and row-level locking when multiple managers concurrently record payments/attendance for overlapping teachers.

**Database:** MySQL, MariaDB or PostgreSQL (final choice can be deferred; schema should stay portable — avoid vendor-specific SQL where practical). Given the reconciliation-heavy, auditable nature of payroll and payments, financial tables (SchoolPayment, TeacherPayout, Expense) should favor normalized relational modeling with explicit audit/history tables over soft in-place overwrites, matching the many manual correction notes seen in the source spreadsheet.

**Authentication & Authorization:** JWT-based stateless API auth (short-lived access token + rotating refresh token); OTP-based mobile login via an India SMS gateway (rate-limited per number) for teachers, password (+ optional MFA) login for Director/Manager/Admin on web; RBAC combined with attribute-based scoping so a Manager's visible data is filtered to their assigned teachers/schools, not just role-gated; account lockout and revocable sessions/devices.

**Security:** BCrypt/Argon2 password hashing; encryption at rest for teacher bank details; secrets (JWT signing key, DB credentials, SMS gateway key) kept out of source control via environment configuration, not a vault at this scale; standard API hardening (input validation, locked-down CORS, rate limiting on public and OTP endpoints, e.g. via bucket4j); secure token storage on mobile (Android Keystore / iOS Keychain); a compliance note for India's Digital Personal Data Protection Act (DPDP) 2023, given the personal and bank data stored — consent capture and a defined retention/deletion policy.

**Observability:** structured JSON logging with a correlation/request ID propagated via MDC across module boundaries (useful given a single request can cross attendance → payroll → audit in the modular monolith); Spring Boot Actuator + Micrometer feeding Prometheus/Grafana (or CloudWatch) with health/readiness probes gating container restarts; alerting on disk space, DB pool exhaustion, and failed payroll batch runs; an error tracker (e.g. Sentry) to catch silent mobile-sync failures.

**Other non-functional requirements:**

- Role-based access control and per-manager data scoping (Section 2).
- Data migration path: a one-time import tool to load the existing Excel history (attendance, payments, expenses) into the new schema, since the organization has months of historical data worth preserving.
- Offline-tolerant mobile attendance capture (schools may have poor connectivity), syncing when back online.
- Audit logging on all financial and attendance edits (Section 9).
- Currency: Indian Rupees (₹) throughout; dates in DD/MM/YYYY to match existing usage.
- Readable, testable code with clear separation of computation logic (e.g. payroll formulas) from persistence and API layers, so the salary/margin formulas in Section 5 can be unit-tested independently of the database.
- Schema migrations managed with Flyway or Liquibase from day one — never hand-run SQL against financial tables.
- Idempotency keys on payout/payment-recording endpoints, so a retried request can't double-disburse a teacher's salary.
- OpenAPI/Swagger-documented API contract for the React web and React Native mobile clients.
- A file storage strategy for expense receipts and documents — local disk backed up alongside the database initially, with self-hosted MinIO (S3-compatible) as a growth path.
- Push notifications (Firebase Cloud Messaging) for training reminders and payout confirmations, tying into the monthly training assignment requirement.
- Time zone handling: store all timestamps in UTC, display in IST throughout.
- Integration tests run against a real Postgres/MySQL via Testcontainers, not an in-memory substitute, given how formula- and query-heavy the payroll/margin logic is.

## 13. Open Questions & Assumptions

- The attendance code `S` appears occasionally alongside `P`, `L` and `T` — assumed to mean Sick leave or a Substitute-covered day; needs confirmation of its exact meaning and whether it should reduce or preserve attendance-based pay.
- The formula `Net Salary = HLS Salary Offered ÷ Overall Working Days × Final Total Attendance` was reverse-engineered from the numbers and matches most rows; a few rows (e.g. teacher #12 "INDRA", #50 "RAGAVI") show negative margin because `FROM SCHOOL` is blank — confirm whether margin should show as negative, zero, or "pending school payment" in those cases.
- Fixed-salary staff (managers, A.O., A.D., IT support) appear in the same sheet with no attendance proration — confirm whether they should be modeled as a separate "Staff" entity/payroll track rather than the Teacher/Contract model.
- The "Substitute" sheet in the source file was empty/placeholder — substitution requirements in Section 7 are inferred from expense-ledger comments and should be validated with the Director/Managers.
- Contract rate changes mid-tenure (e.g. a school renegotiating its rate) and multi-school assignments in one month are not clearly represented in the current sheet — confirm how often these occur and whether the Contract entity needs versioning.
- This document reflects one month's snapshot (August/September 2026 payroll cycle, referencing July expenses and June school payments); it should be validated against a few additional months to confirm the patterns generalize before finalizing the schema.
