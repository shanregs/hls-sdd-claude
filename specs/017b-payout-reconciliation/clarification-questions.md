# Clarification Questions: Substitute Payout Reconciliation (017b)

**NOT YET ANSWERED (2026-11-01).** The spec, plan and tasks were written on option A of every question below (marked "assumed,
pending owner confirmation" in `spec.md`). Answer with a list such as "Q1 A, Q2 A, Q3 B". Where the answer is not A, the change needed is
listed under "If not A".

## Q1. How is the pay for one substitute day worked out?

**Context**: 017a only records the credit (substitute, School, date, day value 0.50 or 1.00). No document gives a substitute rate.

| Option | Answer | Implications |
|--------|--------|--------------|
| A (recommended) | The substitute's own day value on that date (013a salary in effect divided by the month's working days) times the day value | No new data; same rule the pay run uses, so it is explainable. Risk: a Teacher with no School calendar may have no divisor, then the credit is a mismatch to fix |
| B | One flat Rupee rate per substitute day, a system setting | Simple and predictable; ignores seniority; one new setting |
| C | The absent Teacher's day value | Pays the cover "as the person replaced"; needs the absent Teacher's salary, which the substitute's payslip would reveal |

**If not A**: B changes FR-003 and research (a settings key, no pay-rule call); C needs a payroll call for another person and a privacy rule.

## Q2. Is a substitute day extra pay, or does it replace something?

| Option | Answer | Implications |
|--------|--------|--------------|
| A (recommended) | Extra pay on top of the month's salary | Matches "credited for the day"; a free Teacher is paid a salary anyway, cover is additional work |
| B | Paid only for days beyond the Teacher's normal working days | Needs the working calendar per substitute; fewer payouts |

**If not A**: B adds an eligibility check in FR-002 using the substitute's working days.

## Q3. Which pay run pays it, and from when?

| Option | Answer | Implications |
|--------|--------|--------------|
| A (recommended) | Offered once the credit's month is ended and locked; the next draft run including the substitute takes it | Matches 013b rule for waiting adjustments; never touches approved runs |
| B | Offered at once, paid in the next run even before the month is locked | Faster pay; the amount may be based on unlocked attendance and the credit may still be voided |

**If not A**: B removes the lock condition in FR-002 and raises mismatch volume.

## Q4. What happens when a credit is changed or voided after the run was approved?

| Option | Answer | Implications |
|--------|--------|--------------|
| A (recommended) | No automatic recovery; shown as "correction needed"; the Admin raises a Correction adjustment | Same as 030; no surprise deductions |
| B | Automatically create a negative adjustment in the next run | Faster, risk of wrongly reducing pay; needs an approval rule |
| C | Ignore (paid is final) | No work, but the books drift from attendance |

**If not A**: B adds an automatic negative adjustment path and Director approval of it.

## Q5. Do credits before go-live (or in months already approved) get paid?

| Option | Answer | Implications |
|--------|--------|--------------|
| A (recommended) | No; only credits dated in or after a go-live month set in System Settings; earlier ones are listed "before reconciliation start" | No surprise back pay; 017a is not merged yet, so there is probably nothing to backfill |
| B | Yes, all live credits whose month is not yet approved for the substitute | Pays old credits in the first run |
| C | Yes, all credits, with a one-off Correction for approved months | Largest, most manual |

## Q6. Does a Manager who covers the class personally earn anything?

| Option | Answer | Implications |
|--------|--------|--------------|
| A (recommended) | No | 017a creates no credit for it; Manager pay is a fixed structure |
| B | Yes, a flat allowance | Needs a rate and an eligibility rule; changes 017a |

## Q7. Is the School billed for substitute days?

| Option | Answer | Implications |
|--------|--------|--------------|
| A (recommended) | No change to billing (022) or margin (014b) | Substitute cost lowers margin only through the paid adjustment if reports ever include it; kept outside this spec |
| B | Add a billable substitute line to the School's dues | Needs the contract terms (012) on cover; large change to 022 |

## Q8. One adjustment per credit, or one per Teacher per month?

| Option | Answer | Implications |
|--------|--------|--------------|
| A (recommended) | One per credit | Exact trace to School and date; more payslip lines |
| B | One summed line per Teacher and month | Neater payslip; harder to withdraw one voided day |

## Not asked (settled by neighbouring specs)

- Payment itself is outside the system (D8); this feature only feeds the pay run.
- A Teacher sees pay status but never the absent Teacher's name (017a rule).
- Approval of runs stays with the Director (013b), Admin cannot approve a run they submitted.
