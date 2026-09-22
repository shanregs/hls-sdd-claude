# Quickstart: Audit Trail

Validates the acceptance scenarios in spec.md end to end. Assumes the `audit` module's tasks (data-model.md, contracts/audit-api.yaml) are implemented.

Unlike `organization`, no real financial/attendance module exists yet to write audit entries through its own screens (research.md §1) — so scenarios below write entries via a small test-only harness that calls `AuditWriter` directly, the same way a future `attendance`/`payroll`/`expense`/`substitution` module's service code will.

## Prerequisites

- PostgreSQL reachable (Testcontainers for automated tests; a local/dev Postgres for manual exercise), Flyway migrations `V1__create_identity_tables.sql`, `V2__create_organization_tables.sql`, and `V3__create_audit_tables.sql` all applied
- Backend running (`./mvnw spring-boot:run` from `backend/`)
- A Director or Admin `User` row seeded in `identity_user` (Identity & Access, spec 002) with a known password, and separately a Manager-role user for Scenario 3
- Every call below authenticates for real against Identity's `/api/v1/auth/login`, same as `organization`'s quickstart

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber":"+919800000001","password":"correct-password"}' | jq -r .accessToken)
# Every curl below sends: -H "Authorization: Bearer $TOKEN"
```

## Scenario 1 — A change is automatically recorded (User Story 1, acceptance scenario 1)

Run via `AuditServiceTest`/`AuditIntegrationTest` (there is no HTTP write endpoint to `curl` — research.md §1/§7 — so this is exercised through the test harness, not manually):

```java
UUID entryId = auditWriter.record(new AuditRecordRequest(
    "attendance", "AttendanceRecord", "attendance-record-42",
    AuditAction.UPDATED, "status: Present -> Leave",
    "{\"status\":\"Present\"}", "{\"status\":\"Leave\"}",
    managerUserId, "MANAGER", "req-abc123"));
```

**Expected outcome**: `record(...)` returns the new entry's id; the row is visible in the database with `sequence_no` assigned and `occurred_at` set to server time, not any value the caller passed in (there is none to pass — `AuditRecordRequest` has no timestamp field, research.md §5).

## Scenario 2 — Director views a record's full history (User Story 2, acceptance scenario 1)

```bash
curl -s "http://localhost:8080/api/v1/audit/AttendanceRecord/attendance-record-42/history" \
  -H "Authorization: Bearer $TOKEN"
# → 200, [ {action: "CREATED", ...}, {action: "UPDATED", summary: "status: Present -> Leave", ...} ]
```

**Expected outcome**: every entry for that record appears, oldest first, each with actor, timestamp, and before/after values (SC-002).

## Scenario 3 — A Manager cannot view history (User Story 2, acceptance scenario 3; FR-008; SC-005)

```bash
MANAGER_TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber":"+919800000099","password":"manager-password"}' | jq -r .accessToken)

curl -s -o /dev/null -w "%{http_code}" \
  "http://localhost:8080/api/v1/audit/AttendanceRecord/attendance-record-42/history" \
  -H "Authorization: Bearer $MANAGER_TOKEN"
# → 403
```

**Expected outcome**: access is denied for a non-Director/Admin caller.

## Scenario 4 — A correction appends, it never overwrites (User Story 3, acceptance scenario 3; FR-006; SC-004)

```java
auditWriter.record(new AuditRecordRequest(
    "expense", "Expense", "expense-17",
    AuditAction.CORRECTED, "amount: 500 -> 5000 (typo fix)",
    "{\"amount\":500}", "{\"amount\":5000}",
    adminUserId, "ADMIN", "req-def456"));
```

```bash
curl -s "http://localhost:8080/api/v1/audit/Expense/expense-17/history" \
  -H "Authorization: Bearer $TOKEN"
# → both the original entry and the CORRECTED entry are present; the original is unchanged
```

**Expected outcome**: two entries exist for `expense-17` — the original and the correction — never one entry with its values changed in place.

## Scenario 5 — Nothing can edit or delete an existing entry (User Story 3, acceptance scenarios 1 & 2; FR-005)

There is no code path to attempt this against: `AuditEntryRepository` exposes no update/delete method (research.md §4), and `AuditController` exposes no `PUT`/`PATCH`/`DELETE` mapping at all. This is verified as a standing fact of the codebase (`AuditIntegrationTest` asserts a `DELETE`/`PUT` to the history endpoint's path returns `404`/`405`, not `403` — the operation doesn't exist, it isn't merely forbidden), not something to demonstrate failing at runtime.

## Automated equivalents

Every scenario above has a corresponding case in `AuditServiceTest` (unit, FR-004/FR-010/FR-011 rules), `AuditIntegrationTest` (Testcontainers Postgres, real JWT bearer auth end to end, the atomicity check from research.md §3), and `AuditModuleTest` (`@ApplicationModuleTest`, isolated bootstrap) — see plan.md's Project Structure. Run:

```bash
cd backend && ./mvnw test -Dtest=AuditServiceTest,AuditIntegrationTest,AuditModuleTest
```

The frontend `AuditHistoryPage` (Director/Admin, entity type + entity id lookup, research.md §7) has its own `AuditHistoryPage.test.tsx` covering the history table render and the 403 case, following `AssignmentsPage.test.tsx`'s pattern:

```bash
cd frontend && npx vitest run src/pages/AuditHistoryPage
```
