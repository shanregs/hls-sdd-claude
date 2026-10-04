---

description: "Task list for feature implementation"
---

# Tasks: Android App Foundation

**Input**: Design documents from `/specs/018-android-app-foundation/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/mobile-api.md,
quickstart.md, and **specs 001, 002, 003 implemented** (this feature reuses spec 001's sessions and
sign-in, spec 002's `GET /api/v1/me/access-model`, and spec 003's audit store and screens). Spec 008
is a roadmap dependency only; none of these tasks use it.

**Tests**: included as first-class tasks. Constitution Principle IX requires per-role authorization
tests on every endpoint and menu-visibility tests per role; the location rules (never in the
background, never blocking, never shown to Teacher/Manager/Director) are only trustworthy with tests.

**Organization**: grouped by user story in priority order (spec.md US1-US4). Foundational work
(migration, client-context filter, version gate, app-config, mobile API client) comes first because
every story sends the `X-HLS-*` headers and reads the new columns.

## Format: `[ID] [P?] [Story] Description`

> **Note**: tasks T090-T098 were added after `/speckit-analyze` (API Access trail and other fixes). Their IDs are out of numeric order; the order of listing within each phase is the execution order.

- **[P]**: can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1-US4; absent for Setup/Foundational/Polish

## Path Conventions

`BE` = `backend/src/main/java/com/hls`, `BT` = `backend/src/test/java/com/hls`, `FE` =
`frontend/src`, `MIG` = `backend/src/main/resources/db/migration`, `MOB` = `mobile/src`,
`MOBT` = `mobile/__tests__`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: create the `mobile/` project and tooling.

- [ ] T001 Create the Expo React Native TypeScript project in `mobile/` (`package.json`, `tsconfig.json`, `babel.config.js`, `app.config.ts`, `.env.example` with `EXPO_PUBLIC_API_BASE_URL`), with minSdk 29 and the Android package id decided with the team
- [ ] T002 Add dependencies in `mobile/package.json`: `react-navigation` (native, drawer, native-stack), `react-native-paper`, `expo-location`, `expo-secure-store`, `expo-application`, `expo-screen-capture`, `@react-native-async-storage/async-storage`, a rooted-device check library (research §14), and dev dependencies `jest`, `jest-expo`, `@testing-library/react-native`, `eslint`, `prettier`, `typescript`
- [ ] T003 [P] Configure ESLint and Prettier for `mobile/` matching `frontend/eslint.config.js` rules, and add `npm run lint`, `npm test`, `npm run typecheck` scripts in `mobile/package.json`
- [ ] T004 [P] Configure Android build settings in `mobile/app.config.ts`: `allowBackup: false`, only the foreground location permissions (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`), no `ACCESS_BACKGROUND_LOCATION`, secure window enabled (research §4)
- [ ] T005 [P] Copy design tokens (color, spacing, type scale, light and dark) from `frontend/src/theme` into `mobile/src/theme/tokens.ts` with a header comment naming the source files, and build the Paper light/dark themes in `mobile/src/theme/themes.ts`
- [ ] T006 [P] Add EAS build profiles `development`, `preview`, `production` in `mobile/eas.json`, and a short `mobile/README.md` with run, test and build commands from quickstart.md
- [ ] T007 [P] Add a CI job (or script) running `npm ci`, lint, typecheck and `npm test` for `mobile/`, plus the manifest check from T083, in the repository's existing CI configuration

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: server plumbing and the mobile API client every story needs.

**⚠️ CRITICAL**: no user story work can begin until this phase is complete.

### Backend

- [ ] T008 Write Flyway migration `MIG/V16__add_client_context_to_audit_and_session.sql` (use the next free number if V16 is taken), adding only the columns in data-model.md: on `login_history_entry` — `source VARCHAR(10) NOT NULL DEFAULT 'WEB'`, `app_version VARCHAR(20)`, `location_status VARCHAR(20) NOT NULL DEFAULT 'NOT_APPLICABLE'`, `latitude NUMERIC(9,6)`, `longitude NUMERIC(9,6)`, `accuracy_meters REAL`, `location_captured_at TIMESTAMPTZ`, `device_rooted BOOLEAN NOT NULL DEFAULT false`; on `user_activity_entry` — the same except `device_rooted`; on `session` — `client_type VARCHAR(10) NOT NULL DEFAULT 'WEB'`, `app_version VARCHAR(20)`; add on both audit tables a CHECK that `location_status = 'AVAILABLE'` if and only if latitude, longitude, accuracy_meters and location_captured_at are all non-null; and create the table `api_access_entry` exactly as in data-model.md: `id UUID PRIMARY KEY`, `occurred_at TIMESTAMPTZ NOT NULL`, `source_event_id UUID NOT NULL UNIQUE`, `user_id UUID`, `session_id UUID`, `http_method VARCHAR(10) NOT NULL`, `route_template VARCHAR(200) NOT NULL`, `status_code SMALLINT NOT NULL`, plus `source VARCHAR(10) NOT NULL`, `app_version VARCHAR(20)`, `location_status VARCHAR(20) NOT NULL`, `latitude NUMERIC(9,6)`, `longitude NUMERIC(9,6)`, `accuracy_meters REAL`, `location_captured_at TIMESTAMPTZ`, the same CHECK, and indexes `(occurred_at DESC)` and `(user_id, occurred_at DESC)`, with no foreign key into another module's tables
- [ ] T009 [P] Create the named-interface package `BE/identity/clientcontext/` with `package-info.java` (`@org.springframework.modulith.NamedInterface`, same style as `BE/identity/loginhistory/package-info.java`), and the value types `ClientSource` (`WEB`, `ANDROID`), `LocationStatus` (`AVAILABLE`, `PERMISSION_DENIED`, `SERVICES_OFF`, `NO_FIX`, `INVALID`, `OTHER`, `NOT_APPLICABLE`), `LocationCapture` (status, latitude, longitude, accuracyMeters, capturedAt; invariant: when status is not `AVAILABLE` the four values are all null) and `ClientContext` (source, appVersion, location, deviceRooted) as records
- [ ] T010 Implement `ClientContextParser` in `BE/identity/clientcontext/ClientContextParser.java` that builds a `ClientContext` from the headers in contracts/mobile-api.md and validates a location per research §8: latitude within -90..90, longitude within -180..180, accuracy between 0 and 5000 metres, capture time within 5 minutes of server time (injected `Clock`); anything else (including a malformed header) becomes `INVALID` with the values discarded and never throws; no location header and no status header on an Android request becomes `OTHER`; web requests get `NOT_APPLICABLE`
- [ ] T011 Implement `ClientContextHolder` (request-scoped access, returns a `WEB`/`NOT_APPLICABLE` context outside a request) and `ClientContextFilter` (a `OncePerRequestFilter` that parses once and stores the context as a request attribute) in `BE/identity/clientcontext/`
- [ ] T012 Implement the version gate in `BE/identity/mobile/MobileVersionGate.java` (a filter after `ClientContextFilter`): when `X-HLS-Client` is below `hls.mobile.min-app-version`, answer `426` with `{ "code": "APP_UPDATE_REQUIRED", "message": "Please update the HLS app to continue.", "minimumVersion": "..." }` for every path except `GET /api/v1/mobile/app-config`; add the three properties `hls.mobile.min-app-version`, `hls.mobile.location-wait-seconds` (default 4), `hls.mobile.location-reuse-seconds` (default 10) to `backend/src/main/resources/application.yml`
- [ ] T013 Implement `GET /api/v1/mobile/app-config` (spec FR-030, public by design, non-sensitive values only; runs after T011 and T012) in `BE/identity/mobile/AppConfigController.java` returning `minimumVersion`, `locationWaitSeconds`, `locationReuseSeconds` with a 5 minute cache header, and permit it without authentication in `BE/identity/security/SecurityConfig.java`; register `ClientContextFilter` and `MobileVersionGate` in the same file
- [ ] T014 [P] Confirm and enforce log hygiene: review `backend/src/main/resources/logback-spring.xml` and any request-logging code so `X-HLS-Location` is never written to application or access logs, and add a regression test `BT/identity/clientcontext/LocationHeaderNotLoggedTest.java` that sends a request with the header and asserts the captured log output does not contain the coordinates
- [ ] T015 [P] Backend tests for the foundation in `BT/identity/clientcontext/`: `ClientContextParserTest` (valid location; latitude 91; longitude -181; accuracy -1 and 5001; capture time 6 minutes off; malformed header; status-only header for each status; no headers on Android gives `OTHER`; web gives `NOT_APPLICABLE`; invalid discards the values), and `BT/identity/mobile/AppConfigAndVersionGateTest.java` (below-minimum gets 426 on `/api/v1/auth/login` and `/api/v1/me/access-model` but 200 on `app-config`; web requests unaffected)
- [ ] T016 Update `BT/ApplicationModulesTest.java` / `BT/audit/ArchitectureRulesTest.java` expectations so the new `identity.clientcontext` package is a permitted dependency of `audit`, and add an ArchUnit rule in `BT/audit/ArchitectureRulesTest.java` that `com.hls.audit..` has no dependency on `jakarta.servlet..` or `org.springframework.web..`

### Mobile

- [ ] T017 [P] Implement `MOB/security/secureStore.ts` (Expo SecureStore wrapper: `saveRenewalCredential`, `readRenewalCredential`, `clearAll`) and `MOB/security/memoryToken.ts` (access token kept in memory only, never persisted)
- [ ] T018 Implement the HTTP client in `MOB/api/httpClient.ts`: base URL from env, adds `X-HLS-Client: android/<version>` (version from `expo-application`) and the `User-Agent` in contracts/mobile-api.md to every request, accepts a pluggable `ClientHeadersProvider` (used by location and rooted-flag in later tasks), adds `Authorization: Bearer` when a token exists, and maps outcomes to typed errors: `NoConnectionError`, `UpdateRequiredError` (426), `UnauthorizedError` (401), `WebOnlyRoleError` (403 `WEB_ONLY_ROLE`), `LockedError` (423), `RateLimitedError` (429)
- [ ] T019 [P] Implement `MOB/api/appConfig.ts` (calls `GET /api/v1/mobile/app-config`, falls back to defaults wait 4 s and reuse 10 s if the call fails) and `MOB/config/constants.ts`
- [ ] T020 [P] Tests for the client in `MOBT/api/httpClient.test.ts`: headers present on every call, token never written to storage, each status maps to the right typed error, no `X-HLS-Location` header is added when no provider supplies one

**Checkpoint**: backend filter, gate and config work; the mobile client can talk to the server.

---

## Phase 3: User Story 1 - Sign In to the Android App (Priority: P1) 🎯 MVP

**Goal**: a Teacher, Manager or Director signs in by one-time code or password, lands on a home screen, and stays signed in across reopen.

**Independent Test**: sign in as a Teacher with a code and a Manager with a password, reopen the app and remain signed in; an Admin-only user is refused (quickstart scenarios 1-4).

### Tests for User Story 1

- [ ] T021 [P] [US1] `BT/identity/mobile/MobilePasswordLoginTest.java`: with `X-HLS-Client` the 200 body contains `renewalCredential` and no `Set-Cookie` is sent; without the header the response is unchanged (cookie, no `renewalCredential`); wrong password stays 401 with the generic message; locked account stays 423
- [ ] T022 [P] [US1] `BT/identity/mobile/MobileOtpVerifyTest.java`: same body-credential behavior for `POST /api/v1/auth/otp/verify` with purpose `SIGN_IN`; purpose `PASSWORD_RESET` is unchanged
- [ ] T023 [P] [US1] `BT/identity/mobile/MobileRoleEligibilityTest.java`: Admin-only and System-only users get 403 `WEB_ONLY_ROLE` after correct credentials with no session row created and a `SIGN_IN_FAILURE` login-history event; Admin plus Teacher is allowed; Teacher, Manager, Director allowed; a wrong password for an Admin user still returns the generic 401 (never `WEB_ONLY_ROLE`); web login for an Admin user still succeeds
- [ ] T024 [P] [US1] `BT/identity/mobile/MobileRenewTest.java`: renew with the credential in the body returns a new access token and a rotated credential; an already-used credential returns 401 and revokes the whole session chain with a `SESSION_REVOKED_REUSE` event; expired and unknown credentials return 401; the web cookie path is unchanged
- [ ] T025 [P] [US1] `MOBT/auth/signIn.test.tsx`: OTP flow and password flow happy paths, wrong password message, lockout message with the time, `WEB_ONLY_ROLE` message and no session kept, 429 messages with retry time, no-connection message, forgot-password flow
- [ ] T026 [P] [US1] `MOBT/auth/sessionRestore.test.tsx`: reopen with a stored credential renews silently and reaches Home; failed renewal clears storage and shows Sign In; update-required response shows the update screen and blocks sign-in

### Implementation for User Story 1

- [ ] T027 [US1] Add `clientType` and `appVersion` to the session entity and `SessionService.createSession(...)` in `BE/identity/session/Session.java` and `BE/identity/session/SessionService.java`, filled from `ClientContextHolder`
- [ ] T028 [US1] Implement `BE/identity/mobile/MobileRoleEligibility.java` (true when the user's roles include `TEACHER`, `MANAGER` or `DIRECTOR`) and apply it in `BE/identity/auth/AuthController.java#login` and the OTP sign-in verify in `BE/identity/otp/OtpController.java`, after credential success and before `createSession`, returning 403 `{ "code": "WEB_ONLY_ROLE", "message": "Your account uses the HLS web application." }`; record a `SIGN_IN_FAILURE` event with outcome `Role not permitted in the mobile app`
- [ ] T029 [US1] In `BE/identity/auth/AuthController.java`, `BE/identity/otp/OtpController.java` and `BE/identity/auth/AuthDtos.java`: for Android clients return `renewalCredential` in the response body and send no cookie; leave the web path untouched (research §3)
- [ ] T030 [US1] In `BE/identity/auth/AuthController.java#renew`: when the client is Android read `renewalCredential` from the request body instead of the cookie, apply the same rotation, reuse detection and expiry in `SessionService`, return the rotated credential in the body, and never set or expire a cookie for Android
- [ ] T031 [P] [US1] Implement `MOB/api/authApi.ts` (login, otp request, otp verify, renew, password-reset channels, password-reset complete, as in spec 001's auth-api.md) with the typed errors from T018
- [ ] T032 [US1] Implement the rooted check in `MOB/security/deviceIntegrity.ts` and a `ClientHeadersProvider` that adds `X-HLS-Device-Integrity: ROOTED_SUSPECTED` on the sign-in and OTP-verify calls only when the check trips (FR-028a)
- [ ] T033 [US1] Implement `MOB/auth/AuthProvider.tsx`: states `restoring`, `signedOut`, `signedIn`, `noConnection`, `updateRequired`; stores the renewal credential in secure storage and the access token in memory; silent renewal on start and a single-flight renewal on the first 401; clears everything and returns to sign-in on failed renewal or reuse
- [ ] T034 [US1] Implement the Sign In screen in `MOB/screens/SignInScreen.tsx` with two clearly labelled options (phone or username with password, or a one-time code), the same inline validation and non-revealing messages as the web, and the OTP resend cooldown display (spec 001 FR-023/FR-028); never pre-fill the previous user's identity
- [ ] T035 [P] [US1] Implement the password-reset screens in `MOB/screens/ResetPasswordScreen.tsx` (identifier, channel choice from `password-reset/channels`, code entry, new password with the 10-character rule, success)
- [ ] T036 [P] [US1] Implement `MOB/screens/UpdateRequiredScreen.tsx` and `MOB/screens/NoConnectionScreen.tsx` (Retry button), and check `app-config.minimumVersion` at startup before Sign In
- [ ] T037 [US1] Wire the root navigator in `MOB/navigation/RootNavigator.tsx` to show `SignIn`, `ResetPassword`, `NoConnection`, `UpdateRequired` or the signed-in shell (placeholder until US3) from `AuthProvider` state, and register the app in `mobile/index.ts` / `mobile/App.tsx`

**Checkpoint**: users can sign in and out of the sign-in state; US1 is demonstrable on its own.

---

## Phase 4: User Story 2 - Sign Out and Manage Sessions (Priority: P1)

**Goal**: logout clears the device and the server session; the user can list and end other sessions; a forced sign-out returns to Sign In.

**Independent Test**: sign out and confirm nothing remains; sign in on two devices and end one from the other (quickstart 6-8).

### Tests for User Story 2

- [ ] T038 [P] [US2] `BT/identity/mobile/MobileSessionsTest.java`: `GET /api/v1/me/sessions` returns `clientType` and `appVersion`, marks the current session, lists web and Android sessions together; ending another user's session returns 403; logout of an Android session returns 204 with no cookie header
- [ ] T039 [P] [US2] `MOBT/auth/logout.test.tsx`: logout calls the server, clears secure storage, memory token and cached user data, and shows Sign In; with a failing network call the device is still cleared; after logout back navigation shows no signed-in screen; a deactivated user or ended session returns to Sign In with an explanation
- [ ] T040 [P] [US2] `MOBT/screens/devices.test.tsx`: sessions list shows device, last used, current marked, Android versus Web, and ending a session removes it

### Implementation for User Story 2

- [ ] T041 [US2] Add `clientType` and `appVersion` to `SessionView` in `BE/identity/session/SessionController.java` and return them from `GET /api/v1/me/sessions`; adjust `logout` in `BE/identity/auth/AuthController.java` so an Android session expires no cookie
- [ ] T042 [P] [US2] Implement `MOB/api/sessionsApi.ts` (list, end one, logout) per contracts/mobile-api.md
- [ ] T043 [US2] Implement `logout()` in `MOB/auth/AuthProvider.tsx`: clear secure storage, memory token, async storage user data and any in-memory caches first, then call `POST /api/v1/auth/logout` best-effort, then navigate to Sign In and reset the navigation stack so back cannot return (FR-006)
- [ ] T044 [US2] Handle forced sign-out in `MOB/api/httpClient.ts` and `MOB/auth/AuthProvider.tsx`: on a 401 after a failed single renewal, or reuse detection, clear everything and show Sign In with a short explanation message (FR-005, FR-008)
- [ ] T045 [US2] Implement `MOB/screens/DevicesScreen.tsx` ("Signed-in devices", reached from Profile): list, current marked, Android or Web label, end-session action with confirmation, and error and empty states
- [ ] T046 [US2] Make the app window secure and verify no credential in logs: enable the secure window in `MOB/security/secureWindow.ts` (using `expo-screen-capture`), and add a test `MOBT/security/noSecretsLogged.test.ts` that signs in with a console spy and asserts no token or credential is logged

**Checkpoint**: US1 and US2 together give a complete, safe sign-in/sign-out loop.

---

## Phase 5: User Story 3 - See Only the Menus My Roles Allow (Priority: P1)

**Goal**: the home and menu are built only from the server access model, with items hidden when the app has no screen; ACCOUNT, not-authorized state and theme.

**Independent Test**: sign in as Teacher, Manager, Director and compare the menu to the server model; change a grant on the web and see the app follow (quickstart 5, 17).

### Tests for User Story 3

- [ ] T047 [P] [US3] `MOBT/access/menuFromAccessModel.test.tsx`: for Teacher, Manager, Director and a two-role user fixture the menu equals the server navigation filtered to routes with a screen; no duplicates for the union; unknown routes are ignored without error; a section with no remaining items is omitted
- [ ] T048 [P] [US3] `MOBT/access/refresh.test.tsx`: access model is fetched after sign-in, on start, and on return from background after 5 minutes; a changed model changes the menu with no restart
- [ ] T049 [P] [US3] `MOBT/access/noHardCodedRoles.test.ts`: scans `MOB/` and fails if any source other than the sign-in error handling and test fixtures decides a menu or screen by the strings `TEACHER`, `MANAGER`, `DIRECTOR`, `ADMIN` or `SYSTEM`
- [ ] T050 [P] [US3] `MOBT/screens/notAuthorized.test.tsx` and `MOBT/screens/home.test.tsx`: opening a route not in the navigation shows Not authorized with a way home; Home shows name, roles and one skeleton card per section of the server model, with identical rendering logic for every role (no role names)
- [ ] T051 [P] [US3] `MOBT/theme/theme.test.tsx`: follows device setting by default, in-app override persists across restarts, all five screens render in both themes
- [ ] T052 [P] [US3] `MOBT/a11y/screens.a11y.test.tsx`: Sign In, Home, ACCOUNT/Profile, Devices, Not authorized and No connection have accessible labels and roles on every interactive element and a minimum 48 dp touch target

### Implementation for User Story 3

- [ ] T053 [P] [US3] Implement `MOB/api/accessModelApi.ts` calling `GET /api/v1/me/access-model` and typing the response from specs/002-access-model-app-shell/contracts/access-model-api.md
- [ ] T054 [US3] Implement the route-to-screen registry in `MOB/access/screenRegistry.ts` (`/dashboard` → Home, `/account/profile` → Profile; Devices is reached from Profile and is not a server item) and `MOB/access/useMenu.ts` that returns the server navigation filtered by the registry, with sections that end up empty removed
- [ ] T055 [US3] Implement `MOB/access/AccessModelProvider.tsx`: fetch after sign-in and on start, refresh when the app returns from background after 5 minutes, keep the model in memory only, expose loading, error and a `canOpen(route)` check
- [ ] T056 [US3] Implement the signed-in shell in `MOB/navigation/AppShell.tsx` (drawer built from `useMenu`, header with the user's name, ACCOUNT section always last, route guard that sends an unauthorized destination to Not authorized)
- [ ] T057 [P] [US3] Implement `MOB/screens/HomeScreen.tsx` with the user's name, roles and one skeleton card for each section of the server-provided menu (spec FR-015; no per-role content), and loading, empty and error states
- [ ] T058 [P] [US3] Implement `MOB/screens/ProfileScreen.tsx` using `GET/PUT /api/v1/me/profile` (the same fields and edit rules as the web Profile) with a "Signed-in devices" row and a "Location and privacy" row (the latter is wired in T082), plus `MOB/api/profileApi.ts`
- [ ] T059 [P] [US3] Implement `MOB/screens/NotAuthorizedScreen.tsx` with a "Go to home" action
- [ ] T060 [US3] Implement theme selection in `MOB/theme/ThemeProvider.tsx` (follow device by default, override in plain async storage, remembered) and an ACCOUNT toggle; apply Paper themes from T005
- [ ] T061 [US3] Add the date formatter for DD/MM/YYYY and Indian locale in `MOB/formats/dates.ts` and use it wherever a date is shown (sessions, profile)

**Checkpoint**: signed-in users see the right menu with no role logic in the app.

---

## Phase 6: User Story 4 - Location Recorded With Each API Call (Priority: P2)

**Goal**: location is captured only at an API call, sent with it, stored with the login-history or user-activity entry, and visible only to Admin and System.

**Independent Test**: sign in with location allowed, denied and off and inspect Login History on the web; confirm nothing is recorded while idle (quickstart 9-14).

### Tests for User Story 4

- [X] T062 [P] [US4] `BT/audit/loginhistory/LoginHistoryLocationTest.java`: a sign-in with a valid location header stores status `AVAILABLE` with latitude, longitude, accuracy, capture time, `source = ANDROID`, app version; each of the four reason statuses stores no coordinates; an invalid location stores `INVALID` and no values; a web sign-in stores `WEB` and `NOT_APPLICABLE`; the rooted flag is stored on the login-history row only; a failed sign-in (`SIGN_IN_FAILURE`, `LOCKOUT`) also carries the context
- [X] T063 [P] [US4] `BT/audit/useractivity/UserActivityLocationTest.java`: a profile update, password change and own-session end from an Android client each produce a user-activity row with the same context; an event triggered with no request (for example automatic lock expiry) has `WEB` and `NOT_APPLICABLE`
- [X] T064 [P] [US4] `BT/audit/AuditLocationAsyncCaptureTest.java`: the context is captured when the event is published, so a row consumed after the request thread has ended still carries the right location, and redelivery of the same event creates no duplicate row (existing `source_event_id` key)
- [X] T065 [P] [US4] `BT/audit/AuditLocationVisibilityTest.java`: per role, Admin and System see the new fields on `GET /api/v1/audit/login-history`, `/user-activity`, `/logs` (login-history and user-activity rows) and the exports; Director, Manager and Teacher receive 403 and never the data; a Teacher or Manager calling any other endpoint never receives location; the `source=ANDROID|WEB` filter works and an invalid `source` returns 400
- [X] T066 [P] [US4] `BT/audit/AuditLocationExportTest.java`: CSV exports for login history, user activity and logs contain the new columns `source`, `app_version`, `location_status`, `latitude`, `longitude`, `accuracy_meters` (and `device_rooted` for login history) with empty values when the status is not `AVAILABLE`
- [X] T067 [P] [US4] `MOBT/location/locationGate.test.ts`: allowed permission returns a fix and sends `X-HLS-Location`; denied sends `PERMISSION_DENIED`; services off sends `SERVICES_OFF`; no fix within the wait limit sends `NO_FIX` and the call proceeds without extra delay beyond that limit; a burst of calls inside the reuse window shares one reading; a call after the reuse window takes a fresh reading; the wait and reuse values come from `app-config`; an approximate-only permission grant still yields a reading; a permission changed in device settings while the app runs is honoured on the next call
- [X] T068 [P] [US4] `MOBT/location/noIdleLocation.test.ts`: with the app idle, backgrounded, or after sign-out, `expo-location` is never called; no timers or watchers are registered by the location module
- [X] T069 [P] [US4] `MOBT/location/consent.test.tsx`: the plain-language explanation is shown before the first system permission prompt, can be reopened from ACCOUNT, and denying leaves sign-in working
- [X] T070 [P] [US4] `FE/features/audit/LoginHistoryPage.test.tsx`, `FE/features/audit/UserActivityPage.test.tsx` and `FE/features/audit/AuditLogsPage.test.tsx` (extend): the new Source, App version and Location columns render, "Location unavailable — <reason>" shows for non-`AVAILABLE` entries, the rooted flag shows on login history, the Source filter works, and vitest-axe passes in both themes; from the AUDIT menu an Admin reaches a sign-in's location in at most 3 clicks (SC-009)

- [ ] T090 [P] [US4] `BT/audit/apiaccess/ApiAccessRecordingTest.java`: every request from an Android client except `GET /api/v1/mobile/app-config` produces exactly one `api_access_entry`, including GET reads and failed requests (a 401 sign-in has a null `user_id`, a request rejected with 401 by security and a request answered 426 by the version gate are recorded too); the entry holds `http_method`, the matched `route_template` (never the query string or actual id values; `UNMATCHED` for no match), `status_code`, `source`, `app_version` and the location or its reason; no request or response body, header or query string is stored anywhere in the row; web requests produce no entry; the entry exists after a real HTTP request that has no surrounding test transaction (this fails if the event is published outside a transaction); an entry is still written when the consumer runs after the request thread ended; redelivery creates no duplicate
- [ ] T091 [P] [US4] `BT/audit/apiaccess/ApiAccessVisibilityTest.java`: per role, Admin and System get 200 on `GET /api/v1/audit/api-access` and `/export`; Director, Manager and Teacher get 403; filters `userId`, `from`, `to`, `locationStatus`, `httpMethod` work; `to` before `from` and an unknown `locationStatus` return 400; pagination stays responsive with 10,000 rows (as in spec 003)
- [ ] T097 [P] [US4] `BT/identity/clientcontext/LocationNeverGatesRequestTest.java`: for a sample of endpoints (sign-in, renew, access-model, profile, sessions), a request with no location, a status-only header, or an invalid location returns the same outcome as one with a valid location (FR-025), and a failure while recording the API Access entry does not change the response

### Implementation for User Story 4

- [X] T071 [US4] Add `ClientContext clientContext` (nullable) to `LoginHistoryRecorded` in `BE/identity/loginhistory/LoginHistoryRecorded.java` and fill it in `BE/identity/loginhistory/LoginHistoryPublisher.java` from `ClientContextHolder.current()` at publish time; keep existing constructors/callers compiling
- [X] T072 [US4] Add `ClientContext clientContext` (nullable) to the request-triggered events in `BE/identity/activity/` (`SessionEnded`, `PasswordChanged`, `PasswordResetByAdmin`, `PasswordResetCompleted`, `PasswordResetRequested`, `ProfileUpdated`, `UserCreated`, `UserRoleChanged`, `AccountActivationChanged`, `AccountLockChanged`) and fill it from `ClientContextHolder.current()` at each publish site; events published outside a request pass the `WEB`/`NOT_APPLICABLE` context
- [X] T073 [US4] Persist the context in `BE/audit/loginhistory/LoginHistoryEntry.java` and `LoginHistoryEventConsumer.java`, and in `BE/audit/useractivity/UserActivityEntry.java` and `UserActivityEventConsumer.java`, using the columns from T008: `source` default `'WEB'`, `location_status` default `'NOT_APPLICABLE'`, coordinates `NUMERIC(9,6)`, accuracy `REAL`; set `device_rooted` on login history only
- [X] T074 [US4] Return the new fields in `BE/audit/AuditLoginHistoryController.java`, `BE/audit/AuditUserActivityController.java` and, for login-history and user-activity rows only, the list in `BE/audit/AuditLogsController.java` as in contracts/mobile-api.md (nested `location` object; `deviceRooted` on login history only), keeping authorization on the existing `AUDIT` view permission, and add the optional `source` query filter that returns 400 for any value other than `WEB` or `ANDROID`
- [X] T075 [US4] Add the new columns to the CSV in `BE/audit/support/CsvStreamingExporter.java` and the exports in `BE/audit/AuditLoginHistoryController.java`, `AuditUserActivityController.java` and `AuditLogsController.java` per contracts/mobile-api.md; the combined logs view carries them for rows from either source
- [X] T076 [US4] Update the audit contract documentation in `specs/003-audit/contracts/audit-api.md` with a short "Added by 018" section pointing to contracts/mobile-api.md (no other change to spec 003's text), and add the public `app-config` endpoint to the list of public capabilities in `specs/001-identity-access/spec.md` FR-001 and `contracts/auth-api.md` as an addition made by spec 018
- [X] T077 [P] [US4] Update the web audit screens: add Source, App version and Location (coordinates with accuracy, or "Location unavailable — reason") columns, the rooted flag, and a Source filter in `FE/features/audit/LoginHistoryPage.tsx`, `FE/features/audit/UserActivityPage.tsx`, `FE/features/audit/AuditLogsPage.tsx` (login-history and user-activity rows only) and `FE/features/audit/AuditFilterBar.tsx`, with types in the audit API client used by those pages
- [X] T078 [US4] Implement `MOB/location/LocationGate.ts`: before each API call, reuse a reading younger than `locationReuseSeconds`, otherwise request one fix with `expo-location` (foreground only, balanced accuracy) waiting at most `locationWaitSeconds`; return a `ClientHeadersProvider` result of `X-HLS-Location` or `X-HLS-Location-Status` (`PERMISSION_DENIED`, `SERVICES_OFF`, `NO_FIX`, `OTHER`); never use last-known position as a substitute; no timers, watchers or background tasks (FR-018)
- [X] T079 [US4] Plug `LocationGate` into `MOB/api/httpClient.ts` as the header provider for every call to the HLS API, so sign-in, renew, access-model, profile, sessions and logout all carry it, and ensure a location failure can never make a call fail (FR-021, FR-025)
- [X] T080 [US4] Implement the consent flow in `MOB/location/LocationConsent.tsx` and `MOB/screens/LocationPrivacyScreen.tsx`: a plain-language explanation shown before the first system permission prompt (recorded only when the app talks to the server, used only for audit), followed by the foreground permission request; the same screen is reachable from Profile → "Location and privacy" (T058)
- [X] T081 [US4] Ensure location handling never blocks Sign In: if the first sign-in happens before consent, show the explanation and permission prompt on the Sign In screen without delaying the credential submit beyond the wait limit, in `MOB/screens/SignInScreen.tsx`
- [X] T082 [US4] Replace the placeholder "Location and privacy" row in `MOB/screens/ProfileScreen.tsx` with the real navigation to T080's screen, and show the current permission state there

- [ ] T092 [US4] Implement `BE/identity/clientcontext/ApiAccessFilter.java`: a servlet filter registered as the outermost filter (before the Spring Security chain, via a `FilterRegistrationBean` with the highest precedence, and ordered before `ClientContextFilter` only if it can still read the context after the chain returns) so that responses produced by security (401) and by `MobileVersionGate` (426) are recorded too. After the response is written it publishes `ApiAccessRecorded` (T093) when the `ClientContext` source is `ANDROID` and the request is not `GET /api/v1/mobile/app-config`. User and session come from the `sub` and `sid` claims of a valid bearer token (null when absent or invalid); also record the method, the status code, and the matched route pattern from Spring's best-matching-pattern request attribute (fallback `UNMATCHED`); never read bodies or the query string. Publish through a small `@Transactional` service class (as `BE/identity/loginhistory/LoginHistoryPublisher.java` does) because `@ApplicationModuleListener` events are only delivered after a transaction commits and would otherwise be silently dropped; catch and log any publishing error so it can never change the response
- [ ] T093 [US4] Add `ApiAccessRecorded` (eventId, occurredAt, userId, sessionId, httpMethod, routeTemplate, statusCode, `ClientContext`) in `BE/identity/clientcontext/ApiAccessRecorded.java` (the package is already a named interface)
- [ ] T094 [US4] Implement the audit side in `BE/audit/apiaccess/`: `ApiAccessEntry` (columns exactly as T008: `route_template VARCHAR(200)`, `status_code SMALLINT`, location columns, `source_event_id` unique), `ApiAccessEntryRepository`, `ApiAccessEventConsumer` (`@ApplicationModuleListener`, deduplicating on `source_event_id`), and `BE/audit/AuditApiAccessController.java` with `GET /api/v1/audit/api-access` and `/export` per contracts/mobile-api.md, authorized by `AUDIT_API_ACCESS`
- [ ] T095 [US4] Add `AUDIT_API_ACCESS` to `BE/identity/permissions/PermissionModule.java`, seed it View and Export for Admin and System only (false for all other roles) by adding it to the audit modules list in `BE/identity/permissions/PermissionMatrixService.java`, add the "API Access" item (`/audit/api-access`, section AUDIT) to `BE/identity/accessmodel/NavigationCatalog.java`, and update any existing tests that enumerate the audit modules or navigation
- [ ] T096 [P] [US4] Add `FE/features/audit/ApiAccessPage.tsx` (table with user, date range, location status and method filters, location column, CSV export via `useAuditExport.ts`), register its route and menu entry, and add `FE/features/audit/ApiAccessPage.test.tsx` including vitest-axe in both themes and a check that only users whose access model contains the item can reach it

- [ ] T098 [US4] Stop the Spring Modulith event-publication registry growing without bound now that every Android request publishes an event: `backend/src/main/resources/application.yml` has no `spring.modulith.events.*` setting today, so set the completion mode so completed publications are deleted (or archived), using the property name valid for the Spring Modulith version in `backend/pom.xml`, and add a test in `BT/audit/apiaccess/EventRegistryCleanupTest.java` that completed publications do not accumulate after many requests

**Checkpoint**: all four stories work; audit shows where app actions came from, including every read.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T083 [P] Add the build checks from quickstart.md in `mobile/scripts/check-manifest.mjs`: fail if the merged Android manifest contains `ACCESS_BACKGROUND_LOCATION` or `allowBackup` is not `false`, and call it from the CI job in T007
- [X] T084 [P] Add the Maestro smoke flow in `mobile/e2e/smoke.yaml` (sign in → menu → Profile → Logout) and document how to run it in `mobile/README.md`
- [X] T085 [P] Write the contract tests for the mobile API client in `MOBT/api/contract.test.ts` against a mock server built from `specs/018-android-app-foundation/contracts/mobile-api.md` (login and OTP body credential, renew, 426, 403 `WEB_ONLY_ROLE`, `app-config`, sessions fields)
- [ ] T086 [P] Run and record the manual pass from quickstart.md scenarios 1-17 on the emulator and one physical phone, including the TalkBack pass and the SC-001 and SC-002 timings, in `specs/018-android-app-foundation/quickstart-results.md`
- [X] T087 Run the full backend suite and verification (`mvn test`, ArchUnit and Spring Modulith) and confirm no earlier spec's tests regress, in particular the web login, renew, sessions and audit tests
- [X] T088 Update `docs/spec-roadmap.md` row 018 to Implemented with the test counts once T086 and T087 are done, and note the follow-ups (`019-mobile-attendance`, the System-only heat map spec, moving the three `hls.mobile.*` settings into spec 011)
- [X] T089 Record the open items for the team in `specs/018-android-app-foundation/quickstart-results.md`. **Blockers for a production release** (not for development): the retention period for API Access and other location data, and HLS's approved privacy-notice wording for the permission explanation (FR-019). Also open: distribution channel, app name and icon, and whether to add Play Integrity attestation (research §14)

---

## Dependencies & Execution Order

**Phase order**: Setup → Foundational → US1 → US2 → US3 → US4 → Polish. US1 is the MVP.

**Story dependencies**:
- **US1** needs Foundational only.
- **US2** needs US1 (a session must exist to end it); its server work (T038, T041) can start after Foundational.
- **US3** needs US1 (sign-in) and uses the shell from T037; it does not need US2.
- **US4** backend work (T062-T066, T071-T077) needs only Foundational, so it can run in parallel with US1-US3. The mobile part (T078-T082) needs the HTTP client (T018) and, for the consent screen, the Profile screen from US3 (T058).

**Within a story**: tests first and failing, then backend entities and services, then controllers, then mobile screens.

**Key task ordering**:
- T008 (migration) before T027, T041 and T073.
- T009 → T010 → T011 → T012 before any other backend task that reads `ClientContextHolder`.
- T071/T072 (events) before T073 (audit persistence); T073 before T074 and T075.
- T093 before T092 and T094; T095 before T096; T094 and T095 before T091 can pass; T092 needs T011.
- T017/T018 before every mobile story task.

## Parallel Opportunities

- Setup: T003-T007 in parallel after T001-T002.
- Foundational: T009 with T017, T019; T013-T015 in parallel after T011-T012.
- US1: T021-T026 (all tests) in parallel; T031, T035, T036 in parallel.
- US2: T038-T040 in parallel.
- US3: T047-T052 in parallel; T057-T059 in parallel.
- US4: T062-T070 in parallel; T077 (web) in parallel with T071-T075 (backend) and T078-T082 (mobile).
- A team of three can split: backend (Foundational, US1/US2 server, US4 server), mobile (Setup, US1-US3 mobile, US4 mobile), web (T070, T077).

### Parallel example: User Story 4

```text
Backend:  T071 → T072 → T073 → T074 → T075        (events, persistence, API, CSV)
Backend:  T093 → T092, T094 → T095                (API Access trail)
Web:      T077 (needs T074's response shape), T096 (needs T094 and T095)
Mobile:   T078 → T079 → T080 → T081 → T082        (gate, client, consent)
Tests:    T062-T070 can all be written first, in parallel
```

## Implementation Strategy

1. **MVP**: Setup, Foundational and US1. Demonstrates sign-in on a phone against the real backend,
   including the web-only role refusal and silent renewal.
2. **Increment 2**: US2 (sign-out and sessions) and US3 (menus). After these the app is a safe,
   role-aware shell that later mobile specs (such as attendance) can add screens to by extending the
   screen registry.
3. **Increment 3**: US4 (location). Backend and web parts can ship earlier than the mobile part,
   since they are inert until an Android client sends headers.
4. **Release**: Polish, the manual device pass, and the open items list. Distribution and the
   privacy-notice wording are decided by HLS and do not change the code.

## Notes

- Do not implement a heat map or any map screen; FR-026a only requires full-precision storage.
- Do not request background location anywhere; T068 and T083 guard this.
- The migration number in T008 may need to change if another migration lands first.
- Spec 008's tasks are unrelated; do not touch `backend/src/main/java/com/hls/attendance`.
