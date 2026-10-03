# API Contract: User Management

All endpoints require a valid session (spec 001's JWT access token) in the `Authorization` header
and are restricted to `ADMIN` or `SYSTEM` (`PermissionGuard`, re-checked server-side independently
of what the frontend renders — Constitution Principle X). Any other caller receives 403.

Role & Permission Management's existing endpoints (`GET`/`PUT /api/v1/identity/permission-matrix`,
spec 002) are unchanged by this spec and are not repeated here.

## GET /api/v1/identity/users

**Authorization**: `USER_MANAGEMENT.VIEW`.

**Query params**: `query` (optional free-text match against display name or phone), `role` (optional,
one of the five fixed roles), `active` (optional boolean), `page`, `size`. Filters combine with AND.

**Response 200**:

```json
{
  "content": [
    {
      "id": "…",
      "displayName": "Manoj Manager",
      "phone": "9800000003",
      "username": "manoj.manager",
      "email": "manoj.manager@example.com",
      "roles": ["MANAGER"],
      "active": true
    }
  ],
  "page": 0,
  "size": 25,
  "totalElements": 1
}
```

---

## POST /api/v1/identity/users

**Authorization**: `USER_MANAGEMENT.CREATE`.

**Request**:

```json
{
  "displayName": "New Teacher",
  "phone": "9800000099",
  "roles": ["TEACHER"],
  "username": null,
  "email": null,
  "initialPassword": null
}
```

**Response 201**: the created user (same shape as the list's `content` entries).

**Response 400**: `roles` is empty, or contains a value outside the five fixed roles.

**Response 409**: `{"reason": "A user with phone 9800000099 already exists."}` (reusing spec 001's
existing duplicate-phone/username/email rejections verbatim; the message names the duplicated field).

---

## PUT /api/v1/identity/users/{userId}/roles

Replaces the user's full role set with exactly the roles given — the server computes which roles
were added and which were removed and publishes one `UserRoleChanged` event per change (data-
model.md).

**Authorization**: `USER_MANAGEMENT.EDIT`.

**Request**:

```json
{ "roles": ["MANAGER", "DIRECTOR"] }
```

**Response 200**: the updated user.

**Response 400**: `{"reason": "A user must hold at least one role."}` — the given role set is
empty — or it contains a value outside the five fixed roles.

**Response 409**: `{"reason": "This would leave no active user able to administer the system as Admin."}`
— the last-admin safeguard (FR-007) refused the request; no role was changed.

---

## POST /api/v1/identity/users/{userId}/deactivate

**Authorization**: `USER_MANAGEMENT.EDIT`.

**Response 204**: all of the user's active sessions end immediately (reusing spec 001's existing
enforcement); no further sign-in succeeds for them by any method.

**Response 409**: `{"reason": "This would leave no active user able to administer the system as Admin."}`
— the last-admin safeguard refused the request; the user remains active.

---

## POST /api/v1/identity/users/{userId}/reactivate

**Authorization**: `USER_MANAGEMENT.EDIT`.

**Response 204**: the user can sign in again, recognized by exactly the roles they held at
deactivation. Never refused by the last-admin safeguard (reactivation only ever increases the
number of active Admins).

Deactivating an already-inactive user, and reactivating an already-active one, return 204 as no-ops
and publish no event.

---

## POST /api/v1/identity/users/{userId}/reset-password

**Authorization**: `USER_MANAGEMENT.EDIT`.

**Request**:

```json
{ "newPassword": "a-brand-new-password" }
```

**Response 204**: the password is set; all of the user's existing sessions end and any account
lockout is cleared (identical side effects to spec 001's self-service reset). The password is never
logged, echoed, or recorded in the audit entry.

**Response 400**: `{"reason": "Password must be at least 10 characters and not your phone number."}`
— the exact message spec 001's self-service reset already uses (research.md §2's shared
`PasswordPolicy`).

---

## Notes

- **Token invalidation (FR-004/FR-006)**: after `deactivate` or `reset-password`, the affected
  user's already-issued access tokens are rejected with 401 on their next request (the token's `sid`
  session is revoked / the user is inactive). A *role* change still takes effect within one token
  lifetime (15 minutes).
- **Atomicity (FR-011)**: the last-admin check and the change it guards run in one transaction under
  a pessimistic lock, so concurrent requests cannot together leave zero active Admins.

- Every write endpoint above is additionally recorded as an immutable User Activity entry
  (FR-008) — this is a side effect of the underlying `UserAdminService` methods publishing domain
  events, not something the endpoint itself does directly, and is not separately retryable or
  skippable by the caller.
- `PermissionModule` gains one new constant for this spec: `USER_MANAGEMENT`, seeded with `VIEW`,
  `CREATE`, and `EDIT` grants for `ADMIN` and `SYSTEM` only, all other roles/actions `false`
  (spec.md's Role & Permission Impact table; matches `PermissionMatrixSeeder`'s existing
  seed-defaults pattern, same as spec 003's four `AUDIT_*` constants).
- Two new `NavigationCatalog` entries appear for "User Management": under section `SYSTEM` for
  `ADMIN`, and under section `SYSTEM CONFIGURATION` for `SYSTEM` — following the exact
  `NavItem(label, route, module, action, section, order, roles)` shape specs 002/003 established.
