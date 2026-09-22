# Research: Place Bulk Import

No `[NEEDS CLARIFICATION]` markers remain — spec.md's Assumptions section already resolved the open design questions.

## 1. Submission format: a JSON array in the request body, not a CSV file upload

**Decision**: `POST /api/v1/school/places/bulk-import` takes a JSON body — an array of `{zoneId, name, pincode}` rows — the same shape as the existing single `PlaceRequest`, just plural.

**Rationale**: Every endpoint in this codebase so far is JSON-in/JSON-out; a multipart file upload plus CSV parsing would be the first departure from that pattern and would add a new dependency (a CSV parsing library — none exists in `backend/pom.xml` today) for a need spec.md's Assumptions section explicitly defers. Converting a spreadsheet to a JSON array is a client-side (or even a one-off script) concern, not something this backend feature needs to solve on day one.

**Alternatives considered**:
- *Multipart CSV upload, parsed server-side*: rejected for now — real added complexity (a new dependency, encoding/quoting edge cases, a different request content-type than every other endpoint) for a need not yet confirmed as a real friction point. Spec.md leaves the door open to add this later, the same way bulk import itself was deferred out of specs/008 until one-row-at-a-time became a real friction point.

## 2. Partial success, not all-or-nothing — no batch-wide transaction rollback on a bad row

**Decision**: Each row is validated in memory first (name present, PIN code present, `zoneId` resolves to an existing Zone via `ZoneRepository.findById`); only rows that pass validation are ever persisted. The whole batch's valid-row inserts happen inside one `@Transactional` method (so a real database-level failure mid-batch doesn't leave a half-committed batch of otherwise-good rows), but an individual row failing *validation* never throws — it's recorded as a failed result and simply never reaches `save(...)`.

**Rationale**: FR-002/FR-003 require every valid row to succeed even when other rows fail, with a clear per-row report — the opposite of a single exception aborting the whole `@Transactional` method (Spring's default behavior would roll back the entire batch on any propagated exception). Doing validation as plain in-memory checks that produce a result object, never an exception, sidesteps that entirely: nothing is ever thrown for a bad row, so there's nothing for the transaction to roll back over.

**Alternatives considered**:
- *One transaction per row (each row's own `@Transactional`, catching exceptions individually)*: rejected — unnecessary complexity (self-invocation/proxying concerns with per-row `@Transactional` inside a loop in the same class) for no real benefit, since plain validation-then-conditionally-save already achieves the same outcome without ever needing a transaction to roll back.

## 3. Zone-existence validation — new for bulk import, deliberately not retrofitted onto single-add

**Decision**: `PlaceService.bulkImportPlaces(...)` checks each row's `zoneId` against `ZoneRepository.findById(...)` before creating a `Place`; a row naming an unknown Zone is reported as a failed row (FR-004), not created. The existing single-place `addPlace(...)` (specs/008) is left exactly as it is today — no Zone-existence check.

**Rationale**: spec.md's Assumptions section names this explicitly: a typo'd Zone id is easy to notice and fix immediately when adding one place by hand, but easy to miss inside a batch of hundreds — bulk import is where this check earns its cost. Retrofitting the same check onto `addPlace` would be an unrelated, unrequested change to already-shipped, merged code (specs/008 is on `main`), which this feature has no reason to touch.

**Alternatives considered**:
- *Add the same Zone-existence check to `addPlace` too, for consistency*: rejected — out of scope for this feature, and specs/008 is already merged; changing its established behavior isn't something this bulk-import feature was asked to do.

## 4. Max batch size: 5,000 rows, enforced before anything is created

**Decision**: `PlaceService.bulkImportPlaces(...)` rejects (via an exception mapped to 400, mirroring `MethodArgumentNotValidException`'s handling in `TeacherController`) a batch whose size exceeds 5,000, and separately rejects an empty batch — both checked before any row is touched (FR-005/FR-006).

**Rationale**: A single, simple, documented default (spec.md Assumptions) is enough to guard against a clearly-wrong submission (e.g., an entire national file pasted in by mistake) without needing real usage data to justify a more precise number yet.

## 5. `bulkImportPlaces` joins the existing `PlaceCommands` interface — no new interface pair

**Decision**: `PlaceCommands` gains one new method, `List<BulkImportRowResult> bulkImportPlaces(List<BulkPlaceRow> rows, UUID actingUserId)`. No new `PlaceBulkImportCommands` interface, no new controller.

**Rationale**: specs/008's own research.md established that a genuinely different *concept* within `school` (Zone vs. Place) gets its own interface pair, but bulk import isn't a new concept — it's a batch-shaped way to call the same "add a place" capability `PlaceCommands.addPlace` already exposes. Splitting it into a separate interface would suggest a conceptual difference that doesn't exist.

## 6. No new Spring Modulith / ArchUnit concerns

**Decision**: Everything lives inside the already-covered `com.hls.school` package (`internal` and `api` respectively) — no new package, no new `ArchitectureTest` rule, no `SchoolModuleTest` bootstrap-mode change.

**Rationale**: Same reasoning specs/008 applied extending `school` with `Place` — this is a pure extension of an already-enforced boundary.
