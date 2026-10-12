# Contract: Substitution API (HTTP)

Errors: house format `{ "reason": "..." }`: 400 invalid, 403 permission, 404 not found or out of scope, 409 conflict (stale
version, unavailable Teacher, wrong status). Order of checks: permission, scope, rules.

| Method | Path | Key / action | Purpose |
| --- | --- | --- | --- |
| GET | `/api/v1/substitutions?status=&schoolId=&from=&to=&page=&size=` | SUBSTITUTION VIEW | requirements in scope, default OPEN and ASSIGNED, newest duty date first |
| GET | `/api/v1/substitutions/{id}` | VIEW | detail with history and School informed |
| GET | `/api/v1/substitutions/{id}/candidates` | VIEW | every active Teacher but the absent one: `{teacherId, name, available, reason}` |
| POST | `/api/v1/substitutions/{id}/assign` | EDIT | `{substituteTeacherId, note, version}` (assign or reassign) |
| POST | `/api/v1/substitutions/{id}/release` | EDIT | `{reason, version}` back to OPEN |
| POST | `/api/v1/substitutions/{id}/cover` | EDIT | `{note, version}` Manager covers |
| POST | `/api/v1/substitutions/{id}/inform` | EDIT | `{contact, method, note, version}` School informed |
| GET | `/api/v1/me/substitutions?from=&to=` | MY_SUBSTITUTIONS VIEW | the caller's own assignments |
| PUT | `/api/v1/attendance/teachers/{teacherId}/marks/{date}/principal-confirmation` | TEACHER_ATTENDANCE or ATTENDANCE EDIT | `{confirmed, principalName, note}` |
| DELETE | same | same | clear it |

Requirement view: `id, teacherId, teacherName, schoolId, schoolName, dutyDate, dayValue, kind, status, substituteTeacherId,
substituteName, coverName, informed{at, byName, contact, method, note}, note, version`. The `/me` view omits the absent
Teacher and has `id, schoolId, schoolName, dutyDate, dayValue, status`.
