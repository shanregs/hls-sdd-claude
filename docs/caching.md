# Caching of reference data

Small, read-mostly tables are kept in memory so that a request does not query them again and again. Everything
else (marks, leave requests, notifications, sessions, one-time codes, audit and the Teacher-month lock state) is
always read from the database, because it changes all the time or must be exact.

## What is cached

| Cache name | Table | Where | Why | Invalidated by |
| --- | --- | --- | --- | --- |
| `permissionMatrix` | `permission_matrix` | `PermissionMatrixService` | Read by every endpoint check and every menu item | `updateGrant`, `seedDefaults` |
| `attendanceStatusCodes` | `attendance_status_code` | `StatusCodeCatalog` | A dozen rows, reloaded by the grid, calendar, history, mark and month-lock code | `StatusCodeService.create`, `update` |
| `attendanceCalendar` | `attendance_calendar_setting`, `attendance_non_working_date` | `CalendarService.rules()` and `nonWorkingDates()` | Needed by every rollup, grid and leave count | the five write methods of `CalendarService` |
| `leaveTypes` | `leave_type` | `LeaveTypeCatalog` | Read for every leave list and application | nothing edits them yet; call `LeaveTypeCatalog.changed()` from any future editor |
| `otpPolicy` | `otp_policy_settings` | `OtpService` | One row, read for every code request | call `OtpService.policyChanged()` from the settings editor (spec 011) |

## How it works

`com.hls.cache.SnapshotCache` holds one immutable copy of a table.

- It loads on first use and serves from memory until it is invalidated or older than `hls.cache.ttl-seconds`.
- A writer calls `invalidateAround()` next to its write. That drops the copy at once and again when the writer's
  transaction ends, whether it committed or rolled back. A reader that loaded the data in between, inside the
  writer's transaction, therefore never keeps an uncommitted or rolled-back view.
- A load that raced with an invalidation is returned to its caller but not stored.
- Concurrent first readers share one load.

## Settings

| Property | Default | Meaning |
| --- | --- | --- |
| `hls.cache.enabled` | `true` | `false` makes every read go to the database |
| `hls.cache.ttl-seconds` | `60` | The longest a copy is served without a reload |

The time limit is the safety net for a change made by **another application instance** or by hand in the
database: in-process writes invalidate straight away, other instances catch up within the time limit. For a single
instance nothing else is needed. If the app is ever run on several instances and a change must be instant
everywhere, replace the in-memory copy with a shared cache (for example Redis) or publish an invalidation event.

## Rules for adding a cache

1. Cache only a small table that is read much more often than it is written.
2. The cached value must be immutable, or treated as read-only by every caller. Edits load their own copy from the
   database and then call `invalidateAround()`.
3. Every code path that writes the table must invalidate. If some code writes through the repository directly,
   route it through the service that owns the cache.
4. Name the cache and add a row to the table above.
5. Test it the way `ReferenceDataCacheTest` does: reads come from memory, a change is seen at once, a rolled-back
   change does not stay.

A test that changes a cached table with SQL must call `SnapshotCaches.invalidateAll()` afterwards.
