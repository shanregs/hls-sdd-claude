# API Contract: Audit

All endpoints require a valid session (spec 001's JWT access token) in the `Authorization` header
and are restricted to `ADMIN` or `SYSTEM` (`PermissionGuard`, re-checked server-side independently
of what the frontend renders — Constitution Principle X). Any other caller receives 403.

Every list endpoint accepts standard pagination/sort query params (`page`, `size`, `sort`) and
returns a `Page`-shaped envelope. Every filter param is optional unless noted; omitting all filters
returns the most recent entries first.

## GET /api/v1/audit/login-history

**Authorization**: `AUDIT_LOGIN_HISTORY.VIEW`.

**Query params**: `userId`, `from` (ISO instant), `to`, `method` (`PASSWORD`\|`OTP`), `outcome`.

**Response 200**:

```json
{
  "content": [
    {
      "id": "…",
      "occurredAt": "2026-09-24T09:12:00Z",
      "userId": "…",
      "phoneMasked": "98XXXXX001",
      "method": "PASSWORD",
      "eventType": "SIGN_IN_ATTEMPT",
      "outcome": "FAILURE"
    }
  ],
  "page": 0,
  "size": 25,
  "totalElements": 1
}
```

---

## GET /api/v1/audit/change-history

**Authorization**: `AUDIT_CHANGE_HISTORY.VIEW`.

**Query params**: `actorUserId`, `entityType`, `entityId`, `from`, `to`.

**Response 200**:

```json
{
  "content": [
    {
      "id": "…",
      "occurredAt": "2026-09-24T09:15:00Z",
      "actorUserId": "…",
      "entityType": "PERMISSION_MATRIX",
      "entityId": "MANAGER.ATTENDANCE.EDIT",
      "field": "granted",
      "beforeValue": "false",
      "afterValue": "true"
    }
  ],
  "page": 0,
  "size": 25,
  "totalElements": 1
}
```

---

## GET /api/v1/audit/user-activity

**Authorization**: `AUDIT_USER_ACTIVITY.VIEW`.

**Query params**: `actorUserId`, `affectedUserId`, `action`, `from`, `to`.

**Response 200**:

```json
{
  "content": [
    {
      "id": "…",
      "occurredAt": "2026-09-24T09:20:00Z",
      "actorUserId": "…",
      "affectedUserId": "…",
      "action": "ACCOUNT_DEACTIVATED",
      "detail": null
    }
  ],
  "page": 0,
  "size": 25,
  "totalElements": 1
}
```

---

## GET /api/v1/audit/logs

The unified feed (US4) — merges all three sources.

**Authorization**: `AUDIT_LOGS.VIEW`.

**Query params**: `type` (`LOGIN`\|`CHANGE`\|`ACTIVITY`, repeatable — omit for all three), `userId`
(matches actor or affected user, whichever the entry type has), `from`, `to`.

**Response 200**:

```json
{
  "content": [
    {
      "id": "…",
      "occurredAt": "2026-09-24T09:20:00Z",
      "type": "ACTIVITY",
      "actorUserId": "…",
      "summary": "Account deactivated"
    }
  ],
  "page": 0,
  "size": 25,
  "totalElements": 1
}
```

---

## GET /api/v1/audit/{login-history|change-history|user-activity|logs}/export

Same path suffix and same query params as the corresponding list endpoint above, plus the same
authorization (`*.EXPORT` in place of `*.VIEW`). `from` and `to` are **required** on export (unlike
the list endpoints), bounding the result set (spec.md's edge case on unbounded exports;
research.md §5).

**Response 200**: `Content-Type: text/csv`, streamed body, one row per entry, header row matching
the JSON field names above.

**Response 400**: `from`/`to` missing or `to` earlier than `from`.

---

## Notes

- No endpoint in this contract accepts a write (`POST`/`PUT`/`PATCH`/`DELETE`) — every audit entry
  is created only as a side effect of consuming a domain event from another module (FR-004;
  research.md §4). There is deliberately no `POST /api/v1/audit/...` for manual entry creation.
- `PermissionModule` gains four new constants for this spec: `AUDIT_LOGS`, `AUDIT_LOGIN_HISTORY`,
  `AUDIT_CHANGE_HISTORY`, `AUDIT_USER_ACTIVITY` — each seeded with `VIEW` and `EXPORT` grants for
  `ADMIN` and `SYSTEM` only, all other roles/actions `false` (spec.md's Role & Permission Impact
  table; matches `PermissionMatrixSeeder`'s existing seed-defaults pattern).
- Four new `NavigationCatalog` entries appear under the `AUDIT` section for `ADMIN` and `SYSTEM`
  (Audit Logs, Login History, Change History, User Activity), following the same
  `NavItem(label, route, module, action, section, order, roles)` shape spec 002 established.
- **Implementation note**: two real regressions were found and fixed during implementation, not
  anticipated by plan.md/research.md: (1) `identity.permissions`/`identity.loginhistory`/
  `identity.user` needed `@org.springframework.modulith.NamedInterface` `package-info.java` files
  — Spring Modulith treats sub-packages as internal by default, so `audit` reaching into
  `PermissionGuard`, `Role`, `LoginHistoryRecorded`, etc. failed `ApplicationModulesTest` until
  exposed; (2) `event_publication.serialized_event` (tasks.md T004a) needed `TEXT`, not the
  library's implicit `VARCHAR(255)` — real event payloads overflowed it, breaking unrelated spec
  001 tests (`LockoutIntegrationTest`, `SessionManagementIntegrationTest`) that publish through the
  same registry. Both fixed; full suite green after.

## Added by spec 018 (Android app foundation)

Spec 018 extends this contract; the full shapes are in
`specs/018-android-app-foundation/contracts/mobile-api.md`. Summary:

- Login History, User Activity and the combined Audit Logs rows gain `source` (`WEB` or `ANDROID`),
  `appVersion`, and a `location` object (`status`, `latitude`, `longitude`, `accuracyMeters`,
  `capturedAt`). Login History rows also gain `deviceRooted`. Authorization is unchanged: the same
  `AUDIT_*` view and export permissions (Admin and System by default), so Teacher, Manager and
  Director never receive these fields.
- Login History and User Activity accept an optional `source=WEB|ANDROID` filter (400 for any other
  value). All four CSV exports of this spec gain the columns `source`, `appVersion`,
  `locationStatus`, `latitude`, `longitude`, `accuracyMeters` (and `deviceRooted` for Login History).
- New permission module `AUDIT_API_ACCESS` (View and Export, Admin and System by default) and a new
  endpoint `GET /api/v1/audit/api-access` (+ `/export`), with the AUDIT → API Access menu item.
  API Access entries are not part of the combined Audit Logs list.
