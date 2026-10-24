# Contract: Module interfaces and events

All interfaces return records, never entities, and apply no caller scope (they are for other modules, not for users).

## `schoolbilling.api.MouApprovals` (new, implemented in `schoolbilling.internal`)

For specs 022 (billing), 030 (incentives) and 023 (marketing). This spec only reports the state; whether a consumer waits for
"approved" is that spec's rule.

```java
record MouApprovalState(
    UUID schoolId,
    UUID contractId,            // null when state is NO_MOU
    String state,               // see below
    Instant approvedOn,         // set for APPROVED and APPROVED_WITHOUT_DOCUMENT; null otherwise (also null for the pre-existing ones)
    boolean documentAttached,   // a current, non-removed signed document exists
    boolean countsAsApproved) {} // true for APPROVED, APPROVED_WITHOUT_DOCUMENT, APPROVED_BEFORE_APPROVAL_STEP

MouApprovalState stateOn(UUID schoolId, LocalDate date);
Map<UUID, MouApprovalState> stateOfAll(Collection<UUID> schoolIds, LocalDate date);   // every given id has an entry, fixed number of queries
```

`state` is one of `NO_MOU` (no live contract covers the date), `NOT_APPLICABLE` (the "MoU pending" placeholder),
`DRAFT`, `PENDING`, `REJECTED`, `APPROVED`, `APPROVED_WITHOUT_DOCUMENT`, `APPROVED_BEFORE_APPROVAL_STEP`.
A cancelled contract is never "in effect", so `CANCELLED` is not returned here. The MoU chosen is the one
`SchoolContracts.contractOf(schoolId, date)` returns. Any state other than the three that count never answers "approved".
`SchoolContracts` and `ContractView` are **unchanged**.

Consumers (022, 030, 023) inject this interface; they never read `mou_approval`, `mou_document` or `mou_approval_event`.

## Events published by `schoolbilling.api` (listened to by `notification`)

```java
record MouSubmitted(UUID contractId, UUID schoolId, UUID submittedBy, boolean resubmitted /* rejection resubmit or reset by a replaced document */) {}
record MouDecided(UUID contractId, UUID schoolId, String outcome /* APPROVED | REJECTED */, String reason /* rejection, else null */,
                  UUID submittedBy, UUID decidedBy, boolean termsAmended) {}
```

Published in the publisher's transaction. Notification types `MOU_SUBMITTED` and `MOU_DECIDED` (added to `NotificationType`; the
column is `VARCHAR(60)` since V50, the names are shorter). Recipients (`MouEventListener`):

- `MouSubmitted`: active Directors and the School's active Zone Manager (`ManagerQueries.managerOfSchool`), minus the submitter.
  Not Admin.
- `MouDecided`: the submitter and every active Admin, minus the decider. The message names the School and the outcome, includes
  the rejection reason, and says if terms were amended. Link `/operations/school-contracts/schools/{schoolId}`.

## Used from other modules (existing public APIs, unchanged)

| From `schoolbilling` | Interface | Used for |
| --- | --- | --- |
| `files.api.FileStore` | `store`, `open`, `remove`, `find` | bytes of every document version; owner type `MOU_DOCUMENT`, owner id the contract |
| `organization.api.ScopeQueries`, `ManagerQueries` | scope of the caller; Zone Manager of a School | scope tests, notification recipients, Zone Manager name in lists |
| `school.api.SchoolDirectory`, `ChangeRecorder`, `StaleVersion`, `CallerContext` | school names, audit, version check | as 012 |
| `identity` (`AppUserRepository`, `RoleAssignmentRepository`, permission guard) | user names, Directors and Admins | history names, recipients |

## Identity and navigation

`PermissionModule.SCHOOL_CONTRACTS(VIEW, CREATE, EDIT, APPROVE)`. `PermissionEligibility`: `APPROVE` not eligible for Admin
(same pattern as `TEACHER_TRANSFERS`), nothing eligible for Teacher or System (unchanged). `PermissionMatrixService.seedSchoolContracts`:
Director `APPROVE`, Manager `APPROVE`. `NavigationCatalog`: OPERATIONS item "MoU Approvals" (`/operations/school-contracts/approvals`)
requiring `APPROVE`, for Director and Manager. `AuditVisibility`: `CONTRACT`, `MOU_DOCUMENT`, `MOU_APPROVAL` require View on
`SCHOOL_CONTRACTS`. Data scope in `AccessModelService` is unchanged (Admin and Director org-wide, Manager assigned).
