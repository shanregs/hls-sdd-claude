# Quickstart: School Master Data — Places

Validates the acceptance scenarios in spec.md end to end.

## Prerequisites

- PostgreSQL reachable, all Flyway migrations through this feature's applied
- Backend running (`./mvnw spring-boot:run` from `backend/`)
- A Director or Admin `User` row seeded in `identity_user`, logged in for a real bearer token
- An existing Zone (see `specs/007-school-zone/quickstart.md` Scenario 1)

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber":"+919800000001","password":"correct-password"}' | jq -r .accessToken)
ZONE_A=$(curl -s -X POST http://localhost:8080/api/v1/school/zones \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"name":"North Chennai"}' | jq -r .id)
```

## Scenario 1 — Add a place to a Zone (User Story 1)

```bash
curl -s -X POST http://localhost:8080/api/v1/school/places \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"zoneId\":\"$ZONE_A\",\"name\":\"Ambattur\",\"pincode\":\"600053\"}"
# → 200, PlaceView with the given zoneId, name, pincode
```

## Scenario 2 — Look up which Zone a place belongs to, by PIN code and by name (User Story 2)

```bash
curl -s "http://localhost:8080/api/v1/school/places?pincode=600053" -H "Authorization: Bearer $TOKEN"
# → [{"...", "zoneId":"<ZONE_A>", "name":"Ambattur", "pincode":"600053"}]

curl -s "http://localhost:8080/api/v1/school/places?name=Ambattur" -H "Authorization: Bearer $TOKEN"
# → same result, found by name instead

curl -s "http://localhost:8080/api/v1/school/places?pincode=999999" -H "Authorization: Bearer $TOKEN"
# → [] — clearly no match, not an error (SC-003)
```

## Scenario 3 — A PIN code spanning multiple places in different Zones (User Story 2, AC3)

```bash
ZONE_B=$(curl -s -X POST http://localhost:8080/api/v1/school/zones \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"name":"South Chennai"}' | jq -r .id)
curl -s -X POST http://localhost:8080/api/v1/school/places \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"zoneId\":\"$ZONE_B\",\"name\":\"Neighboring Village\",\"pincode\":\"600053\"}"

curl -s "http://localhost:8080/api/v1/school/places?pincode=600053" -H "Authorization: Bearer $TOKEN"
# → both places returned, one per Zone — not just one arbitrarily (SC-002)
```

## Scenario 4 — View every place in a Zone (User Story 3)

```bash
curl -s "http://localhost:8080/api/v1/school/zones/$ZONE_A/places" -H "Authorization: Bearer $TOKEN"
# → [{"name":"Ambattur", ...}] — exactly this Zone's places (SC-004)
```

## Automated equivalents

Every scenario above has a corresponding `PlaceServiceTest` (unit) and `SchoolIntegrationTest` (Testcontainers, real JWT auth) case. Run:

```bash
cd backend && ./mvnw test -Dtest=PlaceServiceTest,SchoolIntegrationTest
```

The extended `ZonesPage` (Places section) has its new cases added to its existing `ZonesPage.test.tsx`:

```bash
cd frontend && npx vitest run src/pages/ZonesPage
```
