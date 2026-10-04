# Quickstart: Android App Foundation

How to prove the feature works end to end. Contracts: [mobile-api.md](contracts/mobile-api.md).
Data: [data-model.md](data-model.md). Prerequisites: specs 001-005 running locally.

## Prerequisites

- Backend and Postgres running (`docker-compose.yml`), dev seed users present (Teacher, Manager,
  Director, Admin, System, and one user with Admin plus Teacher).
- Web frontend running, for the audit screens.
- Android emulator (Android 10 or later, Google APIs image) or a physical phone with USB debugging.
- Node (version per `mobile/package.json`), and the Expo CLI via `npx expo`.
- In `mobile/.env`: `EXPO_PUBLIC_API_BASE_URL` set to the backend as the emulator sees it
  (`http://10.0.2.2:8080` for the standard emulator).

## Run

```powershell
cd mobile
npm install
npx expo run:android      # development build on the emulator/phone
npm test                  # Jest and React Native Testing Library
npm run lint
```

Backend tests for the server side:

```powershell
cd backend
mvn -q test -Dtest='ClientContext*,MobileAuth*,AuditLocation*,ApiAccess*,AppConfig*,SessionClientType*'
```

## Validation scenarios

Set the emulator location first (Extended controls → Location) so there is a position to capture.

1. **Sign in with OTP (US1)**: sign in as the Teacher with a one-time code. Expect the Teacher home,
   no role chooser. Kill and reopen the app: still signed in, no credentials asked.
2. **Sign in with password (US1)**: as the Manager with phone and password; then username and
   password. Wrong password shows the same message as the web and does not name the wrong part.
   Five wrong passwords show the lockout message with the time.
3. **Web-only roles (US1, FR-003)**: sign in as the Admin-only user. Expect the "uses the web
   application" message and no session in `GET /api/v1/me/sessions` for that user. Sign in as the
   Admin plus Teacher user: allowed, and the menu shows only items the app has screens for.
4. **Password reset (US1)**: Forgot password → code → new password → sign in with it.
5. **Menus (US3)**: sign in as Teacher, Manager and Director in turn. Each menu equals the
   `GET /api/v1/me/access-model` navigation filtered to `/dashboard`, `/account/profile`. On the web,
   as Admin, remove a grant for a role; return the app from background after 5 minutes (or restart
   it): the menu follows with no new build. Open a deep link to a route not in the user's navigation:
   "not authorized" with a way home.
6. **Logout (US2)**: Logout, then press back and reopen: Sign In, no previous name or menu. With the
   emulator in airplane mode, Logout still returns to Sign In; the server session ends once the app
   can reach it, or expires.
7. **Sessions (US2)**: sign in on two devices (emulator plus a browser). In the app open Profile →
   Signed-in devices: both listed, current marked, browser one marked Web. End the browser session
   from the app and confirm the browser is signed out on its next request. End the app's session
   from the web and confirm the app returns to Sign In with an explanation.
8. **Renewal reuse (US2)**: with a proxy or test hook, replay an old renewal credential. Expect the
   app to return to Sign In and the session chain revoked (a `SESSION_REVOKED_REUSE` login-history
   entry).
9. **Location allowed (US4)**: allow location while in use, sign in, open ACCOUNT. On the web as
   Admin open AUDIT → Login History, filter `source=ANDROID`: the sign-in row shows coordinates,
   accuracy, capture time and app version. Do a profile edit in the app: the matching User Activity
   row shows a location too.
10. **Location denied or off (US4)**: deny permission, then separately turn location services off.
    Sign-in and every call still work, with no extra wait beyond the configured limit; the rows show
    `PERMISSION_DENIED` and `SERVICES_OFF`. Set the emulator to produce no fix to see `NO_FIX`.
11. **No background location (US4, SC-007)**: leave the app idle and then in the background for 10
    minutes. No location reading in the device's location-use log, and no new audit rows.
12. **API Access trail (US4, FR-023a)**: open a screen that only reads data (Home). As Admin open
    AUDIT → API Access filtered to that user and day. Expect one row per request (access-model,
    profile, and so on), each with method, route pattern (no query string or ids), status and
    location. A failed sign-in has a row with no user. There is no row for
    `GET /api/v1/mobile/app-config` or for web requests, and nothing stored from request or
    response bodies.
12b. **Who can see it (US4)**: as Teacher, Manager and Director on web and app, no screen or API shows
    location (`GET /api/v1/audit/*`, including `api-access`, answers 403). As System and Admin it is
    visible.
13. **Invalid location (US4)**: send a request with `lat=999` or a `ts` ten minutes off. The request
    succeeds; the row shows `INVALID` with no coordinates.
14. **Rooted device (FR-028a)**: on a rooted or test-flagged emulator the sign-in row shows
    `device_rooted = true` and sign-in is not blocked.
15. **Version gate (FR-029)**: set `hls.mobile.min-app-version` above the app's version and
    restart the backend. The app shows "Please update" at Sign In and cannot continue; web is
    unaffected.
16. **No connection**: with airplane mode on, open the app signed in: "No connection" and Retry, no
    menu. Turn it off and Retry: the home loads.
17. **Theme and accessibility (FR-016, SC-010)**: switch device dark mode and the in-app override on
    Sign In, Home, ACCOUNT, Sessions and Not authorized. Run the TalkBack pass: every control is
    announced with a label, focus order is sensible, touch targets are at least 48 dp, contrast
    meets AA in both themes.

## Build checks (also run in CI)

- The merged Android manifest has no `ACCESS_BACKGROUND_LOCATION`, `allowBackup` is `false`.
- No source file in `mobile/` hard-codes a role name to decide a menu (a lint rule or test scans for
  `TEACHER`, `MANAGER`, `DIRECTOR` outside the sign-in eligibility message handling).
- ArchUnit and Spring Modulith verification pass (backend).

## Expected outcomes

Matches SC-001 to SC-010 in `spec.md`; timings in SC-001 and SC-002 are measured on the emulator and
on one physical mid-range phone.
