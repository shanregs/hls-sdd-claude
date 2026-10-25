# Clarification questions: 029 Content Management

Not answered: these need the owner. Each has a recommended default; none has been applied to spec.md. Reply with the question number and A/B/C or "recommended".

## Q1. Who may review (approve or return) content?

**Question:** Which roles hold the Approve action on Content by default?
Why it matters: it sets the permission seed, the notification recipients and the self-review rule.

**Recommended:** Option A - mirrors 025 (verifier needs Approve, never the assignee) and keeps Managers from approving peers.

| Option | Description |
| --- | --- |
| A | Director only (Admin can be granted, Manager not eligible) |
| B | Admin and Director, as in 025 |
| C | Director, plus the Zone Manager of the content owner's area |

## Q2. File types and size

**Question:** Should content files follow the shared file store's present type and size policy (10 MB, photo, PDF, document), or a wider one for content such as video or slide decks?
Why it matters: a stricter or wider limit changes storage cost and the upload screen.

**Recommended:** Option A - reuse the store as is; raise the limit later in one place.

| Option | Description |
| --- | --- |
| A | Reuse the store's policy unchanged |
| B | Reuse it but add presentation formats (pptx) |
| C | Larger limit (for example 50 MB) and video for content only |

## Q3. Files only, or links too?

**Question:** May a Manager submit a link (for example a shared drive URL) instead of uploading a file?
Why it matters: the deck requires the actual output to be captured, and a link can change or lose access after approval.

**Recommended:** Option A - file only in version 1.

| Option | Description |
| --- | --- |
| A | File only |
| B | File or link, link kept as text |
| C | Link allowed only alongside a file |

## Q4. Approved content that is revised

**Question:** When a Manager submits a new version of already approved content, should it go back to review?
Why it matters: it decides whether "Approved" ever means a version that nobody has checked.

**Recommended:** Option A - new version returns to Submitted and needs approval; the earlier approval stays in history and the earlier version stays the last approved one.

| Option | Description |
| --- | --- |
| A | Back to review; earlier approved version remains the last approved |
| B | Not allowed; approved content is locked |
| C | Stays approved, new version is just added |

## Q5. Tasks and content pieces

**Question:** Is a content task always one content piece, and may a Manager create content with no assigned task?
Why it matters: it decides whether the task link is mandatory, and how the dashboard and overdue counts are computed.

**Recommended:** Option A - one task per assigned piece; self-started content has no task and is not counted as overdue.

| Option | Description |
| --- | --- |
| A | One task per assigned piece; self-started content has no task |
| B | One task may cover several pieces |
| C | Every content piece must have a task, self-started ones get a task for themselves |
