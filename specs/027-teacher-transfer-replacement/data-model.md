# Data Model: Teacher Transfer and Replacement

Migration `V56__create_teacher_movement.sql` (number to re-check at implementation). Every table has `id uuid` primary key. No
foreign key crosses a module: teacher, school, position, assignment and user ids are plain uuids. Dates are business dates
(Asia/Kolkata). Placement data itself is not copied: `contract_assignment` (module `schoolbilling`) is unchanged and gains no
column.

## movement_request

| Column | Type | Notes |
| --- | --- | --- |
| kind | text | TRANSFER, REPLACEMENT |
| teacher_id | uuid | the moving Teacher (transfer) or the Teacher being replaced (replacement) |
| from_school_id | uuid | the School the Teacher is placed in at the time of the request |
| to_school_id | uuid | transfer: the receiving School; replacement: same as `from_school_id` |
| position_id | uuid | transfer: the named receiving position, null if automatic or none; replacement: the position being filled, null if none |
| replacement_teacher_id | uuid | replacement only |
| outgoing_destination_school_id | uuid | replacement only: where the replaced Teacher goes, null means available |
| effective_on | date | today or later when raised or changed |
| reason_category | text | SCHOOL_REQUEST, TEACHER_REQUEST, MANAGER_REBALANCING, AFTER_REVIEW, OTHER |
| note | text | up to 1000; required for OTHER and for SCHOOL_REQUEST |
| status | text | PENDING, APPROVED, REJECTED, CANCELLED (APPLIED is derived, see below) |
| requested_by, requested_at | uuid, timestamptz | |
| decided_by, decided_at, decision_reason | uuid, timestamptz, text | set on approve, reject, or cancel; reason required for reject and cancel |
| applied_notified_at | timestamptz | set once by the daily job when the "took effect" notice went out |
| version | bigint | optimistic lock |
| created_at, updated_at | timestamptz | |

Checks: `kind = 'REPLACEMENT'` implies `replacement_teacher_id` is not null and `to_school_id = from_school_id`; `kind = 'TRANSFER'`
implies `replacement_teacher_id` and `outgoing_destination_school_id` are null and `to_school_id <> from_school_id`;
`decided_by <> requested_by` when status is APPROVED or REJECTED.

Indexes: (status, effective_on), (from_school_id), (to_school_id), (teacher_id).

### Derived status (not stored)

Shown status = APPLIED when stored status is APPROVED, `effective_on <= today` and every linked row is still ACTIVE; CANCELLED
(reason "Teacher no longer placed there") when a linked row is no longer ACTIVE; otherwise the stored status.

### State transitions

```
PENDING  --approve--> APPROVED --(date arrives, derived)--> APPLIED
PENDING  --reject---> REJECTED
PENDING  --cancel---> CANCELLED
APPROVED --cancel (date still in the future; rows reversed)--> CANCELLED
APPROVED --(linked row cancelled by another route; daily job)--> CANCELLED
```

PENDING may have its effective date, position, destination and note changed (version-checked); every change is a history row.
APPLIED, REJECTED and CANCELLED are final.

## movement_participant

One row per Teacher involved in an open request, so "one open request per Teacher" holds across roles.

| Column | Type | Notes |
| --- | --- | --- |
| request_id | uuid | |
| teacher_id | uuid | |
| role | text | MOVING, OUTGOING, REPLACEMENT |
| open | boolean | true while the request is PENDING; set false on any decision |

Partial unique index on `(teacher_id)` where `open`. The outgoing Teacher's own destination is the same Teacher, so it needs no
separate row.

## movement_assignment

Links an approved request to the placement rows it ended or created, so history joins and stale detection need no scan.

| Column | Type | Notes |
| --- | --- | --- |
| request_id | uuid | |
| assignment_id | uuid | `contract_assignment.id` (plain uuid) |
| teacher_id | uuid | |
| effect | text | ENDED (the row whose `ends_on` the move set), CREATED (a row the move inserted) |

Unique on `(request_id, assignment_id)`. Index on `assignment_id` for the history join.

## movement_request_history (append-only)

| Column | Type | Notes |
| --- | --- | --- |
| request_id | uuid | |
| action | text | RAISED, CHANGED, APPROVED, REJECTED, CANCELLED, APPLIED, CLOSED_STALE |
| from_status, to_status | text | |
| actor_user_id | uuid | null for the daily job |
| note | text | decision reason or a summary of what changed |
| at | timestamptz | |

A database trigger refuses UPDATE and DELETE (as `performance_history` in 026). Each row is also recorded through
`ChangeRecorder` with before and after values.

## Validation rules (applied by `MovementChecks`, on raise, on change and again on approve)

- Teacher is ACTIVE or ON_LEAVE, has an ACTIVE row covering the request date at `from_school_id`, and still has it on the
  effective date; the effective date is after that row's `starts_on`; there is no future ACTIVE row.
- Effective date is today or later; no non-leave mark on or after it; no locked attendance month from it (FR-004).
- Receiving School active; position (if the contract has positions) vacant on the effective date, or named and vacant; a
  PER_TEACHER contract requires a named position for a transfer.
- Replacement Teacher (and any other Teacher newly placed) is ACTIVE, has no ACTIVE row covering the effective date, no future
  row, and no open participation in another request.
- Reason note rules (FR-003). Rejection and cancellation require a reason. The decider is not the requester.

## Read models (no tables)

- **Teacher movement**: `contract_assignment` rows of a Teacher (via `TeacherPlacementSource.historyOf`) joined to
  `movement_assignment` and `movement_request`: School, from, to, kind (Initial assignment, Direct move, Transfer, Replacement),
  reason, requester, approver.
- **School movement**: the same join over the School's rows, with the other Teacher or School named.
- **Teacher filters**: `TeacherMovementSource.transferred()` and `replaced()` (see research.md section 10).
