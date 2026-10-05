# Quickstart: School Contracts (MoU)

Runnable checks that prove the feature end to end. Setup is in `docs/running-locally.md` (database, backend with
demo data, frontend). Demo logins (all `Password123!`, or a one-time code): Asha Admin `9800000001`, Divya Director
`9800000002`, Manoj Manager (Zone Manager) `9800000003`, Tara Teacher `9800000004`, Sunil System `9800000005`.
Endpoints are in `contracts/school-contracts-api.md`; tables in `data-model.md`.

## Prerequisites

- Specs 001 to 005, 008, 009 and 010 running locally; demo data on.
- Flyway applies `V20` and `V21`; the log shows both. Existing Teacher placements now appear as assignments and the
  Schools that had them have a "MoU pending" contract.

## Scenarios

1. **Placements carried over, attendance unchanged (US2)**: before starting the upgraded backend, note each demo
   Teacher's School and start date on the Teachers screen and one month of Tara's My Attendance and leave history.
   After it, everything is identical; the Teachers screen says "assignment", never "interim". `teacher_placement`
   no longer exists and the `contract_assignment` row count equals the old count.
2. **MoU, same salary for all (US1)**: as Asha open OPERATIONS → School Contracts → Demo School One. It shows "MoU
   pending". Record the MoU: 4 Teachers, same salary ₹15,000, from the 1st of this month, signed on a past date, a
   School signatory (Principal), the Zone Manager (Manoj) and a Director (Divya). It saves with 4 positions at
   ₹15,000. HLS signatories of Zone Manager only, Director only, or two Directors also save (automated test T018);
   with no HLS signatory it is refused. Saving without the signed date, without a School signatory, or with a future signed date names the
   missing item.
3. **MoU, different salaries (US1)**: record a new MoU for Demo School Two for 3 Teachers at ₹15,000, ₹18,000 and
   ₹20,000 (with position titles). It cannot be saved until each position has an amount. Record a second MoU for
   Demo School One from next month: the first ends the day before, both are in the history with their signatories.
   As Manoj the screen shows the contracts but no Create or End; `POST /school-contracts/schools/{id}/contracts`
   answers 403. A School with no Zone Manager refuses a contract with the instruction to assign one.
4. **Map recruited Teachers (US3)**: as Manoj map Tara to a vacant position of Demo School One from today: the
   School shows 1 filled and 3 vacant. Map three more; a fifth is refused ("All 4 positions are filled; record a new
   contract to add Teachers."). On the different-salary contract the position must be chosen and its salary shows.
   Mapping a Teacher to a School with no contract refuses with "Record the MoU for this School first."
5. **Re-map to a new MoU (US3)**: as Asha record the next-month MoU for Demo School One and use "Map Teachers to the
   new contract": the Teachers' old assignments end the day before, new ones start under the new positions, the
   Teachers screen shows one continuous School, and My Attendance for the month shows no gap.
6. **Maintain (US4)**: the School Contracts list shows each School with status, filled and vacant positions and end
   date; filter by "MoU pending" and by Zone Manager; set an end date within 30 days on one contract: it shows "ends
   soon". As Manoj the list holds only his Schools. Sunil (System) has no menu item and the API answers 403.
7. **Constraints**: two requests mapping two Teachers to the same position at once: one wins, one is refused. A
   direct database update of a signatory or position is not offered by any endpoint. Audit Logs shows an entry for
   every contract, mapping and re-map above.
8. **Boundary**: `schoolbilling` is not called from `teacher.internal`, and no module reads contract tables; run
   `mvn -o test -Dtest=ApplicationModulesTest,SchoolBillingModuleRulesTest`.
