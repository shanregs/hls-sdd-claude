# Research: Designations and Employment Details (spec 005a)

Every open point from the spec is settled here. No `NEEDS CLARIFICATION` remains.

## 1. Which module owns the designation list?

- **Decision**: a new module `designation`. `organization` (Manager history) and `teacher` (current designation) depend
  on `designation.api`; `designation` depends on neither. The "how many people hold it" count and the "missing"
  counts come from an SPI `designation.api.HolderCounter` that `organization` and `teacher` each implement; the
  designation service collects all beans of that type.
- **Rationale**: a designation is shared by two kinds of people owned by two modules. If it lived in `organization`
  the count of Teachers would need `organization` to call `teacher`, which already depends on `organization`
  (cycle, the build fails under Modulith). The SPI keeps every arrow pointing one way, the same pattern as
  `ManagerViewEnricher`.
- **Alternatives**: put it in `organization` and let the Teacher count come through an enricher (works, but then
  `teacher` reads `organization.api` for the designation rules and `organization` owns a Teacher concept); put it in
  `school` (the shared base module, but it is for Zones and Schools).

## 2. Employee id unique across Managers and Teachers

- **Decision**: a table `employee_id_claim` in `designation` whose primary key is the normalized id (trimmed, lower
  case), with `person_id` unique. Both modules call `EmployeeIds.claim(kind, personId, personName, employeeId)` in
  the same transaction as the person's update, and `release(personId)` when it is cleared. A duplicate violates the
  primary key; the service turns it into a 409 naming the holder.
- **Rationale**: the two person tables live in different modules, so a unique index cannot span them. A claim table
  is the only database-level guarantee that two simultaneous requests cannot both succeed (SC-003). The holder's
  name is stored with the claim (refreshed whenever that person's id is saved) so the error can name the person
  without a cross-module read; the name is for the message only.
- **Alternatives**: check-then-insert in code (race); advisory locks (works but invisible in the schema); a view
  over both tables (cross-module table access).

## 3. Manager designation history

- **Decision**: append-only `manager_designation` (manager id, designation id, effective date, recorded by, recorded
  at, identity `seq`). The designation on a date is the row with the greatest `(effective_on, seq)` where
  `effective_on <= date`. A `BEFORE UPDATE OR DELETE` trigger refuses changes, as in V20 and V22.
- **Rationale**: the clarified rule ("latest on or before the date; the one recorded last wins on a shared date") is
  exactly an ordering by effective date then insertion order. An identity column gives a total order without
  relying on timestamps that can tie.
- **Alternatives**: `valid_from`/`valid_to` ranges (needs rewrites, which the spec forbids).

## 4. Teacher designation

- **Decision**: nullable `designation_id` and `employee_id` columns on `teacher`; only the current value is kept; the
  audit log keeps earlier ones (clarified).
- **Rationale**: a Teacher's pay follows their recorded salary; history would add nothing.

## 5. What "made inactive" means for a Manager, and the exit date

- **Decision**: a Manager is inactive when its account is deactivated or loses the Manager role; `ManagerAccountSync`
  already reacts to those events. When it flips to inactive and the Manager has no exit date, the sync sets the exit
  date to the business date of the event and audits it as the acting user of the event; when it flips to active it
  clears the exit date and audits the prior value. An Admin or Director then corrects the date through the
  employment endpoint (not before the joining date, at most 90 days after today; only while inactive).
- **Rationale**: there is no "make inactive" dialog in the Managers screen; the action happens in User Management
  (spec 004). Changing that screen is out of scope, so the date defaults to today (the spec's default) and is edited
  afterwards. Managers already inactive before this spec keep no exit date and are flagged (clarified).
- **Alternatives**: ask for the date in the User Management dialog (changes spec 004 and crosses modules).
  *Deviation from the spec's wording ("an exit date is asked for"), recorded in the final report.*

## 6. Effective-date rules for a Manager's designation

- **Decision**: a later change must not be earlier than the first day of the current month (business date,
  Asia/Kolkata); the first designation may start on the joining date when one is set (any date from it on) or any
  date up to today when none is set. Setting the same designation as the one in effect on the new date is refused.
  A designation cannot be removed.
- **Rationale**: FR-006 and the clarifications. The business date comes from a small `BusinessDate` bean in
  `designation.api` (configured by `hls.business-timezone`, like attendance's calendar) so the first day of the month
  is right between 00:00 and 05:30 IST.

## 7. Permission model

- **Decision**: `DESIGNATIONS(VIEW, CREATE, EDIT)`; seeded to Admin and Director; ineligible for Teacher and System;
  eligible for Manager `VIEW` only (a new restriction in `PermissionEligibility`). Changing a person's designation,
  employee id, joining or exit date requires `DESIGNATIONS` `EDIT`. Reading the fields on a person needs only the
  existing `MANAGERS`/`TEACHERS` `VIEW`, so a Zone Manager sees them without any new grant.
- **Rationale**: FR-011 and the clarification; the Role & Permissions screen must not allow a Zone Manager to be
  given `EDIT`.

## 8. Missing details

- **Decision**: `MANAGERS` and `TEACHERS` list endpoints accept `missing=true`; the Designations screen reads a
  summary (`GET /api/v1/designations/summary`) built from the same counters. "Missing" for a Manager is no
  designation, no joining date, or inactive with no exit date; for a Teacher, no designation. Each view carries
  a `missing` list of codes (`DESIGNATION`, `JOINING_DATE`, `EXIT_DATE`).
- **Rationale**: one definition used by the list filter, the counts and the flags (SC-006: the count matches the list).

## 9. Teacher "My Profile" and the System role

- **Decision**: `TeacherService.mine` returns the view with `employment = null`; the frontend never renders the
  fields there. The System role has no `MANAGERS`/`TEACHERS`/`DESIGNATIONS` grants.

## 10. Designation "ever held" and kind change

- **Decision**: `designation.held_ever` is set the first time `DesignationDirectory.markHeld` is called by an
  assignment; renaming is always allowed, changing kind or (never) deleting is refused once it is set. Deletion has
  no path: the DELETE endpoint always answers 409.
- **Rationale**: for Teachers history is not kept, so "ever held" cannot be derived from rows; a flag is cheap and
  exact.

## 11. What "holding" means in counts

- **Decision**: a person holds a designation when it is their current designation (Manager: the row in effect today;
  Teacher: the column), regardless of active/exited status. 013a filters further by status if it needs to.
