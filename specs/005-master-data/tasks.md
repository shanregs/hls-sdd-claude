---

description: "Task list for feature implementation"
---

# Tasks: Master Data (Zones, Schools, Managers, Teachers)

**Input**: Design documents from `/specs/005-master-data/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/master-data-api.md,
quickstart.md, and **specs 001-004 implemented** (this feature adds permission modules and
navigation to 002's matrix/catalog, publishes into 003's Change History, and reads `identity`'s
public `user` package for the Manager/Teacher roles).

**Tests**: included as first-class tasks - Constitution Principle IX requires per-role authorization
tests on every endpoint and per-scope-boundary tests (Manager A vs Manager B) on every
list/search/detail view, and the spec's invariants (Manager covers the Zone, placement non-overlap,
status machine, salary "as of") are only trustworthy with tests.

**Organization**: grouped by user story in priority order (spec.md US1-US9). P1 stories US1-US5 come
first, then the P2 stories US6-US9. Migrations are written in the story that first needs them.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1-US9; absent for Setup/Foundational/Polish

## Path Conventions

Web application per plan.md. Backend: `backend/src/main/java/com/hls/{school,organization,teacher}/`
(each with `api/` = Spring Modulith named interface, `internal/`, `web/`), additive changes under
`com/hls/identity/` and `com/hls/audit/`, tests under `backend/src/test/java/com/hls/...`,
migrations in `backend/src/main/resources/db/migration/`. Frontend: `frontend/src/features/{zones,
schools,managers,teachers}/`. Below, `BE` = `backend/src/main/java/com/hls`, `BT` =
`backend/src/test/java/com/hls`, `FE` = `frontend/src`.

---

## Phase 1: Setup

- [ ] T001 Verify specs 001-004 are implemented: confirm `PermissionModule`, `PermissionMatrixService`,
      `NavigationCatalog`, `UserAdminService`, `audit.changehistory.ChangeHistoryEntry` and
      `frontend/src/features/users` exist and the backend suite (`mvn test` in `backend/`) and
      frontend suite (`npm run test` in `frontend/`) are green. Do not proceed until they are.
- [ ] T002 [P] Run `mvn -q -pl backend compile` and `npm --prefix frontend run build`; confirm no new
      dependency is needed (plan.md Technical Context).

**Checkpoint**: prerequisites present; both projects build.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: permissions, navigation, the generic audit event, the shared API types and SPIs, and
module boundary rules that every story builds on.

**CRITICAL**: no user story task may start until this phase is complete.

- [ ] T003 Add constants `ZONES`, `SCHOOLS`, `MANAGERS`, `TEACHERS`, `TEACHER_SALARY` to
      `BE/identity/permissions/PermissionModule.java` (additive only, matching spec 004's
      `USER_MANAGEMENT`).
- [ ] T004 Extend `PermissionMatrixService.seedDefaults()` in
      `BE/identity/permissions/PermissionMatrixService.java` to idempotently seed data-model.md's
      grant table: `ZONES`/`SCHOOLS`: ADMIN `VIEW, CREATE, EDIT, DELETE`, DIRECTOR `VIEW, CREATE,
      EDIT`; `MANAGERS`: ADMIN and DIRECTOR `VIEW, CREATE, EDIT`; `TEACHERS`: ADMIN and DIRECTOR
      `VIEW, CREATE, EDIT`; `TEACHER_SALARY`: ADMIN and DIRECTOR `VIEW, CREATE`; plus MANAGER
      `SCHOOLS.VIEW`, `SCHOOLS.EDIT`, `TEACHERS.VIEW`, `TEACHERS.EDIT`. Nothing for TEACHER or
      SYSTEM. Depends on T003.
- [ ] T005 Add four `NavItem` entries to `BE/identity/accessmodel/NavigationCatalog.java`, section
      `"MASTER DATA"`: "Zones" (`/master-data/zones`, `ZONES`, `VIEW`, order 40, ADMIN+DIRECTOR);
      "Schools" (`/master-data/schools`, `SCHOOLS`, `VIEW`, order 41, ADMIN+DIRECTOR+MANAGER);
      "Managers" (`/master-data/managers`, `MANAGERS`, `VIEW`, order 42, ADMIN+DIRECTOR); "Teachers"
      (`/master-data/teachers`, `TEACHERS`, `VIEW`, order 43, ADMIN+DIRECTOR+MANAGER). No SYSTEM or
      TEACHER entries. Depends on T003.
- [ ] T005a Extend `AccessModelService.dataScope` in `BE/identity/accessmodel/AccessModelService.java`
      with the four modules per research.md section 18 (reuse the existing `DataScope` enum and
      widest-scope rule). Depends on T003.
- [ ] T006 [P] Backend test `BT/identity/accessmodel/MasterDataAccessModelTest.java`: per role,
      `GET /api/v1/me/access-model` shows exactly the MASTER DATA items above (Admin/Director all
      four; Manager Schools and Teachers only; Teacher and System none), with the seeded actions
      (Manager `SCHOOLS`: VIEW, EDIT; `TEACHERS`: VIEW, EDIT); and the seeded matrix has
      `TEACHER_SALARY` true only for ADMIN and DIRECTOR. Depends on T004, T005. Also assert the `dataScope` entries per role from T005a (Admin/Director `ORG_WIDE`, Manager `ASSIGNED` for `SCHOOLS` and `TEACHERS`, none for Teacher/System).
- [ ] T007 Create `BE/audit/api/EntityChanged.java` (record: `UUID eventId, Instant occurredAt, UUID
      actorUserId, String entityType, String entityId, String field, String beforeValue, String
      afterValue`, Javadoc "published by master-data modules for audit to append to Change
      History") and `BE/audit/api/package-info.java` annotated
      `@org.springframework.modulith.NamedInterface` (research.md section 1).
- [ ] T008 Create `BE/audit/changehistory/EntityChangedConsumer.java`
      (`@ApplicationModuleListener` on `EntityChanged`, dedup on `eventId` via
      `ChangeHistoryEntryRepository.findBySourceEventId`, saving a `ChangeHistoryEntry`, mirroring
      `PermissionMatrixChangeConsumer`). Depends on T007.
- [ ] T009 [P] Test `BT/audit/changehistory/EntityChangedConsumerTest.java`: publishing an
      `EntityChanged` in a committed transaction yields exactly one `change_history_entry` with the
      same entity type/id/field/before/after; redelivery of the same `eventId` is a no-op (copy
      the pattern of `UserActivityEventConsumerTest`). Depends on T008.
- [ ] T010 Create `BE/audit/support/AuditVisibility.java` mapping `entity_type` to the required
      `PermissionModule` VIEW grant (`ZONE`, `PLACE` -> `ZONES`; `SCHOOL` -> `SCHOOLS`; `MANAGER`,
      `ZONE_MANAGER_ASSIGNMENT`, `SCHOOL_MANAGER_ASSIGNMENT` -> `MANAGERS`; `TEACHER`,
      `TEACHER_PLACEMENT` -> `TEACHERS`; `TEACHER_SALARY` -> `TEACHER_SALARY`; unknown/existing types
      such as `PERMISSION_MATRIX` -> unrestricted) using `PermissionGuard`/`PermissionMatrixService`
      from `identity.permissions`, and apply it in `BE/audit/logs/AuditLogQueryService.java`, the
      Change History list and its `/export` in `BE/audit/AuditChangeHistoryController.java`, and
      `BE/audit/AuditLogsController.java` so entries the caller may not see are excluded from
      content, `totalElements` and CSV (research.md section 16). Depends on T003.
- [ ] T011 [P] Test `BT/audit/AuditVisibilityTest.java`: with `EntityChanged` rows for `SCHOOL`,
      `TEACHER` and `TEACHER_SALARY` published, an Admin sees all three in Change History, Audit
      Logs and the CSV export; a System user sees none of them but still sees `PERMISSION_MATRIX`
      rows; `totalElements` matches what is visible. Depends on T008, T010.
- [ ] T012 Update `BT/audit/ApplicationModuleBoundaryTest.java`'s `EXPECTED_CONSUMED_EVENTS` to add
      `com.hls.audit.api.EntityChanged` (and its class Javadoc). Depends on T008.
- [ ] T013 Create shared API types in `BE/school/api/`: `PageResponse<T>(List<T> content, int page,
      int size, long totalElements)`, `Reason(String reason)`, and exceptions `NotFoundException`,
      `ConflictException`, `InvalidInputException`, `ForbiddenFieldException` (each carries a
      plain-language message); plus `BE/school/api/package-info.java` annotated
      `@org.springframework.modulith.NamedInterface`.
- [ ] T014 Create `BE/MasterDataExceptionAdvice.java` (`@RestControllerAdvice(basePackages =
      {"com.hls.school","com.hls.organization","com.hls.teacher"})`) mapping `NotFoundException` ->
      404, `ConflictException` -> 409, `InvalidInputException` -> 400, `ForbiddenFieldException` ->
      403, and `ObjectOptimisticLockingFailureException` -> 409 `"This record was changed by
      someone else. Reload and try again."`, all as `Reason` (FR-026). Depends on T013.
- [ ] T015 [P] Unit test `BT/MasterDataExceptionAdviceTest.java` asserting each exception's status
      and body, including the optimistic-lock message. Depends on T014.
- [ ] T016 Define the cross-module SPIs as interfaces in `BE/school/api/` (research.md section 2,
      and section 17 below): `ZoneChangeGuard.checkDelete(UUID zoneId)`,
      `SchoolChangeGuard.checkPlaceChange(UUID schoolId, UUID newZoneId)`,
      `SchoolDeactivationGuard.checkDeactivate(UUID schoolId)` (each throws `ConflictException` to
      veto), `SchoolScopeProvider.visibleSchoolIds(UUID userId, Set<Role> roles)` returning
      `Optional<Set<UUID>>` (empty = no opinion), and the read-model enrichers
      `SchoolViewEnricher.enrich(Collection<UUID> schoolIds)` and
      `ZoneViewEnricher.enrich(Collection<UUID> zoneIds)` each returning
      `Map<UUID, Map<String,Object>>`. Depends on T013.
- [ ] T017 Create empty module skeletons with `api` named-interface `package-info.java` files:
      `BE/organization/api/package-info.java` and `BE/teacher/api/package-info.java` (annotated
      `@org.springframework.modulith.NamedInterface`). Confirm `ApplicationModulesTest` still passes.
- [ ] T018 [P] Architecture test `BT/MasterDataModuleRulesTest.java` (ArchUnit): classes in
      `com.hls.school..` do not depend on `com.hls.organization..` or `com.hls.teacher..`; classes in
      `com.hls.organization..` do not depend on `com.hls.teacher..`; `com.hls.audit..` does not
      depend on `school`, `organization`, or `teacher`; only `..api..` packages of the three new
      modules are accessed from outside their module. Depends on T017.

**Checkpoint**: permissions, navigation, audit event and consumer, audit visibility, shared API
types, SPIs and boundary tests exist and are green. Stories can begin.

---

## Phase 3: User Story 1 - Maintain Zones and Their Places (Priority: P1) MVP

**Goal**: Admin/Director create and rename Zones, add Places, look Places up by PIN/name; others are
refused.

**Independent Test**: as Admin create a Zone, add Places, rename the Zone, find a Place by PIN code
and by name (spec.md US1); as Manager/Teacher/System the menu and route are absent.

### Tests for User Story 1

- [ ] T019 [P] [US1] `BT/school/ZoneControllerTest.java`: list/create/rename/delete as Admin and
      Director (Director delete is 403); duplicate name (case-insensitive) is 409; delete of a Zone
      with Places is 409 naming the dependency; optimistic-lock conflict on rename is 409;
      `MANAGER`/`TEACHER`/`SYSTEM` get 403 on every Zone endpoint; unauthenticated is 401; every
      change appears in Change History (`await` up to 5 s) with before/after name. Also assert pagination: `size` above 100 is capped at 100, page 2 returns the next rows, and an empty result is `content: []` with `totalElements: 0`, not an error.
- [ ] T020 [P] [US1] `BT/school/PlaceControllerTest.java`: add a Place; duplicate names and PIN codes
      are accepted; PIN code not exactly six digits is 400; lookup by `pinCode` and by `name`
      returns every match with its Zone, and an unmatched lookup is an empty list not an error;
      Place edit/delete as Admin/Director; `MANAGER`/`TEACHER`/`SYSTEM` get 403 on every Place
      endpoint.
- [ ] T021 [P] [US1] `FE/features/zones/ZonesPage.test.tsx`: list renders with counts; create/rename
      dialogs call the API and show 409 reasons inline; the Places panel adds a Place and shows
      lookup results; Create/Edit/Delete controls hidden when the access model lacks those actions. Also assert the loading, empty ("No ... match your search") and error states.

### Implementation for User Story 1

- [ ] T022 [US1] Create `backend/src/main/resources/db/migration/V11__create_school_tables.sql` with
      `zone`, `place` and `school` tables exactly per data-model.md: `zone(id UUID PK, name
      VARCHAR(120) NOT NULL, version BIGINT NOT NULL, created_at, updated_at TIMESTAMPTZ)` with a
      unique index on `lower(name)`; `place(id UUID PK, zone_id UUID NOT NULL REFERENCES zone, name
      VARCHAR(160) NOT NULL, pin_code CHAR(6) NOT NULL CHECK (pin_code ~ '^[0-9]{6}$'), created_at
      TIMESTAMPTZ)` with indexes on `zone_id`, `pin_code`, `lower(name)`; `school(id UUID PK, name
      VARCHAR(200) NOT NULL, place_id UUID NOT NULL REFERENCES place, address TEXT NOT NULL,
      contact_person VARCHAR(120), contact_phone VARCHAR(20), billing_contact VARCHAR(200), active
      BOOLEAN NOT NULL DEFAULT true, version BIGINT NOT NULL, created_at, updated_at TIMESTAMPTZ)`
      with indexes on `place_id`, `active`, `lower(name)` (the `school` table is used from US2).
- [ ] T023 [P] [US1] `BE/school/internal/Zone.java` + `ZoneRepository.java` (JPA entity with
      `@Version`; `existsByNameIgnoreCase`; counts of Places).
- [ ] T024 [P] [US1] `BE/school/internal/Place.java` + `PlaceRepository.java` (`findByPinCode`,
      case-insensitive `findByNameContaining...`, `findByZoneId` paged).
- [ ] T025 [US1] `BE/school/internal/ZoneService.java`: list (with `placeCount`/`schoolCount` plus
      `ZoneViewEnricher` extras), create, rename (version check), delete (refuse with a message
      listing Places/Schools, call every `ZoneChangeGuard.checkDelete`), each publishing
      `EntityChanged(entityType="ZONE", field="name"|"created"|"deleted")`. Depends on T016, T023.
- [ ] T026 [US1] `BE/school/internal/PlaceService.java`: add (six-digit PIN, name required),
      lookup by PIN/name (all matches), list by Zone, edit, delete; publishes
      `EntityChanged(entityType="PLACE", ...)`. Depends on T024.
- [ ] T027 [US1] Public views and interfaces in `BE/school/api/`: `ZoneQueries`, `PlaceQueries`
      (`ZoneView`, `PlaceView`, `zoneOfPlace(placeId)`), implemented by the services above.
      Depends on T025, T026.
- [ ] T028 [US1] `BE/school/web/ZoneController.java` for the Zone endpoints in
      contracts/master-data-api.md, each gated by `PermissionGuard.require(...)` for
      `ZONES.VIEW|CREATE|EDIT|DELETE`. Depends on T025.
- [ ] T029 [US1] `BE/school/web/PlaceController.java` for the Place endpoints (list per Zone, lookup,
      add, edit, delete) gated by `ZONES.VIEW|EDIT`. Depends on T026.
- [ ] T030 [P] [US1] `FE/features/zones/zonesApi.ts` (`authFetch` client for Zones/Places,
      mirroring `features/users/userManagementApi.ts`).
- [ ] T031 [US1] `FE/features/zones/ZonesPage.tsx` and `ZoneDialog.tsx` (DataGrid with server
      pagination and search; counts columns; create/rename/delete gated by access-model actions).
      Depends on T030.
- [ ] T032 [US1] `FE/features/zones/PlacesPanel.tsx` (Places of the selected Zone, add/edit/delete,
      PIN/name lookup with "no matches" empty state). Depends on T030.
- [ ] T033 [US1] Register `/master-data/zones` in `FE/App.tsx` behind `RouteGuard`. Depends on T031.

**Checkpoint**: User Story 1 works - Zones and Places can be managed and looked up.

---

## Phase 4: User Story 2 - Maintain Schools and Place Them in a Zone (Priority: P1)

**Goal**: Admin/Director create/edit/move/deactivate Schools located in a Place; Zone follows Place;
a Manager sees only assigned Schools (none until US3) and edits only contact fields.

**Independent Test**: create a School in a Place, edit its profile, move it to a Place in another
Zone, deactivate it (spec.md US2 scenarios 1-5).

### Tests for User Story 2

- [ ] T034 [P] [US2] `BT/school/SchoolControllerTest.java`: create requires a Place (400 without);
      the response shows the Place and the Place's Zone; Admin/Director edit all profile fields;
      move to a Place in another Zone updates the derived Zone; Admin and Director can
      deactivate and reactivate (`SCHOOLS.EDIT`) while a Manager gets 403 on both; a Place with Schools cannot be deleted or moved to another
      Zone (FR-005a, 409); `TEACHER`/`SYSTEM` get 403 on every School endpoint; an unassigned Manager
      lists no Schools and gets 404 on a School id; changes appear in Change History. Also assert pagination: `size` above 100 is capped at 100, page 2 returns the next rows, and an empty result is `content: []` with `totalElements: 0`, not an error.
- [ ] T035 [P] [US2] `FE/features/schools/SchoolsPage.test.tsx`: list shows Place and Zone;
      create requires a Place; Manager fixture shows name/Place/billing contact read-only and only
      contact person/phone/address editable; Create/Deactivate hidden without the actions. Also assert the loading, empty ("No ... match your search") and error states.

### Implementation for User Story 2

- [ ] T036 [P] [US2] `BE/school/internal/School.java` + `SchoolRepository.java` (entity per the
      V11 `school` table with `@Version`; `existsByPlaceId`; paged query with optional `query`,
      `zoneId`, `placeId`, `active` filters and an optional `Set<UUID>` id restriction for scope).
- [ ] T037 [US2] `BE/school/internal/SchoolService.java`: list/get through the scope restriction
      (Admin/Director: unrestricted; others: union of `SchoolScopeProvider.visibleSchoolIds`,
      fail-closed when none), create, `update(...)` (a caller who is not Admin/Director may change
      only `contactPerson`, `contactPhone`, `address`, otherwise throw `ForbiddenFieldException`
      with "Managers can edit only a School's contact person, phone, and address."), `changePlace`
      (run every `SchoolChangeGuard.checkPlaceChange` when the Zone differs), `deactivate` (run
      every `SchoolDeactivationGuard.checkDeactivate`), `reactivate`; merge `SchoolViewEnricher`
      extras into the view; publish `EntityChanged(entityType="SCHOOL", ...)` per changed field.
      Depends on T016, T036.
- [ ] T038 [US2] Extend `PlaceService` (T026): delete and Zone change are refused with 409 while any
      School is located in the Place (`SchoolRepository.existsByPlaceId`) (FR-005a).
- [ ] T039 [US2] Public `BE/school/api/SchoolQueries.java`/`SchoolCommands.java`/`SchoolView.java`
      (`schoolsInZone`, `zoneOf(schoolId)`, `exists`, `isActive`) implemented by `SchoolService`.
      Depends on T037.
- [ ] T040 [US2] `BE/school/web/SchoolController.java` for the School endpoints in
      contracts/master-data-api.md, gated by `SCHOOLS.VIEW|CREATE|EDIT`, with Admin/Director-only role checks on
      `PUT /schools/{id}/place`, `POST /schools/{id}/deactivate` and `POST /schools/{id}/reactivate`
      (Manager also holds `SCHOOLS.EDIT`, so the role check is required). Depends on T037.
- [ ] T041 [US2] `FE/features/schools/schoolsApi.ts`, `SchoolsPage.tsx`, `SchoolDialog.tsx`
      (fields read-only for a Manager per the access model and role; Place picker with search).
- [ ] T042 [US2] Register `/master-data/schools` in `FE/App.tsx` behind `RouteGuard`.

**Checkpoint**: User Stories 1-2 work together.

---

## Phase 5: User Story 3 - Manage Manager Records and Assign Them (Priority: P1)

**Goal**: Admin/Director create Manager records, assign Managers to Zones and Schools, with the
"School's Manager covers its Zone" invariant enforced atomically and history retained.

**Independent Test**: two Managers in one Zone with three Schools; assign, refuse a non-covering
assignment, refuse removing a Zone that still has the Manager's Schools (spec.md US3).

### Tests for User Story 3

- [ ] T043 [P] [US3] `BT/organization/ManagerControllerTest.java`: create a Manager record only for
      a user with the Manager role (400 otherwise; 409 if already a Manager); list/get with Zones,
      School and Teacher counts; `PUT /managers/{id}/zones` assigns several Zones; a Zone may have
      several Managers; `MANAGER`/`TEACHER`/`SYSTEM` get 403; changes are audited. Also assert pagination: `size` above 100 is capped at 100, page 2 returns the next rows, and an empty result is `content: []` with `totalElements: 0`, not an error.
- [ ] T044 [P] [US3] `BT/organization/ManagerAssignmentInvariantTest.java`: assigning a School to a
      Manager who does not cover its Zone is 409 with no change; removing a Manager from a Zone
      where they still have Schools is 409 naming the Schools; moving a School to a Place in a Zone
      its Manager does not cover is 409 (`SchoolChangeGuard`); deleting a Zone with Managers is 409
      (`ZoneChangeGuard`); reassigning a School ends the old row and inserts the new one in one
      transaction and `manager-history` lists both with dates; unassigning (null) works; a failed
      change leaves both tables untouched. Add a concurrency case: two threads, one removing the Manager from Zone Z and one assigning a School in Z to that Manager - exactly one succeeds and the final state satisfies the invariant.
- [ ] T044a [P] [US3] `BT/organization/ManagerAccountSyncTest.java`: removing the Manager role or
      deactivating the user (spec 004 endpoints) makes their scope empty on the next request even
      with a still-valid token, sets `manager.active=false`, shows their Schools as "needs a
      Manager", keeps assignment history; reactivation or re-adding the role restores scope.
- [ ] T045 [P] [US3] `BT/organization/ManagerSchoolEditTest.java`: an assigned Manager lists and
      opens only their Schools; edits contact person/phone/address (200) but a changed name, Place
      or billing contact is 403 and unchanged; a School of another Manager is 404.
- [ ] T046 [P] [US3] `FE/features/managers/ManagersPage.test.tsx`: list with Zone chips and counts;
      create from a user picker; assign-Zones dialog shows the 409 reason inline; assign-School-
      Manager dialog lists only Managers covering the School's Zone. Also assert the loading, empty ("No ... match your search") and error states.

### Implementation for User Story 3

- [ ] T047 [US3] Create `backend/src/main/resources/db/migration/V12__create_organization_tables.sql`
      per data-model.md: `manager(id UUID PK, user_id UUID NOT NULL UNIQUE, active BOOLEAN NOT NULL
      DEFAULT true, version BIGINT NOT NULL, created_at TIMESTAMPTZ)`;
      `zone_manager_assignment(id UUID PK, zone_id UUID NOT NULL, manager_id UUID NOT NULL REFERENCES
      manager, starts_on DATE NOT NULL, ends_on DATE)` with a partial unique index on `(zone_id,
      manager_id) WHERE ends_on IS NULL`; `school_manager_assignment(id UUID PK, school_id UUID NOT
      NULL, manager_id UUID NOT NULL REFERENCES manager, starts_on DATE NOT NULL, ends_on DATE)` with
      a partial unique index on `(school_id) WHERE ends_on IS NULL`. No foreign key into `school`.
- [ ] T048 [P] [US3] `BE/organization/internal/Manager.java` + `ManagerRepository.java`.
- [ ] T049 [P] [US3] `BE/organization/internal/ZoneManagerAssignment.java`,
      `SchoolManagerAssignment.java` and their repositories (current = `ends_on IS NULL`; queries by
      manager, zone, school).
- [ ] T050 [US3] `BE/organization/internal/ManagerService.java`: create (validate the user holds
      `Role.MANAGER` via `identity.user`), list/get (display name/phone read from `identity`),
      `setZones` (end removed rows, refuse if the Manager still has current School rows in a removed
      Zone), `assignSchoolManager` (the Manager must have a current Zone row for
      `SchoolQueries.zoneOf(schoolId)`; end the old row and insert the new in one transaction),
      history queries; publish `EntityChanged` (`MANAGER`, `ZONE_MANAGER_ASSIGNMENT`,
      `SCHOOL_MANAGER_ASSIGNMENT`). Depends on T039, T048, T049. Take `PESSIMISTIC_WRITE` on the involved `manager` row(s) (in id order) before checking and changing anything, so concurrent assignment changes for one Manager run one at a time (FR-009).
- [ ] T050a [US3] Create `BE/organization/api/ManagerQueries.java`, `ManagerView.java` and
      `ManagerViewEnricher.java` (research.md section 17): `ManagerQueries.managerOfSchool(schoolId)`
      and `managerSummary(managerId)`; `ManagerService` implements the queries and merges every
      `ManagerViewEnricher`'s extras into Manager views. Depends on T050.
- [ ] T051 [US3] `BE/organization/api/ScopeView.java` (`orgWide`, `zoneIds`, `schoolIds`) and
      `ScopeQueries.java` with an implementation `ScopeService` computing the scope per request from
      the current rows: ADMIN/DIRECTOR -> `orgWide`; MANAGER -> their Manager's current Zones and
      Schools; union across roles (research.md section 3). No caching. Depends on T049. `scopeOf` MUST NOT trust the roles in the token for Manager scope: it checks, through `identity.user`'s public service, that the user currently holds `Role.MANAGER` and is active, and returns an empty scope otherwise (spec.md edge case on Manager role loss).
- [ ] T052 [US3] Implement the SPIs in `BE/organization/internal/`: `SchoolScopeProviderImpl`
      (Manager's current School ids), `SchoolChangeGuardImpl` (the School's current Manager must
      cover the new Zone), `ZoneChangeGuardImpl` (refuse delete while Managers are assigned),
      `SchoolManagerEnricher` (adds `manager {id, displayName}` to School views) and
      `ZoneManagerEnricher` (adds `managerCount`). Depends on T016, T050, T051. `SchoolManagerEnricher` also adds `needsManager: true` when the School's current Manager record is inactive, and the Schools list shows a "Needs a Manager" badge.
- [ ] T052a [US3] `BE/organization/internal/ManagerAccountSync.java`: `@ApplicationModuleListener`
      methods for `identity.activity.UserRoleChanged` (when `Role.MANAGER` is removed or added) and
      `AccountActivationChanged`, setting `manager.active` accordingly and never touching assignment
      rows or history; dedup by event id. Depends on T048.
- [ ] T053 [US3] `BE/organization/web/ManagerController.java` for the Manager endpoints in
      contracts/master-data-api.md, gated by `MANAGERS.VIEW|CREATE|EDIT`, including
      `PUT /schools/{id}/manager` and `GET /schools/{id}/manager-history`. Depends on T050.
- [ ] T054 [US3] `FE/features/managers/managersApi.ts`, `ManagersPage.tsx`, `ManagerDialog.tsx`,
      `AssignZonesDialog.tsx`.
- [ ] T055 [US3] `FE/features/schools/AssignSchoolManagerDialog.tsx` and a "Manager" row action in
      `SchoolsPage.tsx` (Admin/Director only, gated by `MANAGERS.EDIT`). Depends on T041, T054.
- [ ] T056 [US3] Register `/master-data/managers` in `FE/App.tsx` behind `RouteGuard`.

**Checkpoint**: User Stories 1-3 work together; Managers can now see their Schools.

---

## Phase 6: User Story 4 - Manage Teachers, Status and Interim Placement (Priority: P1)

**Goal**: Admin/Director create Teachers, move them along the status machine, place/move/schedule
them in Schools; Managers see and edit contact details of Teachers in their Schools only.

**Independent Test**: create a Teacher, place them, activate, move to another School; the first
Manager loses sight and the second gains it (spec.md US4).

### Tests for User Story 4

- [ ] T057 [P] [US4] `BT/teacher/TeacherStatusMachineTest.java` (unit): allowed transitions
      `IN_TRAINING -> ACTIVE`, `ACTIVE <-> ON_LEAVE`, any -> `EXITED`; every other pair (including
      anything from `EXITED`, and `ACTIVE -> IN_TRAINING`) throws `ConflictException` "A Teacher
      cannot move from X to Y."
- [ ] T058 [P] [US4] `BT/teacher/TeacherPlacementServiceTest.java`: place with today's date; move
      with a past date after the current start (old row ends the day before); a date earlier than
      the current start is 409; a date equal to the current start marks the old row `CORRECTED` and
      inserts the replacement; a future date schedules (current row `ends_on` = day before, nothing
      changes today), `pending` is returned, cancelling restores `ends_on` NULL and marks the row
      `CANCELLED`; a second scheduled move replaces the first; an exited Teacher or inactive School
      is 409; placements never overlap (database exclusion constraint also verified).
- [ ] T059 [P] [US4] `BT/teacher/TeacherControllerTest.java`: Admin/Director create/edit/list;
      create with a `userId` links the account (must hold the Teacher role, unique, 409 otherwise);
      status endpoint enforces the machine and exit ends the placement, cancels a scheduled one and
      clears `user_id`; a Manager lists/opens only Teachers placed today in their Schools, can edit
      contact fields (200) but cannot create (403), change status, or move placement (403); an
      unplaced Teacher is invisible to Managers and visible to Admin/Director; `TEACHER`/`SYSTEM`
      get 403 on the management endpoints; responses contain no salary field; changes are audited. Also assert pagination: `size` above 100 is capped at 100, page 2 returns the next rows, and an empty result is `content: []` with `totalElements: 0`, not an error.
- [ ] T060 [P] [US4] `BT/teacher/SchoolDeactivationGuardTest.java`: deactivating a School with an
      active or scheduled-incoming Teacher is 409 listing the cause; succeeds once they are moved
      or exited.
- [ ] T061 [P] [US4] `FE/features/teachers/TeachersPage.test.tsx`: list with status chips and
      "interim placement" label; create/edit dialogs; status dialog offers only allowed next
      statuses; placement dialog with date shows scheduled state and cancel; Manager fixture shows
      no Create/Status/Placement controls. Also assert the loading, empty ("No ... match your search") and error states.

### Implementation for User Story 4

- [ ] T062 [US4] Create `backend/src/main/resources/db/migration/V13__create_teacher_tables.sql`
      per data-model.md: `teacher(id UUID PK, name VARCHAR(160) NOT NULL, phone VARCHAR(20), email
      VARCHAR(200), address TEXT, status VARCHAR(20) NOT NULL, status_effective_on DATE NOT NULL,
      user_id UUID UNIQUE, version BIGINT NOT NULL, created_at, updated_at TIMESTAMPTZ)`;
      `teacher_placement(id UUID PK, teacher_id UUID NOT NULL REFERENCES teacher, school_id UUID NOT
      NULL, starts_on DATE NOT NULL, ends_on DATE, status VARCHAR(12) NOT NULL, created_at
      TIMESTAMPTZ)` with an exclusion constraint on `(teacher_id WITH =, daterange(starts_on,
      coalesce(ends_on,'infinity'),'[]') WITH &&) WHERE (status = 'ACTIVE')` (needs the
      `btree_gist` extension) and an index on `(school_id)`; and `teacher_salary_history(id UUID PK,
      teacher_id UUID NOT NULL REFERENCES teacher, amount NUMERIC(12,2) NOT NULL CHECK (amount >=
      0), effective_on DATE NOT NULL, recorded_by UUID NOT NULL, created_at TIMESTAMPTZ)` with index
      `(teacher_id, effective_on DESC)` (the salary table is used from US9). Begin the migration with `CREATE EXTENSION IF NOT EXISTS btree_gist;` and a SQL comment noting that a database user without extension rights needs this run once by a privileged user (research.md section 6).
- [ ] T063 [P] [US4] `BE/teacher/internal/Teacher.java` (entity with `@Version`, the status
      transition table as a static method, `status_effective_on`) + `TeacherRepository.java`.
- [ ] T064 [P] [US4] `BE/teacher/internal/TeacherPlacement.java` (status enum `ACTIVE`, `CANCELLED`,
      `CORRECTED`) + `TeacherPlacementRepository.java` (placement in effect on a date; pending row;
      Teachers placed at a School on a date).
- [ ] T065 [US4] `BE/teacher/internal/TeacherService.java`: create/edit (contact fields, `version`),
      list/get through the scope restriction (T067), `changeStatus` (machine from T063; EXITED ends
      the current placement on the effective date, cancels any scheduled one, clears `user_id`),
      `linkUser` (user must hold `Role.TEACHER`, not already linked, Teacher not exited); publishes
      `EntityChanged` for `TEACHER`. Depends on T063, T050a and T067 (the scope-filtered list lives here).
- [ ] T066 [US4] `BE/teacher/internal/TeacherPlacementService.java` implementing research.md
      section 6 exactly (immediate, scheduled, cancel, same-day correction, refusal rules,
      School must exist and be active via `SchoolQueries`); publishes `EntityChanged` for
      `TEACHER_PLACEMENT`. Depends on T039, T064.
- [ ] T067 [US4] `BE/teacher/api/TeacherScopeQueries.java` + implementation:
      `teacherIdsInScope(userId, roles)` and `isTeacherInScope(userId, roles, teacherId)` built on
      `organization.api.ScopeQueries` (Admin/Director: all Teachers including unplaced; Manager:
      Teachers whose `ACTIVE` placement today is in `schoolIds`; Teacher role: only the Teacher
      linked to the caller; System: none). Depends on T051, T064.
- [ ] T068 [US4] `BE/teacher/internal/`: `TeacherSchoolDeactivationGuard` (implements
      `SchoolDeactivationGuard`: refuse if any Teacher has an `ACTIVE` placement at the School today
      or in the future) and `SchoolTeacherCountEnricher` (adds `teacherCount` to School views).
      Depends on T016, T064. Also add `ManagerTeacherCountEnricher` implementing `organization.api.ManagerViewEnricher`, which adds `teacherCount` to Manager views.
- [ ] T069 [US4] Public `BE/teacher/api/TeacherQueries.java`, `TeacherCommands.java`, `TeacherView.java`
      (no salary member by design) implemented by the services. Depends on T065, T066.
- [ ] T070 [US4] `BE/teacher/web/TeacherController.java` for the Teacher endpoints in
      contracts/master-data-api.md (list/get/create/edit/status/user/placements/pending-cancel),
      gated by `TEACHERS.VIEW|CREATE|EDIT`, with Admin/Director-only operations checked by role as
      well as permission. Depends on T065, T066, T067. The Teacher list and detail take each Teacher's `manager` from `organization.api.ManagerQueries.managerOfSchool` (T050a).
- [ ] T071 [US4] `FE/features/teachers/teachersApi.ts`, `TeachersPage.tsx`, `TeacherDialog.tsx`,
      `StatusDialog.tsx`, `PlacementDialog.tsx` (date picker, scheduled-move banner with cancel,
      "interim placement" label everywhere a placement is shown, dates DD/MM/YYYY).
- [ ] T072 [US4] Register `/master-data/teachers` in `FE/App.tsx` behind `RouteGuard`.

**Checkpoint**: User Stories 1-4 work together.

---

## Phase 7: User Story 5 - Manager Sees Only Their Assigned Data Everywhere (Priority: P1)

**Goal**: prove, across every list/search/detail/count, that scope holds, and expose the shared
scope API and the dashboard scope endpoint.

**Independent Test**: two Managers in different Zones never see each other's Schools or Teachers,
including through filters, counts and guessed ids (spec.md US5).

### Tests for User Story 5

- [ ] T073 [P] [US5] `BT/organization/MasterDataScopeBoundaryTest.java` (Testcontainers, real HTTP):
      two Managers with different Zones/Schools/Teachers; for each Manager call every Schools and
      Teachers list with and without `query`, `status`, `zoneId`, `schoolId`, `active` filters and
      page sizes, plus detail endpoints for the other Manager's ids - assert only own records, 404
      for foreign ids with a body identical to a nonexistent id, and `totalElements` equal to own
      counts; Admin and Director see everything; a Manager+Director user sees everything; a Teacher
      user and a System user get 403 on management lists; reassigning a School to the other Manager
      changes both Managers' results on the next request without signing in again (FR-022). Add a case: a Teacher who moved from Manager A's School to Manager B's School shows each Manager only the placements at their own Schools, while Admin sees all.
- [ ] T074 [P] [US5] `BT/organization/ScopeQueriesTest.java`: `ScopeQueries.scopeOf` for each role and
      multi-role unions; a Manager with no assignments gets an empty scope; ended assignment rows
      are excluded.

### Implementation for User Story 5

- [ ] T075 [US5] `BE/organization/web/ScopeController.java`: `GET /api/v1/me/scope` returning the
      caller's `{orgWide, zoneCount, schoolCount, zones[{id,name}]}` from `ScopeQueries` (no other
      user's data). Depends on T051.
- [ ] T076 [US5] Review every School and Teacher read path (lists, detail, enrichers, counts,
      placements, history) and fix any that load a record outside the scoped query; add the rule
      "later modules MUST use `ScopeQueries`/`TeacherScopeQueries`" as a check in
      `BT/MasterDataModuleRulesTest.java` (no other package queries `school`/`teacher` tables
      directly). Depends on T073.

**Checkpoint**: scope is proven; later specs can reuse `ScopeQueries` and `TeacherScopeQueries`.

---

## Phase 8: User Story 6 - Manager Dashboard (Priority: P2)

**Goal**: the Manager dashboard shows assigned Zone, School and Teacher counts with links.

### Tests

- [ ] T077 [P] [US6] `FE/dashboards/ManagerDashboard.test.tsx`: shows the three counts from
      `/api/v1/me/scope` and `/api/v1/teachers?size=1`; links open the filtered lists; no
      assignments shows the empty state; a Manager+Director fixture shows both sets of widgets
      clearly labelled.

### Implementation

- [ ] T078 [US6] Replace the placeholder widgets in `FE/dashboards/ManagerDashboard.tsx` with real
      "Assigned zones", "Assigned schools" and "Assigned teachers" cards (loading/empty/error
      states, links to `/master-data/schools` and `/master-data/teachers`); keep the other
      placeholders. Depends on T075, T070.

---

## Phase 9: User Story 7 - Teacher Views Their Own Profile (Priority: P2)

**Goal**: ACCOUNT -> My Profile shows the Teacher's own details and nothing else.

### Tests

- [ ] T079 [P] [US7] `BT/teacher/TeacherMeTest.java`: a linked Teacher user gets their own name,
      contact, status and current School (no salary field); a Teacher user with no record gets 404
      `"Your profile has not been set up yet."`; a Teacher cannot reach any `/teachers` management
      endpoint (403); no user can read another Teacher through `/teachers/me`.
- [ ] T080 [P] [US7] Extend `FE/account/ProfilePage.test.tsx`: a Teacher fixture renders the profile
      block; "not set up yet" renders a message not an error; non-Teacher roles do not call
      `/teachers/me`.

### Implementation

- [ ] T081 [US7] `BE/teacher/web/TeacherMeController.java`: `GET /api/v1/teachers/me` resolving by
      `teacher.user_id` = JWT subject. Depends on T065.
- [ ] T082 [US7] Add a Teacher profile block (name, phone, email, status, current School labelled
      interim) to `FE/account/ProfilePage.tsx`, loaded only when the user holds the Teacher role.
      Depends on T081.

---

## Phase 10: User Story 8 - Bulk-Import Places (Priority: P2)

**Goal**: Admin/Director import many Places with a per-row report.

### Tests

- [ ] T083 [P] [US8] `BT/school/PlaceBulkImportTest.java`: 20 rows with 3 bad PINs/missing names and
      2 repeats of existing Places yields 15 `ADDED`, 2 `ALREADY_EXISTS`, 3 `REJECTED` with row
      numbers and reasons; a repeat earlier in the same file is `ALREADY_EXISTS`; re-importing adds
      0; same name with a different PIN (or vice versa) is added; empty list, 5,001 rows or unknown
      `zoneId` is 400 with nothing created; `MANAGER`/`TEACHER`/`SYSTEM` get 403; 1,000 rows finish
      in under 30 s (SC-004); imports are audited.
- [ ] T084 [P] [US8] `FE/features/zones/BulkImportPlacesDialog.test.tsx`: parses pasted
      `name,pinCode` lines into rows, submits, and shows the per-row report; blank lines ignored;
      oversize input blocked client-side with the same message.

### Implementation

- [ ] T085 [US8] `BE/school/internal/PlaceBulkImportService.java` per research.md section 12 (load
      the Zone's existing `(lower(name), pin_code)` set once; classify each row; `saveAll` the
      `ADDED` rows in one transaction; publish one `EntityChanged` per added Place) and
      `POST /api/v1/places/bulk-import` in `PlaceController`, gated by `ZONES.CREATE`. Depends on T026.
- [ ] T086 [US8] `FE/features/zones/BulkImportPlacesDialog.tsx` opened from `PlacesPanel` (gated by
      `ZONES.CREATE`). Depends on T032, T085.

---

## Phase 11: User Story 9 - Salary History (Priority: P2)

**Goal**: Admin/Director record dated salary changes; "current" and "as of" queries; everyone else
never receives salary.

### Tests

- [ ] T087 [P] [US9] `BT/teacher/SalaryHistoryTest.java`: record 20,000 effective 2026-04-01 then
      22,000 effective 2026-10-01; list shows both and `current` 22,000; `asOf=2026-06-15` ->
      20,000, `asOf=2026-10-15` -> 22,000, `asOf=2026-01-01` -> `{"amount": null}`; a correction is a
      new row (earlier row unchanged, no update/delete endpoint exists); negative amount is 400;
      `MANAGER`/`TEACHER`/`SYSTEM` get 403 on every salary endpoint; no other Teacher or School
      response (list, detail, `/teachers/me`) contains a salary member; an Admin sees the change in
      Change History and a System user does not (T010).
- [ ] T088 [P] [US9] `FE/features/teachers/SalaryPanel.test.tsx`: shows history and current amount
      in rupees with Indian grouping; add-entry form; "as of" lookup shows "no salary recorded";
      the panel is not rendered without `TEACHER_SALARY` access.

### Implementation

- [ ] T089 [US9] `BE/teacher/internal/SalaryHistoryEntry.java`, `SalaryHistoryRepository.java`,
      `SalaryService.java` (append-only; `current`, `asOf(date)`; publishes `EntityChanged(
      entityType="TEACHER_SALARY", field="salary")` with before = previous current amount or null).
      Depends on T062, T063.
- [ ] T090 [US9] `BE/teacher/web/TeacherSalaryController.java` for the three salary endpoints,
      gated by `TEACHER_SALARY.VIEW|CREATE` (Admin/Director by default); salary is never added to
      any existing view. Depends on T089.
- [ ] T091 [US9] `FE/features/teachers/SalaryPanel.tsx` (history table, add form, as-of lookup),
      rendered inside the Teacher detail only when the access model grants `TEACHER_SALARY`; shared
      `FE/features/teachers/formatters.ts` for Indian-grouped rupees and DD/MM/YYYY dates. Depends
      on T071, T090.

---

## Final Phase: Polish & Cross-Cutting Concerns

- [ ] T092 [P] Add axe-core accessibility cases for `ZonesPage`, `SchoolsPage`, `ManagersPage`,
      `TeachersPage`, the Teacher profile block, `BulkImportPlacesDialog` and the new dialogs, in
      both themes, to `FE/a11y/a11y.test.tsx`, asserting zero critical WCAG 2.2 AA violations.
- [ ] T093 [P] Extend `BE/identity/devseed/DevDataSeeder.java` (still gated by
      `hls.seed.demo-data=true`, idempotent) with one Zone, two Places, two Schools, a Manager record
      for Manoj with Zone/School assignments, and two Teachers (one linked to Tara's user) via the
      public APIs.
- [ ] T094 [P] Append a "005 Master Data" folder to `postman/HLS.postman_collection.json` (via the
      generator approach used for 001-004): Zones, Places (lookup, add, bulk import), Schools,
      Managers/assignments, Teachers (status, placements, user link), `me/scope`, `teachers/me`,
      salary endpoints; add `zoneId`, `placeId`, `schoolId`, `managerId`, `teacherId` collection
      variables set from create responses.
- [ ] T095 [P] Run quickstart.md's eight scenarios end-to-end against the running app and record the
      results (including timing the SC-001 ten-minute setup and the SC-004 1,000-row import). Record whether `btree_gist` was already present in the environment used. Time the Manager dashboard against SC-007 (3 seconds) by hand.
- [ ] T096 [P] Update `docs/spec-roadmap.md` row 005 to "Implemented" once every checkpoint above has
      passed, and note the new shared APIs (`ScopeQueries`, `TeacherScopeQueries`) for specs 008+.
- [ ] T097 Run the full backend suite (`mvn test`, including `ApplicationModulesTest` and
      `MasterDataModuleRulesTest`) and the full frontend suite (`npm run test`, including T092) and
      confirm both are green.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: none. **Foundational (Phase 2)**: depends on Setup; blocks every story.
- **US1 (Phase 3)**: depends on Foundational. First increment: MVP.
- **US2 (Phase 4)**: depends on US1 (Places) and the SPIs (T016). Managers see nothing until US3.
- **US3 (Phase 5)**: depends on US2 (`SchoolQueries`) and US1 (`ZoneQueries`).
- **US4 (Phase 6)**: depends on US3 (`ScopeQueries`) and US2 (School existence/active).
- **US5 (Phase 7)**: depends on US2-US4 (it verifies and exposes what they built).
- **US6 (Phase 8)** depends on US5 (T075) and US4 (T070); **US7 (Phase 9)** depends on US4;
  **US8 (Phase 10)** depends on US1 only (can run any time after US1); **US9 (Phase 11)** depends
  on US4.
- **Polish**: depends on every story being complete.

### Parallel Opportunities

- Foundational: T006, T009, T011, T012, T015, T018 can run in parallel once their dependencies land;
  audit work (T007-T012) is independent of the permission/nav work (T003-T006) and of T013-T016.
- Within a story: all `[P]` test tasks together; entity/repository tasks (T023/T024, T048/T049,
  T063/T064) together; frontend API client and tests while backend services are built.
- After US4: US6, US7, US8 and US9 touch disjoint files and can be staffed in parallel.

---

## Parallel Example: User Story 3

```bash
# Tests together:
Task: "ManagerControllerTest in BT/organization/ManagerControllerTest.java"
Task: "ManagerAssignmentInvariantTest in BT/organization/ManagerAssignmentInvariantTest.java"
Task: "ManagerSchoolEditTest in BT/organization/ManagerSchoolEditTest.java"
Task: "ManagersPage.test.tsx in FE/features/managers/"
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. Phase 1 and Phase 2. 2. Phase 3 (US1). 3. **Stop and validate**: Zones and Places can be managed
and looked up by Admin/Director, and refused to everyone else.

### Incremental Delivery (the plan's five slices)

1. Foundation (Phases 1-2) -> 2. Zones and Places (US1, plus US8 any time after) -> 3. Schools and
Managers (US2, US3) -> 4. Teachers and scope (US4, US5, US9) -> 5. Experience (US6, US7) ->
Polish. Each slice leaves the full suites green and is demonstrable on its own.
