# Contract: Module interfaces and events

All new interfaces return records, never entities, and apply no caller scope unless said; `transfer` applies its own.

## `schoolbilling.api.PlacementMoves` (new, implemented in `schoolbilling.internal`)

```java
record MoveSpec(Kind kind /* TRANSFER | REPLACEMENT */, UUID outgoingTeacherId, UUID schoolId, UUID incomingTeacherId /* replacement */,
                UUID toSchoolId /* transfer */, UUID positionId /* named, else null */,
                UUID outgoingDestinationSchoolId /* replacement, else null */, LocalDate effectiveOn) {}
record MoveCheck(boolean ok, List<String> problems) {}
record AppliedMove(List<UUID> endedAssignmentIds, List<UUID> createdAssignmentIds) {}

MoveCheck check(MoveSpec spec);                              // dry run, same rules as apply, writes nothing
AppliedMove apply(UUID actorUserId, MoveSpec spec);          // caller's transaction, all or nothing; ConflictException on any failure
void reverse(UUID actorUserId, AppliedMove move);            // cancels created rows still in the future, restores the open end of ended rows
List<VacantPosition> vacantPositions(UUID schoolId, LocalDate on); // record VacantPosition(UUID positionId, int number, BigDecimal salary)
```

Rules: the outgoing row ends the day before `effectiveOn`; the incoming row starts on it; a replacement's incoming row reuses
the outgoing row's `position_id`; no day has two Schools or, apart from an outgoing Teacher with no destination, none.
`reverse` refuses (ConflictException) any created row that has started. The existing `TeacherPlacementSource` is unchanged.

## `teacher.api.TeacherMovementSource` (new, owned by `teacher`, implemented by `transfer`)

```java
Set<UUID> transferredTeacherIds();   // Approved or Applied transfer, or two ACTIVE placement rows at different Schools
Set<UUID> replacedTeacherIds();      // outgoing Teacher of an Approved or Applied replacement
```

The Teacher list intersects these with the caller's scope.

## `attendance.api.PlacementAttendance` (new)

```java
boolean hasNonLeaveMarkOnOrAfter(UUID teacherId, LocalDate date);
boolean anyMonthLocked(UUID teacherId, LocalDate fromDate);       // effective month and later months that hold marks
int retagLeaveMarks(UUID actorUserId, UUID teacherId, LocalDate from, UUID newSchoolId); // caller's transaction
```

`retagLeaveMarks` changes only leave-category marks dated on or after `from`, writes an `attendance_mark_history` row per mark
(CORRECTED), throws if any is in a locked month, and never touches an earlier date.

## `substitution.api.PlacementSubstitution` (new)

```java
int relocate(UUID actorUserId, UUID teacherId, LocalDate from, UUID newSchoolId /* null: cancel */, String reason);
```

Requirements with `duty_date >= from` that are not CANCELLED move to the new School (OPEN again if a substitute was assigned,
who is told through `SubstitutionEnded`) and `SubstitutionNeeded` is published for the new School; with a null School they are
cancelled with the reason. Returns the number changed. Writes history and audit as the module's other changes do.

## Events published by `transfer.api` (listened to by `notification`)

```java
record TransferRequested(UUID requestId, String kind, UUID teacherId, UUID fromSchoolId, UUID toSchoolId, UUID requestedBy, LocalDate effectiveOn) {}
record TransferDecided(UUID requestId, String kind, String outcome /* APPROVED | REJECTED | CANCELLED */, UUID teacherId,
                       UUID replacementTeacherId, UUID fromSchoolId, UUID toSchoolId, UUID requestedBy, UUID decidedBy, LocalDate effectiveOn) {}
record TransferApplied(UUID requestId, String kind, UUID teacherId, UUID replacementTeacherId, UUID fromSchoolId, UUID toSchoolId, LocalDate effectiveOn) {}
```

Notification types `TRANSFER_REQUESTED`, `TRANSFER_DECIDED`, `TRANSFER_APPLIED`. Published in the publisher's transaction (decided)
or by the daily job (applied).

## Identity and navigation

`PermissionModule.TEACHER_TRANSFERS(VIEW, CREATE, EDIT, APPROVE)`; `PermissionEligibility`: Approve not eligible for Admin, nothing
eligible for Teacher or System; `NavigationCatalog`: OPERATIONS item "Transfers & Replacements" requiring View.
