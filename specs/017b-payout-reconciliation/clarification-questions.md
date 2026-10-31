# Clarification Questions: Substitute Teachers, Day-Rate Pay and Reconciliation (017b)

**Q1 to Q8 ANSWERED 2026-11-01 by the owner (via the coordinator).** The answers replace the earlier assumptions and are recorded in
the Clarifications section of `spec.md`. **N1 to N6 are new, raised by the rework, NOT YET ANSWERED**; the spec, plan and tasks use the
recommended option A of each, marked "assumed, pending owner confirmation".

## Decision: scope change (owner, 2026-11-01)

A substitute is a special type of Teacher with no monthly salary, paid only for the days engaged on substitution (and attended). They
are in the system (record, allocation, attendance, payout) but have no login or app access at first. Their allocation and attendance
are managed by the Zone Managers of the Zone (Admin and Director also, per existing role rules). Consequently the earlier Teacher view
(old User Story 4) and the per-credit payroll adjustment (old `SubstituteCreditIntake`) are dropped.

## Answered

| Q | Question | Owner's answer |
| --- | --- | --- |
| Q1 | Pay rate | By designation (005a): each Teacher designation carries a configurable, effective-dated daily rate; a day's pay is that rate. No per-substitute or flat rate. |
| Q2 | Extra or replacing | Paid only for days actually engaged and attended; no salary, no "extra on top"; no engaged days means nothing. |
| Q3 | When and how | Monthly inside the 013b run, after the month has ended and the substitute's attendance is locked; one line, days times rate; Admin processes, Director approves. |
| Q4 | Changed after approval | No automatic clawback; flagged "correction needed"; Admin raises a Correction. |
| Q5 | Before go-live | Back-pay everything on record; no go-live-month setting. Closed months: see N4. |
| Q6 | Manager covering personally | Unpaid; recorded for reporting only. |
| Q7 | School billing / margin | Unchanged. |
| Q8 | One adjustment per credit or per month | One pay line per substitute per month; payslip shows day count and rate. |

## New questions (recommended default is option A and is what the spec assumes)

### N1. How is a "substitute" marked: a Teacher attribute or a designation kind?

| Option | Answer | Implications |
| --- | --- | --- |
| A (recommended) | A new engagement type (Regular or Substitute) on the Teacher record; designations unchanged (a Teacher-kind designation may carry a day rate) | Small change to 005; designation screens keep working; the type can change with a clear audit |
| B | A new designation kind SUBSTITUTE (005a has TEACHER and MANAGER only) | Rate and type together, but 005a's kind rule, uniqueness and held-ever lock change; a Teacher cannot easily move between types |

If not A: the 005a data model and tasks T006 and T008 to T012 change.

### N2. Can a regular, salaried Teacher who is free still be assigned to cover?

| Option | Answer | Implications |
| --- | --- | --- |
| A (recommended) | No: only Substitute-type Teachers are offered and accepted | One clear pay rule; changes the 017a availability list |
| B | Yes, but they are paid nothing here (salary only) | No 017a change; cover by salaried Teachers stays unpaid extra work |
| C | Yes, and they are paid the day rate of their designation too | Needs a rate on every Teacher designation and double-pay rules |

### N3. Where does a substitute's day attendance live?

| Option | Answer | Implications |
| --- | --- | --- |
| A (recommended) | In this feature: one day record per allocation, marked Present or Absent by the Zone Manager, locked per substitute and month by Admin or Director | 008's placement-based grid, rollups and lock are untouched (they only exclude substitutes); no mobile, no self-marking |
| B | In the 008 grid using the built-in Substitution code S | Reuses the grid; needs 008 to allow marks without a placement and to count substitutes in "every working day marked" locks |

### N4. How are already-approved months back-paid?

| Option | Answer | Implications |
| --- | --- | --- |
| A (recommended) | A supplementary run (013b, exists) per original month, listing only substitutes with unpaid attended days | No 013b change to the run model; several small runs, each listed by original month |
| B | One "back pay" line per substitute in the first open run, grouped by original month | One payment; needs 013b to accept lines for past months (new rule, larger amendment) |

If B: the 013b amendment grows by a past-month line kind and its payslip layout.

### N5. What about credits already on record (017a) and those held by Teachers who are not substitutes?

| Option | Answer | Implications |
| --- | --- | --- |
| A (recommended) | Existing credits before go-live become day records "Attended (migrated)" and are flagged for review; credits of Teachers not marked as substitutes are listed "settle first" and are not paid until the Teacher is marked Substitute | Honours "back-pay everything"; salaried Teachers are not paid twice by accident |
| B | Pay only credits that have a mark; no migrated attendance | Safer but pays little of the recorded days, against the owner's answer |
| C | Mark every credited Teacher as Substitute automatically | Wrongly changes salaried Teachers |

### N6. Which Zone sees and assigns which substitutes?

| Option | Answer | Implications |
| --- | --- | --- |
| A (recommended) | One shared pool; any Zone Manager can assign any substitute; a Zone Manager sees days and pay only for substitute days at own-Zone Schools | Matches 017a (all active Teachers listed) and the data scope rule |
| B | A substitute belongs to a home Zone; only that Zone's Manager assigns them | Needs a Zone on each substitute and transfer rules; blocks cross-Zone cover |

## Not asked (settled by neighbouring specs)

- Payment itself is outside the system (D8).
- Approval stays with the Director; the submitter cannot approve (013b).
- Who edits Teacher records and designations stays as in 005 and 005a (Admin, Director).
