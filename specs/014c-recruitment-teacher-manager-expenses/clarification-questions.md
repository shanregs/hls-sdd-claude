# Clarification Questions: 014c Recruitment, Teacher, Manager and Expenses Reports

2026-10-27. Please answer with a letter per question ("defaults" means the recommended option A for all). Each default is
already written into Assumptions of `spec.md`, so an unanswered question changes nothing. Plan and tasks follow the answers.

## Q1. Recruitment report: month or season?

016 counts by season; the framework filters by month.

| Option | Answer | Effect |
| --- | --- | --- |
| A (recommended) | Season filter replaces the month; default is the current season; no month-state label | Equals the 016 dashboard exactly |
| B | Month filter: drives held, offers and joiners dated in the month | Needs new dated counts from 016; will not match the 016 dashboard |
| C | Both: season plus an optional month inside it | Most flexible; more 016 additions |

## Q2. Manager productivity: how are assigned, completed and activity counts produced?

025 and 035 show overdue, escalated and planned/done/missed on their own screens, but assigned/completed per Manager per
month and the 035 counts are not on a public interface.

| Option | Answer | Effect |
| --- | --- | --- |
| A (recommended) | 025 and 035 each contribute `ManagerColumns` (assigned, completed; planned, done, missed, rescheduled) | Same pattern as the dashboard; common columns equal the comparison table by construction; small additive change to each |
| B | Report shows only the columns the dashboard already has (overdue, escalated, visits missed, Schools won) | No change to 025 or 035; drops assigned, completed, planned, rescheduled from the request |
| C | New read interfaces in 025 and 035 used only by the report | Duplicates figures; risk of mismatch with the dashboard |

## Q3. May a Zone Manager see their own expense claimed in Manager productivity?

014a says a Zone Manager has no Expenses access; the request says a Zone Manager sees their own Manager-productivity row.

| Option | Answer | Effect |
| --- | --- | --- |
| A (recommended) | Yes, own row shows own expense claimed (their own claims, already visible to them in My Expenses) | No new exposure; the Expenses report stays closed to Zone Managers |
| B | Hide the expense column from Zone Managers | Strictest reading of the 014a decision; column absent for them |
| C | Show the column only when Admin or Director views any row, nobody else | Same effect as B, stated as a rule |

## Q4. Teacher report: which Teachers are listed and under which Zone Manager?

| Option | Answer | Effect |
| --- | --- | --- |
| A (recommended) | Teachers placed or in training in the month plus those who exited in it; scope by the placement on the last day of the month (or exit date) | One row per Teacher; a mid-month mover appears once, for the Manager of the destination |
| B | Every Teacher, whatever status or month | Long lists; exited Teachers always shown |
| C | One row per Teacher per School in the month | A mover appears twice; sums double count people |

## Q5. Teacher training attendance: which source?

| Option | Answer | Effect |
| --- | --- | --- |
| A (recommended) | Induction and training days from the 008 attendance roll-up (attended of available) | No 024 change; matches the Attendance report |
| B | Sessions attended from the 024 training calendar | Needs a read-only per-Teacher attendance count added to 024; may differ from attendance |
| C | Both columns | Two figures to explain; the 024 addition as in B |
