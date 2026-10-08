# Contracts: Designations and Employment Details (spec 005a)

HTTP endpoints, then the Java interfaces other modules read (spec 013a and 032 build on these). Errors follow the
house shapes: 400 invalid input (message names the problem), 403 forbidden, 404 not found or out of scope, 409
conflict or stale `version`.

## HTTP: designations (module `designation`)

| Method and path | Permission | Notes |
| --- | --- | --- |
| `GET /api/v1/designations?kind=&status=` | `DESIGNATIONS` VIEW | list: id, name, kind, retired, holders, version |
| `GET /api/v1/designations/summary` | `DESIGNATIONS` VIEW | `{teachersMissingDesignation, managersMissingDesignation, managersMissingJoiningDate, managersMissingExitDate}` |
| `GET /api/v1/designations/options?kind=MANAGER` | `DESIGNATIONS` EDIT | active designations of the kind, for the pickers |
| `POST /api/v1/designations` | `DESIGNATIONS` CREATE | body `{name, kind}`; 201 |
| `PUT /api/v1/designations/{id}` | `DESIGNATIONS` EDIT | body `{name, kind, retired, version}`; rename, retire, reactivate; a changed `kind` is refused (409) once held |
| `DELETE /api/v1/designations/{id}` | `DESIGNATIONS` EDIT | always 409 "A designation is never deleted." |

## HTTP: people (modules `organization` and `teacher`)

| Method and path | Permission | Notes |
| --- | --- | --- |
| `GET /api/v1/managers?missing=true` | `MANAGERS` VIEW | only Managers with no designation, no joining date, or inactive with no exit date |
| `GET /api/v1/managers`, `/{id}` | `MANAGERS` VIEW | `ManagerView.employment` added |
| `PUT /api/v1/managers/{id}/employment` | `DESIGNATIONS` EDIT | body `{employeeId, joiningDate, exitDate, version}`; null clears |
| `POST /api/v1/managers/{id}/designation` | `DESIGNATIONS` EDIT | body `{designationId, effectiveOn}`; appends a history row |
| `GET /api/v1/teachers?missingDesignation=true` | `TEACHERS` VIEW | scoped as before |
| `GET /api/v1/teachers`, `/{id}` | `TEACHERS` VIEW | `TeacherView.employment` added (null on `/me`) |
| `PUT /api/v1/teachers/{id}/employment` | `DESIGNATIONS` EDIT | body `{designationId, employeeId, version}`; scope as for the Teacher |

`employment` on a Manager: `{employeeId, joiningDate, exitDate, designation: {id, name, retired} | null,
history: [{designationId, name, effectiveOn, recordedAt}] (detail only), missing: ["DESIGNATION","JOINING_DATE","EXIT_DATE"]}`.
On a Teacher: `{employeeId, designation: {id, name, retired} | null, missing: ["DESIGNATION"]}`.

## Java: `designation.api` (named interface)

```java
public interface DesignationDirectory {
    enum Kind { TEACHER, MANAGER }
    record DesignationInfo(UUID id, String name, Kind kind, boolean retired) {}

    Optional<DesignationInfo> find(UUID designationId);                    // exists? kind? retired?
    Map<UUID, DesignationInfo> findAll(Collection<UUID> designationIds);   // bulk, one query
    /** For writers: the designation must exist, match the kind and be active; marks it held. */
    DesignationInfo assign(UUID designationId, Kind kind);
}

public interface EmployeeIds {
    enum PersonKind { TEACHER, MANAGER }
    /** Validates the format and claims the id for the person; null or blank releases it. Returns the normalized id or null. */
    String claim(PersonKind kind, UUID personId, String personName, String employeeId);
}

/** Implemented by organization and teacher; read by the designation list for counts. */
public interface HolderCounter {
    DesignationDirectory.Kind kind();
    Map<UUID, Long> holdersByDesignation();
    long missingDesignation();
    long missingJoiningDate();   // Managers; 0 for Teachers
    long missingExitDate();      // inactive Managers; 0 for Teachers
}
```

## Java: `organization.api.ManagerQueries` (added)

```java
record ManagerEmployment(UUID managerId, String employeeId, LocalDate joiningDate, LocalDate exitDate, boolean active) {}

Optional<UUID> designationOn(UUID managerId, LocalDate date);                    // latest row on or before date
Map<UUID, UUID> designationsOn(Collection<UUID> managerIds, LocalDate date);     // bulk; absent key = none
Map<UUID, ManagerEmployment> employment(Collection<UUID> managerIds);            // bulk; employee id, joining, exit
Map<UUID, Long> holderCountsByDesignation(Collection<UUID> designationIds);      // Managers whose designation today is it
```

## Java: `teacher.api.TeacherDirectory` (added)

```java
record TeacherEmployment(UUID teacherId, UUID designationId, String employeeId) {}

Optional<UUID> currentDesignation(UUID teacherId);
Map<UUID, UUID> currentDesignations(Collection<UUID> teacherIds);                // bulk; absent key = none
Map<UUID, TeacherEmployment> employment(Collection<UUID> teacherIds);
Map<UUID, Long> holderCountsByDesignation(Collection<UUID> designationIds);
```

Guarantees (each is a test): bulk forms use a fixed number of queries (SC-007); `designationOn` follows the
(effective date, recorded order) rule (SC-008); no method returns a salary or any other spec 005 field.
