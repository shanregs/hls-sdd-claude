# Logical data model: 52 tables

One PostgreSQL schema. Real foreign keys everywhere. Naming: `snake_case`, `id` bigint identity (or UUID, decide in plan), `created_at`, `created_by`, `updated_at`. Money `numeric(12,2)`. Dates `date`. Timestamps `timestamptz` (UTC); business date is computed in IST.
Enums as Postgres enums or checked text. Key columns only; the plan adds the rest.

## Platform (8)
| Table | Key columns and rules |
|---|---|
| `app_user` | username, phone (unique), password_hash, employee_id (nullable FK), active, locked_until, failed_count, last_login_at |
| `user_role` | user_id, role (ADMIN, DIRECTOR, MANAGER, TEACHER, CONTENT_CREATOR, SYSTEM_ENGINEER). Unique (user_id, role) |
| `role_permission` | role, permission_key. Unique pair. Seeded by migration, edited by Admin. |
| `login_session` | user_id, token_hash, expires_at, last_seen_at |
| `setting` | key (pk), value, updated_by, updated_at |
| `audit_log` | at, user_id, action, entity_type, entity_id, before_json, after_json, reason |
| `notification` | user_id, event_key, entity_ref, text, created_at, read_at |
| `stored_file` | name, content_type, size, storage_path, uploader_id, created_at, owner_type, owner_id |

## Master data (9)
| Table | Key columns and rules |
|---|---|
| `zone` | name (unique) |
| `school` | name, address, city, pincode, zone_id, manager_id (FK employee), weekly_off_override, active |
| `school_contact` | school_id, name, designation, phone, email |
| `designation` | name, suggested_salary |
| `employee` | kind (TEACHER, MANAGER, ADMIN, DIRECTOR, SYSTEM_ENGINEER, CONTENT_CREATOR, SUBSTITUTE), employee_no (unique), name, phone, status (TRAINEE, ACTIVE, EXITED), designation_id, joining_date, exit_date, never deleted |
| `employee_zone` | employee_id, zone_id. Managers (one or more) and Substitutes (one). |
| `employee_salary` | employee_id, amount, effective_from, created_by. Append-only; the row in effect on a date is the latest with effective_from <= date. |
| `holiday` | date (unique), name |
| `school_month_days` | school_id, year, month, days (default calendar days, editable). Unique school+year+month. Used as the invoice divisor only. |

## Contracts (3)
| Table | Key columns and rules |
|---|---|
| `school_contract` | school_id, teacher_count, start_date, end_date, fee_mode (SAME, PER_POSITION), flat_fee, status (PENDING, APPROVED, ENDED), signatories, approved_by, approved_on, prospect_id (nullable FK), document_file_id. Partial unique: one APPROVED/active per school. |
| `contract_position` | contract_id, label, fee |
| `placement` | employee_id, position_id, school_id, start_date, end_date. No overlapping rows per employee (exclusion constraint). |

## Attendance and leave (4)
| Table | Key columns and rules |
|---|---|
| `attendance_mark` | employee_id, date, code (PRESENT, ABSENT, LEAVE, HALF_DAY, TRAINING_DAY), leave_id, note, marked_by. Unique (employee_id, date). |
| `manager_checkin` | employee_id, date, day_type (OFFICE, FIELD, LEAVE, ABSENT), purpose, in_at, in_lat, in_lng, out_at, out_lat, out_lng, edited_by, edit_reason. Unique (employee_id, date). |
| `month_lock` | kind (TEACHER, MANAGER), year, month, locked_by, locked_at, reopened_by, reopen_reason |
| `leave_request` | employee_id, type (CASUAL, SICK, PERSONAL, OTHER, LOP), from_date, to_date, half_day, status (PENDING, APPROVED, REJECTED, CANCELLED), decided_by, decision_reason, same_day_flag |

## Substitution (1)
| Table | Key columns and rules |
|---|---|
| `substitution` | school_id, teacher_id, date, source_leave_id, status (OPEN, ASSIGNED, MANAGER_COVER, CANCELLED), substitute_id (nullable), rate, school_informed, note, day_mark (PRESENT, ABSENT, null), marked_by. Unique (teacher_id, date). A Substitute cannot be booked twice on a date. |

## Recruitment (6)
`college`, `drive`, `candidate`, `offer`, `induction_batch`, `induction_member` (batch_id, employee_id, attendance counts, signed_off_by).

## Training (2)
`training_session` (date, topic, trainer, venue, audience NEW_JOINER or ONGOING), `training_attendee` (session_id, employee_id, attended).

## Marketing (2)
`prospect` (school_name, contact, stage, owner_manager_id, expected_teachers, proposed_terms, approved_by), `prospect_activity` (prospect_id, kind VISIT/CALL/MEETING, planned_on, status, outcome required on completion).

## Tasks (2)
`task` (assignee_id, creator_id, title, due_date, status, completion_note, late_reason, verified_by, source_type, source_id), `task_evidence` (task_id, file_id).

## Performance (2)
`observation` (teacher_id, school_id, observer_id, date, six ratings, comments, school_feedback, status DRAFT/SUBMITTED, result), `performance_followup` (observation_id, task_id, follow_up_date).

## Transfers (1)
`transfer_request` (teacher_id, from_school_id, to_school_id, effective_date, reason, category, replacement_teacher_id, status, approved_by).

## Payroll (3)
| Table | Key columns and rules |
|---|---|
| `payroll_run` | year, month (unique), status (DRAFT, FINALISED), finalised_by, finalised_at, acknowledged_by, acknowledged_at, ack_comment |
| `payroll_line` | run_id, employee_id, paid_days, unpaid_days, day_value, gross, lop, adjustments_total, net, status (COMPUTED, UNCOMPUTABLE), reason, paid_on, pay_mode, pay_ref |
| `payroll_adjustment` | employee_id, kind (INCENTIVE, REIMBURSEMENT, SUBSTITUTE_PAY, CORRECTION, MANUAL_LOP, OTHER), amount (signed), reason, run_id (null until applied), source_type, source_id. Partial unique: one INCENTIVE per School. |

## Expenses (2)
`expense_claim` (employee_id, date, amount, category, zone_id or head_office, reference_text, status, approver_id, reject_reason, paid_on, adjustment_id), `expense_bill` (claim_id, file_id).

## Billing (5)
`invoice` (school_id, year, month, invoice_date, due_date, total; unique school+year+month; immutable), `invoice_line` (invoice_id, kind PLACEMENT or UNCOVERED_DAY_CREDIT, placement_id, substitution_id nullable, days, fee, amount signed), `payment` (school_id, amount signed, date, mode, ref), `payment_allocation` (payment_id, invoice_id, amount), `invoice_adjustment` (invoice_id, amount signed, reason).

## Content (2)
`content_item` (title, owner_id, status), `content_version` (item_id, number, file_id, status, review_comment, reviewed_by).

## Rules at the database level
- Exclusion constraints stop overlapping placements, and a Substitute double-booked on one date.
- Append-only: `employee_salary`, `placement` history, `payroll_line` after finalise, `invoice`, `payment`. Enforce with triggers or no-update repository methods, and test.
- Derived values (overdue, absent today, not billed) are queries, not columns.
- No stored month rollups; totals are computed from marks.
- GST columns are intentionally absent (D-20).
- `otp_code` is intentionally absent; it is added when SMS sign-in is added (D-36).
