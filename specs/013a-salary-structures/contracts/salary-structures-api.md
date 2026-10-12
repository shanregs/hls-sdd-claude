# Contract: Salary Structures API (HTTP, for the screens)

All paths under `/api/v1/salary-structures`. JSON. Amounts are strings with two decimals (`"30000.00"`); dates are ISO
and shown as DD/MM/YYYY. Errors use the house format (`{ "reason": "..." }`): 400 invalid input (the reason names the
problem), 403 not permitted, 404 not found, 409 conflict. Module `SALARY_STRUCTURES`; every endpoint requires `VIEW`
(reads) or `CREATE` (writes) and refuses a caller without it on the server, whatever the menu shows. Zone Manager,
Teacher and System always get 403. Only the `/people` endpoints below return an individual's pay, and only to holders of `SALARY_STRUCTURES`.

| Method | Path | Action | Purpose |
| --- | --- | --- | --- |
| GET | `/designations?kind=&includeRetired=true` | VIEW | every designation with its **suggested default** in effect today (or null), `kind` (`TEACHER`/`MANAGER`), retired flag and the missing-salary flag with the number of people who hold it and have no salary of their own |
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
  "missingSalary": { "flag": true, "people": 3, "note": "People holding this designation with no salary of their own" },
  "defaultNote": "A suggested default only: it is offered when a person's salary is entered, and nobody is paid it." }
```

`inEffect` is the suggested default; it is null (the screen says "no default yet") when no row is on or before today.
`defaultNote` is on every designation (US4 scenario 4). `missingSalary.people` counts the Managers or Teachers who hold
the designation and have no salary of their own in effect today, whatever the designation's default is.

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

## People (amendment 2026-10-11)

Each person's own salary. `kind` is `TEACHER`, `MANAGER` or `STAFF`; `personId` is the Teacher id, Manager id or (for
staff) the user id.

| Method | Path | Action | Purpose |
| --- | --- | --- | --- |
| GET | `/people?kind=&q=&withoutSalary=true&page=&size=` | VIEW | one page of people with their salary in effect today; `q` matches the name; `withoutSalary` keeps only those with none |
| GET | `/people/{kind}/{personId}` | VIEW | one person: salary in effect, the suggested default of their designation, and (Managers and staff) the full history, newest first |
| POST | `/people/{kind}/{personId}/salaries` | CREATE | record the person's own salary; `MANAGER` or `STAFF` only |
| PUT | `/people/STAFF/{userId}/employment` | CREATE | set a staff member's joining and exit dates |

List entry:

```json
{ "kind": "MANAGER", "personId": "uuid", "name": "Manoj Manager", "designation": "Zone Manager",
  "inEffect": { "amount": "65000.00", "effectiveOn": "2026-11-01" },
  "joiningDate": "2025-06-01", "exitDate": null, "active": true, "salaryEditable": true }
```

`inEffect` is null when the person has no salary (the screen says "no salary yet"). For a Teacher `salaryEditable` is
false: the amount is read from the Teacher's own record and changed on the Teachers screen. `designation` is the
designation name or null (a staff member has none). Staff entries also carry `roles`.

Detail adds `suggestedDefault { amount, effectiveOn }` (the designation's default in effect, or null) and
`history[]` rows `{ id, amount, effectiveOn, note, recordedBy { id, name }, recordedAt }` (empty for a Teacher).

Record a person's salary (same body and refusals as a designation's salary; 201):

```json
{ "amount": "65000.00", "effectiveOn": "2026-11-01", "note": "Agreed at recruitment" }
```

A `TEACHER` is refused with 400 ("A Teacher's salary is changed on the Teachers screen."); an unknown person is 404.
Employment body: `{ "joiningDate": "2026-06-01", "exitDate": null }`; an exit before the joining date is refused with 400.
Every `POST` and `PUT` writes its audit entries in the same transaction.
