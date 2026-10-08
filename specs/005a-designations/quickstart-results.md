# Quickstart results: Designations and Employment Details (spec 005a)

## Automated (2026-10-08)

| Check | Result |
| ----- | ------ |
| Frontend `tsc -b` (typecheck) | clean |
| Frontend `eslint .` | 0 errors (6 warnings in files this spec does not touch) |
| Frontend tests of this spec (`DesignationsPage`, `ManagerEmployment`, `TeacherEmployment`) | 18 pass |
| Backend: `DesignationApiTest`, `DesignationModuleRulesTest`, `EmployeeIdsAndInterfacesTest`, `ManagerEmploymentApiTest`, `ManagerDesignationOnDateTest`, `TeacherEmploymentApiTest`, `PermissionEligibilityTest`, `NavigationSectionOrderTest`, `MasterDataAccessModelTest`, `ApplicationModulesTest` | 62 tests pass |

In a full frontend run on this slow machine, three tests timed out (one of this spec, two of spec 023); the one of this
spec passes alone with the rest of its file. The full backend suite result after the rebase is recorded below.

## Manual scenarios (running app, quickstart.md)

Pending: no run against the running app yet.
