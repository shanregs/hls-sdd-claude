# Clarification Questions: Manager Incentives (030)

**ANSWERED 2026-10-27: the user chose "defaults", that is option A for Q1 to Q5.** The answers are recorded in the
Clarifications section of `spec.md`. The questions are kept below for history.

## Q1. Who earns the incentive when the prospect's owner is not a Manager?

**Context**: Spec 023 says the incentive goes to the prospect's owner, who may be a Director, a Zone Manager or another
marketing user (FR-004, Edge Cases). Payroll can only add an incentive for a person on the pay run.

| Option | Answer | Implications |
|--------|--------|--------------|
| A (recommended) | Only an owner who is a payroll Manager earns; otherwise no incentive and the School is listed as "no earner" | Simple, safe; Director sees the gap and can act outside the system |
| B | Any owner who is on the pay run (Manager, Director, staff) earns | Needs payroll intake for all kinds of person; widens the scheme beyond "Manager incentives" |
| C | If the owner is not a Manager, the School's Zone Manager earns | Rewards someone who did not own the prospect |

## Q2. Which payroll month pays the incentive?

**Context**: D9 says "that month's Manager payroll". The month could mean the month the MoU was approved or the month
the incentive was approved. 013b takes waiting adjustments into the next draft run (never into an approved run).

| Option | Answer | Implications |
|--------|--------|--------------|
| A (recommended) | The next run that includes the Manager after the incentive is approved (usually the month of approval) | Matches how 013b already treats waiting adjustments; no change to approved months |
| B | The month of the MoU approval, even if the incentive is approved later | If that month is already approved, falls back to the next run anyway; adds a rule with little gain |
| C | A fixed month rule (for example the month after approval) | Predictable for Managers, but needs a new setting and extra logic |

## Q3. Is the amount always Rs 5,000, or are there exceptions?

**Context**: The core business flow lists "exact Rs 5,000 rules and exceptions" as an open question; nothing documents them.

| Option | Answer | Implications |
|--------|--------|--------------|
| A (recommended) | One amount for every School, kept as a setting (default Rs 5,000), frozen on each incentive | Simplest; the Director can change the future amount without code |
| B | The Director can change the amount of one incentive at approval, with a reason | Needs an audit rule and an upper limit; covers exceptions |
| C | A minimum of Teachers or a minimum MoU term is needed before a School qualifies | Needs thresholds from the business; unknown today |

## Q4. What happens if the MoU is rejected or sent back to pending after the incentive is approved?

**Context**: 031 returns an approved MoU to pending when its document is replaced. Payment is irreversible once a run is approved.

| Option | Answer | Implications |
|--------|--------|--------------|
| A (recommended) | Hold the incentive (not taken by any run) until the MoU is approved again; after payment, never taken back automatically (Admin uses a Correction adjustment) | Prevents paying for an unsigned School without surprising anyone after payment |
| B | Ignore later MoU changes; the incentive is final once approved | Simplest; risk of paying for a School whose MoU later falls through |
| C | Cancel the incentive, and create a new one when the MoU is approved again | Two records per School, conflicts with one-incentive-per-School |

## Q5. Do MoUs that were approved before this feature goes live earn an incentive?

**Context**: Some Schools already have an approved MoU (including "approved before approval step"). Paying them could
create a large one-time batch.

| Option | Answer | Implications |
|--------|--------|--------------|
| A (recommended) | No; only MoUs approved after go-live earn | No surprise payout; Director can ask for a one-off backfill later |
| B | Yes, create pending incentives for every School with an approved MoU and a Manager owner | Director reviews and approves each; may include old Schools that were not meant to be rewarded |
| C | Only MoUs approved from a start date the Director sets (a setting) | Flexible, one more setting to manage |

## Not asked (left to the plan or already settled)

- Who approves: settled by D5 (Director or Zone Manager; a Manager may approve one they earned; audit records the approver).
- Whether the Admin can approve: no (D5).
- Impact on 013b: an additive intake for kind Incentive (like 015's `ReimbursementIntake`); impact on 031: none (read-only use of `MouApprovals` and `MouDecided`).
