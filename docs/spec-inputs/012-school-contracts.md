# 012 School Contracts (MoU): `/speckit-specify` input

2026-10-05. Roadmap row 012. Depends on 005 (master data: Schools, Zone Managers, Teachers, the interim
Teacher–School placement and the scope queries) and 003 (audit). It replaces the interim placement with the contract
and the Teacher mapping, keeps attendance (008) working through the mapping, and is the base for **022 School Billing**
(month-end billing, `docs/spec-inputs/022-school-billing.md`) and 013 (payroll).

The business process: **MoU contract → Teacher mapping (this spec) → attendance capture (008) → month-end billing and
salary computation (022, 013)**.

## Feature description (paste as the argument to `/speckit-specify`)

012-school-contracts: The contract between HLS and a partner School, which is the MoU. It records the School, the
number of Teachers it covers, a start date and an optional end date, whether the salary the School pays is the same
for all Teachers or different for each (one position per Teacher, each with its salary), and the signing details: the
date signed, who signed for the School (name and designation), and who signed for HLS (the School's Zone Manager and a
Director). Only Admin and Director create contracts; they are never edited, a change is a new MoU that ends the old
one. Recruited Teachers are mapped to the contract's positions by Admin, Director or the Zone Manager, replacing the
interim Teacher–School placement of spec 005; a new MoU re-maps the current Teachers in one step with no gap. A School
Contracts list shows each School's status, filled and vacant positions, and Schools with no MoU yet. Module
`schoolbilling` (the constitution's home of the contract). Billing starts from the contract in spec 022.

Upstream flows (2026-10-05): HLS runs campus recruitment and school marketing in parallel. **023 School Marketing and
MoU Pipeline** (`docs/spec-inputs/023-school-marketing-mou.md`) wins the School and hands over to this spec with the
proposed terms pre-filled; **016 Campus Recruitment** (`docs/spec-inputs/016-campus-recruitment.md`) produces the
Teachers (012 maps any non-exited Teacher whatever their status; 016 may add a "Ready to deploy" rule later). This spec stays the only record of the signed MoU and the mapping,
exposes contract existence, positions and filled count to 023 through its public interface, and holds no prospects,
visits, candidates, offers or training.

Out of scope: billing, receivables and payments (spec 022), the Teacher's pay (013), uploading the signed MoU
document, the marketing pipeline (023), recruitment and induction (016), mobile screens.

## Points for `/speckit-clarify`

- How are carried-over placements (no position, "MoU pending") tidied once the MoU is recorded: mapped one by one, or
  a bulk map in order?
- Is "ends soon" a fixed 30 days, or a setting?
- Can a Director sign for HLS on contracts for Schools in any Zone, and may a Zone Manager sign for a School outside
  their Zone? (The draft says the Zone Manager is always the School's own.)
- Can the end date of a contract be removed (made open-ended) again, or only set?
