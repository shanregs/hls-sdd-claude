# Quickstart: Teacher Master Data

Validates the acceptance scenarios in spec.md end to end. Assumes the `teacher` module's tasks (data-model.md, contracts/teacher-api.yaml) are implemented, including the Identity `TokenService` touch (research.md §3).

## Prerequisites

- PostgreSQL reachable (Testcontainers for automated tests; a local/dev Postgres for manual exercise), Flyway migrations `V1`-`V4` all applied
- Backend running (`./mvnw spring-boot:run` from `backend/`)
- An Admin, a Director, and a Manager `User` row seeded in `identity_user` (Identity & Access, spec 002) with known passwords
- Every call below authenticates for real against Identity's `/api/v1/auth/login`

```bash
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber":"+919800000001","password":"correct-password"}' | jq -r .accessToken)
DIRECTOR_TOKEN=$(... same pattern, a Director-role user ...)
MANAGER_A_TOKEN=$(... same pattern, a Manager-role user ...)
```

## Scenario 1 — Admin creates a profile (User Story 1)

```bash
curl -s -X POST http://localhost:8080/api/v1/teachers \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Priya Sharma","phone":"+919811111111","bankAccountHolderName":"Priya Sharma","bankAccountNumber":"1234567890","bankName":"HDFC Bank","ifscCode":"HDFC0001234","hlsOfferedSalary":17000,"status":"IN_TRAINING"}'
# → 200, TeacherProfileView with a new id
TEACHER_ID=<id from the response above>
```

**Expected outcome**: the profile is immediately retrievable (see Scenario 3) with exactly the details entered (SC-001).

## Scenario 2 — Admin updates salary and changes status, both tracked (User Story 2)

```bash
curl -s -X PATCH http://localhost:8080/api/v1/teachers/$TEACHER_ID \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"hlsOfferedSalary":18000}'
# → 200, hlsOfferedSalary now 18000

curl -s -X POST http://localhost:8080/api/v1/teachers/$TEACHER_ID/status \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"status":"ACTIVE"}'
# → 200, status now ACTIVE

curl -s "http://localhost:8080/api/v1/audit/TeacherProfile/$TEACHER_ID/history" \
  -H "Authorization: Bearer $DIRECTOR_TOKEN"
# → 3 entries: CREATED, then the salary UPDATE (17000 -> 18000), then the status UPDATE (IN_TRAINING -> ACTIVE)
```

**Expected outcome**: both changes are retrievable as history via the real Audit module — the prior salary and prior status are never lost (SC-002).

## Scenario 3 — Director views any profile; Manager only a currently-assigned one (User Story 3)

```bash
# Director: always allowed.
curl -s http://localhost:8080/api/v1/teachers/$TEACHER_ID -H "Authorization: Bearer $DIRECTOR_TOKEN"
# → 200

# Manager A is not yet assigned to this teacher: denied.
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/v1/teachers/$TEACHER_ID \
  -H "Authorization: Bearer $MANAGER_A_TOKEN"
# → 403

# Assign Manager A to this teacher via Organization's real endpoint (spec 003)...
curl -s -X POST http://localhost:8080/api/v1/organization/teacher-assignments \
  -H "Authorization: Bearer $DIRECTOR_TOKEN" -H "Content-Type: application/json" \
  -d "{\"teacherId\":\"$TEACHER_ID\",\"managerId\":\"<Manager A's userId>\"}"

# ...now Manager A can view it.
curl -s http://localhost:8080/api/v1/teachers/$TEACHER_ID -H "Authorization: Bearer $MANAGER_A_TOKEN"
# → 200
```

**Expected outcome**: view access tracks Organization's current assignment in real time (SC-003) — no code in `teacher` itself decides this, it's `identity.api.ManagerScopeQueries` reading Organization live.

## Scenario 4 — A Teacher views only their own profile, read-only (User Story 4)

```bash
# A logged-in Teacher whose identity_user row has linkedTeacherId = $TEACHER_ID:
TEACHER_TOKEN=$(... login as that Teacher via /api/v1/auth/otp/verify or password login ...)

curl -s http://localhost:8080/api/v1/teachers/me -H "Authorization: Bearer $TEACHER_TOKEN"
# → 200, the same profile

# No PATCH/status endpoint is ever called by this token in this scenario — there is
# no "teacher self-edit" capability offered anywhere in the API for FR-009 to violate.
```

**Expected outcome**: a Teacher retrieves their own profile via the new `/me` endpoint, with no edit path available to them at all (SC-004).

## Automated equivalents

Every scenario above has a corresponding `TeacherServiceTest` (unit) and `TeacherIntegrationTest` (Testcontainers, real JWT auth, real Organization assignment, real Audit history read-back) case — see plan.md's Project Structure. Run:

```bash
cd backend && ./mvnw test -Dtest=TeacherServiceTest,TeacherIntegrationTest,TeacherModuleTest,TokenServiceTest
```

The frontend `TeacherProfilesPage` (Admin/Director/Manager) and `MyProfilePage` (Teacher) have their own `*.test.tsx` files, following `AssignmentsPage.test.tsx`'s pattern:

```bash
cd frontend && npx vitest run src/pages/TeacherProfilesPage src/pages/MyProfilePage
```
