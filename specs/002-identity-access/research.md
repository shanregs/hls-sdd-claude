# Research: Identity & Access

No `[NEEDS CLARIFICATION]` markers remain in Technical Context (the `/speckit-clarify` pass resolved the material ambiguities — Accounts Officer's scope, live vs. duplicated Manager-scope data, and fail-open/closed behavior). This phase resolves the technical *how* for what's left.

## 1. JWT issuance and validation

**Decision**: Use Spring Boot's `spring-boot-starter-oauth2-resource-server` for validating incoming access tokens (standard JWT/JWK support), paired with a small in-house `TokenService` for *issuing* tokens — Spring Security's resource-server starter is a validator, not an issuer, so signing still needs explicit code. Sign with a symmetric HS256 key for v1 (single application instance, no need for asymmetric key distribution to a separate resource server).

**Rationale**: Matches the constitution's "JWT (short-lived access token, rotating refresh token)" requirement (Principle VIII) with the smallest dependency footprint — no separate authorization-server product needed at this scale.

**Alternatives considered**: A dedicated OAuth2 authorization server (e.g., Spring Authorization Server): rejected as heavyweight for a single-app, <100-user system with no third-party clients to federate.

## 2. Password hashing

**Decision**: `BCryptPasswordEncoder` from `spring-security-crypto`, work factor 12 (current reasonable default).

**Rationale**: Requirements §12 names BCrypt/Argon2 explicitly; BCrypt is bundled with Spring Security (no extra dependency) and is the more battle-tested default at this scale.

**Alternatives considered**: Argon2 (also named in Requirements §12): rejected only for v1 — no external library needed to add today; revisit if a future security review asks for it specifically.

## 3. OTP generation, storage, and delivery

**Decision**: A 6-digit numeric OTP, stored **hashed** (BCrypt, same as passwords) in a new `OtpChallenge` row with a short TTL (e.g., 5 minutes) and a single-use flag, generated behind an `OtpSender` interface. A dev/test implementation logs the code (never exposes it in an API response) instead of calling a real gateway; the real India SMS gateway integration is Requirements §12's own named out-of-scope vendor decision.

**Rationale**: Storing OTPs hashed (not plaintext) applies the same "don't persist secrets in the clear" discipline as passwords, at negligible extra cost. The `OtpSender` interface mirrors the pattern already established for `CurrentUserResolver` in spec 003 — isolate the one piece that's genuinely a separate decision behind an interface with one swap point.

**Alternatives considered**: Storing the OTP in the JWT/session state instead of a DB row: rejected — OTP requests must be rate-limited and looked up by phone number before a session exists, so a dedicated row is the natural fit.

## 4. Rate-limiting OTP requests (FR-009)

**Decision**: Bucket4j, in-memory, keyed by phone number, applied only to the OTP-request endpoint.

**Rationale**: Requirements §12 names Bucket4j directly; an in-memory limiter is sufficient because the constitution's own deployment target is a single EC2 instance for the foreseeable scale — no distributed limiter (e.g., Redis-backed) is needed until a second instance exists, which the constitution says not to add prematurely.

**Alternatives considered**: A DB-backed counter table: rejected as unnecessary complexity for a limiter that only needs to survive within one running instance's uptime.

## 5. Session and refresh-token mechanics (FR-011, FR-012, SC-005)

**Decision**: Each login creates a `Session` row (channel, issued/last-active timestamps, a hash of the current refresh token, revoked flag). Access-token validation is signature+expiry only (fast, stateless), but every request that needs session-level state (refresh, revoke-check) looks up the `Session` row by its embedded session id claim. Revoking a session sets `revoked = true` immediately; the next refresh or session-scoped check against that id fails from that moment, satisfying SC-005's 60-second bound without waiting for the short-lived access token to expire on its own.

**Rationale**: Pure stateless JWT (no session lookup at all) can't satisfy FR-012/SC-005's "revoke and it takes effect quickly" requirement, since a still-valid, still-signed access token would keep working until it naturally expires. A DB-backed session row closes that gap while keeping access-token validation itself fast.

**Alternatives considered**: A distributed token-revocation list (e.g., Redis-backed blocklist): rejected for the same single-instance-scale reason as decision 4 — the `Session` table already lives in the same PostgreSQL instance the rest of the system uses.

## 6. Manager-scope live query against Organization (FR-004, FR-014, FR-019)

**Decision**: `ManagerScopeGuard` calls `organization.api.AccountabilityQueries` in-process (same JVM, same modular monolith — no network hop today) but wraps the call with an explicit timeout budget and catches both a thrown exception and a timeout as "scope check failed," in both cases denying the request (fail-closed, per Clarifications session 2026-09-21). The bounded-timeout wrapper is kept even though the call is in-process today, so the contract doesn't silently change if Organization is ever extracted into its own service later — which the constitution explicitly allows as a future, not a current, possibility.

**Rationale**: Writing the fail-closed contract against "the call can fail or be slow" rather than "the call can only throw a same-process exception" keeps FR-019 correct regardless of how Organization is deployed later.

**Alternatives considered**: Treating any call into Organization as always-succeeds (no timeout wrapper) since it's in-process today: rejected — it would make FR-019's "unreachable or timed out" language untestable today (nothing to simulate) and wrong tomorrow if deployment changes.

## 7. Frontend token storage

**Decision**: The access token is held only in React application memory (never written to `localStorage`/`sessionStorage`); the refresh token is set by the backend as an `httpOnly`, `Secure`, `SameSite=Strict` cookie and is never read by JavaScript at all. Silent renewal (FR-011) is a background call the frontend makes when the in-memory access token is close to expiry; the browser attaches the httpOnly cookie automatically.

**Rationale**: Keeps the refresh token — the longer-lived, more valuable credential — out of reach of XSS, which is the standard mitigation for exactly this kind of web session model; the access token's short lifetime bounds the damage if it's ever exposed via a memory-inspection-level attack, which is a much higher bar than XSS.

**Alternatives considered**: Both tokens in `localStorage`: rejected — any XSS on the app would leak the refresh token, defeating the point of having a short-lived access token at all.

## 8. Mobile app (React Native) scope

**Finding**: No `mobile/` directory exists in the repository yet, and standing one up (Expo vs. bare React Native workflow, Android-first tooling, native module setup) is a meaningfully sized decision on its own.

**Decision**: Out of scope for this plan. FR-008 already allows Teachers to complete OTP login "from either the mobile app or the web portal" — the web portal path (extending the existing `frontend/` Vite app) fully satisfies FR-008/SC-002 without a mobile app existing yet. Bootstrapping React Native is deferred to a dedicated future decision.

**Alternatives considered**: Bootstrapping a minimal React Native shell as part of this plan just to say "mobile exists": rejected — it would add a large, mostly-unrelated setup task to a plan whose actual functional requirement is already satisfiable through the web channel.
