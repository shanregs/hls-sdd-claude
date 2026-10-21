# Contract: Module interfaces (the extension point)

Package `com.hls.reporting.api`. Implemented by the owning module in its own `internal` package; collected by `reporting` as
`List<TileSource>`, `List<ApprovalSource>` and `List<ManagerColumns>`. `reporting.api` depends only on `identity` types; no
source module is depended on by `reporting`.

## TileSource

```java
public interface TileSource {
    List<TileDefinition> tiles();                          // what this module offers; constant
    TileResult read(TileDefinition tile, TileContext ctx); // one tile; throw or time out = that tile shows ERROR
}

record TileDefinition(String key, Area area, int order, String label, PeriodKind periodKind,
                      String permissionModule, PermissionAction action, ScopeKind scope,
                      boolean zoneAware, Link link) {}
enum Area { MANAGERS, SCHOOLS, RECRUITMENT, TEACHERS, ATTENDANCE_LEAVE, PAYROLL }
enum ScopeKind { ALL, ORG_WIDE_ONLY }

interface TileContext {
    UUID userId(); Set<Role> roles(); LocalDate today(); YearMonth month(); Optional<UUID> zoneId();
    <T> T memo(String key, Supplier<T> compute);          // shared by tiles of one request
}

record TileResult(String key, State state, List<Value> values, boolean organizationWide, String note, Instant takenAt,
                  Table table) {
    static TileResult ok(String key, Value... values);     // the only way to carry a number
    static TileResult noData(String key, String note);
    static TileResult notAvailable(String key, String missing);
    static TileResult notWorkingDay(String key, String reason);
}
```

Rules for implementers: apply the caller's data scope (`ScopeQueries`, `TeacherScopeQueries`, the module's own guard) and never
widen it; reuse the module's existing figure service so the number equals the list; return `noData` for a zero denominator and
`notAvailable` for an optional provider that is missing; never return a count of 0 for a source that did not answer; do not
cache; read only (the call runs in a read-only transaction). The key and the link query are the module's promise that the list
shows the same records.

## ApprovalSource

```java
public interface ApprovalSource {
    String kind();                                   // MANAGER_LEAVE, FORGOTTEN_CHECK_IN, FINAL_STAGE_PROSPECT, ...
    String permissionModule(); PermissionAction action(); // Approve or Process on the owning module
    List<ApprovalItem> pending(UUID userId, Set<Role> roles);   // oldest first not required; reporting sorts
}
record ApprovalItem(String kind, UUID itemId, UUID requesterUserId, String requesterName, String subject,
                    LocalDate askedOn, Link link) {}
```

`reporting` checks the grant before calling, removes items whose `requesterUserId` is the caller, sorts by `askedOn`, totals per
kind and pages.

## ManagerColumns

```java
public interface ManagerColumns {
    List<Column> columns();                                           // key, label, link template with {managerId}
    Map<UUID, Map<String, Long>> figures(TileContext ctx, Set<UUID> managerUserIds); // bulk, fixed number of queries
}
```

## Implementations delivered by this spec

| Module | Classes | Existing service reused |
| --- | --- | --- |
| tasks | `TaskTiles`, `TaskApprovals`, `TaskManagerColumns` | `TaskQueries.summary`, per-assignee counts |
| recruitment.marketing | `MarketingTiles`, `FinalStageApprovals`, `MarketingManagerColumns` | `DashboardService`, prospect stage queries |
| recruitment | `RecruitmentTiles` | `RecruitmentDashboardService` |
| teacher | `TeacherTiles` | `TeacherRepository` counts within `TeacherScopeQueries` |
| attendance | `TeacherAttendanceTiles` | `AttendanceReadApi.teachersAwayOn`, `WorkingCalendar` |
| attendance.manager | `ManagerAttendanceTiles`, `ForgottenCheckInApprovals`, `ManagerAttendanceColumns` | missed check-in alerts, `ManagerAttendanceReadApi`, month state |
| leave | `TeacherLeaveTiles` | pending Teacher leave count |
| leave.manager | `ManagerLeaveTiles`, `ManagerLeaveApprovals` | pending and on-leave-today queries |
| substitution | `SubstitutionTiles` | `SubstitutionRepository` by status for today |
| payroll.run | `PayrollTiles`, `PayrollApprovals` | `RunService`, `OwedService`, month locks |

Later specs (015, 022, 024, 026, 027, 030) add their own class of the same kind; nothing in `reporting` changes. Small additive
read methods on the services above are allowed where a figure is not yet exposed (for example per-assignee task counts).
