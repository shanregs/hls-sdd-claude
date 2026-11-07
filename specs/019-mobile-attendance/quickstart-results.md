# Quickstart Results: Mobile Attendance

**Status**: automated checks done; device pass (scenarios 1-15 on the emulator against the real backend,
TalkBack, wrong phone clock, SC-001 and SC-009) is still to be run by hand.

## Automated (from `mobile/`)

- `npm test`: all suites pass (39 suites, 400 tests including the attendance suites).
- `npm run lint`, `npm run typecheck`, `npm run check:manifest`: clean.

## T038: is Present first? (read from the seed, backend not run)

Migration V14 seeds P Present (sort 1), L Leave (2), T Training day (3), N Non-working (4, NON_WORKING);
V15 adds S Substitution (5), H Holiday (6, NON_WORKING), A Absent (7). Present is first by `sort_order`.
The app shows the server's list in the order received, minus codes of category NON_WORKING (N and H).
Still to confirm with the backend running: `GET /api/v1/attendance/status-codes?activeOnly=true` returns
the list ordered by `sort_order`.

**Live check 2026-11-07**: `GET /api/v1/attendance/status-codes?activeOnly=true` on port 8080 (token of the Admin and of
the demo Teacher 9800000004, both 200) returned, in this order: P Present, L Leave, T Training day, N Non-working,
S Substitution, H Holiday, A Absent. Present is first, matching `sort_order` 1 to 7 (the response carries no
`sortOrder` field, so the order is the server's list position). No change to the app was needed.

## Real refusal counts (T009)

A Teacher can meet 8 refusals when saving and a Manager 6 (the spec said 6 and 4); both are covered by
tests with the exact server texts.

## T036: review

No file under `mobile/src/attendance/` or the five new screens chooses content by role name, hard-codes a
status code, computes a total, or decides editability from the phone's date: the only code-like literal is
the letter "H" shown for a holiday day state. Role words appear only in user-facing wording.

## Open items

- Weekly offs on the Holiday Calendar use the default unless a School is passed in; the screen is not
  given the user's School yet, so a School-specific weekly off is not shown (model and tests support it).
- Run the device pass and record the timing for SC-001 and the audit check for SC-009 here.
