# Quickstart: Mobile Leave

How to prove the feature works end to end. Contract: [mobile-leave-screens.md](contracts/mobile-leave-screens.md).
Data: [data-model.md](data-model.md). Setup of the emulator, backend and app: `docs/running-mobile.md`.

## Prerequisites

- Backend, database and web app running with the demo data (`docs/running-locally.md`); the leave demo data
  (a Pending, an Approved and a Rejected request for demo Teachers, spec 009 FR-014) is seeded.
- The app from specs 018 and 019 installed on an Android 10+ emulator, signed out.
- Demo accounts: Tara Teacher (`9800000004`, one-time code in the backend log), Manoj Manager
  (`manoj.manager` / `Password123!`), Divya Director (`9800000002` / `Password123!`).
- A second Manager with a Teacher of their own (create it on the web) to test scope.

## Run the automated tests

```powershell
cd mobile
npm test              # includes the leave suites
npm run lint
npm run typecheck
```

## Validation scenarios

1. **Menus (US5)**: sign in as Tara: LEAVE → Apply Leave and My Leave History, and no Leave Management. As
   Manoj and as Divya: OPERATIONS → Leave Management and no LEAVE section.
2. **Apply (US1, SC-001)**: as Tara open Apply Leave, choose a type, two dates and a reason, tap Check. The
   preview shows the server's working days; a weekly off in the range reads "Weekly off", a holiday "Holiday".
   Submit: My Leave History opens with "Request submitted" and the new Pending request on top. Time it: under
   90 seconds.
3. **Refusals (SC-004)**: try dates overlapping that request, a start more than 30 days back, a range over 90
   days, and (after the web locks the month) a locked month. Each shows plain wording; nothing is created;
   the draft stays.
4. **Offline submit (SC-006)**: airplane mode, Submit: "No connection", the draft is kept. Turn it off, Submit.
5. **History and cancel (US2)**: filter by each status; a Rejected request shows the reason; cancel the Pending
   one (confirm): it becomes Cancelled. Cancel on an Approved request that has started is not offered.
6. **Decide (US3, SC-002)**: as Manoj open Leave Management: only his Teachers' Pending requests, with the
   count. Open one: the days it would mark are shown. Approve with a note; open another and Reject with a
   reason. On the web both show; Tara's history shows the outcomes; Tara's My Attendance (spec 019) shows the
   Leave days.
7. **Revoke and conflicts**: revoke an Approved request with a reason (its Leave days disappear from My
   Attendance). On the web mark a covered day as Present as Manoj, then try to approve: the app explains the
   day was marked by a supervisor and reloads the request.
8. **Scope (SC-005)**: the second Manager's Teacher's request never appears for Manoj; opening it by link
   shows "Not found". Divya sees both Managers' requests.
9. **Home (US4)**: Manoj's and Divya's Home show the Pending count; tapping it opens the Pending list;
   after deciding one the count drops. Tara's Home has no leave widget.
10. **Action buttons (SC-007)**: change the phone clock by a day; the buttons shown on every request still
    match what the server allows.
11. **Audit (SC-010)**: on the web AUDIT pages, the create, cancel, approve, reject and revoke show source
    "Android app" with the location or the reason it is missing.
12. **Accessibility (SC-009)**: TalkBack on Apply Leave, History, Leave Management and the detail: every
    control is announced with a name; text stays usable at large sizes in light and dark themes.
