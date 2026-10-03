# Phase 0 Research: Identity & Access

Spec.md left one explicit open item ("the real [SMS] gateway is selected during planning") and
several technical questions unstated. All are resolved here rather than left to
implementation-time guesswork.

## 1. SMS gateway for Teacher OTP

- **Decision**: MSG91, accessed only through an `SmsGateway` interface; a logging dev-stub
  implementation is the default, and `Msg91SmsGateway` (research.md §15) is the real implementation,
  selected via `hls.sms.provider=msg91`.
- **Rationale**: MSG91 is a widely used India-focused SMS API with straightforward REST
  integration and standard DLT (Distributed Ledger Technology, India's mandatory SMS-template
  registration regime) support — a real operational requirement for sending OTPs to Indian numbers,
  independent of which vendor is chosen. The interface boundary means the vendor is swappable
  without touching `OtpService` or any test that mocks `SmsGateway`.
- **Alternatives considered**: Twilio (rejected — no DLT support out of the box for India routes,
  historically less reliable for Indian carrier delivery without extra configuration); Exotel /
  Kaleyra (reasonable alternatives — not chosen only because MSG91's documentation and pricing are
  the most straightforward for a first integration; the interface makes switching low-cost later).
- **2026-09-23 pricing re-check** (confirming the decision, not changing it): India OTP SMS in 2026
  runs roughly MetaReach ₹0.12, **MSG91 ₹0.15**, Gupshup ₹0.17, Exotel/Kaleyra ₹0.18, Twilio ~₹0.45
  (Twilio bills in USD and adds a 2-3% forex margin on top — a real, recurring cost with zero service
  value, not just a sticker-price difference). MSG91 remains the pick over the marginally cheaper
  MetaReach: OTP delivery is security-critical, and MSG91's proven DLT tooling and support maturity
  are worth more than ₹0.03/SMS. Source: messagecentral.com/blog/sms-otp-pricing-india (2026).

## 2. Password hashing

- **Decision**: BCrypt via Spring Security's `BCryptPasswordEncoder`.
- **Rationale**: it is Spring Security's supported default, requires no extra dependency, and is
  well-understood and audited. FR-017's "not stored in recoverable form" is satisfied by any modern
  adaptive hash; BCrypt keeps the implementation and its review simple.
- **Alternatives considered**: Argon2 (rejected — marginally stronger under some threat models, but
  adds a dependency and configuration surface with no concrete requirement in spec.md or the
  constitution driving the extra complexity).

## 3. Token storage on the client

- **Decision**: the short-lived access token (JWT) is held in memory (a React context value, never
  written to `localStorage`/`sessionStorage`); the rotating renewal credential is set by the backend
  as an `HttpOnly`, `Secure`, `SameSite=Strict` cookie, invisible to JavaScript.
- **Rationale**: this is the standard mitigation against a renewal credential being stolen via XSS —
  the credential that matters most (it grants 14 days of access) is never reachable by frontend
  script. Losing the in-memory access token on a hard page reload is expected and cheap: the app
  immediately calls `/auth/renew`, which succeeds using the `HttpOnly` cookie without the user
  noticing.
- **Alternatives considered**: storing both tokens in `localStorage` (rejected — exposes the
  long-lived renewal credential to any successful XSS, which Constitution Principle X's "fail
  closed" posture argues against); storing both in cookies (rejected — would require CSRF protection
  for the access token on every request, adding complexity the in-memory approach avoids).

## 4. Rate limiting implementation

- **Decision**: `bucket4j` with an in-memory bucket keyed by normalized phone number (already a
  dependency in `backend/pom.xml`), enforcing 3 OTP requests/minute (FR-007).
- **Rationale**: in-memory is sufficient and simplest at the constitution's deployment scale (a
  single EC2/VM, under 100 users, no load balancer). A distributed store (Redis) would only matter
  once multiple backend instances exist, which is explicitly out of scope for this deployment model.
- **Alternatives considered**: a distributed rate limiter (rejected as premature — no multi-instance
  deployment exists or is planned at current scale).

## 5. Lockout state

- **Decision**: `failed_attempt_count` and `lock_until` as columns directly on `AppUser`, updated
  transactionally on each password attempt.
- **Rationale**: FR-012's rule (5 failures → 30-minute lock, reset on success) is simple enough that
  a separate lockout service/table would add indirection without benefit at this scale.
- **Alternatives considered**: a separate `LoginAttempt` audit-style table (rejected for this
  spec — the append-only login-history event, FR-019, already captures every attempt for audit
  purposes; duplicating that history into a second table just for lockout counting is redundant).

## 6. Phone number normalization

- **Decision**: a small custom normalizer (strip spaces/hyphens, strip a leading `+91` or `0`,
  validate the remaining 10 digits against India's mobile numbering pattern), not a general-purpose
  library.
- **Rationale**: the constitution and spec.md fix the scope to Indian mobile numbers only (FR-004,
  spec.md Assumptions). A general international library (e.g. `libphonenumber`) would add a sizeable
  dependency to solve a single-country rule that is a handful of lines.
- **Alternatives considered**: Google's `libphonenumber` (rejected — solves a much broader problem
  than this product will ever need, at a real dependency-size and maintenance cost).

## 7. Access/refresh token mechanics

- **Decision**: the access token is a signed JWT (via `jjwt`, already a dependency) carrying the
  user id and resolved roles, validated statelessly by Spring Security's OAuth2 Resource Server
  support on every request; the renewal credential is an opaque, randomly generated, single-use
  token, stored **hashed** in `renewal_credential`, chained to a `Session`. Presenting an
  already-used renewal credential revokes that session and every credential descended from it
  (FR-010).
- **Rationale**: this is the standard, well-understood "JWT access + opaque rotating refresh"
  pattern. Keeping the access token stateless (no DB lookup per request) keeps the hot path fast;
  storing the refresh credential hashed means a database compromise alone does not yield usable
  credentials.
- **Alternatives considered**: a fully stateless refresh scheme (rejected — reuse detection, which
  FR-010 explicitly requires, needs server-side state to recognize "this credential was already
  consumed").

## 8. Bootstrap Admin/System creation

- **Decision**: a Spring Boot `ApplicationRunner` reading bootstrap phone/password/name from
  deployment configuration (environment variables, not source-controlled files), creating the
  Admin and System users idempotently (skip if a user with that phone already exists) on startup.
- **Rationale**: directly implements FR-020's "no credentials in source control, no invented
  defaults, no duplicate creation on restart."
- **Alternatives considered**: a one-off SQL seed script run manually at deploy time (rejected — less
  reliable across environments than a self-checking startup step, and easier to forget).

## 9. Login-history publication for spec 003

- **Decision**: `LoginHistoryPublisher` both persists the event to `login_history_event` and
  publishes a Spring Modulith application event (e.g. `LoginHistoryRecorded`) that spec 003's Audit
  module will subscribe to when it ships. This spec defines and stores the event; it does not know
  spec 003 exists.
- **Rationale**: matches the constitution's modular-monolith pattern (module-to-module
  communication through public APIs/events, Principle VII) and mirrors the same
  publish-a-change-record approach spec 002 uses for permission-matrix edits — one consistent
  pattern across the codebase for "this module recorded something the Audit module will surface."
- **Alternatives considered**: letting spec 003 query `identity`'s table directly (rejected — Spring
  Modulith's boundary rules treat this as a cross-module data reach-through, exactly what
  Principle VII's public-API rule forbids).

## 10. Frontend auth state management

- **Decision**: a single React context (`useAuth`) wrapping the in-memory access token and
  login/logout/renew calls — no external state-management library.
- **Rationale**: the amount of client state here (one token, one user object) does not justify
  Redux/Zustand/etc.; React context is the simplest tool that fits, consistent with the
  ease-of-maintenance priority already applied to spec 002's stack choices.
- **Alternatives considered**: Zustand or Redux Toolkit (rejected as unnecessary weight for this
  amount of state; can be revisited if a later spec's state genuinely outgrows context).

## 11. Accessibility testing tooling

- **Decision**: axe-core, run against the sign-in, OTP-entry, forgot-password, landing-placeholder,
  and Profile screens, to satisfy FR-024/SC-008's WCAG 2.2 AA requirement. This spec introduces the
  tooling; spec 002 reuses it rather than adopting a second tool.
- **Rationale**: standard automated accessibility engine, already the plan for spec 002 — introducing
  it here (since this spec ships first) avoids two different a11y-testing setups in the same
  frontend.
- **Alternatives considered**: manual-only review (rejected — not repeatable per change).

## 12. Email gateway for password-reset delivery (Constitution v2.3.0)

- **Decision**: an `EmailGateway` interface parallel to `SmsGateway` (§1), with a logging dev-stub
  implementation. No real provider (e.g. SES, SendGrid, Postmark) is selected in this spec.
- **Rationale**: email is a reset-only, optional, second delivery channel (FR-016) — not a sign-in
  method — so it carries far less urgency than the SMS gateway choice. Deferring the real provider
  keeps this spec's dependency footprint unchanged; the interface boundary means swapping in a real
  provider later touches only `EmailGateway`'s implementation.
- **Alternatives considered**: picking a provider now (rejected — premature; no requirement forces a
  choice before a production deployment is imminent, unlike SMS which every Teacher sign-in needs
  from day one).

## 13. Resolving a password-login identifier as phone or username

- **Decision**: `PasswordAuthService.authenticate(identifier, password)` tries a normalized-phone
  lookup first (only if `PhoneNumberNormalizer.normalize(identifier)` succeeds), and if that finds no
  user — including when normalization itself failed, or a phone-shaped identifier simply isn't
  registered — it falls back to a case-insensitive username lookup on the raw identifier. Whichever
  lookup finds a user is used; if neither does, or the password doesn't match, or no password is set,
  the result is the identical generic failure (FR-008, FR-027).
- **Rationale**: a username is permitted to be all-digits (FR-025), so a "normalizes successfully →
  must be a phone, stop there" shortcut would wrongly miss a numeric username that happens to share
  the 10-digit Indian-mobile shape. Always falling through to the username lookup on a phone-lookup
  miss closes that gap without needing an explicit "identifier type" selector in the UI or API.
- **Alternatives considered**: a client-side toggle for "phone" vs. "username" (rejected — extra UI
  friction with no correctness benefit, since server-side resolution as described is unambiguous and
  correct without it).

## 14. Sending a password-reset code to both SMS and email at once

- **Decision**: `channel: "BOTH"` is a request/verify-API-level value only. `OtpService.request(...)`
  resolves the account's phone and (if present) email, generates **one** code, and persists **two**
  `OneTimeCode` rows sharing that code's hash and expiry — one per destination — sending the code via
  both gateways. `OtpService.verify(...)` checks whichever destination(s) `channel` implies and, on a
  match, marks every unused row sharing that `code_hash` and `purpose` as used
  (`OneTimeCodeRepository.findByCodeHashAndPurpose`), not just the one matched — so the code cannot
  complete a second reset from the other channel's message.
- **Rationale**: reuses the same single-use-code machinery already built for one channel, rather than
  inventing a second "reset session" concept; the two-row design keeps `OneTimeCode.destination`
  single-valued (no schema change to hold two destinations in one row) while still letting either
  message's code work. Marking siblings used together closes the "send to both, use twice" gap that a
  naive per-row-only used flag would leave open.
- **Alternatives considered**: a single row with a nullable second destination column (rejected —
  complicates every other query with an optional second field for a case only `PASSWORD_RESET`
  needs); two independent codes, one per channel (rejected — worse UX, since the user would need to
  know which message's code corresponds to which channel instead of either one simply working).
- **Fallback**: if `channel: "BOTH"` is requested for an account with no registered email, only the
  SMS row is created and sent — behaviorally identical to requesting `channel: "SMS"` outright. The
  neutral response (FR-007) is unaffected either way.

## 15. MSG91 Flow API integration (real `SmsGateway`)

- **Decision**: `Msg91SmsGateway` calls MSG91's Flow API, `POST
  https://control.msg91.com/api/v5/flow`, with header `authkey: <MSG91_AUTH_KEY>` and body
  `{"template_id": "<MSG91_TEMPLATE_ID>", "recipients": [{"mobiles": "91<phone>", "var1":
  "<code>"}]}`. `mobiles` is the phone prefixed with India's country code, no `+`. Selected via
  `hls.sms.provider=msg91` (`@ConditionalOnProperty`); the dev-stub remains the default
  (`matchIfMissing = true`), so no test or local run needs real credentials. `MSG91_AUTH_KEY` and
  `MSG91_TEMPLATE_ID` have no defaults in `application.yml` — a `provider=msg91` deployment missing
  either fails fast at startup rather than silently sending with blank credentials.
- **Rationale**: the Flow API is MSG91's current DLT-compliant, variable-templated send path — a
  fixed message string cannot be sent to Indian numbers at all without going through an
  operator-approved template, so this is the only viable API for OTP delivery, not a preference among
  equals. `RestClient` (already available via `spring-boot-starter-web`, no new dependency) keeps the
  integration to one small class.
- **Required operational setup** (not done by this spec — a human, dashboard-side prerequisite): an
  MSG91 account, an API auth key, a DLT-approved Flow template with exactly one variable (`var1`)
  holding the OTP, e.g. "Your HLS verification code is {{var1}}. Valid for 5 minutes." DLT template
  approval is a multi-day process with India's telecom operators; this cannot be shortened by code.
- **Alternatives considered**: MSG91's separate OTP-specific API (`/api/v5/otp`, which generates and
  verifies the code on MSG91's own side) — rejected, because it would move code generation, hashing,
  expiry, and single-use enforcement out of `OtpService` entirely, conflicting with this spec's
  design (FR-006, data-model.md's `OneTimeCode`) and with the "BOTH" channel feature (research.md
  §14), which needs one code shared across two independently-tracked rows that only `OtpService` owns.

## 16. Surfacing SMS/email gateway failures without leaking registration status

- **Decision**: `OtpService.request(...)` catches `SmsDeliveryException`/`EmailDeliveryException`
  per destination; if every resolved destination failed, it refunds the rate-limit token
  (`Bucket.addTokens(1)`) and returns `RequestOutcome.DELIVERY_FAILED`, which `OtpController` maps to
  HTTP 503 with a plain "couldn't send the code, try again shortly" message — not the neutral FR-007
  response. If at least one destination succeeded (e.g. `BOTH` with a working SMS route but a
  down email provider), it still reports success overall.
- **Rationale**: spec.md's edge case explicitly calls for an honest failure message here, not the
  neutral one — and this is safe because the gateway being down affects every eligible destination
  identically, not this particular account, so it reveals nothing account-specific (unlike the
  registration-status leaks FR-007 exists to prevent). Refunding the rate-limit token matches the
  edge case's "does not count toward the rate limit as a delivered code."
- **Alternatives considered**: always returning the neutral response even on gateway failure
  (rejected — spec.md's edge case already calls for the honest message, and silently discarding a
  real infrastructure failure would leave users stuck retrying against a rate limit for no reason).

## 17. OTP request throttling: resend cooldown and consecutive-request lockout (FR-028/FR-029)

- **Decision**: two new guards run in `OtpService.request(...)` *before* the existing FR-007
  bucket4j per-minute check: (1) a resend cooldown — reject if a request for the same key arrives
  before `otp_policy_settings.resend_cooldown_seconds` has elapsed since that key's last request;
  (2) a consecutive-request lockout — track a per-key consecutive-request count, and once a request
  would exceed `otp_policy_settings.max_consecutive_requests`, reject it and lock the key for
  `otp_policy_settings.consecutive_request_lockout_hours`. Both checks share one key format,
  `purpose:channel:destination-as-submitted` (the same identity FR-007's bucket already keys on), so
  cooldown/lockout state is scoped per sign-in vs. per-reset attempt independently. A successful
  `OtpService.verify(...)` clears the consecutive-request state for that key immediately
  (`OtpRequestThrottle.resetOnSuccessfulVerification`), separate from `wrong_attempt_count` on
  `OneTimeCode` (FR-029 counts *requests for a new code*, not wrong guesses against an issued one —
  those remain two independent counters, per spec.md's edge case distinguishing them).
- **Thresholds are DB-backed, state is not**: `OtpPolicySettings` (data-model.md) is a single-row
  table read fresh from `OtpPolicySettingsRepository` on every `request()` call — no caching, since
  one extra indexed PK read per OTP request is negligible at this deployment's scale (constitution:
  under 100 users). The transient per-key counters (last-request time, consecutive count,
  lock-until) live only in `OtpRequestThrottle`'s in-memory `ConcurrentHashMap`, exactly like the
  existing bucket4j buckets (research.md §4) — consistent with the constitution's single-instance
  deployment assumption. A restart resets every key's throttle state to zero, which is the safe
  direction to fail in (worst case: an attacker gets a fresh window, not a legitimate user stuck
  locked out past their window).
- **Ordering rationale**: the cooldown/lockout checks run before the FR-007 bucket check (not after)
  because they are the *tighter* constraint in the common case (30s cooldown vs. a 1-request bucket
  refill) — checking them first avoids consuming a bucket4j token on a request that was going to be
  rejected anyway, and keeps the 503-worthy "every destination failed" accounting in research.md §16
  clean (a throttled request never reaches the gateway at all, so it never needs a token refund).
- **Why the pre-existing hardcoded OTP constants were left alone**: `CODE_TTL_MINUTES` (5),
  `MAX_WRONG_ATTEMPTS` (5, wrong-guess limit on an issued code), and `MAX_REQUESTS_PER_MINUTE` (3,
  the FR-007 bucket) remain fixed deployment config, unchanged by this feature. The user's request
  was explicitly for three new values (resend cooldown, consecutive-request limit, lockout duration)
  to be DB-configurable; migrating the pre-existing constants too was out of scope and would have
  been unrequested scope expansion.
- **Alternatives considered**: storing the transient counters in the database alongside the
  thresholds (rejected — turns every OTP request into a write-heavy hot row with no benefit at this
  scale, and conflicts with the existing in-memory bucket4j precedent for the exact same kind of
  per-destination throttling state); a single combined "requests per N hours" counter instead of a
  separate cooldown+lockout pair (rejected — doesn't satisfy the user's explicit two-tier
  requirement: a short resend wait *and* a much longer lockout after repeated abuse).
