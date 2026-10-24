# Quickstart: Mobile Expenses

Validates spec 036 end to end. Device setup is in `docs/running-mobile.md`; backend and database in `docs/running-locally.md`. No
backend change is part of this spec, so the backend of `main` (with spec 015 merged) is enough.

## Prerequisites

- The stack running (`.\scripts\run-all.ps1`, or `start-db.ps1` and `run-backend.ps1`) with the demo data: the Manager Manoj
  (`manoj.manager`), the Director Divya, the Teacher Tara (`tara.teacher`), a System user, and the Admin Asha for the web side.
- For the link field: one planned visit for Manoj dated yesterday (Marketing Calendar on the web) and one open induction batch.
- A native build with the new packages: from `mobile/` run `npx expo install expo-image-manipulator expo-file-system expo-crypto
  expo-document-picker @react-native-community/netinfo`, `npm i @noble/ciphers`, then `npx expo run:android` (new native modules
  need a rebuild). An emulator (airplane mode via the quick settings) and ideally one real phone with HEIC photos switched on.

## Automated checks

```powershell
cd mobile
npm run typecheck
npm run lint
npm test
npm run check:manifest
```

Expected: all pass. Tests cover each story against the fake server: raise, edit, correct and resubmit, withdraw, delete; the category
fields; bill limits; conversion of HEIC and WebP to jpg within 2000 px and 10 MB; the outbox (save, kill and resume at create, bills
and submit; a lost reply after create and after a bill; a refusal; the duplicate warning; a stale version; two users on one phone;
sign-out and sign-in again); the list cache offline; menu visibility per role; and that nothing failed or offline is shown as done.

## Walkthrough (emulator or phone)

1. **Menu**: sign in as Manoj. OPERATIONS shows My Expenses and no Expenses (review). Sign in as Tara (Teacher): My Expenses is
   there. As Divya (Director): My Expenses only. As the System user: no expense entry, and no expense screen is reachable.
2. **Raise online**: Add expense: Travel and Fuel, 850, yesterday, Manoj's Zone, note, paid by myself. Linked work offers yesterday's
   visit; choose it. Take a photo, see the preview, Retake once, Submit. The claim shows Submitted in the list and in the web My
   Expenses with one bill; the web history shows one creation and one submission.
3. **Category fields**: Other with an empty reason: Submit stays disabled; fill it and submit. Training Stay requires a batch (the open
   batch is offered). No bill: leave out the photo, the no-bill reason is required.
4. **HEIC / WebP**: pick a HEIC photo and a WebP image from the gallery (and a large photo above 10 MB if available). No error is shown;
   the bill on the web is a jpg, long edge at most 2000 px, text readable, under 10 MB. A pdf under 10 MB is accepted unchanged; a pdf
   over 10 MB is refused with the reason. A sixth bill is refused.
5. **Offline capture and later sync**: switch to airplane mode. Add an expense with a photo and Submit: "Saved. It will be sent when
   you are back online." The list shows it under "Waiting to send" with the count. Close and reopen the app: still there. Switch
   airplane mode off: within about 30 seconds it sends, the waiting row disappears and the claim shows Submitted once in the web (no
   second copy). Repeat, but kill the app during the upload: on reopening it finishes without a duplicate claim or duplicate bill.
6. **Refusal on sync**: offline, edit a server Draft dated 58 days ago and Submit; stay offline until the server's date passes the 60-day
   limit (or use a seeded claim already older), then reconnect: the item shows "Not sent" with the server's reason and stays. Correct
   the date and send again: one claim, Submitted. Delete another refused item: it and its photo are gone.
7. **Duplicate**: raise a claim with the same date, amount and category as an existing one: the warning appears; Confirm sends it;
   Cancel keeps the entries.
8. **Server wins**: offline, edit the note of a server Draft and Submit (waiting). On the web change that Draft's amount, then reconnect:
   the item shows the "changed by someone else" reason and the server's current values; re-apply by hand and send.
9. **Shared phone**: with unsent items as Manoj, choose Log out: the warning counts them; sign out. Sign in as Tara: no waiting items,
   none are sent, and the list shows only Tara's claims. Sign in as Manoj again: the items are back and sent.
10. **My Expenses**: with claims in each status, check each filter, the owed total (Approved, paid by myself, unpaid) and that Rejected
    shows who, when and why on the detail with Correct and resubmit. Withdraw a Submitted claim (back to Draft); delete a Draft.
    Offline, open the list: it is marked out of date and the waiting items show.
11. **TalkBack and themes**: run steps 2 and 5 with TalkBack on, in light and dark.

Record the results in `quickstart-results.md` when the walkthrough is done, as in spec 034.
