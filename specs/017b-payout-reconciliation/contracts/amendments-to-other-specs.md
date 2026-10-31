# Amendments required to other specs (notes and tasks only; no code in this spec)

Found by reading the specs and code on main (2026-11-01). Each row is a task in `tasks.md`.

| Spec | What it assumes today | Amendment required |
| --- | --- | --- |
| 005 teacher | `teacher.user_id` is a nullable link to an account; a Teacher is placed in Schools and has a salary on the record (016 offer) | New column `engagement` (REGULAR, SUBSTITUTE, default REGULAR). A Substitute: no account created or linked (cleared if turned into one), no placement, salary field not required and ignored. Teacher form shows the type (Admin, Director). |
| 005a designation | Designation kind TEACHER or MANAGER; employee id; Teacher designation history | No change to kinds. A Substitute still holds a Teacher designation (history used to pick the rate on a date). |
| 001/002 identity | Teacher OTP login by registered phone; Teacher dashboard and My menus | Refuse OTP/sign-in for a Substitute; Teacher role is not granted; no menu. Substitute has no mobile account (018 to 020 unaffected). |
| 008 attendance | Placement-based grid; Teacher self-marks (3-day window) and Manager marks; "S" Substitution code is a built-in status; lock needs every working day of every placed Teacher marked; marks refused without a placement | Exclude Substitute-type Teachers from the grid, rollups, self-marking, lock blockers and the missed-mark lists. Substitute day attendance is kept by 017b (N3 A), so the "S" code stays unused for them. No lock change. |
| 010 notifications | 017a notifies the substitute (assigned/ended) | Send to the School's Zone Manager (else Admin and Director) instead; the substitute has no login. |
| 013a salary structures | Every payroll person has a salary (`person_salary`); designation default is only a suggested monthly amount | New append-only `designation_day_rate` (designation id, amount, effective_on, recorded_by) with `payroll.api.DayRates`; screen Salary Structures → Designation day rates (Admin, Director edit; View for others with the Salary Structures right); audit entity type `DESIGNATION_DAY_RATE`. |
| 013b payroll | Payroll persons are Teachers placed in a School with a salary, Managers, staff; a run line = earned - deduction + adjustments; kinds INCENTIVE/REIMBURSEMENT/SUBSTITUTE_CREDIT reserved | New person source "Substitute" with a day-rate line (no salary, no deduction); `DayRateWork` port; payslip rendering of days and rate; supplementary run uses `unpaidPresentDays`; `SUBSTITUTE_CREDIT` stays reserved and unused; `DayRatePay` read for reconciliation. |
| 017a substitution | Pool = every ACTIVE Teacher who is unplaced or placed in a closed School; credit from assignment; "My Substitutions" page and notification for the Teacher; absent Teacher name hidden from the substitute | Pool = Active Substitute-type Teachers only (N2 A); the Teacher "My Substitutions" page and its menu item are not built or are removed; add the day record created on assignment (017b) and cancelled on `SubstitutionEnded`; Zone Manager screen gains Mark attendance. `SubstituteCredits` stays as the read interface. |
| 014b school reports | `SubstitutionCover` from 017a | Unchanged. |
| 022 billing, 014b margin | Not touched | Unchanged (Q7). |
| Constitution | Teacher role and accounts; Default role access matrix | PATCH 2.5.17: Substitute Payouts row; note that a Substitute-type Teacher has no account. |

Risks: R1 013b run model may assume every payroll person has a salary row; if so the Substitute source needs its own line type (decided at implementation in research R3). R2 a very late day (marked after the primary run) needs a supplementary run per month. R3 designation history for a Substitute may be thin in old data (no designation: "cannot pay").
