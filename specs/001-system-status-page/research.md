# Phase 0 Research: System Status Page

No `NEEDS CLARIFICATION` markers remained in Technical Context after `/speckit-clarify` found the spec fully bounded, but several implementation-approach decisions still needed to be made explicitly since this is the first feature built in the repository. Each is recorded below in Decision/Rationale/Alternatives form.

## 1. How to check core data store reachability

**Decision**: A dedicated `StatusService` obtains a JDBC `Connection` from the configured `DataSource` and calls `Connection.isValid(timeoutSeconds)` with a 3-second bound, catching any exception as "unreachable."

**Rationale**: `Connection.isValid(int)` is a standard JDBC mechanism purpose-built for exactly this ("is the connection, and by extension the database, currently usable") and takes an explicit timeout, directly satisfying FR-006's bounded-wait requirement without hand-rolled timeout/executor logic.

**Alternatives considered**:
- **Spring Boot Actuator's `/actuator/health`** (with its built-in `DataSourceHealthIndicator`): rejected as the primary mechanism — it's an ops-facing JSON endpoint, not the staff-readable page the spec calls for (FR-002–FR-004), and publicly exposing Actuator's health surface (even a trimmed one) raises questions out of scope for a throwaway feature. The underlying idea (a lightweight liveness probe) is still reused, just via a direct JDBC check instead of pulling in the Actuator dependency.
- **A trivial `SELECT 1` query via `JdbcTemplate`**: functionally similar to `Connection.isValid`, but adds a round trip through the full query-execution stack for a check that doesn't need it; `Connection.isValid` is lighter and equally standard.

## 2. Frontend approach for the status page

**Decision**: A minimal React page (`StatusPage.tsx`) under `frontend/src/pages/StatusPage/`, using `fetch` to call `GET /api/status` and rendering the result — no state-management library, no auth guard on the route (per FR-001).

**Rationale**: The constitution commits the project to a React web app; building this warm-up feature on the real frontend stack (rather than a static HTML page or a different throwaway tool) means it actually exercises the toolchain — build, dev server, deployment via Docker Compose — that every subsequent module will rely on. That's the stated purpose of the warm-up (tracker Section 1: "learn the workflow safely").

**Alternatives considered**:
- **Server-rendered HTML (Thymeleaf) page returned directly by Spring Boot**: simpler for this one page, but doesn't exercise the React/Spring Boot integration pattern (separate frontend build, API contract, CORS/dev-proxy setup) that real modules will need — defeats the purpose of a warm-up.
- **A standalone static HTML file with inline JS**: same objection — proves nothing about the real stack.

## 3. Bounded-wait timeout value

**Decision**: 3 seconds, applied both as the `Connection.isValid` timeout (FR-006) and as the expected check-completion time under normal conditions (SC-004) — the two requirements are tied to the same number rather than left as independently-tunable values, since nothing in the spec calls for them to differ.

**Rationale**: Keeps the implementation and the acceptance test for SC-004 mechanically linked — if the timeout value ever changes, both the "bounded wait" behavior and the measurable SC-004 target move together.

**Alternatives considered**: A shorter timeout (e.g., 1s) was considered but rejected as prone to false "Degraded" readings on a briefly slow-but-healthy connection pool under normal load; nothing in the spec calls for sub-second detection.

## 4. Testing approach for the Degraded path

**Decision**: One JUnit 5 unit test with a mocked `DataSource`/`Connection` covering OK, Degraded (unreachable), and Degraded (timeout) logic; one Testcontainers-backed Postgres integration test that stops the container mid-test to prove the real Degraded path (User Story 2, Acceptance Scenario 1) works end-to-end, not just against a mock.

**Rationale**: The constitution explicitly requires "Integration tests run against a real Postgres... via Testcontainers, not an in-memory substitute" for exactly this kind of DB-reachability-dependent logic. Establishing this pattern here — in the very first feature — means later modules (which reuse Postgres via Testcontainers far more heavily) inherit a working example rather than inventing the pattern under payroll-level time pressure.

**Alternatives considered**: Mocked-only testing — rejected as insufficient per the constitution's explicit Testcontainers mandate, and because a mock can't prove the bounded-wait timeout actually behaves correctly against a real, unresponsive connection.

## 5. Observability

**Decision**: Structured JSON logging (Logback + JSON encoder) with a request-correlation id (`MDC`), plus a Micrometer counter (`status.check.total`, tagged by result `OK`/`DEGRADED`) recorded on every status check.

**Rationale**: Constitution Principle VIII requires structured logging with request correlation and metrics "built in from the first module, not retrofitted after launch." This is literally the first module, so it is the natural place to establish the pattern (log format, correlation-id propagation, metrics-naming convention) that every later module will follow.

**Alternatives considered**: Deferring observability setup until a "real" module (e.g., Identity & Access) — rejected as directly contradicting the constitution's explicit "not retrofitted after launch" language.

## 6. Repository/build tooling bootstrap

**Decision**: Maven for the backend build (`backend/pom.xml`), base Java package `com.hls`, single git repository with top-level `backend/` and `frontend/` directories, `docker-compose.yml` at the repo root wiring the app container to a Postgres container.

**Rationale**: No prior ADR fixed these choices, and nothing in the spec or constitution mandates Gradle over Maven or a different package root — Maven is the more common default for enterprise Spring Boot projects and keeps the build simple for a small team. A single repo with two top-level directories matches the constitution's single-deployable/Docker Compose deployment model without introducing multi-repo coordination overhead this project's scale doesn't need.

**Alternatives considered**: Gradle — equally valid, no strong reason to prefer it here; deferred as a non-blocking choice. Separate repos for backend/frontend — rejected as unnecessary coordination overhead for a single small team shipping one Docker Compose stack.

**Note**: These are this feature's own working decisions, not yet formalized as ADRs. If the team wants them binding project-wide, consider writing an ADR (e.g., ADR-0004: Build Tooling & Repo Layout) before the next module starts — flagged here rather than assumed silently.
