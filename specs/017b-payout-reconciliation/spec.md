# Feature Specification: Substitute Teachers: Day Attendance, Day-Rate Pay and Reconciliation

**Feature Branch**: `spec/017b-payout-reconciliation`

**Created**: 2026-11-01 (reworked 2026-11-01 after the owner's answers and scope change)

**Status**: Draft. Q1 to Q8 are answered by the owner (see Clarifications). Six new questions that came out of the rework (N1 to N6) are settled on recommended defaults, marked "assumed, pending owner confirmation", and written in `clarification-questions.md`.

**Input**: User description: "017b-payout-reconciliation. Substitutes are a special type of Teacher. They have no monthly salary: they are paid only for the days they are engaged on substitution (and attend), otherwise nothing. They are part of the system (record, allocation, attendance, payout) but get no login and no app access at first. Their allocation (017a) and their day attendance are managed by the Zone Managers of the Zone (Admin and Director also, per existing role rules). Pay for a day is the daily rate of the substitute's designation (005a). Pay is made monthly inside the 013b payroll run: after the month has ended and the substitute's attendance for it is locked, each substitute with attended days gets one pay line for the month (days times rate, no base salary), processed by the Admin and approved by the Director like other pay. A day cancelled or corrected after the month's run was approved is never taken back automatically: it is flagged 'correction needed' and the Admin raises a Correction. Everything already on record is back-paid. A Manager who covers a class personally is unpaid. School billing and margin are unchanged. Roadmap row 017 (017b), constitution Principle VI, decisions D2, D7, D8."

## Clarifications

### Session 2026-11-01: owner answers to Q1 to Q8 and the scope change

- Scope change (decided by the owner): a substitute is a special type of Teacher with **no monthly salary**, paid only for engaged and attended days. A substitute is in the system (record, allocation, attendance, payout) but has **no login and no app access** at first. The Zone Managers of the Zone manage their allocation and their day attendance; Admin and Director may do the same under the existing role rules. There is no Teacher-facing view for a substitute; the former "Teacher sees own pay" story is replaced by a Zone Manager and Admin view of a substitute's days and pay.
- Q1 (decided): pay per day comes from the **designation** (005a): each designation can carry a daily rate, and a day's pay is that rate. The rate is set on a screen (Salary Structures, Designation day rates), by Admin or Director, with an effective date and a kept history, the same convention as the suggested salary of a designation (013a). There is no per-substitute rate and no flat rate.
- Q2 (decided): paid only for days actually engaged and attended. No salary, no "extra on top" logic. A substitute with no attended days in a month gets nothing.
- Q3 (decided): paid monthly inside the 013b run. After the month has ended and the substitute's attendance for it is locked, each substitute with attended days gets a pay line in that month's run (no base salary, days times designation rate). The Admin processes and the Director approves like other pay.
- Q4 (decided): a day cancelled or corrected after the month's payroll was approved is never taken back automatically; the day is flagged "correction needed" and the Admin raises a Correction in 013b.
- Q5 (decided): **back-pay everything already recorded**. There is no go-live month setting. Every engaged day on record is paid. Months whose run was already approved are handled as in User Story 6: paid through a supplementary run per original month (013b mechanism), listed by original month. Risk and options are in the Assumptions.
- Q6 (decided): a Manager who covers a class personally is unpaid; the cover is recorded for reporting only (017a already does this).
- Q7 (decided): School billing (022) and the margin report (014b) are unchanged.
- Q8 (decided): one pay line per substitute per month. The payslip shows the number of days and the rate (and, if the rate changed inside the month, one row per rate).

### New assumptions from the rework (pending owner confirmation; see clarification-questions.md N1 to N6)

- N1: "Substitute" is a Teacher attribute (engagement type Substitute), set when the Teacher record is created or changed by Admin or Director. Designations are unchanged; a designation of kind Teacher may carry a daily rate.
- N2: Only substitute-type Teachers can be assigned to cover a class. Regular Teachers who happen to be free are no longer offered (they are on a salary). This amends 017a.
- N3: A substitute's day attendance is kept by this feature (a record per engaged day), not in the 008 monthly grid, and is locked per substitute and month by Admin or Director.
- N4: Back-pay of already-approved months uses the existing 013b supplementary run, one per original month.
- N5: Credits already on record for Teachers who are not marked as substitutes are listed for the Admin to settle before go-live; they are not paid until the Teacher is marked a substitute. Existing credits dated before go-live count as attended (source "migrated from credit") so they can be back-paid, flagged for review.
- N6: The substitute pool is shared across Zones (any Zone Manager can assign any substitute); a Zone Manager sees days and pay only for substitute days at Schools in their own Zone.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A substitute is engaged, attends, and is paid for the month (Priority: P1)

A Zone Manager allocates a substitute (a Teacher of type Substitute) to cover a class (017a). On the day, the Zone Manager marks the substitute
Present. When the month has ended and the substitute's month is locked, the Admin starts the month's pay run: the substitute gets one line with
the days attended times the rate of their designation, no base salary, no loss of pay. The Director approves, and each day shows "Paid in
run <month>".

**Why this priority**: This is the whole point: a substitute earns only for engaged days, and is paid through the same run, approval and payslip as everyone else.

**Independent Test**: Allocate a substitute for three days (2 full, 1 half) in a past month, mark all Present, lock the month, compute the run, see
one line of 2.5 days times the designation rate, approve, and see each day Paid in run.

**Acceptance Scenarios**:

1. **Given** a substitute with 2.5 attended days in a locked month and a designation rate of ₹800, **When** the Admin computes the month's run, **Then** the substitute has one line with earned pay ₹2,000, no deduction, and the line's working lists each day (School, date, day value) and the rate.
2. **Given** a substitute with a day allocated but marked Absent (did not come), **When** the run is computed, **Then** that day is not paid and shows "Did not attend".
3. **Given** a substitute with no attended day in the month, **When** the run is computed, **Then** they have no line (nothing is paid, not even zero).
4. **Given** a month that is not yet locked for the substitute, **When** the run is computed, **Then** the substitute is listed as "not ready: attendance not locked" and does not block the others.
5. **Given** a designation whose rate changed on 15/11/2026, **When** a month has days on both sides, **Then** each day uses the rate in effect on its date, and the line shows one row per rate.
6. **Given** a substitute whose designation has no daily rate in effect on a day, **When** the run is computed, **Then** the substitute is listed "cannot pay: no day rate for designation" with the designation name and the run continues.
7. **Given** a substitute day marked Present for a half day (day value 0.50), **When** paid, **Then** it counts 0.5 of the rate.
8. **Given** an approved run, **When** it is looked at, **Then** the substitute's days are frozen in it and cannot change.

---

### User Story 2 - The Zone Manager records the substitute's day attendance (Priority: P1)

A substitute has no login and does not mark themselves. The Zone Manager opens OPERATIONS → Substitutions → a requirement (or the day list "Substitute attendance") and marks each assigned day Present or Absent (did not come). Admin and Director can do the same for any School. Marks can be corrected until the substitute's month is locked, and each change is recorded with who and when.

**Why this priority**: Without attendance there is no pay; attendance is the evidence that the day was worked.

**Independent Test**: As the Zone A Manager, mark a Zone A substitute day Present; as the Zone B Manager, try the same day and get "not found"; correct it to Absent; see the history.

**Acceptance Scenarios**:

1. **Given** an assigned day in the past or today, **When** the Zone Manager of that School's Zone marks it Present, **Then** it is saved with who and when, and the day shows "Attended".
2. **Given** a future date, **When** anyone tries to mark it, **Then** it is refused.
3. **Given** a substitute's month that is locked, **When** anyone tries to mark or change a day, **Then** it is refused until an Admin or Director reopens that substitute-month with a reason.
4. **Given** a Manager of another Zone, **When** they try to mark or view, **Then** the answer is "not found".
5. **Given** a substitute, **When** they would try to sign in or mark themselves, **Then** there is no account and no way in: substitutes have no login and the mobile and web self-marking paths refuse them.
6. **Given** a day whose allocation is cancelled (017a), **When** the Zone Manager looks, **Then** the day is no longer markable and shows "Cancelled".

---

### User Story 3 - Admin, Director and Zone Manager see every day and where it stands (Priority: P1)

Under PAYROLL & FINANCE → Substitute Payouts (and the substitute's record), Admin and Director see per substitute and month: days allocated, days attended, days paid, the rate, the amount, and a status per day; the Zone Manager sees the days at Schools of their own Zone. Day status: Allocated, Attended, Did not attend, Cancelled, Locked and waiting for the run, In draft run, In a run awaiting approval, Paid in run, Cannot pay (with reason), Back-pay waiting, Cancelled or corrected after payment (correction needed), Correction raised, Corrected. Totals per substitute, School and month show days allocated, attended and paid. CSV export.

**Why this priority**: Payroll people must prove that every engaged day was paid once, and find the ones that were not.

**Independent Test**: With days in each state, open the screen, filter, and see statuses and totals that add up; export the CSV and compare.

**Acceptance Scenarios**:

1. **Given** days in several states, **When** the Admin opens a month, **Then** each day shows its status; filters by substitute, School, Zone, month and status work.
2. **Given** a month, **When** totals are shown, **Then** days allocated equals attended plus did-not-attend plus cancelled plus not yet marked, and attended equals paid plus each unpaid state, per substitute, School and month; rows sum to the footer.
3. **Given** the list, **When** exported, **Then** a CSV with the same filters downloads, formula-safe cells, dates DD/MM/YYYY, amounts in Rupees.
4. **Given** a Zone Manager, **When** they open the screen, **Then** they see only days at Schools of their Zone, read-only except marking attendance.
5. **Given** a substitute's record, **When** Admin or Director opens it, **Then** the days and pay history of that substitute are shown (the view that replaces the former Teacher view).

---

### User Story 4 - Mismatches, cancellations after payment and Corrections (Priority: P1)

A Mismatches tab lists every day that cannot be paid or whose payment no longer matches, each with reason and next step: no rate for the designation; substitute not marked as substitute type, not Active or exited; day cancelled or corrected after the run took it (draft: the line is recomputed; awaiting approval: waits; approved: correction needed); amount differs from what the rate gives now; back-pay waiting for a supplementary run. For "correction needed" the Admin starts a Correction from the row with amount and reason prefilled and editable, linked to the day.

**Why this priority**: Money questions after approval are where trust is lost.

**Independent Test**: Cancel a day after its run was approved; see "Cancelled or corrected after payment, correction needed" with the amount paid; raise the Correction; see it in 013b and the row "Correction raised", then "Corrected" when a run applies it.

**Acceptance Scenarios**:

1. **Given** a day cancelled or changed to Absent while the run is a draft, **When** the run is recomputed, **Then** the line no longer includes it (013b recompute), and the day shows "Cancelled before payment".
2. **Given** the same change while the run awaits approval, **When** shown, **Then** it is "Changed, run awaiting approval"; if the Director returns the run, recompute drops it; if approves, it becomes "correction needed".
3. **Given** the change after approval, **When** the Admin opens the row, **Then** the paid amount, the run and "Raise correction" (Admin) are shown; nothing is changed automatically.
4. **Given** a correction raised, **When** a later approved run applies it, **Then** the row becomes Corrected with that run's month.
5. **Given** a substitute designation without a day rate, **When** a rate with an earlier effective date is added later, **Then** the next reconcile clears the mismatch and the day becomes payable (a supplementary or next run).

---

### User Story 5 - Missed events are repaired and everything is audited (Priority: P2)

A daily job compares allocations, day attendance and paid days, repairs gaps (a new allocation without a day record, a cancelled allocation still markable, run data not yet reflected), and writes what it did. Every mark, lock, reopen, status change and correction is audited with who or what did it.

**Independent Test**: Delete a day record in test data and run the job; it is recreated once; a second run changes nothing.

**Acceptance Scenarios**:

1. **Given** a live allocation with no day record, **When** the job runs, **Then** one is created, recorded as a repair.
2. **Given** an approved run whose event was missed, **When** the job runs, **Then** the days it carried become Paid in run.
3. **Given** the job runs twice, **When** the second ends, **Then** nothing changed.
4. **Given** any change, **When** the Director opens Change History, **Then** it names the day, old and new value, and the actor or "reconcile job".

---

### User Story 6 - Back-pay of everything already recorded (Priority: P1)

At go-live, every engaged day already on record is paid. Days in a month whose run is still open (not approved) are paid in that month's run. Days in months whose run was already approved are listed "Back-pay waiting" by original month; for each such month the Admin starts a supplementary run (013b) that contains only the substitutes with unpaid attended days of that month. Existing credits (from 017a) dated before go-live are brought in as attended (source "migrated from credit") and flagged for the Admin to review; credits for Teachers not marked as substitutes are listed for settling first.

**Why this priority**: The owner asked for everything on record to be paid; leaving old days unpaid breaks trust in the first run.

**Independent Test**: Seed credits in an approved month and an open month; run the go-live migration; see migrated days; compute the open month's run; start the supplementary run for the approved month; all days Paid in run with their original months shown.

**Acceptance Scenarios**:

1. **Given** credits on record before go-live, **When** the migration runs, **Then** one day record exists per credit, attendance "Attended (migrated)", flagged for review, and none is counted twice.
2. **Given** a credit for a Teacher who is not a substitute, **When** the migration runs, **Then** it is listed under "settle first" and not paid until the Teacher is marked a substitute and the Admin releases it.
3. **Given** a month whose primary run is approved and which has unpaid attended substitute days, **When** the Admin looks at 013b, **Then** a supplementary run for that month is available and contains exactly those substitutes (013b rule).
4. **Given** all back-pay supplementary runs approved, **When** reconciliation runs, **Then** every migrated day is Paid in run with its original month and the run month.

### Edge Cases

- A substitute is allocated at two Schools on one date (day values add to at most 1.00, 017a): two days, both markable, both paid by value.
- A half-day allocation marked Present: pays 0.5 of the rate.
- A substitute is changed on a requirement after being marked Present: the first day becomes Cancelled (reason "substitute changed"); if the month was paid, "correction needed".
- A substitute exits or becomes inactive: days already attended before exit are still paid; no new allocation is possible (017a availability).
- A designation is changed for a substitute: the designation in effect on each day's date gives the rate (designation history, 005a).
- A substitute's month is reopened for correction by Admin or Director with a reason: the run line is recomputed if the run is a draft; if approved, correction needed.
- An Absent mark on an allocated day: the 017a requirement stays Assigned; Manager may release and re-assign (017a); this feature does not change the requirement.
- Rate is zero or negative: refused when entered; a missing rate is a mismatch.
- A Manager covers personally: no day record is made; nothing is paid; reporting continues from 017a.
- Regular Teachers (salaried) who covered before this change are not paid here (N5).
- A user holds both Admin and Director roles: may do everything either may; the 013b rule that the submitter cannot approve still applies.
- Large lists are paged; totals are for the whole filter.

## Requirements *(mandatory)*

### Functional Requirements

**Substitute as a Teacher type (amends 005, 017a, 008, 013a, 013b, identity)**

- **FR-001**: A Teacher MUST have an engagement type, Regular (default) or Substitute. Only Admin and Director set it (Teacher record rights of spec 005). A Substitute has no placement-based attendance, no monthly salary, and MUST NOT be a payroll person of the salary kind.
- **FR-002**: A Substitute MUST NOT be given a login: no user account is created or linked, no OTP sign-in is possible, and the self-service paths (My Attendance, mobile attendance, notifications to the person) MUST refuse them. Existing Teachers who are turned into Substitutes MUST have any account link cleared or disabled.
- **FR-003**: Only Substitute-type Teachers with status Active MUST be offered and accepted as substitutes in 017a (server re-checks). Regular Teachers are not offered.
- **FR-004**: 017a notifications addressed to the substitute (assigned, ended) MUST go to the Zone Manager of the School instead, since the substitute has no login.

**Designation daily rate**

- **FR-005**: A designation of kind Teacher MAY carry a daily rate, recorded with an effective date and kept as append-only history, edited by Admin and Director on Salary Structures → Designation day rates, with audit. The rate must be above zero.
- **FR-006**: The rate for a day MUST be the rate in effect on that day for the designation the substitute held on that day (005a history). There is no per-substitute rate and no flat rate.

**Day attendance**

- **FR-007**: For every live allocation (017a credit) the system MUST keep one substitute day record: substitute, School, date, day value, attendance (Not marked, Present, Absent), who marked and when.
- **FR-008**: Admin, Director and the Zone Manager of the School's Zone MUST be able to mark or correct a day (Present or Absent) for dates up to today, until the substitute-month is locked. A substitute cannot mark themselves.
- **FR-009**: Admin and Director MUST be able to lock a substitute's month after it has ended, only when every live day of it is marked, and to reopen it with a reason; every step is audited. A locked month refuses marks.
- **FR-010**: When 017a ends an allocation (substitute changed, released, absence withdrawn, Manager covers), the day record MUST become Cancelled with the reason and can no longer be marked or paid.

**Pay**

- **FR-011**: The month's pay run (013b) MUST include, for each Substitute with at least one Present day in a locked month, exactly one line: earned pay equal to the sum over Present days of (day value times the rate in effect that day), rounded as the run rounds, no deduction, no base salary. A Substitute with no Present day has no line.
- **FR-012**: The line's frozen working MUST list each day (School, date, day value, rate) and the payslip MUST show the number of days and the rate (one row per rate if it changed).
- **FR-013**: A Substitute who cannot be paid (month not locked, designation without a rate, not Active type, no designation) MUST be listed with the reason and MUST NOT block the run (013b rule).
- **FR-014**: The system MUST NOT change an approved run. A day marked Present after the primary run is approved is paid through a supplementary run (013b) for that month.
- **FR-015**: Back-pay (User Story 6): every Present or migrated day not yet paid MUST be payable; months with an approved run use a supplementary run per original month. There is no go-live-month setting.
- **FR-016**: A Manager covering personally MUST create no day record and no pay.

**Reconciliation**

- **FR-017**: Every day MUST show exactly one status from: Allocated (not marked), Attended, Did not attend, Cancelled, Locked and waiting for run, In draft run, In a run awaiting approval, Paid in run, Cannot pay (reason), Back-pay waiting, Changed (run awaiting approval), Cancelled or corrected after payment (correction needed), Correction raised, Corrected.
- **FR-018**: The system MUST total per substitute, School and month: days allocated, attended, paid and each unpaid state, and the amount paid, so that attended equals paid plus unpaid for the same filter.
- **FR-019**: The Mismatches view MUST list days that cannot be paid, days changed or cancelled after payment, days whose paid amount differs from what the rates give now, and back-pay waiting, each with reason and next step.
- **FR-020**: An Admin MUST be able to raise a Correction (013b kind Correction) from a "correction needed" row with amount and reason prefilled and editable, linked to the day. The system MUST NOT take money back automatically.
- **FR-021**: A daily reconcile job MUST repair missing day records, mark days paid from approved runs and be idempotent; every repair is recorded.

**Screens, roles, scope, audit**

- **FR-022**: A PAYROLL & FINANCE item "Substitute Payouts" shows list, totals, mismatches, detail and history, with filters (month, substitute, School, Zone, status), paged and sortable; the substitute's record also shows their days and pay. CSV export with formula-safe cells, DD/MM/YYYY dates, Rupee amounts.
- **FR-023**: Access: Admin and Director View, Edit (mark), Approve (lock and reopen) and Export org-wide; only Admin raises a Correction; a Manager (Zone Manager) Views and marks days at Schools of their own Zone only, never locks, never raises corrections; Teacher and System have none (a substitute has no access at all). Out-of-scope items answer "not found". The server re-checks every call.
- **FR-024**: Every mark, correction of a mark, lock, reopen, status change, repair, migration and raised Correction MUST write an audit entry naming the day, old and new value, and the actor or "reconcile job"; day history is append-only.
- **FR-025**: Payroll MUST NOT depend on the substitution module; payroll reads the days through a port that payroll owns and substitution implements (Principle VII).

### Key Entities *(include if feature involves data)*

- **Teacher engagement type**: Regular or Substitute, on the Teacher record.
- **Designation day rate**: daily amount for a Teacher designation from an effective date (append-only).
- **Substitute day**: one per live allocation; substitute, School, date, day value, attendance, marker, status, run and amounts paid, source (allocation or migrated).
- **Substitute month lock**: substitute, month, locked by, reopen history with reasons.
- **Day event**: append-only history of a day's changes.
- **Substitute pay line** (013b): the single line per substitute and month with its frozen days and rates.

## Role & Permission Impact *(mandatory — Constitution Principles II–IV)*

| Role | Substitute Payouts | Notes |
| --- | --- | --- |
| Admin | View, Edit (mark days), Approve (lock, reopen), Export, org-wide; raises Corrections in 013b | Processes the run |
| Director | View, Edit, Approve, Export, org-wide | Approves the run |
| Manager (Zone Manager) | View and Edit (mark days) for own Zone Schools only | No lock, no export of other Zones |
| Teacher | none | A Substitute has no login at all |
| System | none | |

Constitution: a PATCH amendment adds a "Substitute Payouts" row to the Default role access matrix and a PAYROLL & FINANCE navigation item, and the Teachers row gains a note that a Substitute-type Teacher has no account; no principle changes (number 2.5.17 proposed, renumber at merge). Principle VI is fulfilled. Principle VII holds: package `substitution.payout`; `payroll.api` gains a port implemented by `substitution`; `designation.api` is read for the designation on a date.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Every Present day in a locked month is paid in exactly one run line, never zero and never two, in 100% of test scenarios including repeated events, recomputes and job runs.
- **SC-002**: For any month, attended days equal paid days plus unpaid days by state per substitute, School and month, and totals equal the sum of rows.
- **SC-003**: No approved run changes because of this feature in any scenario.
- **SC-004**: A day changed after approval shows "correction needed" within one minute of the change.
- **SC-005**: A Zone Manager marks a substitute's day in under 30 seconds; a payroll user finds a substitute's month in under 30 seconds and raises a Correction in under 1 minute.
- **SC-006**: 100% of out-of-scope requests answer "not found"; a substitute can never sign in.
- **SC-007**: The CSV equals the screen for the same filters and opens without executing any cell.
- **SC-008**: Each substitute pay line on a payslip shows days and rate and can be traced to every School and date.
- **SC-009**: After go-live and the back-pay runs, no engaged day on record is left unpaid except those listed with a reason.

## Assumptions

- N1 to N6 above are assumed, pending owner confirmation.
- Payment of the money stays outside the system (013b, D8).
- Substitutes receive no notifications, mobile screens or web access in this version; the 017a "My Substitutions" Teacher page is not built for them (amends 017a).
- Rate by designation applies to Teacher-kind designations; the owner may later want a "Substitute" designation, which needs no code change.
- 013b can take a pay line that has no base salary and a supplementary run per past month; if it cannot take the day-rate line without bigger changes, the amendment in contracts/amendments-to-other-specs.md grows (risk R3 and item R11 in research.md).
- Out of scope: School billing and margin (Q7), leave and loss of pay for substitutes, a substitute app, notifications to substitutes, per-substitute rates.
- Business dates use the application clock; amounts in Indian Rupees with two decimals.
- Migration number: highest on main at writing is V150; this feature plans V160 and V161 (engagement and day rates); re-check at implementation.
