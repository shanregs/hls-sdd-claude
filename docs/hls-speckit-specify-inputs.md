# HLS Module Specification Inputs — `/speckit-specify` Prompts

2026-09-21 · @shanregs

Ready-to-paste feature descriptions for `/speckit-specify`, one per remaining module, in the dependency order set by the [Implementation Plan & Deliverables Tracker](<HLS SDD Implementation Plan & Deliverables Tracker.md>) (constitution v1.5.0). Each is written business-facing (WHAT/WHY, no tech stack) and grounded in the [Requirements doc](<HLS Teacher Management System — Requirements.md>) sections and constitution principles cited inline, so `/speckit-clarify` and `/speckit-analyze` have something concrete to trace back to.

Already specified: **001-system-status-page** (done, warm-up feature) and **002-identity-access** (spec drafted; still needs `/speckit-clarify` → `/speckit-plan` → ... before Organization starts). Run the modules below one at a time — paste the whole paragraph as the argument to `/speckit-specify` — and let each finish its own path through `/speckit-clarify` → `/speckit-plan` → `/speckit-checklist` → `/speckit-tasks` → `/speckit-analyze` → `/speckit-implement` → `/speckit-converge` before starting the next.

## 1. Organization

Build the module that records which Manager/Area Coordinator is responsible for which schools and teachers. The Director assigns managers to one or more schools; each teacher and school has exactly one accountable manager at a time, though the assignment can change over time and history must be preserved. This assignment is the source of truth every other module uses to enforce "a Manager sees and acts only on their own assigned teachers/schools" (Requirements §2, Constitution Principle II) — it does not itself perform any business transaction, it only answers "who owns this teacher/school right now, and who owned it as of a given date."

## 2. Audit

Build a single, append-only audit trail that every other module writes to whenever a financial or attendance-affecting record is created, changed, or corrected. Each entry must capture what changed, who changed it, when, and the value before and after — for attendance edits, payroll corrections, school payment recording, expense approvals, and substitution assignments alike (Requirements §3, §5, §6, §9/9a; Constitution Principle I). Directors and Admins must be able to view the full history of any record. Entries can never be edited or deleted once written, even by an Admin — corrections must appear as new entries, not overwrites.

## 3. Teacher Master Data

Build the module that maintains each teacher's profile: name, contact details, bank details for payout, the monthly salary HLS has offered them, and their current status (in training / active / on leave / exited). A System Assistant/Admin creates and maintains these records; a Director and the teacher's assigned Manager can view them; a teacher can view (but not edit) their own profile from the mobile app. This is reference data other modules (Attendance, Payroll, Training, Substitution) look up but never own — status changes (e.g. a teacher exiting) must be tracked with a timestamp, not just silently overwritten (Requirements §10; Constitution Principle I).

## 4. School Master Data

Build the module that maintains each partner school's profile: name/branch, primary billing contact, preferred payment mode, and which Manager is currently accountable for it. A System Assistant/Admin creates and maintains school records; Director and the assigned Manager can view them. This module holds only the school's identity and contact data — the commercial terms (contracted rate, billing cycle) belong to the SchoolBilling module and must be looked up through it, not duplicated here (Requirements §4, §10).

## 5. SchoolBilling

Build the module that manages each school's contract terms and the money HLS collects from schools. A contract links a school, an assigned manager and a per-teacher (or lump-sum) rate with a billing cycle, start date and end date, and drives an automatically-generated expected receivable per school per month based on active teacher assignments — never a manually typed number. Managers record actual payments received (date, mode: Bank/Cash/Cheque/UPI, receiver), supporting multiple partial payments against one month's invoice. The system must compute and surface the outstanding balance per school automatically, and the Director/Admin dashboard must show total expected vs. collected vs. outstanding, org-wide and per manager/school, with automated alerts for overdue payments past a configurable threshold (Requirements §4; Constitution Principle III).

## 6. Attendance

Build the daily attendance capture and monthly rollup system that replaces the "STAFF ATTENDANCE REGISTER" spreadsheet. Teachers mark their own daily attendance from the mobile app (optionally with geo-tag/photo/check-in code), or a Manager marks it on their behalf; attendance is tagged to a specific school assignment and supports half-day (fractional) values. The system must support at least Present, Leave, Training-day, and a non-working-day status as a configurable set of codes, and automatically compute per-teacher monthly rollups: training days total vs. attended, days worked, days leave, overall working days, and final weighted attendance total. Every mark and edit must be attributable to who made it and when. Monthly attendance locks once payroll runs for that month, with an explicit re-open/correction workflow rather than silent edits (Requirements §3; Constitution Principle I, II).

## 7. Payroll / Payout

Build the payroll engine that computes each teacher's monthly Net Salary automatically from their attendance rollup and HLS-offered salary — no manual formula re-entry. It must apply configurable rounding rules (nearest rupee / nearest 50) while tracking the rounding delta as its own ledger line rather than discarding it, and compute per-teacher and aggregate margin (school-billed rate vs. HLS payout), explicitly flagging any teacher/month where the school's payment is missing so margin never silently shows as an unexplained loss. It must support multiple payment modes, split disbursement across managers/dates, a downloadable payslip per teacher per month, and non-attendance-prorated fixed salaries for staff/managers on the same payroll run. A payroll run must be triggered on demand (no fixed schedule), be safely re-runnable without double-processing, and a failed run must never affect the previously approved payroll (Requirements §5; Constitution Principle III, VI).

## 8. Expense

Build the manager/staff expense-claim system replacing the "EXPENSES" and "Manager-Details of expenses" spreadsheets. Managers and teachers log expense claims with date, category (Transport/Commute, Food, Fuel, Substitution payout, Observation visit, Miscellaneous), amount, a free-text note, and an optional receipt photo. The system auto-totals expenses by manager per day/month/org-wide, distinguishes routine small expenses from large one-off costs requiring approval above a configurable threshold, and lets a substitution-payout-tagged expense be reconciled against the Substitution module instead of sitting as an unlabeled line. Director/Admin need expense trend views per manager/category/month, exportable alongside payroll and school-payment numbers (Requirements §6).

## 9. Training

Build the training-calendar and attendance-tracking module covering both recurring weekend refresher sessions and the one-month induction program for new recruits, plus a standing recurring monthly training calendar (not just ad hoc events). Admin/Director schedule sessions with topic, trainer, and venue; existing teachers register for weekend sessions, while Admin/Director can directly assign specific teachers and managers to a monthly session, creating an expected-attendance obligation distinct from open registration. Manager attendance at monthly sessions is tracked the same way as teacher attendance. The system must track assigned-vs-attended per person per monthly session and surface non-compliance (assigned but absent) as an exception to Director/Admin, and must maintain each teacher's training completion history (Requirements §8).

## 10. Substitution

Build the module for assigning and reconciling substitute-teacher coverage. When a regular teacher is on leave, a Manager can assign a substitute (from the existing teacher pool or an external stand-in) to a specific school for specific dates, recording the school, original teacher, substitute, dates covered, and the agreed payout. Substitution payouts must flow automatically into payroll/expense records tagged by school and original teacher's contract — never as an unlabeled cash expense. The system must track substitution frequency per teacher/school and surface repeated substitution as a potential staffing signal to the Director (Requirements §7).

## 11. Reporting

Build the read-only reporting and dashboard layer that consumes the other modules' data without owning any transactional data itself. It must provide: a Director dashboard (org-wide receivables vs. collected vs. outstanding, payables vs. paid, aggregate margin, headcount by status, filterable by month/manager/school); a Manager dashboard (their teachers' attendance today, pending collections/payouts, their own expenses); a Teacher/mobile view (their attendance calendar, latest payslip, upcoming training, expense claim status); monthly payroll/billing reports exportable as PDF/Excel; and exception reports (attendance below threshold, overdue school payments, negative/low margin, expenses pending approval) (Requirements §11).

## 12. Campus Recruitment

Build the campus-hiring workflow tracking candidates from drive to onboarding, not just a calendar. Director/Managers schedule recruitment drives (college, dates, venue, interviewing staff); each drive records candidates with an interview outcome (selected/waitlisted/rejected); selected candidates get a Job Offer (role, salary, offer date, response deadline, status: offered → accepted/declined/expired); an accepted candidate automatically becomes a Teacher record in "Recruited" status linked to the next available onboarding batch, with no manual re-entry of candidate details. Director/Admin need a dashboard of drives scheduled vs. completed and candidates interviewed vs. offered vs. joined, per college and season (Requirements §9; Constitution Principle IX).

## 13. Marketing & Sales Calendar

Build a deliberately lightweight outreach log — not a full CRM — as a sub-package of Campus Recruitment, tracking HLS's school-facing sales activity. Director/Managers log planned outreach (school/prospect name, activity type: visit/call/proposal meeting, date, owner); each entry records an outcome (converted to contract — linked to the School record once signed, follow-up needed with a next-action date, or declined); and a simple pipeline view shows prospects by stage (contacted → meeting held → proposal sent → won/lost) (Requirements §9a; Constitution Principle IX).

## Not spec-kit feature specs

**Integration & UAT** and **EC2/VM Deployment** (tracker rows 15–16) are process/infra milestones, not business-facing features — they don't get a `/speckit-specify` input in the usual sense. If you want them tracked through the same workflow anyway, say so and a prompt for each can be added here.
