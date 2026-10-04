# API Contract: Android App Foundation

This feature adds **one public endpoint**, changes a few existing endpoints only for requests that
carry the Android client header, and extends two audit responses. Everything else the app calls is
the existing contract of spec 001 (`auth-api.md`), 002 (`access-model-api.md`) and 003
(`audit-api.md`), unchanged.

## Request headers sent by the app on every call to the HLS API

| Header | Value | When |
| ------ | ----- | ---- |
| `X-HLS-Client` | `android/<semver>` e.g. `android/1.0.0` | always |
| `X-HLS-Location` | `lat=12.971599;lng=77.594566;acc=18.5;ts=1759581000000` (`ts` = capture time, epoch ms) | when a position was captured for this call |
| `X-HLS-Location-Status` | `PERMISSION_DENIED`, `SERVICES_OFF`, `NO_FIX` or `OTHER` | when no position was captured; never sent together with `X-HLS-Location` |
| `X-HLS-Device-Integrity` | `ROOTED_SUSPECTED` | only on `login` and `otp/verify (SIGN_IN)` and only when the check trips |
| `User-Agent` | `HLS-Android/<semver> (Android <os>; <model>)` | always; becomes the session's device description |

Rules for the server:
- Every request that carries `X-HLS-Client` (except `app-config`) produces one API Access entry
  (see "New: API Access" below).
- Headers are optional for every endpoint. A missing or malformed location header never causes an
  error response; it is stored as `OTHER` or `INVALID` (research.md §8).
- `X-HLS-Location` MUST NOT be written to application or access logs.
- Web requests do not send these headers and see no change in behavior.

## Version gate (all endpoints)

If `X-HLS-Client` carries a version below `hls.mobile.min-app-version`, every endpoint except
`GET /api/v1/mobile/app-config` answers:

**Response 426**: `{ "code": "APP_UPDATE_REQUIRED", "message": "Please update the HLS app to continue.", "minimumVersion": "1.2.0" }`

---

## GET /api/v1/mobile/app-config  (new, public)

No session required. Cacheable for 5 minutes.

**Response 200**:

```json
{
  "minimumVersion": "1.0.0",
  "locationWaitSeconds": 4,
  "locationReuseSeconds": 10
}
```

---

## Changed: POST /api/v1/auth/login, POST /api/v1/auth/otp/verify (purpose SIGN_IN)

When `X-HLS-Client` is present:

- **Response 200** adds `renewalCredential` (string) to the body and sets **no** cookie:

  ```json
  {
    "accessToken": "eyJ...",
    "expiresInSeconds": 900,
    "renewalCredential": "9f3c...",
    "user": { "id": "...", "displayName": "...", "roles": ["TEACHER"] }
  }
  ```

- **Response 403** (new): the user's roles contain none of TEACHER, MANAGER, DIRECTOR. No session is
  created. Only returned after the credentials were valid:

  ```json
  { "code": "WEB_ONLY_ROLE", "message": "Your account uses the HLS web application." }
  ```

All other responses (401 wrong credentials, 423 locked, 429 rate limits, 503 gateway) are unchanged.
When the header is absent, behavior is exactly as in spec 001, including the cookie.

## Changed: POST /api/v1/auth/renew

When `X-HLS-Client` is present the renewal credential is read from the body, not a cookie:

**Request**: `{ "renewalCredential": "9f3c..." }`

**Response 200**: same shape as login's 200, with the new rotated `renewalCredential`.

**Response 401**: missing, expired, or already used (reuse also revokes the whole session chain,
FR-010). Body `{ "message": "Please sign in again." }`.

## Changed: POST /api/v1/auth/logout

Unchanged contract. For an Android session no cookie is expired (there is none). Returns 204.

## Changed: GET /api/v1/me/sessions

Each item gains `clientType` (`WEB` or `ANDROID`) and `appVersion` (nullable):

```json
{
  "id": "...",
  "deviceDescription": "HLS-Android/1.0.0 (Android 14; Pixel 7)",
  "signedInAt": "...",
  "lastActivityAt": "...",
  "current": true,
  "clientType": "ANDROID",
  "appVersion": "1.0.0"
}
```

`DELETE /api/v1/me/sessions/{sessionId}` is unchanged.

## Used unchanged by the app

| Endpoint | Spec | Use in the app |
| -------- | ---- | -------------- |
| `POST /api/v1/auth/otp/request` | 001 | OTP sign-in and password reset |
| `GET /api/v1/auth/password-reset/channels` | 001 | which reset channels to offer |
| `POST /api/v1/auth/password-reset/complete` | 001 | finish reset |
| `GET /api/v1/me/access-model` | 002 | builds the menu |
| Profile read/update endpoints | 001/002 | ACCOUNT → Profile |

---

## Extended: audit read and export (spec 003)

Authorization is unchanged: `AUDIT` view/export, which by default means Admin and System only. The
new fields are on the same rows, so no one else can see them.

### GET /api/v1/audit/login-history

Each item gains:

```json
{
  "source": "ANDROID",
  "appVersion": "1.0.0",
  "location": {
    "status": "AVAILABLE",
    "latitude": 12.971599,
    "longitude": 77.594566,
    "accuracyMeters": 18.5,
    "capturedAt": "2026-10-04T09:30:00Z"
  },
  "deviceRooted": false
}
```

When the status is not `AVAILABLE`, `latitude`, `longitude`, `accuracyMeters` and `capturedAt` are
`null` and `status` carries the reason. Entries made on the web have `source: "WEB"` and
`location.status: "NOT_APPLICABLE"`.

### GET /api/v1/audit/user-activity

Same fields as above, without `deviceRooted`.

### GET /api/v1/audit/{login-history|user-activity|logs}/export

The CSV adds columns `source`, `app_version`, `location_status`, `latitude`, `longitude`,
`accuracy_meters`, and, for login history, `device_rooted`. The combined `logs` view carries the
first set for rows that came from either source. Change history is unchanged.

Query filter added to both list endpoints: `source=WEB|ANDROID` (optional).

### Extended: GET /api/v1/audit/logs

Login-history and user-activity rows in the combined list carry the same `source`, `appVersion` and
`location` fields as above (and `deviceRooted` for login-history rows). API Access entries are not
part of this list.

### New: GET /api/v1/audit/api-access

**Authorization**: permission module `AUDIT_API_ACCESS`, action View (Admin and System by default).
Others get 403.

**Query**: `userId`, `from`, `to` (ISO instants, `to` not before `from`), `locationStatus`,
`httpMethod`, `page`, `size` (same pagination rules as the other audit lists).

**Response 200**:

```json
{
  "items": [
    {
      "id": "...",
      "occurredAt": "2026-10-04T09:30:12Z",
      "userId": "...",
      "sessionId": "...",
      "httpMethod": "GET",
      "routeTemplate": "/api/v1/me/access-model",
      "statusCode": 200,
      "source": "ANDROID",
      "appVersion": "1.0.0",
      "location": { "status": "AVAILABLE", "latitude": 12.971599, "longitude": 77.594566,
                    "accuracyMeters": 18.5, "capturedAt": "2026-10-04T09:30:10Z" }
    }
  ],
  "page": 0, "size": 50, "totalElements": 1234
}
```

**Response 400**: `to` before `from`, or an unknown `locationStatus`.

### New: GET /api/v1/audit/api-access/export

CSV of the same rows and filters, with columns `occurred_at`, `user_id`, `session_id`,
`http_method`, `route_template`, `status_code`, `source`, `app_version`, `location_status`,
`latitude`, `longitude`, `accuracy_meters`, `location_captured_at`. Same authorization.

### Not exposed

No endpoint returns location to a Teacher, Manager or Director, and no endpoint returns raw
location for any user other than through the audit routes above (login history, user activity, logs
and API access). The planned heat map is a later
spec and will add its own System-only permission and endpoint.
