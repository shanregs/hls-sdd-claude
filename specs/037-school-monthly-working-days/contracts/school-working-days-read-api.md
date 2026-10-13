# Java contract: SchoolWorkingDays (published interface)

Package `com.hls.attendance.api` (a named interface of the `attendance` module). For module code only: payroll
(013a/013b), Manager attendance (032) and the attendance rollups (008) once each adopts it (FR-012). Nothing reads the
tables of `attendance.workingdays`.

```java
public interface SchoolWorkingDays {

    enum Source { STORED, CALENDAR_DERIVED }

    /** The working days a School counts in a month. Half days are possible, so the figure is a decimal. */
    record MonthDays(UUID schoolId, YearMonth month, BigDecimal workingDays, Source source) {}

    /** One School, one month. Never empty: falls back to the calendar-derived count. */
    MonthDays forMonth(UUID schoolId, YearMonth month);

    /** Many Schools, one month. One entry per School id, keyed by School. */
    Map<UUID, MonthDays> forMonth(Collection<UUID> schoolIds, YearMonth month);

    /** Many Schools, a whole year: twelve entries per School, January first. */
    Map<UUID, List<MonthDays>> forYear(Collection<UUID> schoolIds, int year);

    /** Many Schools, any run of months from {@code from} to {@code to}, both included, oldest first. */
    Map<UUID, List<MonthDays>> forRange(Collection<UUID> schoolIds, YearMonth from, YearMonth to);
}
```

## Behaviour

- A stored row returns its figure with `STORED`, even when the calendar would give a different count (spec User Story 3,
  scenario 1). A stored figure of 0 is returned as 0, `STORED`; the consumer decides what that means.
- A month without a row returns the number of working dates of that School's calendar (its override if it has one,
  otherwise the default) with `CALENDAR_DERIVED`. A missing row is neither an error nor zero.
- An unknown School id is treated as the default calendar, `CALENDAR_DERIVED`.
- The number of queries is fixed: one for the stored rows of all Schools in the range, one calendar read
  (`WorkingCalendar.workingDaysBySchool`) for the whole range. It does not grow with the number of Schools.
- Same School and month at the same moment give the same answer to every caller (FR-014); no caller-specific logic.
- No permission check: this is a module-to-module read; the screen permission applies only to the HTTP endpoints
  (FR-013). The answer holds School, month, figure and source only, never a person.
- Read-only transactions. A changed figure is visible to the next call; a consumer that has finalised a month keeps
  what it stored (the consumer's rule, not this interface's).

## Tests that pin the contract

- stored 26 vs calendar 27 returns 26 `STORED`;
- no row returns the calendar count `CALENDAR_DERIVED`, including a School with a weekly-off override;
- stored 0 returns 0 `STORED`;
- 50 Schools x 12 months issue the same number of queries as 1 School x 12 months;
- a deleted row returns the calendar count at once.
