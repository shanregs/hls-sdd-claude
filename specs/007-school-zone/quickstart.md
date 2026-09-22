# Quickstart: School Master Data — Zones

Validates the acceptance scenarios in spec.md end to end.

## Prerequisites

- PostgreSQL reachable, all Flyway migrations through this feature's applied
- Backend running (`./mvnw spring-boot:run` from `backend/`)
- A Director or Admin `User` row seeded in `identity_user`, logged in for a real bearer token

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber":"+919800000001","password":"correct-password"}' | jq -r .accessToken)
```

## Scenario 1 — Create a Zone (User Story 1)

```bash
ZONE_A=$(curl -s -X POST http://localhost:8080/api/v1/school/zones \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"North Chennai"}' | jq -r .id)

curl -s http://localhost:8080/api/v1/school/zones/$ZONE_A -H "Authorization: Bearer $TOKEN"
# → {"id":"...","name":"North Chennai"}
```

## Scenario 2 — Assign, then reassign, a School's Zone (User Story 2)

```bash
SCHOOL_ID=$(uuidgen)
curl -s -X POST http://localhost:8080/api/v1/school/school-zone-assignments \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"schoolId\":\"$SCHOOL_ID\",\"zoneId\":\"$ZONE_A\"}"

curl -s http://localhost:8080/api/v1/school/schools/$SCHOOL_ID/zone -H "Authorization: Bearer $TOKEN"
# → {"state":"CURRENT_ZONE","zoneId":"<ZONE_A>"}

ZONE_B=$(curl -s -X POST http://localhost:8080/api/v1/school/zones \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"South Chennai"}' | jq -r .id)

# Reassign — need the current assignment's id first (not returned by the
# school-zone lookup, same reasoning organization's quickstart already notes):
# in practice the frontend tracks this from the assignment response itself.
```

**Expected outcome**: after reassignment, the School's current Zone is B, and its history still shows the earlier period in A (SC-002) — proven at the automated-test level (`SchoolIntegrationTest`), since this manual walkthrough would need the prior assignment's id captured from Scenario 2's own response.

## Scenario 3 — Conflicting reassignment is rejected (FR-005, SC-003)

```bash
# Two requests both naming the same current assignment id as endsAssignmentId,
# fired concurrently — exactly one returns 200, the other 409. Same pattern as
# organization-api.yaml's school-assignments endpoint; see SchoolIntegrationTest
# for the automated concurrent-request version.
```

## Scenario 4 — View a Zone's Schools; an unassigned School reports UNASSIGNED (User Story 3)

```bash
curl -s http://localhost:8080/api/v1/school/zones/$ZONE_A/schools -H "Authorization: Bearer $TOKEN"
# → ["<schools currently in Zone A>"]

curl -s http://localhost:8080/api/v1/school/schools/$(uuidgen)/zone -H "Authorization: Bearer $TOKEN"
# → {"state":"UNASSIGNED"} — a School that was never assigned, not an error
```

## Automated equivalents

Every scenario above has a corresponding `ZoneServiceTest` (unit) and `SchoolIntegrationTest` (Testcontainers, real JWT auth, including the concurrent-conflict case Scenario 3 only sketches manually) case — see plan.md's Project Structure. Run:

```bash
cd backend && ./mvnw test -Dtest=ZoneServiceTest,SchoolIntegrationTest,SchoolModuleTest
```

The frontend `ZonesPage` has its own `ZonesPage.test.tsx`, following `AssignmentsPage.test.tsx`'s pattern:

```bash
cd frontend && npx vitest run src/pages/ZonesPage
```
