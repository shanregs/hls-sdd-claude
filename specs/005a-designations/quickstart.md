# Quickstart: Designations and Employment Details

Runnable checks that prove the feature end to end. Setup is in `docs/running-locally.md`. Demo logins (all
`Password123!`): Asha Admin `9800000001`, Divya Director `9800000002`, Manoj Manager (Zone Manager) `9800000003`,
Tara Teacher `9800000004`, Sunil System `9800000005`. Endpoints: `contracts/designations-api.md`; tables:
`data-model.md`.

## Prerequisites

Specs 001 to 005, 008, 009, 012 running locally with demo data. Flyway applies `V24`; existing Managers and Teachers
keep working with empty new fields.

## Scenarios

1. **The list (US1)**: as Asha open MASTER DATA -> Designations. The demo data shows a few designations. Add
   "Primary Teacher" (Teachers); rename it; retire it; reactivate it. A duplicate name for the same kind, an empty
   name or one over 80 characters is refused naming the problem; "Coordinator" for both kinds is allowed. Deleting
   (API `DELETE`) answers 409. As Manoj, Tara and Sunil the menu item is absent and `GET /api/v1/designations` is 403.
2. **A Manager's details (US2)**: open Manoj on the Managers screen: "designation missing" and "joining date missing".
   Set designation "Zone Manager", employee id `E-1001`, joining date. An employee id already used by a Teacher
   (differing only by capitals) is refused naming that person. Record a promotion with a later effective date: both
   rows stay in the history; a date before the first of this month is refused. Deactivate Manoj's account in User
   Management: his exit date becomes today; edit it to up to 90 days ahead; reactivate: the exit date clears and the
   old value is in the audit log. As Manoj the fields are visible with no edit controls; `PUT .../employment` is 403.
3. **A Teacher's details (US3)**: set a designation and an employee id on Tara; a Zone Manager reads them for a
   Teacher in their School and cannot change them; Tara's My Profile shows neither field.
4. **Missing details (US4)**: the Designations screen shows counts of people missing a designation (and Managers
   missing a joining or exit date); the links open the Managers and Teachers screens filtered, listing exactly those
   people; when every person has the fields the counts are zero.
5. **Audit**: MASTER DATA changes above appear in the audit change history with who, when and old and new values.

## Automated checks

`mvn -o test -Dtest=DesignationApiTest` and the other classes named in `tasks.md`; `npm test`, `npm run typecheck`,
`npm run lint` in `frontend/`.
