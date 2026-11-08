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

## 2. Assumptions (reviewed with the owner 2026-11-08; outcomes in section 4)

I made these to close gaps. Items marked **(high)** change money and should be confirmed before the payroll and billing specs.

| Id | Assumption | Why it matters |
|----|-----------|----------------|
| A-01 **(high)** | CHANGED. Pay uses the actual calendar days of the month. Invoices use a per-School, per-month **School month figure** (default calendar days, editable). The table `school_month_days` returns. | Divisor for pay and invoice |
| A-02 **(high)** | LOP day value = monthly salary / calendar days of the month. Payable days = days placed (Teachers) or days employed (others) in the month, minus unpaid days. A weekly off or holiday is never deducted. | Core pay formula |
| A-03 **(high)** | **Staff** (Admin, Director, System Engineer, Content Creator) have no attendance tracking. They get full salary unless Admin enters a manual LOP adjustment line. | Payroll for 4 roles |
| A-04 **(high)** | CHANGED. A School gets a credit of position fee / School month figure for each uncovered day. Covered = Substitute marked Present, or Manager covers. Vacant positions are not billed. | Invoice amount |
| A-05 **(high)** | Manager "No check-in" working days are listed at month lock; Admin must resolve each one (Office, Field, Leave or Absent). Absent and Leave are LOP. Same rule for unmarked Teacher days: Manager or Admin resolves them before the lock. | Month lock |
| A-06 | Rounding: each pay or invoice component is rounded half-up to 2 decimals. | Totals reproducible |
| A-07 | Expense approvers: Teacher claims go to the School's Manager. Manager claims go to Admin or Director. Admin's claim goes to the Director. Admin and Director can approve any claim except their own. | Self-approval |
| A-08 | Leave decisions: Teacher leave by a Manager of the Zone (Admin and Director may also decide). Manager leave by Admin or Director. | Leave workflow |
| A-09 | CHANGED. A Manager sees and acts on **all Schools in their Zones**. Each School still has one named Manager who approves its MoU, transfers and Teacher expense claims. A Zone can have several Managers. | Scope rule |
| A-10 | Max 5 bills per expense claim, 10 MB each. Content cap 100 MB. Offer, MoU and evidence files 20 MB. | Limits |
| A-11 | Incentive amount is one setting, snapshotted when the MoU is approved. It is paid once per School (the first signed MoU). The earner is the prospect owner; if the MoU has no prospect, the School's Manager. | Incentive |
| A-12 | Audit log retention is 3 years; money tables (invoices, payroll, payments, salaries, placements) are never purged. Confirm with the accountant. | Compliance |
| A-13 | CHANGED. System Engineer has technical access and also manages users and roles, like Admin. No payroll, billing or HR data by default. Risk: they could grant themselves money permissions; a guardrail is proposed in `04-roles-permissions.md`. | Role scope |
| A-14 | CHANGED. Password login only for now. SMS one-time codes are deferred and will need a DLT-registered sender when added. | Lead time |
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
| Per-School monthly working days (037/038) | Kept only as the School month figure for invoices; pay uses calendar days (A-01) |

## 4. Assumption review outcome (2026-11-08)

| Id | Outcome |
|----|---------|
| A-01 | Changed: per-School month figure for invoices only (new D-31). Pay uses calendar days. |
| A-02 | Confirmed. Weekly offs and holidays are never deducted; leave counts working days only. |
| A-03 | Confirmed. Staff get full salary; Admin adds a manual LOP line when needed. |
| A-04 | Changed: credit for uncovered days at position fee / School month figure (new D-32). Covered = Substitute marked Present or Manager covers. |
| A-05 | Confirmed. Month lock is blocked until every unmarked or no-check-in working day is resolved. |
| A-06 | Confirmed. Round each component half-up to 2 decimals. |
| A-07 | Confirmed. |
| A-08 | Changed: Manager leave decided by Admin or Director (new D-33). |
| A-09 | Changed: Manager scope is by Zone; one named Manager per School kept for approvals (new D-34). |
| A-10 | Confirmed. |
| A-11 | Confirmed. |
| A-12 | Confirmed: 3 years for audit, configurable; confirm with the accountant before go-live. |
| A-13 | Changed: System Engineer also manages users and roles (new D-35). Guardrail proposed, not yet confirmed. |
| A-14 | Changed: password login only for now; SMS deferred (new D-36). |
| A-15 | Confirmed. |

### New decisions from the review

| Id | Decision |
|----|----------|
| D-31 | Invoices divide by a School month figure per School and month. Default is the calendar days of the month; Admin can edit it. Pay never uses it. |
| D-32 | An uncovered day (Teacher Absent or on Leave on a School working day, no Substitute marked Present, no Manager cover) gives the School a credit of position fee / School month figure. Raising an invoice is blocked while a Substitute assignment in the month has no day mark. |
| D-33 | Manager leave is decided by Admin or Director. |
| D-34 | Managers act on all Schools in their Zones. The named School Manager approves its MoU, transfers and Teacher expense claims. |
| D-35 | System Engineer manages users and roles in addition to technical settings. |
| D-36 | No SMS sign-in in release 1. Admin or System Engineer sets temporary passwords. |
