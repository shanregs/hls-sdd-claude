# Contract: Screens (Android)

One screen, reached only from the server's menu item **My Expenses** (route `/operations/my-expenses`, section OPERATIONS). A route
the registry does not know is not shown; the review item `/operations/expenses` is not known, so it never appears. No screen or
button is chosen by role name. Every state has loading, empty, error and "no connection" with Try again; light and dark themes; 48 dp
targets; TalkBack labels; amounts in rupees with Indian grouping, dates DD/MM/YYYY. Detail and form open inside the screen and Back
closes them first (`useInnerBack`).

## My Expenses list

- Header: "Owed to you: ₹N" (the server's `owed`), a status filter (All, Draft, Submitted, Approved, Rejected, Paid, Paid by company), an
  optional month chooser, and an **Add expense** button when the menu grants Create.
- **Waiting to send** section at the top (only the signed-in user's items), with a count: each card shows amount, category, date and one
  of the outbox states with its words; "Not sent" shows the server's reason. Tap opens the item; it can be edited, sent again, or
  deleted. A banner row says "Sending..." or "Waiting for a connection" with **Send now**.
- Claims below, newest first, 50 at a time with Load more. A card: category, amount, date, area, status in words (Rejected in its own
  style), number of bills or "no bill", and the linked work label. The decision note is on the detail (gap G4).
- Offline or load failed: the last loaded list is shown under "May be out of date, loaded <time>" and filtered locally; the waiting
  section is always shown. Empty: "No expenses yet" pointing to Add expense. System and any user without the menu item never reach this
  screen (direct access shows "not authorized").

## Claim detail (inside the screen)

- Amount, date, category (and reason for Other), area, note, paid by, link, status; for Submitted the age; the **decision** (who, when,
  reason) for Rejected or Approved; for Paid the payment details (how, date, mode, reference).
- **Bills**: name, type, size, and a thumbnail from downloaded bytes for images (viewable full screen); pdf by name only; removed bills
  shown struck through with the reason. "n of 5".
- **History**: oldest first with who and when.
- Buttons, each only when `allowedActions` has it: **Edit** (opens the form; for Draft or Rejected; Rejected shows **Correct and resubmit**),
  **Submit** (Draft), **Withdraw** (Submitted; confirm dialog; becomes Draft), **Delete** (Draft; confirm dialog), **Add bill**.
  Withdraw and Delete need a connection; offline they explain that and do nothing.
- Offline: the row's fields and "More details need a connection".

## Claim form (new, edit a Draft, correct a Rejected, or edit a waiting item)

- Fields: Amount (rupees, two decimals), Date (default today by the server-corrected clock; chooser hints the last 60 days, not
  blocking), Category (six, in words), Area (the server's areas incl. Head office; default Head office), Note, Paid by (Myself / Company).
- Category fields: **Reason** required for Other; **Batch** (induction batches) required for Training Stay; **Linked work** optional
  for Travel and Fuel (planned visits, drives and tasks of the claimant around the date). Changing category or date reloads the choices
  and clears an invalid choice.
- **Bills** (up to 5): Take photo (camera), Choose photo (gallery), Choose PDF. Each shows a preview with Retake and Remove. A photo is
  prepared (jpg, about 2000 px) as it is added, with a short "Preparing..." state; if it cannot be read: "This photo could not be read.
  Try another or retake it." A sixth bill is refused with the reason. Camera refused: the explanation, the gallery stays available.
- **No bill**: with no bill, a "Reason there is no bill" field appears and is required to submit.
- Buttons: **Save as draft** and **Submit**; Submit is disabled until amount, date, category, area and (reason / batch / bill-or-no-bill
  reason) are present (early hints; the server decides). On Submit/Save the claim is written to the outbox first (storage full: "Not
  saved. Free some space and try again." and the form stays). Online, the form waits for the result: success closes it with the claim
  shown; a server refusal shows its reason in place with every entry kept; a possible duplicate opens the **Duplicate dialog**.
  Offline, the form closes with "Saved. It will be sent when you are back online."
- Editing a waiting item resets its refusal and sets it `WAITING`. A refused item shows the reason at the top; a stale-version refusal
  also shows "Current claim on the server" values for comparison.

## Dialogs

- **Duplicate**: "You may have raised this already: same date, amount and category", the matching claims (read by id), Submit anyway /
  Cancel (online form) or Confirm / Keep as Draft (waiting item).
- **Sign-out warning** (from the menu's Log out and the Profile screen): shown only when the user has unsent items: "N claims are not
  sent yet. They stay on this phone and are sent when you sign in again." Sign out / Stay.
- **Withdraw / Delete confirmations**; **Bill source** (Take photo / Choose photo / Choose PDF).

## Words

Statuses: Draft, Submitted, Approved, Rejected, Paid, Paid by company (the server's `statusLabel`). Outbox: Waiting to send, Sending,
Possible duplicate, Not sent. Categories: Rent and Utilities, Travel and Fuel, Training Stay, Marketing, Office Supplies, Other. A
refusal is the server's `reason`; the app's own texts are "No connection. Nothing was sent.", the hints, the photo messages above,
"Not saved. Free some space and try again.", the sign-out warning, and the empty and error states.
