# Quickstart: Place Bulk Import

Validates the acceptance scenarios in spec.md end to end.

## Prerequisites

- PostgreSQL reachable, all Flyway migrations through this feature's applied (no new migration — this feature adds no table)
- Backend running (`./mvnw spring-boot:run` from `backend/`)
- A Director or Admin `User` row seeded in `identity_user`, logged in for a real bearer token
- An existing Zone (see `specs/007-school-zone/quickstart.md` Scenario 1)

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber":"+919800000001","password":"correct-password"}' | jq -r .accessToken)
ZONE_A=$(curl -s -X POST http://localhost:8080/api/v1/school/zones \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"name":"Coimbatore North"}' | jq -r .id)
```

## Scenario 1 — Bulk-import several places in one request (User Story 1)

```bash
curl -s -X POST http://localhost:8080/api/v1/school/places/bulk-import \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "[
    {\"zoneId\":\"$ZONE_A\",\"name\":\"Mettupalayam\",\"pincode\":\"641301\"},
    {\"zoneId\":\"$ZONE_A\",\"name\":\"Annur\",\"pincode\":\"641653\"},
    {\"zoneId\":\"$ZONE_A\",\"name\":\"Sulur\",\"pincode\":\"641402\"}
  ]"
# → 200, successCount=3, failureCount=0, each result's "place" populated

curl -s "http://localhost:8080/api/v1/school/zones/$ZONE_A/places" -H "Authorization: Bearer $TOKEN"
# → all 3 places present (SC-001)
```

## Scenario 2 — A batch mixing valid and invalid rows: good rows still land, bad rows are reported (User Story 2)

```bash
curl -s -X POST http://localhost:8080/api/v1/school/places/bulk-import \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "[
    {\"zoneId\":\"$ZONE_A\",\"name\":\"Valparai\",\"pincode\":\"642127\"},
    {\"zoneId\":\"00000000-0000-0000-0000-000000000000\",\"name\":\"Unknown Zone Place\",\"pincode\":\"641000\"},
    {\"zoneId\":\"$ZONE_A\",\"name\":\"\",\"pincode\":\"641000\"}
  ]"
# → 200, successCount=1, failureCount=2
#   results[0].succeeded=true, place present
#   results[1].succeeded=false, reason mentions the unknown zone id
#   results[2].succeeded=false, reason mentions the missing name
```

## Scenario 3 — An oversized or empty batch is rejected outright (User Story 3)

```bash
curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/api/v1/school/places/bulk-import \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d "[]"
# → 400 (empty batch, SC-003's "nothing created" complement)

# A batch of 5001 rows (generated, not shown) also → 400, nothing created.
```

## Automated equivalents

Every scenario above has a corresponding `PlaceServiceTest` (unit) and `SchoolIntegrationTest` (Testcontainers, real JWT auth) case. Run:

```bash
cd backend && ./mvnw test -Dtest=PlaceServiceTest,SchoolIntegrationTest
```

The extended `ZonesPage` (Bulk Import form) has its new cases added to its existing `ZonesPage.test.tsx`:

```bash
cd frontend && npx vitest run src/pages/ZonesPage
```
