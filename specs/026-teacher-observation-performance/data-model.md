# Data Model: Teacher Observation and Performance

Migration `V60__create_performance.sql`. All tables carry `id uuid` (primary key), `created_at`, and where editable
`version bigint` for optimistic locking. No foreign key crosses a module: `teacher_id`, `school_id`, user ids and file ids are
plain uuids. Dates are business dates (Asia/Kolkata).

## observation

One row per version of an observation (research.md section 4).

| Column | Type | Notes |
| --- | --- | --- |
| root_id | uuid | same for all versions; equals `id` of version 1 |
| version_no | int | 1, 2, ... |
| current | boolean | exactly one true per root (partial unique index) |
| teacher_id, school_id | uuid | School where the visit happened (kept if the Teacher moves later) |
| observer_user_id, observer_name | uuid, text | who observed; name kept as written |
| manager_user_id | uuid | the School's Manager at the time, may be null for an Admin or Director visit |
| observed_on | date | |
| class_name, subject | text | up to 100 each, free text |
| duration_minutes | int | 5 to 240; null while Draft |
| status | text | DRAFT, SUBMITTED, NOT_HELD, SUPERSEDED |
| not_held_reason | text | PRINCIPAL_DECLINED, PRINCIPAL_ABSENT, TEACHER_ABSENT, SCHOOL_CLOSED, OTHER; null unless NOT_HELD |
| not_held_text | text | required when reason is OTHER |
| overall_result | text | GOOD, NEEDS_IMPROVEMENT, UNSUITABLE; required to submit |
| average_rating | numeric(3,1) | computed from the rated areas on save |
| suggested_result | text | computed guide, not authoritative |
| comments, strengths, improvements, principal_comments | text | each up to 2000 |
| permission_by, permission_way, permission_on | text, text, date | all null when not recorded; way is IN_PERSON, PHONE, MESSAGE, WRITTEN |
| issue_id | uuid | the performance issue it opened or joined, if any |
| followup_of_issue_id | uuid | set when it is a follow-up observation for an issue |
| correction_reason | text | required on versions 2 and up |
| submitted_at | timestamptz | |

Indexes: (teacher_id, observed_on desc) where current; (school_id, status); (observer_user_id); unique (root_id) where current.

## observation_rating

`observation_id`, `area` (ENGLISH_COMMUNICATION, TEACHING_ABILITY, CONTENT_DELIVERY, CLASSROOM_MANAGEMENT, STUDENT_ENGAGEMENT,
PREPARATION, CONFIDENCE, PUNCTUALITY), `rating` (1 to 5, null for Not observed). Primary key (observation_id, area). Submit
needs at least one non-null rating.

## observation_attachment

`observation_id`, `file_id` (uuid from `files`), `name`, `added_by`, `added_at`. At most 5 per observation root; a correction
version carries the same files.

## principal_feedback

Versioned the same way as `observation` (`root_id`, `version_no`, `current`).

| Column | Type | Notes |
| --- | --- | --- |
| teacher_id, school_id | uuid | |
| principal_name | text | defaulted from the School contact |
| given_on | date | |
| category | text | GOOD_PERFORMANCE, NEEDS_IMPROVEMENT, ATTENDANCE, TEACHING, COMMUNICATION, BEHAVIOUR, OTHER |
| comments | text | 10 to 2000 |
| action_required | boolean | |
| observation_root_id | uuid | optional link |
| recorded_by | uuid | |
| status | text | RECORDED, WITHDRAWN |
| withdrawn_reason, withdrawn_by, withdrawn_at | | |
| issue_id | uuid | the issue it opened or joined |
| correction_reason | text | versions 2 and up |

## performance_issue

| Column | Type | Notes |
| --- | --- | --- |
| teacher_id, school_id | uuid | School at the time it opened |
| manager_user_id | uuid | Manager when opened, informational |
| source_type, source_root_id | text, uuid | OBSERVATION or FEEDBACK |
| category | text | taken from the source (result or feedback category) |
| status | text | OPEN, UNDER_CORRECTION, REFERRED, RESOLVED, CANCELLED |
| resolution | text | IMPROVED, CONTINUE, TRANSFERRED, REPLACED, EXITED; set when RESOLVED |
| correction_rounds | int | count of corrective actions, shown on the issue |
| opened_by, opened_on | | |
| closed_by, closed_on, closing_reason | | reason required for Cancelled and for Resolved - continue |
| version | bigint | |

Unique partial index on `teacher_id` where status in (OPEN, UNDER_CORRECTION, REFERRED): one open issue per Teacher (spec edge
case; new Needs improvement results join it).

## issue_link

`issue_id`, `record_type` (OBSERVATION, FEEDBACK), `record_root_id`, `linked_on`. Lets one issue hold several observations and
feedback records and lets a referral list its supporting records.

## corrective_action

`issue_id`, `round_no`, `discussed`, `required_change` (each up to 2000), `follow_up_on` (date, 1 to 60 days after recording),
`recorded_by`, `recorded_on`, `follow_up_source_key` (`{issueId}:{round}`), `follow_up_observation_root_id` (set when the
follow-up is submitted). Unique (issue_id, round_no).

## referral

`issue_id` (unique), `recommendation` (TRANSFER, REPLACE), `reason` (20 to 2000), `referred_by`, `referred_on`,
`decision_outcome` (TRANSFERRED, REPLACED, null), `decision_reported_on`. Supporting records are the issue's links.

## observation_plan

`teacher_id`, `school_id`, `planned_on`, `reason` (SCHOOL_FEEDBACK, MANAGEMENT_PLAN, FOLLOW_UP, OTHER), `reason_text` (required
for OTHER), `planned_by`, `source_feedback_root_id`, `issue_id` (for follow-ups), `task_source`, `task_source_key`, `status`
(OPEN, DONE, CANCELLED), `completed_by_observation_root_id`, `version`. Unique partial index on `teacher_id` where status =
OPEN: one open plan per Teacher.

## performance_history

Append-only (trigger refuses UPDATE and DELETE). `record_type`, `record_id`, `teacher_id`, `event` (CREATED, SUBMITTED,
CORRECTED, NOT_HELD, WITHDRAWN, PLANNED, REPLANNED, ISSUE_OPENED, CORRECTIVE_ACTION, FOLLOW_UP_DONE, REFERRED, RESOLVED,
CANCELLED, REASSIGNED, DECISION_REPORTED), `actor_user_id`, `actor_name`, `at`, `before_json`, `after_json`, `note`. Every
event is also sent to Change History through `ChangeRecorder`.

## State transitions

```text
Observation: DRAFT -> SUBMITTED -> (correction) new version SUBMITTED, old SUPERSEDED
             DRAFT -> NOT_HELD (reason, no ratings)
Issue:       OPEN -> UNDER_CORRECTION (corrective action) -> UNDER_CORRECTION (next round) 
             UNDER_CORRECTION | OPEN -> RESOLVED/IMPROVED (follow-up result Good)
             OPEN | UNDER_CORRECTION -> REFERRED (referral) -> RESOLVED/TRANSFERRED | REPLACED (027) | CONTINUE (Director)
             any open state -> CANCELLED (Admin or Director, reason) | RESOLVED/EXITED (Teacher exits)
Plan:        OPEN -> DONE (observation submitted) | CANCELLED
```

## Validation summary

Duration 5 to 240; ratings 1 to 5 or Not observed; submit needs at least one rating, an overall result, and strengths or
improvements; free text up to 2000; feedback comments 10 to 2000; referral reason at least 20; follow-up 1 to 60 days ahead;
at most 5 attachments; a Teacher who is not ACTIVE or not placed cannot be planned; a Teacher with no Manager cannot be planned.
