# Research: Enter a Whole Year on One Screen

Each item: decision, why, what else was considered.

## 1. Partial save: one transaction per month

- **Decision**: save each changed month in its own `TransactionTemplate` transaction, collect a result per month, and
  return `200` with the list (unless the whole request is invalid, see 4).
- **Why**: FR-009 says valid months stay stored when others fail, and one failed month must not roll back eleven good
  ones. The 037 write path already works per row.
- **Rejected**: one transaction for all months with a savepoint per month (more machinery, same result); all-or-nothing
  (the requester chose partial save on 2026-10-16).

## 2. Reuse the 037 write path, add one outcome type

- **Decision**: add `WorkingDaysService.saveMonth(actor, roles, schoolId, year, month, figure, version)` returning an
  outcome (`CREATED`, `UPDATED`, `UNCHANGED`, or a refusal carrying the current row). Internally it calls the existing
  `insert(...)`, the version check and `record(...)`, so audit and history are identical to the grid (FR-022, FR-023).
- **Why**: one place enforces the range, the history row and the audit event. The existing `create` and `edit` throw
  `Duplicate`, `Stale` and `RowDeleted`; `saveMonth` meets the same conditions and returns them as data so the loop
  continues.
- **Rejected**: calling `create` and `edit` from the new service and catching the exceptions per month. It works, but the
  "equal figure is unchanged" rule needs the current row, and an expected case is clearer as a return value.

## 3. The "equal figure is unchanged" rule (clarification 2026-10-16)

- **Decision**: after a failed create (a row exists) or a stale version, compare the current stored figure with the typed
  one. Equal: `UNCHANGED`, write nothing. Different: `ALREADY_EXISTS` or `STALE`, with the current row returned.
- **Why**: a retry after a dropped connection must not report conflicts for months the first attempt stored; two people
  entering the same figure should not be told they conflicted.
- **Race**: two creates for the same month at once: the unique key lets one in, and the other's
  `DataIntegrityViolationException` becomes "reload and compare", as `WorkingDaysService.create` already does.
- **Edge**: a retry of an **update** whose first attempt succeeded sees a newer version but the same figure, so it is
  also `UNCHANGED`.

## 4. What fails the whole request and what fails one month

- **Whole request (400 or 403)**: no View, or neither Create nor Edit; unknown School; School outside the caller's
  scope; year outside the window (current year minus 5 to plus 5, FR-003); a month number outside 1 to 12; the same
  month twice; more than 12 entries; missing fields. These are mistakes in the request or the caller's rights.
- **One month (a status in the 200 body)**: figure out of range or not a half step; version stale; row already exists
  with a different figure; row deleted by someone else; the caller lacks Create (new month) or Edit (stored month).
- **Why**: the screen can only act on month-level problems, and a permission that covers some months only (Create but
  not Edit) must not fail the whole request.
- **Year window**: because the whole request is refused outside the window, the window check runs once and the 037
  rule `checkNewYear` is applied at request level for both the read and the save. Existing rows outside the window stay
  editable in the grid, as in 037.

## 5. Per-month permission needs a non-throwing guard

- **Decision**: add `PermissionGuard.isGranted(roles, module, action)` returning a boolean, built on the same
  `permissionMatrixService.isGranted` that `require` uses. The controller still calls `require` for View and checks that
  Create or Edit is held; the service uses `isGranted` per month.
- **Why**: `require` throws, which cannot express "this month yes, that month no".
- **Rejected**: catching `AccessDeniedException` per month (exceptions as control flow).

## 6. Matching a month: by key, not by id

- **Decision**: the Save request names each month by `month` (1 to 12) and sends `version` (null for no figure). The
  server finds rows by `(schoolId, year, month)`.
- **Why**: a month with no figure has no id; one `findBySchoolIdAndYear` (already used by the year copy) serves the
  request.
- **Rejected**: sending row ids (cannot work for new months).

## 7. The read is built from existing pieces

- **Decision**: `loadYear` reads the School's rows for the year (`findBySchoolIdAndYear`), the calendar counts for the
  twelve months with `CalendarCounts.byMonth` (one `WorkingCalendar` call), the School through `SchoolDirectory`, and the
  last-changed names through `WorkingDaysViewFactory.userNames`.
- **Why**: no new query shape; the query count is fixed (SC-009); the hint equals the grid's `calendarDerived`.

## 8. Which months the user can edit

- **Decision**: the screen reads View, Create and Edit from the access model with `useGrantedActions` on the 037 route,
  as the grid does. A month with a stored figure is editable with Edit, a month without one with Create. The server
  repeats the check per month (`NOT_PERMITTED`).
- **Why**: Principle IV (hide what is not allowed) and Principle X (the server decides). The year screen is a child of the
  existing route and `isAuthorizedPath` already authorizes children of an authorized route, so no access-model change is
  needed.

## 9. Leaving with unsaved changes

- **Finding**: the app uses `BrowserRouter` (`main.tsx`), and React Router's `useBlocker` works only with a data router.
  Moving the whole app to a data router is out of scope.
- **Decision**: ask for confirmation (a small dialog) when the user changes the School or year and when they use the
  screen's own "Back to the grid" button, and add a `beforeunload` handler for closing or reloading the tab with unsaved
  changes. Navigation by the left menu and the browser back button is **not** intercepted; typed values live in page
  state only and are lost on leaving.
- **Impact on the spec**: FR-024 says leaving the screen asks for confirmation. This plan covers every way except the
  left menu and the browser back button. To settle at `/speckit-analyze`: accept the limit (and narrow FR-024), or take
  on a data-router migration as a separate change.

## 10. Layout without horizontal page scroll

- **Decision**: no table. Each month is a row (name, days in month, field, stored-figure or hint text) in a single column
  at phone width and two columns from the small breakpoint up. Error text wraps under the field.
- **Why**: a stacked list cannot overflow sideways. The page keeps `minWidth: 0` like the shell.
- **Check**: the real 375 px check is manual (quickstart step 8), like T045 of spec 037. In DevTools device mode, drag
  the content to scroll; the emulated scrollbar cannot be dragged.

## 11. Helpers

- **Decision**: both helpers are pure functions in `yearEntryState.ts` over the typed values. They fill empty, untyped
  months only and report the months they could not fill. Neither calls the server.
- **Why**: FR-017 says they never save and never overwrite; pure functions make that testable without a browser.

## 12. Idempotency without a key

- **Decision**: no idempotency key. The unique key on `(school_id, year, month)` and the "equal figure is unchanged" rule
  make a repeated Save harmless (decision 3).
- **Rejected**: a client-generated request id with a server-side table (new storage for a problem the data already
  solves).

## 13. No other module changes

Consumers read the figure through `attendance.api.SchoolWorkingDays` (037) and see these figures like any other
(FR-023). Payroll, Manager attendance and the rollups are not touched.

## 14. Names confirmed before coding (T001, 2026-10-16)

- Backend (`attendance.workingdays.internal`): `WorkingDaysService` (`insert`, `requireSchoolExists`, private `record`,
  `checkVersion`, `conflictFor`), `WorkingDaysRules.checkFigure` and `checkNewYear`, `CalendarCounts.byMonth` (package
  private, so `YearEntryService` sits in the same package), `WorkingDaysScope.requireSchool` (an out-of-scope School
  throws `NotFoundException`, so the year endpoints answer 404 for it, as the grid does),
  `WorkingDaysViewFactory.userNames`, `SchoolWorkingDaysRepository.findBySchoolIdAndYear`,
  `PermissionMatrixService.isGranted`. Controller base path `/api/v1/school-working-days`.
- `PermissionGuard` is used from `web` only; to keep `attendance.workingdays.internal` free of an `identity.permissions`
  dependency the controller computes `canCreate` and `canEdit` and passes them to the service.
- Frontend (`features/schoolWorkingDays`): `schoolWorkingDaysApi.ts` (`call`, `Outcome`), `workingDaysRules.ts`
  (`figureProblem`, `formatFigure`, `monthName`, `FINALISED_NOTICE`), `useGrantedActions` (`features/common`).
- Helper names in `yearEntryState.ts` are `fillWithCalendarCounts` and `fillSameFigure` (a `use` prefix would be read
  as a React hook by the lint rules).
- A month typed as something that cannot be a figure (for example `abc`) is held back in the browser and not sent; the
  other changed months are still sent. The server repeats the rule for anything that does arrive.
