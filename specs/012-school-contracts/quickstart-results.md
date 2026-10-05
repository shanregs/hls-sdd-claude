# Quickstart results: School Contracts (MoU) (spec 012)

2026-10-05. Branch `feature/012-school-contracts`. The scenarios of `quickstart.md` were checked two ways: by the
automated suites (every scenario has a test) and by a live API walkthrough against a freshly migrated, demo-seeded
backend on a throwaway database. The browser walkthrough was **not** done (see "Not done").

## Automated results

| Suite | Result |
| --- | --- |
| Backend, full run after the placement move (before any contract code) | 601 tests, 0 failures |
| Backend, spec 012 tests | `ContractApiTest` (15), `ContractAuditTest` (2), `PositionAssignmentTest` (7), `RemapTest` (5), `MappingApiTest` (4), `ContractListApiTest` (4), `SchoolContractsPublicApiTest` (2), `PlacementMigrationTest` (1), `SchoolBillingModuleRulesTest` (3): all green |
| Backend, existing placement, teacher, directory, deactivation and dev-seed tests | unchanged assertions, green (`TeacherPlacementServiceTest` 9, `TeacherDirectoryTest`, `SchoolDeactivationGuardTest`, `DevSeedTest`); `TeacherControllerTest` changed on purpose: a Zone Manager now maps Teachers in their own scope |
| Frontend, spec 012 | `ContractsListPage` (4), `SchoolContractPage` (6), `MapTeachersDialog` (4), axe in light and dark for the list, the School page and both dialogs (8) |
| Frontend, teachers and profile | green with the "interim" wording removed |

## Live API walkthrough (demo data, `V1`-`V21`)

| Scenario | Result |
| --- | --- |
| 1. Placements carried over, attendance unchanged (US2) | Tara, Meena and Karthik show the same School and start date as before; the seeded Demo School One had a "MoU pending" contract created by the migration path, then its MoU recorded by the seeder. Attendance and leave suites pass unchanged. |
| 2. MoU with the signing details (US1) | Demo School One: 4 Teachers, INR 15,000 each, signed by Manoj (Zone Manager) and Divya (Director), School signatory R. Kumar (Principal). |
| 3. Different salaries and the Zone Manager rule (US1) | A different-salary MoU for 3 Teachers (20,000, 21,000, 22,000) was recorded as Director; the previous contract showed "Ends soon" and ended the day before. Demo School Two (no Zone Manager) refused a contract: "This School has no Zone Manager yet." Manoj got 403 on create. |
| 4. Map recruited Teachers (US3) | Karthik, Meena and Tara hold positions 1 to 3 of the current MoU; position 4 is vacant. |
| 5. Re-map to a new MoU (US3) | As Manoj, `map-teachers` moved the three Teachers to the new MoU's positions in one call (200, 3 remapped). |
| 6. Maintain (US4) | The list shows Active / MoU pending with filled, vacant and not-mapped counts; Manoj sees only Demo School One (the other School is a 404); Teacher and System get 403. |
| 7. Constraints | Covered by tests: two simultaneous mappings to one position (one wins), signed rows cannot be updated or deleted even by SQL, every change audited. |
| 8. Boundary | `ApplicationModulesTest` and `SchoolBillingModuleRulesTest` pass: `teacher` never depends on `schoolbilling`. |

## Not done

- **Browser walkthrough** of the screens (mouse and phone width): not run in this session. The screens are covered by
  component tests and axe checks only.
- **Keyboard-only and screen-reader pass** (T043): not done; axe reports no critical violations in either theme.
- **Full suites after the review fixes**: not re-run on request; the affected classes were (`PositionAssignmentTest`, `RemapTest`, `MappingApiTest`, `ContractApiTest`, `TeacherControllerTest`, `TeacherPlacementServiceTest`, `ApplicationModulesTest`). The full backend (641) and frontend (54 files) suites were green before the review fixes.
