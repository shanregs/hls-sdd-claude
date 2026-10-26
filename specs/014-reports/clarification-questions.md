# Clarification Questions: 014 Reports

2026-10-27. Not answered; the recommended default stays in the spec Assumptions until the Director or product owner decides.

## Q1. One spec or three?

The nine reports touch ten modules and have different audiences.

| Option | Answer | Implication |
| --- | --- | --- |
| A (recommended) | Split: 014a Attendance + Payroll + own reports + framework; 014b School + Collections + Margin; 014c Recruitment + Teacher + Manager + Expenses | Smaller plans; margin (D14) can ship early after 014a framework |
| B | One spec, three delivery phases | One plan, larger review |
| C | Margin and Attendance first only; rest later | Fastest to the Director's asks |

## Q2. Margin: which pay figure?

| Option | Answer | Implication |
| --- | --- | --- |
| A (recommended) | Payslip net pay | Simple, matches what the Teacher receives |
| B | Gross salary before LOP | Margin unaffected by absences |
| C | Cost to HLS (net + reimbursements) | Fuller cost, mixes expense claims into margin |

## Q3. Margin when a Teacher moves School mid-month

| Option | Answer | Implication |
| --- | --- | --- |
| A (recommended) | Split pay by placement days to match the due lines | Per-School margin is correct; needs placement days from 027 |
| B | Show the Teacher on the School with most days | Simple, small error |
| C | Show "not matched" for that Teacher | No wrong figures, margin gaps |

## Q4. Does a Zone Manager see pay-derived figures?

Margin shows Teacher pay by Teacher (D14 says Zone Manager sees margin for own Schools).

| Option | Answer | Implication |
| --- | --- | --- |
| A (recommended) | Zone Manager sees margin per School and Teacher, not the payroll report | Meets D14 literally; pay is inferable from fee minus margin |
| B | Zone Manager sees margin per School only (totals), not per Teacher | Hides individual pay |
| C | Zone Manager sees per Teacher but not the pay column | Same inference as A |

## Q5. Default month and the unlocked-month rule

| Option | Answer | Implication |
| --- | --- | --- |
| A (recommended) | Default last locked month; unlocked and unapproved months selectable with a warning label | Matches month-end habit |
| B | Only locked and approved months can be reported | Never shows changing figures; no mid-month view |
| C | Default current month | Always fresh, but figures change |
