# Spec 022 clarification questions (awaiting the owner)

`/speckit-clarify` was run as a review only. Nothing below has been answered; the spec currently uses each recommended default. The feature is an internal receivables ledger: nothing is sent to the School. Reply with a choice per question and the answers will be folded into `spec.md` under a Clarifications section.

## Q1. When is a School's monthly due raised, and how?

Affects FR-001 and the first screen.

- A (recommended): after the month starts, by one "raise all Schools" action plus a per-School action; Schools already raised are skipped.
- B: in advance, before the month starts, from the planned assignments.
- C: only after month end, once attendance for the month is locked.

Recommended default: A. Billing is by assignment (D6), so attendance lock is not needed; mid-month joins are known from assignment dates.

## Q2. How is the due date set, and what are the first values for payment days and escalation days?

Both numbers are settings. Affects FR-007, FR-016, FR-017.

- A (recommended): due date is the raised date plus 15 days; the follow-up task is created the day after the due date; escalation to the Director is 7 days after the due date.
- B: due on a fixed day each month (for example the 10th of the following month); escalate 7 days later.
- C: due 30 days after the raised date; escalate 15 days after.

Recommended default: A.

## Q3. Who may add or reverse an adjustment (credit note), and who may cancel or replace a wrong monthly due?

Affects FR-009, FR-010 and the permission table.

- A (recommended): Admin and Director only; a Zone Manager can see them but not change them. A wrong due is cancelled with a reason and a corrected one raised for the same month; both stay visible.
- B: also the School's Zone Manager for own Schools, up to a limit.
- C: anyone proposes, the Director approves.

Recommended default: A.

## Q4. Must a collection be allocated when recorded, and how is the default allocation chosen?

Affects FR-011 and User Story 3.

- A (recommended): allocation is chosen when the collection is recorded and defaults to the oldest due first; any excess is kept as advance credit and can be allocated later by the same users.
- B: a collection must be fully allocated when recorded; no advance credit.
- C: collections always sit as advance credit until a Zone Manager or Admin allocates them.

Recommended default: A.

## Q5. From when does ageing count, and does an overdue School block anything?

Affects FR-014 and the buckets.

- A (recommended): ageing counts days past the due date (amounts not yet due are shown as "not yet due"); an overdue School is information only and blocks nothing, for example Teacher mapping.
- B: ageing counts from the first day of the billed month, regardless of the due date; information only.
- C: as A, but a School more than 90 days overdue blocks new Teacher mapping until the Director overrides.

Recommended default: A.

## Not asked (decided from existing material)

- A Teacher transferred between two Schools in a month: each School owes its part (D6, spec 027); a Teacher who exits mid-month counts up to the exit date.
- The monthly due has only a simple internal reference; there is no invoice document, number series, sending or printing for the School.
- A Zone Manager sees nothing about Schools outside their area (Constitution Principle III).
