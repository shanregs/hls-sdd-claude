# Quickstart: Zone-Based Manager Scoping (reworked)

Validates the acceptance scenarios in spec.md end to end. Unlike this spec's original quickstart, Zone creation and School↔Zone assignment go through `school`'s real, already-shipped endpoints (`specs/007-school-zone/quickstart.md`), not endpoints this feature defines.

## Prerequisites

- PostgreSQL reachable, all Flyway migrations through this feature's applied (`V1`-`V8`)
- Backend running (`./mvnw spring-boot:run` from `backend/`)
- A Director or Admin `User` row seeded in `identity_user`, logged in for a real bearer token

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber":"+919800000001","password":"correct-password"}' | jq -r .accessToken)

# Zone creation is school's capability (specs/007), not this feature's.
ZONE_ID=$(curl -s -X POST http://localhost:8080/api/v1/school/zones \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"North Chennai"}' | jq -r .id)
```

## Scenario 1 — Assign two Managers to cover an existing Zone (User Story 1)

```bash
MANAGER_A=$(uuidgen); MANAGER_B=$(uuidgen)
curl -s -X POST http://localhost:8080/api/v1/organization/zone-manager-assignments \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"zoneId\":\"$ZONE_ID\",\"managerId\":\"$MANAGER_A\"}"
curl -s -X POST http://localhost:8080/api/v1/organization/zone-manager-assignments \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"zoneId\":\"$ZONE_ID\",\"managerId\":\"$MANAGER_B\"}"

curl -s http://localhost:8080/api/v1/organization/zones/$ZONE_ID/coverage -H "Authorization: Bearer $TOKEN"
# → {"zoneId":"...","managerIds":["<A>","<B>"],"schoolIds":[]}
```

**Expected outcome**: both Managers are simultaneously current for the same Zone.

## Scenario 2 — A School's Manager must come from its Zone's covering Managers (User Story 2)

```bash
SCHOOL_ID=$(uuidgen)
# Assigning a School to a Zone is school's capability (specs/007), not this feature's.
curl -s -X POST http://localhost:8080/api/v1/school/school-zone-assignments \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"schoolId\":\"$SCHOOL_ID\",\"zoneId\":\"$ZONE_ID\"}"

# Manager A (covers this zone) — succeeds.
curl -s -X POST http://localhost:8080/api/v1/organization/school-assignments \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"schoolId\":\"$SCHOOL_ID\",\"managerId\":\"$MANAGER_A\"}"
# → 200

# Manager X (never assigned to cover this zone), for a School with no Zone at all — rejected.
MANAGER_X=$(uuidgen)
curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/api/v1/organization/school-assignments \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"schoolId\":\"$(uuidgen)\",\"managerId\":\"$MANAGER_X\"}"
# → 422 (this second schoolId has no Zone at all yet — FR-004's "no Zone" case)
```

**Expected outcome**: assigning within the Zone's covering-Manager pool succeeds; assigning outside it (or to a School with no Zone) is rejected with `422`, never `200` (SC-001/SC-002).

## Scenario 3 — Zone coverage view (User Story 3)

```bash
curl -s http://localhost:8080/api/v1/organization/zones/$ZONE_ID/coverage -H "Authorization: Bearer $TOKEN"
# → managerIds: [A, B], schoolIds: [SCHOOL_ID] — one lookup, full current picture (SC-003);
#   schoolIds comes from a live call to school.api.ZoneQueries.currentSchoolsForZone, not a local copy
```

## Scenario 4 — Teacher accountability is completely unaffected (SC-004)

```bash
TEACHER_ID=$(uuidgen); TEACHER_MANAGER=$(uuidgen)
curl -s -X POST http://localhost:8080/api/v1/organization/teacher-assignments \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"teacherId\":\"$TEACHER_ID\",\"managerId\":\"$TEACHER_MANAGER\"}"
# → 200 — succeeds with NO zone/zone-coverage setup at all; this endpoint's
# behavior is byte-for-byte what specs/003 already shipped (FR-006/FR-007)
```

**Expected outcome**: unlike `school-assignments`, `teacher-assignments` has no Zone precondition — it works exactly as it did before this feature, proving the "untouched" claim rather than just asserting it.

## Automated equivalents

Every scenario above has a corresponding, extended `AccountabilityServiceTest` (unit) and `OrganizationIntegrationTest` (Testcontainers, real JWT auth, real `school` endpoints for Zone/School-Zone setup — not a stand-in) case. Run:

```bash
cd backend && ./mvnw test -Dtest=AccountabilityServiceTest,OrganizationIntegrationTest,OrganizationModuleTest
```

The extended `AssignmentsPage` (Zone-Manager section, research.md §9) has its Zone-related cases added to its existing `AssignmentsPage.test.tsx`:

```bash
cd frontend && npx vitest run src/pages/AssignmentsPage
```
