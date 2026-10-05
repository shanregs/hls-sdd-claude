# Contract: Recruitment and Induction API

All paths under `/api/v1/recruitment` (recruitment) and `/api/v1/induction` (training). JSON; dates ISO; amounts are
strings with two decimals. Errors use the house format (`{ "reason": "..." }`): 400 invalid input, 403 not permitted
(including "not your drive"), 404 not found, 409 conflict (open offer, already accepted, expired, overlap, seat limit,
locked month).

## Colleges and drives (module `RECRUITMENT`)

| Method | Path | Action | Purpose |
| --- | --- | --- | --- |
| GET | `/colleges?query=` | VIEW | the College list |
| POST | `/colleges` | CREATE | add a college (duplicate name and city is 409) |
| GET | `/drives?from=&to=&season=&mine=` | VIEW | drives for the calendar or list; `mine=true` keeps drives the caller scheduled or attends |
| POST | `/drives` | CREATE | schedule a drive (`collegeId`, `dates[]`, `venue`, `season`, `interviewerUserIds[]`) |
| GET | `/drives/{id}` | VIEW | the drive with its candidates and outcome counts |
| PUT | `/drives/{id}` | EDIT | change dates, venue, interviewers (own drives for a Zone Manager) |
| POST | `/drives/{id}/status` | EDIT | mark held or cancelled (`reason` required to cancel) |

## Candidates, outcomes, assessment (module `RECRUITMENT`)

| Method | Path | Action | Purpose |
| --- | --- | --- | --- |
| POST | `/drives/{id}/candidates` | CREATE | add one candidate |
| POST | `/drives/{id}/candidates/import` | CREATE | CSV import (`multipart`, header `name,phone,email,degree,year,notes`); returns `{ saved, skipped, errors:[{row,reason}] }` |
| GET | `/candidates?drive=&outcome=&query=` | VIEW | candidates; phone and email only for roles with `RECRUITMENT` VIEW |
| POST | `/candidates/{id}/outcome` | EDIT | `{ outcome, note }`; keeps history |
| POST | `/candidates/{id}/assessment` | EDIT | `{ scores: { SPEAKING: 4, ENGLISH: 3, COMMUNICATION: 4 }, remarks }`; a new assessment, earlier kept |
| GET | `/candidates/{id}/history` | VIEW | outcome and assessment history |

## Offers (module `OFFERS`)

| Method | Path | Action | Purpose |
| --- | --- | --- | --- |
| GET | `/offers?status=&candidate=` | VIEW | offers (every role with `OFFERS` VIEW sees all) |
| POST | `/candidates/{id}/offers` | CREATE | create a draft (`role`, `monthlySalary`, `allowances`, `terms`, `expectedJoining`, `offerDate`, `responseDeadline`); refused if the candidate is not SELECTED or already has an open offer |
| PUT | `/offers/{id}` | EDIT | change a **draft** only |
| POST | `/offers/{id}/issue` | EDIT | issue (records who and when) |
| POST | `/offers/{id}/supersede` | CREATE | a new offer replacing an issued one; the old becomes SUPERSEDED |
| POST | `/offers/{id}/accept` | EDIT | accept; body `{ confirmNewRecord: false }`; creates the Teacher, returns the offer with `teacherId` |
| POST | `/offers/{id}/decline` | EDIT | decline with a reason |
| GET | `/offers/{id}/letter` | VIEW | the printable offer letter (HTML) |

## Induction (module `INDUCTION`)

| Method | Path | Action | Purpose |
| --- | --- | --- | --- |
| GET | `/api/v1/induction/batches` | VIEW | batches with roster counts |
| POST | `/api/v1/induction/batches` | CREATE | create a batch |
| POST | `/api/v1/induction/batches/{id}/enrol` | CREATE | `{ teacherId }`; overlap and seat-limit refusals are 409 |
| GET | `/api/v1/induction/batches/{id}/roster` | VIEW | enrolments with attendance for the batch dates |
| POST | `/api/v1/induction/enrolments/{id}/attendance` | EDIT | `{ date, status: PRESENT|HALF|ABSENT, reason }`; present writes a training-day mark in 008 |
| POST | `/api/v1/induction/enrolments/{id}/signoff` | EDIT | `{ result, remarks }` |
| POST | `/api/v1/induction/enrolments/{id}/follow-up` | EDIT | `{ action: NEXT_BATCH|RELEASE, reason }` after NOT_COMPLETED |
| GET | `/api/v1/induction/ready-to-deploy` | VIEW | active Teachers who completed induction with no School |
| GET | `/api/v1/induction/to-be-enrolled` | VIEW | Teachers in training without a batch |

## Dashboard (module `RECRUITMENT`)

`GET /dashboard?season=&from=&to=&mine=` returns per college and totals: `drivesScheduled`, `drivesHeld`, `assessed`,
`selected`, `offered`, `accepted`, `inducted`, `readyToDeploy`, `placed`, `active`, `joiningRatio`.

## Role matrix (default grants)

| Endpoint group | Admin | Director | Zone Manager | Teacher | System |
| --- | --- | --- | --- | --- | --- |
| read drives, candidates, outcomes, offers | allowed | allowed | allowed (all) | 403 | 403 |
| write drives, candidates, outcomes, assessment | allowed | allowed | own drives only | 403 | 403 |
| issue, supersede, accept, decline offers | 403 (view only) | allowed | 403 | 403 | 403 |
| induction (batches, attendance, sign-off) | allowed | allowed | 403 | 403 | 403 |
| dashboard | allowed | allowed | allowed (counts of all, `mine` filter) | 403 | 403 |

Permissions are runtime-editable; the scope check on a Zone Manager's writes never widens.

## Public interfaces added to other modules

```java
// teacher.api
interface TeacherRegistry {
    record Candidate(String name, String phone, String email, String address) {}
    record Match(UUID teacherId, String name, String status) {}
    List<Match> findMatches(String phone, String email);
    UUID createTrainee(UUID actor, Candidate details);                 // status IN_TRAINING, no salary entry
    void activate(UUID actor, UUID teacherId);                         // IN_TRAINING -> ACTIVE
    void exit(UUID actor, UUID teacherId, LocalDate on, String reason);
    boolean hasSalaryEntry(UUID teacherId);
    void recordFirstSalary(UUID actor, UUID teacherId, BigDecimal amount, LocalDate effectiveOn);
    Set<UUID> activeTeacherIds();
}
// attendance.api
interface TrainingAttendance {
    void markTrainingDay(UUID actor, UUID teacherId, LocalDate date, BigDecimal value); // 1.00 or 0.50, no School
    void clearTrainingDay(UUID actor, UUID teacherId, LocalDate date);
}
// schoolbilling.api  (spec 012, amendment A4)
record TeacherFirstAssigned(UUID teacherId, UUID schoolId, LocalDate startsOn) {}
// recruitment.api: published when an offer is accepted; training listens (recruitment never depends on training)
record OfferAccepted(UUID offerId, UUID candidateId, UUID teacherId) {}
// recruitment.api / training.api
interface DriveActivities { List<PlannedActivity> plannedBetween(LocalDate from, LocalDate to, UUID ownerUserId); }
record PlannedActivity(String kind, UUID id, UUID ownerUserId, LocalDate date, String place, String status) {}
record InductionBatchView(UUID id, String name, LocalDate startsOn, LocalDate endsOn) {}
```
