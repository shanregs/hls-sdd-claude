# 018 Android App Foundation: `/speckit-specify` input

2026-10-04. Roadmap row 018. Depends on 001 (identity), 002 (access model and navigation), 003 (audit)
and 008 (attendance, for the shared scope rules). Constitution v2.3.0: React Native, Android-first.

## Feature description (paste as the argument to `/speckit-specify`)

018-android-app-foundation: The first release of the HLS Android app for Teachers, Managers and the
Director. It is another client of the existing APIs and follows the same roles, permission matrix and
scope rules as the web app; it adds no new business rules. Initial scope is limited to:

1. **Login.** Sign in with the same methods as the web app (phone or username with password, or
   one-time code; Teacher OTP login). Roles are detected automatically, with no manual role selection.
   Lockout, password reset by OTP and the short-lived session with rotating refresh and reuse
   detection behave exactly as in spec 001. Tokens are kept in secure device storage. When a refresh
   fails or reuse is detected, the user is signed out and returned to Login.
2. **Logout.** Ends the session on the server and clears all tokens and cached user data on the
   device. Users can also see and end their other sessions, as in spec 001.
3. **Main menus.** After login the app shows the signed-in user's role-based home and main menu,
   built only from the server-provided navigation model (spec 002): the union of the user's roles'
   menus and actions, hidden (not disabled) when not permitted. Menus are never hard-coded per role
   in the app. Menu items whose screens are not yet built in the app are not shown. Includes ACCOUNT
   (Profile, Logout) and a "not authorized" state for stale or unauthorized destinations. Light and
   dark theme.
4. **Location with API calls.** Whenever the app sends data to or receives data from the server (any
   API call), it captures the device's current location at that moment and attaches it to the
   request. Location is never collected in the background, on a timer, or when no API call is being
   made. The user is asked for foreground location permission with a clear explanation. If permission
   is denied, location services are off, or no fix is available within a short time, the API call
   still goes ahead without location and the server records "location unavailable" with the reason.
   The server stores the location on the related Login History / User Activity entry (spec 003) and
   on a new API Access entry for every request, so Admin and System can see where each action came
   from (decided in the 2026-10-04 analysis; see specs/018-android-app-foundation/spec.md). Location is used only for this audit purpose.

Out of scope: business screens (attendance, holiday calendar, leave, payslips, expenses, training),
offline capture and later sync, push notifications, photo or geo-tag evidence, iOS, and any
background or continuous location tracking.

## Points for `/speckit-clarify`

- Does the server enforce anything based on location (e.g. a geofence), or is it audit-only? The
  input assumes audit-only.
- Is location mandatory for any action (such as marking attendance in a later spec), or always
  best-effort? The input assumes best-effort everywhere.
- How long is location retained, and who may see it? Suggested: Admin and System only, with the
  same retention as the Audit entry it belongs to.
- Required precision (precise vs approximate) and the acceptable wait for a fix before the call
  proceeds without it.
- Wording and legal basis for the consent and permission prompt (staff privacy notice).
- Sessions: allowed on more than one device, and the refresh lifetime on mobile.
- Minimum supported Android version and whether the app may run on rooted devices.

## Role & Permission Impact (required section)

- No new permission keys for business modules. The app reads the 002 access model per user.
- Reuses the Audit read permission for the location column. Teacher and Manager do not see location
  on audit screens.
- Backend changes: accept an optional location (latitude, longitude, accuracy, capture time, status
  and reason when unavailable) on API requests from the mobile client; persist it on the Login
  History and User Activity entries; add an Android client identifier and app version to the same
  entries.
