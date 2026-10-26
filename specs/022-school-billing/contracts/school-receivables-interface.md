# Contract: `schoolbilling.api.SchoolReceivables` and events

Public interface for 013b (payroll margin), 014 (reports) and 028 (dashboard). It checks no screen permission and holds no
person-level secrets beyond amounts; callers apply their own scope. Bulk-oriented: the number of queries does not depend on
the number of ids.

```java
public interface SchoolReceivables {

    /** What one Teacher was billed for the month: sum of the Teacher's lines in non-cancelled dues, net of nothing (adjustments are per due, not per line). */
    record TeacherBilled(UUID teacherId, YearMonth month, BigDecimal amount, List<UUID> schoolIds) {}

    /** One School and month: owed (due total less active adjustments), collected (allocations), balance, and whether it is overdue. */
    record SchoolMonth(UUID schoolId, YearMonth month, BigDecimal owed, BigDecimal collected,
                       BigDecimal balance, boolean overdue, LocalDate dueOn) {}

    /** A School's position across all months. */
    record SchoolBalance(UUID schoolId, BigDecimal owed, BigDecimal collected, BigDecimal outstanding,
                         BigDecimal overdue, BigDecimal advanceCredit) {}

    Map<UUID, List<TeacherBilled>> billedPerTeacher(Collection<UUID> teacherIds, YearMonth from, YearMonth to);

    Map<UUID, List<SchoolMonth>> schoolMonths(Collection<UUID> schoolIds, YearMonth from, YearMonth to);

    Map<UUID, SchoolBalance> balances(Collection<UUID> schoolIds, LocalDate asOf);
}
```

A month with no non-cancelled due yields no entry (not zero). A cancelled due contributes nothing.

## Events published by `schoolbilling.api`

| Event | When | Consumers |
| --- | --- | --- |
| `MonthlyDueRaised(dueId, schoolId, month, total)` | After a due is raised | 028 tiles (later) |
| `MonthlyDueOverdue(dueId, schoolId, month, balance, daysOverdue, noManager)` | The job found a newly overdue due | `notification` (Director and Admin when `noManager`) |

## Interfaces used (existing)

`TeacherPlacementSource.spansOverlapping`, `SchoolContracts.contractsOverlapping` / `positionOf`, `WorkingCalendar.workingDays`, `SchoolWorkingDays.forMonth`, `ManagerQueries.managerOfSchool`, `ScopeQueries.scopeOf`, `TaskCreation.createFollowUp` / `complete` / `cancel` / `reassign`.
