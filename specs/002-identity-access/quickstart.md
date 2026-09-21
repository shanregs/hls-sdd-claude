# Quickstart: Identity & Access

Validates the acceptance scenarios in spec.md end to end. Assumes the module's tasks (data-model.md, contracts/identity-api.yaml) are implemented, and that Organization's `AccountabilityQueries` API (spec 003) is available — even a test/in-memory implementation — since Manager-scoping scenarios depend on it (Clarifications session 2026-09-21).

## Prerequisites

- PostgreSQL reachable, Flyway migrations for both `organization` and `identity` applied
- Backend running (`./mvnw spring-boot:run` from `backend/`)
- Frontend running (`npm run dev` from `frontend/`) for the browser-driven scenarios
- A seeded `User` row per role for manual exercise (Director, Manager A, Manager B, Admin, Accounts Officer, Teacher) — or use the API directly as below

## Scenario 1 — Staff password login (User Story 1, acceptance scenarios 1 & 2)

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber":"+919800000001","password":"correct-password"}'
# → 200, TokenPair (or MfaChallenge if MFA is enabled on this account)

curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber":"+919800000001","password":"wrong-password"}'
# → 401, {"message":"Incorrect phone number or password."} — generic, per FR-015
```

**Expected outcome**: correct credentials reach a `TokenPair`; wrong credentials get a generic 401 that doesn't say which field was wrong (SC-001, FR-015).

## Scenario 2 — Teacher OTP login (User Story 2, acceptance scenarios 1–3)

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/otp/request \
  -H "Content-Type: application/json" -d '{"phoneNumber":"+919800000099"}'
# → 202 (always, per FR-015)

# (dev OtpSender logs the code instead of sending a real SMS — research.md §3)
CODE=<read from backend logs>

curl -s -X POST http://localhost:8080/api/v1/auth/otp/verify \
  -H "Content-Type: application/json" \
  -d "{\"phoneNumber\":\"+919800000099\",\"code\":\"$CODE\"}"
# → 200, TokenPair

curl -s -X POST http://localhost:8080/api/v1/auth/otp/verify \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber":"+919800000099","code":"000000"}'
# → 401 (wrong/expired code — caller may request a new OTP)
```

**Expected outcome**: correct OTP within its TTL authenticates the Teacher; wrong/expired code is denied with the ability to retry (SC-002).

## Scenario 3 — Manager scoping, live against Organization (User Story 3, all 3 acceptance scenarios)

Requires Organization's assignment data (spec 003) — assign Manager A to School X and Manager B to School Y first (see `specs/003-organization-scoping/quickstart.md` Scenario 1).

```bash
TOKEN_A=<Manager A's access token from Scenario 1's flow>
TOKEN_B=<Manager B's access token>

curl -s http://localhost:8080/api/v1/schools/<School X id> -H "Authorization: Bearer $TOKEN_A"
# → 200 — Manager A is assigned to School X

curl -s http://localhost:8080/api/v1/schools/<School X id> -H "Authorization: Bearer $TOKEN_B"
# → 403/404 (denied, not the data) — Manager B is not assigned to School X

# Reassign School X to Manager B via Organization's API (specs/003 quickstart Scenario 2), then:
curl -s http://localhost:8080/api/v1/schools/<School X id> -H "Authorization: Bearer $TOKEN_B"
# → 200 — reflects the new assignment immediately, no re-login (SC-006)
```

**Expected outcome**: a Manager only ever sees their own assigned Schools/Teachers; reassignment through Organization is reflected on the Manager's very next request. (This scenario exercises another module's endpoint as the object of the scope check — Identity's own endpoints don't own School/Teacher data themselves.)

## Scenario 4 — Fail closed when Organization is unreachable (FR-019, Edge Case)

```bash
# Simulate Organization being unreachable (e.g., stop its module/mock in a test profile), then:
curl -s http://localhost:8080/api/v1/schools/<any school id> -H "Authorization: Bearer $TOKEN_A"
# → 403 (denied) — never 200, even for a School Manager A would normally be allowed to see
```

**Expected outcome**: the request is denied, not allowed through on a stale/cached scope.

## Scenario 5 — Account lockout (User Story 4)

```bash
for i in 1 2 3 4 5; do
  curl -s -X POST http://localhost:8080/api/v1/auth/login \
    -H "Content-Type: application/json" \
    -d '{"phoneNumber":"+919800000001","password":"wrong"}' > /dev/null
done

curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber":"+919800000001","password":"correct-password"}'
# → 401 — locked, even with the correct password now (indistinguishable from a wrong-password 401, per FR-015)
```

**Expected outcome**: after the configured threshold of consecutive failures, even correct credentials are denied until an explicit unlock action (SC-004).

## Scenario 6 — View and revoke a session (User Story 5)

```bash
TOKEN=<a valid access token>
curl -s http://localhost:8080/api/v1/auth/sessions -H "Authorization: Bearer $TOKEN"
# → 200, [ {id, channel, deviceLabel, issuedAt, lastActiveAt}, ... ]

curl -s -X DELETE "http://localhost:8080/api/v1/auth/sessions/<other session id>" \
  -H "Authorization: Bearer $TOKEN"
# → 204

curl -s -X POST http://localhost:8080/api/v1/auth/refresh --cookie "refresh_token=<the revoked session's refresh token>"
# → 401 — revoked session can no longer refresh (SC-005, within 60s)
```

## Automated equivalents

Every scenario above has a corresponding `IdentityIntegrationTest` (Testcontainers) case, and the lockout/OTP/fail-closed rules specifically have `AuthenticationServiceTest`/`ManagerScopeGuardTest` unit cases. Run:

```bash
cd backend && ./mvnw test -Dtest=AuthenticationServiceTest,ManagerScopeGuardTest,IdentityIntegrationTest,IdentityModuleTest
```
