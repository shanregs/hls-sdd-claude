# Contract: School Contracts API

All paths under `/api/v1/school-contracts` unless stated. JSON. Amounts are strings with two decimals
(`"15000.00"`); dates are ISO (`2026-10-05`) and shown as DD/MM/YYYY in the UI. Errors use the house format
(`{ "reason": "..." }`): 400 invalid input, 403 not permitted, 404 not found (also for a School outside the
caller's scope), 409 conflict (overlap, positions filled, stale version). Every endpoint filters by the caller's
scope through `organization.api.ScopeQueries`; a Zone Manager never receives another Zone Manager's School.

## Contracts / MoU (module `SCHOOL_CONTRACTS`)

| Method | Path | Action | Purpose |
| --- | --- | --- | --- |
| GET | `?status=&managerId=&page=0&size=25` | VIEW | every School in scope with its contract status, filled and vacant positions, dates |
| GET | `/schools/{schoolId}` | VIEW | the School's contract history, each with positions, signatories and mapped Teachers |
| POST | `/schools/{schoolId}/contracts` | CREATE | record a new MoU (ends the current contract the day before) |
| PUT | `/contracts/{id}/mou` | EDIT | record the MoU on a `RATE_PENDING` contract, once |
| POST | `/contracts/{id}/end` | EDIT | set the end date (not before the start date) |
| POST | `/contracts/{id}/cancel` | EDIT | cancel a contract that has no mapped Teacher |
| GET | `/signatory-candidates?schoolId=` | VIEW | the HLS signatories on offer: the School's Zone Manager and the active Directors |

Create body (same salary for all):

```json
{ "teacherCount": 4, "salaryMode": "SAME_FOR_ALL", "rate": "15000.00",
  "startsOn": "2026-10-01", "endsOn": "2027-03-31", "signedOn": "2026-09-20",
  "schoolSignatories": [ { "name": "R. Kumar", "designation": "Principal" } ],
  "hlsSignatories": [ { "userId": "uuid", "designation": "Zone Manager" },
                      { "userId": "uuid", "designation": "Director" } ],
  "version": null }
```

Different salary for each Teacher: `"salaryMode": "PER_TEACHER"`, no `rate`, and `"positions": [ { "title":
"Maths PGT", "salary": "20000.00" }, ... ]` with exactly `teacherCount` entries. `PUT /contracts/{id}/mou` takes
the same body (without `startsOn`, which the pending contract already has).

Refusals (400): a missing or non-positive amount, a count and positions that disagree, a missing signed date or one
in the future, no School signatory, no HLS signatory (the Zone Manager and/or a Director is needed), a `userId`
that is not the School's Zone Manager or an active Director. 409: the dates overlap another active contract, or the
School has no Zone Manager. No Manager is stored on the contract. Signatories and positions cannot be edited after
creation.

List response row:

```json
{ "schoolId": "uuid", "schoolName": "Demo School One", "zoneManagerName": "Manoj", "status": "ACTIVE",
  "contractId": "uuid", "startsOn": "2026-10-01", "endsOn": "2027-03-31", "teacherCount": 4,
  "filled": 3, "vacant": 1, "salaryMode": "PER_TEACHER", "signedOn": "2026-09-20" }
```

`status` is one of `ACTIVE`, `ENDS_SOON`, `MOU_PENDING`, `NONE`, `ENDED`.

## Assignments: mapping Teachers to positions

Served at the existing Teacher paths so the Teachers screen keeps working (permission `TEACHERS` `EDIT`, scope
through `ScopeQueries`):

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/api/v1/teachers/{id}/placements` | map or move a Teacher (`schoolId`, `positionId` optional, `effectiveOn`) |
| DELETE | `/api/v1/teachers/{id}/placements/pending` | cancel the scheduled move |
| POST | `/api/v1/school-contracts/contracts/{id}/map-teachers` | re-map the School's current Teachers to the new contract's positions in one step (`[{ teacherId, positionId }]`) |

`positionId` may be left out for a `SAME_FOR_ALL` contract (the next vacant position is used); for `PER_TEACHER` it
is required. A School with no contract covering the date gets a "MoU pending" contract and the Teacher is assigned without a position. Refusals: every position of an active MoU is filled; the position is not on
the School's contract or is already filled on those dates; the Teacher is exited; the dates overlap another
assignment of the Teacher. `map-teachers` is all or nothing and keeps each Teacher's placement continuous (the old
assignment ends the day before the new contract starts).

## Role matrix (default grants)

| Endpoint group | Admin | Director | Zone Manager | Teacher | System |
| --- | --- | --- | --- | --- | --- |
| list, School history, signatory candidates | allowed | allowed | allowed (own Schools) | 403 | 403 |
| create, record MoU, end, cancel | allowed | allowed | 403 | 403 | 403 |
| map and move Teachers, re-map | allowed | allowed | allowed (own Schools) | 403 | 403 |

Permissions are runtime-editable, so a role can be given or denied an action; the scope never widens.

## Public interface for other modules (`schoolbilling.api`)

```java
interface SchoolContracts {
    /** The contract in effect for the School on a date (any state), with its positions and signing details. */
    Optional<ContractView> contractOf(UUID schoolId, LocalDate date);
    /** The position the Teacher fills on a date and the salary the School pays for it; empty while unmapped. */
    Optional<TeacherPosition> positionOf(UUID teacherId, LocalDate date);
    /** Contracts of the School that overlap from..to, oldest first (for specs 013 and 022). */
    List<ContractView> contractsOverlapping(UUID schoolId, LocalDate from, LocalDate to);
    /** Teachers at the School in from..to who are not mapped to any position. */
    Set<UUID> unmappedTeachers(UUID schoolId, LocalDate from, LocalDate to);
}
record ContractView(UUID id, UUID schoolId, String state, String salaryMode, Integer teacherCount, BigDecimal rate,
                    LocalDate signedOn, LocalDate startsOn, LocalDate endsOn, List<PositionView> positions) {}
record PositionView(UUID id, int number, String title, BigDecimal salary) {}
record TeacherPosition(UUID contractId, UUID positionId, int number, UUID schoolId, BigDecimal salary) {}
```

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

`TeacherDirectory`, `TeacherSchoolHooks`, `TeacherService` and `ManagerTeacherCountEnricher` call this instead of
the old repository, so callers of `TeacherDirectory` (attendance, leave) see no change.
