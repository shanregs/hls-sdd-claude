# Quickstart: Mobile Visits and Tasks

Validates spec 034 end to end. Prerequisites and device setup are in `docs/running-mobile.md`; the backend and database are in
`docs/running-locally.md`. No backend change is part of this spec, so the backend of `main` is enough.

## Prerequisites

- The stack running (`.\scripts\run-all.ps1`, or `start-db.ps1` and `run-backend.ps1`) with the demo data, which has the Manager
  Manoj (`manoj.manager`), the Director Divya and three demo tasks for Manoj (one due today, two overdue).
- On the web as Asha (Admin) or Manoj: at least one prospect in Manoj's Zone, and two planned visits for Manoj, one today and one
  next week (MARKETING → Marketing Calendar → Plan an activity), plus one visit last week that was never completed.
- An Android emulator or phone with the app built by `npx expo run:android` from `mobile/` (no new native package since spec 033).

## Automated checks

```powershell
cd mobile
npm run typecheck
npm run lint
npm test
npm run check:manifest
```

Expected: all pass. The tests cover each story against the fake server: the visit list and its grouping, filter and month change;
completing a visit (outcome required, follow-up date, photos after completion, a failed photo never undoing it); plan, reschedule and
cancel; the task views, every step driven by the `actions` flags, the overdue reason, evidence up to 5; adding a task for oneself;
Home cards; menu visibility per role and permission; and that no failed or offline step is shown as done.

## Walkthrough (emulator or phone)

1. **Menu**: sign in as Manoj. OPERATIONS shows My Tasks and MARKETING shows Marketing Calendar. Sign in as Tara (Teacher): neither.
   As Divya (Director): both, with tasks read-only.
2. **Home**: the cards show "N visits today" and "N overdue, M due today", matching the web. Each opens its list.
3. **Visits list**: today's visit first, then next week's, then last week's as Missed. Filter by Missed and by Completed. Move to next
   month with the arrows. Open a visit: the target, date, kind and colleagues as on the web.
4. **Complete a visit**: leave the outcome empty: Send is disabled. Write an outcome, set a follow-up date, take a photo and Send. The
   visit shows Completed with the outcome; the photo uploads and shows; a follow-up task appears in My Tasks. Try completing it again
   from a second device or the web first: the server's reason is shown and the entries are kept.
5. **Plan, move, cancel**: plan a visit to a prospect (search by typing), choose the kind and date, add a colleague. Reschedule it: the
   detail shows "Moved from". Cancel another with a reason: it shows Cancelled with the reason. A completed visit offers none of the
   three buttons.
6. **Tasks**: the Today, Overdue and All views match the web. Open an overdue task: Complete is not offered until a reason of at least 5
   characters is given; give one with a follow-up date. Acknowledge another task, start it, then complete it with a note of at least
   10 characters (the hint counts down) and a photo. The status becomes Completed and it moves to Awaiting verification.
7. **Evidence**: add photos to a task until it has 5: the add control disappears at 5.
8. **Stale task**: on the web, cancel a task Manoj has open on the phone, then tap Acknowledge: the server's reason is shown and the task
   reloads.
9. **Add a task for me**: add a task for tomorrow; it appears under Upcoming. As Divya the control is not offered.
10. **Offline**: airplane mode, then Send on a visit completion and a task step: each says "No connection. Nothing was sent." and keeps
    the entries; nothing shows as done.
11. **Permissions**: on the web remove Edit from the Manager's `TASKS` or `MARKETING`; after the next load the matching buttons are
    gone.

## Results

Record outcomes and timings (SC-001, SC-002) in `quickstart-results.md` once implemented, marking anything not done as **not done**.
