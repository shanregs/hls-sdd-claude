# Data Model: Notifications

Migration `V18__create_notification_table.sql`. Plain ids, no cross-module foreign keys (house rule).

## notification

| Column | Type | Notes |
| --- | --- | --- |
| id | uuid PK | |
| recipient_user_id | uuid not null | the user who sees it (from the JWT on every read) |
| type | varchar(30) not null | LEAVE_DECIDED, LEAVE_REQUESTED, LEAVE_CANCELLED, ATTENDANCE_CHANGED, ATTENDANCE_MONTH_LOCKED, ATTENDANCE_MONTH_REOPENED (check constraint) |
| title | varchar(80) not null | short headline |
| message | varchar(300) not null | one or two sentences; reasons shortened to 200 characters |
| link | varchar(200) null | an app route only (must start with `/`) |
| channel | varchar(10) not null default 'IN_APP' | check in ('IN_APP'); room for SMS/PUSH later |
| group_key | varchar(120) null | merge key, `attendance:{actor}:{teacher}:{yyyy-MM}`; null for events that never merge |
| detail | varchar(600) null | for merged attendance changes: comma-separated ISO dates |
| created_at | timestamptz not null | first creation; retention uses it |
| updated_at | timestamptz not null | last merge; the 10-minute window uses it; equals created_at when never merged |
| read_at | timestamptz null | null while unread |

Constraints and indexes:

- `CHECK (link IS NULL OR link LIKE '/%')`.
- Index `(recipient_user_id, created_at DESC)` for the list.
- Partial index `(recipient_user_id) WHERE read_at IS NULL` for the unread count.
- Partial index `(recipient_user_id, group_key) WHERE read_at IS NULL AND group_key IS NOT NULL` for the merge lookup.
- Index `(created_at)` for retention.

### Lifecycle

```
created (unread) --open / mark read--> read
created (unread) --same group within 10 min--> updated in place (still unread)
read | unread --delete / clear read--> removed
any --older than 90 days (created_at)--> removed by the daily job
```

A read notification is never merged into; the next change creates a new row.

## Derived values

- **Unread count**: `count(*) where recipient_user_id = ? and read_at is null` (the UI shows "99+" above 99).
- **Day count of a merged attendance notification**: the number of distinct dates in `detail`.

## Events (not stored; defined in the producers' `api` packages)

| Event | Published by | Fields |
| --- | --- | --- |
| LeaveRequested | leave (submit) | requestId, teacherId, schoolId, firstDate, lastDate, teacherName |
| LeaveDecided | leave (approve, reject, revoke) | requestId, teacherId, decision (APPROVED, REJECTED, REVOKED), firstDate, lastDate, reason |
| LeaveCancelled | leave (Teacher cancel) | requestId, teacherId, schoolId, firstDate, lastDate, teacherName, wasApproved |
| AttendanceMarkChanged | attendance (supervisor set, correct, clear) | teacherId, date, actorUserId, actorName |
| AttendanceMonthLocked | attendance (lock month) | teacherId, month, actorUserId |
| AttendanceMonthReopened | attendance (reopen) | teacherId, month, actorUserId, reason |

A supervisor cancelling or revoking is a `LeaveDecided` with decision REVOKED (the Teacher is the audience);
only a Teacher's own cancellation is `LeaveCancelled` (the supervisors are the audience).

## Seed data

No SQL seed. The permission module is seeded by `PermissionMatrixService.seedDefaults` (idempotent), demo
notifications by `NotificationDevSeeder`.
