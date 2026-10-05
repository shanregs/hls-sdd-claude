# Contract: School Billing API

All paths under `/api/v1/billing`. JSON. Amounts are strings with two decimals (`"45000.00"`); months are
`YYYY-MM`; dates are ISO (`2026-10-05`) and shown as DD/MM/YYYY in the UI. Errors use the house format
(`{ "reason": "..." }`): 400 invalid input, 403 not permitted, 404 not found (also for a School outside the
caller's scope), 409 conflict (overlap, closed month, stale version). Every endpoint filters by the caller's
scope through `organization.api.ScopeQueries`; a Manager never receives another Manager's School.

## Contracts / MoU (module `SCHOOL_CONTRACTS`)

| Method | Path | Action | Purpose |
| --- | --- | --- | --- |
| GET | `/schools/{schoolId}/contracts` | VIEW | contract history of a School, newest first, each with its positions and signatories |
| POST | `/schools/{schoolId}/contracts` | CREATE | record a new MoU (ends the current contract the day before) |
| PUT | `/contracts/{id}/mou` | EDIT | record the MoU on a `RATE_PENDING` contract, once |
| POST | `/contracts/{id}/end` | EDIT | set the end date (not earlier than the last month with a current receivable) |
| GET | `/signatory-candidates?schoolId=` | VIEW | the HLS signatories on offer: the School's Zone Manager and the active Directors |
| GET | `/settings` | VIEW | overdue days, expected payment day, first billable month |
| PUT | `/settings` | EDIT | update the first two (version required) |

Create body (same salary for all):

```json
{ "teacherCount": 4, "salaryMode": "SAME_FOR_ALL", "rate": "15000.00",
  "startsOn": "2026-10-01", "endsOn": "2027-03-31", "signedOn": "2026-09-20",
  "schoolSignatories": [ { "name": "R. Kumar", "designation": "Principal" } ],
  "hlsSignatories": [ { "userId": "uuid", "designation": "Zone Manager" },
                      { "userId": "uuid", "designation": "Director" } ] }
```

Different salary for each Teacher: `"salaryMode": "PER_TEACHER"`, no `rate`, and `"positions": [ { "title":
"Maths PGT", "salary": "20000.00" }, ... ]` with exactly `teacherCount` entries. Refusals (400): a missing
or non-positive amount, a count and positions that disagree, a missing signed date or one in the future, no
School signatory, the Zone Manager or a Director missing among the HLS signatories, a `userId` that is not
the School's Zone Manager or an active Director. No Manager is stored on the contract: the responsible
Manager is the School's Zone Manager, read from `organization`; a School with none is refused with a plain
message. Signatories and positions cannot be edited after creation.

## Assignments: mapping Teachers to positions (module `SCHOOL_BILLING`)

Served at the existing Teacher paths so the Teachers screen keeps working:

| Method | Path | Action | Purpose |
| --- | --- | --- | --- |
| POST | `/api/v1/teachers/{id}/placements` | CREATE | map or move a Teacher (`schoolId`, `positionId` optional, `effectiveOn`) |
| DELETE | `/api/v1/teachers/{id}/placements/pending` | EDIT | cancel the scheduled move |
| GET | `/schools/{schoolId}/assignments?month=2026-10` | VIEW | positions of the School's contract with the Teacher mapped to each (or vacant), and Teachers not yet mapped |
| POST | `/contracts/{id}/map-teachers` | CREATE | re-map the School's current Teachers to the new contract's positions in one step (`[{ teacherId, positionId }]`) |

`positionId` may be left out for a `SAME_FOR_ALL` contract (the next vacant position is used); for
`PER_TEACHER` it is required. Refusals: the School has no contract covering the date; every position is filled;
the position is not on the School's contract or is already filled on those dates; the Teacher is exited; the
dates overlap another assignment of the Teacher. `map-teachers` is all or nothing and keeps each Teacher's
placement continuous (the old assignment ends the day before the new contract starts).

## Receivables and months (module `SCHOOL_BILLING`)

| Method | Path | Action | Purpose |
| --- | --- | --- | --- |
| GET | `/overview?month=2026-10` | VIEW | totals and the per-Manager and per-School breakdown |
| GET | `/schools/{schoolId}/months/{month}` | VIEW | the School's month: current receivable with breakdown, history, adjustments, payments, balance |
| POST | `/months/{month}/calculate` | EDIT | generate or recalculate every School in scope of the caller (Admin, Director) |
| POST | `/schools/{schoolId}/months/{month}/calculate` | EDIT | recalculate one School |
| POST | `/schools/{schoolId}/months/{month}/adjustments` | EDIT | add an adjustment line (`amount` signed, `reason` required); open month only |
| POST | `/months/{month}/close` | APPROVE | Admin only; 409 lists Teachers whose attendance is not locked |

## Payments (module `SCHOOL_BILLING`)

| Method | Path | Action | Purpose |
| --- | --- | --- | --- |
| POST | `/schools/{schoolId}/months/{month}/payments` | CREATE | record a payment |
| POST | `/payments/{id}/reverse` | CREATE | reverse a payment (`reason` required) |
| GET | `/schools/{schoolId}/payments?month=2026-10` | VIEW | payments and reversals, oldest first |
| GET | `/overview/export?month=2026-10` | EXPORT | CSV of the overview, same scope as the screen |

Payment body: `{ "paidOn": "2026-10-07", "mode": "CASH", "amount": "4500.00", "receiverUserId": null,
"comment": "first part" }`. Response includes `possibleDuplicate: true` when an entry with the same School,
month, amount and date exists (a warning, not a refusal). There is no PUT or DELETE for payments.

## Overview response

```json
{ "month": "2026-10", "closed": false,
  "totals": { "expected": "90000.00", "collected": "13500.00", "outstanding": "76500.00", "advance": "0.00" },
  "byManager": [ { "managerId": "uuid", "name": "Manoj", "expected": "45000.00", "collected": "13500.00", "outstanding": "31500.00" } ],
  "bySchool": [ { "schoolId": "uuid", "name": "Demo School One", "managerName": "Manoj", "contractState": "ACTIVE",
                  "expected": "45000.00", "collected": "13500.00", "outstanding": "31500.00",
                  "carriedForward": "0.00", "overdue": false } ],
  "noRateSchools": [ { "schoolId": "uuid", "name": "Demo School Two" } ] }
```

`byManager` and `bySchool` each sum to `totals`. For a Manager the lists hold only their Schools and `totals`
is the sum of those.

## Role matrix (default grants)

| Endpoint group | Admin | Director | Manager | Teacher | System |
| --- | --- | --- | --- | --- | --- |
| contracts and MoU details view, settings view | allowed | allowed | allowed (own Schools) | 403 | 403 |
| contract create, edit, end, settings edit | allowed | allowed | 403 | 403 | 403 |
| mapping Teachers, payments, reversals | allowed | allowed | allowed (own Schools) | 403 | 403 |
| calculate, adjustments | allowed | allowed | 403 | 403 | 403 |
| close a month | allowed | 403 | 403 | 403 | 403 |
| overview, School month views, export | allowed | allowed | allowed (own Schools) | 403 | 403 |

Permissions are runtime-editable, so a role can be given or denied an action; the scope never widens.

## Public interface for other modules (`schoolbilling.api`)

```java
interface SchoolBilling {
    /** The salary the School pays for the position the Teacher fills on a date; empty while unmapped or no MoU. */
    Optional<TeacherPosition> positionOf(UUID teacherId, LocalDate date);
    /** What the School paid for a month, net of reversals, and its balance up to that month. */
    Map<UUID, SchoolMonthFigures> figures(Collection<UUID> schoolIds, YearMonth month);
    /** A Teacher's billed line for a month: the salary used, working days at the School, working days in the month, amount (spec 013). */
    List<TeacherBilledLine> billedLines(UUID teacherId, YearMonth month);
    boolean isClosed(YearMonth month);
}
record TeacherPosition(UUID contractId, UUID positionId, int number, UUID schoolId, BigDecimal salary) {}
record SchoolMonthFigures(BigDecimal expected, BigDecimal collected, BigDecimal balance) {}
record TeacherBilledLine(UUID schoolId, UUID contractId, UUID positionId, BigDecimal salary,
                         int workingDaysAtSchool, int workingDaysInMonth, BigDecimal amount) {}

/** Published when a School-month first becomes overdue (listened to by notification). */
record SchoolPaymentOverdue(UUID schoolId, String schoolName, YearMonth month, BigDecimal balance, UUID managerUserId) {}
```

`billedLines` returns one line per contract the Teacher was mapped under in the month (a Teacher whose School
signed a new MoU mid-month has two), so spec 013 can compute margin per Teacher without reading billing
tables. School-level payment status (`figures`) tells payroll when the School's payment is missing.

## Interface owned by `teacher.api` and implemented by `schoolbilling`

```java
interface TeacherPlacementSource {
    List<PlacementSpan> spansOverlapping(Collection<UUID> teacherIds, LocalDate from, LocalDate to);
    Set<UUID> teachersAssignedDuring(LocalDate from, LocalDate to);
    boolean hasCurrentOrFutureAssignment(UUID schoolId, LocalDate from);
    Map<UUID, Long> teacherCountsBySchool(Collection<UUID> schoolIds, LocalDate on);
    void assign(UUID actor, UUID teacherId, UUID schoolId, UUID positionId, LocalDate effectiveOn);
    void cancelPending(UUID actor, UUID teacherId);
    void endForExit(UUID teacherId, LocalDate exitDate);
}
```

`TeacherDirectory`, `TeacherSchoolHooks`, `TeacherService` and `ManagerTeacherCountEnricher` call this instead
of the old repository, so callers of `TeacherDirectory` see no change.

## New in `attendance.api` (added to `AttendanceReadApi`)

```java
/** Working days for billing, from the same day plan as the rollup; no School calendar lives in billing. */
BillingDays billingDays(UUID teacherId, YearMonth month);
record BillingDays(Map<UUID, List<LocalDate>> workingDatesBySchool, int workingDaysInMonth) {}
```

`workingDatesBySchool` lists the dates the Teacher was placed at each School and the day was a working day in
the Teacher's attendance (which follows that School's calendar); dates, not counts, so a month can be split
between two contracts of one School. `workingDaysInMonth` adds the days the
Teacher was not placed that the default calendar treats as working days.
