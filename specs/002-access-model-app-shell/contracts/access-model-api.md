# API Contract: Access Model & Permission Matrix

All endpoints require a valid session (spec 001's JWT access token) in the `Authorization` header.
There are no public endpoints in this feature. Every endpoint independently re-checks permission and
scope server-side (Constitution Principle X) regardless of what the frontend would render.

## GET /api/v1/me/access-model

Returns the resolved access model for the caller, per data-model.md's "Access Model" shape.

**Authorization**: any authenticated user (this is how they learn what they may do).

**Response 200**:

```json
{
  "roles": ["MANAGER"],
  "navigation": [
    {
      "section": "Dashboard",
      "items": [
        { "label": "Dashboard", "route": "/dashboard", "actions": ["VIEW"] }
      ]
    },
    {
      "section": "ACCOUNT",
      "items": [
        { "label": "Profile", "route": "/account/profile", "actions": ["VIEW", "EDIT"] }
      ]
    }
  ],
  "dataScope": {
    "DASHBOARD": "ASSIGNED"
  }
}
```

**Notes**:
- Sections with no authorized item for this caller are absent from `navigation` entirely (FR-008),
  not present with an empty `items` array.
- `roles` reflects the union basis for everything else in the payload; the frontend MUST NOT
  recompute grants from `roles` itself — `navigation`/`dataScope` are the authoritative, already-
  resolved result.
- Called on sign-in and on every session renewal (research.md §7). No separate cache-invalidation
  endpoint exists in this feature; a fresh call always reflects the current matrix.

**Response 401**: no valid session. Standard unauthenticated response, consistent with spec 001.

---

## GET /api/v1/identity/permission-matrix

Returns the full current matrix (all stored role × module × action rows) and, for every module, the
actions that can be granted to each role, for the Role & Permissions screen (a module × role grid of
icons; data-model.md "Eligibility").

**Authorization**: `ADMIN`, `DIRECTOR`, or `SYSTEM` only. Any other caller receives 403, and per
FR-002 the frontend never renders a route that would call this for a Manager/Teacher.

**Response 200**:

```json
{
  "entries": [
    { "role": "MANAGER", "module": "DASHBOARD", "action": "VIEW", "granted": true },
    { "role": "MANAGER", "module": "IDENTITY_PERMISSIONS", "action": "VIEW", "granted": false }
  ],
  "modules": [
    {
      "module": "USER_MANAGEMENT",
      "eligible": {
        "ADMIN": ["VIEW", "CREATE", "EDIT"], "DIRECTOR": ["VIEW", "CREATE", "EDIT"],
        "MANAGER": ["VIEW", "CREATE", "EDIT"], "TEACHER": ["VIEW", "CREATE", "EDIT"],
        "SYSTEM": ["VIEW", "CREATE", "EDIT"]
      }
    },
    {
      "module": "IDENTITY_PERMISSIONS",
      "eligible": {
        "ADMIN": ["VIEW", "EDIT"], "DIRECTOR": ["VIEW", "EDIT"], "MANAGER": [],
        "TEACHER": [], "SYSTEM": ["VIEW", "EDIT"]
      }
    }
  ]
}
```

`modules` lists every module in the fixed order, each with, per role, the eligible actions in the fixed
action order; an empty list means nothing applies to that role (shown as a dash). A stored entry with
`granted: false`, and an eligible action with no stored entry, both read as "not granted".

**Response 403**: caller lacks `IDENTITY_PERMISSIONS.VIEW`.

---

## PUT /api/v1/identity/permission-matrix/{role}/{module}/{action}

Sets one grant's `granted` flag.

**Authorization**: `ADMIN`, `DIRECTOR`, or `SYSTEM` only (`IDENTITY_PERMISSIONS.EDIT`).

**Request body**:

```json
{ "granted": false }
```

**Response 200**: the updated entry, in the same shape as the GET list's `entries[]`.

**Response 409 (Conflict)**: the edit was rejected because it would remove the last remaining
`ADMIN`/`DIRECTOR`/`SYSTEM` grant of `IDENTITY_PERMISSIONS.EDIT` (FR-004, data-model.md's
invariant). Body includes a plain-language `reason` field; no partial state change occurs.

```json
{ "reason": "This would leave no one able to manage the permission matrix." }
```

**Response 409 (Conflict)**, second cause: the grant is not eligible (the action does not apply to the
module, or the role may not hold the module). Checked first; no state change.

```json
{ "reason": "That permission does not apply to this role and module." }
```

**Response 403**: caller lacks `IDENTITY_PERMISSIONS.EDIT` (e.g., a Manager somehow reaching the
route directly).

**Side effect**: every successful edit publishes a change record (actor, timestamp, role, module,
action, before/after `granted`) for spec 003's Audit module to consume (FR-003). This feature does
not expose a way to read that history itself.

---

## Frontend routing contract (not a network API, but a shared contract)

- Every route the shell defines maps to exactly one `(module, action)` pair from the navigation
  catalog. `RouteGuard` checks the current access model before rendering; on failure it renders
  `NotAuthorizedPage` and does not mount the target route's component (so no data-fetching for that
  screen ever occurs — FR-010's "MUST NOT fetch that screen's data").
- The root route redirects to `/dashboard`, which itself renders per-role widgets from the access
  model — never a role-selection step (FR-011).
