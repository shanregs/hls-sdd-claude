# Research: Substitution Workflow (spec 017a)

## 1. Existing building blocks (found 2026-10-11)

- Attendance: `MarkService.setMark/setLeaveMark/clearLeaveMark/clearMark` are the only writers of `attendance_mark`;
  status codes have a `category` (WORKED, LEAVE, TRAINING, NON_WORKING); the seeded Absent code `A` is category LEAVE;
  `attendance_mark.leave_request_id` is set for marks made by approved leave. `AttendanceMarkChanged` (spec 010) is the
  existing pattern for a published event inside the transaction; the notification module listens with `@EventListener`.
- Leave: `LeaveAttendance.apply(requestId, approver, teacher, days)` writes the Leave marks; `leave.api.LeaveTypes` exposes
  the Loss-of-Pay type (A3, code `LOP`). Leave depends on attendance, so attendance cannot read leave tables: the
  loss-of-pay flag has to be given to attendance by the caller. `LeaveAttendance.apply` gets an overload with the flag.
- Scope: `TeacherScopeQueries` (Teachers) and `SchoolScopeProvider` (Schools); `CallerContext.isOrgWide(roles)`.
- Recipients: `RecipientResolver.leaveSupervisors(schoolId)` is the "Manager, else Admin and Director" rule reused here.
- Notification type is a database check constraint (V23 widened it); every new type needs a migration.
- 013a (`UnpaidDayClassifier`) reads the status category; it is merged and left untouched.

## 2. Decisions

- **D-1 Leave counts as an absence for cover.** The roadmap says "absence", but an approved leave day leaves the class
  uncovered in the same way, and leave is the commonest planned absence. Both raise a requirement; `kind` tells them apart.
- **D-2 Availability is placement-based.** The business view is that a placed Teacher is teaching at their School on a
  working day. Available = not placed that day, or placed in a School closed that day (`WorkingCalendar.workingDays` for the
  placement's School), no leave-category mark, not over-allocated by substitutions. Candidates are all active Teachers,
  because an unplaced (bench) Teacher belongs to no Manager's scope.
- **D-3 Credit is a view of the requirement.** `SubstituteCredits` reads Assigned requirements; history rows keep the rest.
- **D-4 Events carry the loss-of-pay flag**, so a future consumer needs no second read.
- **D-5 Principal confirmation sits on the mark** (columns), audited through `ChangeRecorder`; changing the status code
  clears it, because the confirmation was for the earlier status.
- **D-6 Notification constraint**: V48 replaces `notification_type_check` with a pattern check (`type ~ '^[A-Z_]+$'`)
  instead of another closed list, so parallel specs that add types cannot overwrite each other's list.
- **D-7 Migration number V48.** Max on `main` is V26; parallel work for 011 and 025 is expected near V27 to V46.
- **D-8 No tasks.** 025 is not used; escalation of an unresolved requirement waits for it.

## 3. Analysis (speckit-analyze, 2026-10-11)

Cross-checked spec, plan and tasks: every FR maps to at least one task (see the table in tasks.md); no task lacks a
requirement; terms agree (requirement, credit, outcome); the Role & Permission Impact section matches the keys in the plan
and in the tasks; no `[NEEDS CLARIFICATION]` is left. Gaps found and closed in the documents: (a) FR-011 needed a
notify-on-end event, added as `SubstitutionEnded`; (b) the Teacher view must not name the absent Teacher, made explicit in
US4; (c) the notification constraint collision risk, D-6; (d) `LeaveAttendance.apply` must carry the loss-of-pay flag,
section 1.
