# Data Model: Enter a Whole Year on One Screen

**No new tables, columns, constraints or migrations.** The feature reads and writes the two tables of spec 037
(`school_working_days` and `school_working_days_history`, see
[the 037 data model](../037-school-monthly-working-days/data-model.md)).

## Used as is

- `school_working_days`: unique on `(school_id, year, month)`; `version` is the optimistic-lock value the screen carries
  for each stored month.
- `school_working_days_history`: one `CREATE` or `EDIT` row per month created or updated here (`COPY` stays for the year
  copy). Unchanged and refused months add nothing.
- Central audit: `EntityChanged` with entity type `SCHOOL_WORKING_DAYS`, written exactly as the grid writes it.

## Year Entry View (not stored)

Returned by `GET /year`. For one School and year:

| Field | Meaning |
|---|---|
| schoolId, schoolName, zoneName | the School chosen |
| year | the year chosen (inside the window) |
| months[12] | one entry per month, January to December |
| months[].month | 1 to 12 |
| months[].daysInMonth | 28 to 31, leap years respected |
| months[].calendarDerived | working dates from the spec 008 calendar for that School and month (the hint) |
| months[].id, workingDays, version | the stored figure and its version; all `null` when no figure is stored |
| months[].updatedBy, updatedAt | who last changed the stored figure and when; `null` when none |

## Year Save Request (not stored)

`PUT /year`: `schoolId`, `year`, and `months[]` holding the **changed** months only: `month`, `workingDays`, `version`
(`null` when the screen loaded no figure for that month). 1 to 12 entries, no month twice.

## Year Save Result (not stored)

Per requested month: `month`, `status` (`CREATED`, `UPDATED`, `UNCHANGED`, `NOT_SAVED`) and, for `NOT_SAVED`, a `reason`
code (`INVALID_FIGURE`, `STALE`, `ALREADY_EXISTS`, `ROW_DELETED`, `NOT_PERMITTED`) with a message, plus the month's
**current stored state** (same shape as a Year Entry View month) so the screen continues without a reload. Totals:
`created`, `updated`, `unchanged`, `notSaved`.

## Validation rules (all inherited)

- Figure: 0 up to the days in that month, in steps of 0.5 (`WorkingDaysRules.checkFigure`, spec 037 FR-003).
- Year: current year minus 5 to plus 5, judged on `WorkingCalendar.businessToday()` (spec 037 FR-029); the whole request
  is refused outside it.
- School: must exist and be inside the caller's scope.
- Month status rules:

| Stored state | Typed vs stored | Version sent | Result |
|---|---|---|---|
| none | not applicable | none | `CREATED` (needs Create) |
| none, but a row appeared meanwhile | equal | none | `UNCHANGED` |
| none, but a row appeared meanwhile | different | none | `NOT_SAVED` `ALREADY_EXISTS` |
| exists, version matches | equal | match | `UNCHANGED` |
| exists, version matches | different | match | `UPDATED` (needs Edit) |
| exists, version differs | equal | differs | `UNCHANGED` |
| exists, version differs | different | differs | `NOT_SAVED` `STALE` |
| deleted meanwhile | any | a version | `NOT_SAVED` `ROW_DELETED` (never recreated) |
| any | out of range or not a half step | any | `NOT_SAVED` `INVALID_FIGURE` |
| any | any | caller lacks the needed action | `NOT_SAVED` `NOT_PERMITTED` |

The figure is validated first, then permission, then the stored-state rules.

## Front-end state (not stored, not persisted)

`loaded` (the view), `typed` (month to text), derived `dirty` months (typed differs from loaded), `results` (the last
save result) and the set of helper-filled months. Lost when the user leaves; the leave rules are in
[research.md](./research.md) section 9.
