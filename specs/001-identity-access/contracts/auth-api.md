# API Contract: Identity & Access

Per FR-001, everything not listed here as public MUST refuse an unauthenticated caller (fail
closed). All responses avoid revealing whether a phone number is registered (FR-008).

## Public endpoints (no session required)

### POST /api/v1/auth/login

Password sign-in, any role (Constitution v2.3.0). `identifier` is either a phone number or a
username (FR-027) — the caller does not say which.

**Request**: `{ "identifier": "9876543210", "password": "..." }` or
`{ "identifier": "priya.manager", "password": "..." }`

**Response 200**: sets the `HttpOnly`/`Secure`/`SameSite=Strict` renewal-credential cookie
(research.md §3) and returns the short-lived access token plus the signed-in identity:

```json
{
  "accessToken": "eyJ...",
  "expiresInSeconds": 900,
  "user": { "id": "...", "displayName": "...", "roles": ["MANAGER"] }
}
```

**Response 401**: generic `{ "message": "Your phone number/username or password is incorrect." }`
for a wrong password, an unregistered identifier, or an account with no password set (FR-008, edge
cases) — identical in every case.

**Response 423 (Locked)**: `{ "message": "Account locked until 14:32.", "unlockAt": "..." }`
(FR-012).

---

### POST /api/v1/auth/otp/request

Request a sign-in code (any role) or a password-reset code (delivered by SMS, email, or both at
once — caller's choice).

**Request** (`purpose: "SIGN_IN"`): `{ "destination": "9876543210", "channel": "SMS", "purpose":
"SIGN_IN" }` — `destination` is the phone number to text directly. `channel` MUST be `"SMS"` (OTP
sign-in is SMS-only, research.md §13).

**Request** (`purpose: "PASSWORD_RESET"`): `{ "destination": "priya.manager", "channel": "EMAIL",
"purpose": "PASSWORD_RESET" }` — here `destination` is a **phone-or-username identifier**, not the
raw email/phone. The server resolves it to the account and sends to that account's actual phone or
email, matching the chosen `channel`. `channel` may also be `"BOTH"`, in which case the identical
code is sent to the account's phone (SMS) and registered email in the same request; if no email is
on file, this silently behaves as SMS-only (research.md §14). The caller never needs to already know
the phone/email value (research.md §13, extended to password reset) — this also keeps the neutral
response meaningful: a client cannot distinguish "identifier not found" from "found but that channel
isn't registered."

**Response 200** (always, whether or not the identifier/phone is eligible for that channel — FR-007's
neutral response): `{ "message": "If this is registered, a code has been sent." }`

**Response 429**: `{ "message": "Too many requests, try again shortly." }` after 3 requests/minute
for that `destination` value as submitted (FR-007). No SMS/email is sent for a 429.

**Response 429** (`RESEND_TOO_SOON`, FR-028): `{ "message": "Please wait N seconds before
requesting another code." }` with a `Retry-After: N` header (seconds), when a new request for the
same `purpose`+`channel`+`destination` key arrives before the configured resend cooldown (default
30s, `otp_policy_settings.resend_cooldown_seconds`) has elapsed since the previous request. Checked
before the FR-007 per-minute bucket (research.md §17). No SMS/email is sent.

**Response 429** (`TOO_MANY_CONSECUTIVE_REQUESTS`, FR-029): `{ "message": "Too many requests.
Please try again in about N hour(s)." }` with a `Retry-After: <seconds>` header, when a request
would be the 6th consecutive request for that key without an intervening successful verification
(default limit 5, `otp_policy_settings.max_consecutive_requests`). The key is then locked for the
configured duration (default 4h, `otp_policy_settings.consecutive_request_lockout_hours`); every
request against a locked key returns this same response until the lock expires or a successful
`POST /api/v1/auth/otp/verify` against a *different, still-valid* code resets the count immediately
(research.md §17). No SMS/email is sent.

**Response 503**: `{ "message": "We couldn't send the code right now. Please try again shortly." }`
if every resolved destination's gateway send failed (research.md §16) — this is the one
non-neutral response, safe because a gateway outage affects every eligible destination identically,
not this specific account. The failed attempt does not count toward the rate limit.

---

### POST /api/v1/auth/otp/verify

**Request**: `{ "destination": "9876543210", "code": "123456", "purpose": "SIGN_IN" }` for sign-in
(SMS number, `channel` omitted/ignored), or `{ "destination": "priya.manager", "code": "123456",
"purpose": "PASSWORD_RESET", "channel": "EMAIL" }` (or `"BOTH"`) for reset — `destination` and
`channel` MUST match what was passed to `/otp/request` so the same account/destination(s) resolve
again.

**Response 200** (purpose `SIGN_IN`): same shape as `/auth/login`'s 200.

**Response 200** (purpose `PASSWORD_RESET`): a short-lived reset token used by
`/auth/password-reset/complete`. When `channel: "BOTH"` was used to request the code, entering it
from either the SMS or the email message succeeds here, and successfully verifying invalidates the
code on **both** channels — it cannot then be used a second time from the other message (FR-016).

**Response 401**: `{ "message": "Code expired, request a new one." }` or a generic invalid-code
message after 5 wrong entries invalidates the code (edge cases).

---

### GET /api/v1/auth/password-reset/channels?identifier=...

Returns which delivery channels are available for a given phone/username, without revealing
whether the identifier is registered (FR-016): `{ "channels": ["SMS"] }` or
`{ "channels": ["SMS", "EMAIL"] }` for any input, resolving to `["SMS"]` when the identifier is
unregistered or has no email on file, so the UI can decide which choice(s) to render.

---

### POST /api/v1/auth/password-reset/complete

**Request**: `{ "resetToken": "...", "newPassword": "..." }`

**Response 200**: password updated; per FR-016, all of the user's sessions are ended and any
lockout is cleared.

**Response 400**: password does not meet policy (≥10 characters, not equal to the phone number,
FR-017).

---

### POST /api/v1/auth/renew

Reads the `HttpOnly` renewal-credential cookie (no request body). Rotates it (research.md §7).

**Response 200**: new access token, same shape as `/auth/login`'s 200; sets a new renewal-credential
cookie.

**Response 401**: renewal credential missing, expired (>14 days since sign-in), or already used —
the latter case revokes the whole session chain server-side (FR-010) before responding.

---

## Authenticated endpoints (require a valid access token)

### POST /api/v1/auth/logout

Ends the current session immediately (FR-014). **Response 204**.

### GET /api/v1/me/sessions

Lists the caller's own active sessions only (FR-015): device description, sign-in time, last
activity, and which one is "this device."

### DELETE /api/v1/me/sessions/{sessionId}

Ends one of the caller's own sessions (FR-015). **Response 403** if `sessionId` does not belong to
the caller — a user can never end another user's session.

---

## Internal-only (no HTTP endpoint; FR-021)

`UserAdminService.createUser(...)` / `.deactivateUser(...)` — used by the bootstrap initializer
(research.md §8) and by automated tests. Spec 004 is what exposes this as a screen/endpoint pair;
this spec deliberately does not.

## Added by spec 018 (Android app foundation)

- **New public endpoint** `GET /api/v1/mobile/app-config`: non-sensitive configuration the Android
  app needs before sign-in (minimum app version, location wait and reuse seconds). It is an addition
  to the public list above and to spec.md FR-001's list of public capabilities; everything else still
  requires authentication.
- When a request carries `X-HLS-Client: android/<version>`, `login` and `otp/verify` (SIGN_IN)
  return the renewal credential in the JSON body as `renewalCredential` and set no cookie, and
  `renew` reads it from the body (`{ "renewalCredential": "..." }`). Requests without the header
  behave exactly as described above. See specs/018-android-app-foundation/contracts/mobile-api.md.
- For the Android client, `login` and `otp/verify` answer `403 { "code": "WEB_ONLY_ROLE" }` after
  valid credentials when the user holds none of Teacher, Manager or Director. `GET /api/v1/me/sessions`
  items gain `clientType` and `appVersion`.
