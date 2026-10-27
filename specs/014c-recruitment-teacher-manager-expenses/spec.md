# Feature Specification: Recruitment, Teacher, Manager and Expenses Reports (014c)

**Feature Branch**: `spec/014c-reports-people`

**Created**: 2026-10-27

**Status**: Draft

**Input**: User description: "014c-recruitment-teacher-manager-expenses: four reports on the 014a framework (filters, scope, CSV export, audit): Recruitment, Teacher, Manager productivity and Expenses. Every figure is read from the owning module. Full text in docs/spec-inputs/014c-recruitment-teacher-manager-expenses.md."

## Clarifications

### Carried from 014a (answered 2026-10-27, "defaults")

- Default month is the last locked month; an open or unapproved month can be chosen and carries a warning label.
- A Zone Manager has no access to Payroll or Expenses.
- Version 1 is CSV only, every export audited, per-report permission keys, figures read from the owning module.

Open questions for 014c are in `clarification-questions.md`; each has a default recorded under Assumptions.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Manager Productivity Report (Priority: P1) 🎯 MVP

The Admin or Director opens REPORTS → Manager productivity, picks a month and sees one row per Manager: tasks assigned,
completed, overdue and escalated; activities planned, completed, missed and rescheduled; visits and Schools won; training
days attended; attendance %; and expense claimed. A Zone Manager sees only their own row.

**Why this priority**: The deck asks for Manager productivity, and most columns already exist on the dashboard comparison
table, so it proves the reuse rule (same figure, same source) with the least new work.

**Independent Test**: Seed two Managers with known tasks, activities, attendance and claims; every common column equals the
dashboard comparison table for the same month, and the others equal their owning screens.

**Acceptance Scenarios**:

1. **Given** a month, **When** the report is opened, **Then** the columns shared with the dashboard comparison table show the same numbers.
2. **Given** a Manager with no planned activity in the month, **When** the report is opened, **Then** planned and completed show "no data yet", not 0.
3. **Given** a Zone Manager, **When** they open it, **Then** only their own row appears and totals cover only that row.
4. **Given** the Activity Calendar source is down, **When** the report is opened, **Then** only the activity columns show "not available".
5. **Given** an unlocked attendance month, **When** the report is opened, **Then** it carries the "open, figures may change" label.

---

### User Story 2 - Expenses Report (Priority: P1)

The Admin or Director sees spend by area, category and month, budget versus actual with the amount remaining, claims
pending with their age, and cost per School acquired and per Teacher trained.

**Why this priority**: The deck lists expenses by area, category and cost per School and Teacher; 015 already computes most
of it. Money figures need the strictest scope (no Zone Manager).

**Independent Test**: Seed approved claims and budgets in two areas; totals equal the 015 budget screen, the pending
summary and the cost measures.

**Acceptance Scenarios**:

1. **Given** approved claims, **When** the report is opened for a month, **Then** spend by area and category equals the 015 screens and the dashboard expense tile.
2. **Given** an area over budget, **When** opened, **Then** the row shows budget, actual, remaining and "over budget".
3. **Given** claims waiting for a decision, **When** opened, **Then** the age buckets and totals equal the 015 pending summary.
4. **Given** no School won in the period, **When** opened, **Then** cost per School says "no Schools won", not a division error or 0.
5. **Given** a Zone Manager or Teacher, **When** they request it, **Then** it is not in their menu and a direct request is refused.

---

### User Story 3 - Teacher Report (Priority: P1)

The Admin or Director sees one row per Teacher with status, joined or exited in the month, placement (School and Zone),
tenure, observations done and open performance issues (026), transfers and replacements in the month (027) and training
attendance; plus a count of Teachers by status. A Zone Manager sees only their own Teachers.

**Why this priority**: The main people report, and the first that crosses scope per Teacher.

**Independent Test**: Seed Teachers in each status with an observation, an open issue and a transfer; the row values equal
the Teacher screens and a Zone Manager sees only Teachers in their Schools.

**Acceptance Scenarios**:

1. **Given** Teachers in each status, **When** opened, **Then** the status counts add up to the rows shown.
2. **Given** a Teacher who exited in the month, **When** opened, **Then** the row shows exit in the month and the last placement.
3. **Given** a Teacher with an open performance issue, **When** opened, **Then** the count equals the 026 screen.
4. **Given** a Teacher moved to another Zone mid-month, **When** a Zone Manager opens it, **Then** the Teacher appears only for the Manager of the placement on the last day of the month (default; see Assumptions).
5. **Given** a Teacher with no placement, **When** opened, **Then** placement shows "not placed", not blank.

---

### User Story 4 - Recruitment Report (Priority: P2)

The Admin or Director sees the recruitment funnel by college and season: drives held, candidates, interviewed, selected,
offers issued and accepted, joiners, joining ratio, induction completed, ready to deploy, and demand against supply. A Zone
Manager sees the same counts as the 016 recruitment dashboard.

**Why this priority**: Valuable but narrower in audience; the figures exist on the 016 dashboard.

**Independent Test**: Seed a season with two colleges; each count equals the 016 dashboard and demand equals the 023 dashboard.

**Acceptance Scenarios**:

1. **Given** a season, **When** opened, **Then** each college row and the total equal the 016 dashboard.
2. **Given** selected is zero, **When** opened, **Then** the joining ratio shows "no data yet".
3. **Given** no induction provider or supply source, **When** opened, **Then** those columns show "not available" and others load.
4. **Given** a Zone Manager, **When** opened, **Then** they see exactly what they see on the 016 dashboard, no more.

---

### User Story 5 - Export and Audit on Each Report (Priority: P1)

Roles with Export download the current filter as CSV with the 014a header block; each export is audited. Uses the 014a
writer and audit unchanged.

**Independent Test**: Export each of the four reports; rows equal the screen; the audit trail shows four exports.

**Acceptance Scenarios**:

1. **Given** a filtered report, **When** exported, **Then** rows and totals equal the screen.
2. **Given** a user without Export on that report, **When** they try, **Then** it is refused.
3. **Given** a name starting with `=`, `+`, `-` or `@`, **When** exported, **Then** it is neutralized.

---

### Edge Cases

- Month in the future: empty result with a message. Month before a Teacher or Manager joined: no row.
- A Manager with no Zone, or a Zone with no Manager: the Manager shows under "No Zone"; the Zone filter uses the 028 resolver.
- A Teacher exited before the month is not listed; exited in the month is listed once and marked.
- A School won whose owner changed: counted for the owner recorded by 023, as the dashboard does.
- Claims with no Zone (Head office): shown as "Head office" in the Expenses report and counted in the total.
- A budget set for an area with no spend: actual is a real zero (a budget exists) and remaining equals the budget.
- A source module fails or is slow: that column shows "not available"; others load.
- More than 500 rows: paged on screen; the export is complete.
- Inactive Zone, Manager or college still appears for months in which it had data.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST provide read-only reports Recruitment, Teacher, Manager productivity and Expenses under REPORTS, each a `ReportSource` on the 014a framework, with no change to the framework.
- **FR-002**: Every figure MUST be read from the owning module's published interface and equal the same figure on its owning screen and, where one exists, the dashboard tile or comparison column.
- **FR-003**: Recruitment MUST show, per college and season and as a total: drives held, candidates, interviewed, selected, offers issued, offers accepted, joiners, joining ratio, induction completed, ready to deploy, and demand against supply.
- **FR-004**: Teacher MUST show one row per Teacher with status, joined or exited in the month, School and Zone, tenure, observations done, open performance issues, transfers and replacements in the month and training attendance, and a count of Teachers by status.
- **FR-005**: Manager productivity MUST show per Manager: tasks assigned, completed, overdue and escalated; activities planned, completed, missed and rescheduled; visits and Schools won; training days attended; attendance %; expense claimed. Columns shared with the dashboard comparison table MUST be produced by the same `ManagerColumns` sources.
- **FR-006**: Expenses MUST show spend by area, category and month, budget against actual and remaining, pending claims by age, cost per School acquired and cost per Teacher trained.
- **FR-007**: Each report MUST accept the 014a filters that apply: month (Recruitment: season), Zone, School, Teacher, Manager; the default month is the last locked month, else the current month with the open label.
- **FR-008**: Rows and totals MUST be scoped to the caller before counting. Zone Manager: Recruitment as in 016; Teacher report own Teachers; Manager productivity own row only; Expenses none.
- **FR-009**: A figure with no data MUST show "no data yet", an unbuilt or failing source "not available" for that column only, and a division with no divisor an explanatory state; never a false zero.
- **FR-010**: An open attendance month MUST be labelled on screen and in the export; the Expenses pending summary MUST be labelled "as of today".
- **FR-011**: Every report MUST export CSV through the 014a writer and be audited through the 014a export audit.
- **FR-012**: Access MUST be per report through permission keys with View and Export (table below), editable at runtime. Expenses is never available to a Zone Manager or Teacher (014a decision).
- **FR-013**: The first page MUST load under 3 seconds for 500 Teachers and 50 Managers; an export under 30 seconds. Bulk reads only, a fixed number of queries per source.
- **FR-014**: Reports MUST NOT edit any data and MUST NOT store figures; no table and no migration.
- **FR-015**: Where a figure is not on a public interface, the owning spec MUST gain a small additive read-only method; the list is in `source-check.md`. No behaviour of a merged module may change.

### Key Entities

- **Report**: a read-only view, as defined by 014a; four new definitions here.
- **Report Row**: one college, Teacher, Manager, area or month with cells in states (value, no data yet, not available, not matched).
- **Season**: the recruitment season (016) used instead of the month for the Recruitment report.
- **Month State**: open or locked for attendance-based columns.

## Role & Permission Impact *(mandatory — Constitution Principles II–IV)*

| Role | Menu (section → item) | Default actions | Data scope |
| ---- | --------------------- | --------------- | ---------- |
| Admin | REPORTS → Recruitment, Teacher, Manager productivity, Expenses | View, Export | Org-wide |
| Director | REPORTS → Recruitment, Teacher, Manager productivity, Expenses | View, Export | Org-wide |
| Manager (Zone Manager) | REPORTS → Recruitment (as 016), Teacher (own Teachers), Manager productivity (own row) | View, Export | Assigned / own |
| Teacher | none | none | None |
| System | none | none | None |

**New permission keys**: `REPORT_RECRUITMENT`, `REPORT_TEACHER`, `REPORT_MANAGER`, `REPORT_EXPENSES`, each View and Export,
following the 014a per-report pattern (014a adds `REPORT_ATTENDANCE`, `REPORT_PAYROLL`, `MY_REPORT`). No Teacher grant and no
System grant are possible on any of them; `REPORT_EXPENSES` is not eligible for Manager.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For a seeded month and season, 100% of report figures equal the owning screen, the dashboard tile or comparison column and a hand calculation.
- **SC-002**: In every scope test, no record outside the caller's scope appears in a row, total, link or export.
- **SC-003**: Every export is in the audit trail and its rows equal the screen (100%).
- **SC-004**: First page under 3 seconds and export under 30 seconds at the FR-013 volumes.
- **SC-005**: When one source module is down, the other columns still load in 100% of cases.
- **SC-006**: The four reports are added with no change to the 014a framework code.

## Assumptions

- Built on the 014a contract (`ReportSource`, `ReportContext`, `ReportPage`, `Cell`, `DefaultMonth`, `ReportCsvWriter`, `ReportExportAudit`), by those names; 014a is being implemented in parallel on `impl/014a-reports-framework`, and the plan re-checks the names against it.
- Recruitment uses a season filter instead of the month (default: the current season); the month-state label does not apply. (Q1)
- Manager productivity builds task and activity figures with the `ManagerColumns` pattern, so common columns equal the dashboard by construction. (Q2)
- A Zone Manager sees their own Manager-productivity row including their own expense claimed, but has no Expenses report. (Q3)
- The Teacher report lists Teachers placed or in training in the month plus those who exited in it; scope by the placement on the last day of the month (or the exit date). (Q4)
- Training attendance for a Teacher is the induction and training days in the 008 attendance roll-up; no 024 change. (Q5)
- Expenses budget against actual is for the chosen month; the pending summary is as of today and labelled so.
- CSV only; no charts, schedule or email.
- No migration. Main's last is V120; V130 (030, on `impl/030-manager-incentives`) and V140 (039, on `spec/039-user-themes`) are taken; if one is ever needed, take the next free number above V140 after re-checking main and open branches.
- Out of scope: School, Collections, Margin (014b); Attendance and Payroll (014a).
- Depends on 014a, 005, 008, 015, 016, 023, 024, 025, 026, 027, 028, 032, 035.
