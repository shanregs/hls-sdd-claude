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
