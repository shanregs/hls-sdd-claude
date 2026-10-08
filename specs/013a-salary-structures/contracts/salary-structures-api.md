# Contract: Salary Structures API (HTTP, for the screens)

All paths under `/api/v1/salary-structures`. JSON. Amounts are strings with two decimals (`"30000.00"`); dates are ISO
and shown as DD/MM/YYYY. Errors use the house format (`{ "reason": "..." }`): 400 invalid input (the reason names the
problem), 403 not permitted, 404 not found, 409 conflict. Module `SALARY_STRUCTURES`; every endpoint requires `VIEW`
(reads) or `CREATE` (writes) and refuses a caller without it on the server, whatever the menu shows. Zone Manager,
Teacher and System always get 403. No endpoint returns any individual person's pay.

| Method | Path | Action | Purpose |
| --- | --- | --- | --- |
| GET | `/designations?kind=&includeRetired=true` | VIEW | every designation with the salary in effect today (or null), `kind` (`TEACHER`/`MANAGER`), retired flag and the missing-salary flag with the number of people affected |
| GET | `/designations/{designationId}` | VIEW | the designation, its salary in effect today and its full history, newest first |
| POST | `/designations/{designationId}/salaries` | CREATE | record a salary |
| GET | `/policy` | VIEW | the policy in effect today and the full history, newest first |
| POST | `/policy` | CREATE | record a new policy version |

## Record a salary

```json
{ "amount": "32000.00", "effectiveOn": "2027-02-01", "note": "Annual revision" }
```

Response `201`:

```json
{ "id": "uuid", "designationId": "uuid", "amount": "32000.00", "effectiveOn": "2027-02-01",
  "note": "Annual revision", "recordedBy": { "id": "uuid", "name": "A. Admin" },
  "recordedAt": "2026-10-08T06:12:00Z" }
```

Refused with 400 and a named reason: amount missing, zero, negative, more than two decimals or above the maximum;
date missing or before the first day of the current month; note over 500 characters. 404 for an unknown designation.
A retired designation is accepted (US1 scenario 6).

## List entry

```json
{ "designationId": "uuid", "name": "Primary Teacher", "kind": "TEACHER", "retired": false,
  "inEffect": { "amount": "30000.00", "effectiveOn": "2026-11-01" },
  "missingSalary": { "flag": true, "people": 3, "note": "Teachers with no salary of their own" },
  "teacherDefaultNote": "Only the default offered when a Teacher's salary is set." }
```

`inEffect` is null (the screen says "no salary yet") when no row is on or before today. For a Teacher designation
`teacherDefaultNote` is present (US4 scenario 4) and `missingSalary.people` counts Teachers with no recorded salary of
their own. For a Manager designation it counts Managers holding it while it has no salary in effect.

## Record a policy version

```json
{ "effectiveOn": "2026-11-01", "lopDivisor": "WORKING_DAYS", "halfDayFraction": "0.500",
  "rounding": "NEAREST_RUPEE", "payMonth": "CALENDAR_MONTH", "note": null }
```

Refused with 400: a fraction not strictly between 0 and 1 (or with more than three decimals), an unknown enumeration
value, a missing or too-early date, a note over 500 characters. A version that changes nothing from the one in effect
is accepted (it is a dated record), not refused.

## Audit

Every `POST` writes `EntityChanged` entries in the same transaction (data-model.md, "State and lifecycle"); they appear
in the existing Audit → Change History screen. This API adds no audit endpoint.
