# Research: School Master Data — Places

No `[NEEDS CLARIFICATION]` markers remain. This is a small, additive extension of `specs/007-school-zone`'s already-established patterns.

## 1. No uniqueness on name or PIN code — lookups are multi-result from the start

**Decision**: `findByPincode`/`findByName` return `List<PlaceView>`, never a single value or `Optional`.

**Rationale**: Spec.md's Edge Cases are explicit: real Indian postal/administrative geography doesn't guarantee either is unique (a PIN code can span multiple villages; village names repeat across India). Designing the return type as a list from the start avoids an awkward later migration from "one result" to "many" once the first real collision is hit.

**Alternatives considered**:
- *Enforce uniqueness and reject duplicates*: rejected — spec.md's Edge Cases explicitly call for allowing this, since it reflects real geography, not a data-entry mistake to prevent.

## 2. No reassignment/edit operation — matches spec.md's stated scope exactly

**Decision**: `PlaceCommands` has only `addPlace`; there is no `movePlaceToZone` or `updatePlace`.

**Rationale**: Spec.md's Assumptions explicitly scope this out, the same way `specs/007-school-zone` itself was deliberately split out of the original Zone-scoping work rather than guessed at. Building an unrequested reassignment operation now would be speculative.

## 3. `Place` stays inside the existing `school` module — no new package, no new module test

**Decision**: `Place`/`PlaceRepository`/`PlaceService` are added to `com.hls.school.internal`; `PlaceQueries`/`PlaceCommands`/`PlaceView` to `com.hls.school.api`. No changes to `ArchitectureTest` or `SchoolModuleTest` beyond what already exists.

**Rationale**: `school`'s existing ArchUnit rule (`schoolInternalsAreOnlyAccessedFromWithinSchool`) already covers any new class added under `com.hls.school.internal` — there's nothing Place-specific to add. `SchoolModuleTest`'s `STANDALONE` bootstrap mode is unaffected: adding one more internal type with no new external dependency doesn't change the module's dependency footprint (specs/007 research.md §3's reasoning still holds).

## 4. Endpoints join the existing `ZoneController`, not a new controller

**Decision**: Place endpoints (`POST /places`, `GET /places?pincode=`/`?name=`, `GET /zones/{zoneId}/places`) are added to `ZoneController`, with `PlaceService` injected alongside `ZoneService`.

**Rationale**: Matches `organization.internal.OrganizationController`'s own precedent — one controller per module, not one per entity, once a module has more than one closely related resource type. `ZoneController` is still small enough that splitting it now would be premature.
