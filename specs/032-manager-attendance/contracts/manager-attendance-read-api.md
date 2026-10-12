# Contract: Java interfaces of Manager attendance (for payroll, leave and notifications)

Server-side only; no HTTP exposure. All in `attendance.api` (a named interface) unless stated.

## `ManagerAttendanceReadApi` (payroll and reports)

```java
public interface ManagerAttendanceReadApi {

    /** The month's figures for one Manager. Never a bare number: a month with no information says so. */
    ManagerMonth month(UUID managerId, YearMonth month);

    /** Bulk form for a grid or a run; every asked Manager is in the map. A fixed number of queries. */
    Map<UUID, ManagerMonth> months(Collection<UUID> managerIds, YearMonth month);
}

public sealed interface ManagerMonth {

    record Found(
            YearMonth month,
            BigDecimal workingDays,      // default-calendar working days inside the Manager's employment
            BigDecimal daysWorked,       // a half day worked counts 0.5
            BigDecimal leavePaid,
            BigDecimal leaveUnpaid,      // Loss-of-Pay leave days (also part of unpaid)
            BigDecimal unpaid,           // leaveUnpaid + days with no check-in and no leave
            BigDecimal fieldDays,
            List<UnpaidDay> unpaidDays,  // each date with 1 or 0.5, for payroll to value per day
            List<LocalDate> pending,     // dates not yet over (today and later), not counted as unpaid
            boolean locked) implements ManagerMonth {}

    /** The Manager has no joining date (spec 005a): no day can be called unpaid. */
    record NoJoiningDate() implements ManagerMonth {}

    /** No such Manager. */
    record Unknown() implements ManagerMonth {}
}

public record UnpaidDay(LocalDate date, BigDecimal fraction) {}
```

Guarantees (each is a test):

1. The same records and calendar always give the same figures; a locked month returns the frozen figures even if the
   records or the calendar change later (and reopening returns live figures again).
2. A check-in without a check-out is a worked day; a check-in on a weekly off, a holiday or outside the Manager's
   employment changes no figure.
3. A working day with no check-in and no approved leave is `unpaid` 1; a Loss-of-Pay leave day is `unpaid`; any other
   approved leave is paid; a half-day leave day with a check-in is half leave plus half worked, and the leave half is
   unpaid only for Loss-of-Pay; with no check-in the other half is also unpaid.
4. A date after today, and today, is in `pending` and never in `unpaidDays`.
5. A Manager with no joining date gives `NoJoiningDate`, never `Found` with zero unpaid days.
6. The bulk form equals the one-at-a-time form for every Manager and runs in a fixed number of queries for 40 Managers.
7. Nothing here returns a location or a photo.

## `ManagerLeaveAttendance` (used by the leave module)

```java
public interface ManagerLeaveAttendance {

    /** One leave day to write: a date, whether it is a half day, and whether the leave type is Loss-of-Pay. */
    record LeaveDay(LocalDate date, boolean half, boolean unpaid) {}

    /** The working days in the range for a Manager (default calendar, inside their employment), oldest first. */
    List<LocalDate> workingDays(UUID managerId, LocalDate from, LocalDate to);

    /** Writes the days onto the Manager's records for an approved request. Fails with a conflict if any day is in a
     *  locked month or already has full-day leave; changes nothing then. */
    void apply(UUID requestId, UUID actor, UUID managerId, List<LeaveDay> days);

    /** Removes the leave of a request (revoked or cancelled); fails with a conflict in a locked month. */
    void remove(UUID requestId, UUID actor);
}
```

## `PendingManagerRequests` (an SPI the lock consults)

```java
public interface PendingManagerRequests {
    record Pending(String kind, UUID requestId, UUID managerId, LocalDate firstDate, LocalDate lastDate) {}

    /** The requests still waiting for a decision that touch the month. */
    List<Pending> pendingFor(YearMonth month);
}
```

`leave.manager` implements it for leave requests; attendance adds its own forgotten check-in requests. Locking collects
every implementation, so attendance never depends on `leave`.

## Events (published inside the writer's transaction)

| Event | Module | Carries | Becomes a notification for |
| --- | --- | --- | --- |
| `ManagerCheckInMissed` | attendance.api | manager id, date | the Manager, every active Admin and Director |
| `ManagerDayCorrected` | attendance.api | manager id, date, actor | the Manager |
| `ManagerRegularizationRequested` | attendance.api | request id, manager id, date | every active Admin and Director |
| `ManagerRegularizationDecided` | attendance.api | request id, manager id, date, decision, reason | the Manager |
| `ManagerLeaveRequested` | leave.api | request id, manager id, dates | every active Admin and Director (not the requester) |
| `ManagerLeaveDecided` | leave.api | request id, manager id, dates, decision, reason | the Manager |
| `ManagerLeaveCancelled` | leave.api | request id, manager id, dates | every active Admin and Director |

## Changes to spec 013a (`payroll.PayRulesImpl`)

- `unpaidDays` for a Manager: `ManagerMonth.Found` becomes `UnpaidDays.Found` with each `UnpaidDay` (date and fraction);
  `NoJoiningDate` and `Unknown` become `UnpaidDays.Unavailable` with a stated reason.
- `totalLossOfPay` for a Manager is then worked out the same way as a Teacher's (each unpaid day at that day's salary,
  summed, rounded once); it is `Cannot` when the month is unavailable or an unpaid day has no salary.
- `unpaidDays` for an Admin or Director (`Kind.STAFF`) is `UnpaidDays.Found` with an empty list by rule; their
  `totalLossOfPay` is `Found` with zero. This is a stated answer, not "not available".
- The working days a Manager is payable for are unchanged (013a counts them from the joining and exit dates).
