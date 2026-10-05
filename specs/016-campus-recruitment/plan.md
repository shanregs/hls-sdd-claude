# Implementation Plan: Campus Recruitment, Job Offers and Induction

**Branch**: `016-campus-recruitment` | **Date**: 2026-10-05 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/016-campus-recruitment/spec.md`

## Summary

Adds two bounded contexts (Constitution Principle VII), `recruitment` and `training` (induction), and the small
changes in `teacher`, `attendance` and `schoolbilling` they need:

- **`recruitment`**: Colleges, Campus Drives (dates, interviewers, season label, status), Candidates per drive (single
  entry and CSV import), interview Outcome with history, structured Assessment scores (group speaking, English,
  communication), Job Offers (draft, issued, accepted, declined, expired, superseded) with a printable offer letter,
  and the recruitment dashboard (funnel through placement, joining ratio). A daily job expires unanswered offers.
- **`training`**: Induction Batches, Enrolments, daily attendance entry and sign-off. A present day is written as a
  training-day mark (code T) in spec 008 through a new public interface; absences (with their reason) are kept by
  `training` because 008 has no record for an absence.
- **Teacher creation on acceptance** through a new public interface of `teacher` (`TeacherRegistry`): creates the
  Teacher "in training" with no salary entry, matches phone and email against existing Teachers, activates on
  sign-off, exits on release, and records the first salary entry.
- **First salary from the accepted offer (decision, Clarifications)**: spec 012 publishes `TeacherFirstAssigned`
  (this is the small 012 event, amendment **A4**) from its assignment service on a Teacher's first assignment of any kind (a contract with no position yet counts); `recruitment` listens and, for a
  Teacher created from an accepted offer, writes the offer's monthly salary to the salary history with the
  assignment start date as effective date, exactly once. 012 never depends on `recruitment`.
- **Spec 008 amendment (A5, required by FR-010)**: a training-day mark may have no School, and the rollup counts
  training marks on days the Teacher is not placed (today `RollupCalculator.compute` skips unplaced days).
- **Planned activities (contract C1)**: drives are exposed through `recruitment.api.DriveActivities`; induction
  batches are exposed by id for Training Stay expenses (spec 015).

`recruitment` depends on `teacher.api`, `attendance.api` (through `training`), `schoolbilling.api` (the event) and
`identity.user`; `training` depends on `recruitment.api` (the accepted offer and candidate), `teacher.api` and
`attendance.api`. Nothing depends on `recruitment.internal` or `training.internal`. **`recruitment` never depends on `training`**: acceptance publishes `OfferAccepted(offerId, candidateId, teacherId)` from `recruitment.api` in the acceptance transaction, and `training` listens and enrols the Teacher in the next batch with room (or leaves them in "to be enrolled"); ArchUnit enforces the direction.

## Technical Context

**Language/Version**: Java 25 (Spring Boot 4.1.1-based); TypeScript 5.7 with React 19. Unchanged.

**Primary Dependencies**: Spring Web, Spring Data JPA, Spring Security, Spring Modulith, Flyway, ArchUnit;
`@EnableScheduling` is already on (spec 010). No new library: the CSV import uses the same hand-written parser style as
the Place bulk import, the offer letter is server-rendered HTML with a print stylesheet (no PDF library).

**Storage**: PostgreSQL via `V22__create_recruitment_tables.sql` (see [data-model.md](./data-model.md)); the same
migration drops `NOT NULL` on `attendance_mark.school_id`. No cross-module foreign keys; foreign keys inside one
module are allowed. If another migration takes V22 first, this one is renumbered at merge.

**Testing**: JUnit 5, Spring Boot Test, Testcontainers (`IntegrationTestBase`). Rule unit tests first (open-offer
uniqueness, offer immutability, enrolment overlap and seat limit, acceptance idempotence, the duplicate-Teacher match).
Per-role and per-scope tests on every endpoint (Zone Manager on own drive and on another's). Attendance tests for the
unplaced training mark and its rollup (`RollupCalculatorTest`). A test that the 012 event writes the salary exactly
once. ArchUnit rules (`RecruitmentModuleRulesTest`). Frontend: Vitest + Testing Library with a fixture per role, axe in
both themes.

**Target Platform**: Browser; single Spring Boot deployable.

**Project Type**: Web application (frontend + backend).

**Performance Goals**: a 500-row candidate CSV imports in under 10 s; the dashboard loads in under 1 s for 200
colleges, 2,000 candidates and 1,000 offers (grouped SQL, no per-row queries).

**Constraints**: candidate phone and email are shown only to roles holding `RECRUITMENT` `VIEW` and are never logged
in plain text; an issued offer never changes; acceptance creates exactly one Teacher (a unique index and an
idempotent service); a recruit has no login; induction attendance has one source of truth (008) except absences;
every write is audited in the same transaction.

**Scale/Scope**: low thousands of candidates a season; about 40 endpoints; 7 screens.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle I (Auditable)** - PASS. Every drive, outcome, assessment, offer, acceptance, Teacher creation,
  enrolment, attendance entry and sign-off is audited through `ChangeRecorder`; offers and outcomes keep history.
- **Principle II (Roles, configurable permissions)** - PASS. New modules `RECRUITMENT`, `OFFERS` and `INDUCTION`,
  seeded per the spec, runtime-editable, ineligible for Teacher and System. The constitution's Default role access
  matrix gets rows for them at merge.
- **Principle III (Data scope)** - PASS with the spec's documented exception: recruitment data is not Zone-bound
  (colleges are not in a Zone), so a Zone Manager reads all drives and writes only their own; the write rule is
  enforced in the service, not only the guard.
- **Principle IV (Role-Based Experience)** - **needs an amendment.** The constitution lists the staff navigation
  sections; RECRUITMENT is a new one (spec 023 will add MARKETING). Resolved by a MINOR amendment adding both
  sections, done as a task in this spec's polish phase (see tasks). All other UI rules are met (server-driven menus,
  DD/MM/YYYY, rupees, WCAG 2.2 AA, phone width).
- **Principle V, VI** - not affected; induction is the onboarding workflow Principle VI names, and salary starts at
  placement (decision D2).
- **Principle VII (Modular Monolith)** - PASS: the module list already names `recruitment` and `training`. New public
  interfaces: `teacher.api.TeacherRegistry`, `attendance.api.TrainingAttendance`, `schoolbilling.api` event
  `TeacherFirstAssigned`, `recruitment.api` (offer, candidate views, `DriveActivities`). ArchUnit and Modulith verify.
- **Principle IX (Reliability, Testability)** - PASS, needs the tests above.
- **Principle X (Security)** - PASS: actor from the JWT; CSV rows validated and length-limited; candidate contact
  details not logged; offer letters render escaped text only.
- **Principles VIII, XI** - not applicable (XI's recruitment tracking to outcome is what this spec delivers: drives to
  placement).

No violations requiring justification beyond the Principle IV amendment. Complexity Tracking is not needed.

## Project Structure

### Documentation (this feature)

```text
specs/016-campus-recruitment/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── recruitment-api.md
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/src/main/java/com/hls/
├── identity/
│   ├── permissions/{PermissionModule,PermissionEligibility,PermissionMatrixService}.java   # + RECRUITMENT, OFFERS, INDUCTION
│   └── accessmodel/{NavigationCatalog,AccessModelService}.java        # + RECRUITMENT section, scope entries
├── teacher/
│   ├── api/TeacherRegistry.java            # new: createTrainee, findMatches, activate, exit, recordFirstSalary
│   └── internal/TeacherRegistryImpl.java   # over TeacherService and SalaryService
├── attendance/
│   ├── api/TrainingAttendance.java         # new: markTrainingDay, clearTrainingDay (no School)
│   └── internal/{TrainingAttendanceImpl,RollupCalculator,MarkService}.java   # A5: school-less T marks, rollup counts them
├── schoolbilling/
│   ├── api/TeacherFirstAssigned.java       # A4: record(teacherId, schoolId, startsOn)
│   └── internal/AssignmentService.java     # publishes it on a Teacher's first assignment
├── recruitment/
│   ├── api/                                # CandidateView, OfferView, DriveActivities, OfferAccepted (record)
│   ├── internal/                           # College, CampusDrive, Candidate, Outcome history, AssessmentScore, JobOffer;
│   │                                       #   repositories; DriveService, CandidateService (+CsvImporter), AssessmentService,
│   │                                       #   OfferService (+OfferLetterRenderer), AcceptanceService, OfferExpiryJob,
│   │                                       #   FirstSalaryListener, RecruitmentDashboardService, RecruitmentDevSeeder
│   └── web/                                # DriveController, CandidateController, OfferController, DashboardController
└── training/
    ├── api/                                # InductionBatchView (by id, for spec 015)
    ├── internal/                           # InductionBatch, InductionEnrolment, InductionAbsence; BatchService,
    │                                       #   EnrolmentService, InductionAttendanceService, SignOffService
    └── web/                                # BatchController

backend/src/main/resources/db/migration/V22__create_recruitment_tables.sql
backend/src/test/java/com/hls/{recruitment,training}/      # rules, scope, authorization, import, acceptance, expiry, attendance, module rules
backend/src/test/java/com/hls/attendance/                  # unplaced training mark and rollup (A5)

frontend/src/features/recruitment/
├── DrivesPage.tsx          # month calendar and list
├── DriveDetailPage.tsx     # candidates, outcome, assessment, CSV import
├── OffersPage.tsx          # offers by status, send, supersede, accept, decline, letter
├── OfferLetterView.tsx     # printable letter
├── InductionPage.tsx       # batches, roster, attendance, sign-off, ready to deploy
├── RecruitmentDashboard.tsx
└── recruitmentApi.ts
frontend/src/App.tsx                          # + guarded routes under /recruitment
docs/postman/                                 # new folder "016 Campus Recruitment"
.specify/memory/constitution.md               # Principle IV sections and matrix rows (MINOR 2.4.0)
docs/spec-roadmap.md                          # 016 status; A4 and A5 noted
```

**Structure Decision**: Web application (unchanged layout). Two new backend modules with the `api` / `internal` / `web`
split, one migration, additive public interfaces in `teacher`, `attendance` and `schoolbilling`, and one new
frontend feature folder.

### Delivery slices (for tasks.md, each independently demoable)

1. **Foundation**: migration, permission modules, navigation, `TeacherRegistry`, `TrainingAttendance` and the A5
   attendance change, the A4 event in 012; module skeletons and rules.
2. **Drives, candidates and assessment (US1)**: colleges, drives, calendar, candidates, CSV import, outcome, scores.
3. **Offers (US2)**: send, supersede, expire, decline, letter, access rules.
4. **Acceptance and first salary (US3)**: one Teacher per acceptance, duplicate match, enrolment, the first-salary listener.
5. **Induction (US4)**: batches, enrolment, attendance, sign-off, next batch or release, ready to deploy.
6. **Dashboard and polish (US5)**: funnel and joining ratio, demo data, docs, Postman, constitution amendment.

## Complexity Tracking

No violations. Table intentionally omitted.
