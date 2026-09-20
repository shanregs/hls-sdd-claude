# Quickstart: System Status Page

Validation guide for spec.md's user stories, once implemented. See `data-model.md` for the response shape and `contracts/status-api.yaml` for the full API contract.

## Prerequisites

- Java 25+, Maven, Node.js (for the `frontend/` React app)
- Docker (for Postgres via `docker-compose.yml`)

## Setup

```sh
# from repo root
docker compose up -d postgres
cd backend && mvn spring-boot:run &
cd frontend && npm install && npm run dev
```

## Validate User Story 1 — system reports OK

1. With Postgres and the backend both running, open the frontend status page (or call `curl http://localhost:8080/api/status` directly).
2. **Expected**: `status: "OK"`, current `version`, and `serverTime` in IST (see contracts/status-api.yaml `ok` example).
3. Reload the page — `serverTime` and `checkDurationMs` should change on each load (not cached), per SC-002.

## Validate User Story 2 — Degraded when the data store is unreachable

1. With the backend still running, stop Postgres: `docker compose stop postgres`.
2. Reload the status page (or re-call `GET /api/status`).
3. **Expected**: HTTP 200 (page still loads, FR-007) with `status: "DEGRADED"` and `dataStoreReachable: false` (contracts/status-api.yaml `degraded` example).
4. Restart Postgres: `docker compose start postgres`, wait a few seconds, reload again.
5. **Expected**: `status` returns to `"OK"` without restarting the backend (Acceptance Scenario 2 of User Story 2).

## Validate User Story 3 — readable on any device

1. Open the status page in a browser and resize the window to phone width (~375px).
2. **Expected**: status, version, and timestamp remain fully visible with no horizontal scrolling.

## Run automated tests

```sh
cd backend && mvn test
```

- `StatusServiceTest` — unit coverage for OK/Degraded/timeout logic against a mocked `DataSource`.
- `StatusIntegrationTest` — Testcontainers-backed Postgres test that stops the container mid-test to prove the real Degraded path (this is the authoritative check for User Story 2; the manual steps above are for human/exploratory verification).

```sh
cd frontend && npm test
```

- `StatusPage.test.tsx` — renders the page against a mocked API response for both `OK` and `DEGRADED`, and asserts the phone-width layout (User Story 3) doesn't clip content.
