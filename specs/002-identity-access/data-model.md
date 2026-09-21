# Data Model: Identity & Access

Derived from spec.md's Key Entities section, its Functional Requirements, and research.md's decisions. Two tables (`OtpChallenge`, `PasswordResetToken`) are implementation-level supporting entities the spec's Key Entities section doesn't name directly, but FR-008/FR-009/FR-016 require them to exist in some form.

## User

| Field | Type | Notes |
|---|---|---|
| `id` | UUID (PK) | |
| `displayName` | text | |
| `phoneNumber` | text, unique | Primary login identifier for every role (FR-017) |
| `email` | text, nullable | Used for password reset / MFA if on file (FR-016, FR-018) |
| `roles` | set of enum (`DIRECTOR`, `MANAGER`, `ADMIN`, `ACCOUNTS_OFFICER`, `TEACHER`) | One or more; effective access is the union (FR-002) |
| `passwordHash` | text, nullable | Null for users who only ever use OTP login (pure Teachers with no staff role) |
| `mfaEnabled` | boolean | Default false (FR-007's "optional MFA") |
| `mfaMethod` | enum (`SMS`, `EMAIL`), nullable | Set only when `mfaEnabled = true` (FR-018) |
| `active` | boolean | |
| `failedAttemptCount` | integer | Reset to 0 on successful login |
| `lockedUntil` | timestamp, nullable | Null = not locked; set on reaching the configurable threshold (FR-010) |
| `linkedTeacherId` / `linkedManagerId` | UUID, nullable | Opaque references to the later Teacher Master Data / Organization records — same forward-reference pattern as spec 003 |
| `createdAt` | timestamp | |

**Invariants**:
- `phoneNumber` unique across all roles (FR-017) — DB unique constraint, not just an application check.
- A user with only the `TEACHER` role and no other role may have `passwordHash = NULL` (Teachers never set a password, per spec — OTP only).
- `lockedUntil` in the future blocks login even with correct credentials (FR-010); an explicit unlock action clears it.

## Session

| Field | Type | Notes |
|---|---|---|
| `id` | UUID (PK) | Embedded as a claim in the access token, so validation can look it up |
| `userId` | UUID | |
| `channel` | enum (`WEB`, `MOBILE`) | FR-007/FR-008's two login surfaces |
| `refreshTokenHash` | text | Hash of the current refresh token, rotated on each use (never the raw token) |
| `issuedAt` | timestamp | |
| `lastActiveAt` | timestamp | Updated on each successful refresh |
| `expiresAt` | timestamp | Absolute inactivity ceiling (FR-011) |
| `revoked` | boolean | Set by FR-012's revoke action |
| `revokedAt` | timestamp, nullable | |
| `deviceLabel` | text, nullable | Enough detail to identify the session in a list (User Story 5 acceptance scenario 1) |

**Invariants**:
- Once `revoked = true`, no further refresh or session-scoped check against this row succeeds (research.md §5) — this is what makes SC-005's 60-second bound hold.
- `refreshTokenHash` changes on every successful refresh (rotation); the previous hash is no longer valid (prevents replay of a stolen refresh token after it's been used once).

## AuthAuditEntry

| Field | Type | Notes |
|---|---|---|
| `id` | UUID (PK) | |
| `actorUserId` | UUID, nullable | Null when the identifier attempted doesn't resolve to a known user (FR-015 — don't reveal existence, but still record the attempt) |
| `attemptedIdentifier` | text, nullable | The phone number/credential attempted, for failed/unknown-identity attempts |
| `action` | enum (`LOGIN_SUCCESS`, `LOGIN_FAILURE`, `ACCESS_DENIED`, `SESSION_REVOKED`, `ACCOUNT_LOCKED`, `ACCOUNT_UNLOCKED`, `PASSWORD_RESET_REQUESTED`, `PASSWORD_RESET_COMPLETED`) | FR-013 |
| `roleAtTime` | text, nullable | Snapshot of the acting role(s) at the moment of the event |
| `occurredAt` | timestamp | |
| `requestId` | text, nullable | Correlation id, reusing the warm-up feature's existing `CorrelationIdFilter` |

**Invariants**: Append-only — never updated or deleted (Constitution Principle I), same discipline as spec 003's assignment history rows.

## OtpChallenge *(implementation-level, not in spec's Key Entities — supports FR-008/FR-009)*

| Field | Type | Notes |
|---|---|---|
| `id` | UUID (PK) | |
| `phoneNumber` | text | Looked up by this, not by user id — a challenge can exist before the system reveals whether the number is registered (FR-015) |
| `codeHash` | text | Hashed OTP (research.md §3), never stored or logged in plaintext |
| `expiresAt` | timestamp | Short TTL (research.md §3) |
| `consumed` | boolean | Set true on successful verification; a consumed or expired challenge cannot be reused |
| `attemptCount` | integer | Failed-verification counter feeding the same lockout-style protection as FR-010, scoped to this challenge |

## PasswordResetToken *(implementation-level, not in spec's Key Entities — supports FR-016)*

| Field | Type | Notes |
|---|---|---|
| `id` | UUID (PK) | |
| `userId` | UUID | |
| `tokenHash` | text | Hashed one-time code/link token |
| `expiresAt` | timestamp | |
| `consumed` | boolean | Single-use, same pattern as `OtpChallenge` |

## Relationship to Organization's data (no local table)

`AccessScope` (per spec.md's Key Entities, now annotated "not stored by this module") is **not** a table here. `ManagerScopeGuard` calls `organization.api.AccountabilityQueries.currentManagerForSchool`/`currentManagerForTeacher` (spec 003) at request time for any Manager-scoped check (FR-004, FR-014), and denies the request if that call fails or times out (FR-019). This module's own migration therefore creates no table for Manager-to-Teacher/School assignment data.

## State transitions (login/lockout)

```
        login attempt (password or OTP)
                    │
          ┌─────────┴─────────┐
      credentials valid   credentials invalid
          │                     │
   failedAttemptCount = 0   failedAttemptCount += 1
          │                     │
   Session created         ┌────┴────┐
   AuthAuditEntry:      threshold   below
   LOGIN_SUCCESS        reached    threshold
                            │           │
                   lockedUntil set  AuthAuditEntry:
                   AuthAuditEntry:   LOGIN_FAILURE
                   ACCOUNT_LOCKED
```

Every branch above writes exactly one `AuthAuditEntry` row; none of them ever mutate a previous row.
