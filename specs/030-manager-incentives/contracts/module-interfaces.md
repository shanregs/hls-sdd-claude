# Contract: Module interfaces and events

## Consumed (existing public APIs, unchanged)

| Interface | Used for |
| --- | --- |
| `schoolbilling.api.MouApprovals.stateOn/stateOfAll` | approval date (`approvedOn`), `countsAsApproved`, state name; null `approvedOn` for `APPROVED_BEFORE_APPROVAL_STEP` |
| `schoolbilling.api.MouDecided`, `MouSubmitted` (events) | trigger earning (APPROVED), hold (REJECTED, or `MouSubmitted`), release |
| `recruitment.api.ProspectOwners.ownerOfSchool` | owner user id of the won prospect |
| `organization.api.ManagerQueries` (`managerByUserId`, `managerOfSchool`), `ScopeQueries` | is the owner a Manager; Zone Manager of a School; scope |
| `school.api.ChangeRecorder`, `SchoolDirectory` | audit, School and Zone names |
| `payroll.api.PayrollAdjustmentApplied` (event) | mark `PAID_IN_RUN` when `kind = INCENTIVE` |
| `payroll.api.IncentiveIntake` (new, amendment A) | offer, withdraw, status |
| settings (`SettingKey.INCENTIVE_AMOUNT`) | amount |

## Offered by `incentive.api`

```java
record IncentiveEarned(UUID incentiveId, UUID schoolId, UUID earnerUserId, UUID zoneManagerUserId /* null if none */) {}
record IncentiveDecided(UUID incentiveId, UUID schoolId, UUID earnerUserId, String outcome /* APPROVED | REJECTED */,
                        String reason, UUID decidedBy, boolean approvedByEarner) {}
interface IncentiveSummary {
    Counts counts(Scope scope);                 // pending, approvedAwaitingPayroll, paidThisMonth, noEarner
    List<PendingItem> pendingFor(UUID userId);  // for the Director/Zone Manager approvals inbox (028)
}
```

Events are published after commit (application-module listener). Notification types `INCENTIVE_PENDING` and `INCENTIVE_DECIDED`
(listener `IncentiveEventListener` in `notification`): pending => active Directors and the School's Zone Manager; decided => the earner, minus the decider.
Link `/operations/incentives/{id}`.

## Identity and navigation

`PermissionModule.MANAGER_INCENTIVES(VIEW, APPROVE, EXPORT)`; `NavigationCatalog`: OPERATIONS item "Incentives" (`/operations/incentives`, requires VIEW).
A Manager with no incentive and no Zone sees the item and an empty state (VIEW is seeded for Manager).
