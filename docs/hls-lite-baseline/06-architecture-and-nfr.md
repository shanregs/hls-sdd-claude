# Architecture and non-functional requirements

## Stack (D-29)

| Layer | Choice |
|---|---|
| Frontend | React with TypeScript, responsive, installable PWA (manifest, service worker for shell only; no offline data sync) |
| Backend | Java 25, latest GA Spring Boot, Spring MVC, Spring Data JPA or JDBC, Flyway migrations |
| Database | PostgreSQL, latest stable major community edition |
| Files | Folder on the server (or an object store behind one interface) |
| SMS | Not in release 1. Sign-in is by password only (D-36). Add later behind an interface with an India DLT-registered sender. |
| Build | Maven or Gradle (decide in plan); one deployable jar serving API; frontend static build behind the same web server |

Verify the exact latest Spring Boot and PostgreSQL versions on the day the project starts.

## Shape
- A **modular monolith**: one deployable, one schema, packages per area, direct method calls across packages in the same transaction.
- No message bus, outbox, scheduler jobs for business events, or second data store.
- No caching layer. Permission lookups hit the database per request (250 users).
- Derived values are queries.
- Packages: `platform` (auth, access, audit, settings, notification, files), `masterdata`, `contracts`, `attendance`, `substitution`, `payroll`, `billing`, `expenses`, `recruitment`, `training`, `marketing`, `tasks`, `performance`, `transfers`, `content`, `reporting`.
- Strict module-boundary tests are not required. Keep a light rule: controllers call services; services call repositories; no UI logic in services.

## Observability (required)
- **Logging**: structured JSON logs to stdout; a correlation/request id on every line; user id and role in context; no secrets or full phone numbers in logs; log levels configurable at runtime.
- **Metrics**: Micrometer through Spring Actuator, exposed for scraping (Prometheus format). Include JVM, HTTP latency and error rate, DB pool, plus business counters: sign-in failures, locked accounts, payroll run duration, uncomputable payroll lines, files uploaded.
- **Tracing**: OpenTelemetry for HTTP and DB spans; the trace id appears in logs.
- **Health**: liveness and readiness endpoints; readiness checks the database and file storage.
- **Dashboards and alerts**: a small starter dashboard (latency, errors, DB, JVM) and alerts for: service down, error rate high, DB connections near the limit, disk low, backup missing. Backend choice (Grafana stack or cloud-native) is decided in the plan.
- **Audit** is a business feature (PLT-09), separate from technical logs.

## Security
- HTTPS only. HSTS. Secure, HttpOnly, SameSite cookies or bearer tokens (decide in plan).
- Passwords hashed with Argon2id or bcrypt. Lockout after 5 failures for 30 minutes. Failure messages never reveal user existence.
- Password reset is by Admin or System Engineer setting a temporary password that must be changed at next sign-in. Sign-in attempts are rate-limited per user and per IP.
- Authorization on the server for every endpoint (permission, then scope). Hiding menus is not security.
- File uploads: type and size checks, stored outside the web root, access checked by owning record.
- Input validation on all APIs. Parameterised queries only.
- PII and salary data are never logged. Backups are encrypted.
- Dependency scanning in CI.

## Performance and capacity (NFR-01)
- 100 concurrent users, mostly Teachers marking attendance in one half hour.
- Indexes on (employee_id, date), (school_id, year, month), (school_id, status). No pagination or load-test specs beyond page size 50 and one smoke load test for the attendance peak.

## Operations
- One server (or small cloud instance), PostgreSQL on the same host or managed.
- CI builds, runs tests and Flyway against a real PostgreSQL (Testcontainers).
- Nightly `pg_dump` and file-folder copy, shipped off-site; quarterly restore drill.
- Rolling restart is acceptable (a few seconds of downtime out of hours).
- Time zone: store UTC, compute business date in Asia/Kolkata in one helper (`BusinessClock`). All "today" logic uses it.

## Testing
- Unit and integration tests, with money rules written from the worked examples in `03-business-rules.md` before implementation.
- API tests per endpoint for permission and scope (allowed, denied, out-of-scope).
- A small set of browser end-to-end tests for the main flows: sign in, mark attendance, leave, substitution, payroll run, invoice and payment.
- Accessibility: keyboard navigation, labels, contrast on all screens.
