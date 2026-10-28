# Feature Specification: School, Collections and Margin Reports (014b)

**Feature Branch**: `spec/014b-school-reports`

**Created**: 2026-10-28

**Status**: Draft

**Input**: User description: "014b-school-reports-margin: three reports on the 014a framework (filters, scope, CSV export, audit): Margin (School fee minus Teacher pay, per Teacher, School and month), Collections (billed, collected, outstanding and ageing) and School (one row per School and month with people, attendance, substitution, performance, billing and prospect stage). Every figure is read from the owning module. Full text in docs/spec-inputs/014b-school-reports-margin.md."

## Clarifications

### Carried from 014a (answered 2026-10-27)

- Which pay figure for margin? Payslip net pay.
- Margin when a Teacher moves School mid-month? The pay is split by the working days billed at each School, to match the School due lines.
- Does a Zone Manager see pay-derived figures? Margin per School and per Teacher (decision D14), but not the Payroll report.
- Default month and unlocked months? The default is the last locked month; an unlocked or unapproved month can be chosen and carries a warning label.
- Version 1 is CSV only, every export is audited, each report has its own permission keys, and figures are read from the owning module.

### Session 2026-10-28 (analysis fixes)

- Q: Is the split basis the placement days or the billed days? -> A: The working days billed at each School on the due line; they equal the placement working days and make fee and pay agree by construction, so no change to 027 is needed.
- Q: Is the Margin Teacher filter offered? -> A: No. A Teacher with two Schools has two rows, so the page's Teacher filter cannot work; Margin offers the Zone and School filters.
- Q: Which default month and warning label does each report use? -> A: Margin the last approved pay run, School the last locked attendance month, Collections the current month; each labels what is unfinished for that report.

### Session 2026-10-28

- Q: Does the Zone Manager see each Teacher's net pay in the Margin report, or only the fee and the margin? -> A: Fee, net pay and margin, for Teachers at their own Schools only. Hiding the pay column would protect nothing (pay is fee less margin); the Payroll report itself stays closed to them.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Margin Report (Priority: P1) 🎯 MVP

The Admin or Director opens REPORTS → Margin, picks a month and sees, for each Teacher, the fee the School is billed for that Teacher, the Teacher's net pay from the payslip, and the margin (fee less pay). Rows are grouped by School with a total per School and per Zone. A Zone Manager sees the same report for their own Schools only.

**Why this priority**: Margin is the one number that says whether placing a Teacher earns HLS money, and the deck asks for it. Every other figure it needs already exists in billing and payroll, so this proves the reports can combine two modules without recomputing either.

**Independent Test**: With a billed month and an approved payroll run, open Margin for that month and check that each Teacher's fee equals the School Billing due line, the pay equals the payslip net, and the margin is their difference; a Teacher placed in two Schools has the pay split by placement days.

**Acceptance Scenarios**:

1. **Given** a Teacher with a due line of ₹20,000 and a payslip net of ₹14,000 in the month, **When** the report is opened, **Then** the row shows fee ₹20,000, pay ₹14,000 and margin ₹6,000, and the School and Zone totals include it.
2. **Given** a Teacher placed in School A for 10 days and School B for 20 days, **When** the report is opened, **Then** the pay is split 10:20 between the two School rows, each row's fee is that School's own due line, and the two margins add up to the fee less the whole payslip net.
3. **Given** a Teacher with a due line but no payslip in the month, **When** the report is opened, **Then** the row says "not matched" with the reason "no payslip for this month", the margin is blank, and the row is left out of every total (never shown as zero).
4. **Given** a Teacher with a payslip but no due line (for example, placed in a School with no billing that month), **When** the report is opened, **Then** the row says "not matched" with the reason "no School fee for this month" and is left out of every total.
5. **Given** a month whose payroll run is still a draft, **When** the report is opened, **Then** a banner says "payroll not approved, figures may change" and the pay figures are the draft's.
6. **Given** a month with no payroll run at all, **When** the report is opened, **Then** every pay and margin figure says "no data yet" with a note, and the fee column still shows.
7. **Given** a Zone Manager, **When** they open Margin, **Then** they see only Teachers placed at their own Schools (a Teacher placed in a School of theirs for part of the month is split as above and only that part shows), and the Zone filter offers only their Zones.
8. **Given** a Zone Manager, **When** they ask for the Payroll report, **Then** it is refused.

---

### User Story 2 - Collections Report (Priority: P1)

The Admin or Director opens REPORTS → Collections and sees, per School, per Zone and for the organization: what was billed, what was collected, what is outstanding, and how much of the outstanding amount is 0 to 30, 31 to 60, 61 to 90 and over 90 days past its due date. A Zone Manager sees their own Schools.

**Why this priority**: Receivables are the other half of the money picture. The School Billing screens already hold the figures, so this report is the same numbers laid out for review and export.

**Independent Test**: For a month with a billed School, a partial collection and an overdue balance, open Collections and check that billed, collected, outstanding and each age bucket equal School Billing for the same School and month, and that the buckets add up to the outstanding figure.

**Acceptance Scenarios**:

1. **Given** a School billed ₹60,000 for the month with ₹25,000 collected, **When** the report is opened, **Then** the row shows billed ₹60,000, collected ₹25,000 and outstanding ₹35,000.
2. **Given** outstanding amounts past due by 10, 45, 75 and 120 days, **When** the report is opened, **Then** each amount falls in the bucket 0 to 30, 31 to 60, 61 to 90 and over 90 days, and the four buckets add up to the outstanding figure of the row.
3. **Given** an amount that is not yet due, **When** the report is opened, **Then** it counts as outstanding but is in no age bucket, and a separate "not yet due" column shows it so that "not yet due" plus the four buckets equals outstanding.
4. **Given** a Zone and the organization, **When** the report is opened, **Then** the Zone and organization totals equal the sum of their School rows.
5. **Given** a School with a credit note or adjustment, **When** the report is opened, **Then** the billed and outstanding figures equal those on the School Billing screen for that School.
6. **Given** a Zone Manager, **When** they open Collections, **Then** they see only their own Schools and no other Zone.

---

### User Story 3 - School Report (Priority: P2)

The Admin or Director opens REPORTS → School and sees one row per School for the month: Teachers placed, when the contract ends, attendance %, substitution cover, observations done and open performance issues, billed, collected and outstanding, and where the School stands from prospect to active. A Zone Manager sees their own Schools.

**Why this priority**: It is a convenient single page per School, but each figure is already on another report or screen, so it adds value after Margin and Collections.

**Independent Test**: For a School with Teachers, attendance, a substitution, an observation and a billed month, open the School report and check each cell against the screen of the module that owns it.

**Acceptance Scenarios**:

1. **Given** a School with three Teachers placed on the last day of the month, **When** the report is opened, **Then** Teachers placed shows 3 (Teachers whose placement covers the last day) and the contract end shows the date on the School's contract.
2. **Given** a School whose Teachers have locked attendance, **When** the report is opened, **Then** attendance % equals the Attendance report's figure for that School and month.
3. **Given** substitution requirements raised for a School in the month, **When** the report is opened, **Then** the cover figure shows how many were covered by a substitute, by the Manager, or left open, equal to the Substitution screen.
4. **Given** observations done and performance issues open for a School's Teachers, **When** the report is opened, **Then** the counts equal the Teacher Performance screen for the same Teachers.
5. **Given** a billed month, **When** the report is opened, **Then** billed, collected and outstanding equal the Collections report for the same School and month.
6. **Given** a School that began as a prospect, **When** the report is opened, **Then** it shows its stage from the marketing pipeline (for example Won) and "not applicable" for a School that never had a prospect record.
7. **Given** a School with no Teachers placed or no contract, **When** the report is opened, **Then** the missing figures say "no data yet", never 0.

---

### User Story 4 - Access, Filters, Export and Audit (Priority: P1)

All three reports use the 014a framework unchanged: the month and Zone filters (and School where useful), the month-state label, the CSV export with a header block, an audit entry for every export, and permission keys that decide who sees and exports each report.

**Why this priority**: Money reports must be scoped and audited from the first release.

**Independent Test**: Export each report and compare the rows to the screen; check one audit entry per export; sign in as a Zone Manager, a Teacher and a System user and check what each can open.

**Acceptance Scenarios**:

1. **Given** any of the three reports, **When** the person with Export clicks Export CSV, **Then** the file has a header block and the same rows and totals as the screen, and one audit entry is written.
2. **Given** a person without Export, **When** they ask for the file directly, **Then** it is refused, and the screen still opens if they hold View.
3. **Given** a Teacher or the System role, **When** they ask for any of the three reports, **Then** it is refused and no REPORTS item shows.
4. **Given** a Zone Manager, **When** they ask for a Zone or School outside their area, **Then** it is refused.
5. **Given** a month that is not yet final for the report, **When** it is chosen, **Then** the report carries a warning label naming what is unfinished for that report: payroll not approved (Margin), attendance not locked (School), or "as of <date>" (Collections).
6. **Given** a name that starts with a formula character in the exported file, **When** it is written, **Then** it is neutralised as in the other reports.
7. **Given** one source module is slow or down, **When** a report is opened, **Then** only that report's figures from that source say "not available" and every other figure still shows.

### Edge Cases

- A Teacher whose placement began or ended mid-month: pay is split by placement days; days outside a billed School belong to no row and are not invented.
- A Teacher on loss of pay or with reimbursements in the payslip: the margin uses the payslip net pay as paid, so a deduction raises the margin and a reimbursement lowers it; the report states this in a note.
- A Teacher paid in a different month from the one billed (arrears, adjustments): matched by month of the payslip and the month of the due line only; anything else is "not matched".
- A School billed for a Teacher in two lines (a rate change mid-month): both lines add into one fee for the row.
- A credit note larger than the billed amount: billed shows the net figure and outstanding never goes below zero.
- A Zone with no Schools: no rows and a note, not an error.
- A month before billing or payroll began: "no data yet" for the missing side.
- A School transferred between Zones mid-month: rows follow the School's Zone on the last day of the month, as in the 014c Teacher report.
- Margin is negative: shown as a negative number, never hidden.
- A Teacher who moved Schools mid-month counts in Teachers placed at the School of the last day, while the Attendance % of a School counts Teachers at their latest placement in the month, as the Attendance report does; the two can differ for a mover, and the report says which rule each column follows.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST provide three read-only reports under REPORTS: Margin, Collections and School, on the 014a framework unchanged (month filter, Zone and School filters, month-state label, CSV export with header block, audit of every export, permission keys).
- **FR-002**: Margin MUST show, for each Teacher and School in the month, the fee (the School Billing due line), the pay (that School's share of the payslip net, as paid; the whole net when the Teacher is at one School) and the margin (fee less pay), with totals per School and per Zone.
- **FR-003**: When a Teacher is placed in more than one School in the month, Margin MUST split the payslip net pay between the Schools in proportion to the working days billed at each School (the days on the School's due line for the Teacher), so that the split pays add up to the payslip net and each School's pay is set against that School's own due line.
- **FR-004**: A Teacher with a fee but no payslip, or a payslip but no fee, MUST be shown as "not matched" with the reason and MUST be left out of every total; a missing figure MUST never be shown as 0.
- **FR-005**: Margin MUST label a month whose payroll run is a draft or awaiting approval with "payroll not approved, figures may change", and MUST say "no data yet" for pay and margin when the month has no run.
- **FR-006**: Collections MUST show for each School, Zone and the organization the billed, collected and outstanding amounts and the outstanding amount in the age buckets 0 to 30, 31 to 60, 61 to 90 and over 90 days past due, plus a "not yet due" figure, such that the buckets and "not yet due" add up to outstanding.
- **FR-007**: Collections figures MUST equal the School Billing screens for the same School and month, ageing as of the day the report is read.
- **FR-007a**: Collections MUST show two parts: the dues of the chosen month (billed, collected, outstanding) and the position across all months as of today (outstanding with its age buckets), so that the age buckets and "not yet due" add up to the outstanding of that position, not of the chosen month.
- **FR-008**: The School report MUST show, per School and month: Teachers placed on the last day of the month, the contract end, attendance %, substitution cover (covered by a substitute, by the Manager, open), observations done, open performance issues, billed, collected, outstanding, and the prospect-to-active stage.
- **FR-009**: Every figure MUST be read from the module that owns its meaning through that module's published interface, and MUST equal the figure on the owning screen for the same filter; the reports MUST NOT recompute fees, pay, dues, attendance or stages.
- **FR-010**: Margin MUST be available to Admin, Director and, for their own Schools, the Zone Manager (decision D14); Collections and School MUST be available to Admin, Director and, for their own Schools, the Zone Manager; Teacher and System MUST have no access to any of the three.
- **FR-011**: The Zone Manager MUST NOT gain access to the Payroll report through Margin; Margin shows the fee, the net pay and the margin of Teachers at the Zone Manager's own Schools only, and nothing about any other Teacher.
- **FR-012**: The default month MUST be, for Margin, the last month with an approved pay run; for School, the last month whose attendance is locked; for Collections, the current month; each falls back to the current month when there is none. A month that is not final MUST be selectable and MUST carry a warning label naming what is unfinished for that report (Margin: payroll not approved; School: attendance not locked; Collections: figures are as of today).
- **FR-013**: A source that is slow or fails MUST show "not available" for its own figures only; the rest of the report MUST still load.
- **FR-014**: Each export MUST write exactly one audit entry (who, which report, which filter, when) and MUST neutralise any cell that starts with a formula character.
- **FR-015**: The first page of each report MUST appear within 3 seconds and the export within 30 seconds at 500 Teachers and 50 Managers, and the number of database statements MUST NOT grow with the number of Teachers or Schools.
- **FR-016**: The reports MUST be read-only and MUST NOT change any billing, payroll, attendance or placement data.

### Key Entities

- **Margin Row**: one Teacher in one School for a month with the fee, the net pay (after the placement-day split), the margin, and a state (value, no data yet, not available, not matched with a reason).
- **Collections Row**: one School, Zone or the organization for a month with billed, collected, outstanding, the four age buckets and not yet due.
- **School Row**: one School for a month with its people, attendance, substitution, performance, billing and prospect-stage figures.
- **Pay Split**: the share of a Teacher's payslip net pay that belongs to each School, from the working days billed at that School in the month.
- **Age Bucket**: a range of days past the due date (0 to 30, 31 to 60, 61 to 90, over 90) that groups outstanding amounts.

## Role & Permission Impact *(mandatory — Constitution Principles II–IV)*

| Role     | Menu (section → item) | Default actions | Data scope |
| -------- | --------------------- | --------------- | ---------- |
| Admin    | REPORTS → Margin, Collections, School | View, Export | Org-wide |
| Director | REPORTS → Margin, Collections, School | View, Export | Org-wide |
| Manager  | REPORTS → Margin, Collections, School | View, Export | Assigned (own Schools only) |
| Teacher  | None | None | None |
| System   | None | None | None |

**New permission keys**: `REPORT_MARGIN`, `REPORT_COLLECTIONS`, `REPORT_SCHOOL` (View, Export). Eligibility: Admin, Director and Manager only; Teacher and System can never be granted them. Margin does not give the Manager the Payroll report (`REPORT_PAYROLL` stays Admin and Director).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For a month with billing and an approved payroll run, 100% of Margin rows equal the School Billing due line (fee) and the payslip net (pay, after the split), and the margin of every row equals fee less pay.
- **SC-002**: For a Teacher placed in two Schools, the split pay of the two rows adds up to the payslip net exactly (to the paisa) in 100% of cases.
- **SC-003**: Teachers with a fee but no payslip, or the reverse, are shown as "not matched" in 100% of cases, and no total changes when one is added.
- **SC-004**: Billed, collected and outstanding in Collections equal School Billing for the same School and month in 100% of cases, and the age buckets plus "not yet due" add up to outstanding in 100% of rows.
- **SC-005**: Every School report figure equals the owning screen's figure for the same School and month (checked for each column).
- **SC-006**: A Zone Manager sees rows only for their own Schools in all three reports, and gets a refusal for any other Zone or School, in 100% of tests.
- **SC-007**: The first page of each report appears in under 3 seconds and each export in under 30 seconds at 500 Teachers and 50 Managers.
- **SC-008**: An Admin can answer "which Schools lose money this month, and which are overdue by more than 60 days" from these reports in under 2 minutes without a spreadsheet.

## Assumptions

- Margin is computed at report time from published figures of the owning modules; nothing is stored, and no migration is needed unless the plan finds one is unavoidable for a read interface.
- The fee is the monthly due line of School Billing (022); version 1 billing is monthly per Teacher with no GST (decision D10), so the fee needs no tax treatment.
- The pay is the payslip net pay of the approved or draft run (013b) read through `PayrollFigures` (added by 014a); cost to HLS beyond net pay (employer contributions, expenses, incentives) is out of scope.
- Ageing is as of the day the report is read, and a past month's dues are aged as of today; the report states the "as of" date in its header.
- Amounts are shown in rupees with two decimals; the split of a payslip is rounded to the paisa and any remainder paisa goes to the School with the most days.
- Prerequisite read-only amendments to merged specs (listed in the plan): 022 exposes the fee and the billed working days per Teacher per School for a month, and the ageing; 017a, 026, 023, 008 and 005 expose per-School figures; 013b `PayrollFigures` already exists from 014a. No change to 027 is needed because the due lines already carry the billed days.
- Out of scope: the Teacher, Recruitment, Manager and Expenses reports (014c), the Payroll cost report (014a), charts and scheduled output, and any cost to HLS beyond net pay.
- Dependencies: 014a (framework), 012 and 022 (contracts, dues, collections), 013b (payslips), 027 (placement), 017a (substitution), 023 (prospects), 026 (performance), 008 (attendance).
