# API Contract: Master Data

All endpoints require a valid session (spec 001's access token) and independently enforce both a
permission (`PermissionGuard`) and the caller's data scope, regardless of what the frontend renders
(Constitution Principle X). Failures are `{"reason": "..."}`.

**Common rules**
- `401` no valid session; `403` the caller's roles lack the permission; `404` the record does not
  exist **or is outside the caller's data scope** (indistinguishable, FR-020).
- `409` a business-rule refusal (reason says why) or a stale `version` ("This record was changed by
  someone else. Reload and try again."); `400` invalid input.
- Lists: `page` (default 0), `size` (default 25, max 100), optional `query`; response
  `{"content":[...],"page":0,"size":25,"totalElements":N}`.
- Mutable resources include `version`; update bodies must send the `version` they loaded.
- Dates are ISO `YYYY-MM-DD`; money is a decimal rupee amount.

## Zones - module `school`

| Method | Path | Permission | Notes |
| --- | --- | --- | --- |
| GET | `/api/v1/zones` | `ZONES.VIEW` | `query`; each item has `placeCount`, `schoolCount`, `managerCount` |
| POST | `/api/v1/zones` | `ZONES.CREATE` | `{ "name": "Chengalpattu" }` -> 201 `{id,name,version,...}`; 409 duplicate name |
| PUT | `/api/v1/zones/{id}` | `ZONES.EDIT` | `{ "name", "version" }` (rename) |
| DELETE | `/api/v1/zones/{id}` | `ZONES.DELETE` | 204; 409 listing what depends on it (Places, Schools, Managers) |

## Places - module `school`

| Method | Path | Permission | Notes |
| --- | --- | --- | --- |
| GET | `/api/v1/zones/{zoneId}/places` | `ZONES.VIEW` | paginated, `query` on name/PIN |
| GET | `/api/v1/places` | `ZONES.VIEW` | lookup: `pinCode` and/or `name`; returns **every** match with its Zone; empty list, not an error |
| POST | `/api/v1/zones/{zoneId}/places` | `ZONES.EDIT` | `{ "name", "pinCode" }`; 400 if PIN is not six digits; duplicates accepted |
| PUT | `/api/v1/places/{id}` | `ZONES.EDIT` | `{ "name", "pinCode", "zoneId" }`; 409 if changing `zoneId` while Schools are in it |
| DELETE | `/api/v1/places/{id}` | `ZONES.EDIT` | 204; 409 while any School is in it |
| POST | `/api/v1/places/bulk-import` | `ZONES.CREATE` | below |

### POST /api/v1/places/bulk-import

```json
{ "zoneId": "...", "rows": [ { "name": "Madurantakam", "pinCode": "603306" } ] }
```

**Response 200** (always, when the batch itself is acceptable; one entry per input row, in order):

```json
{ "added": 15, "alreadyExisted": 2, "rejected": 3,
  "results": [ { "row": 1, "outcome": "ADDED" },
               { "row": 2, "outcome": "ALREADY_EXISTS" },
               { "row": 3, "outcome": "REJECTED", "reason": "PIN code must be six digits." } ] }
```

`ALREADY_EXISTS` = same name (case-insensitive) and PIN code in that Zone already, or earlier in the
same list. **400** (nothing created) if `rows` is empty, exceeds 5,000, or `zoneId` is unknown.

## Schools - module `school`

| Method | Path | Permission | Notes |
| --- | --- | --- | --- |
| GET | `/api/v1/schools` | `SCHOOLS.VIEW` | scoped; filters `query`, `zoneId`, `placeId`, `active`; item: profile, `place{id,name,pinCode}`, `zone{id,name}`, `manager{id,displayName}` or null, `teacherCount` |
| GET | `/api/v1/schools/{id}` | `SCHOOLS.VIEW` | scoped; 404 if out of scope |
| POST | `/api/v1/schools` | `SCHOOLS.CREATE` | `{ name, placeId, address, contactPerson, contactPhone, billingContact }` -> 201; Place required |
| PUT | `/api/v1/schools/{id}` | `SCHOOLS.EDIT` | full profile + `version`. A **Manager** caller may change only `contactPerson`, `contactPhone`, `address`; any other changed field -> **403** `"Managers can edit only a School's contact person, phone, and address."` Admin/Director may change `name`, `billingContact` too |
| PUT | `/api/v1/schools/{id}/place` | `SCHOOLS.EDIT` (Admin/Director only) | `{ "placeId", "version" }`; 409 if the School's Manager does not cover the new Place's Zone |
| POST | `/api/v1/schools/{id}/deactivate` | `SCHOOLS.DELETE` | 204; 409 while active or scheduled Teachers are placed in it |
| POST | `/api/v1/schools/{id}/reactivate` | `SCHOOLS.DELETE` | 204 |

## Managers and assignments - module `organization`

| Method | Path | Permission | Notes |
| --- | --- | --- | --- |
| GET | `/api/v1/managers` | `MANAGERS.VIEW` | item: `id,userId,displayName,phone,active`, `zones[]`, `schoolCount`, `teacherCount` |
| GET | `/api/v1/managers/{id}` | `MANAGERS.VIEW` | adds zone and school assignment history |
| POST | `/api/v1/managers` | `MANAGERS.CREATE` | `{ "userId" }`; 400 if the user lacks the Manager role; 409 if already a Manager |
| PUT | `/api/v1/managers/{id}/zones` | `MANAGERS.EDIT` | `{ "zoneIds": [...], "version" }` replaces the current Zone set; 409 listing Schools if removing a Zone where the Manager still has Schools |
| PUT | `/api/v1/schools/{schoolId}/manager` | `MANAGERS.EDIT` | `{ "managerId": "..." \| null }`; 409 if the Manager does not cover the School's Zone; null unassigns |
| GET | `/api/v1/schools/{schoolId}/manager-history` | `MANAGERS.VIEW` | dated assignment history |
| GET | `/api/v1/me/scope` | any authenticated | the caller's `{ orgWide, zoneCount, schoolCount, zones[{id,name}] }` for the dashboard (no other user's data) |

## Teachers - module `teacher`

| Method | Path | Permission | Notes |
| --- | --- | --- | --- |
| GET | `/api/v1/teachers` | `TEACHERS.VIEW` | scoped (Manager: placed in their Schools today; Admin/Director: all, including unplaced); filters `query`, `status`, `schoolId`; item: profile, status, current `school`, `manager`, `pendingPlacement` - **no salary field** |
| GET | `/api/v1/teachers/{id}` | `TEACHERS.VIEW` | scoped; includes placement history |
| POST | `/api/v1/teachers` | `TEACHERS.CREATE` | `{ name, phone, email, address, status, userId? }`; Admin/Director only |
| PUT | `/api/v1/teachers/{id}` | `TEACHERS.EDIT` | contact fields + `version`; Manager allowed for scoped Teachers |
| POST | `/api/v1/teachers/{id}/status` | `TEACHERS.EDIT` (Admin/Director) | `{ "status", "effectiveOn" }`; 409 `"A Teacher cannot move from X to Y."` for any disallowed transition, including anything from `EXITED` |
| PUT | `/api/v1/teachers/{id}/user` | `TEACHERS.EDIT` (Admin/Director) | `{ "userId" \| null }`; 400 if the user lacks the Teacher role; 409 if already linked to another Teacher or if the Teacher has exited |
| POST | `/api/v1/teachers/{id}/placements` | `TEACHERS.EDIT` (Admin/Director) | `{ "schoolId", "effectiveOn" }`: past/today -> immediate; future -> scheduled. 409 for: date before current placement start, exited Teacher, inactive School |
| DELETE | `/api/v1/teachers/{id}/placements/pending` | `TEACHERS.EDIT` (Admin/Director) | cancel the scheduled move -> 204 |
| GET | `/api/v1/teachers/me` | any authenticated | the caller's own record (name, contact, status, current School; **no salary**) or `404` with `{"reason":"Your profile has not been set up yet."}` |

### Salary - module `teacher`

| Method | Path | Permission | Notes |
| --- | --- | --- | --- |
| GET | `/api/v1/teachers/{id}/salary` | `TEACHER_SALARY.VIEW` | full history, newest first, plus `current` |
| GET | `/api/v1/teachers/{id}/salary?asOf=2026-06-15` | `TEACHER_SALARY.VIEW` | `{ "amount": 20000.00, "effectiveOn": "2026-04-01" }` or `{ "amount": null }` when none recorded by then |
| POST | `/api/v1/teachers/{id}/salary` | `TEACHER_SALARY.CREATE` | `{ "amount", "effectiveOn" }` appends a row; never edits one |

## Scope shared with later modules (Java API, not HTTP)

- `organization.api.ScopeQueries.scopeOf(userId, roles) -> ScopeView{orgWide, zoneIds, schoolIds}`
- `teacher.api.TeacherScopeQueries.teacherIdsInScope(userId, roles)` and
  `.isTeacherInScope(userId, roles, teacherId)`

Later modules (attendance, leave, payroll, reports) MUST use these and MUST NOT re-implement scoping.

## Audit side effects (every write above)

Each successful write publishes one `audit.api.EntityChanged` per changed field - actor, time,
`entityType`, `entityId`, `field`, `beforeValue`, `afterValue` - appended to Change History.

## Audit visibility change (spec 003 endpoints, behavior added by this spec)

`GET /api/v1/audit/change-history` (and `/export`) and `GET /api/v1/audit/logs` omit entries whose
`entityType` requires a `VIEW` grant the caller lacks (data-model.md). A System user therefore sees
no master-data or salary entries; an Admin sees them all.
