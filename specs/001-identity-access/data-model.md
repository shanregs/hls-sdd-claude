# Phase 1 Data Model: Identity & Access

Derived from spec.md's Key Entities section and its FRs. All six entities are persisted (unlike
spec 002, this feature has no purely-resolved/in-memory entities beyond the JWT payload itself).

## AppUser (table `app_user`)

| Field | Type | Notes |
|---|---|---|
| `id` | UUID (PK) | |
| `display_name` | text, required | |
| `phone` | text, unique, required | normalized canonical Indian mobile format (research.md §6); always usable for OTP, and for password sign-in when a password is set (FR-004) |
| `username` | text, unique, nullable | 3-30 chars, letters/digits/periods/underscores (FR-025); stored alongside a `username_lower` value for case-insensitive matching (research.md §13) |
| `email` | text, unique, nullable | password-reset delivery only, never a sign-in identifier (FR-026) |
| `password_hash` | text, nullable | BCrypt hash (research.md §2); null until a password is set — those users can still sign in by OTP (FR-006) |
| `active` | boolean, default true | deactivated users cannot sign in by any method (FR-018) |
| `linked_teacher_id` | UUID, nullable | set when the user holds the Teacher role and is linked to a Teacher record (FR-011); the `teacher` module does not exist yet, so this is an opaque UUID reference, not a foreign key, until spec 007 |
| `failed_attempt_count` | integer, default 0 | reset to 0 on successful sign-in (FR-012) |
| `lock_until` | timestamp, nullable | null when not locked; sign-in refused while `now < lock_until` even with correct password |

**Constraints**:
- `phone` UNIQUE across all users regardless of role (FR-004, edge case: "same phone number for two
  users" is rejected).
- `username_lower` (a generated/maintained lowercase copy of `username`) UNIQUE, independent of the
  `phone` uniqueness domain (FR-025, edge case: "the same username is entered for two users").
- `email` UNIQUE when present (FR-026, edge case: "the same email is entered for two users").
- A user MUST hold at least one `RoleAssignment` row (FR-003); enforced at the service layer (a user
  with zero roles is a data error, per spec.md's edge case, not a schema-level impossibility since
  the two rows are inserted in the same transaction).

## RoleAssignment (table `role_assignment`)

| Field | Type | Notes |
|---|---|---|
| `id` | UUID (PK) | |
| `user_id` | UUID (FK → `app_user`) | |
| `role` | enum: `ADMIN`, `DIRECTOR`, `MANAGER`, `TEACHER`, `SYSTEM` | fixed set (Constitution Principle II), not free text |

**Constraints**: UNIQUE on `(user_id, role)` — a user cannot hold the same role twice. A user MAY
hold several distinct roles (FR-003).

## Session (table `session`)

| Field | Type | Notes |
|---|---|---|
| `id` | UUID (PK) | |
| `user_id` | UUID (FK → `app_user`) | |
| `device_description` | text | shown in the Profile sessions list (FR-015) |
| `signed_in_at` | timestamp | |
| `last_activity_at` | timestamp | updated on each renewal |
| `status` | enum: `ACTIVE`, `ENDED`, `REVOKED` | `ENDED` = user logged out or ended it from Profile; `REVOKED` = reuse-detection or deactivation |

## RenewalCredential (table `renewal_credential`)

| Field | Type | Notes |
|---|---|---|
| `id` | UUID (PK) | |
| `session_id` | UUID (FK → `session`) | |
| `credential_hash` | text | the opaque renewal token, stored hashed (research.md §7), never in plain form |
| `issued_at` | timestamp | |
| `used_at` | timestamp, nullable | set the moment it is exchanged for a new one; a second presentation after this is set triggers FR-010's cascade revoke |
| `superseded_by` | UUID, nullable (FK → `renewal_credential`) | forms the chain "every renewal descended from it" that FR-010 revokes on reuse |

**State transition**: `issued` → `used` (exactly once, exchanged for a new row) → presenting it again
after `used_at` is set → revoke this `Session` and walk `superseded_by` forward, marking every
descendant credential's session `REVOKED` too.

## OneTimeCode (table `one_time_code`)

| Field | Type | Notes |
|---|---|---|
| `id` | UUID (PK) | |
| `channel` | enum: `SMS`, `EMAIL` | `SMS` for sign-in and SMS-based reset; `EMAIL` only for reset (FR-016) |
| `destination` | text | a normalized phone number (`SMS`) or an email address (`EMAIL`) |
| `purpose` | enum: `SIGN_IN`, `PASSWORD_RESET` | (renamed from the earlier `TEACHER_LOGIN` — sign-in OTP is no longer role-restricted, Constitution v2.3.0) |
| `code_hash` | text | never stored in plain form |
| `expires_at` | timestamp | 5 minutes from issuance (FR-006) |
| `used_at` | timestamp, nullable | single-use; set on successful verification |
| `wrong_attempt_count` | integer, default 0 | code invalidated after 5 wrong entries (edge case) |

**Constraints**: `channel = SMS` for every `purpose = SIGN_IN` row (OTP sign-in is SMS-only,
research.md §13's "OTP delivery is SMS-only" note). Rate limiting (3 requests/minute per
destination, FR-007) is enforced by the `bucket4j` bucket (research.md §4), not a database
constraint on this table.

**"Both" channels (research.md §14)**: `channel` itself is never `BOTH` at the row level — that
value exists only in the request/verify API (`OtpChannel` there has a third `BOTH` option). When a
`PASSWORD_RESET` is requested with `channel: "BOTH"`, the service creates **two** rows sharing the
same `code_hash` and `expires_at` — one `channel = SMS` (destination = phone), one `channel = EMAIL`
(destination = email) — and sends the identical code to both. Verifying successfully against either
row marks **both** rows `used_at` together (via `findByCodeHashAndPurpose`), so the code cannot then
complete a second reset from the other channel's message (FR-016).

## LoginHistoryEvent (table `login_history_event`, append-only)

| Field | Type | Notes |
|---|---|---|
| `id` | UUID (PK) | |
| `occurred_at` | timestamp | |
| `user_id` | UUID, nullable | null when the phone number matched no user (e.g. a failed sign-in to an unregistered number) |
| `phone_masked` | text | the phone number as entered, masked, per FR-019 |
| `method` | enum: `PASSWORD`, `OTP`, `RENEWAL` | |
| `event_type` | enum: `SIGN_IN_SUCCESS`, `SIGN_IN_FAILURE`, `LOCKOUT`, `LOGOUT`, `SESSION_ENDED_BY_USER`, `SESSION_REVOKED_REUSE`, `OTP_REQUESTED`, `PASSWORD_RESET` | the full set FR-019 requires |
| `outcome` | text | short human-readable result |
| `client_ip` | text | |
| `device_description` | text | |

**Constraints**: rows are never updated or deleted (append-only, Constitution Principle I).
Corrections are new rows, never edits.

## OtpPolicySettings (table `otp_policy_settings`, single row)

| Field | Type | Notes |
|---|---|---|
| `id` | SMALLINT (PK, always `1`) | `CHECK (id = 1)` enforces the single-row invariant |
| `resend_cooldown_seconds` | integer, default 30 | FR-028 |
| `max_consecutive_requests` | integer, default 5 | FR-029 |
| `consecutive_request_lockout_hours` | integer, default 4 | FR-029 |
| `updated_at` | timestamp | when the row was last changed |

**Constraints**: exactly one row, seeded by Flyway with the defaults above. Read fresh by
`OtpService` on every request (research.md §17) — no caching layer, since a DB read per OTP request
is negligible at this deployment's scale (constitution: under 100 users). The transient per-key
throttle *state* (last-request time, consecutive count, lock-until) is in-memory only, consistent
with `one_time_code`'s own rate-limit bucket (research.md §4) — only the *thresholds* here are
DB-backed, so an in-memory-only outage (a restart) simply resets everyone's throttle state to zero,
which is the safe direction to fail in.

## Relationships

```text
AppUser (1) ──< RoleAssignment (many)
AppUser (1) ──< Session (many)
Session (1) ──< RenewalCredential (many, chained via superseded_by)
AppUser (1) ──< LoginHistoryEvent (many, via user_id when known)
(no FK)      OneTimeCode is keyed by phone, not user_id, since an OTP can be
             requested before a user's existence is confirmed (neutral response requirement, FR-007)
(no FK)      OtpPolicySettings is a standalone singleton, referenced by destination key
             (not a foreign key) from OtpService's in-memory throttle state (FR-028/FR-029)
```
