# Quickstart: School Marketing and MoU Pipeline

Runnable checks that prove the feature end to end. Setup is in `docs/running-locally.md`. Demo logins (all
`Password123!`): Asha Admin `9800000001`, Divya Director `9800000002`, Manoj Manager (Zone Manager of Demo Zone)
`9800000003`, Tara Teacher `9800000004`, Sunil System `9800000005`. Endpoints are in `contracts/marketing-api.md`,
tables in `data-model.md`.

## Prerequisites

- Specs 001 to 005, 010 and 012 merged; demo data on. Flyway applies `V23`. Spec 016 is not needed (supply then shows
  "not available").
- The demo data adds three prospects in Demo Zone (one in Negotiation with a proposal, one lost, one won with its
  School and an MoU) and one in a second Zone that Manoj does not manage.

## Scenarios

1. **Prospects and visits (US1)**: as Manoj add a prospect in Demo Zone; adding the same name, board and Zone again is
   refused and the existing one is shown. Plan a visit with two attendees; it shows on both calendars. Complete it
   without an outcome: refused. Complete it with an outcome and a follow-up date: the prospect shows "follow-up
   overdue" once the date passes. Reschedule a visit: the earlier date stays in its history; leave one planned past its
   date: it shows as missed. Attach a photo and a PDF to a visit; download both; an `.exe` or an 11 MB file is refused.
   Manoj cannot open the prospect of the second Zone (404).
2. **Pipeline and review (US2)**: move a prospect through the stages on the board; put it On Hold and resume it; mark
   one Lost with a reason and reopen it; every step is in the history. Move one to Final Stage without a proposal:
   refused. With a proposal, move it to Final Stage and, as Divya, approve: it is won. Reject another: it returns to
   Negotiation with the reason. As Asha the review buttons are missing and the API answers 403.
3. **Proposals (US3)**: record a proposal for 4 Teachers at one amount, then revise it to 5 Teachers with different
   amounts per position: both revisions show, the latest current, the monthly total computed, each labelled "Proposal
   (not a contract)". A missing or non-positive amount is refused.
4. **Win and hand-off (US4)**: as Asha open the won prospect's Win dialog, pick a Place of the Zone and create the
   School; the MoU form of spec 012 opens pre-filled from the proposal. Save the MoU: the prospect shows MoU and "0 of 5
   positions filled"; map a Teacher in 012 and it shows Active. As Manoj a won prospect offers no School creation and no
   MoU recording. Leave a won prospect without an MoU past the limit (set it to 1 day in Settings and run the daily
   job): it is flagged on the board and the owner, Manoj and every Admin and Director get one notification; running
   again sends nothing; changing the limit and passing it again sends a new one.
5. **Dashboard (US5)**: as Divya open the dashboard: visits this month, prospects by stage, win rate, Schools won per
   Zone and owner, and demand against supply (supply "not available" without 016, or the ready-to-deploy count with it).
   Manoj sees only his Zone.
6. **Boundary and degrade**: the module works with 012 absent (no hand-off, no MoU status) and with 016 absent; run
   `mvn -o test -Dtest=ApplicationModulesTest,MarketingModuleRulesTest`.
