# Feature Specification: Reports Framework, Attendance and Payroll Reports (014a)

**Feature Branch**: `spec/014-reports`

**Created**: 2026-10-27

**Status**: Draft

**Input**: User description: "014a-reports-framework: first third of spec 014. The shared report framework (month and Zone filters, caller scope, month-state labels, CSV export, audit, permissions) and the first reports on it: Attendance (Teacher % and Manager %, LOP days), Payroll cost, and the Manager's and Teacher's own attendance, leave and payslip report. School, Collections and Margin are 014b; Recruitment, Teacher, Manager and Expenses are 014c. Full text in docs/spec-inputs/014a-reports-framework.md."

## Clarifications

### Session 2026-10-27

Answered by the product owner ("defaults", the recommended option for all five):

- Q: One spec or three? → A: Split: 014a framework + Attendance + Payroll + own reports; 014b School + Collections + Margin; 014c Recruitment + Teacher + Manager + Expenses.
- Q: Which pay figure for margin (014b)? → A: Payslip net pay.
- Q: Margin when a Teacher moves School mid-month (014b)? → A: Pay is split by placement days to match the School due lines.
- Q: Does a Zone Manager see pay-derived figures? → A: The Zone Manager sees margin per School and per Teacher (D14), but not the payroll report (so in 014a the Payroll report stays Admin and Director only).
- Q: Default month and unlocked months? → A: Default is the last locked month; unlocked or unapproved months can be chosen and carry a warning label.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Attendance and LOP Report (Priority: P1) 🎯 MVP

The Admin or Director opens REPORTS → Attendance, picks a month and sees, per Teacher, School, Zone and for the organization, the working days, present, absent, leave, LOP days and Teacher attendance %; and per Manager the working days, checked-in days, leave, missed check-ins and Manager attendance %. A Zone Manager sees their own Teachers and Schools and their own Manager row.

**Why this priority**: The deck asks for both percentages and LOP days, and the data exists in 008, 009 and 032. It also proves the framework (filters, scope, month state).

**Independent Test**: Seed a locked month with known marks and leave; the percentages and LOP days equal a hand count, the attendance grid and the payroll run for the same people.

**Acceptance Scenarios**:

1. **Given** a Teacher present 20 of 25 working days, **When** the report is opened, **Then** attendance is 80% and absent and LOP days match the attendance grid.
2. **Given** a month that is not locked, **When** the report is opened, **Then** it is labelled "open, figures may change".
3. **Given** a Manager who missed three check-ins, **When** the report is opened, **Then** the row shows three and the Manager %.
4. **Given** a School with zero working days in the month, **When** the report is opened, **Then** its percentage shows "no data yet", not 0%.
5. **Given** a Zone Manager, **When** they filter by a Zone or School outside their area, **Then** the result is refused or empty and no total includes that data.
6. **Given** no month chosen, **When** the report opens, **Then** it shows the last locked month; if none is locked, the current month with the open label.

---

### User Story 2 - Payroll Cost Report (Priority: P1)

Admin and Director see payroll cost for a month: gross, LOP deduction, reimbursements and net, by Teachers, Managers and other staff, by Zone and by School, with the run state (draft, approved, paid), and a month-by-month table for the academic cycle.

**Why this priority**: The deck lists payroll cost; only people with Payroll access may see pay.

**Independent Test**: With an approved and a draft run, totals equal the payroll run, its export and the dashboard payroll tile.

**Acceptance Scenarios**:

1. **Given** an approved run, **When** the report is opened, **Then** totals equal the run total and the state is shown.
2. **Given** a draft run, **When** opened, **Then** it is labelled "payroll not approved, figures may change".
3. **Given** a month with no run, **When** opened, **Then** it says "no run yet", with no zeros.
4. **Given** a Zone Manager or Teacher, **When** they request it, **Then** it is not in their menu and a direct request is refused.

---

### User Story 3 - CSV Export and Audit on Every Report (Priority: P1)

On each report, roles with Export download the current filter as CSV. The first rows state the report, month, filter, month state and time made. Each export is audited.

**Why this priority**: Part of the framework; the Accountant and Director work in spreadsheets.

**Independent Test**: Export each report; rows equal the screen; the audit trail shows the export.

**Acceptance Scenarios**:

1. **Given** a filtered report, **When** an Admin exports, **Then** file rows equal screen rows and totals.
2. **Given** a user without Export on that report, **When** they try, **Then** it is refused.
3. **Given** any export, **When** it is made, **Then** the audit trail records who, report, filter and time.
4. **Given** a name starting with `=`, `+`, `-` or `@`, **When** exported, **Then** it is neutralized.

---

### User Story 4 - My Reports for Managers and Teachers (Priority: P2)

A Manager sees attendance and leave for the Teachers in their Schools. A Teacher sees only their own attendance, leave and payslip history.

**Why this priority**: Closes the roadmap's "own reports" line with data that is already visible elsewhere.

**Independent Test**: Sign in as a Manager and as a Teacher; each sees only their scope.

**Acceptance Scenarios**:

1. **Given** a Teacher, **When** they open My Report, **Then** they see only their own months and no other Teacher by any filter or link.
2. **Given** a Manager, **When** a Teacher leaves their School, **Then** later months of that Teacher are no longer visible to that Manager.

---

### Edge Cases

- A Teacher placed in two Schools in a month: attendance shows per School by the mark's School; the person total counts each day once.
- A person with no joining date or no working days: shown with a reason, not as 0%.
- Month in the future: empty result with a message.
- More than 500 rows: paged on screen; the export is complete.
- A source module fails or is slow: that column shows "not available"; others load.
- Inactive Zone, School or Manager still appears for months in which it had data.
- Two exports at once do not interfere.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST provide read-only reports Attendance, Payroll and My Report under REPORTS, on a shared framework that 014b and 014c reuse unchanged.
- **FR-002**: Every figure MUST be read from the owning module's published interface; the module MUST NOT store figures; a report figure MUST equal the same figure on its owning screen and the dashboard tile.
- **FR-003**: Attendance MUST show Teacher attendance % and Manager attendance %, working days, present, absent, leave, LOP days and missed check-ins, rolled up by person, School, Zone and organization.
- **FR-004**: Payroll MUST show gross, LOP deduction, reimbursements and net by kind of person, Zone and School, the run state, and a cycle table by month.
- **FR-005**: Each report MUST take a month and, where it applies, Zone, School, Teacher and Manager filters; the default month is the last locked month, else the current one.
- **FR-006**: Rows and totals MUST be scoped to the caller before counting; a total MUST NOT include a record the caller cannot see.
- **FR-007**: A figure with no data MUST show "no data yet"; an unbuilt or failing source MUST show "not available" for that column only; a figure MUST never default to zero.
- **FR-008**: An unlocked attendance month or a payroll run not approved MUST be labelled on screen and in the export.
- **FR-009**: Every report MUST export CSV for the same filter and scope; the first rows hold report, month, filter, month state and time made; cells are neutralized against spreadsheet formulas.
- **FR-010**: Every export MUST be recorded in the audit trail with who, report, filter and time.
- **FR-011**: Access MUST be per report through the permission matrix (View, Export), defaults in the table below, editable at runtime; Payroll is never available to a Zone Manager or Teacher.
- **FR-012**: A Teacher MUST see only their own attendance, leave and payslip history; a Manager only the Teachers in their Schools.
- **FR-013**: The first page MUST load in under 3 seconds for 500 Teachers and 50 Managers, and an export in under 30 seconds.
- **FR-014**: Reports MUST NOT edit any data.
- **FR-015**: The framework MUST let a later spec add a report (its filters, columns, scope rule and grants) without changing the framework.

### Key Entities

- **Report**: a named read-only view with filters, columns, scope rule and grants; no stored data.
- **Report Row**: one person, School, Zone or month with figures, each in a state (value, no data yet, not available, not matched).
- **Month State**: open or locked for attendance; draft, approved or paid for payroll.
- **Export Record**: an audit entry for each CSV made.

## Role & Permission Impact *(mandatory — Constitution Principles II–IV)*

| Role     | Menu (section → item) | Default actions | Data scope |
| -------- | --------------------- | --------------- | ---------- |
| Admin    | REPORTS → Attendance, Payroll | View, Export | Org-wide |
| Director | REPORTS → Attendance, Payroll | View, Export | Org-wide |
| Manager (Zone Manager) | REPORTS → Attendance (own Teachers, Schools and own Manager row) | View, Export | Assigned |
| Teacher  | MY ATTENDANCE → My Report (own attendance, leave, payslip history) | View | Own |
| System   | none | none | None |

**New permission keys**: `REPORT_ATTENDANCE` (View, Export), `REPORT_PAYROLL` (View, Export), `MY_REPORT` (View). The REPORTS group exists only as a sketch in the access model (spec 002); no REPORTS module key is in the code, so the plan defines the group and keys.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For a seeded month, 100% of Attendance and Payroll figures equal the owning screen, the dashboard tile and a hand calculation.
- **SC-002**: In every scope test, no record outside the caller's scope appears in any row, total, link or export.
- **SC-003**: Every export is in the audit trail (100%) and its rows equal the screen.
- **SC-004**: First page under 3 seconds and export under 30 seconds at the FR-013 volumes.
- **SC-005**: When one source module is down, the other columns still load in 100% of cases.
- **SC-006**: A further (test) report is added on the framework with no change to the framework code.

## Assumptions

- The Management Dashboard (028) module `reporting` is the reuse base; no tile changes here.
- Version 1 is CSV only; no scheduled, emailed, PDF or Excel output; no charts beyond month tables.
- Default month is the last locked month; unlocked and unapproved months are selectable with a warning label (confirmed 2026-10-27).
- The Zone Manager does not see Payroll (confirmed 2026-10-27). Margin per School and per Teacher for the Zone Manager belongs to 014b.
- Out of scope here, in 014b: School, Collections, Margin. In 014c: Recruitment, Teacher, Manager, Expenses.
- Depends on 008, 009, 013a, 013b, 028 and 032.
