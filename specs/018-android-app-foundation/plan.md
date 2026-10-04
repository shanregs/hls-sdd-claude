# Implementation Plan: Android App Foundation

**Branch**: `018-android-app-foundation` | **Date**: 2026-10-04 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/018-android-app-foundation/spec.md`

## Summary

Deliver the first HLS Android app: sign in, sign out, role-based menus and an ACCOUNT area, plus an
audit-only location captured at each API call. The app is a React Native client of the existing
backend. It reuses spec 001 sign-in and session rules, spec 002's server-driven navigation and
spec 003's audit store. The backend changes are small and additive: a request filter that reads
client headers into a `ClientContext`, the renewal credential returned in the response body for the
Android client, a role-eligibility and minimum-version gate, new columns on the login-history,
user-activity and session tables, a new append-only API Access audit trail that records every
Android request with its location, and extra fields on the audit screens and exports plus a new
AUDIT → API Access screen. See
[research.md](research.md) for the decisions behind this.

## Technical Context

**Language/Version**: TypeScript 5.x on React Native with Expo (mobile); Java 25+ on Spring Boot
4.1.1 (backend, unchanged); TypeScript on React 19 (web audit screens, small change).

**Primary Dependencies**: mobile: Expo SDK, React Navigation, React Native Paper, expo-location,
expo-secure-store, a rooted-device check library. Backend: existing Spring Security, Spring
Modulith, Flyway; no new dependency. Web: existing MUI.

**Storage**: PostgreSQL via Flyway migration V16 (additive columns and one new audit table, see
[data-model.md](data-model.md)). On device: Android Keystore-backed secure store for the renewal
credential; plain storage for theme and flags; access token in memory only.

**Testing**: mobile: Jest, React Native Testing Library, API-client contract tests against
[contracts/mobile-api.md](contracts/mobile-api.md), a Maestro smoke flow. Backend: JUnit 5 and
Testcontainers, per-role authorization tests, ArchUnit and Spring Modulith verification. Web:
Vitest and vitest-axe for the changed audit screens.

**Target Platform**: Android 10 (API 29) and later, phones. Backend and web as today.

**Project Type**: mobile app plus existing web service and web frontend.

**Performance Goals**: reopen to home in under 3 seconds for a returning user (SC-002); location
handling adds no more than the configured wait (default 4 s) and none at all when a recent reading
is reused (default 10 s window); audit list pagination keeps spec 003's 10,000-row expectation with
the added columns.

**Constraints**: foreground location only, taken only at an API call; no background permission;
location never gates an action; backend enforces all permission and scope; no hard-coded role
menus in the app; no offline business actions.

**Scale/Scope**: under 100 users (Constitution). About eight app screens (Sign In with OTP and reset flows,
Home, ACCOUNT/Profile, Signed-in devices, Not authorized, No connection, Update required).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Assessment | Status |
| --------- | ---------- | ------ |
| I. Operational truth is auditable | Sign-ins, account actions and every Android request are audited with source, app version, location and rooted flag, using the existing append-only store (a new entry type in `audit`). Audit rows are only inserted, never edited. | Pass |
| II. Five fixed roles, configurable permissions | No new role. One new permission module, `AUDIT_API_ACCESS`, seeded for Admin and System like the other audit modules. The app reads the existing access model. Only Admin, Director and System can edit the matrix as before. | Pass |
| III. Data scope | The app adds no business data. Every business endpoint a later mobile spec calls keeps its own scope checks; this spec adds none that bypass them. | Pass |
| IV. Role-based experience, one framework | Menus come from the server model; items without an app screen are hidden; "not authorized" screen; light/dark theme; WCAG 2.2 AA, 48 dp touch targets. The app uses a drawer/tab navigation suited to a phone rather than the desktop left panel. Design tokens are shared with the web theme. | Pass |
| V, VI, VIII, XI | Not touched (no payroll, training, batch or recruitment). | N/A |
| VII. Modular monolith | New code stays in `identity` (filter, `ClientContext` in a new named-interface package `identity.clientcontext`) and `audit` (reads the context from events, adds columns). `audit` never reads HTTP. Verified by ArchUnit and Spring Modulith. | Pass |
| IX. Reliability and testability | Per-role authorization tests on the changed and new endpoints, tests for the location validation and no-background-location rule, UI tests for menu visibility per role in the app. | Pass |
| X. Security, identity, observability | Credential in Keystore-backed storage, access token in memory only, secure window, no backups; backend still the control; `X-HLS-Location` kept out of logs; role-eligibility and version gates are enforced on the server. | Pass |

Also checked against *Additional Constraints*: React Native Android-first, Spring Boot, PostgreSQL
with Flyway, DD/MM/YYYY and Indian locale for any date shown (the app shows few dates; they follow
the same formatter as the web). The "offline readiness" constraint is not met by this release; the
spec schedules it for a later mobile spec, which is consistent with the constitution's wording
"mobile attendance and expense capture work with temporary offline storage" (those screens do not
exist yet).

**Post-design re-check**: no violations arose from the data model or contracts. The one tension, a
phone navigation pattern versus the desktop "left panel" wording of Principle IV, is covered by the
principle's own tablet/phone drawer rule and needs no waiver.

## Project Structure

### Documentation (this feature)

```text
specs/018-android-app-foundation/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── mobile-api.md    # Phase 1 output
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 output (/speckit-tasks, not created here)
```

### Source Code (repository root)

```text
backend/src/main/java/com/hls/
├── identity/
│   ├── clientcontext/                 # NEW named interface: ClientContext, LocationCapture, LocationStatus,
│   │                                  #   ClientContextHolder, ClientContextFilter (parses X-HLS-* headers)
│   ├── clientcontext/ApiAccessFilter  # NEW: publishes ApiAccessRecorded after each Android request
│   ├── mobile/                        # NEW: AppConfigController (GET /api/v1/mobile/app-config),
│   │                                  #   MobileVersionGate (426), MobileRoleEligibility
│   ├── auth/AuthController.java       # CHANGED: body credential for Android, WEB_ONLY_ROLE check
│   ├── otp/OtpController.java         # CHANGED: same for sign-in verify
│   ├── session/                       # CHANGED: client_type, app_version; SessionController view fields
│   ├── loginhistory/                  # CHANGED: LoginHistoryRecorded + publisher carry ClientContext
│   ├── activity/                      # CHANGED: request-triggered events carry ClientContext
│   └── security/SecurityConfig.java   # CHANGED: permit app-config; register the filter
├── audit/
│   ├── loginhistory/ useractivity/    # CHANGED: persist and return the new fields; source filter
│   ├── apiaccess/                     # NEW: ApiAccessEntry, repository, consumer, AuditApiAccessController (+export)
│   └── support/CsvStreamingExporter   # CHANGED: new CSV columns
└── resources/db/migration/V16__add_client_context_to_audit_and_session.sql   # NEW (next free number)

backend/src/test/java/com/hls/
├── identity/clientcontext/            # header parsing and validation, no-error-on-bad-header
├── identity/mobile/                   # role eligibility, version gate, body-credential flows
├── audit/                             # location fields visible to Admin/System only, per-role 403s, export columns
└── audit/ArchitectureRulesTest        # ArchUnit: audit has no web dependency; identity.clientcontext exposed

frontend/src/features/audit/           # CHANGED: Source, App version, Location columns/filter on three screens;
                                       #   NEW ApiAccessPage; vitest + axe

mobile/                                # NEW React Native (Expo) app
├── app.config.ts                      # minSdk 29, no background location, allowBackup=false, secure window
├── src/
│   ├── api/                           # HTTP client: adds X-HLS-* headers, 426/401 handling, no-connection errors
│   ├── location/                      # LocationGate: permission, one fix per call/burst, wait + reuse window
│   ├── auth/                          # sign-in (OTP, password), reset, session store, renewal, logout
│   ├── security/                      # SecureStore wrapper, rooted check, clear-all
│   ├── access/                        # access-model fetch + route→screen registry
│   ├── navigation/                    # drawer/tab shell built from the registry
│   ├── screens/                       # SignIn, ResetPassword, Home, Profile, Devices, NotAuthorized, NoConnection, UpdateRequired
│   ├── theme/                         # tokens copied from frontend/src/theme, light/dark, override
│   └── formats/                       # DD/MM/YYYY helpers
├── e2e/                               # Maestro smoke flow
└── __tests__/                         # Jest and RNTL

docs/spec-roadmap.md                   # status of row 018 updated as the work progresses
```

**Structure Decision**: web application plus a new `mobile/` client (the template's mobile-plus-API
shape), keeping the existing `backend/` and `frontend/` as they are. All server changes are in the
two modules that already own this behavior, `identity` and `audit`, so no new module appears.

## Complexity Tracking

No Constitution Check violations to justify. The one addition worth stating is a third top-level
project (`mobile/`); the constitution explicitly calls for a React Native client, so it is a stated
requirement, not a deviation.

## Risks and notes for tasks

- **Audit write path**: events are consumed asynchronously after the request. The `ClientContext`
  must be captured when the event is published, not read later. A test must prove a row written
  after the request still carries the right location.
- **Unauthenticated events** (`SIGN_IN_FAILURE`, `LOCKOUT`) have no user but do have the client
  context; they are the most useful rows for the "where did this attempt come from" audit.
- **Migration number** may collide with in-flight spec 008 work; renumber at implementation time.
- **Log hygiene**: confirm no access log, request logger or error report records `X-HLS-Location`.
- **API Access volume**: about 7 million rows a year at 100 users. Index on time and on user and time
  (data-model.md); a retention period is an open question for HLS and a blocker for production release.
- **Event registry growth**: every Android request also creates a Spring Modulith event-publication
  row. Tasks must confirm that completed publications are purged or archived by the existing
  configuration (`spring.modulith.events.*`), and add the setting if they are not, so the registry does
  not grow unbounded.
- **Filter order**: `ApiAccessFilter` is the outermost filter so 401 and 426 responses are recorded;
  it publishes through a `@Transactional` service because module events are delivered after commit.
- **Privacy weight**: this trail records each staff member's position at every request. Access is
  Admin and System only, behind its own permission key, with no business data stored in the rows.
- **Spec 011** will later own the three `hls.mobile.*` settings; keep the names stable.
- **Heat map** (later, System-only, web) depends on FR-026a; the stored precision here is what it
  will plot. No map code in this release.
- **Distribution and privacy-notice wording** remain open and do not block implementation.
