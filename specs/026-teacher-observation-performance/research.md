# Research: Teacher Observation and Performance

Decisions taken while planning, with what was read in the code. Format: Decision, Rationale, Alternatives.

## 1. What the code offers today

- `tasks.api.TaskCreation` has one operation, `createFollowUp`, idempotent per (`source`, `sourceKey`) while a task is open,
  and refuses an assignee who is not an active Manager. The task types `OBSERVATION` and `PRINCIPAL_FEEDBACK` already exist.
  Changing the date, completing, cancelling or reassigning is reachable only through `TaskService` with a user caller.
- There is no event when a School's Manager changes, when a Teacher is placed or moved, or when a Teacher exits. A Teacher's
  placement is read from `TeacherPlacementSource` (owned by `teacher`, implemented by `schoolbilling`); the Manager of a School
  is read through `organization.api`.
- Migrations on main stop at V53. Notification type checks are already a pattern (V50), so new types need no table change.
- The Principal's name comes from `school.api.SchoolContacts.contactsOf`.

## 2. Task module amendment (C2)

**Decision**: add four operations to `TaskCreation`, all addressed by (`source`, `sourceKey`) and run as the system with an
actor note: `reschedule(source, key, dueOn, title, description)`, `complete(source, key, note, completedByUserId)`,
`cancel(source, key, reason)` and `reassign(source, key, assigneeUserId)`. Each is a no-op returning false when no open task
matches. They write task history and Change History the way `createFollowUp` does, and `reassign` publishes `TaskAssigned`
to the new assignee.

**Rationale**: spec 026 FR-015 to FR-017 and FR-020 need them, and 027 and 029 will too. Addressing by source key keeps
`performance` free of task ids and lets a second call be idempotent.
**Alternatives**: call `TaskService` with a fake caller (breaks the module boundary); store task ids in `performance` and call
a new id-based API (more state to keep in step, no gain).

## 3. One module, many records

**Decision**: a single `performance` package with Observation, Principal Feedback, Performance Issue (with Corrective Actions
and Referral) and Observation Plan.
**Rationale**: they share the Teacher page, the scope rule, the history table and the issue loop; splitting would need
cross-module calls on every save (an observation opens an issue in the same transaction, FR-019).
**Alternatives**: the roadmap's separate "observation" and "performance" modules (rejected for the cross-calls above).

## 4. Versions and correction

**Decision**: a Submitted observation or feedback is never updated. A correction inserts a new row with the same `root_id`,
`version_no` plus one, a reason and the earlier values left intact; the latest row has `current = true` (a partial unique
index per `root_id`). Lists show current rows; Admin and Director can open the chain. Ratings sit in a child table
(`observation_rating`) copied to the new row.
**Rationale**: FR-025 and SC-006 say no earlier version is lost; DB-level append-only is simpler than diffing.
**Alternatives**: a JSON snapshot per change in history (harder to query and to show side by side).

## 5. Observation, issue and plan lifecycle

- Observation status: Draft, Submitted, Not held, Superseded (a corrected-away version). Draft is editable only by its observer.
  Not held needs a reason and no ratings, and does not complete the task (FR-008). Reason "Principal declined" is stored
  as a coded value so lists can flag and filter it (spec clarify 2026-10-21).
- Issue: Open to Under correction (corrective action) to Resolved (improved, continue, transferred, replaced, exited), or to
  Referred (referral), or Cancelled. A follow-up with result Needs improvement keeps the issue Under correction and asks for the
  next corrective action; the round number is the count of corrective actions.
- Plan: Open to Done (observation submitted) or Cancelled (issue cancelled, Teacher exits, or task cancelled).

**Rationale**: matches spec FR-018 to FR-024; the states are few enough for a plain enum and a transition check in the service.

## 6. Planning and the task bridge

**Decision**: planning calls `TaskCreation.createFollowUp` with type `OBSERVATION`, source `OBSERVATION_PLAN`, source key
`{teacherId}`, assignee the Teacher's Manager read at that moment, due the planned date, linked type `TEACHER`. If the call
returns `created = false`, the plan is updated and `reschedule` is called. A follow-up observation uses source
`PERFORMANCE_FOLLOW_UP` and key `{issueId}:{round}` so it never clashes with a general plan, and an open plan for the same
Teacher is reused instead of a second task (spec edge case: one open Observation task per Teacher), keeping the earlier
due date only if it is sooner.
**Rationale**: SC-002 and SC-004 (exactly one task) through the existing partial unique index.
**Alternatives**: two task kinds per Teacher (rejected: the spec says one open task per Teacher).

## 7. Manager change and Teacher exit

**Decision**: a `ManagerReconcileJob` runs daily (and the same method runs when the Planned list is opened by Admin or
Director): for each open plan it reads the Teacher's current Manager and calls `reassign` if different; for each open issue it
reads the Teacher's status and closes the issue as Resolved - exited and cancels its plan when the Teacher has exited.
**Rationale**: no event exists, and adding event publication to `organization`, `schoolbilling` and `teacher` is a wider
change than this spec needs. Spec FR-017 says "a change MUST reassign" and the edge case says "on the next check", so a daily
check with an on-open run meets both.
**Alternatives**: publish new events from the three modules (cleaner, bigger blast radius; can replace the job later without
changing the rule); poll on every request (cost with no benefit).

## 8. Scope

**Decision**: a `ScopeGuard` wraps `TeacherScopeQueries.teacherScope`. A record is visible to a Manager if its Teacher is in
scope today, or the Manager authored it (spec 6.2). Lists add a `teacher_id in (...)` or `author_id = :me` predicate. A single
record out of scope answers 404. Teacher and System never reach a controller: the grant check fails first.
**Rationale**: Principle III and SC-005; the same predicate serves lists, the Teacher page and the summary.

## 9. Free text and files

**Decision**: comments, strengths, areas, discussion and reasons are plain text up to 2000 characters, escaped on output. No
pupil fields exist. Attachments (up to 5, photo, PDF or document, size limit from `files`) use `FileStore`, are downloaded
only through `/observations/{id}/attachments/{fileId}` after the visibility check, and are served as attachments.

## 10. Rating guide

**Decision**: the average (rated areas only, one decimal) and the suggested result (4 or more Good, below 2.5 Unsuitable,
otherwise Needs improvement) are computed on the server in `ObservationService` and returned on every read; the stored result
is the observer's. The form shows both before submit through a `POST /observations/preview` that stores nothing.
**Rationale**: FR-004; one place for the rule, tested without the UI.

## 11. Interfaces for 027 and 028

**Decision**: `PerformanceReferrals` (`referredFor(teacherId)`, `referral(issueId)`, `reportDecision(issueId, TRANSFERRED |
REPLACED, actor)`) and `PerformanceSummary` (per Manager and per Zone for a period). Both are read-only except
`reportDecision`, which only closes a Referred issue. They return plain records, not entities, and apply no caller scope
because their callers (027, 028) apply their own.
**Rationale**: FR-023, FR-028, SC-009. Until 027 exists, the Director closes a Referred issue by hand (FR-024).

## 12. Notifications

**Decision**: two events, `ObservationPlanned(planId, teacherId, teacherName, managerUserId, dueOn, reason)` and
`ReferralRecorded(issueId, teacherId, teacherName, recommendation)`; `notification` adds types `OBSERVATION_PLANNED` (to the
Manager) and `REFERRAL_RECORDED` (to every active Director), with DD/MM/YYYY dates and links to the plan list and the issue.
Overdue and escalation are the task module's own events.

## 13. Not decided here

The mobile screens (a later spec), a Teacher-facing view (needs its own spec), and weighted scoring are out of scope as in the
spec.

## 14. As built (2026-10-21): changes from the decisions above

- **Task key.** An observation plan's task is addressed by the **plan's own id** (source `OBSERVATION_PLAN`), not by the Teacher id
  or `{issueId}:{round}`. A completed task stays live in the task module until it is verified, so a Teacher-keyed task would have
  blocked the next plan. One open plan per Teacher is kept by a partial unique index on `observation_plan`. A corrective action
  reuses the Teacher's open plan (kept at the earlier date) or makes its own; its `follow_up_source_key` records the plan's key.
- **A fifth C2 operation.** `TaskCreation.completeLinked(type, linkedType, linkedId, assigneeUserId, note)` completes the assignee's
  open tasks of one type linked to a record. The Principal feedback task is created by hand, so it has no source key to address it
  by; Principal feedback uses it with `PRINCIPAL_FEEDBACK` and linked type `TEACHER` (FR-016).
- **Interface names.** The 027 record type is `PerformanceReferrals.ReferralInfo`; `reportDecision` takes the outcome as the text
  `TRANSFERRED` or `REPLACED`.
- **Summary keys.** `PerformanceSummary.byManager` is keyed by the Manager's **user id** (the School's Manager when the record was
  made; an open plan belongs to the Teacher's Manager today); `byZone` by Zone id. Observations done, resolved issues and referrals
  fall in the period; planned and overdue observations and open issues are counted as they stand today.
- **Request shapes.** A correction is `POST /observations/{id}/correct` with `{reason, form}`; `POST /feedback/{id}/plan` answers 201
  when a plan was made and 200 when the open plan was changed; `GET /options` returns the coded values for the screens.
- **Validation order.** Request fields that a Teacher or System caller could trip over (for example `actionRequired`) are nullable
  wrappers, so the permission check always answers 403 before any 400.
