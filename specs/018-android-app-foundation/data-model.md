# Data Model: Android App Foundation

This feature adds columns to existing tables and one small in-request value object. It adds no new
table. All additions are nullable or defaulted, so existing rows stay valid and the audit tables
remain append-only (rows are only ever inserted).

Migration: `V16__add_client_context_to_audit_and_session.sql`. The number is the next free one after
`V15` at the time of planning; if spec 008 or another branch adds a migration first, take the next
free number at implementation time.

## ClientContext (in-request value, not stored as such)

Built per request from the headers in `contracts/mobile-api.md`.

| Field | Type | Notes |
| ----- | ---- | ----- |
| source | `WEB` \| `ANDROID` | `ANDROID` only when `X-HLS-Client` is present and well formed |
| appVersion | string, nullable | semantic version from the header, e.g. `1.0.0` |
| location | LocationCapture | always present; status `NOT_APPLICABLE` for `WEB` |
| deviceRooted | boolean | `true` only when `X-HLS-Device-Integrity: ROOTED_SUSPECTED` was sent |

## LocationCapture (value)

| Field | Type | Notes |
| ----- | ---- | ----- |
| status | enum | `AVAILABLE`, `PERMISSION_DENIED`, `SERVICES_OFF`, `NO_FIX`, `INVALID`, `OTHER`, `NOT_APPLICABLE` |
| latitude | decimal(9,6), nullable | only when `AVAILABLE`; -90..90 |
| longitude | decimal(9,6), nullable | only when `AVAILABLE`; -180..180 |
| accuracyMeters | real, nullable | only when `AVAILABLE`; 0..5000 |
| capturedAt | timestamptz, nullable | only when `AVAILABLE`; within 5 minutes of server time |

Invariants:
- When `status` is not `AVAILABLE`, latitude, longitude, accuracy and capturedAt are all null.
- A received location that breaks any range becomes `INVALID` with those fields discarded (FR-024).
- `NOT_APPLICABLE` is used for web requests and for Android requests that sent no location headers
  at all. Android requests that sent neither location nor status headers are stored as `OTHER`.

## Changed tables

### `login_history_entry` (audit module, from V8)

| New column | Type | Default | Notes |
| ---------- | ---- | ------- | ----- |
| source | VARCHAR(10) NOT NULL | `'WEB'` | `WEB` or `ANDROID` |
| app_version | VARCHAR(20) | null | |
| location_status | VARCHAR(20) NOT NULL | `'NOT_APPLICABLE'` | enum above |
| latitude | NUMERIC(9,6) | null | |
| longitude | NUMERIC(9,6) | null | |
| accuracy_meters | REAL | null | |
| location_captured_at | TIMESTAMPTZ | null | |
| device_rooted | BOOLEAN NOT NULL | `false` | login history only (FR-028a) |

Check constraint: `location_status = 'AVAILABLE'` if and only if latitude, longitude, accuracy and
captured_at are all non-null.

### `user_activity_entry` (audit module, from V8)

Same columns as above except `device_rooted` (not applicable). Rows created from events triggered by
a request on the Android app carry the context; background-triggered rows (for example automatic
lock expiry) have `source = 'WEB'` and `location_status = 'NOT_APPLICABLE'`.

### `session` (identity module, from V2)

| New column | Type | Default | Notes |
| ---------- | ---- | ------- | ----- |
| client_type | VARCHAR(10) NOT NULL | `'WEB'` | `WEB` or `ANDROID` |
| app_version | VARCHAR(20) | null | |

Shown in `GET /api/v1/me/sessions` so the list can mark Android sessions (FR-007).

## Unchanged

`change_history_entry` has no location (spec FR-023 limits location to login history and user
activity). The access-model tables, `one_time_code`, `renewal_credential` and all business tables
are unchanged.

## Events changed (identity → audit)

| Event | Change |
| ----- | ------ |
| `LoginHistoryRecorded` | adds `ClientContext clientContext` (nullable; null means web) |
| Request-triggered events in `identity.activity` (`SessionEnded`, `PasswordChanged`, `PasswordResetByAdmin`, `PasswordResetCompleted`, `ProfileUpdated`, `UserCreated`, `UserRoleChanged`, `AccountActivationChanged`) | add `ClientContext clientContext` (nullable) |
| `AccountLockChanged`, `PasswordResetRequested` | add it too; they are triggered by an unauthenticated request, so the context is still the caller's |

The audit consumers copy the context to the columns above. Redelivery stays idempotent through the
existing `source_event_id` unique key.

## Retention

Location columns have no separate lifetime: they live and go exactly as the audit row they are on
(FR-027). There is no update or delete path for them.
