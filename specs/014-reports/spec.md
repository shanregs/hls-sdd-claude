# Feature Specification: Reports

**Feature Branch**: `spec/014-reports`

**Created**: 2026-10-27

**Status**: Draft

**Input**: User description: "014-reports: The REPORTS menu: Attendance (Teacher % and Manager %, LOP days), Payroll cost, Teacher, School, Recruitment, Manager, Expenses, Collections and Margin (School fee minus Teacher pay, D14) reports, with month, Zone, School, Teacher and Manager filters and CSV export. Full text in docs/spec-inputs/014-reports.md."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Margin: What HLS Earns on Each Teacher and School (Priority: P1) 🎯 MVP

The Admin or Director opens REPORTS → Margin, picks a month and sees, for each Teacher and School, the School fee (the amount in the School's monthly due) minus the Teacher pay (the payslip), with totals per School and per Zone. A Zone Manager opens the same report and sees only their own Schools.

**Why this priority**: The Director has asked for margin since the start (D14); it is the one figure no existing screen shows.

**Independent Test**: With two Schools in two Zones, three Teachers with dues and payslips (one with a due but no payslip), open the report as the Director, a Zone Manager and a Teacher; the numbers match a hand calculation and each role sees only what it may.

**Acceptance Scenarios**:

1. **Given** a Teacher billed 30,000 and paid 22,000 in the month, **When** the report is opened, **Then** the row shows margin 8,000 and the School and Zone totals include it.
2. **Given** a Teacher with a due line but no payslip (or the reverse), **When** the report is opened, **Then** the row is shown as "not matched" with the reason and is left out of the totals, never shown as zero.
3. **Given** a Zone Manager, **When** they open the report, **Then** every row and total covers only their own Schools; a School outside their area is not reachable by filter or by link.
4. **Given** a Teacher or System user, **When** they look for Margin, **Then** it is not in their menu and a direct request is refused.
5. **Given** a month whose payroll run is still a draft, **When** the report is opened, **Then** it is labelled "payroll not approved, figures may change".

---

### User Story 2 - Attendance and LOP Report (Priority: P1)

The Admin or Director sees, per Teacher, School, Zone and for the organization, the month's working days, present, absent, leave, LOP days and Teacher attendance %; and per Manager the working days, checked-in days, leave, missed check-ins and Manager attendance %. A Zone Manager sees their own Teachers and Schools and their own row.

**Why this priority**: Attendance and LOP drive pay and School trust; the deck lists both percentages.

**Independent Test**: Seed a locked month with known marks and leave; the percentages and LOP days equal a hand count and equal the attendance grid and the payroll run for the same people.

**Acceptance Scenarios**:

1. **Given** a Teacher present 20 of 25 working days, **When** the report is opened, **Then** attendance is 80% and the absent and LOP days match the attendance grid.
2. **Given** a month that is not locked, **When** the report is opened, **Then** it is labelled "open, figures may change".
3. **Given** a Manager who missed three check-ins, **When** the report is opened, **Then** the row shows three and the Manager %.
4. **Given** a School with zero working days in the month, **When** the report is opened, **Then** its percentage shows "no data yet", not 0%.

---

### User Story 3 - Payroll Cost Report (Priority: P2)

Admin and Director see payroll cost for a month: gross, LOP deduction, reimbursements and net, by Teachers, Managers and other staff, by Zone and by School, with the run state, and a month-by-month table for the academic cycle.

**Why this priority**: The deck's payroll tile needs a detailed view; only people with Payroll access may see pay.

**Independent Test**: With an approved and a draft run, totals equal the payroll run and its export.

**Acceptance Scenarios**:

1. **Given** an approved run, **When** the report is opened, **Then** the totals equal the run total and the run state is shown.
2. **Given** a month with no run, **When** the report is opened, **Then** it says "no run yet".
3. **Given** a Zone Manager or Teacher, **When** they request it, **Then** it is refused.

---

### User Story 4 - School, Collections and Teacher Reports (Priority: P2)

Per School: Teachers placed, contract end, attendance %, substitution cover, observations and open issues, billed, collected, outstanding. A Collections view shows billed, collected, outstanding and ageing (0 to 30, 31 to 60, 61 to 90, over 90 days past due) by School, Zone and organization. A Teacher report lists Teachers by status with placement, tenure, joiners and exits, open performance issues, transfers and training attendance.

**Why this priority**: Gives the Director and Zone Managers one place for the School and Teacher picture; each figure already exists in an owning module.

**Independent Test**: Seed dues and collections of known ages; ageing buckets and totals equal the School Billing screen; Teacher and School counts equal the master data lists.

**Acceptance Scenarios**:

1. **Given** a due 45 days past due with a balance, **When** Collections is opened, **Then** it is in the 31 to 60 bucket and equals the School Billing ageing.
2. **Given** a Zone Manager, **When** they open School, Collections or Teacher reports, **Then** only their own Schools and Teachers appear in rows and totals.
3. **Given** a Teacher exited this month, **When** the Teacher report is opened for the month, **Then** they appear as an exit with the exit date.

---

### User Story 5 - Recruitment, Manager and Expenses Reports (Priority: P3)

Recruitment by college and season: drives, candidates, selected, offers issued and accepted, joiners, joining ratio, induction completed and ready to deploy, demand versus supply. Manager report per Manager: tasks assigned, completed, overdue, escalated, planned versus completed activity, visits, Schools won, training attended, attendance % and expense claimed. Expenses by area, category and month, budget versus actual, pending and ageing, cost per School acquired and per Teacher trained.

**Why this priority**: Valuable but the dashboard already covers the headline counts.

**Independent Test**: Each figure equals the same figure on its owning screen or the dashboard tile.

**Acceptance Scenarios**:

1. **Given** the Manager report and the dashboard comparison table for the same month, **When** compared, **Then** their common columns are identical.
2. **Given** a Zone Manager, **When** they open the Manager report, **Then** they see only their own row; Expenses is not available to them.
3. **Given** a module that is not built or fails, **When** its columns are requested, **Then** that column shows "not available" and the rest of the report loads.

---

### User Story 6 - My Reports for Managers and Teachers (Priority: P3)

A Manager sees attendance and leave for the Teachers in their Schools. A Teacher sees only their own attendance, leave and payslip history.

**Why this priority**: Closes the "own reports" line of the roadmap using data already visible elsewhere.

**Independent Test**: Sign in as a Manager and as a Teacher; each sees only their scope.

**Acceptance Scenarios**:

1. **Given** a Teacher, **When** they open their report, **Then** they see only their own months and no other Teacher by any filter or link.
2. **Given** a Manager, **When** a Teacher leaves their School, **Then** past months of that Teacher stay visible only for the months they were in scope.

---

### User Story 7 - CSV Export of Any Report (Priority: P2)

On every report the allowed roles export the current filter to CSV. The first rows state the report, month, filter, month state and time made. Each export is audited.

**Why this priority**: The Accountant and Director work in spreadsheets; the existing modules already export the same way.

**Independent Test**: Export each report; rows equal the screen; the audit trail shows the export.

**Acceptance Scenarios**:

1. **Given** a filtered report, **When** an Admin exports it, **Then** the file rows equal the screen rows and totals.
2. **Given** a user without Export on that report, **When** they try, **Then** it is refused.
3. **Given** any export, **When** it is made, **Then** the audit trail records who, which report, filter and time.
4. **Given** a name starting with `=`, `+`, `-` or `@`, **When** exported, **Then** it is neutralized so a spreadsheet does not run it.

---

### Edge Cases

- A Teacher placed in two Schools in one month (transfer): pay and fee are split by placement days; unmatched parts are shown "not matched".
- A Zone Manager changes: past months follow the Zone Manager on the date of the data, as in 022; the new one sees all of the School's months, the old one none.
- A person with no designation or salary: shown with a reason, not counted as zero.
- Filter month in the future: empty result with a message.
- A report with more than 500 rows: paged on screen; the export is complete.
- A source module is slow or fails: that column shows an error note; other columns remain.
- A deleted or inactive Zone, School or Manager still appears for months in which it had data.
- Two exports at once do not interfere.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST provide read-only reports under REPORTS: Attendance, Payroll, Teachers, Schools, Recruitment, Managers, Expenses, Collections, Margin, plus a Manager's Teacher attendance and leave view and a Teacher's own report.
- **FR-002**: Every figure MUST be read from the owning module's published interface; the module MUST NOT store figures, and a report figure MUST equal the same figure on its owning screen and the dashboard tile.
- **FR-003**: The Attendance report MUST show Teacher attendance % and Manager attendance %, working days, present, absent, leave, LOP days and missed check-ins, rolled up by person, School, Zone and organization.
- **FR-004**: The Payroll report MUST show gross, LOP deduction, reimbursements and net by kind of person, Zone and School with the run state, and a cycle table by month.
- **FR-005**: The Margin report MUST show School fee minus Teacher pay per Teacher, School and month, with totals by School and Zone, and MUST show unmatched rows separately and exclude them from totals.
- **FR-006**: The Collections report MUST show billed, collected, outstanding and ageing buckets by School, Zone and organization, equal to School Billing.
- **FR-007**: The Teacher, School, Recruitment, Manager and Expenses reports MUST show the figures listed in User Stories 4 and 5.
- **FR-008**: Each report MUST take a month (default the last locked month, else the current) and, where it applies, Zone, School, Teacher and Manager filters.
- **FR-009**: Rows and totals MUST be scoped to the caller before counting; a total MUST NOT include a record the caller cannot see.
- **FR-010**: Margin MUST be available to Admin, Director and, for their own Schools, the Zone Manager; Payroll and Expenses MUST NOT be available to a Zone Manager or Teacher.
- **FR-011**: A figure with no data MUST show "no data yet"; an unbuilt or failing source MUST show "not available" for that column only; a figure MUST never default to zero.
- **FR-012**: A month not locked, or a payroll run not approved, MUST be labelled on screen and in the export.
- **FR-013**: Every report MUST export CSV for the same filter and scope; the first rows MUST hold report, month, filter, month state and time made; cells MUST be neutralized against spreadsheet formulas.
- **FR-014**: Every export MUST be recorded in the audit trail with who, report, filter and time.
- **FR-015**: Access MUST be per report through the permission matrix (View, Export) with defaults set in the Role & Permission Impact table, editable at runtime.
- **FR-016**: A Teacher MUST see only their own attendance, leave and payslip history.
- **FR-017**: Each report MUST load its first page in under 3 seconds for 500 Teachers and 50 Managers, and an export in under 30 seconds.
- **FR-018**: Reports MUST NOT edit any data.

### Key Entities

- **Report**: a named, read-only view with its filters, columns, scope rule and grants; no stored data.
- **Report Row**: one person, School, Zone or month with its figures and, per figure, a state (value, no data yet, not available, not matched).
- **Month State**: open or locked for attendance; draft, approved or paid for payroll.
- **Export Record**: an audit entry for each CSV made.

## Role & Permission Impact *(mandatory — Constitution Principles II–IV)*

| Role     | Menu (section → item) | Default actions | Data scope |
| -------- | --------------------- | --------------- | ---------- |
| Admin    | REPORTS → Attendance, Payroll, Teachers, Schools, Recruitment, Managers, Expenses, Collections, Margin | View, Export on all | Org-wide |
| Director | Same as Admin | View, Export on all | Org-wide |
| Manager (Zone Manager) | REPORTS → Attendance, Teachers, Schools, Recruitment, Managers (own row), Collections, Margin | View on those; Export on Attendance, Teachers, Schools, Collections, Margin | Assigned (own Zones, Schools, Teachers); Managers report own row only |
| Teacher  | MY ATTENDANCE / LEAVE → My Report (own attendance, leave, payslip history) | View | Own |
| System   | none | none | None |

**New permission keys**: `REPORT_ATTENDANCE`, `REPORT_PAYROLL`, `REPORT_TEACHERS`, `REPORT_SCHOOLS`, `REPORT_RECRUITMENT`, `REPORT_MANAGERS`, `REPORT_EXPENSES`, `REPORT_COLLECTIONS`, `REPORT_MARGIN` (each View, Export); the exact keys and their relation to the existing REPORTS menu group are settled in the plan.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For a seeded month, 100% of report figures equal the owning screen, the dashboard tile and a hand calculation.
- **SC-002**: A Director finds the margin of any School for a month in under 30 seconds and without a spreadsheet.
- **SC-003**: In every scope test, no record outside the caller's scope appears in any row, total, link or export.
- **SC-004**: Every export is in the audit trail (100%), and its rows equal the screen rows.
- **SC-005**: First page under 3 seconds and export under 30 seconds at the volumes in FR-017.
- **SC-006**: When one source module is down, the other columns of the report still load in 100% of cases.
- **SC-007**: The Director's month-end review (attendance, payroll, collections, margin) is possible with no manual spreadsheet work.

## Assumptions

- The Management Dashboard (028) is the reuse base; no tile changes in this spec.
- Version 1 is CSV only; no scheduled, emailed, PDF or Excel output; no charts beyond month tables.
- Margin uses the School's monthly due line (022) as the fee and the payslip net (013b) as the pay; whether to use the cost to HLS instead is an open choice (default: net pay).
- Default month: the last locked month, else the current one.
- Zone Manager scope follows the existing "own Schools" rule of 022 and 012.
- A Zone Manager does not see Payroll or Expenses; recruitment counts for all drives as in 016.
- Delivery may be split into 014a (Attendance, Payroll, own reports, framework), 014b (School, Collections, Margin) and 014c (Recruitment, Teacher, Manager, Expenses); the recommendation is in the clarification questions.
- Open choices are in `clarification-questions.md`; the defaults above stand until answered.
- Depends on 008, 009, 012, 013a, 013b, 015, 016, 022, 023, 024, 025, 026, 027, 028, 032 and 035.
