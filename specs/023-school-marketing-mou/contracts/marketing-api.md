# Contract: Marketing API

All paths under `/api/v1/marketing`. JSON; dates ISO; amounts are strings with two decimals. Errors use the house
format (`{ "reason": "..." }`): 400 invalid input, 403 not permitted, 404 not found (also for a prospect outside the
caller's Zones), 409 conflict (duplicate, stale version, a state that forbids the action).

## Prospects and pipeline (module `MARKETING`)

| Method | Path | Action | Purpose |
| --- | --- | --- | --- |
| GET | `/prospects?zone=&owner=&stage=&query=&page=` | VIEW | list; each row has `stage` (stored) and `effectiveStage` (MoU or Active when derived) |
| POST | `/prospects` | CREATE | add a prospect; a duplicate is 409 with the existing one |
| GET | `/prospects/{id}` | VIEW | the prospect with proposal, activities, history and contract status |
| PUT | `/prospects/{id}` | EDIT | change details (version required) |
| POST | `/prospects/{id}/stage` | EDIT | `{ stage, reason }` (reason required for LOST); ON_HOLD, resume and reopen are `stage` values `ON_HOLD`, `RESUME`, `REOPEN` |
| POST | `/prospects/{id}/owner` | EDIT | `{ ownerUserId }`, Admin and Director only |
| POST | `/prospects/{id}/review` | APPROVE | `{ decision: APPROVE|REJECT, reason }` on a prospect in FINAL_STAGE; approval makes it won |
| POST | `/prospects/{id}/win` | EDIT | Admin and Director: `{ placeId }` creates the School (or `{ linkSchoolId }` links one); returns the hand-off target |
| GET | `/pipeline?zone=&owner=` | VIEW | counts and cards by stage for the board |

## Activities, calendar, attachments (module `MARKETING`)

| Method | Path | Action | Purpose |
| --- | --- | --- | --- |
| GET | `/activities?from=&to=&mine=&prospect=` | VIEW | calendar and list; `status` includes the derived MISSED and a RESCHEDULED flag |
| POST | `/activities` | CREATE | plan an activity (`prospectId` or `schoolId`, `type`, `date`, `attendeeUserIds[]`, `notes`) |
| POST | `/activities/{id}/complete` | EDIT | `{ outcome, notes, followUpOn }`; outcome required |
| POST | `/activities/{id}/reschedule` | EDIT | `{ date }`; the earlier date stays in the history |
| POST | `/activities/{id}/cancel` | EDIT | `{ reason }` |
| POST | `/activities/{id}/attachments` | CREATE | multipart upload; 10 MB, allowed types only |
| GET | `/files/{fileId}` | VIEW | download as an attachment, if the caller may see the visit |
| DELETE | `/activities/{id}/attachments/{fileId}` | EDIT | Admin and Director only, `{ reason }` |

## Proposals (module `MARKETING`)

| Method | Path | Action | Purpose |
| --- | --- | --- | --- |
| GET | `/prospects/{id}/proposals` | VIEW | revisions, newest first; labelled "Proposal (not a contract)" |
| POST | `/prospects/{id}/proposals` | CREATE | a new revision: `{ teacherCount, startMonth, salaryMode, rate | positions[], notes }` |

## Dashboard and settings

| Method | Path | Action | Purpose |
| --- | --- | --- | --- |
| GET | `/dashboard?period=` | VIEW | visits, prospects by stage, win rate, Schools won per Zone and owner, demand, supply (`null` when not available), shortfall |
| GET | `/settings` | VIEW | `{ mouOverdueDays }` |
| PUT | `/settings` | EDIT | Admin and Director: `{ mouOverdueDays (1 to 90), version }`, audited |

## Role matrix (default grants)

| Endpoint group | Admin | Director | Zone Manager | Teacher | System |
| --- | --- | --- | --- | --- | --- |
| prospects, activities, proposals: read and write | allowed | allowed | own Zones only | 403 | 403 |
| owner change, win (create School), remove attachment, settings | allowed | allowed | 403 | 403 | 403 |
| Final Stage review (approve or reject) | 403 | allowed | own Zones only | 403 | 403 |
| dashboard | allowed | allowed | own Zones only | 403 | 403 |

A Zone Manager outside the prospect's Zone gets 404. Permissions are runtime-editable; the Zone check never widens.

## Public interfaces and events

```java
// recruitment.api
interface MarketingActivities { List<PlannedActivity> plannedBetween(LocalDate from, LocalDate to, UUID ownerUserId); }
interface ProspectOwners      { Optional<UUID> ownerOfSchool(UUID schoolId); }          // for the incentive spec (030)
interface SupplySource        { OptionalInt readyToDeployCount(); }                      // spec 016 provides it; default: empty
record FollowUpScheduled(UUID activityId, UUID prospectId, UUID ownerUserId, LocalDate dueOn, String title) {}
record WonProspectOverdue(UUID prospectId, String name, UUID ownerUserId, UUID zoneId, int days) {}
record ProspectWon(UUID prospectId, UUID schoolId) {}
// school.api (new)        SchoolRegistry.create(UUID actor, UUID placeId, SchoolProfile profile) -> UUID
// organization.api        ManagerQueries.managersOfZone(UUID zoneId) -> List<ManagerRef>
// schoolbilling.api (A6)  SchoolContracts.occupancyOf(UUID schoolId, LocalDate on) -> Occupancy(positions, filled, vacant)
// files.api (new)         FileStore.store/open/remove
```
