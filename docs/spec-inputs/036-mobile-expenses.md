# 036 Mobile Expenses: `/speckit-specify` input

2026-10-08. Roadmap row 036 (new). Depends on 015 (Expenses, server side) and 018 (Android app foundation).
Wave 4 in the delivery plan: do not run `/speckit-specify` until 015 is specified and its contracts exist. It
adds screens to the Android app and no new server rules. Decision D11 applies (claims have no amount limit;
the Admin or Director reviews).

## Feature description (paste as the argument to `/speckit-specify`)

036-mobile-expenses: Expense screens for the HLS Android app, built on the app shell, sign-in and
server-driven menus of spec 018 and the expense rules and APIs of spec 015, which stay unchanged.

1. **Raise an expense.** The employee enters the amount, date, category (the server's list: Rent and
   Utilities, Travel and Fuel, Training Stay, Marketing, Office Supplies, Other), area and a note. Travel and
   Fuel can be linked to a visit or a drive; Training Stay to a batch; Other requires a reason. The app
   shows the server's required fields per category.
2. **Bill photo.** The employee photographs the bill or receipt with the camera (or picks an image), sees a
   preview, and may retake it before submitting. The app compresses the image and sends it through the
   shared file store.
3. **Offline capture and later sync.** If the phone has no signal, the expense and its photo are saved on the
   device as a Draft with a "waiting to send" mark, and sent automatically when the connection returns. The
   employee can see, edit and delete unsent drafts. The server stays the source of truth; a draft the server
   refuses on sync is shown with the reason and kept for correction. This is the first offline capture in the
   app and sets the pattern for later specs.
4. **My expenses.** A list, newest first, filterable by status (Draft, Submitted, Approved, Rejected, Paid or
   Reimbursed), with amount, category, date and the decision note. A Rejected claim shows its reason and can
   be corrected and resubmitted, as 015 allows.
5. **Review (Director and Admin).** Where the server offers it, the approver sees the pending claims in their
   scope with the bill photo, and approves or rejects (reason required). Budget-versus-actual, ageing and
   reports stay on the web.
6. Menu entries appear only because the server's access model offers them (spec 018); what each button may
   do comes from the server's `allowedActions`. Refusals are shown in plain language and the entered data is
   kept. Every call carries the device location or its reason, as in spec 018.

Out of scope: budgets and reports (web), payment and reimbursement processing (015 and 013b), OCR or
automatic reading of bills, multiple bills per claim unless 015 allows them, other offline features, push
notifications, and iOS.

## Role & Permission Impact (to carry into the spec)

Whoever 015 lets raise expenses (Manager and other employees as defined there) sees their own claims. Admin
and Director may review. Teacher and System have no mobile expense screens unless 015 grants them. No new
permission keys: 036 uses those 015 adds.

## Points for `/speckit-clarify`

- Where are drafts stored (encrypted app storage), and what happens to them on sign-out or a different user
  signing in on the same phone?
- Duplicate detection: the same bill photo or amount and date sent twice after a retry.
- Should the expense date be capped to the past, and by how many days, on the phone?
- Photo limits: size, resolution, count per claim, and the allowed file types.
- Is a sync conflict possible (claim edited on the web while a draft is waiting), and who wins?
- Does the approver view on the phone belong here or stay web-only for version 1?

## Hand-off from the implemented spec 015 (where it differs from the text above)

2026-10-23. Spec 015 is implemented; use these facts when 036 is specified.

- **Photos:** the server accepts **jpg, png and pdf only**, at most 10 MB each and **5 bills per claim**. HEIC and WebP are
  refused (400 with the reason), so the app converts to jpg and keeps each file under 10 MB before upload.
- **Retries:** send a `clientRequestId` (a UUID made once per claim on the phone) on `POST /api/v1/my-expenses`. A repeat
  of the same id and the same body returns the first claim (200 instead of 201); the same id with a different body is 409.
  A likely duplicate on submit (same date, amount and category as a claim that is not Rejected) is 409 with `matches`;
  resend with `confirmDuplicate: true` after the person agrees.
- **Dates:** the expense date cannot be in the future or more than **60 days** old when **submitting** (a Draft may hold any date).
- **Teachers:** a Teacher has My Expenses (own claims only) like a Manager; the menu item comes from the access model.
- **Buttons:** show only what `allowedActions` lists (`EDIT`, `SUBMIT`, `WITHDRAW`, `DELETE`, `ADD_BILL`, `APPROVE`,
  `REJECT`, `REVERSE`, `RECORD_PAID`, `REMOVE_BILL`). Nobody can approve a claim they raised.
- **Links:** `GET /api/v1/my-expenses/link-options?category=&date=` lists the caller's own visits, meetings, drives and tasks
  near the date (Travel and Fuel) or induction batches (Training Stay); send `link: {kind, ref}` on the claim.
- **Areas:** `GET /api/v1/my-expenses/areas` lists Head office and the Zones.
- **Review on the phone:** the review endpoints (`/api/v1/expenses`) exist for Admin and Director; budgets, cost measures and the
  CSV stay on the web as 036 says.
