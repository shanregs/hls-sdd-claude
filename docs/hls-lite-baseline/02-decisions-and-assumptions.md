# Decisions and assumptions

## 1. Owner decisions (final)

| Id | Decision | Source |
|----|----------|--------|
| D-01 | Scale: 200 Teachers, 15-20 Managers, 50 Schools, 10 Zones, about 250 sign-in users. One server, one database. | Original sizing |
| D-02 | Two kinds of training. **Induction** for new joiners: trainees have no salary until converted to Teacher, inducted and contracted to a School. **Ongoing training** for working Teachers: a training day counts as a school work day. Present is paid, Absent is loss of pay (LOP). | C1 |
| D-03 | Trainee attendance (Present/Absent) is recorded but never paid. | C1 |
| D-04 | A Teacher with Active status but no placement gets no pay. | C2 |
| D-05 | Teacher salary is fixed by HLS and does not depend on which School she is in, or on a transfer mid-month. | C3 |
| D-06 | A Manager is the Zone Manager (same role, interchangeable). Managers are tied to the Schools they manage. Their main job is covering substitutions; the second is observing Teachers. Which of these they do on a given day does not change pay. Manager leave means a pay cut. | C4, C10 |
| D-07 | Leave types exist (Casual, Sick, Personal, Other, Loss-of-Pay) but every leave day is deducted. | C5 |
| D-08 | A half day deducts half a day. | C6 |
| D-09 | Invoice pro-rata and pay pro-rata use calendar days. | C7 |
| D-10 | Teacher statuses: Trainee (no salary), Active, Exited. | C8 |
| D-11 | People on payroll: Teachers, Managers, Admin, System Engineer, Content Creator, Director, and Substitutes. | C9 |
| D-12 | Principal or any designated person at a School is a School contact; capture the designation text. | C10 |
| D-13 | The Zone Manager marks training attendance. Sessions differ by audience (new-joiner induction vs ongoing training). | C11 |
| D-14 | Incentive is earned only when the MoU is signed, and is credited to the Manager. | C12 |
| D-15 | Approval hierarchy: Teacher, then Manager (Zone Manager), then oversight by Admin and Director. | C13 |
| D-16 | The permission matrix is editable by Admin. | B1 |
| D-17 | The Manager attends the observation and enters it for the Teacher observed. | B2 |
| D-18 | Admin runs and finalises payroll, including Admin's own pay. The Director acknowledges all pay; acknowledgement does not gate the run. | A1 |
| D-19 | Substitute rate differs per person and is ad hoc. It is entered on each substitution. | A2 |
| D-20 | No GST fields now. Added later by migration when HLS registers. | A4 |
| D-21 | Managers are paid like Teachers (fixed monthly salary). | A6 |
| D-22 | Phone/Android: not known. Browser-based PWA only; no Play Store app. Offline not needed (no poor-signal Schools). | Q1, Q2 |
| D-23 | Schools pay monthly; no per-term or annual billing. | Q4 |
| D-24 | No data migration from the old system. | Q9 |
| D-25 | Admin and Director see all money. Managers see no margin and no payroll. | Q10, A5 |
| D-26 | Principal feedback on observations is a plain note. | Q11 |
| D-27 | One Admin is enough (no four-eyes control). The Director acknowledges payroll to cover this. | Q12 |
| D-28 | Expenses: monthly total by Zone is enough. No budgets. | Q8 |
| D-29 | Stack: React frontend (PWA), Java 25 with the latest Spring Boot, PostgreSQL latest community edition. Logging, metrics and observability are required. | E1 |
| D-30 | All modules ship in release 1. | F |

## 2. Assumptions to confirm

I made these to close gaps. Items marked **(high)** change money and should be confirmed before the payroll and billing specs.

| Id | Assumption | Why it matters |
|----|-----------|----------------|
| A-01 **(high)** | Pay and invoice use the **actual calendar days of the month** (28 to 31) as the divisor. The per-School "working days" screen (old specs 037/038) is dropped. Your note "each School may have different calendar days, 30 or 31" was read as month length. If a School really needs its own day count, add a `school_month_days` table and use it for that School's invoice only. | Divisor for LOP, pro-rata, invoice |
| A-02 **(high)** | LOP day value = monthly salary / calendar days of the month. Payable days = days placed (Teachers) or days employed (others) in the month, minus unpaid days. A weekly off or holiday is never deducted. | Core pay formula |
| A-03 **(high)** | **Staff** (Admin, Director, System Engineer, Content Creator) have no attendance tracking. They get full salary unless Admin enters a manual LOP adjustment line. | Payroll for 4 roles |
| A-04 **(high)** | Billing ignores absences and substitutes: a placed Teacher is billed the full position fee for her placed days. Vacant positions are not billed. | Invoice amount |
| A-05 **(high)** | Manager "No check-in" working days are listed at month lock; Admin must resolve each one (Office, Field, Leave or Absent). Absent and Leave are LOP. Same rule for unmarked Teacher days: Manager or Admin resolves them before the lock. | Month lock |
| A-06 | Rounding: each pay or invoice component is rounded half-up to 2 decimals. | Totals reproducible |
| A-07 | Expense approvers: Teacher claims go to the School's Manager. Manager claims go to Admin or Director. Admin's claim goes to the Director. Admin and Director can approve any claim except their own. | Self-approval |
| A-08 | Leave decisions: Teacher leave by the Manager (Admin and Director may also decide). Manager leave by the Director. | Leave workflow |
| A-09 | A Manager's data scope is the **Schools assigned to them**. Zone-wide actions (substitute pool, marking Substitute days) are open to any Manager of that Zone. A Zone can have several Managers. | Scope rule |
| A-10 | Max 5 bills per expense claim, 10 MB each. Content cap 100 MB. Offer, MoU and evidence files 20 MB. | Limits |
| A-11 | Incentive amount is one setting, snapshotted when the MoU is approved. It is paid once per School (the first signed MoU). The earner is the prospect owner; if the MoU has no prospect, the School's Manager. | Incentive |
| A-12 | Audit log retention is 3 years; money tables (invoices, payroll, payments, salaries, placements) are never purged. Confirm with the accountant. | Compliance |
| A-13 | System Engineer has technical and platform access only (settings, health, observability, unlocking users, audit view). No payroll, billing or HR data. | Role scope |
| A-14 | SMS one-time codes need a registered sender in India (DLT). Start registration early. Password login and an Admin temporary password remain as fallback. | Lead time |
| A-15 | Backups: nightly database dump plus an off-site copy, with a restore test each quarter. | Data safety |

## 3. Conflicts resolved from the earlier draft

| Earlier text | Resolved to |
|--------------|-------------|
| "Director approves payroll" (013b, section 3, 4.3, R21) | Admin finalises; Director acknowledges (D-18) |
| Substitute rate per person with a default | Rate typed on each substitution (D-19) |
| Expense budget (phase 2) and R37 | Removed (D-28) |
| Manager sees margin for own Schools | No (D-25) |
| Hard-coded permissions in code | Editable matrix (D-16). Scope rules remain in code. |
| Five roles | Six roles; Substitute has no login |
| Phase 1 / Phase 2 split | One release (D-30) |
| "No monitoring beyond a health URL" | Logging, metrics, tracing (D-29) |
| Per-School monthly working days (037/038) | Dropped; calendar days (A-01) |
