# Data Model: MoU Documents and Approval

Migration `V100__create_mou_approval.sql` (number to re-check at implementation; V90 is the highest on main). Every table has
`id uuid` primary key. Foreign keys to `contract` are allowed (same module). User ids and file ids are plain uuids (no foreign key
into `identity` or `files`). Dates are business dates (Asia/Kolkata); instants are `timestamptz`. The `contract`,
`contract_position`, `contract_signatory` and `contract_assignment` tables of spec 012 gain no column.

## mou_approval

One row per contract that has real terms. Created with the contract (`create`, `recordMou`) as DRAFT, or by the backfill below.

| Column | Type | Notes |
| --- | --- | --- |
| contract_id | uuid | unique, foreign key to `contract` |
| status | text | DRAFT, PENDING, APPROVED, REJECTED, APPROVED_LEGACY |
| round | int | number of the current or last submission, 0 while DRAFT, then 1, 2, ... |
| submitted_by, submitted_at | uuid, timestamptz | of the current round; for a reset caused by a replacement, the uploader |
| decided_by, decided_at | uuid, timestamptz | set on approve or reject; cleared when a new round starts |
| decision_reason | text | up to 500; required for REJECTED, null otherwise |
| version | bigint | optimistic lock; also bumped by a term edit |
| created_at, updated_at | timestamptz | |

Checks: `status = 'PENDING'` implies `submitted_by` and `submitted_at` not null and `decided_by` null; `status = 'REJECTED'`
implies `decided_by` and `decision_reason` not null; `status = 'APPROVED'` implies `decided_by` and `decided_at` not null;
`status = 'APPROVED_LEGACY'` implies `decided_by` null and `round = 0`; `round >= 0`. There is **no** check that
`decided_by <> submitted_by` (self approval is allowed).

Indexes: unique (contract_id); (status, submitted_at) for the pending list.

### Backfill (in the migration)

```sql
INSERT INTO mou_approval (id, contract_id, status, round, version, created_at, updated_at)
SELECT gen_random_uuid(), id, 'APPROVED_LEGACY', 0, 0, now(), now() FROM contract WHERE state = 'ACTIVE';
```

plus one `mou_approval_event` (`LEGACY_MARKED`, actor null) per row. `RATE_PENDING` contracts get no row (they have no terms);
a later `recordMou` creates the DRAFT row. `CANCELLED` contracts get none. Contracts created after the migration are DRAFT.

### Derived values (not stored)

- **Shown state**: see research.md section 5 (`NOT_APPLICABLE`, `CANCELLED`, `DRAFT`, `PENDING`, `APPROVED`,
  `APPROVED_WITHOUT_DOCUMENT`, `APPROVED_BEFORE_APPROVAL_STEP`, `REJECTED`; the public interface adds `NO_MOU`).
- **Document state**: `ATTACHED` when a `mou_document` of the contract has no removal, else `NONE`; current version = highest
  `version_no` without removal.
- **Self decided**: `decided_by = submitted_by` for the round (shown in the history).

### State transitions

```
create / recordMou            -> DRAFT
DRAFT    --submit-->             PENDING   (round 1)
PENDING  --approve-->            APPROVED
PENDING  --reject (reason)-->    REJECTED
REJECTED --submit-->             PENDING   (round + 1)
APPROVED --new document-->       PENDING   (round + 1, submitter = uploader)
backfill                      -> APPROVED_LEGACY   (final)
```

PENDING may have its commercial terms edited by an approver (version-checked); a new document on a PENDING or REJECTED MoU only
adds a history entry. APPROVED_LEGACY and CANCELLED contracts are never submitted or decided.

## mou_document

One row per uploaded version. Written once; only the removal columns may be set, once.

| Column | Type | Notes |
| --- | --- | --- |
| contract_id | uuid | foreign key to `contract` |
| version_no | int | 1, 2, 3 per contract; unique with `contract_id` |
| file_id | uuid | `stored_file.id` in the `files` module (owner type `MOU_DOCUMENT`, owner id the contract) |
| file_name, content_type, size_bytes | text, text, bigint | copied from the stored file so history reads without the store |
| added_by, added_at | uuid, timestamptz | |
| removed_by, removed_at, removal_reason | uuid, timestamptz, text | all three set together by a removal; reason up to 300 (the store limit) |

Indexes: unique (contract_id, version_no); unique (file_id).

A trigger refuses DELETE and any UPDATE other than setting the three removal columns while they are null. The current document
is derived (highest `version_no` where `removed_at` is null).

## mou_approval_event (append-only)

| Column | Type | Notes |
| --- | --- | --- |
| contract_id | uuid | foreign key to `contract` |
| round | int | the approval round the event belongs to |
| action | text | DOCUMENT_ADDED, DOCUMENT_REPLACED, DOCUMENT_REMOVED, SUBMITTED, RESUBMITTED, APPROVED, REJECTED, RESET_TO_PENDING, TERMS_AMENDED, LEGACY_MARKED |
| from_status, to_status | text | null for events that do not change status |
| actor_user_id | uuid | null only for LEGACY_MARKED |
| actor_role | text | ADMIN, DIRECTOR, MANAGER |
| document_version | int | for document events |
| note | text | reason (reject, removal), or a one-line summary |
| detail | text | for TERMS_AMENDED: `field: old -> new` lines |
| at | timestamptz | |

A trigger refuses UPDATE and DELETE (as `performance_history` in 026). Every row that matters is also written to Change History
through `ChangeRecorder` with entity types `MOU_APPROVAL` (status, round, decision) and `MOU_DOCUMENT` (added, removed), and
`CONTRACT` for the amended fields, always with actor, old and new values. A self approval writes the field `selfDecided = true`.

## Function and trigger changes in V100

`contract_signed_rows_are_final()` stays for `contract_signatory`. A new function `contract_position_guard()` replaces the
trigger on `contract_position`: DELETE refused; UPDATE allowed only when `id`, `contract_id`, `number` and `title` are unchanged and
the new `salary` is above zero **and** `mou_approval.status = 'PENDING'` for `NEW.contract_id`; otherwise it raises the same
kind of error as today.

## Validation rules (applied by the services, mirrored by checks where cheap)

- Upload: contract exists and is in scope; caller holds `EDIT`; extension pdf, jpg, jpeg or png; non-empty; at most 10 MB;
  content matches the type (`FileStore`); contract not CANCELLED is **not** required for upload (a cancelled MoU's documents stay
  readable, and a late upload is harmless) but the placeholder (RATE_PENDING) refuses with "record the MoU terms first".
- Remove: caller holds `EDIT` and is Admin or Director by scope (never a Manager); reason required, at most 300 characters.
- Submit: caller holds `EDIT`; contract has real terms and is not CANCELLED; status DRAFT or REJECTED; version matches.
- Approve and reject: see research.md section 7; reject reason required, at most 500 characters.
- Amend terms: see research.md section 8; at least one field must change; nothing changes on failure.

## Read models (no tables)

- **Approval detail** of a contract: derived state, document list with versions, history events, allowed actions.
- **Pending list**: PENDING approvals joined to contract, School name, Zone Manager name, submitter name, current document flag;
  filtered by the caller's scope.
- **List columns**: `approvalState`, `documentState` per contract row, from two batch lookups.
- **`MouApprovalState`** for other modules (contracts/module-interfaces.md).
