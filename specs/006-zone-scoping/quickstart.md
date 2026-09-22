# Quickstart: Zone-Based Manager Scoping

Validates the acceptance scenarios in spec.md end to end. Assumes this feature's tasks (data-model.md, contracts/zone-api.yaml) are implemented, and the existing `AccountabilityServiceTest`/`OrganizationIntegrationTest` fixtures have been updated (plan.md Summary).

## Prerequisites

- PostgreSQL reachable, all Flyway migrations through this feature's applied
- Backend running (`./mvnw spring-boot:run` from `backend/`)
- A Director or Admin `User` row seeded in `identity_user`, logged in for a real bearer token

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber":"+919800000001","password":"correct-password"}' | jq -r .accessToken)
```

## Scenario 1 — Create a Zone and assign two Managers to it (User Story 1)

```bash
ZONE_ID=$(curl -s -X POST http://localhost:8080/api/v1/organization/zones \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"North Chennai"}' | jq -r .id)

MANAGER_A=$(uuidgen); MANAGER_B=$(uuidgen)
curl -s -X POST http://localhost:8080/api/v1/organization/zones/$ZONE_ID/managers \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d "{\"managerId\":\"$MANAGER_A\"}"
curl -s -X POST http://localhost:8080/api/v1/organization/zones/$ZONE_ID/managers \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d "{\"managerId\":\"$MANAGER_B\"}"

curl -s http://localhost:8080/api/v1/organization/zones/$ZONE_ID -H "Authorization: Bearer $TOKEN"
# → {"id":"...","name":"North Chennai","managerIds":["<A>","<B>"],"schoolIds":[]}
```

**Expected outcome**: both Managers are simultaneously current for the same Zone.

## Scenario 2 — A School's Manager must come from its Zone (User Story 2)

```bash
SCHOOL_ID=$(uuidgen)
curl -s -X POST http://localhost:8080/api/v1/organization/school-zone-assignments \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"schoolId\":\"$SCHOOL_ID\",\"zoneId\":\"$ZONE_ID\"}"

# Manager A (in the zone) — succeeds.
curl -s -X POST http://localhost:8080/api/v1/organization/school-assignments \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"schoolId\":\"$SCHOOL_ID\",\"managerId\":\"$MANAGER_A\"}"
# → 200

# Manager X (never assigned to this zone) — rejected.
MANAGER_X=$(uuidgen)
curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/api/v1/organization/school-assignments \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"schoolId\":\"$(uuidgen)\",\"managerId\":\"$MANAGER_X\"}"
# → 422 (this second schoolId has no Zone at all yet — FR-005's "no Zone" case)
```

**Expected outcome**: assigning within the Zone's Manager pool succeeds; assigning outside it (or to a School with no Zone) is rejected with `422`, never `200` (SC-001/SC-002).

## Scenario 3 — Zone coverage view (User Story 3)

```bash
curl -s http://localhost:8080/api/v1/organization/zones/$ZONE_ID -H "Authorization: Bearer $TOKEN"
# → managerIds: [A, B], schoolIds: [SCHOOL_ID] — one lookup, full current picture (SC-003)
```

## Scenario 4 — Teacher accountability is completely unaffected (SC-004)

```bash
TEACHER_ID=$(uuidgen); TEACHER_MANAGER=$(uuidgen)
curl -s -X POST http://localhost:8080/api/v1/organization/teacher-assignments \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"teacherId\":\"$TEACHER_ID\",\"managerId\":\"$TEACHER_MANAGER\"}"
# → 200 — succeeds with NO zone/zone-manager setup at all; this endpoint's
# behavior is byte-for-byte what specs/003 already shipped (FR-007/FR-008)
```

**Expected outcome**: unlike `school-assignments`, `teacher-assignments` has no Zone precondition — it works exactly as it did before this feature, proving the "untouched" claim rather than just asserting it.

## Automated equivalents

Every scenario above has a corresponding `ZoneServiceTest` (unit) and `OrganizationIntegrationTest` (Testcontainers, real JWT auth) case, plus updated `AccountabilityServiceTest` cases for the new precondition — see plan.md's Project Structure. Run:

```bash
cd backend && ./mvnw test -Dtest=ZoneServiceTest,AccountabilityServiceTest,OrganizationIntegrationTest,OrganizationModuleTest
```

The extended `AssignmentsPage` (Zone section, research.md §6) has its Zone-related cases added to its existing `AssignmentsPage.test.tsx`:

```bash
cd frontend && npx vitest run src/pages/AssignmentsPage
```
