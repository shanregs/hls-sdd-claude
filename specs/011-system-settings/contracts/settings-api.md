# Contract: Settings and System Dashboard API (spec 011)

All endpoints need a bearer token. 401 without one. 403 without the stated grant. 422 `{ "message", "fields": {key: msg} }`
for invalid values.

## GET /api/v1/settings/{group}  (group = `system` | `security`)

Needs View on `SYSTEM_SETTINGS` (system) or `SECURITY_SETTINGS` (security).

```json
{ "group": "security",
  "settings": [
    { "key": "lockout.max-failed-attempts", "label": "Failed sign-ins before lockout", "unit": "attempts",
      "type": "INTEGER", "min": 3, "max": 20, "default": "5", "value": "5", "isDefault": true,
      "updatedAt": null, "updatedBy": null }
  ] }
```

`default` is the second-tier value (deployed property or built-in). `isDefault` is true when no override exists.

## PUT /api/v1/settings/{group}

Needs Edit on the group's module. Body `{ "values": { "<key>": "<text>", ... } }`. Only keys of the group are
accepted (unknown key: 422). All values are validated first; any failure stores nothing. A value equal to the
current effective value is a no-op (no audit). Response: the same body as GET (200).

## GET /api/v1/system/dashboard

Needs View on `SYSTEM_DASHBOARD`.

```json
{ "health": { "database": "UP", "applicationVersion": "0.1.0", "migrationVersion": "40",
              "startedAt": "...", "uptimeSeconds": 1234, "serverTime": "..." },
  "users": { "total": 12, "active": 10, "inactive": 2, "lockedNow": 1,
             "byRole": { "ADMIN": 1, "DIRECTOR": 1, "MANAGER": 3, "TEACHER": 7, "SYSTEM": 1 } },
  "failedSignInsLast24h": 3,
  "recentAudit": { "status": "OK",
                   "events": [ { "kind": "CHANGE", "at": "...", "actorName": "A. Admin", "summary": "SECURITY_SETTING lockout.max-failed-attempts: 5 -> 3" },
                               { "kind": "LOGIN", "at": "...", "actorName": null, "summary": "Password sign-in failed" } ] } }
```

A user holding several roles is counted once in each role. `recentAudit.status` is `UNAVAILABLE` (empty events) if
the lookup fails; the rest of the response still returns.

## Unchanged public endpoint

`GET /api/v1/mobile/app-config` (no auth) returns `{minimumVersion, locationWaitSeconds, locationReuseSeconds}` from
System Settings; Cache-Control stays `public, max-age=300`.

## Internal interfaces

- `com.hls.identity.policy.RuntimeSettings` (identity, implemented by settings): typed getters for all eleven values.
- `com.hls.audit.api.RecentAudit` (audit): `recent(Set<Role>, int)` and `failedSignInsSince(Instant)`.
