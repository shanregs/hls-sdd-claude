# Simplified data model: ER diagrams and old-to-new table mapping

Companion to `schema.sql` (51 tables and one view, validated on PostgreSQL 16) and `docs/simplified-requirements.md` (taken from `origin/main`, not from the owner's uncommitted local edits). The diagrams show keys and the columns that matter for understanding; `schema.sql` has every column and constraint. Notation: `PK` primary key, `FK` foreign key, `UK` unique. Every line below is a real foreign key in the DDL.

Two conventions explain some columns you will see in `schema.sql` but not in the pictures:

- **Kind guard.** `employee` is one table for Teacher, Manager, staff and Substitute. Where a child must point at one kind only (a placement must be a Teacher, a Zone's manager must be a Manager), the child has a constant `*_kind` column and a composite foreign key `(id, kind)`. The database refuses a Manager being placed in a School. Pictures draw this as an ordinary relationship to `employee`.
- **Derived, not stored.** Overdue, absent today, not billed, invoice status and attendance month totals are queries. There is one view, `invoice_balance`.

## 1. Overview (all 51 tables)

```mermaid
erDiagram
    app_user ||--o{ login_session : "signs in"
    app_user ||--o{ notification : "receives"
    app_user ||--o| employee : "may be"
    zone ||--o{ school : contains
    zone ||--o{ employee : "home zone"
    employee ||--o{ employee_salary : "paid"
    designation ||--o{ employee : classifies
    school ||--o{ school_contract : "MoU"
    school_contract ||--o{ contract_position : seats
    contract_position ||--o{ placement : "filled by"
    employee ||--o{ placement : "Teacher placed"
    employee ||--o{ attendance_mark : "Teacher day"
    employee ||--o{ manager_checkin : "Manager day"
    employee ||--o{ leave_request : requests
    attendance_mark }o--o| leave_request : "written by"
    employee ||--o{ substitution : "absent / covers"
    payroll_run ||--o{ payroll_line : lines
    employee ||--o{ payroll_line : "paid in"
    employee ||--o{ payroll_adjustment : "adjusted"
    payroll_run ||--o{ payroll_adjustment : "applied in"
    school ||--o{ invoice : billed
    invoice ||--o{ invoice_line : lines
    invoice ||--o{ payment_allocation : "paid by"
    payment ||--o{ payment_allocation : splits
    invoice ||--o{ invoice_adjustment : credits
    employee ||--o{ expense_claim : claims
    expense_claim ||--o{ expense_bill : bills
    stored_file ||--o{ expense_bill : "file"
    college ||--o{ drive : hosts
    drive ||--o{ candidate : registers
    candidate ||--o{ offer : receives
    induction_batch ||--o{ induction_member : seats
    training_session ||--o{ training_attendee : invites
    zone ||--o{ prospect : "in zone"
    prospect ||--o{ prospect_activity : visits
    prospect ||--o{ school_contract : "won as"
    employee ||--o{ task : assigned
    task ||--o{ task_evidence : proof
    employee ||--o{ observation : "observed / observer"
    observation ||--o{ performance_followup : follows
    employee ||--o{ transfer_request : moves
    content_item ||--o{ content_version : versions
```

Not drawn above to keep it readable: `otp_code`, `setting`, `audit_log` (loosely referenced by `entity_type` and `entity_id`, deliberately without foreign keys), `school_contact`, `holiday`, `school_month_days`, `month_lock`, `expense_budget`. They appear in the module diagrams.

Table count by area (matches `docs/simplified-requirements.md` section 6): Platform 7, Master data 8, Contracts 3, Attendance and leave 4, Substitution 1, Recruitment 6, Training 2, Marketing 2, Tasks 2, Performance 2, Transfers 1, Payroll 3, Expenses 3, Billing 5, Content 2 = 51. MVP: 33 tables; Phase 2: 18 tables (the six recruitment, two training, two marketing, two task, two performance, one transfer, two content tables, plus `expense_budget`).

## 2. Module diagrams

### 2.1 Platform [MVP]

```mermaid
erDiagram
    app_user {
        bigint id PK
        text phone UK
        text username UK
        text password_hash
        text_array roles "ADMIN DIRECTOR MANAGER TEACHER CONTENT_CREATOR"
        boolean active
        smallint failed_attempts
        timestamptz locked_until
    }
    otp_code {
        bigint id PK
        bigint user_id FK
        text purpose "LOGIN or PASSWORD_RESET"
        timestamptz expires_at
        timestamptz consumed_at
    }
    login_session {
        bigint id PK
        bigint user_id FK
        text token_hash UK
        timestamptz expires_at
        timestamptz revoked_at
    }
    setting {
        text key PK
        text value
        bigint updated_by FK
    }
    audit_log {
        bigint id PK
        timestamptz at
        bigint actor_user_id FK
        text action
        text entity_type
        bigint entity_id
        jsonb before_data
        jsonb after_data
    }
    notification {
        bigint id PK
        bigint user_id FK
        text kind
        text message
        timestamptz read_at
    }
    stored_file {
        bigint id PK
        text original_name
        text content_type
        bigint size_bytes
        text storage_path UK
        bigint uploaded_by FK
        bigint replaces_file_id FK
        bigint observation_id FK
    }
    app_user ||--o{ otp_code : "gets"
    app_user ||--o{ login_session : "has"
    app_user ||--o{ notification : "receives"
    app_user ||--o{ audit_log : "acts"
    app_user ||--o{ setting : "updates"
    app_user ||--o{ stored_file : "uploads"
    stored_file |o--o| stored_file : "replaces (MoU versions)"
```

### 2.2 Master data [MVP]

```mermaid
erDiagram
    zone {
        bigint id PK
        text name UK
        bigint manager_id FK "employee of kind MANAGER"
    }
    school {
        bigint id PK
        text name
        bigint zone_id FK
        bigint manager_id FK "null = zone manager"
        text city
        text pincode
        smallint_array weekly_offs "null = organisation default"
    }
    school_contact {
        bigint id PK
        bigint school_id FK
        text role
        text name
        text phone
    }
    designation {
        bigint id PK
        text name UK
        text kind
        numeric suggested_salary
        numeric substitute_day_rate
    }
    employee {
        bigint id PK
        text kind "TEACHER MANAGER STAFF SUBSTITUTE"
        text employee_code UK
        bigint user_id FK "UK, null for Substitutes"
        bigint designation_id FK
        bigint zone_id FK
        text status "IN_TRAINING ACTIVE EXITED"
        date joining_date
        date exit_date
        numeric day_rate "Substitutes only"
    }
    employee_salary {
        bigint id PK
        bigint employee_id FK
        numeric monthly_salary
        date effective_from "UK with employee_id"
        bigint set_by FK
    }
    holiday {
        bigint id PK
        date holiday_date
        bigint school_id FK "null = every School"
    }
    school_month_days {
        bigint school_id PK
        smallint year PK
        smallint month PK
        numeric working_days
    }
    zone ||--o{ school : contains
    zone ||--o{ employee : "home zone"
    employee |o--o{ zone : "heads"
    employee |o--o{ school : "manages"
    school ||--o{ school_contact : has
    school |o--o{ holiday : "own holidays"
    school ||--o{ school_month_days : "working days"
    designation |o--o{ employee : classifies
    employee ||--o{ employee_salary : "dated, append-only"
```

### 2.3 Contracts and placement [MVP]

```mermaid
erDiagram
    school_contract {
        bigint id PK
        bigint school_id FK
        smallint teacher_count
        date start_date
        date end_date
        text fee_mode "UNIFORM or PER_POSITION"
        numeric uniform_monthly_fee
        bigint document_file_id FK
        text status "PENDING APPROVED ENDED"
        bigint approved_by FK
        bigint incentive_owner_id FK
        bigint prospect_id FK
    }
    contract_position {
        bigint id PK
        bigint contract_id FK
        bigint school_id FK "same School, enforced"
        smallint position_no "UK with contract_id"
        numeric monthly_fee
    }
    placement {
        bigint id PK
        bigint employee_id FK "Teacher only"
        bigint position_id FK
        bigint school_id FK
        date start_date
        date end_date "null = current"
        text end_reason
    }
    school ||--o{ school_contract : "MoU (one live)"
    school_contract ||--o{ contract_position : seats
    contract_position ||--o{ placement : "filled by (no overlap)"
    employee ||--o{ placement : "one School at a time"
    stored_file |o--o{ school_contract : "signed PDF"
    prospect |o--o{ school_contract : "won prospect"
    employee |o--o{ school_contract : "incentive owner"
```

### 2.4 Attendance and leave [MVP]

```mermaid
erDiagram
    attendance_mark {
        bigint id PK
        bigint employee_id FK "Teacher only"
        bigint school_id FK
        date mark_date "UK with employee_id"
        text status "PRESENT ABSENT LEAVE HALF_DAY TRAINING"
        bigint leave_request_id FK
        bigint marked_by FK
    }
    leave_request {
        bigint id PK
        bigint employee_id FK "Teacher or Manager"
        text leave_type
        date from_date
        date to_date
        numeric days
        text status "PENDING APPROVED REJECTED CANCELLED REVOKED"
        boolean same_day
        bigint decided_by FK
    }
    manager_checkin {
        bigint id PK
        bigint employee_id FK "Manager only"
        date work_date "UK with employee_id"
        text day_type "OFFICE FIELD LEAVE"
        text purpose
        timestamptz check_in_at
        timestamptz check_out_at
    }
    month_lock {
        bigint id PK
        text kind "TEACHER or MANAGER"
        smallint year
        smallint month
        boolean is_locked
        text reopen_reason
    }
    employee ||--o{ attendance_mark : "Teacher day"
    school ||--o{ attendance_mark : "placed in"
    employee ||--o{ leave_request : requests
    leave_request |o--o{ attendance_mark : "approval writes"
    employee ||--o{ manager_checkin : "Manager day"
    app_user ||--o{ leave_request : decides
```

### 2.5 Substitution and payroll [MVP]

```mermaid
erDiagram
    substitution {
        bigint id PK
        bigint absent_employee_id FK "Teacher only"
        bigint school_id FK
        date absence_date "UK with absent_employee_id"
        text status "OPEN ASSIGNED MANAGER_COVER CANCELLED"
        bigint substitute_id FK "Substitute only"
        boolean school_informed
        text day_status "PRESENT or ABSENT"
        numeric day_rate "snapshot"
        bigint paid_in_run_id FK
    }
    payroll_run {
        bigint id PK
        smallint year "UK with month"
        smallint month
        text status "DRAFT or APPROVED"
        bigint started_by FK
        bigint approved_by FK
    }
    payroll_line {
        bigint id PK
        bigint run_id FK
        bigint employee_id FK "UK with run_id"
        numeric monthly_salary
        numeric payable_days
        numeric lop_days
        numeric earned_pay
        numeric lop_amount
        smallint substitute_days
        numeric net_pay
        text flag_reason
        text paid_status
    }
    payroll_adjustment {
        bigint id PK
        bigint employee_id FK
        text kind "INCENTIVE REIMBURSEMENT CORRECTION MANUAL"
        numeric amount "signed"
        text status "PENDING APPROVED REJECTED APPLIED"
        bigint school_id FK "one INCENTIVE per School"
        bigint expense_claim_id FK
        bigint applied_run_id FK
    }
    employee_salary }o--|| employee : "salary in effect"
    payroll_run ||--o{ payroll_line : lines
    employee ||--o{ payroll_line : "paid"
    employee ||--o{ payroll_adjustment : "adjusted"
    payroll_run |o--o{ payroll_adjustment : "applied in"
    payroll_run |o--o{ substitution : "pays the day"
    employee ||--o{ substitution : "absent / covering"
    school ||--o{ substitution : "for School"
    leave_request |o--o{ substitution : "raised by"
    expense_claim |o--o| payroll_adjustment : "reimbursed via"
```

### 2.6 Billing [MVP]

```mermaid
erDiagram
    invoice {
        bigint id PK
        text invoice_no UK
        bigint school_id FK "UK with year, month"
        bigint contract_id FK
        smallint year
        smallint month
        date raised_on
        date due_date
        numeric total_amount "immutable"
    }
    invoice_line {
        bigint id PK
        bigint invoice_id FK
        bigint placement_id FK "null = manual line"
        numeric monthly_fee
        smallint billable_days
        numeric amount
    }
    payment {
        bigint id PK
        bigint school_id FK
        numeric amount "negative = reversal"
        date paid_on
        text mode
        bigint reverses_payment_id FK
    }
    payment_allocation {
        bigint id PK
        bigint payment_id FK "UK with invoice_id"
        bigint invoice_id FK
        numeric amount
    }
    invoice_adjustment {
        bigint id PK
        bigint invoice_id FK
        numeric amount "credit"
        text reason
    }
    invoice_balance {
        bigint invoice_id "VIEW"
        numeric balance
    }
    school ||--o{ invoice : billed
    school_contract ||--o{ invoice : "under"
    invoice ||--o{ invoice_line : lines
    placement |o--o{ invoice_line : "billed seat"
    school ||--o{ payment : pays
    payment |o--o| payment : "reversed by"
    payment ||--o{ payment_allocation : splits
    invoice ||--o{ payment_allocation : "paid by"
    invoice ||--o{ invoice_adjustment : credits
    invoice ||--|| invoice_balance : "derived"
```

### 2.7 Expenses [MVP; budget is Phase 2]

```mermaid
erDiagram
    expense_claim {
        bigint id PK
        bigint claimant_id FK
        date claim_date
        numeric amount
        text category
        bigint zone_id FK "null when head_office"
        boolean head_office
        text status "SUBMITTED APPROVED REJECTED PAID"
        bigint decided_by FK
        text paid_via "PAYROLL or DIRECT"
    }
    expense_bill {
        bigint id PK
        bigint claim_id FK
        bigint file_id FK "UK"
    }
    expense_budget {
        bigint id PK
        bigint zone_id FK "null = head office"
        smallint year
        smallint month
        numeric amount
    }
    employee ||--o{ expense_claim : claims
    zone |o--o{ expense_claim : "area"
    expense_claim ||--o{ expense_bill : bills
    stored_file ||--o| expense_bill : "file"
    zone |o--o{ expense_budget : budget
```

### 2.8 Recruitment and training [Phase 2]

```mermaid
erDiagram
    college {
        bigint id PK
        text name UK
        text placement_officer
        text principal_name
    }
    drive {
        bigint id PK
        bigint college_id FK
        date drive_date
        text status
    }
    candidate {
        bigint id PK
        bigint drive_id FK
        text phone "UK with drive_id"
        text outcome
        bigint employee_id FK "Teacher made on acceptance, UK"
    }
    offer {
        bigint id PK
        bigint candidate_id FK
        numeric monthly_salary
        date deadline
        text status "ISSUED ACCEPTED DECLINED EXPIRED REPLACED"
    }
    induction_batch {
        bigint id PK
        text name UK
        date start_date
        smallint seat_limit
        text status
    }
    induction_member {
        bigint batch_id PK
        bigint employee_id PK
        smallint days_attended
        boolean signed_off
    }
    training_session {
        bigint id PK
        date session_date
        text topic
        text audience
    }
    training_attendee {
        bigint session_id PK
        bigint employee_id PK
        boolean attended
    }
    college ||--o{ drive : hosts
    drive ||--o{ candidate : registers
    candidate ||--o{ offer : receives
    candidate |o--o| employee : "becomes Teacher"
    induction_batch ||--o{ induction_member : seats
    employee ||--o{ induction_member : "in training"
    training_session ||--o{ training_attendee : invites
    employee ||--o{ training_attendee : attends
```

### 2.9 Marketing, tasks, performance, transfers [Phase 2]

```mermaid
erDiagram
    prospect {
        bigint id PK
        text school_name
        bigint zone_id FK
        bigint owner_id FK "Manager only"
        text stage
        numeric proposed_fee
        bigint final_approved_by FK
        bigint school_id FK
    }
    prospect_activity {
        bigint id PK
        bigint prospect_id FK
        text kind
        date planned_for
        text status
        text outcome "required when DONE"
    }
    task {
        bigint id PK
        text task_type
        bigint assignee_id FK "Manager only"
        bigint school_id FK
        bigint teacher_id FK
        date due_date
        text status
        bigint verified_by FK
    }
    task_evidence {
        bigint id PK
        bigint task_id FK
        bigint file_id FK
    }
    observation {
        bigint id PK
        bigint teacher_id FK "Teacher only"
        bigint observer_id FK "Manager only"
        bigint school_id FK
        date observed_on
        text status "DRAFT or SUBMITTED"
        text suggested_result
    }
    performance_followup {
        bigint id PK
        bigint observation_id FK
        text issue_summary
        bigint followup_observation_id FK
        bigint task_id FK
    }
    transfer_request {
        bigint id PK
        text kind "TRANSFER or REPLACEMENT"
        bigint employee_id FK
        bigint from_school_id FK
        bigint to_school_id FK
        bigint replacement_id FK
        date effective_date
        text status
    }
    zone ||--o{ prospect : "in"
    employee ||--o{ prospect : "owns"
    prospect ||--o{ prospect_activity : "visits and calls"
    prospect |o--o{ school_contract : "won as"
    employee ||--o{ task : "assigned to"
    school |o--o{ task : "about"
    task ||--o{ task_evidence : evidence
    stored_file ||--o| task_evidence : file
    employee ||--o{ observation : "observed"
    school ||--o{ observation : "at"
    observation ||--o{ performance_followup : "issue"
    task |o--o{ performance_followup : "tracked by"
    observation |o--o{ performance_followup : "follow-up visit"
    stored_file }o--o| observation : "attachment (max 5)"
    employee ||--o{ transfer_request : "moved"
    school ||--o{ transfer_request : "from / to"
```

### 2.10 Content [Phase 2]

```mermaid
erDiagram
    content_item {
        bigint id PK
        text code UK
        text title
        bigint author_user_id FK
    }
    content_version {
        bigint id PK
        bigint content_id FK
        smallint version_no "UK with content_id"
        bigint file_id FK "UK"
        text review_status "PENDING APPROVED RETURNED"
        bigint reviewed_by FK
        text review_comment
    }
    app_user ||--o{ content_item : authors
    content_item ||--o{ content_version : versions
    stored_file ||--o| content_version : file
```

## 3. Old table to new table mapping (all 146 objects)

Source: `docs/er-diagrams.md` section 3 and the count check in its section 6 (146 objects = 145 base tables plus the view `due_balance`). Result codes: **KEPT** the concept survives as a table (often renamed or trimmed), **MERGED** folded into another table or into columns of it, **AUDIT** replaced by rows in `audit_log`, **SETTING** a row in `setting` or a code constant, **DERIVED** computed by a query, **DROPPED** not rebuilt.

### 3.1 identity (7)

| Old table | New | Result | Reason |
| --- | --- | --- | --- |
| `app_user` | `app_user` | KEPT | Roles become a `text[]` column; theme preference and `linked_teacher_id` gone (link lives in `employee.user_id`). |
| `one_time_code` | `otp_code` | KEPT | Renamed, hash only. |
| `permission_matrix` | none | DROPPED | Five fixed roles; the role x capability table is code (decision 8). |
| `renewal_credential` | `login_session` | MERGED | One session token renewed on activity; no rotating refresh-token reuse detection. |
| `role_assignment` | `app_user.roles` | MERGED | Roles are a column list. |
| `session` | `login_session` | KEPT | Renamed (the old name clashed with training sessions). |
| `user_preferences` | none | DROPPED | Themes dropped (spec 039). |

### 3.2 audit (4)

| Old table | New | Result | Reason |
| --- | --- | --- | --- |
| `api_access_entry` | none | DROPPED | Device location on every API call dropped; location only at Manager check-in/out. |
| `change_history_entry` | `audit_log` | AUDIT | The single audit table. |
| `login_history_entry` | `app_user.last_login_at`, `failed_attempts` | MERGED | Failed sign-ins go to the server log. |
| `user_activity_entry` | `audit_log` | AUDIT | Business actions only; page-view activity dropped. |

### 3.3 settings (1)

| Old table | New | Result | Reason |
| --- | --- | --- | --- |
| `system_setting` | `setting` | KEPT | Plain key-value, no range framework. |

### 3.4 school (4)

| Old table | New | Result | Reason |
| --- | --- | --- | --- |
| `place` | `school.city`, `school.pincode` | MERGED | No PIN bulk import; two text columns. |
| `school` | `school` | KEPT | Gains `manager_id` (replaces assignment table) and `weekly_offs`. |
| `school_contact` | `school_contact` | KEPT | Same. |
| `zone` | `zone` | KEPT | Gains `manager_id` (replaces assignment table). |

### 3.5 organization (4)

| Old table | New | Result | Reason |
| --- | --- | --- | --- |
| `manager` | `employee` (kind MANAGER) | MERGED | One person table. |
| `manager_designation` | `designation` | MERGED | Designation list covers all kinds. |
| `school_manager_assignment` | `school.manager_id` | MERGED | One Manager per School; history in `audit_log`. |
| `zone_manager_assignment` | `zone.manager_id` | MERGED | Same; history in `audit_log`. |

### 3.6 designation (2)

| Old table | New | Result | Reason |
| --- | --- | --- | --- |
| `designation` | `designation` | KEPT | Gains kind, suggested salary, substitute day rate. |
| `employee_id_claim` | `employee.employee_code` UK | MERGED | A unique constraint replaces the claim table. |

### 3.7 teacher (2)

| Old table | New | Result | Reason |
| --- | --- | --- | --- |
| `teacher` | `employee` (kind TEACHER) | MERGED | One person table; status IN_TRAINING / ACTIVE / EXITED. |
| `teacher_salary_history` | `employee_salary` | MERGED | One dated salary history for everyone. |

### 3.8 recruitment: campus recruitment (10 of 21)

| Old table | New | Result | Reason |
| --- | --- | --- | --- |
| `college` | `college` | KEPT | Contacts become columns. |
| `college_contact` | `college` columns | MERGED | Two contacts (placement officer, principal) as columns. |
| `campus_drive` | `drive` | KEPT | Renamed. |
| `campus_drive_date` | `drive.drive_date` | MERGED | One date per drive. |
| `campus_drive_interviewer` | `drive.interviewers` | MERGED | Free text. |
| `drive_date_change` | `audit_log` | AUDIT | Date changes are audited. |
| `candidate` | `candidate` | KEPT | Outcome is a column. |
| `candidate_outcome_history` | `audit_log` | AUDIT | Outcome history. |
| `assessment_score` | `candidate.outcome_note` | MERGED | Scores not carried; note field. |
| `job_offer` | `offer` | KEPT | Superseding chain simplified to status REPLACED. |

### 3.9 recruitment: school marketing (11 of 21)

| Old table | New | Result | Reason |
| --- | --- | --- | --- |
| `marketing_prospect` | `prospect` | KEPT | Proposal terms inline. |
| `marketing_activity` | `prospect_activity` | KEPT | Visits, calls, meetings. |
| `activity_attachment` | `stored_file` | MERGED | Attachments dropped from the simplified activity; use notes. |
| `activity_attendee` | none | DROPPED | Single owner per prospect; attendees are text in the outcome. |
| `activity_date_history` | `audit_log` | AUDIT | Reschedule history. |
| `overdue_notice` | none | DERIVED | Overdue is a query (planned date passed, status PLANNED). |
| `proposal_position` | `prospect.expected_teachers`, `proposed_fee` | MERGED | Inline proposal columns. |
| `proposal_revision` | `audit_log` | AUDIT | Edit in place; old values kept in audit. |
| `prospect_owner_history` | `audit_log` | AUDIT | Owner changes audited. |
| `prospect_stage_history` | `audit_log` | AUDIT | Stage changes audited. |
| `marketing_setting` | `setting` | SETTING | Row(s) in `setting`. |

### 3.10 training (13)

| Old table | New | Result | Reason |
| --- | --- | --- | --- |
| `induction_batch` | `induction_batch` | KEPT | Same idea. |
| `induction_enrolment` | `induction_member` | KEPT | Renamed; carries days attended and sign-off. |
| `induction_absence` | `induction_member.days_attended` | MERGED | Count instead of absence rows. |
| `training_session` | `training_session` | KEPT | Plain sessions. |
| `training_assignment` | `training_attendee` | MERGED | Invited people. |
| `training_registration` | `training_attendee` | MERGED | No seat-limited registration; invitation list only. |
| `training_attendance_entry` | `training_attendee.attended` | MERGED | Flag, plus a TRAINING `attendance_mark`. |
| `training_attendance_history` | `audit_log` | AUDIT | History. |
| `training_session_history` | `audit_log` | AUDIT | History. |
| `training_pattern` | none | DROPPED | No pattern generator; Admin copies last month. |
| `training_pattern_skip` | none | DROPPED | With the pattern. |
| `training_exception` | none | DERIVED | Non-attendance is a filter on the attendee list. |
| `training_reminder_sent` | none | DROPPED | No reminder job. |

### 3.11 attendance (17)

| Old table | New | Result | Reason |
| --- | --- | --- | --- |
| `attendance_mark` | `attendance_mark` | KEPT | Fixed status list. |
| `attendance_mark_history` | `audit_log` | AUDIT | Corrections audited. |
| `attendance_status_code` | `CHECK` on `attendance_mark.status` | MERGED | Fixed codes, no configurable list. |
| `attendance_calendar_setting` | `setting` (`weekly_offs`) | SETTING | Org-wide weekly offs. |
| `attendance_non_working_date` | `holiday` | KEPT | Renamed. |
| `attendance_teacher_month` | none | DERIVED | Month totals computed from marks. |
| `attendance_teacher_month_event` | `audit_log`, `month_lock` | MERGED | Lock/reopen. |
| `school_working_days` | `school_month_days` | KEPT | Same figure. |
| `school_working_days_history` | `audit_log` | AUDIT | History. |
| `manager_day` | `manager_checkin` | KEPT | One row per Manager per day. |
| `manager_day_activity` | none | DROPPED | Field day carries a purpose text; no planned-activity link (C1 dropped). |
| `manager_day_history` | `audit_log` | AUDIT | Corrections. |
| `manager_missed_alert` | none | DERIVED | Dashboard lists "no check-in yet today". |
| `manager_month` | none | DERIVED | Computed from check-ins. |
| `manager_month_event` | `month_lock` | MERGED | Shared lock table (kind MANAGER). |
| `manager_regularization` | `manager_checkin.edited_by`, `edit_reason` | MERGED | Admin edits the day with a reason. |
| `manager_attendance_setting` | `setting` | SETTING | Row(s). |

### 3.12 leave (3)

| Old table | New | Result | Reason |
| --- | --- | --- | --- |
| `leave_request` | `leave_request` | KEPT | Shared by Teachers and Managers. |
| `manager_leave_request` | `leave_request` | MERGED | Same table, decided by Director. |
| `leave_type` | `CHECK` on `leave_request.leave_type` | MERGED | Five fixed types. |

### 3.13 payroll (13)

| Old table | New | Result | Reason |
| --- | --- | --- | --- |
| `payroll_run` | `payroll_run` | KEPT | One per month. |
| `payroll_run_line` | `payroll_line` | KEPT | Renamed. |
| `payroll_run_line_adjustment` | `payroll_line.adjustments_total` and `payroll_adjustment.applied_run_id` | MERGED | Link kept on the adjustment. |
| `payroll_adjustment` | `payroll_adjustment` | KEPT | Absorbs incentive, reimbursement, correction. |
| `payroll_payslip` | `payroll_line` | MERGED | Printable page from the line; no stored PDF. |
| `payroll_paid_month` | `payroll_line.paid_*` | MERGED | Payment status on the line. |
| `payroll_payment_entry` | `payroll_line.paid_*` | MERGED | Date, mode, reference on the line. |
| `payroll_run_event` | `audit_log` | AUDIT | Run history. |
| `person_salary` | `employee_salary` | MERGED | One salary history. |
| `designation_salary` | `designation.suggested_salary` | MERGED | A column, no history (D12). |
| `designation_day_rate` | `designation.substitute_day_rate`, `employee.day_rate` | MERGED | Rate per person with a default. |
| `pay_policy` | none | SETTING | Pay rules are constants in one `PayRules` class. |
| `staff_employment` | `employee` (kind STAFF) | MERGED | Staff are employees. |

### 3.14 substitution (5)

| Old table | New | Result | Reason |
| --- | --- | --- | --- |
| `substitution_requirement` | `substitution` | MERGED | One row per absent Teacher per date. |
| `substitute_day` | `substitution` | MERGED | Day mark and rate snapshot as columns. |
| `substitute_day_event` | `audit_log` | AUDIT | Events. |
| `substitution_history` | `audit_log` | AUDIT | History. |
| `substitute_month_lock` | none | DROPPED | Payroll approval is the lock; `substitution.paid_in_run_id` prevents double pay. |

### 3.15 schoolbilling (15)

| Old table | New | Result | Reason |
| --- | --- | --- | --- |
| `contract` | `school_contract` | KEPT | Gains approval and document columns. |
| `contract_position` | `contract_position` | KEPT | Same. |
| `contract_assignment` | `placement` | KEPT | Dated Teacher-to-position rows, no-overlap exclusion constraints. |
| `contract_signatory` | `school_contract.signatory_*` | MERGED | One signatory as columns. |
| `mou_document` | `stored_file`, `school_contract.document_file_id` | MERGED | Versions via `replaces_file_id`. |
| `mou_approval` | `school_contract.status`, `approved_by`, `approved_on` | MERGED | Two states, not five. |
| `mou_approval_event` | `audit_log` | AUDIT | Events. |
| `monthly_due` | `invoice` | KEPT | Renamed; immutable. |
| `due_line` | `invoice_line` | KEPT | Renamed. |
| `due_adjustment` | `invoice_adjustment` | KEPT | Credit adjustments. |
| `school_collection` | `payment` | KEPT | Negative row is the reversal. |
| `collection_allocation` | `payment_allocation` | KEPT | Renamed. |
| `due_balance` (view) | `invoice_balance` (view) | KEPT | Still a view. |
| `overdue_follow_up` | none | DERIVED | Overdue list per Manager; no automatic tasks. |
| `billing_setting` | `setting` (`payment_due_days`) | SETTING | Row. |

### 3.16 expenses (5)

| Old table | New | Result | Reason |
| --- | --- | --- | --- |
| `expense_claim` | `expense_claim` | KEPT | Draft state and idempotency keys dropped. |
| `expense_bill` | `expense_bill` | KEPT | Same. |
| `expense_claim_event` | `audit_log` | AUDIT | Events. |
| `expense_area_budget` | `expense_budget` | KEPT | Phase 2. |
| `expense_budget_change` | `audit_log` | AUDIT | Budget changes. |

### 3.17 transfer (4)

| Old table | New | Result | Reason |
| --- | --- | --- | --- |
| `movement_request` | `transfer_request` | KEPT | One table; kind TRANSFER or REPLACEMENT. |
| `movement_participant` | `transfer_request.employee_id`, `replacement_id` | MERGED | Columns. |
| `movement_assignment` | `placement` | MERGED | Placement rows are the history. |
| `movement_request_history` | `audit_log` | AUDIT | History. |

### 3.18 tasks (3)

| Old table | New | Result | Reason |
| --- | --- | --- | --- |
| `task` | `task` | KEPT | No Acknowledged state. |
| `task_evidence` | `task_evidence` | KEPT | Same. |
| `task_history` | `audit_log` | AUDIT | History. |

### 3.19 incentive (4)

| Old table | New | Result | Reason |
| --- | --- | --- | --- |
| `incentive` | `payroll_adjustment` (kind INCENTIVE) | MERGED | Unique index: one per School. |
| `incentive_event` | `audit_log` | AUDIT | Events. |
| `incentive_program` | `setting` (`incentive_amount`) | SETTING | One amount. |
| `no_earner` | none | DERIVED | Query: approved contracts with no owner. |

### 3.20 performance (10)

| Old table | New | Result | Reason |
| --- | --- | --- | --- |
| `observation` | `observation` | KEPT | Principal feedback text as column. |
| `observation_rating` | `observation.rating_*` | MERGED | Six rating columns with CHECK 1..5. |
| `observation_attachment` | `stored_file.observation_id` | MERGED | Owner column on the file. |
| `observation_plan` | `task` | MERGED | Planned observation is a task. |
| `principal_feedback` | `observation.principal_feedback` | MERGED | Text column. |
| `performance_issue` | `performance_followup` | MERGED | Issue and corrective action together. |
| `corrective_action` | `performance_followup` | MERGED | Same row. |
| `issue_link` | `performance_followup.followup_observation_id`, `task_id` | MERGED | Columns. |
| `performance_history` | `audit_log` | AUDIT | History. |
| `referral` | `transfer_request` | MERGED | "Unsuitable" opens a transfer request. |

### 3.21 content (4)

| Old table | New | Result | Reason |
| --- | --- | --- | --- |
| `content` | `content_item` | KEPT | Renamed. |
| `content_version` | `content_version` | KEPT | Gains review columns. |
| `content_review` | `content_version.review_*` | MERGED | Review outcome on the version. |
| `content_history` | `audit_log` | AUDIT | History. |

### 3.22 notification, files, infrastructure (5)

| Old table | New | Result | Reason |
| --- | --- | --- | --- |
| `notification` | `notification` | KEPT | About ten events, no purge job. |
| `stored_file` | `stored_file` | KEPT | Plus `replaces_file_id` and `observation_id`. |
| `event_publication` | none | DROPPED | No event bus (rule 3). |
| `event_publication_archive` | none | DROPPED | Same. |
| `flyway_schema_history` | tool table | KEPT | Created by the migration tool, not part of the 51 (see build plan: Flyway or Liquibase). |

### 3.23 Tally

Counted by script from the tables above (all 146 old objects appear exactly once).

| Result | Count |
| --- | --- |
| KEPT (same concept survives, often renamed or trimmed; includes the view and the migration tool table) | 46 |
| MERGED into another table or into columns | 52 |
| AUDIT (history or event table replaced by `audit_log` rows) | 24 |
| DERIVED (computed by a query) | 7 |
| SETTING (row in `setting` or a code constant) | 6 |
| DROPPED with no replacement | 11 |
| Total | 146 |

Of the 46 KEPT, 44 are business tables (the other two are the view `due_balance`, which becomes `invoice_balance`, and `flyway_schema_history`, owned by the migration tool). The new schema has 51 tables: the 44 survivors plus seven that exist only as merge targets with no single predecessor (`employee`, `employee_salary`, `audit_log`, `month_lock`, `substitution`, `training_attendee`, `performance_followup`). Reduction: 146 objects to 51 tables (65 percent fewer).

## 4. Choices made in the DDL that the requirements document left open

1. **Teacher statuses.** `employee.status` is IN_TRAINING, ACTIVE, EXITED. The old specs say four stored statuses and do not list them (noted as unsure in the requirements document); Available, Assigned, Under Review, Transferred, Replaced are derived from `placement`, `transfer_request` and open `observation` rows.
2. **Substitute pay.** A PRESENT `substitution` row with `paid_in_run_id` null is picked up by the next payroll run, so a day marked after approval is paid in the following run with no correction machinery. The requirements text says "adjustment line"; this achieves the same result without a manual step.
3. **Incentive owner.** `school_contract.incentive_owner_id` (and `prospect_id`) carries who earns the incentive, so the MVP works before the Phase 2 prospect screens exist.
4. **Multiple MoU documents.** Kept through `stored_file.replaces_file_id` (a version chain) rather than a table.
5. **Observation attachments.** `stored_file.observation_id` is the one owner column on `stored_file`; a join table would have been a 52nd table.
6. **Observation rating areas.** Six area names are a proposal (classroom management, lesson delivery, student engagement, subject knowledge, communication, punctuality). The old specs say "several areas".
7. **Teacher home Zone.** Not stored; derived from the current placement's School. Managers and Substitutes use `employee.zone_id`.
8. **Audit log has no foreign key to its subject** (`entity_type`, `entity_id`) on purpose, so rows survive and any table can be audited.
