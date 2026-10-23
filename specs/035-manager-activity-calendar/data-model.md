# Data Model: Manager Activity Calendar

The calendar module stores nothing. All stored changes below belong to the amended modules (migration
`V70__amend_planned_activity_sources.sql`). The rest are in-memory read models.

## Stored changes (amendments)

| Table | Change | Owner | Notes |
| --- | --- | --- | --- |
| `marketing_activity` | add `completed_on DATE NULL` | 023 (`recruitment.marketing`) | Set to the business date when a visit is completed; NULL for earlier rows and for planned or cancelled ones |
| `campus_drive` | add `held_on DATE NULL` | 016 (`recruitment`) | Set when a drive is marked held; NULL otherwise and for earlier rows |
| `drive_date_change` | new | 016 (`recruitment`) | `id UUID PK, drive_id UUID NOT NULL, old_date DATE NOT NULL, new_date DATE NOT NULL, changed_by UUID NOT NULL, changed_at TIMESTAMPTZ NOT NULL`; index on `drive_id`; trigger refuses UPDATE and DELETE; no foreign key across modules is needed (same module: FK to `campus_drive` is allowed) |

Unchanged but read: `activity_date_history` (023), `task` and `task_history` (025: `DUE_CHANGED` before and after values,
`completed_at`, `linked_type`, `linked_id`, `place`), `campus_drive` dates and interviewers.

Rules for writing `drive_date_change`: on a drive update, compute removed dates and added dates; when both sets are non-empty
and of equal size, sort both and write one row per pair; otherwise write nothing. Existing Change History entries are kept as is.

## Read models (in the calendar module, not persisted)

### CalendarEntry (what a source adapter returns)

`kind, id, date, originalDate?, ownerUserId, attendeeUserIds, place?, zoneId?, schoolId?, linkedType?, linkedId?, nativeStatus,
completedOn?` Built from the feed record; nothing else crosses the module boundary.

### CalendarItem (what the API returns)

| Field | Meaning |
| --- | --- |
| `key` | `kind:id:date` (unique per grid cell) |
| `activityId`, `kind` | `CAMPUS_DRIVE`, `SCHOOL_VISIT`, `CALL`, `MEETING`, `TASK`, `TRAINING`; visit types map as VISIT, CALL, PROPOSAL_MEETING and FOLLOW_UP to School visit, Call, Meeting, Meeting; every task type maps to Task (the task type is shown as `subKind`) |
| `date` | Current date |
| `status` | `PLANNED`, `DONE`, `MISSED`, `CANCELLED` |
| `doneLate`, `completedOn` | Done after the date |
| `rescheduled`, `originalDate`, `daysMoved` | Moved at least once; original is the first planned date |
| `daysLate` | For Missed: today minus date |
| `people` | `{userId, name, zoneName}` of the owner and each attendee who is a Manager; one entry per Manager used for per-Manager views |
| `zoneId`, `zoneName` | Resolved Zone or null ("No Zone") |
| `place` | Text or null ("No place recorded") |
| `link` | `{screen, path}` to the owning screen, present only when the caller holds that screen's View permission |

### CalendarSummary

`planned, done, doneLate, open, missed, cancelled, rescheduled`, each total and `byKind`; and `byManager` (Director and Admin
only). Totals count distinct `(kind, id)` once; a multi-date drive counts once, with the status of its last date. Invariant:
`planned = done + open + missed`; `cancelled` is outside `planned`.

### NeedsAttentionEntry

`item` (CalendarItem), `reason` (`MISSED` | `RESCHEDULED` | both), `daysLate`, `daysMoved`; grouped by Manager, oldest
first. An item moved twice and later missed appears once with both marks.

### SourceReport

`{kind group, available, read, note}` for DRIVES, VISITS, TASKS, TRAINING. TRAINING is `available:false` until 024 adds a
source. A source that throws is `available:true, read:false` and sets `partial=true` on the response.

## State rules

```text
nativeStatus (cancelled)                   -> CANCELLED
nativeStatus (completed|held|verified)     -> DONE   (doneLate when completedOn > date)
otherwise date < today (business date)     -> MISSED
otherwise                                  -> PLANNED
```

A MISSED item that is later completed or cancelled changes class on the next load (FR-010). A drive is judged on its last date.

## Validation

- `month` is `YYYY-MM`; a calendar range over 31 days or Needs attention over 93 days answers 400.
- `kind` in the six values, `status` in the four; unknown values answer 400.
- A Manager's `managerId` other than their own answers 404; `zoneId` narrows only.
