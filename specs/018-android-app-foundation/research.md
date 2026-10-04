# Research: Android App Foundation

Decisions taken before design. Each was checked against the existing code (`backend/`, `frontend/`)
and the specs it reuses (001 auth, 002 access model, 003 audit).

## 1. Mobile client stack

- **Decision**: React Native with TypeScript, built with Expo (managed workflow, EAS Build for the
  Android artifact), in a new top-level `mobile/` folder. Navigation with React Navigation. UI with
  React Native Paper (Material 3) using the same design tokens as the web theme.
- **Rationale**: Constitution *Additional Constraints* fixes React Native, Android-first. Expo gives
  secure storage, location, and build tooling without owning native Gradle code, which suits a
  team that is mostly Java and React. Paper is the closest match to the web's MUI (both Material),
  so Principle IV's "one design system" is kept by sharing tokens rather than components.
- **Alternatives considered**: bare React Native (more native upkeep for no gain here); Flutter
  (contradicts the constitution); a PWA of the web app (cannot do reliable secure storage and
  the constitution already chose native).

## 2. Code sharing with the web app

- **Decision**: no shared npm package or monorepo workspace in this release. The mobile app has its
  own thin API client. Design tokens (colors, spacing, type scale) are copied from
  `frontend/src/theme` into `mobile/src/theme/tokens.ts` with a note naming the source file.
  Correctness of the API usage is protected by contract tests against `contracts/mobile-api.md`.
- **Rationale**: a workspace would change the web build and CI for a first release that only shares
  a few types. Copy-and-note is cheap and reversible; a shared package can come with the
  second mobile spec when more types are common.
- **Alternatives considered**: npm workspace with `packages/api-types` (more setup, touches web
  CI); generating a client from OpenAPI (the repo has no OpenAPI document today).

## 3. Renewal credential transport

- **Finding**: the web app carries the renewal credential in an `HttpOnly` cookie
  (`RenewalCookies`, `POST /api/v1/auth/renew` reads `@CookieValue`). A native app has no cookie
  jar it should rely on, and the credential must live in Android Keystore-backed storage.
- **Decision**: same endpoints, chosen by a client header. When a request carries
  `X-HLS-Client: android/<version>`, the server returns the renewal credential in the JSON body
  (`renewalCredential`) and accepts it in the body of `renew`; it sets no cookie. Requests without
  the header behave exactly as today and never see the credential in a body.
- **Rationale**: reuses every rule in `SessionService` (rotation, reuse detection, 14-day expiry
  by default, lockout) with no duplicated logic and no new endpoints for the same actions. The web
  keeps its stronger "script cannot read it" cookie.
- **Alternatives considered**: separate `/api/v1/mobile/auth/*` endpoints (duplicates controllers,
  and the two would drift); using the cookie in React Native (unreliable and unsafe storage).

## 4. Where the app stores secrets and data

- **Decision**: the renewal credential and user identity live only in Expo SecureStore (Android
  Keystore). The short-lived access token is kept in memory only. Non-sensitive preferences (theme
  choice, whether the location explanation was shown) use plain storage. Logout and failed renewal
  clear all of it.
- **Decision**: Android `allowBackup` is `false`; the app window is marked secure so the recent-apps
  thumbnail and screenshots are blank (FR-004).
- **Alternatives considered**: keeping the access token in storage (no benefit, it is 15 minutes);
  biometric re-open lock (was offered in clarification Q1 option C and not chosen).

## 5. Carrying location and client facts on every call

- **Decision**: the app adds request headers on every call to the HLS API:
  - `X-HLS-Client: android/<appVersion>`
  - `X-HLS-Location: lat=<..>;lng=<..>;acc=<m>;ts=<epoch ms>` when a position was captured
  - `X-HLS-Location-Status: PERMISSION_DENIED | SERVICES_OFF | NO_FIX | OTHER` when it was not
  - `X-HLS-Device-Integrity: ROOTED_SUSPECTED` when the check trips (sent with sign-in calls)
- **Rationale**: headers work on GET, POST and DELETE alike (FR-018 says "sends or receives"), need
  no change to any existing request body, and are read in one place on the server. A body field
  would mean editing every endpoint.
- **Alternatives considered**: a query parameter (leaks coordinates into proxy and access logs); a
  separate "report location" call (extra call, and not tied to the real request).
- **Privacy note**: `X-HLS-Location` MUST be excluded from request/access logging. The existing
  structured logging config is reviewed in tasks to confirm headers are not logged.

## 6. Capturing the position, and the grouping of calls

- **Decision**: foreground location only (`expo-location`, Android `ACCESS_FINE_LOCATION` and
  `ACCESS_COARSE_LOCATION`; no `ACCESS_BACKGROUND_LOCATION` in the manifest, enforced by a build
  check). A single `LocationGate` runs before each API call:
  1. if a reading is younger than the reuse window, use it (this is how a burst of calls on one
     screen load shares one reading);
  2. otherwise request a fix, waiting at most the wait limit, and if none arrives send the call
     with status `NO_FIX`.
  Defaults: wait limit 4 seconds, reuse window 10 seconds.
- **Decision**: both values are served by `GET /api/v1/mobile/app-config` (see §8) so they can be
  tuned without a new release. The app falls back to the defaults if that call fails.
- **Rationale**: matches spec FR-021/FR-022. Android's `getLastKnownPosition` is not used as a
  substitute for a fresh fix, because an old position would misreport where an action happened.
- **Alternatives considered**: a watcher that keeps the position warm (violates "never read
  location except at an API call", FR-018).

## 7. Server handling: one request-scoped client context

- **Decision**: a servlet filter in the `identity` module parses the headers into an immutable
  `ClientContext` (source `ANDROID`/`WEB`, app version, `LocationCapture`, device-rooted flag) and
  stores it as a request attribute. `ClientContextHolder.current()` returns it (a `WEB` context with
  no location when no header was sent).
- **Decision**: the existing events `LoginHistoryRecorded` and the request-triggered events in
  `identity.activity` gain a nullable `ClientContext` component, filled at publish time from the
  holder, because the audit consumers run after the request on another thread (`@ApplicationModuleListener`).
  `ClientContext` lives in a new named-interface package `com.hls.identity.clientcontext`, the same
  way `identity.loginhistory` and `identity.activity` are exposed to `audit` today.
- **Rationale**: keeps `audit` the only writer of audit tables (Principle VII) and needs no change
  to controllers beyond the points that already publish.
- **Alternatives considered**: audit reading the HTTP request itself (wrong direction; audit must
  not depend on the web layer); a new audit table for location (the spec says it belongs on the
  entry itself).

## 7a. Recording every request (API Access trail)

- **Decision (analysis 2026-10-04)**: location is captured on every call and stored for every call. A
  second servlet filter in `identity.clientcontext` (`ApiAccessFilter`) runs around the whole request,
  and after the response is written publishes `ApiAccessRecorded` (user and session from the JWT if
  present, method, matched route pattern, status code, and the `ClientContext`). Only requests with an
  Android `ClientContext` are recorded; `GET /api/v1/mobile/app-config` is excluded. The `audit`
  module consumes the event into the new `api_access_entry` table, deduplicating on the event id.
- **Rationale**: it makes the stated purpose ("where did each action come from") true for reads as
  well as writes, and gives the later heat map real density. Publishing after the response means the
  status code is known and a failure to record never changes the response.
- **Route pattern, not path**: the matched pattern (Spring's best-matching-pattern attribute) is
  stored, so ids and query strings never reach the audit table.
- **Costs accepted**: a fresh fix can delay a call by up to the wait limit (mitigated by the 10 s
  reuse window); roughly 7 million small rows a year at 100 users; and more sensitive location data
  about staff, which is why access is limited to Admin and System and a separate permission key
  (`AUDIT_API_ACCESS`) lets them be switched off independently.
- **Alternatives considered**: capture only on calls that create an audit row (rejected by the
  product owner); log in the web server's access log (not append-only, not queryable by the audit
  screens, and would put coordinates in log files).

## 8. Validation of a received location

- **Decision**: a location is valid when latitude is within -90..90, longitude within -180..180,
  accuracy is between 0 and 5,000 m, and the capture time is within 5 minutes of the server time
  (past or future). Anything else is stored as status `INVALID` with the values discarded, and the
  request continues (FR-024). A malformed header is treated the same way, never an error response.
- **Rationale**: location must never block sign-in (FR-021, FR-025). The 5 minute window tolerates
  normal clock drift but catches a wrong device clock.

## 9. Minimum app version and app configuration

- **Finding**: spec 011 (system settings) does not exist yet, so there is no settings UI.
- **Decision**: the app-config response is public by design (spec FR-030) and carries no secrets. `hls.mobile.min-app-version`, `hls.mobile.location-wait-seconds` and
  `hls.mobile.location-reuse-seconds` are deployment configuration for now. A public, cacheable
  `GET /api/v1/mobile/app-config` returns them. When a request carries `X-HLS-Client` below the
  minimum, the filter answers `426` with `{ "code": "APP_UPDATE_REQUIRED", "minimumVersion": "..." }`
  for every endpoint except `app-config` itself. Moving these into the settings module is noted for
  spec 011.
- **Alternatives considered**: database settings table now (premature, and duplicates 011).

## 10. Roles allowed in the app

- **Decision**: enforced on the server, not only in the UI. For an Android client, `login` and OTP
  `verify (SIGN_IN)` check that the user holds at least one of TEACHER, MANAGER, DIRECTOR. If not,
  no session is created, the response is `403 { "code": "WEB_ONLY_ROLE" }`, and a login-history
  entry (`SIGN_IN_FAILURE`, outcome "Role not permitted in the mobile app") is recorded. Order of
  checks: credentials first, so the response never reveals anything for a wrong password.
- **Rationale**: Principle X says the backend is the control. The check runs after credential
  success, so it cannot be used to discover accounts.

## 11. Menus in the app

- **Decision**: the app calls `GET /api/v1/me/access-model` after sign-in, on start, and when it
  returns from the background after 5 minutes. It maps each `route` in the response to a screen in a
  local registry (`/dashboard`, `/account/profile`, `/account/sessions`). Routes not in the registry
  are dropped. A user with Admin plus Teacher therefore sees only items that have an app screen.
- **Decision**: Sessions has no web route today. Spec 001 serves the list at
  `GET /api/v1/me/sessions`, and the app presents it under ACCOUNT → Profile → "Signed-in devices".
  It is a screen reached from Profile, not a server navigation item.
- **Rationale**: FR-009 to FR-011 with no per-role logic in the app.

## 12. Offline and failure behavior

- **Decision**: a network failure on start for a signed-in user shows a "No connection" screen with
  Retry and no cached menu (clarification Q3). Logout always clears local data first, then tries to
  end the server session; if it cannot, the credential is dropped locally and the server session
  simply expires (FR-006). The login screen shows "No connection" when sign-in cannot reach the
  server.

## 13. Distribution, target and testing

- **Decision**: minSdk 29 (Android 10, clarification Q5), targetSdk the current Google Play
  requirement at build time. Distribution (Play Store vs private) is a release decision and does not
  change the code; EAS profiles `development`, `preview`, `production`.
- **Decision**: tests are Jest with React Native Testing Library for screens, hooks and the API
  client, plus contract tests (mock server built from `contracts/mobile-api.md`). A Maestro smoke
  flow (sign in → menu → logout) is added for emulator runs. Accessibility is checked with RNTL
  accessibility queries and a manual TalkBack pass listed in `quickstart.md`. Backend tests are JUnit
  with Testcontainers, matching specs 001-005.
- **Alternatives considered**: Detox (heavier native setup); skipping E2E (the sign-in to logout
  path crosses secure storage and permissions, which unit tests cannot prove).

## 14. Rooted-device signal

- **Decision**: best-effort check in the app (known root artifacts and a build-tag test via a
  maintained library such as `jail-monkey`) and reported as `X-HLS-Device-Integrity: ROOTED_SUSPECTED`
  on sign-in only. The server stores it on the login-history entry and never uses it to decide
  anything (FR-028a).
- **Alternatives considered**: Play Integrity attestation (a stronger signal, but needs Google Play
  distribution and server verification; deferred until a decision on distribution).

## Resolved unknowns

All Technical Context items are resolved. Open items carried to implementation, none blocking:
privacy-notice wording (HLS), distribution channel, app icon and name.
