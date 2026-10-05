# 023 School Marketing and MoU Pipeline: `/speckit-specify` input

2026-10-05. Roadmap row 023 (high priority; runs in parallel with 012/022 and with 016). Replaces the "marketing"
half of the old combined HR-calendars spec. Source requirements: `HLS Teacher Management System — Requirements.md`
§9a; `docs/hls-speckit-specify-inputs.md` §13, widened here from a "lightweight log" to the full path from a first
visit to a signed MoU, because this is one of HLS's two core flows.

Depends on 005 (Schools, Zones, Zone Managers), 003 (audit), 012 (the signed MoU is recorded there). Its calendar and
pipeline do not need 012 to be merged; only the last story (hand-off to the contract) does.

The business process: **School prospect identified → visits and meetings by the Director, Zone Managers and the
marketing team → proposal (number of Teachers, salary basis) → MoU negotiated → MoU signed (recorded in 012) →
Teachers deployed (mapping in 012) → billing (022)**. It runs at the same time as campus recruitment (016): marketing
wins the schools, recruitment supplies the Teachers.

## Feature description (paste as the argument to `/speckit-specify`)

023-school-marketing-mou: HLS's marketing team actively visits schools to sign MoUs for placing its recruited
Teachers. This spec captures that activity and its workflow up to the signed MoU. Module `recruitment.marketing` (a
sub-package, as the architecture says), reading Schools and Zones from `organization`/`school` and handing the signed
MoU to `schoolbilling` (spec 012).

1. **School prospects.** A prospect is a School HLS is approaching, with name, board, address, Zone, contact person and
   designation, phone, expected strength, and an owner (a Director, a Zone Manager or another marketing user). A prospect
   that is already a School in spec 005 links to it; one that is not is turned into a School record on winning.
2. **Marketing calendar and visits.** Director, Zone Managers and the marketing team log planned and completed
   activity against a prospect or a School: visit, call, proposal meeting, follow-up, with date, who attends, notes and
   outcome. A calendar (month and list) shows them, with a person's own entries and overdue follow-ups highlighted. The
   same people may be on recruitment drives (016) and school visits; the two calendars are shown side by side for a
   person so clashes are visible.
3. **Pipeline.** Each prospect moves through stages: identified → contacted → meeting held → proposal sent →
   negotiating → won or lost (with a reason). A board and a list show prospects by stage, owner and Zone, with a
   next-action date.
4. **Proposal terms.** On a prospect the user records the proposed MoU: number of Teachers, start month, and whether the
   salary the School would pay is **one amount for all Teachers** or **a different amount for each Teacher**
   (amounts per position), plus notes. Each revision is kept. The proposal is a working draft, not the contract.
5. **Hand-off to the MoU.** When the School agrees, the prospect is marked won and the user is taken to record the
   MoU in spec 012 (Admin or Director), pre-filled from the latest proposal (School, number of Teachers, salary mode
   and amounts, start date, and who from HLS attended). The MoU, with its signatories and signed date, stays in
   spec 012 as the only contract record; this spec only keeps the link to it and shows its status on the prospect (MoU
   recorded, Teachers mapped, positions vacant), so marketing can see whether a won school still needs Teachers.
6. **Dashboard.** Admin and Director see visits this month, prospects by stage, win rate, Schools won per Zone and per
   owner, won Schools still waiting for Teachers (demand) next to recruits ready to deploy (supply, from 016).

Out of scope: a full CRM (email, campaigns, quotes), uploading the signed MoU (012 out of scope too), billing (022),
mobile screens, e-signature.

## Role & Permission Impact (to be developed in the spec)

- Admin, Director: everything, org-wide; only Admin and Director can record the MoU (012 rule is unchanged).
- Manager (Zone Manager): create and update prospects, visits and proposals in their own Zones; see only their Zones'
  prospects (Constitution Principle III); view the MoU status of their prospects.
- Marketing team members who are not Managers: **no new role** is added (the constitution fixes five roles). Open
  question below on how they are represented.
- Teacher, System: none.
- New permission module: `MARKETING` (prospects, activities, proposals); the hand-off reuses 012's `SCHOOL_CONTRACTS`
  `CREATE`.

## Points for `/speckit-clarify`

- Who exactly is "the marketing team"? If they are existing Directors, Zone Managers or Admin users, nothing new is
  needed. If they are separate people, they need a login: which of the five roles (Admin/Director/Manager) do they
  hold, and what scope? A new role would be a constitution change.
- Are prospects Zone-scoped (a Zone Manager sees only their Zone's) and can a Director or marketing user work across
  Zones?
- Is a prospect's owner changeable, and can several people attend one visit?
- Must a School record exist (spec 005) before a prospect can be proposed to, or is it created on winning?
- Can the same School have a new proposal after an MoU ends (renewal)? Is a renewal a new prospect or a stage on the
  School?
- Lost prospects: can they be reopened, and after how long are they dropped from the board?
- Does the proposal's salary basis have to match what the signed MoU says, or is the MoU free to differ (it is only
  pre-filled)?

## Additions from `docs/HLS-core-business-flow.md` (2026-10-05, not yet in the spec)

- Visit status flow (planned, confirmed, visited, report submitted, follow-up required); a visit must have an outcome before it can be closed.
- On Hold stage; a Final stage with management review before the MoU.
- Manager activity plan with missed and rescheduled activities; today, tomorrow and this week views.
- Principal and management contacts; attachments on visits.
- Activity timeline per School and per Manager.
- Link to incentives (030) once a School is confirmed.
- Open: who approves the MoU (D5).
