# Quickstart: Campus Recruitment, Job Offers and Induction

Runnable checks that prove the feature end to end. Setup is in `docs/running-locally.md`. Demo logins (all
`Password123!`): Asha Admin `9800000001`, Divya Director `9800000002`, Manoj Manager (Zone Manager) `9800000003`, Tara
Teacher `9800000004`, Sunil System `9800000005`. Endpoints are in `contracts/recruitment-api.md`, tables in
`data-model.md`.

## Prerequisites

- Specs 001 to 005, 008, 009, 010 and 012 merged; demo data on. Flyway applies `V22`.
- The demo data adds one college, one held drive with six candidates (two selected, one with an issued offer), one
  induction batch and one recruit "Ready Rani" who completed it.

## Scenarios

1. **Drive and candidates (US1)**: as Manoj schedule a drive for the demo college on two dates with two interviewers;
   it shows on the calendar as planned. Import a CSV of ten candidates (one invalid row): nine saved, one reported
   with its row and reason; import the same file again: nothing is saved twice. Record outcomes (4 selected, 2
   waitlisted, 4 rejected) and an assessment with a score per criterion; change one outcome and see both in the history.
   Open a drive Manoj did not schedule: he can read it but every button that writes is missing, and a direct write is
   403 "not your drive".
2. **Offers (US2)**: as Divya send an offer to a selected candidate with a salary, a deadline and a letter (open the
   printable letter); a second open offer for the same candidate is refused. Supersede it with a new package: the old
   offer shows SUPERSEDED. Let one pass its deadline (set it to yesterday in the database and run the expiry job): it
   becomes EXPIRED and accepting it is refused. As Asha the offers are visible but there is no Send or Accept; as Manoj
   they are visible and read-only; Sunil has no menu item and the API answers 403.
3. **Accept and first salary (US3)**: as Divya accept an offer: one Teacher "in training" appears in Teachers with the
   candidate's details and **no salary entry**; accept again: 409, still one Teacher. Accept an offer whose phone
   matches a working Teacher: refused naming that Teacher. Now (spec 012) map a trained Teacher to a School position:
   the Teacher's salary history gets one entry equal to the accepted offer's salary, effective on the assignment date.
4. **Induction (US4)**: as Asha create a batch, enrol the recruit, record five present days and one absence with a
   reason, then open the recruit's attendance (spec 008): five training days (code T) with no School, equal to what the
   rollup reports. A locked month refuses an entry with the lock reason. Sign off as completed: the Teacher becomes
   active and appears in Ready to deploy; sign another off as not completed and move them to the next batch: the earlier
   sign-off stays visible. Mapping a Teacher still in training in spec 012 is refused.
5. **Dashboard (US5)**: as Divya open the dashboard: per college and season the drives, assessed, selected, offered,
   accepted, inducted, ready to deploy, placed and active, and the joining ratio; counts equal the lists. Manoj sees
   the same counts with a "my drives" filter. Ready to deploy drops a recruit once they are mapped to a School.
6. **Boundary**: `recruitment` and `training` are used only through their `api` packages, and `schoolbilling` does not
   depend on either; run `mvn -o test -Dtest=ApplicationModulesTest,RecruitmentModuleRulesTest`.
