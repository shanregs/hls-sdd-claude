# Contract: Module interfaces and events (package `com.hls.content.api`)

## Events (published by `content`, consumed by `notification`)

- `ContentAssigned(contentId, title, authorUserId, assignedByUserId, dueOn)` - only when assigned to someone other than the creator.
- `ContentSubmitted(contentId, title, versionNumber, authorUserId, submittedByUserId)` - to users holding Approve, minus author and submitter.
- `ContentReviewed(contentId, title, versionNumber, decision, comment, authorUserId, reviewerUserId)` - to the author (and the submitter if different).
- `ContentNoReviewer(contentId, title, versionNumber)` - to every active Director and Admin, once per version.

Notification types: `CONTENT_ASSIGNED`, `CONTENT_SUBMITTED`, `CONTENT_APPROVED`, `CONTENT_RETURNED`, `CONTENT_NO_REVIEWER`.
Links go to `/operations/content/{id}`.

## Used from other modules (existing public APIs)

| From | Interface | Used for |
| --- | --- | --- |
| `files.api.FileStore` (extended, additive) | `store(..., InputStream, size, FileLimits)`, `openStream`, `remove`, `find`, `list` | bytes of every version; owner type `CONTENT_VERSION`, owner id the content |
| `tasks.api.TaskCreation` | `createFollowUp` | content task, type `CONTENT_SUBMISSION`, source `CONTENT`, key the content id |
| `tasks` (service call through its published API; no new method) | complete / verify / send back | tasks.api gains only what 025 already offers; if a programmatic status change is missing, the plan adds `TaskProgress` (see tasks T020) |
| `organization.api.ManagerQueries` | is the user an active Manager | task eligibility |
| `identity` permission checker, `AppUserRepository` | holders of Approve, user names | reviewer resolution, lists |
| `reporting.api.TileSource`, `ApprovalSource` | implemented by `content.internal` | dashboard tile and inbox |

## Files API additions (`files.api`)

```java
record FileLimits(Map<String, Long> maxBytesByExtension) { static FileLimits defaults(); }
FileRef store(UUID actor, String ownerType, UUID ownerId, String originalName, InputStream in, long declaredSize, FileLimits limits);
Optional<FileStream> openStream(UUID fileId);   // FileStream(FileRef file, InputStream bytes)
```

`FileLimits.defaults()` is 10 MB for jpg, png, pdf, docx, xlsx, txt, so existing callers behave as before.

## Identity additions

`Role.CONTENT_CONTRIBUTOR`; `PermissionModule.CONTENT(VIEW, CREATE, EDIT, APPROVE)`; eligibility as in data-model.md; navigation items
"Content" (Admin, Director) and "My Content" (Manager, Contributor); `AuditVisibility`: `CONTENT`, `CONTENT_VERSION`, `CONTENT_REVIEW` require View on `CONTENT`.
