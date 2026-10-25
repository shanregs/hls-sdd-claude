# Spec 022 clarification questions (awaiting the owner)

`/speckit-clarify` was run as a review only. Nothing below has been answered; the spec currently uses each recommended default. Reply with a choice per question and the answers will be folded into `spec.md` under a Clarifications section.

## Q1. When is the invoice raised, and how?

Input point 1. Affects FR-001, FR-007 and the first screen.

- A (recommended): after the month starts (from day 1 of the month for that month, for billing the assignments known at that time), by one "raise all Schools" action plus a per-School action; Schools already invoiced are skipped.
- B: in advance, before the month starts, using the planned assignments.
- C: only after month end, once the attendance month is locked.

Recommended default: A. Billing is by assignment (D6), so attendance lock is not needed; mid-month joins are known from the assignment dates.

## Q2. Invoice number format, and must numbers be gapless?

- A (recommended): `INV-YYYY-NNNN`, a sequence that resets each calendar year; cancelled invoices keep their number, so the series has no gaps and no reuse.
- B: `INV-<School code>-YYYYMM`, one per School and month (a re-raised invoice gets a suffix).
- C: a single never-resetting sequence.

Recommended default: A.

## Q3. Default payment due days and escalation days?

Both are settings; this fixes the first values.

- A (recommended): payment due 15 days after the invoice date; follow-up task created the day after the due date; escalate to the Director 7 days after the due date.
- B: due 30 days; escalate after 15 days.
- C: due 7 days; escalate after 3 days.

Recommended default: A.

## Q4. Who may add or reverse a credit note, and who may cancel an invoice?

- A (recommended): Admin and Director only (Create/Approve permission); a Zone Manager can see them but not add them, since a credit note reduces what HLS is owed.
- B: Admin, Director and the School's Zone Manager for own Schools, up to a limit.
- C: Anyone may propose, and the Director approves.

Recommended default: A, with invoice cancellation also Admin and Director only.

## Q5. Does a receipt have to be allocated immediately, and does an overdue School block anything?

- A (recommended): allocation is chosen when the receipt is recorded and defaults to oldest-due invoice first; any excess is kept as advance credit and can be allocated later by the same users. An overdue School is information only; it blocks nothing (for example Teacher mapping).
- B: a receipt must be fully allocated at recording; no advance credit. Overdue information only.
- C: as A, but a School more than 90 days overdue blocks new Teacher mapping until the Director overrides.

Recommended default: A.

## Not asked (decided from existing material)

- A Teacher transferred between two Schools in a month: each School is billed its part (D6, spec 027); a Teacher who exits mid-month is billed up to the exit date.
- A cancelled invoice can be replaced by raising a new one for the same month; both stay visible.
- A printable view is enough; the Zone Manager sees nothing about Schools outside their area (Constitution Principle III).
