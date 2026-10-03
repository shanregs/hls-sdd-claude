# Quickstart: Validating Identity & Access

Prerequisites: PostgreSQL running (or Testcontainers for automated tests); bootstrap Admin/System
configuration set via environment variables before first startup.

## Setup

1. Start the backend against a fresh database with bootstrap config set (e.g.
   `HLS_BOOTSTRAP_ADMIN_PHONE`, `HLS_BOOTSTRAP_ADMIN_PASSWORD`, and the System equivalents).
   **Expect**: on startup, both users exist; restarting does not duplicate them or reset passwords
   (User Story 6).
2. Start the frontend. Confirm the SMS dev-stub logs OTP codes to the console/log file rather than
   sending real SMS (research.md §1), and the email dev-stub logs reset codes rather than sending
   real email (research.md §12).

## Scenario 1: Password sign-in by phone or username, any role (User Story 1)

1. Create a user holding the Manager role, with both a phone number and a username, via the
   internal user-creation capability (test-only at this stage — no UI yet).
2. Sign in with the correct phone number and password. **Expect**: signed in, landing shows the
   name and "Manager", no role-picker step ever appears.
3. Sign in with the correct username and the same password instead. **Expect**: signed in
   identically.
4. Sign in with a wrong password. **Expect**: generic "your phone number/username or password is
   incorrect," identical to signing in with an unregistered identifier.
5. Log out. **Expect**: the session ends; any further request needs signing in again.

## Scenario 2: OTP sign-in, any role (User Story 2)

1. Request a code for any active user's phone number (try a Teacher, then an Admin). **Expect**: a
   neutral confirmation and a code appears in the dev-stub SMS log for both.
2. Enter the code within 5 minutes. **Expect**: signed in with that user's roles.
3. Enter the same code again. **Expect**: refused (single-use).
4. Request 4 codes within one minute for the same number. **Expect**: the 4th is refused (429),
   with no code logged for it.

## Scenario 3: Session renewal and reuse detection (User Story 3)

1. Sign in, then wait past the 15-minute access period (or reduce it via test configuration).
   **Expect**: the app silently renews without prompting for credentials.
2. Manually replay an already-used renewal credential (e.g. via a saved cookie value from before the
   last renewal). **Expect**: 401, and the entire session chain is revoked — the legitimate,
   still-open session is also signed out on its next action.

## Scenario 4: Lockout (User Story 4)

1. Enter 5 consecutive wrong passwords for one user. **Expect**: the 6th attempt (even with the
   correct password) is refused with the unlock time shown.
2. Wait past the lockout window (or reduce it via test configuration) and sign in correctly.
   **Expect**: succeeds, and the failure count resets to 0.

## Scenario 5: Session management and password reset by phone or email (User Story 5)

1. Sign in from two different browsers as the same user. **Expect**: Profile lists both, the current
   one marked "this device."
2. End the other session from Profile. **Expect**: that browser is signed out on its next action.
3. Request a password reset for a user with no registered email. **Expect**: only the SMS option is
   offered; enter the code, set a new password (≥10 chars, not the phone number); sign-in with the
   old password fails, the new one works, and every prior session was ended by the reset.
4. Repeat for a user with a registered email, choosing the email option this time. **Expect**: both
   SMS and email are offered; the code arrives in the dev-stub email log; completing the reset works
   identically.

## Scenario 6: Deactivation (User Story 6)

1. Deactivate a currently signed-in user via the internal capability. **Expect**: their next request
   is refused, and no further sign-in (by either method) succeeds for that user.

## Automated verification

- Backend: `./mvnw test` — Testcontainers-backed tests per user story (lockout and renewal timing
  via a settable `Clock`, reuse-detection cascade, rate-limit boundaries, OTP resend/lockout
  boundaries, bootstrap idempotency, ArchUnit module boundary).
- Frontend: `npm run test` — Vitest/Testing Library flows for sign-in, OTP entry, password reset,
  session list, plus the axe-core accessibility check (SC-008) on all five screens (SignInPage,
  OtpEntryPage, ForgotPasswordPage, LandingPlaceholder, ProfilePage) in both themes.
