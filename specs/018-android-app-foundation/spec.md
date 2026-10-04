# Feature Specification: Android App Foundation

**Feature Branch**: `018-android-app-foundation`

**Created**: 2026-10-04

**Status**: Draft

**Input**: User description: "018-android-app-foundation: The first release of the HLS Android app for Teachers, Managers and the Director. It is another client of the existing APIs and follows the same roles, permission matrix and scope rules as the web app; it adds no new business rules. Initial scope is limited to: (1) Login, (2) Logout, (3) Main menus built only from the server-provided navigation model, and (4) Location captured only at the moment of an API call and attached to it, stored with the related Login History / User Activity entry for audit. Out of scope: business screens (attendance, holiday calendar, leave, payslips, expenses, training), offline capture and later sync, push notifications, photo or geo-tag evidence, iOS, and any background or continuous location tracking. Depends on 001-identity-access, 002-access-model-app-shell, 003-audit and 008-attendance. Per docs/spec-roadmap.md row 018, docs/spec-inputs/018-android-app-foundation.md and Constitution v2.3.0."

## Clarifications

### Session 2026-10-04

- Q: How long should a user stay signed in on the Android app before signing in again? → A: Same as the web app. Spec 001's session settings apply unchanged, with no mobile-specific lifetime.
- Q: How precise should the stored location be? → A: Store exactly as captured (full coordinates and accuracy). Raw location stays visible only to Admin and System on the audit screens. It is kept at full precision so a later heat map can show where actions were performed; that heat map is a later web feature, shown only to the System role, and is not built in this release.
- Q: What should a user see when they open the app with no internet? → A: A "no connection" screen with Retry. No menus or data are shown until the server is reachable; nothing cached is shown as current.
- Q: Should the app run on rooted (modified) Android phones? → A: Yes, but the app records "device appears rooted" on the Login History entry so Admin and System can see it. It never blocks sign-in or any action.
- Q: What is the oldest Android version the app must support? → A: Android 10 and later.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Sign In to the Android App (Priority: P1) 🎯 MVP

A Teacher, Manager or Director installs the app, opens it and signs in with the same methods the web
app offers: phone number or username with a password, or a one-time code sent to their phone. The
system works out their role or roles; the user never picks a role. After signing in they land on
their home screen. Closing and reopening the app keeps them signed in until their session ends.

**Why this priority**: nothing else in the app is reachable without signing in. It is also the first
point where the app proves it behaves as a client of the existing identity rules rather than a
second security model.

**Independent Test**: install the app, sign in once as a Teacher with a one-time code and once as a
Manager with a password, confirm each reaches a home screen, close and reopen the app and confirm
the user is still signed in.

**Acceptance Scenarios**:

1. **Given** a Teacher with a registered phone number, **When** they request a one-time code and
   enter the correct code, **Then** they are signed in and shown the Teacher home screen, with no
   role-selection step.
2. **Given** a Manager with a password, **When** they sign in with their phone number or username
   and the correct password, **Then** they are signed in and shown the Manager home screen.
3. **Given** a user who enters a wrong password or code, **When** they submit, **Then** the app
   shows the same non-revealing failure message as the web app and does not say which part was wrong.
4. **Given** an account locked after repeated failed password attempts, **When** the user tries to
   sign in, **Then** the app shows that the account is temporarily locked and when it can be tried
   again, matching the lockout rules of spec 001.
5. **Given** a user who forgot their password, **When** they use "Forgot password" and complete the
   one-time-code reset, **Then** they can sign in with the new password.
6. **Given** a signed-in user, **When** they close and reopen the app within their session's
   lifetime, **Then** they are still signed in and the app renews its access in the background
   without asking for credentials again.
7. **Given** a user whose roles are only Admin and/or System, **When** they sign in, **Then** the
   app tells them these roles use the web application and does not keep a session open.
8. **Given** a deactivated user, **When** they try to sign in, **Then** sign-in is refused exactly
   as on the web.

---

### User Story 2 - Sign Out and Manage Sessions (Priority: P1)

A signed-in user signs out from ACCOUNT. Signing out ends the session on the server and removes
everything stored on the device for that user. The user can also see their other signed-in devices
and browsers and end any of them. If the system ends their session, or the session can no longer be
renewed, the app returns them to Sign In.

**Why this priority**: staff will use shared or personal phones; a reliable sign-out and a safe
failure path are required before the app can be trusted with business data.

**Independent Test**: sign in, sign out, then confirm that going back or reopening the app shows
Sign In and no previous user's name, menu or data is visible. Sign in on two devices and end the
other device's session from the first.

**Acceptance Scenarios**:

1. **Given** a signed-in user, **When** they choose Logout and confirm, **Then** the session ends on
   the server, all sign-in credentials and cached user data are removed from the device, and Sign In
   is shown.
2. **Given** a user who has just signed out, **When** they press the device's back control or reopen
   the app, **Then** no signed-in screen or previous data is shown.
3. **Given** a user signed in on two devices, **When** they open their sessions list in the app,
   **Then** they see each active session with device or app description and last-used time, the
   current one marked, and can end any other one.
4. **Given** a session ended from elsewhere (another device, a deactivated account, or an
   administrator action), **When** the app next makes a request or tries to renew, **Then** the user
   is returned to Sign In with a short explanation, and device data for that user is cleared.
5. **Given** a renewal credential that was already used (a sign of possible theft), **When** the
   system detects its reuse, **Then** the whole session family is ended as in spec 001, and the app
   returns the user to Sign In.
6. **Given** the device has no network, **When** the user chooses Logout, **Then** the app still
   clears everything on the device and shows Sign In, and the server session is ended as soon as the
   app can reach it, or expires on its own.

---

### User Story 3 - See Only the Menus My Roles Allow (Priority: P1)

After sign-in the app shows a role-based home and a main menu. The menu is built entirely from the
navigation the server provides for that user (spec 002): the union of the menus and actions of every
role the user holds, with anything not permitted not shown at all. A Teacher and a Manager see
different menus without the app knowing the difference ahead of time. A menu item whose screen is not
yet available in the app is not shown. ACCOUNT always offers Profile and Logout.

**Why this priority**: it is how every later mobile spec plugs in. Getting it right here means new
screens appear by being added to the server's navigation, without redefining who sees what.

**Independent Test**: sign in as a Teacher, a Manager and a Director and confirm each sees only the
menu the server returned for them; change a role's permissions on the web and confirm the app's menu
changes on its next refresh without an app update.

**Acceptance Scenarios**:

1. **Given** a user signed in with any role, **When** the app loads their home, **Then** the menu is
   exactly the server-provided navigation for that user, filtered to items the app has a screen for.
2. **Given** a user with two roles, **When** they sign in, **Then** the menu is the union of both
   roles' items and actions, with no duplicate entries.
3. **Given** an Admin edits the permission matrix on the web, **When** an affected user next opens
   the app or returns to it after it has been in the background, **Then** the menu reflects the
   change without reinstalling the app.
4. **Given** the server navigation includes an item the app has no screen for yet, **When** the menu
   is built, **Then** that item is not shown and nothing breaks.
5. **Given** the user opens a destination they no longer have permission for (for example from an
   older screen), **When** the app resolves it, **Then** a clear "not authorized" screen is shown,
   with a way back home.
6. **Given** the user opens ACCOUNT, **When** the screen loads, **Then** they can open Profile
   (their own details, as on the web) and Logout.
7. **Given** a Teacher, Manager or Director, **When** the home screen loads, **Then** it shows a
   role-appropriate placeholder home (their name and roles, and the same skeleton widgets as the web
   dashboard) until business screens are added by later specs.
8. **Given** the device is set to light or dark mode, or the user picks one in the app, **When** any
   screen is shown, **Then** it uses that theme and remembers the choice.

---

### User Story 4 - Location Recorded With Each API Call (Priority: P2)

Every time the app sends data to the server or receives data from it, the app notes where the device
is at that moment and sends that with the request. The server keeps it with the matching Login
History or User Activity entry so an Admin or System user can see where an action came from. Nothing
is collected at any other time. The user is asked once, in plain words, to allow location while the
app is in use. If they refuse, or location is off or cannot be found quickly, the request still goes
ahead and the server records that location was unavailable and why.

**Why this priority**: it is the one new capability beyond reusing the web's rules, it carries the
privacy risk, and it is audit-only. It must never get in the way of signing in or working.

**Independent Test**: sign in with location allowed and confirm the Login History entry shows a
place; repeat with location denied and then with location services off and confirm sign-in succeeds
and the entry shows "location unavailable" with the reason. Leave the app open and idle for several
minutes and confirm no location is recorded.

**Acceptance Scenarios**:

1. **Given** a user who has allowed location, **When** they sign in, **Then** the Login History
   entry for that sign-in shows the device's coordinates, how accurate they were and when they were
   captured.
2. **Given** a signed-in user who has allowed location, **When** the app makes any API call that
   sends or receives data, **Then** the location at the moment of that call is sent with it and
   stored with the User Activity entry, if the action creates one.
3. **Given** the app is open but no API call is being made, **When** time passes, **Then** the app
   does not read the device's location and nothing is recorded; it also never reads location while in
   the background.
4. **Given** the first time location would be needed, **When** the app asks permission, **Then** it
   first explains in plain language that location is recorded only when the app talks to the server
   and is used only for audit, and the system permission prompt follows.
5. **Given** a user who denies permission, **When** they sign in or make any call, **Then** the call
   succeeds without location and the server records "location unavailable — permission denied".
6. **Given** location services are switched off on the device, or no position is found within a
   short time, **When** the app makes a call, **Then** the call is not delayed beyond that short
   wait, goes ahead without location, and the server records "location unavailable" with the reason
   (services off, or no fix in time).
7. **Given** an Admin or System user viewing Login History or User Activity on the web, **When** an
   entry came from the Android app, **Then** they can see its location (or the unavailable reason),
   the app version and that it came from the Android app.
8. **Given** a Teacher, Manager or Director, **When** they use the web app or the mobile app, **Then**
   they cannot see location data, their own or anyone else's.
9. **Given** a request that arrives with a location that is not valid (impossible values, or a
   capture time far from the server's time), **When** the server receives it, **Then** it does not
   reject the request but stores it as "location unavailable — invalid" and does not keep the
   submitted values.

---

### Edge Cases

- A user's only roles are Admin and/or System: sign-in is refused on the app with a message to use
  the web application (User Story 1, scenario 7). A user with Admin plus Teacher, Manager or Director
  is allowed, and sees the menus for their app-eligible roles only.
- The app is opened with no network: a signed-in user sees a "no connection" screen with Retry and no menu or data until the server is reachable (clarified 2026-10-04). A signed-out user sees Sign In with a clear "no connection" message. No business action is possible offline in this release.
- A request is made while a location lookup is still running: the app does not wait longer than the
  short limit and never starts a second lookup for the same call.
- Many API calls fire together (for example loading a home screen): they may share one location
  reading taken at the start of that burst, so the user's battery and the screen's speed are not
  affected.
- A rooted or modified device: sign-in and use proceed normally; the Login History entry is flagged "device appears rooted" (FR-028a).
- Mock or spoofed location on the device: the server records what was sent and cannot verify it. The
  location is for audit, not for allowing or blocking anything.
- The device clock is wrong: the capture time on a location is compared with the server time and an
  implausible difference marks the location invalid (User Story 4, scenario 9).
- A user changes the location permission in device settings while the app is running: the next API
  call follows the new setting.
- An old app version is used after a server change: the server asks the user to update when the app
  is below the minimum supported version, and does not let it continue.
- A user is signed in on the app while their role changes on the web: the next menu refresh applies it
  (User Story 3, scenario 3).
- A shared phone: sign-out leaves no data, and sign-in never pre-fills the previous user's identity
  beyond what the user chose to remember on the sign-in screen.

## Requirements *(mandatory)*

### Functional Requirements

**Sign-in and session (reuses spec 001)**

- **FR-001**: The Android app MUST let users sign in with a phone number or username and password, or
  with a one-time code sent to their phone, following the same rules, messages, rate limits, lockout
  and password-reset flow as spec 001. It MUST NOT introduce any new sign-in method.
- **FR-002**: The system MUST identify the user's role or roles at sign-in. The app MUST NOT ask the
  user to choose a role.
- **FR-003**: The app MUST allow sign-in only for users who hold at least one of Teacher, Manager or
  Director. A user with none of those roles MUST be told to use the web application and MUST NOT be
  left with an active session.
- **FR-004**: The app MUST keep the user signed in across closing and reopening, renewing short-lived
  access in the background using the renewal credential. Credentials MUST be kept only in the
  device's secure credential storage and MUST NOT be written to logs, backups or screenshots of
  the app switcher.
- **FR-005**: When renewal fails, or renewal-credential reuse is detected (spec 001, FR-010), the app
  MUST return the user to Sign In with a short explanation and clear everything stored for that
  user.
- **FR-006**: A user MUST be able to log out. Logout MUST end the session on the server and remove all
  credentials and cached user data from the device. If the server cannot be reached, the device data
  MUST still be cleared and the server session MUST be ended when it next can be.
- **FR-007**: A user MUST be able to list their active sessions from the app and end any session other
  than the current one, with the same behavior as spec 001, FR-015. Sessions from the app and from
  the web MUST appear in the same list, and each session MUST show whether it is an Android app
  session.
- **FR-008**: A deactivated user, or a user whose sessions an administrator ended, MUST lose access
  from the app exactly as on the web.

**Menus and shell (reuses spec 002)**

- **FR-009**: The app MUST build its home and main menu only from the per-user navigation and access
  model the server provides (spec 002). Menus MUST NOT be defined per role inside the app.
- **FR-010**: The menu MUST be the union of all the user's roles. Items the user may not use MUST NOT
  be shown, rather than shown disabled.
- **FR-011**: The app MUST show only the menu items for which it has a screen. Unknown or not-yet-built
  server items MUST be ignored without error.
- **FR-012**: The app MUST refresh the access model when it starts, when it returns from the
  background after a set period, and after a sign-in, so permission changes made on the web take
  effect without an app update.
- **FR-013**: The app MUST show a "not authorized" screen with a way back home when a destination is
  not in the user's navigation.
- **FR-014**: The app MUST provide an ACCOUNT section with Profile and Logout for every signed-in
  user. Profile shows the user's own details as in spec 001, and the user's sessions (FR-007) are
  reachable from it.
- **FR-015**: Each of Teacher, Manager and Director MUST get a home screen that shows the user's name,
  role or roles and skeleton widgets, as the web dashboards do in spec 002.
- **FR-016**: The app MUST support light and dark themes, follow the device setting by default, let
  the user override it, and remember the choice. The screens MUST be readable and operable on phone
  screen sizes, with accessible labels, touch-target sizes and colour contrast.
- **FR-017**: The server MUST enforce permission and data scope on every request independently of
  the app (Constitution Principle X). The app hiding or showing a menu item MUST NOT be treated as
  authorization.

**Location on API calls (new)**

- **FR-018**: The app MUST capture the device's location only at the moment it makes an API call that
  sends or receives data, and MUST send it with that call. It MUST NOT read location at any other
  time, including on a timer, while idle, or while in the background.
- **FR-019**: Before location is first needed, the app MUST explain, in plain language, that location
  is recorded only when the app communicates with the server and is used only for audit, and then
  request foreground-only location permission from the device. The explanation MUST be available
  again from ACCOUNT.
- **FR-020**: A captured location MUST consist of latitude, longitude, accuracy and the time it was
  captured. Precise location SHOULD be used where the device allows; approximate location MUST be
  accepted if the user grants only that.
- **FR-021**: If permission is denied, location services are off, or no position is found within a
  short wait, the API call MUST proceed without location and MUST NOT fail or be noticeably delayed
  because of it. The app MUST report the reason with the call: permission denied, services off, no
  fix in time, or other.
- **FR-022**: Several API calls made together MAY share one location reading taken at the start of
  that group. A reading MUST NOT be reused for calls made later than a short freshness limit after it
  was taken.
- **FR-023**: The server MUST store the location, or the unavailable reason, with the Login History
  entry for a sign-in event and with the User Activity entry for an action that creates one, and with
  no other record. The server MUST also store the app version and that the call came from the Android
  app.
- **FR-024**: The server MUST validate a received location (coordinate ranges, accuracy, and a
  capture time close to the server time). An invalid location MUST NOT reject the request; it MUST be
  stored as "location unavailable — invalid" and the submitted values discarded.
- **FR-025**: Location MUST be used only to show where an audited action came from. The system MUST NOT
  use it to allow, deny, approve or reject any action, and MUST NOT use it for any other purpose.
- **FR-026**: Location data MUST be visible on the audit screens (spec 003) only to users who have
  access to those screens (Admin and System by default). It MUST NOT be shown to Teacher, Manager or
  Director, and MUST be included in the existing audit exports under the same permission.
- **FR-026a**: Location MUST be stored at the precision captured, with no rounding or masking, so that it can later be plotted on a map. This release builds no map or heat map; a later feature will add a heat map view of where audited actions were performed, in the web app's admin area and shown only to the System role (it will bring its own permission key).
- **FR-027**: Location data MUST follow the same retention as the audit entry it belongs to, and MUST
  NOT be editable or deletable separately.
- **FR-028**: The audit screens MUST add a way to see an entry's location, its unavailable reason, the
  app version and the source (Android app or web).

**Release control**

- **FR-028a**: When the app detects that the device appears rooted or modified, it MUST report that with the sign-in, and the server MUST store it on the Login History entry and show it on that entry on the audit screens. It MUST NOT block sign-in or any other action, and, like location, it is for audit only. The detection is best-effort and not proof.
- **FR-029**: The server MUST be able to name a minimum supported app version. An app below it MUST be
  told to update and MUST NOT be allowed to continue past Sign In.

### Key Entities *(include if feature involves data)*

- **Mobile Session**: a user's sign-in from the Android app, as spec 001's session with the extra
  facts that it came from the Android app and from which app version and device description.
- **Location Capture**: the device position sent with one API call: latitude, longitude, accuracy,
  capture time, and either "available" or an unavailable reason (permission denied, services off, no
  fix in time, invalid, other). Belongs to exactly one Login History or User Activity entry.
- **Login History Entry / User Activity Entry** (spec 003): gain an optional Location Capture, the
  app version, and a source (Android app or web).
- **Navigation Model** (spec 002): the server-provided menus, actions and scope the app renders. Not
  changed by this feature.
- **Minimum App Version**: the oldest app version the server accepts.

## Role & Permission Impact *(mandatory — Constitution Principles II–IV)*

| Role     | Menu (section → item) | Default actions | Data scope |
| -------- | --------------------- | --------------- | ---------- |
| Admin    | None in the app (app sign-in is refused if Admin and/or System are the user's only roles, FR-003). On the web: AUDIT screens gain location, app version and source fields | View, Export (existing `AUDIT` permissions) | Org-wide (audit data) |
| Director | Home, ACCOUNT → Profile, Logout. Any other items come from the server navigation if the app has a screen for them (none yet) | View own profile | Own (this release adds no business screens) |
| Manager  | Home, ACCOUNT → Profile, Logout. Same rule | View own profile | Own (this release adds no business screens) |
| Teacher  | Home, ACCOUNT → Profile, Logout. Same rule | View own profile | Own |
| System   | None in the app. On the web: AUDIT screens gain location, app version and source fields | View, Export (existing `AUDIT` permissions) | Org-wide (audit data only; System still sees no business data) |

**New permission keys**: none. The app reads the existing access model from spec 002. Seeing
location data reuses the existing `AUDIT` view/export permissions from spec 003, so the matrix editor
(Admin, Director and System only, Constitution Principle II) can grant or remove it with no change to
who may edit the matrix. Manager and Teacher have no new access.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A Teacher with a registered phone can install the app, sign in with a one-time code and
  reach their home screen in under 1 minute, excluding the time the SMS takes to arrive.
- **SC-002**: A returning user who reopens the app within their session's lifetime reaches their home
  screen without entering credentials in 95% of cases, and in under 3 seconds on a typical mobile
  connection.
- **SC-003**: After Logout, 100% of tests find no credentials, name, menu or business data for that
  user recoverable from the app's screens or saved state on the device.
- **SC-004**: For Teacher, Manager and Director test users, 100% of the menu items shown match the
  server's navigation for that user, with zero items shown that the user lacks permission for, and
  zero role-specific menu definitions in the app.
- **SC-005**: A permission change made on the web appears in an affected user's app menu on its next
  refresh with no app update, in 100% of test cases.
- **SC-006**: In test runs with location allowed, 100% of Login History entries from the app carry a
  location, and 100% of audited actions made through the app carry a location or a stated reason.
- **SC-007**: With the app idle or in the background for 10 minutes, 0 location readings are taken
  and 0 are stored.
- **SC-008**: With permission denied or services off, sign-in and every other call still succeed, and
  the added delay from location handling is no more than the stated short wait, in 100% of tests.
- **SC-009**: Admin and System can find where an app sign-in came from within 3 clicks from the AUDIT
  menu, and Teacher, Manager and Director test users see no location data anywhere in 100% of tests.
- **SC-010**: All Sign In, Home, ACCOUNT, sessions and not-authorized screens pass the accessibility
  checks in both light and dark themes.

## Assumptions

- The app is for Teachers, Managers and the Director. Admin and System remain web-only, per the
  requirements document. A person who holds Admin or System plus an app role can sign in and sees the
  menus of their app roles.
- Location is audit-only and best-effort. It is never required for any action, and there is no
  geofence or other rule that depends on it. Making location mandatory for a later action, such as
  marking attendance, would be a separate decision in that later spec.
- Admin and System see location through the AUDIT screens; no other role does. Retention follows the
  audit entry it belongs to, so there is no separate purge.
- The short wait for a position and the freshness limit for sharing a reading are configurable, with
  defaults of a few seconds and under a minute. These are working values to confirm in planning.
- Users agree to the privacy notice and the permission prompt when they first use the app. The exact
  wording of the privacy notice is set by HLS and is outside this spec.
- Sessions on more than one device are allowed, as on the web. The session lengths of spec 001
  apply to the app unchanged (clarified 2026-10-04); there is no mobile-specific lifetime.
- The app supports Android 10 and later (clarified 2026-10-04). The distribution method (store listing or private distribution) is a planning decision.
- Sign-in method, rate limits and messages come from spec 001 without change, and the navigation
  model comes from spec 002 without change. The audit store is spec 003's, extended only with the new
  optional fields in this spec.
- Spec 008's attendance rules, scope and APIs are a dependency of the mobile attendance spec that
  follows this one, not of this spec's own screens. They are listed so the roadmap order stays valid.
- A heat map of action locations is planned as a later web feature, shown only to the System role (not Admin, Director, Manager or Teacher). This spec only guarantees the data is stored precisely enough for it (FR-026a).
- The following are deferred to later specs: business screens, with attendance (monthly view,
  mark present or absent, Holiday Calendar view) first; offline capture and later sync; push
  notifications; photo or geo-tag evidence; iOS. The Constitution's offline-readiness and iOS
  statements are not contradicted, only scheduled after this release.
