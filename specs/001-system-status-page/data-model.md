# Phase 1 Data Model: System Status Page

This feature introduces no persisted entities — spec.md's Assumptions explicitly state "No historical/audit record of past status checks is kept — each page load reflects only the current, real-time check." There is no database table, migration, or repository for this feature.

The only "shape" worth documenting is the transient, in-memory result of a single status check, which is also the JSON contract returned by the API (see `contracts/status-api.yaml`).

## StatusCheckResult (transient value object, not persisted; implemented as `StatusResponse` — see plan.md/tasks.md)

| Field | Type | Description | Source requirement |
|---|---|---|---|
| `status` | enum: `OK`, `DEGRADED` | Overall result for this check | FR-002 |
| `version` | string | Current application version | FR-003 |
| `serverTime` | ISO-8601 datetime, IST offset | Moment the page was loaded, shown in IST | FR-004 |
| `dataStoreReachable` | boolean | Whether the core data store responded within the bounded wait | FR-005 |
| `checkDurationMs` | integer | How long the reachability check took, in milliseconds | Supports SC-004 verification |

**Derivation rule**: `status = OK` if and only if `dataStoreReachable = true`; otherwise `status = DEGRADED` (FR-002, FR-005, FR-006). This includes the case where the check did not complete within the 3-second bound (treated as unreachable, per FR-006 and research.md §3).

**Lifecycle**: Constructed fresh on every request to `GET /api/status`; never stored, cached, or referenced by a later request (SC-002's "no stale OK" requirement rules out caching across requests).

**Relationships**: None — this feature has no relationship to any other entity in the system (Teacher, School, Contract, etc. are all untouched, consistent with this being a pre-roster warm-up feature).
