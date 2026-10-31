# Data Model: Manager Incentives

Migration `V130__create_incentives.sql`. No foreign keys across modules (ids only). All timestamps `timestamptz`; money `numeric(12,2)`.

## incentive_program (one row)

| Column | Type | Notes |
| --- | --- | --- |
| id | smallint PK | always 1 (check) |
| starts_at | timestamptz | set to the migration time; approvals before it earn nothing (research D-3) |

## incentive

| Column | Type | Notes |
| --- | --- | --- |
| id | uuid PK | |
| school_id | uuid NOT NULL UNIQUE | one incentive per School, ever |
| contract_id | uuid NOT NULL | the MoU whose approval earned it |
| earner_manager_id | uuid NOT NULL | payroll Manager (organization id) |
| earner_user_id | uuid NOT NULL | the prospect owner's user id |
| amount | numeric(12,2) NOT NULL CHECK > 0 | frozen from the setting at creation |
| closure_date | date NOT NULL | business date of the MoU approval |
| status | varchar(20) NOT NULL | PENDING_APPROVAL, APPROVED, REJECTED, HELD, PAID_IN_RUN |
| mou_changed | boolean NOT NULL default false | approved incentive held by a run under review when the MoU changed |
| decided_by | uuid null | approver user id |
| decided_at | timestamptz null | |
| approved_by_earner | boolean NOT NULL default false | approver is the earner |
| reject_reason | varchar(500) null | required when REJECTED |
| hold_reason | varchar(100) null | |
| run_id | uuid null | pay run that applied it |
| run_month | date null | first day of that run's month |
| zone_id | uuid NOT NULL | the School's Zone when earned (informational; scope, filters and Zone Manager checks read the School's live Zone) |
| version | bigint NOT NULL | optimistic lock |
| created_at / updated_at | timestamptz | |

Indexes: `(status)`, `(earner_manager_id, status)`, `(zone_id, status)`, `(run_month)`.
Payroll `source_ref` = `INCENTIVE:` + id.

State transitions:

```text
(created) PENDING_APPROVAL --approve--> APPROVED --run applies--> PAID_IN_RUN (final)
PENDING_APPROVAL --reject(reason)--> REJECTED --reopen (Director, reason)--> PENDING_APPROVAL
APPROVED --MoU stops counting, offer withdrawn--> HELD --MoU approved again, offer again--> APPROVED
APPROVED --MoU changes while run awaits approval--> APPROVED with mou_changed = true
```

## no_earner

| Column | Type | Notes |
| --- | --- | --- |
| id | uuid PK | |
| school_id | uuid NOT NULL UNIQUE | |
| contract_id | uuid null | |
| reason | varchar(40) NOT NULL | NO_PROSPECT, NO_OWNER, OWNER_NOT_MANAGER, APPROVED_BEFORE_APPROVAL_STEP, BEFORE_GO_LIVE |
| detail | varchar(200) null | e.g. owner name |
| resolved_at | timestamptz null | set when an incentive is later created for the School |
| created_at | timestamptz | |

## incentive_event (append-only, trigger forbids update and delete)

| Column | Type | Notes |
| --- | --- | --- |
| id | uuid PK | |
| incentive_id | uuid NOT NULL | |
| kind | varchar(30) | EARNED, APPROVED, REJECTED, REOPENED, OFFERED, HELD, RELEASED, APPLIED, MOU_CHANGED |
| actor_id | uuid null | null for system actions |
| detail | varchar(500) null | reason or run id |
| created_at | timestamptz | |

## Setting

`incentive.amount-per-school` (Decimal, default 5000, 0 to 100000, group SYSTEM) in `SettingKey`.

## Permissions

`PermissionModule.MANAGER_INCENTIVES(VIEW, APPROVE, EXPORT)`; seeds Admin `VIEW, EXPORT`, Director all, Manager `VIEW, APPROVE`.
`PermissionEligibility`: `APPROVE` not eligible for Admin. Row visibility rules in `contracts/incentive-api.md`.
