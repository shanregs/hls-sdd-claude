# Quickstart Results: Mobile Leave

**Status**: automated checks done; the device pass (scenarios 1-12 on the emulator against the real backend,
TalkBack, wrong phone clock, SC-001, SC-002 and SC-010) and the real-server check (T036) are still to be run by hand.

## Automated (from `mobile/`)

- `npm test`: all suites pass (61 suites, 655 tests, of which about 250 are new for leave and the shared pieces).
- `npm run lint`, `npm run typecheck`, `npm run check:manifest`: clean.

## Real refusal counts (T006)

Counted from the code of spec 009 (`LeaveRequestService`, `LeaveDecisionService`, `LeaveCounter`,
`LeaveAttendanceImpl`): 13 when applying, 3 when cancelling, 15 when deciding. Each has a test with the exact text.

## T034: review

No file under `mobile/src/leave/` or the four new screens chooses content by role name, hard-codes a leave type,
works out a working-day count or total, or decides whether an action is offered from a status, a date or the phone
clock: actions come only from `allowedActions`. The only date use is listing the days of the chosen range (for the
preview labels) and the server clock helper that picks the month the date chooser opens on. Status words
("Approved by", "Rejection reason") only choose display text. The "Cancelled by the <kind>" text is built from the
server's `cancelledBy` value, so no role name is compared.

## T036: live check 2026-11-07 (backend on port 8080, demo Teacher 9800000004, Admin for decisions)

- `GET /api/v1/me/leave/types` returned Casual, Sick, Personal, Other, Loss of Pay.
- `POST /api/v1/me/leave/preview` for one working day returned that day with value 1.0; an empty reason answered
  "Give a reason." (also 400 on submit); a Sunday returned no days and "None of these dates is a working day for you."
- Refusal texts triggered through the API and compared with `mobile/__tests__/support/leaveTexts.ts`: all matched
  exactly. Apply: choose a type, choose dates, last before first, give a reason, reason over 500, over 90 days,
  started over 30 days ago, not placed at a School, no working day, both half days on one day, overlap with a
  pending request (preview and submit, 409). Cancel: already started, already cancelled, a rejected request cannot be
  cancelled. Decide (Admin): cancelled by the Teacher, already approved, already rejected, reason required (revoke and
  reject), note over 500, stale version ("This record was changed by someone else. Reload and try again.").
- No mismatch found, so `leaveMessages.ts` is unchanged.
- Not triggered (they need a locked month, supervisor-set attendance or a concurrent save, which would change shared
  data): "Attendance is locked for a month in this range (month locked: ...)", "days set by a supervisor: ...",
  "Leave cannot be removed: month locked: ...", the overlap-at-save race, and the two deciding texts "Only a pending
  request can be decided." / "Only an approved request can be revoked." (the server answers with the status text
  instead for these demo states). T036 stays unticked until those are covered.

## Findings while implementing

- The server requires a reason when applying ("Give a reason."); the spec was corrected on 2026-10-05 (reason required).
- The preview lists counted days only; weekly offs and holidays are labelled from the Holiday Calendar and every
  other uncounted date reads "Not counted".
- Leave Management has no "All": with no status the server returns Pending only.
- A bug found by the tests: tapping Confirm twice quickly could send a decision twice (the busy guard was state);
  fixed with a ref in `ReasonDialog`.

## Open items

- T036: with the backend running, check `GET /api/v1/me/leave/types`, a real preview (counted days only, empty reason
  answers "Give a reason.") and the refusal texts; fix `leaveMessages.ts` if any text differs.
- T038: run the quickstart on the emulator; record SC-001 (apply in under 90 seconds), SC-002 (decide in under 30
  seconds) and SC-010 (audit shows source "Android app"), and check My Attendance shows and drops the leave days.
