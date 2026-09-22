# Quickstart: Daily Attendance Capture & Monthly Rollup

Validates the acceptance scenarios in spec.md end to end.

## Prerequisites

- PostgreSQL reachable, all Flyway migrations through this feature's applied (`V1`-`V9`)
- Backend running (`./mvnw spring-boot:run` from `backend/`)
- A Teacher (with a linked `teacherId` JWT claim), their accountable Manager, an Admin, and a Director all seeded and logged in for real bearer tokens

```bash
TEACHER_TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/teacher-login \
  -H "Content-Type: application/json" -d '{"phoneNumber":"+919800000010","otp":"123456"}' | jq -r .accessToken)
MANAGER_TOKEN=$(... same pattern, this Teacher's accountable Manager ...)
ADMIN_TOKEN=$(... same pattern, Admin ...)
DIRECTOR_TOKEN=$(... same pattern, Director ...)
TEACHER_ID=<the Teacher's id>
SCHOOL_ID=$(uuidgen)   # opaque reference (research.md §1) — any well-formed UUID
```

## Scenario 1 — Teacher marks their own attendance, with a half-day and evidence (User Story 1)

```bash
curl -s -X POST http://localhost:8080/api/v1/attendance/me/marks \
  -H "Authorization: Bearer $TEACHER_TOKEN" -H "Content-Type: application/json" \
  -d "{\"markDate\":\"2026-09-22\",\"schoolId\":\"$SCHOOL_ID\",\"statusCode\":\"PRESENT\",\"fractionalValue\":0.5,\"evidence\":{\"checkinCode\":\"ABC123\"}}"
# → 200, AttendanceMarkView with fractionalValue 0.5, markedByRole "TEACHER" (AC1/AC2/AC3)

curl -s -X POST http://localhost:8080/api/v1/attendance/me/marks \
  -H "Authorization: Bearer $TEACHER_TOKEN" -H "Content-Type: application/json" \
  -d "{\"markDate\":\"2026-09-22\",\"schoolId\":\"$SCHOOL_ID\",\"statusCode\":\"PRESENT\",\"fractionalValue\":1.0}"
# → 200, same day's mark updated in place to 1.0, not a second row (AC4)
```

## Scenario 2 — Manager marks attendance on behalf of a Teacher (User Story 2)

```bash
curl -s -X POST http://localhost:8080/api/v1/attendance/teachers/$TEACHER_ID/marks \
  -H "Authorization: Bearer $MANAGER_TOKEN" -H "Content-Type: application/json" \
  -d "{\"markDate\":\"2026-09-23\",\"schoolId\":\"$SCHOOL_ID\",\"statusCode\":\"LEAVE\"}"
# → 200, markedByRole "MANAGER" (AC1)

# A Manager the Teacher is NOT currently accountable to:
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/api/v1/attendance/teachers/$TEACHER_ID/marks \
  -H "Authorization: Bearer $OTHER_MANAGER_TOKEN" -H "Content-Type: application/json" \
  -d "{\"markDate\":\"2026-09-24\",\"schoolId\":\"$SCHOOL_ID\",\"statusCode\":\"PRESENT\"}"
# → 403 (AC2)
```

## Scenario 3 — Monthly rollup reflects the mix of marks, live (User Story 3)

```bash
curl -s "http://localhost:8080/api/v1/attendance/teachers/$TEACHER_ID/months/2026-09" \
  -H "Authorization: Bearer $DIRECTOR_TOKEN"
# → MonthlyAttendanceRollupView: daysWorked 1.0, daysLeave 1.0, trainingDaysTotal 0,
#   overallWorkingDays 30, unmarkedDays 28, weightedAttendanceTotal 1.0, lockStatus "UNLOCKED" (AC1/AC2)

curl -s -o /dev/null -w "%{http_code}\n" "http://localhost:8080/api/v1/attendance/teachers/$TEACHER_ID/months/2026-09" \
  -H "Authorization: Bearer $UNRELATED_MANAGER_TOKEN"
# → 403 (AC4)
```

## Scenario 4 — Lock, rejected direct edit, reopen, correct, re-lock (User Story 4)

```bash
curl -s -X POST http://localhost:8080/api/v1/attendance/teachers/$TEACHER_ID/months/2026-09/lock \
  -H "Authorization: Bearer $DIRECTOR_TOKEN"
# → 200, LockStatusView status "LOCKED" (AC1)

curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/api/v1/attendance/me/marks \
  -H "Authorization: Bearer $TEACHER_TOKEN" -H "Content-Type: application/json" \
  -d "{\"markDate\":\"2026-09-22\",\"schoolId\":\"$SCHOOL_ID\",\"statusCode\":\"PRESENT\"}"
# → 409 (AC2)

curl -s -X POST http://localhost:8080/api/v1/attendance/teachers/$TEACHER_ID/months/2026-09/reopen \
  -H "Authorization: Bearer $DIRECTOR_TOKEN" -H "Content-Type: application/json" \
  -d '{"reason":"Teacher reported a missed half-day correction"}'
# → 200, LockStatusView status "REOPENED", reopenHistory has one entry (AC3)

curl -s -X POST http://localhost:8080/api/v1/attendance/teachers/$TEACHER_ID/marks \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d "{\"markDate\":\"2026-09-22\",\"schoolId\":\"$SCHOOL_ID\",\"statusCode\":\"PRESENT\",\"fractionalValue\":0.5}"
# → 200 (correction succeeds while reopened)

curl -s -X POST http://localhost:8080/api/v1/attendance/teachers/$TEACHER_ID/months/2026-09/lock \
  -H "Authorization: Bearer $DIRECTOR_TOKEN"
# → 200, LockStatusView status "LOCKED" again, reopenHistory's entry now has relockedAt/relockedBy set (AC4)
```

## Scenario 5 — Grid view across teachers for a month (User Story 5)

```bash
curl -s "http://localhost:8080/api/v1/attendance/grid?period=2026-09" \
  -H "Authorization: Bearer $DIRECTOR_TOKEN"
# → AttendanceGridView: days = ["2026-09-01",...,"2026-09-30"] (30 columns, AC1/Edge Case),
#   rows = one per Teacher, cells keyed by date; 2026-09-22 shows statusCode "PRESENT" for $TEACHER_ID (from Scenario 1)

curl -s "http://localhost:8080/api/v1/attendance/grid?period=2026-09&managerId=$MANAGER_ID" \
  -H "Authorization: Bearer $DIRECTOR_TOKEN"
# → same shape, rows narrowed to $MANAGER_ID's portfolio only (AC4)

curl -s "http://localhost:8080/api/v1/attendance/grid?period=2026-09" \
  -H "Authorization: Bearer $MANAGER_TOKEN"
# → rows limited to this Manager's own accountable Teachers, never anyone outside their portfolio (AC2)

curl -s "http://localhost:8080/api/v1/attendance/grid?period=2026-09" \
  -H "Authorization: Bearer $DIRECTOR_TOKEN" | jq '.rows[0].cells["2026-09-05"]'
# → null — a day with no mark shows clearly as unmarked, never a default status (AC3)
```

### Editing a cell directly from the grid (AC5/AC6)

```bash
# Admin corrects a Teacher's day from the grid, unscoped (FR-024):
curl -s -X POST http://localhost:8080/api/v1/attendance/teachers/$TEACHER_ID/marks \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d "{\"markDate\":\"2026-09-10\",\"schoolId\":\"$SCHOOL_ID\",\"statusCode\":\"LEAVE\"}"
# → 200 — same write path as US1/US2, now attributed to markedByRole "ADMIN" (AC5)

curl -s "http://localhost:8080/api/v1/attendance/grid?period=2026-09" \
  -H "Authorization: Bearer $DIRECTOR_TOKEN" | jq '.rows[] | select(.teacherId=="'$TEACHER_ID'") | .cells["2026-09-10"]'
# → { "statusCode": "LEAVE", ..., "editable": true } — the grid reflects the edit immediately

# The same edit attempted on a locked teacher-month:
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/api/v1/attendance/teachers/$TEACHER_ID/marks \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d "{\"markDate\":\"2026-09-22\",\"schoolId\":\"$SCHOOL_ID\",\"statusCode\":\"PRESENT\"}"
# → 409 — and that cell's grid entry shows "editable": false (AC6), consistent with FR-012
```

## Scenario 6 — Shared Non-Working Calendar applies automatically; an explicit mark still overrides it (FR-022/FR-009/FR-010)

```bash
curl -s -X POST http://localhost:8080/api/v1/attendance/non-working-dates \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"date":"2026-09-28","label":"Gandhi Jayanti (observed)"}'
# → 200, NonWorkingDateView

curl -s "http://localhost:8080/api/v1/attendance/teachers/$TEACHER_ID/months/2026-09" \
  -H "Authorization: Bearer $DIRECTOR_TOKEN"
# → overallWorkingDays now excludes 2026-09-28 with no mark needed for this or any other Teacher;
#   unmarkedDays does NOT count 2026-09-28 (it's calendar-covered, not a gap — FR-010)

curl -s "http://localhost:8080/api/v1/attendance/grid?period=2026-09" \
  -H "Authorization: Bearer $DIRECTOR_TOKEN" | jq '.rows[0].cells["2026-09-28"]'
# → { "statusCode": null, "category": "NON_WORKING", "fractionalValue": null, "editable": true }
#   — visibly non-working, but still editable (an explicit mark can override it)

# The school stays open for this one Teacher on that date — an explicit mark overrides the calendar:
curl -s -X POST http://localhost:8080/api/v1/attendance/teachers/$TEACHER_ID/marks \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d "{\"markDate\":\"2026-09-28\",\"schoolId\":\"$SCHOOL_ID\",\"statusCode\":\"PRESENT\"}"

curl -s "http://localhost:8080/api/v1/attendance/teachers/$TEACHER_ID/months/2026-09" \
  -H "Authorization: Bearer $DIRECTOR_TOKEN"
# → this Teacher's overallWorkingDays now includes 2026-09-28 (their explicit mark won),
#   while every other Teacher's rollup still excludes it via the calendar alone
```

## Automated equivalents

Every scenario above has a corresponding `AttendanceServiceTest` (unit) and `AttendanceIntegrationTest` (Testcontainers, real JWT auth) case. Run:

```bash
cd backend && ./mvnw test -Dtest=AttendanceServiceTest,AttendanceIntegrationTest
```

Frontend cases:

```bash
cd frontend && npx vitest run src/pages/MyAttendancePage src/pages/AttendancePage
```
