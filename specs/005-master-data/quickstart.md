# Quickstart: Validating Master Data

Prerequisites: backend running against PostgreSQL (Flyway migrated through `V13`), specs 001-004
implemented, `hls.seed.demo-data=true` so the dev users exist (Admin `9800000001`, Director
`9800000002`, Manager `9800000003`, Teacher `9800000004`, System `9800000005`; password
`Password123!` where set). The Postman collection (`postman/`) has a folder per scenario. Endpoints
are in [contracts/master-data-api.md](./contracts/master-data-api.md).

## Scenario 1: Zones, Places and lookup (User Story 1)

1. As Admin, create Zone "Chengalpattu"; add Places "Madurantakam" (603306) and "Maraimalai Nagar"
   (603209). **Expect**: both listed under the Zone; the Zone shows 2 Places, 0 Schools, 0 Managers.
2. Add a second Place named "Madurantakam" with a different PIN code. **Expect**: accepted; looking
   up by name returns both.
3. Look up PIN 603306. **Expect**: the Place and its Zone.
4. Sign in as Manager, Teacher and System. **Expect**: no Zones menu item; direct route shows "not
   authorized"; `GET /api/v1/zones` is 403.

## Scenario 2: Bulk import Places (User Story 8)

1. Import 20 rows where 3 have bad PIN codes and 2 repeat existing Places. **Expect**: 15 added, 2
   `ALREADY_EXISTS`, 3 `REJECTED` with reasons and row numbers.
2. Import the same list again. **Expect**: 0 added.
3. Submit an empty list and a 5,001-row list. **Expect**: 400, nothing created.
4. Import 1,000 rows. **Expect**: the report returns in under 30 s.

## Scenario 3: Schools in Places (User Story 2)

1. As Admin, create School "St. Mary's" in Madurantakam. **Expect**: shown with that Place and
   Zone "Chengalpattu", no Manager. Creating without a Place is refused.
2. As Director, edit its billing contact. **Expect**: saved; Change History shows the change.
3. As the (not yet assigned) Manager, list Schools and open the School by id. **Expect**: empty
   list and 404.

## Scenario 4: Managers and assignments (User Story 3)

1. As Admin, create a Manager record for Manoj (Manager role). **Expect**: listed, 0 Zones.
   Try a Teacher user. **Expect**: 400.
2. Assign Manoj to Chengalpattu, then assign "St. Mary's" to Manoj. **Expect**: both succeed.
3. Create a second Zone and School; try to assign that School to Manoj. **Expect**: 409 "must cover
   the School's Zone", nothing changed.
4. Try to remove Manoj from Chengalpattu. **Expect**: 409 naming "St. Mary's".
5. Move "St. Mary's" to a Place in the other Zone. **Expect**: 409 until the Manager is reassigned.
6. As Manoj, edit "St. Mary's" contact phone (allowed) and then its name (**Expect**: 403).

## Scenario 5: Teachers, status and placements (User Story 4)

1. As Admin, create Teacher "Tara" (in training) and link her user. Place her in "St. Mary's"
   effective today. **Expect**: Manoj now sees her; she is labelled "interim placement".
2. Move her to another School effective 7 days from now. **Expect**: shown as scheduled; Manoj still
   sees her. Cancel the move, then re-create it dated in the past before her current placement
   started. **Expect**: refused.
3. Change status in training -> active -> on leave -> active. **Expect**: each saved.
   Try active -> in training. **Expect**: 409.
4. Exit her. **Expect**: placement ended, account link released; any further status or placement
   change is 409; her record remains.

## Scenario 6: Scope never leaks (User Story 5)

1. Create two Zones, two Managers (one each) with Schools and Teachers.
2. As each Manager call `GET /api/v1/schools`, `GET /api/v1/teachers` (with and without `query`
   and filters), their detail endpoints for the *other* Manager's ids, and the dashboard counts.
   **Expect**: only own data; foreign ids are 404; totals reflect only own data.
3. As a user with both Manager and Director roles. **Expect**: organization-wide results.
4. Reassign a School to the other Manager and reload as both. **Expect**: results change on the next
   request with no re-login.

## Scenario 7: Manager dashboard and Teacher profile (User Stories 6-7)

1. Sign in as Manoj. **Expect**: dashboard shows his Zone, School and Teacher counts with links; a
   Manager with no assignments sees the empty state.
2. Sign in as Tara (OTP). Open ACCOUNT -> My Profile. **Expect**: her name, contact, status, current
   School and no salary; no other Teacher is reachable. A Teacher with no record sees "profile not
   set up".

## Scenario 8: Salary history (User Story 9)

1. As Admin, record 20,000 effective 01/04/2026, then 22,000 effective 01/10/2026 for Tara.
   **Expect**: history shows both; current is 22,000.
2. Query as of 15/06/2026 and 15/10/2026. **Expect**: 20,000 and 22,000. As of 01/01/2026.
   **Expect**: "no salary recorded".
3. As Manager, Teacher and System request the salary endpoints and fetch Tara's profile.
   **Expect**: 403 on salary; no salary field anywhere else.
4. As System open Audit -> Change History. **Expect**: no master-data or salary entries visible; as
   Admin they are all present.

## Automated verification

Run the backend suite (`mvn test` in `backend/`) and the frontend suite (`npm run test` in
`frontend/`). Both must be green, including the per-role authorization tests, the two-Manager scope
tests, the placement/status/assignment invariant tests, `ApplicationModulesTest` and the ArchUnit
rules for the three new modules, and the axe-core checks for every new screen in both themes.
