# Research: School Contracts (MoU) (spec 012)

Every open point from the spec is settled here. No `NEEDS CLARIFICATION` remains. Billing decisions (month-end
bill by attendance, payments, close, overdue) are for spec 022; see `docs/spec-inputs/022-school-billing.md`.

## 1. Who owns "which School is this Teacher in?"

- **Decision**: `schoolbilling` owns the assignment rows (`contract_assignment`). `teacher` keeps asking the same
  question through a new interface in `teacher.api` (`TeacherPlacementSource`), which `schoolbilling` implements.
  `teacher.internal` stops owning `teacher_placement`.
- **Rationale**: the constitution gives the Teacher–School–Manager contract to `schoolbilling` (Principle VII) and
  calls the placement "interim". `schoolbilling` must read Teacher data through `teacher.api`, so `teacher` cannot
  call `schoolbilling` back; an interface owned by `teacher` and implemented by `schoolbilling` keeps the
  dependency pointing one way. `attendance`, `leave` and `organization` keep using `TeacherDirectory` unchanged,
  so nothing downstream moves.
- **Alternatives**: keep `teacher_placement` where it is and derive the contract from School and date (least
  change, but the assignment would stay outside the module that holds the salary, and 013, 017 and 022 need "the
  position and salary this Teacher filled on this day" from one place); `teacher` calls `schoolbilling` directly
  (creates a module cycle; Spring Modulith would fail the build).

## 2. How the existing placements move

- **Decision**: one Flyway migration copies every `teacher_placement` row (all statuses, same ids) into
  `contract_assignment` with no position, creates a `RATE_PENDING` contract for each School that has placements
  (starting on its earliest placement date, open-ended), and a second migration drops `teacher_placement`. A
  rollback is a restore from backup, as for earlier migrations.
- **Rationale**: same ids keep audit entries (`TEACHER_PLACEMENT`, entity id = Teacher) valid. Two migrations keep
  the copy reviewable apart from the drop. Teachers carried over have no position until the MoU is recorded and
  they are mapped, so attendance and leave work throughout.
- **Alternatives**: a view named `teacher_placement` over the new table (hides the change and keeps the old name
  alive); keeping the old table as an archive (two truths).
- The migration writes the pending contracts directly, so a School with no Zone Manager still gets one; the "needs a
  Zone Manager" check applies to contracts created through the screen.

## 3. Placement behaviour is kept, now against positions

- **Decision**: the rules proven in spec 005 are moved, not redesigned: dated rows, a future date schedules a move,
  "current" is evaluated against today, rows are never overwritten (`ACTIVE`, `CANCELLED`, `CORRECTED`), and an
  exclusion constraint stops overlap for one Teacher. The service becomes `AssignmentService` in
  `schoolbilling.internal`; the endpoints `POST /api/v1/teachers/{id}/placements` and
  `DELETE /api/v1/teachers/{id}/placements/pending` stay at the same paths so the Teachers screen keeps working,
  served by a thin `teacher.web` handler that calls `TeacherPlacementSource`.
- **New rules**: an assignment maps the Teacher to a vacant position of the School's contract covering its start
  date; with no position named and a `SAME_FOR_ALL` contract the next vacant position is used; for `PER_TEACHER`
  the position must be named. Without a contract, or with all positions filled, the user gets a plain refusal. A
  migrated assignment has no position until mapped.
- **Permission**: mapping keeps the permission and scope the placement endpoint used (`TEACHERS` `EDIT`, Zone
  Manager within their Schools); no extra permission is needed, and `SCHOOL_CONTRACTS` stays about the MoU itself.
- **Re-mapping to a new contract (FR-007)**: one transaction that, for each Teacher listed, ends the current
  assignment the day before the new contract starts and inserts a new one under the chosen new position; the
  placement span the rest of the system sees is continuous.

## 4. The contract is the MoU

- **Decision**: a contract records the MoU as the business signs it: School, N Teachers, `SAME_FOR_ALL` (one
  salary) or `PER_TEACHER` (a salary for each of N positions), dates, `signed_on`, and the signatories (School:
  name and designation, one or more; HLS: the School's Zone Manager and/or one or more Directors, at least one). It holds N rows in
  `contract_position`. A lump sum is not a basis (user direction, 2026-10-05).
- **Versioning**: a contract is immutable once created, except that its end date can be set; a change of salary,
  count or signatory is a new contract that ends the old one the day before. A `RATE_PENDING` contract takes its MoU
  in place, once.
- **Zone Manager**: not a contract column. The responsible Manager is the School's Zone Manager from
  `ManagerQueries.managerOfSchool` whenever needed. The HLS signatory for the Manager is that person at signing,
  stored as a name, designation and user id in `contract_signatory` (a record of who signed, so it never changes
  when the Zone Manager does).
- **HLS Director**: chosen from the active Directors through the same public `identity` lookup spec 010's recipient
  resolver uses (`GET /signatory-candidates`); the contract keeps the user id and name.
- **Rationale**: the signed MoU is a business record, so it is kept whole and unchangeable; specs 013, 017 and 022
  can then show "the salary the School pays for this position" without guessing.
- **Alternatives**: keep a lump sum as a third mode (the user listed two salary modes; a lump sum needs its own
  proration rule); the salary on the assignment instead of the position (the salary belongs to the MoU and must
  exist before a Teacher is recruited).

## 5. Contract status and maintenance

- **Decision**: the list is built in one query per page from contracts, positions and today's assignments;
  "ends soon" is a fixed 30 days before the end date; the end action only sets `ends_on` (not before `starts_on`)
  and never shortens Teachers' own assignments. The list and detail filter by `ScopeQueries` (Zone Manager's own
  Schools, 404 otherwise).
- **Rationale**: keeps the page fast without a cache; "ends soon" as a constant avoids a settings screen that spec
  011 will own later.

## 6. Permissions and navigation

- **Decision**: one new module in `PermissionModule`: `SCHOOL_CONTRACTS(VIEW, CREATE, EDIT)`, ineligible for Teacher
  and System (`PermissionEligibility`). Seeds follow the spec's table (Admin all, Director all three, Zone Manager
  `VIEW`). One navigation item, OPERATIONS → School Contracts (`/operations/school-contracts`), shown with
  `SCHOOL_CONTRACTS` `VIEW`. The constitution's Default role access matrix gets a "School Contracts" row at merge.
- **Rationale**: the MoU is a commercial record only Admin and Director create, while mapping is day-to-day work
  for the Zone Manager, so they are separate permissions.

## 7. Attendance continues to work

- **Decision**: no attendance change in this spec. `TeacherDirectory.placementsOverlapping` keeps its signature and
  now reads `contract_assignment` through `TeacherPlacementSource`; `RollupCalculator.plan`, the grid, the leave
  preview and `MarkService` see the same spans, so the same month gives the same figures (checked by a regression
  test on a month of demo data before and after the migration).
- **Rationale**: step 3 of the business process (attendance capture) must not be disturbed by the switch, and
  step 4 (month-end billing, spec 022) reads attendance and the mapping together.

## 8. Money, API shape and frontend

- **Decision**: `NUMERIC(12,2)` in the database, `BigDecimal` in Java, strings in JSON (`"15000.00"`) so the
  browser never rounds; the UI formats with Indian digit grouping. One frontend feature folder
  `features/schoolbilling/` (it will also hold 022's screens) with a contracts list page, a School contract page
  (history, create form, mapping) and a map-Teachers dialog. The Teachers screen's "interim" wording and
  `PlacementDialog` change to "assignment" and gain a position field.
- **Alternatives**: separate pages per tab (more routes for one workflow).
