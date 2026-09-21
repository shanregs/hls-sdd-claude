# Quickstart: Organization — Manager/School/Teacher Accountability

Validates the acceptance scenarios in spec.md end to end. Assumes the `organization` module's tasks (data-model.md, contracts/organization-api.yaml) are implemented.

## Prerequisites

- PostgreSQL reachable (Testcontainers for automated tests; a local/dev Postgres for manual exercise), Flyway migrations `V1__create_identity_tables.sql` and `V2__create_organization_tables.sql` both applied
- Backend running (`./mvnw spring-boot:run` from `backend/`)
- A Director or Admin `User` row seeded in `identity_user` (Identity & Access, spec 002) with a known password — every call below authenticates for real against Identity's `/api/v1/auth/login`, there is no debug-header shortcut anymore (research.md §6)

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber":"+919800000001","password":"correct-password"}' | jq -r .accessToken)
# Every curl below sends: -H "Authorization: Bearer $TOKEN"
```

## Scenario 1 — Assign a Manager to a School (User Story 1, acceptance scenario 1)

```bash
SCHOOL_ID=$(uuidgen)
MANAGER_A=$(uuidgen)

curl -s -X POST http://localhost:8080/api/v1/organization/school-assignments \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"schoolId\":\"$SCHOOL_ID\",\"managerId\":\"$MANAGER_A\"}"
# → 200, CurrentAssignment with managerId = MANAGER_A

curl -s "http://localhost:8080/api/v1/organization/schools/$SCHOOL_ID/accountable-manager" \
  -H "Authorization: Bearer $TOKEN"
# → 200, {"state":"CURRENT_MANAGER","managerId":"<MANAGER_A>"}
```

**Expected outcome**: the accountable-manager query returns Manager A immediately after assignment (SC-001).

## Scenario 2 — Reassign without losing history (User Story 2, acceptance scenarios 1 & 2)

```bash
MANAGER_B=$(uuidgen)
CURRENT_ASSIGNMENT_ID=<id from Scenario 1's response>

curl -s -X POST http://localhost:8080/api/v1/organization/school-assignments \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"schoolId\":\"$SCHOOL_ID\",\"managerId\":\"$MANAGER_B\",\"endsAssignmentId\":\"$CURRENT_ASSIGNMENT_ID\"}"
# → 200, CurrentAssignment with managerId = MANAGER_B

curl -s "http://localhost:8080/api/v1/organization/schools/$SCHOOL_ID/accountable-manager" \
  -H "Authorization: Bearer $TOKEN"
# → {"state":"CURRENT_MANAGER","managerId":"<MANAGER_B>"}

curl -s "http://localhost:8080/api/v1/organization/schools/$SCHOOL_ID/accountable-manager?asOf=<a timestamp before the reassignment>" \
  -H "Authorization: Bearer $TOKEN"
# → {"state":"CURRENT_MANAGER","managerId":"<MANAGER_A>"}

curl -s "http://localhost:8080/api/v1/organization/schools/$SCHOOL_ID/assignment-history" \
  -H "Authorization: Bearer $TOKEN"
# → [ {managerId: MANAGER_A, effectiveTo: <reassignment time>}, {managerId: MANAGER_B, effectiveTo: null} ]
```

**Expected outcome**: current query returns Manager B; a past-dated query still returns Manager A; history shows both periods with no gap between them (SC-003).

## Scenario 3 — Conflicting reassignment is rejected (FR-011, Edge Case 2)

```bash
# $TOKEN_ADMIN: log in as a second Director/Admin the same way $TOKEN was obtained
# above, to show the conflict isn't specific to one caller's token.
# Two requests both naming the same $CURRENT_ASSIGNMENT_ID as endsAssignmentId,
# fired concurrently (e.g. via `xargs -P2` or two terminal tabs):
curl -s -X POST http://localhost:8080/api/v1/organization/school-assignments \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"schoolId\":\"$SCHOOL_ID\",\"managerId\":\"<Manager C>\",\"endsAssignmentId\":\"$CURRENT_ASSIGNMENT_ID\"}" &
curl -s -X POST http://localhost:8080/api/v1/organization/school-assignments \
  -H "Authorization: Bearer $TOKEN_ADMIN" -H "Content-Type: application/json" \
  -d "{\"schoolId\":\"$SCHOOL_ID\",\"managerId\":\"<Manager D>\",\"endsAssignmentId\":\"$CURRENT_ASSIGNMENT_ID\"}" &
wait
```

**Expected outcome**: exactly one request returns 200; the other returns 409 with a `ConflictError` body — never both 200, never a silently overwritten row (SC-004).

## Scenario 4 — School and Teacher accountability stay independent (FR-013, User Story 2 acceptance scenario 4)

```bash
TEACHER_ID=$(uuidgen)
MANAGER_A=$(uuidgen)   # teacher's own manager
MANAGER_B=$(uuidgen)   # school's manager, about to change

curl -s -X POST http://localhost:8080/api/v1/organization/teacher-assignments \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"teacherId\":\"$TEACHER_ID\",\"managerId\":\"$MANAGER_A\"}"

# ... reassign the teacher's school (Scenario 2's flow, with a different manager) ...

curl -s "http://localhost:8080/api/v1/organization/teachers/$TEACHER_ID/accountable-manager" \
  -H "Authorization: Bearer $TOKEN"
# → still {"state":"CURRENT_MANAGER","managerId":"<MANAGER_A>"} — unchanged by the school reassignment
```

**Expected outcome**: the Teacher's own accountable Manager is untouched by the School's reassignment.

## Scenario 5 — Unassigned items are visible (User Story 3, FR-009, Edge Case 1, SC-005)

```bash
SCHOOL_ID_2=$(uuidgen)
MANAGER_A=$(uuidgen)

curl -s -X POST http://localhost:8080/api/v1/organization/school-assignments \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"schoolId\":\"$SCHOOL_ID_2\",\"managerId\":\"$MANAGER_A\"}"
ASSIGNMENT_ID=<id from the response above>

curl -s -X DELETE "http://localhost:8080/api/v1/organization/school-assignments/$ASSIGNMENT_ID" \
  -H "Authorization: Bearer $TOKEN"
# → 204, no replacement assigned

curl -s "http://localhost:8080/api/v1/organization/unassigned?itemType=SCHOOL" \
  -H "Authorization: Bearer $TOKEN"
# → includes { "itemType":"SCHOOL", "itemId":"<SCHOOL_ID_2>", "lastEndedAt": "<the DELETE's timestamp>" }
```

**Expected outcome**: a School whose only assignment was ended without a replacement appears on the unassigned list within the same session (SC-005), distinguishable via `lastEndedAt` from a School that was never assigned at all (which would show `lastEndedAt: null`).

## Automated equivalents

Every scenario above has a corresponding `OrganizationIntegrationTest` (Testcontainers, now exercising real JWT bearer auth end to end via Identity's `/api/v1/auth/login`) case and, for the conflict/no-op/independence rules specifically, an `AccountabilityServiceTest` unit case — see plan.md's Project Structure. Run:

```bash
cd backend && ./mvnw test -Dtest=AccountabilityServiceTest,OrganizationIntegrationTest,OrganizationModuleTest
```

The frontend `AssignmentsPage` (new in this re-plan, research.md §8) has its own `AssignmentsPage.test.tsx` covering assign/reassign/portfolio/unassigned-list rendering with a mocked `authClient`, following the same pattern as Identity's `SessionsPage.test.tsx`:

```bash
cd frontend && npx vitest run src/pages/AssignmentsPage
```
