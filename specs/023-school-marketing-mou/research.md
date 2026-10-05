# Research: School Marketing and MoU Pipeline (spec 023)

Every open point is settled here. No `NEEDS CLARIFICATION` remains.

## 1. Stages: stored versus derived

- **Decision**: store Prospect, Contacted, Visit, Follow-up, Interested, Negotiation, Final Stage, On Hold and Lost,
  with `won_at`/`won_by` set when a Final Stage review approves. **MoU** and **Active** are derived at read time from
  spec 012: a prospect linked to a School that has a live contract (`SchoolContracts.contractOf`) is **MoU**; one whose
  contract has at least one filled position (`occupancyOf`) is **Active**. The board shows the derived stage for won
  prospects, so nothing can disagree with the contract (spec FR-009).
- **Allowed moves**: any move between the seven active stages (forward or back); On Hold from any active stage and back
  to the stage it left; Lost from any stage with a reason and reopen to the last active stage; Final Stage only when a
  proposal revision exists (the hand-off needs it); a rejected review returns to Negotiation with the reason.
- **Alternatives**: store MoU and Active and keep them in step by events (two truths, and 012 would need to publish
  events for every contract change).

## 2. Final Stage review (decision D5)

- **Decision**: a new action `APPROVE` on module `MARKETING`, seeded to Director and Manager, not Admin. The service also
  checks the Zone: a Zone Manager may approve only a prospect in a Zone they manage; a Director any. Approval sets
  `won_at`/`won_by` and records a stage-history row; a rejection records the reason and moves the prospect to
  Negotiation. A Manager may approve a prospect they own (D5 allows it); the audit entry names the approver.
- **Rationale**: keeping approval as a permission lets the business change who approves without code.

## 3. Winning creates the School; hand-off to 012

- **Contacts (amendment A8 of spec 016)**: a School keeps a principal and an accountant contact (`school.api.SchoolContacts`, table `school_contact`, merged with 016). The prospect's contact person is the default billing contact; the Win dialog also offers principal and accountant fields (name, phone, email, all optional) which are saved to the new School through the school module.
- **Decision**: after approval, an Admin or Director opens the Win dialog: choose a **Place** of the prospect's Zone and confirm the **billing contact** (prefilled from the prospect's contact; name, address, contact person and phone come from the prospect)
  (a School in 005 belongs to a Place) and confirm; `WinService` calls the new `school.api.SchoolRegistry.create`
  (which wraps `SchoolService.create`) and stores `school_id` on the prospect; if a School of the same name and Place
  already exists the user is asked to link it instead (the duplicate rule of the spec). The dialog then navigates to the
  School's contract page in spec 012 with the latest proposal in the router state; 012's MoU form accepts these as
  initial values (amendment A7, a small frontend change). The contract is recorded only in 012.
- A Zone Manager can approve but cannot create the School or record the MoU (spec Clarifications); the prospect shows
  "won, School and MoU to be recorded".
- **Alternatives**: create the School at the first contact (spec says only on a win); copy proposal data into the
  contract (spec forbids).

## 4. Contract status on the prospect (read only)

- **Decision**: `ContractStatusReader` calls `SchoolContracts.contractOf(schoolId, today)` for the MoU dates and
  positions, and the new `occupancyOf(schoolId, today)` (A6) for filled and vacant counts. If the 012 beans are absent
  the reader returns "not available" and the hand-off is hidden (FR-016); Spring `ObjectProvider` is used so the module
  starts without them.

## 5. Visits: status, outcome, reschedule

- **Decision**: stored status `PLANNED`, `COMPLETED`, `CANCELLED`; **missed** is derived (planned and the date has passed);
  **rescheduled** is an action that moves the date and appends a row to `activity_date_history` (the earlier date
  stays), shown as a badge. Completing needs an outcome (people met, discussion, next action) enforced in the service;
  cancelling needs a reason. A follow-up date on an outcome makes the prospect "follow-up overdue" after the date unless
  a later activity exists (spec scenario).

## 6. Attachments and the shared `files` module

- **Decision**: a new module `files` with `FileStore.store(ownerType, ownerId, name, contentType, bytes)`,
  `open(fileId)`, `remove(actor, fileId, reason)`. Bytes go to `hls.files.directory` under a generated name
  (`yyyy/MM/<uuid>`); `stored_file` keeps the original name, detected content type, size, SHA-256, who and when, and a
  `removed_at`, `removed_by` and `removal_reason` (a removal hides the file; the bytes are kept, so the history is
  complete). `ActivityAttachment` links a visit to a file id.
- **Policy**: allowed types jpg, png, pdf, docx, xlsx, txt (extension and magic-number check), maximum 10 MB, maximum
  10 files per visit; anything else is a 400. Served only through `GET /files/{id}` after a visibility check on the owner
  (the caller must be able to see the visit), as `attachment` with `nosniff`.
- **Why a module, not a package**: four specs use it (023 now; 015, 031, 032 later) and they must not reach into 023.
- **Alternatives**: database large objects (backups grow, streaming is harder); object storage (not in the
  single-VM deployment of the constitution).

## 7. Overdue MoU flag and notification

- **Decision**: `OverdueJob` (daily, `hls.marketing.overdue.enabled`, off in `IntegrationTestBase`) finds won prospects
  with no live contract and `won_at` older than the setting (default 14, range 1 to 90). For each it records a row in
  `overdue_notice(prospect_id, limit_days, noticed_at)` and publishes `WonProspectOverdue`. The unique key is
  `(prospect_id, limit_days)`, so a notice is not repeated unless the limit changes and is passed again. `notification`
  listens (like specs 010 and 012) and notifies the owner, the Zone Manager of the Zone (`managersOfZone`) and every
  active Admin and Director once. The board shows the flag from the same query, so it works with the job off.

## 8. Follow-up tasks by event (spec 025 does not exist yet)

- **Decision**: saving an outcome with a follow-up date publishes `FollowUpScheduled(activityId, prospectId,
  ownerUserId, dueOn, title)` from `recruitment.api`. Spec 025 will listen and create the task through its own
  service (contract C2). Until 025 exists nothing listens and the prospect only shows "follow-up overdue".
- **Alternatives**: call a task interface that does not exist (does not compile); a table of pending follow-ups that
  025 later reads (a second source of truth).

## 9. Proposals

- **Decision**: `proposal_revision` rows (revision number per prospect, Teacher count 1 to 500, start month, salary
  mode `SAME_FOR_ALL` or `PER_TEACHER`, a single amount or one `proposal_position` row per position, notes) are
  insert-only (a trigger rejects update and delete); the latest revision is current; the monthly total is computed
  (amount times count, or the sum of positions). Amounts are positive with two decimals. Always labelled "Proposal (not
  a contract)". This mirrors the MoU model of spec 012 so the hand-off pre-fill is a straight copy.

## 10. Dashboard and the supply figure

- **Decision**: one grouped query each for visits this month, prospects by stage, win rate (won divided by won plus
  lost, by Zone and owner), Schools won per Zone and owner. **Demand** is the sum of `occupancyOfAll(schoolIds, today).vacant` over won
  Schools with a live contract; the same batch call gives the derived MoU and Active stage of every won prospect, so the
  board and the dashboard never ask 012 once per prospect. **Supply** comes from `recruitment.api.SupplySource.readyToDeployCount()`, an interface
  defined here; a default bean returns "not available" and spec 016's `training` module provides the real one (a
  one-line addition listed in 016's tasks). A Zone Manager sees only their Zones.

## 11. Prospect duplicates, ownership and scope

- **Decision**: duplicate = same normalized (name, board, Zone) (`lower`, collapsed spaces); linking to an existing
  School of the same name and Place is offered when winning. Owner changes by Admin or Director are rows in
  `prospect_owner_history`. Scope: every query filters by the caller's Zones through `ScopeQueries` (Admin and Director
  org-wide); a prospect outside the scope is 404.

## 12. Planned activities and the incentive hook

- **Decision**: `MarketingActivities.plannedBetween(from, to, ownerUserId)` returns the contract-C1 records (kind
  `SCHOOL_VISIT`, `CALL`, `PROPOSAL_MEETING`, `FOLLOW_UP`). `ProspectOwners.ownerOfSchool(schoolId)` returns the owner
  of the won prospect linked to a School, for the incentive spec (030); this spec pays nothing.

## 13. Navigation and settings

- **Decision**: a MARKETING navigation section with Prospects, Calendar, Pipeline, Dashboard and Settings (Settings by the
  new permission `MARKETING_SETTINGS`, seeded to Admin and Director). Owner change, creating the School and removing an
  attachment are fixed Admin and Director rules in the service, as organization-level actions. The one setting (days until a won prospect without an MoU is
  flagged) is stored in `marketing_setting`; spec 011 may absorb it.
