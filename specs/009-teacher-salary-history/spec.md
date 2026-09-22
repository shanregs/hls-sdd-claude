# Feature Specification: Teacher Salary History

**Feature Branch**: `009-teacher-salary-history`

**Created**: 2026-09-22

**Status**: Draft

**Input**: User description: "if for teacher when they are offered the initial salary, we can have in teacher, but when they get increments in salary, where will we keep the latest salary? By default take latest value, but we can also compute on any past date — is it ok?" Extends `specs/005-teacher` (Teacher Master Data, already implemented) — that module's `hlsOfferedSalary` field is currently a plain, freely-overwritable value on the Teacher Profile with only a generic (opaque, whole-profile) audit trail behind it. This feature replaces that with a proper, queryable salary history: the current salary is always the default answer, but the salary that was in effect on any specific past date is also directly answerable — needed so a future Payroll module can recompute a past month's pay using the salary that actually applied then, not today's value.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Admin Sets a Teacher's Initial Salary at Onboarding (Priority: P1)

When Admin creates a new teacher's profile (specs/005-teacher User Story 1), the HLS-offered salary entered becomes that teacher's first salary-history entry, effective from the day the profile is created.

**Why this priority**: Every later story depends on a first entry existing — there's no "history" to query or increment from otherwise.

**Independent Test**: Create a teacher profile with an initial salary, then confirm that salary is immediately retrievable as both the "current" salary and the salary "as of" the creation date.

**Acceptance Scenarios**:

1. **Given** Admin creates a new teacher profile with an HLS-offered salary, **When** the profile is created, **Then** that amount is recorded as the teacher's current salary, effective from the creation date.

---

### User Story 2 - Admin Records a Salary Increment (Priority: P1)

Admin records a new salary amount for an existing teacher, effective from a given date (typically today, but not required to be) — without losing the prior amount or when it applied.

**Why this priority**: This is the actual capability the feature exists for — increments happen routinely, and losing the trail of what was paid when defeats the purpose.

**Independent Test**: Record a second salary amount for a teacher with a later effective date than the first, then confirm the teacher's current salary is now the new amount, and the original amount is still retrievable as of its own effective date.

**Acceptance Scenarios**:

1. **Given** a teacher with an existing salary, **When** Admin records a new salary amount effective from a later date, **Then** the teacher's current salary becomes the new amount.
2. **Given** a teacher with two recorded salary amounts (an original and an increment), **When** the salary as of a date between the two effective dates is looked up, **Then** the original (earlier) amount is returned, not the increment.
3. **Given** a teacher with two recorded salary amounts, **When** the salary as of a date on or after the increment's effective date is looked up, **Then** the incremented amount is returned.

---

### User Story 3 - Anyone Entitled to View the Profile Can See the Current Salary by Default (Priority: P1)

Viewing a teacher's profile (Director, the teacher's assigned Manager, Admin, or the teacher themself — specs/005-teacher User Stories 3-4) shows the current salary without any extra step.

**Why this priority**: The common case — "what does this teacher currently earn" — must stay exactly as simple as it already is today; history is an additional capability, not a tax on the default path.

**Independent Test**: View a teacher's profile after one or more salary changes and confirm the salary shown is the latest one, with no separate lookup required.

**Acceptance Scenarios**:

1. **Given** a teacher with multiple recorded salary amounts over time, **When** their profile is viewed by anyone already entitled to view it, **Then** the salary shown is the current (latest effective) one.

---

### User Story 4 - Look Up the Salary That Was in Effect on a Specific Past Date (Priority: P2)

Someone who can already view a teacher's profile can additionally ask "what was this teacher's salary on [a specific past date]" and get a direct answer.

**Why this priority**: This is the capability a future Payroll module (or an Admin resolving a pay dispute) actually needs; it's P2 rather than P1 because nothing consumes it yet (research.md), unlike User Stories 1-3, which the existing Teacher Profile view already depends on today.

**Independent Test**: Record two salary amounts for a teacher with different effective dates, then query the salary as of a date before, between, and after those effective dates, and confirm each returns the correct amount (or a clear "no salary recorded yet" for a date before the first entry).

**Acceptance Scenarios**:

1. **Given** a teacher's recorded salary history, **When** the salary as of a specific past date is looked up, **Then** the amount effective on that date is returned.
2. **Given** a date earlier than a teacher's first recorded salary, **When** the salary as of that date is looked up, **Then** the system clearly reports no salary was recorded yet as of that date, not an error and not a misleading value.

---

### Edge Cases

- What happens when Admin records a new salary with an effective date earlier than an existing entry's effective date (a backdated correction, not a future increment)? Allowed — the history is ordered by effective date, not by when the entry was recorded, so a backdated correction slots into its correct place and "as of" queries around it answer correctly either way.
- What happens when two salary entries are recorded with the exact same effective date? The most recently recorded one of the two is treated as authoritative for that date (last write wins for same-day entries) — this is not expected to be a common occurrence and no special conflict handling is offered for it.
- What happens when the salary "as of" a future date (after the latest recorded entry) is looked up? The current (latest) salary is returned, on the assumption that it remains in effect until a further change is recorded.
- What happens to a teacher's salary history when the teacher's status changes (e.g., to "exited")? Unaffected — salary history is independent of status and is never altered or removed by a status change (specs/005-teacher FR-004 continues to apply: nothing about this teacher's record is ever deleted).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST record a teacher's HLS-offered salary as a history of dated entries (amount + effective-from date), not a single freely-overwritable value.
- **FR-002**: The system MUST record the salary entered at profile creation (specs/005-teacher FR-001) as that teacher's first history entry, effective from the creation date.
- **FR-003**: The system MUST allow Admin to record a new salary amount for an existing teacher with a specified effective-from date (defaulting to today if not specified).
- **FR-004**: The system MUST NOT delete or overwrite a previously recorded salary history entry — every recorded amount remains retrievable (specs/005-teacher FR-004's never-deleted principle extended to salary history).
- **FR-005**: The system MUST report a teacher's current salary as the amount from the entry with the latest effective-from date that is not in the future, by default, with no extra step beyond viewing the profile (specs/005-teacher FR-006/007/008).
- **FR-006**: The system MUST allow looking up the salary amount in effect on any specified past date, returning the entry whose effective-from date is the latest one on or before the requested date.
- **FR-007**: The system MUST clearly distinguish "no salary was recorded as of this date" (a date before the first entry) from an error or from a real, zero-like value.
- **FR-008**: The system MUST apply the same viewing rules already established for a teacher's profile (specs/005-teacher FR-006/007/008/009) to salary history — no separate permission model.
- **FR-009**: The system MUST restrict recording a salary change to the Admin role (specs/005-teacher FR-010's existing write restriction extended to salary history).

### Key Entities *(include if feature involves data)*

- **Teacher Salary History Entry**: One record per recorded salary amount for a teacher — the amount, the date it becomes effective from, when it was recorded, and who recorded it. Never deleted or edited once recorded (FR-004). A teacher has at least one entry from the moment their profile is created (FR-002) and may have many, ordered by effective-from date.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of newly created teacher profiles have their initial salary immediately retrievable as both "current" and "as of the creation date."
- **SC-002**: 100% of recorded salary increments are retrievable both as the new current salary and, for the prior amount, as of any date before the increment's effective date, verified through testing.
- **SC-003**: Viewing a teacher's profile shows the current salary with no additional request beyond what specs/005-teacher's existing profile view already requires.
- **SC-004**: A salary-as-of-date query for a date before any recorded entry clearly reports no salary recorded, in 100% of tested cases, never mistaken for an error or a zero salary.

## Assumptions

- This feature replaces, rather than adds alongside, specs/005-teacher's existing free-form salary field on the Teacher Profile — that field predates this feature's design and is corrected here as part of extending the same not-yet-merged module, not preserved as a second, competing way to record salary (see plan.md/research.md for the mechanics).
- "Effective from" dates are calendar dates (not date-times) — salary changes are a daily-granularity concept in this system, consistent with how attendance and payroll are expected to operate.
- This feature only adds recording and querying salary history — it does not implement Payroll itself; a future Payroll module is the anticipated (but not yet built) consumer of the "as of a date" query (User Story 4).
- Recording a salary change is a distinct action from the general profile-update action (specs/005-teacher FR-002) — contact-detail updates and salary changes are no longer the same call, since a salary change carries an effective date that a name/phone/email change does not need.
