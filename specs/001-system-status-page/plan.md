# Implementation Plan: System Status Page

**Branch**: `001-system-status-page` | **Date**: 2026-09-21 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-system-status-page/spec.md`

## Summary

A throwaway, no-auth status page that lets HLS staff confirm the application is up and its core Postgres data store is reachable — the primary requirement per spec.md. Technical approach: a minimal Spring Boot REST endpoint performs a bounded-timeout DB reachability check and returns OK/Degraded plus version and IST server time; a minimal React page renders that JSON. Built using the project's real stack (Java 25+/Spring Boot 4.1.1, React, PostgreSQL, Docker Compose) rather than a throwaway tech stack, so the warm-up also proves out the actual toolchain and bootstraps the initial repo skeleton (`backend/`, `frontend/`) that later modules will build on.

## Technical Context

**Language/Version**: Java 25+ (backend, per constitution Additional Constraints), TypeScript/React (frontend)

**Primary Dependencies**: Spring Boot 4.1.1-based (`spring-boot-starter-web`, `spring-boot-starter-jdbc`), Micrometer (metrics), SLF4J/Logback with JSON encoder (structured logging + correlation id), ArchUnit (architecture boundary testing); React (functional components, `fetch` for the status API call) — no state-management library needed for a single read-only page

**Storage**: PostgreSQL (working default per ADR-0002-pending) — used only as the reachability target for this feature; no schema/tables/migrations introduced

**Testing**: JUnit 5 + Spring Boot Test for the backend (unit test with a mocked `DataSource` for OK/Degraded logic; one Testcontainers-backed Postgres integration test exercising the real Degraded path, per the constitution's Testcontainers mandate); React Testing Library for a basic render/status-mapping test on the frontend

**Target Platform**: Single EC2/VM in `ap-south-1` (or equivalent India region), packaged via Docker Compose alongside the Postgres container — matches constitution's deployment target from day one

**Project Type**: Web application (frontend + backend) — Option 2 structure

**Performance Goals**: Status check completes within 3s under normal conditions (SC-004); staff can read the result within 5s of opening the page (SC-001)

**Constraints**: Bounded-wait DB reachability check, timeout = 3s (ties FR-006's "does not complete within that time" to the concrete SC-004 target — see research.md); no authentication (FR-001); no persisted history of past checks (Assumptions)

**Scale/Scope**: Single page, single endpoint; organization-wide user base is <100 people per the constitution's scale constraint — irrelevant load-wise for this feature

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Applies? | Assessment |
|---|---|---|
| I. Operational Truth Must Be Auditable | No | This feature touches none of attendance/salary/receivables/expenses/training data — it's a generic health signal with no persisted history (spec Assumptions). Out of this principle's scope by design, not a violation. |
| II. Role-Scoped Access and Manager Ownership | Partial — justified exception | Spec FR-001 deliberately requires no login, since this warm-up predates the Identity & Access module (built next, per the tracker) and exposes no teacher/school/financial data. Logged in Complexity Tracking below. |
| III. Payroll/Receivables/Margin Formula-Driven | No | No financial computation in this feature. |
| IV. Training & Substitution Parity | No | Not applicable. |
| V. Modular Monolith With Enforced Boundaries | Yes | Lives in its own package (`com.hls.status`) inside the single Spring Boot app, with zero dependency on any business module (`identity`, `roster`, `schoolbilling`, etc.) — it only reads `DataSource` health. ArchUnit rule: `status` package must not depend on any other application package. |
| VI. Concurrency / Virtual-Thread Batch Processing | Yes (as N/A) | This is a single per-request check, not a triggered batch job (payroll/attendance rollup), so it correctly stays on Spring's default request-path model per the principle's own last sentence — no virtual-thread executor needed here. |
| VII. Reliability, Testability, Incremental Delivery | Yes | Unit + Testcontainers integration coverage planned (see Testing above); this is also the first exercise of the project's TDD/test-infrastructure pattern other modules will reuse. |
| VIII. Security, Identity, Observability | Partial — justified exception + honored where applicable | JWT/OTP auth doesn't apply (no identity involved, see Principle II row). Structured JSON logging with a correlation id and a basic status-check metric ARE included, honoring "built in from the first module, not retrofitted" — this literally is the first module. |
| IX. Recruitment & Marketing Tracked to Outcome | No | Not applicable. |
| Additional Constraints — stack/deployment/timezone | Yes | Java 25+/Spring Boot 4.1.1/React/PostgreSQL/Docker Compose/`ap-south-1` all honored; server time displayed in IST per FR-004, matching the project-wide UTC-stored/IST-displayed convention. |

**Gate result**: PASS. One exception (no RBAC/auth on this single endpoint) is logged and justified in Complexity Tracking rather than blocking — it is bounded to a feature explicitly marked throwaway and pre-Identity-module in spec.md's own Assumptions.

## Project Structure

### Documentation (this feature)

```text
specs/001-system-status-page/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   └── status-api.yaml
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

Nothing under `backend/` or `frontend/` exists yet — this is the first feature implemented, so its tasks also bootstrap the minimal project skeleton (build files, application entry point, base package structure) that later modules will extend.

```text
backend/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/com/hls/
│   │   │   ├── HlsApplication.java        # Spring Boot entry point
│   │   │   └── status/                    # this feature's bounded context
│   │   │       ├── StatusController.java  # GET /api/status
│   │   │       ├── StatusService.java     # DB reachability check + status assembly
│   │   │       └── StatusResponse.java    # response DTO (mirrors contracts/status-api.yaml)
│   │   └── resources/
│   │       ├── application.yml
│   │       └── logback-spring.xml         # structured JSON logging + correlation id
│   └── test/
│       └── java/com/hls/
│           ├── ArchitectureTest.java           # ArchUnit boundary rule (Principle V)
│           └── status/
│               ├── StatusServiceTest.java      # unit: mocked DataSource, OK/Degraded/timeout logic
│               └── StatusIntegrationTest.java  # Testcontainers Postgres: real Degraded path

frontend/
├── package.json
├── src/
│   ├── pages/
│   │   └── StatusPage/
│   │       ├── StatusPage.tsx       # fetches GET /api/status, renders OK/Degraded/version/time
│   │       └── StatusPage.test.tsx  # render + status-mapping test
│   └── App.tsx                      # routes to /status (no auth guard, per FR-001)

docker-compose.yml                    # app + postgres, single EC2/VM target
```

**Structure Decision**: Web application (Option 2) — a `backend/` Spring Boot Maven project and a `frontend/` React project in the same repository, deployed together via `docker-compose.yml`. Maven and the `com.hls` base package are adopted here as this feature's own decisions (no prior ADR fixed them); see research.md for rationale. This is the first feature, so it also stands up the repo skeleton both directories above will be extended by the next modules (Identity & Access, then Teacher/School/Contract master data).

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|---------------------------------------|
| No RBAC/authentication on the `/status` endpoint and page (deviates from Principle VIII's "every endpoint enforces role and ownership") | This is a deliberately throwaway warm-up feature (spec.md Assumptions) scheduled *before* the Identity & Access module in the corrected build order; the endpoint exposes no teacher/school/financial/personal data, only generic app health | Building Identity & Access first just to gate this one endpoint would invert the project's own dependency-corrected module order and add real implementation cost for zero security benefit, since there is no sensitive data to protect here |
