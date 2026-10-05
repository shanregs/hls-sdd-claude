# Research: Campus Recruitment, Job Offers and Induction (spec 016)

Every open point is settled here. No `NEEDS CLARIFICATION` remains.

## 1. How a recruit becomes a Teacher (no re-entry, exactly once)

- **Decision**: a new public interface `teacher.api.TeacherRegistry` with `createTrainee(actor, details)`,
  `findMatches(phone, email)`, `activate(actor, teacherId)`, `exit(actor, teacherId, date, reason)` and
  `recordFirstSalary(actor, teacherId, amount, effectiveOn)`. `recruitment` calls it; it wraps `TeacherService` and
  `SalaryService` so `recruitment` never touches `teacher.internal`.
- **Exactly once**: `AcceptanceService.accept(offerId)` runs in one transaction that locks the offer row
  (`SELECT ... FOR UPDATE` through `PESSIMISTIC_WRITE`), refuses unless the offer is `ISSUED` and not expired, creates
  the Teacher, stores `teacher_id` on the offer and candidate, and sets `ACCEPTED`. A second call finds `ACCEPTED` and
  answers 409 "already accepted". A partial unique index on `job_offer(candidate_id)` where `status = 'ACCEPTED'` is
  the backstop.
- **Duplicate Teacher** (spec FR-008): before creating, `findMatches` compares the normalized phone and the lower-cased
  e-mail. A match that is not exited refuses the acceptance and names the Teacher; a match that has exited needs the
  caller to pass `confirmNewRecord = true`. Nothing is merged or linked.
- **Alternatives**: `recruitment` writes to the Teacher tables (breaks Principle VII); an event that `teacher`
  consumes (acceptance must fail synchronously when a duplicate exists, so a direct call is required).

## 2. First salary from the accepted offer (decision in the spec, A4)

- **Decision**: spec 012's `AssignmentService` publishes `schoolbilling.api.TeacherFirstAssigned(teacherId, schoolId,
  startsOn)` in the assignment's transaction when the Teacher has no earlier assignment row of any status. `recruitment`
  has `FirstSalaryListener` (synchronous, same transaction): if the Teacher was created from an accepted offer and has
  no salary entry yet, it calls `TeacherRegistry.recordFirstSalary` with the offer's monthly salary and `startsOn`.
- **Rationale**: 012 stays unaware of recruitment; one salary entry per Teacher (the listener checks the history and a
  unique `(teacher_id, effective_on)` guard in `SalaryService` backs it); an Admin or Director can still correct it
  through the existing salary screen. A Teacher not created from an offer (a carried-over or hand-entered Teacher) is
  untouched.
- **Alternatives**: poll for new assignments (stale, and a second source of truth); have 012 call recruitment (module
  cycle).

## 3. Induction attendance has one source of truth, except absences

- **Decision**: a present day (whole or half) is written as a training-day mark (code T, weight 1.00) through the new
  `attendance.api.TrainingAttendance.markTrainingDay(actor, teacherId, date, value)`, which respects the month lock
  and writes mark history as for any mark. An absent day writes **no mark**; `training` keeps the day and the reason in
  `induction_absence` because 008 has no record of an absence for an unplaced Teacher.
- **A5, the 008 change (required by FR-010)**: (a) migration drops `NOT NULL` on `attendance_mark.school_id`;
  `MarkService.setMark` still requires a School for ordinary marks, only the new training path allows none;
  (b) `RollupCalculator.compute` currently `continue`s on days the Teacher is not placed, so school-less training marks
  would be lost. It now counts a TRAINING mark on an unplaced day into `trainingAvailable` and `trainingAttended`
  only: not into `workingDays` (it is not a School working day) and not into `weightedTotal` (induction is unpaid,
  decision D2). `RollupCalculatorTest` gets cases for an unplaced training day, a half day, and a month with both.
- **SC-007** (induction days equal the training days 008 reports) is proved by a test that records five induction
  days and reads the rollup.
- **Alternatives**: a separate induction register (a second truth, rejected in the spec); marking against a dummy
  School (breaks scope and calendar rules).

## 4. Offers are immutable

- **Decision**: statuses `DRAFT`, `ISSUED`, `ACCEPTED`, `DECLINED`, `EXPIRED`, `SUPERSEDED`. Only a `DRAFT` row can be
  edited (the letter is regenerated); issuing records who and when and freezes it. A changed package is a new offer
  with `supersedes_id`; the old one becomes `SUPERSEDED` in the same transaction. A partial unique index on
  `job_offer(candidate_id)` where `status IN ('DRAFT','ISSUED')` enforces "at most one open offer"; a database
  trigger rejects an `UPDATE` of the package columns once `status <> 'DRAFT'`.
- **No stipend**: induction is unpaid (Clarifications), so the offer has no stipend column.
- **Expiry**: `OfferExpiryJob` (daily, `hls.recruitment.offer-expiry.enabled`, off in `IntegrationTestBase`, the same
  pattern as the notification retention job) sets `ISSUED` offers past their deadline to `EXPIRED`; acceptance after
  the deadline is refused even before the job runs, by comparing the deadline in the service.

## 5. The offer letter

- **Decision**: a server-rendered HTML page `GET /api/v1/recruitment/offers/{id}/letter` with a print stylesheet
  (candidate name, designation "Trainee / English Trainer", package, terms, training information, expected joining).
  Text is HTML-escaped; the browser prints or saves it as PDF. No PDF library, no e-mail, no e-signature (out of scope).
  The letter shows the offer's status and is regenerated for a draft.

## 6. Candidates, import and duplicates

- **Decision**: single entry and a CSV import (UTF-8, header row: name, phone, email, degree, year, notes). Phones are
  normalized (digits only, last 10) and unique per drive, so a re-import saves nothing twice. Invalid rows are returned
  with the row number and reason; valid rows are saved in one transaction per 100 rows so a large sheet cannot time out
  the request and one bad row never blocks the rest.
- **Alternatives**: Excel files (needs a library); a draft-then-confirm import (more screens than the spec asks).

## 7. Interview outcome and assessment

- **Decision**: outcomes `SELECTED`, `WAITLISTED`, `REJECTED` with append-only history rows (who, when, note).
  Assessment scores are one row per criterion per assessment (`SPEAKING`, `ENGLISH`, `COMMUNICATION`), a whole
  number from 1 to 5, with remarks, assessor and time; a re-assessment adds new rows and the latest set is current, the
  earlier rows stay. The criteria and the 1 to 5 scale are constants now; making them editable is a later settings
  change.

## 8. Scope: Zone Manager reads all, writes own

- **Decision**: reads need `RECRUITMENT` `VIEW` for every role that holds it. Writes on a drive, its candidates,
  outcomes and scores need `RECRUITMENT` `CREATE` or `EDIT` and, for a Zone Manager, that they scheduled the drive or are
  one of its interviewers; the service checks this on every write (not only the controller), answering 403 with "not
  your drive". Admin and Director act on any drive. Offers need `OFFERS` (Director seeded in full, Admin `VIEW`);
  induction needs `INDUCTION` (Zone Manager has none).

## 9. Induction batches

- **Decision**: a batch has name, start and end date, trainer, venue type (physical or virtual) and venue, and a seat
  limit. An enrolment copies the batch dates so a gist exclusion constraint stops one Teacher being enrolled in two
  batches on overlapping dates (`btree_gist` exists from V13); the seat limit is checked under a row lock on the batch.
  Sign-off sets `result` and `remarks`; `COMPLETED` calls `TeacherRegistry.activate`; `NOT_COMPLETED` offers next batch
  (a new enrolment, the old one kept) or release (`TeacherRegistry.exit` with the reason). A locked 008 month refuses an
  attendance entry with the lock reason (the error is passed through).
- **Ready to deploy**: an `ACTIVE` Teacher with a completed enrolment and no current assignment, computed from
  `TeacherRegistry` (active Teachers of this module) and `TeacherPlacementSource.teachersAssignedDuring(today, today)`.

## 10. Dashboard

- **Decision**: one grouped query per measure by college and season label (or date range): drives scheduled and held,
  assessed, selected, offered, accepted, completed induction, ready to deploy, placed in a School and active, and the
  joining ratio (joined divided by selected, `NULL` when none selected). "Placed" reads `TeacherPlacementSource`;
  "active" reads the Teacher status. A Zone Manager sees the same counts with a "my drives" filter.

## 11. Planned activities (contract C1) and Training Stay

- **Decision**: `recruitment.api.DriveActivities.plannedBetween(from, to, ownerUserId?)` returns records (kind
  `CAMPUS_DRIVE`, id, owner, date, place, status). Spec 032 defines the shared view model and adapts it. `training.api`
  exposes `InductionBatchView` by id so spec 015 can link a Training Stay expense. This spec stores no food or
  accommodation cost.

## 12. Navigation and the constitution

- **Decision**: a new navigation section RECRUITMENT with items Campus Drives, Candidates, Offers, Induction and
  Dashboard, each shown by its permission. Principle IV lists the staff sections; a MINOR amendment (2.4.0) adds
  RECRUITMENT now and MARKETING with spec 023, and the matrix gets Recruitment, Offers and Induction rows. Done in the
  polish phase, as spec 012 did for its row.
