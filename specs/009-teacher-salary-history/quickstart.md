# Quickstart: Teacher Salary History

Validates the acceptance scenarios in spec.md end to end.

## Prerequisites

- PostgreSQL reachable, all Flyway migrations through this feature's applied (`V1`-`V7`)
- Backend running (`./mvnw spring-boot:run` from `backend/`)
- An Admin and a Director `User` row seeded in `identity_user`, logged in for real bearer tokens

```bash
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber":"+919800000001","password":"correct-password"}' | jq -r .accessToken)
DIRECTOR_TOKEN=$(... same pattern, a Director-role user ...)
```

## Scenario 1 — Initial salary at onboarding becomes the first history entry (User Story 1)

```bash
TEACHER_ID=$(curl -s -X POST http://localhost:8080/api/v1/teachers \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Priya Sharma","phone":"+919811111111","hlsOfferedSalary":17000,"status":"IN_TRAINING"}' | jq -r .id)

curl -s "http://localhost:8080/api/v1/teachers/$TEACHER_ID/salary" -H "Authorization: Bearer $DIRECTOR_TOKEN"
# → {"state":"RECORDED","amount":17000.00} — the initial salary, with no separate "record" step needed (SC-001)
```

## Scenario 2 — Recording an increment; the old amount stays queryable as of its own date (User Story 2)

```bash
curl -s -X POST http://localhost:8080/api/v1/teachers/$TEACHER_ID/salary \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"amount":19000,"effectiveFrom":"2026-10-01"}'
# → 200, TeacherSalaryHistoryView with effectiveFrom "2026-10-01"

curl -s "http://localhost:8080/api/v1/teachers/$TEACHER_ID/salary" -H "Authorization: Bearer $DIRECTOR_TOKEN"
# → current salary is now 19000 (once today >= 2026-10-01) or still 17000 before then

curl -s "http://localhost:8080/api/v1/teachers/$TEACHER_ID/salary?asOf=2026-09-25" -H "Authorization: Bearer $DIRECTOR_TOKEN"
# → {"state":"RECORDED","amount":17000.00} — the original amount, since 2026-09-25 is before the increment (AC2)

curl -s "http://localhost:8080/api/v1/teachers/$TEACHER_ID/salary?asOf=2026-10-05" -H "Authorization: Bearer $DIRECTOR_TOKEN"
# → {"state":"RECORDED","amount":19000.00} — the incremented amount (AC3)
```

## Scenario 3 — Viewing the profile shows the current salary, no extra step (User Story 3)

```bash
curl -s "http://localhost:8080/api/v1/teachers/$TEACHER_ID" -H "Authorization: Bearer $DIRECTOR_TOKEN"
# → TeacherProfileView.hlsOfferedSalary is the current (latest effective) amount — same call as before this feature (SC-003)
```

## Scenario 4 — A date before any recorded salary clearly reports "not yet recorded" (User Story 4, AC2)

```bash
curl -s "http://localhost:8080/api/v1/teachers/$TEACHER_ID/salary?asOf=2020-01-01" -H "Authorization: Bearer $DIRECTOR_TOKEN"
# → {"state":"NOT_YET_RECORDED"} — not an error, not a misleading zero (SC-004)
```

## Automated equivalents

Every scenario above has a corresponding `TeacherServiceTest` (unit) and `TeacherIntegrationTest` (Testcontainers, real JWT auth) case. Run:

```bash
cd backend && ./mvnw test -Dtest=TeacherServiceTest,TeacherIntegrationTest
```

The extended `TeacherProfilesPage` has its new cases added to its existing `TeacherProfilesPage.test.tsx`:

```bash
cd frontend && npx vitest run src/pages/TeacherProfilesPage
```
