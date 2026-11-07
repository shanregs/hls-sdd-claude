# Manual walkthrough checklists

Checks that only a person can do. Each section lists what to have ready, then numbered steps. For every step: do the
**Do** column, compare with **Expect**, and tick **Pass** or **Fail** (write what you saw next to a Fail).

Source of each section: the spec's `quickstart.md`, `quickstart-results.md` and `tasks.md`. Nothing here is a new feature.
Where a Selenium test already covers a step it says **Automated**; the step is still listed only where a person adds
something (looking, a real device, a decision).

Tick format: `[ ] Pass  [ ] Fail`.

## 0. Common setup (all sections)

Full instructions: `docs/running-locally.md` (web) and `docs/running-mobile.md` (Android).

| Item | Value |
| --- | --- |
| Start database | `.\scripts\start-db.ps1` (PostgreSQL on localhost:5433) |
| Start backend | `.\scripts\run-backend.ps1` (http://localhost:8080, demo data on) |
| Start web app | `.\scripts\run-frontend.ps1` then open http://localhost:5173 |
| Everything at once | `.\scripts\run-all.ps1` |
| Start from empty data | `docker compose -p hls-local down -v` then `.\scripts\start-db.ps1` (deletes local HLS data only) |

Demo logins (password `Password123!` for all; sign in with the phone number or user name):

| Person | Sign in with | Role |
| --- | --- | --- |
| Asha Admin | `asha.admin` or `9800000001` | Admin |
| Divya Director | `9800000002` | Director |
| Manoj Manager | `manoj.manager` or `9800000003` | Manager |
| Tara Teacher | `9800000004` (or one-time code) | Teacher |
| Sunil System | `sunil.system` or `9800000005` | System |
| Cora Contributor | `cora.contributor` or `9800000006` | Content Contributor |

One-time code: on the sign-in screen choose the One-time code tab. The code is not sent; read it from the backend
terminal line `[DEV SMS STUB] To 9800000004: your HLS sign-in/reset code is NNNNNN`.

Dates show as DD/MM/YYYY and money in Indian grouping (for example 1,00,000). Note any screen that does not.

Tester: ______________  Date: ____________  Build / commit: ______________

---

## 1. Spec 017b: Substitute day-rate pay

Source: `specs/017b-payout-reconciliation/quickstart.md` and `quickstart-results.md`.

**Already automated (Selenium `e2e/test_substitute_payouts.py`, 2026-11-01):** substitute badge on the Teachers page (step 1),
day rate with history (2), the list of days (3), mark Present then Absent and the "day still to come" refusal (4),
the pay run line for Sunita (5), Zone Manager sees no lock or export control (10), 375 px width with no sideways scroll (11).
**Never walked in a browser (do these by hand):** locking and reopening a month through the page, raising a Correction
through the page, the go-live move, and the human-only look of dark mode / keyboard / screen reader.

Prerequisites: stack running with demo data. Demo Substitutes: **Sunita Substitute** (three Present days last month, month
locked) and **Suresh Substitute** (one Present day, one Absent day, one day still to come). Day-rate designation "Cover
Teacher" at Rs 800. Sign in as Asha Admin unless stated. Menu: PAYROLL & FINANCE -> **Substitute Payouts**
(`/operations/substitute-payouts`), tabs **All days** and **Needs attention**, with Month and Status filters.

Note: the draft pay run for last month is recomputed by the automated test; if numbers differ from below, say so, do not
treat it as a failure by itself.

| # | Do | Expect | Result |
| --- | --- | --- | --- |
| 1.1 | Open Substitute Payouts, tab **All days**, set Month to last month | Sunita's three days listed with who, where and a status chip; totals shown for the filter | [ ] Pass  [ ] Fail |
| 1.2 | Set Month to this month | Suresh's Present and Absent days and the future day are listed | [ ] Pass  [ ] Fail |
| 1.3 | On Suresh's past Present day press the Absent button, then the Present button again | Status changes each time; no error | [ ] Pass  [ ] Fail |
| 1.4 | On the future day try to mark Present | Refused with a plain reason (a day still to come cannot be marked) | [ ] Pass  [ ] Fail |
| 1.5 | MASTER DATA -> Teachers: look at Sunita | A Substitute badge; no Placement, Link account or Salary action; designation and Edit remain | [ ] Pass  [ ] Fail |
| 1.6 | Salary Structures -> **Designation day rates**: try to add a rate of 0, then -5 | Both refused. Then add Rs 900 from a future date (for example 15/11/2026) and look at history | [ ] Pass  [ ] Fail |
| 1.7 | **Mark days / Lock:** choose a past month that has an unmarked day (or add one) and lock it | Lock is refused and names the unmarked day | [ ] Pass  [ ] Fail |
| 1.8 | Mark the unmarked day, then lock the month again | Month locked; the mark buttons for that month stop working | [ ] Pass  [ ] Fail |
| 1.9 | Reopen a locked month, give a reason | Reopened; AUDIT -> Change History shows the reopen with the reason. Lock it again afterwards | [ ] Pass  [ ] Fail |
| 1.10 | **Payroll line:** open the payroll run for last month (PAYROLL & FINANCE -> Payroll), compute it | One line for Sunita: 2.50 days x Rs 800 = Rs 2,000.00 (a full day counts 1.0, a half day 0.5), no deduction; the working lists each day | [ ] Pass  [ ] Fail |
| 1.11 | If a rate change falls inside the month, open the payslip | Two rows (one per rate) | [ ] Pass  [ ] Fail  [ ] N/A |
| 1.12 | A Substitute with no Present day, and a designation with no rate | No line for the first; the second shows "cannot pay" and does not block the run | [ ] Pass  [ ] Fail  [ ] N/A |
| 1.13 | Sign in as Divya Director, submit and approve the run | Days then show **Paid in run** on Substitute Payouts | [ ] Pass  [ ] Fail |
| 1.14 | **Admin Correction:** as Asha, change a paid day (cancel it / change the substitute) | Tab **Needs attention** lists it as "correction needed"; the approved run is unchanged | [ ] Pass  [ ] Fail |
| 1.15 | On that row use the raise-correction button, enter the amount and a reason | Correction saved; the next run applies it and the row becomes **Corrected** | [ ] Pass  [ ] Fail |
| 1.16 | Mark a day Present after the month was approved | Shows as back-pay waiting; it is paid by a supplementary run for that month, original month kept | [ ] Pass  [ ] Fail |
| 1.17 | Export CSV from the list | Rows equal the screen; a name starting with `=` is shown safely (not as a formula) | [ ] Pass  [ ] Fail |
| 1.18 | Sign in as Manoj Manager | Sees only his own Zone's days; no Lock, Export, Correction or go-live control | [ ] Pass  [ ] Fail |
| 1.19 | Go-live move (only if you have pre-go-live credits): run it | Migrated days appear flagged for review; a regular Teacher's credit is "settle first" | [ ] Pass  [ ] Fail  [ ] N/A |
| 1.20 | Keyboard only (Tab, Enter) through the list and a dialog; then dark mode (Account theme) | Everything reachable, focus visible, text readable in both themes | [ ] Pass  [ ] Fail |
| 1.21 | Screen reader pass (NVDA or Narrator) on the list | Buttons are announced with name and date (for example "Mark Sunita present on ...") | [ ] Pass  [ ] Fail |

Findings: ______________________________________________

---

## 2. Spec 029: Content management sign-off

Source: `specs/029-content-management/quickstart.md`, `quickstart-results.md`, `tasks.md` (T001, T033), and
`docs/aws-deployment-and-ddos-protection.md` section 8.

**Already automated (Selenium `e2e/test_content_management.py`):** steps 1 to 11 of the quickstart and the web part of 12 (create
a Contributor, menu, 20 MB mp4, PDF limit and refused types, return with comment, versions and approval, Manager task, own-content
approval refused, new version of approved content, file removal, six-column matrix).
**Not automated:** the "no reviewer" notice and granting Approve to Admin (step 8 part), Android refusal and Director tile count
(12), expense bill and MoU limits (13), large-upload behaviour behind Cloudflare, dark mode, phone width.

Prerequisites: stack running; Cora Contributor exists in the seed. Logins: Cora, Divya, Asha, Manoj (see section 0). Prepare
files: a 20 MB mp4, a 30 MB PDF, a small docx, a `.doc`, an `.exe` renamed `.pdf`, a 15 MB and a 5 MB file (for the expense bill
check).

| # | Do | Expect | Result |
| --- | --- | --- | --- |
| 2.1 | As Cora: look at the menu | Only Dashboard, My Content, Notifications, Account. Dashboard shows own-content counts | [ ] Pass  [ ] Fail |
| 2.2 | As Cora open `http://localhost:5173/operations/tasks` | "Not authorized" | [ ] Pass  [ ] Fail |
| 2.3 | Create content "Phonics Week 1" (category Lesson, subject English), submit the 20 MB mp4 | Version 1, status Submitted, no task | [ ] Pass  [ ] Fail |
| 2.4 | Submit the 30 MB PDF; then the `.doc`; then the `.exe` renamed `.pdf` | Each refused; the PDF message names the 25 MB PDF limit | [ ] Pass  [ ] Fail |
| 2.5 | As Divya: Content, open the item, open/download the file, return it with a comment | Cora gets a notification | [ ] Pass  [ ] Fail |
| 2.6 | As Cora submit version 2 (docx); as Divya approve | History shows both versions, the comment and the approval date | [ ] Pass  [ ] Fail |
| 2.7 | As Divya create content for Manoj with a due date | Manoj sees a task in My Tasks. Manoj submits a PDF: task shows Completed. Divya approves: task shows Verified | [ ] Pass  [ ] Fail |
| 2.8 | As Divya create and submit your own content | You cannot approve it. If no Admin holds Approve, Directors and Admins get a "no reviewer" notice once | [ ] Pass  [ ] Fail |
| 2.9 | As Asha grant **Approve** on Content to Admin in the permission matrix, then approve the Director's item as Asha (undo the grant afterwards if you do not want it) | Admin can approve | [ ] Pass  [ ] Fail |
| 2.10 | Submit a new version of already approved content | Status returns to Submitted; the earlier approved version stays marked | [ ] Pass  [ ] Fail |
| 2.11 | As Asha remove a file version with a reason | Row stays, file no longer downloads | [ ] Pass  [ ] Fail |
| 2.12 | Permission matrix | Six role columns; Content Contributor is "not applicable" except the allowed modules | [ ] Pass  [ ] Fail |
| 2.13 | Sign in to the mobile app as Cora (setup in section 4) | Refused | [ ] Pass  [ ] Fail |
| 2.14 | Director dashboard, Content tile | Counts match the Content list | [ ] Pass  [ ] Fail |
| 2.15 | Regression: upload a 15 MB expense bill, then a 5 MB one; a 10 MB MoU document | 15 MB refused at 10 MB, 5 MB accepted; MoU still accepts 10 MB | [ ] Pass  [ ] Fail |
| 2.16 | Dark mode and phone width (browser at about 375 px) on My Content, detail page and the submit dialog | Readable, no sideways page scroll | [ ] Pass  [ ] Fail |

### 2A. Video limit question (needs the owner's decision before go-live)

The app accepts video (mp4) up to **500 MB**, audio up to 100 MB, documents up to 25 MB (FR-005, decided 2026-10-26). The
deployment note says the Cloudflare proxy body limit is **100 MB on Free/Pro, 200 MB on Business, 500 MB on Enterprise**, so a
500 MB video cannot pass Cloudflare on the plans in the deployment doc. Choices recorded in the note:

- [ ] A. Keep 500 MB and upload large video from a DNS-only (grey cloud) hostname such as `upload.<domain>`, skipping Cloudflare for that path.
- [ ] B. Lower the video limit to 100 MB.
- [ ] C. Other: ____________________

Checks once the choice is made (staging or production-like only, not the local stack):

| # | Do | Expect | Result |
| --- | --- | --- | --- |
| 2A.1 | Upload a video just under the chosen limit as Cora through the real public address | Succeeds; progress shown; version created | [ ] Pass  [ ] Fail  [ ] N/A |
| 2A.2 | Upload one just over the limit | Refused with a message naming the type and limit | [ ] Pass  [ ] Fail  [ ] N/A |
| 2A.3 | Download the video as Divya | Plain download completes (no in-browser seeking is expected in v1) | [ ] Pass  [ ] Fail  [ ] N/A |
| 2A.4 | Ask the person running the server: nginx `/api/v1/content/` body limit 520m and timeouts, disk size for video, disk alarm and backup of the content folder (section 8 of the deployment note) | Confirmed in writing | [ ] Pass  [ ] Fail  [ ] N/A |

### 2B. Open sign-off items (from tasks.md and results)

- [ ] T001: Constitution 3.0.0 (six roles) decided by the owner 2026-10-26. Sign-off: remove the word PROPOSED from the Sync Impact Report and the version line in `.specify/memory/constitution.md`. Owner approves: ____________
- [ ] T033: walkthrough above recorded in `quickstart-results.md`.

Findings: ______________________________________________

---

## 3. Spec 034: Mobile visits and tasks (device walkthrough, T050)

Source: `specs/034-mobile-visits-tasks/quickstart.md`, `quickstart-results.md`. Setup: `docs/running-mobile.md`.
Nothing in this section has been run on a device yet (all steps "not done" in the results file), so record times.

Prerequisites:
- Stack running with demo data (Manoj has three demo tasks: one due today, two overdue).
- On the web as Asha or Manoj: at least one prospect in Manoj's Zone; two planned visits for Manoj (one today, one next week;
  MARKETING -> Marketing Calendar -> Plan an activity) and one visit last week never completed.
- Emulator or phone with the app built (`.\scripts\run-mobile.ps1 -Build` first time; backend must be up; the emulator reaches
  the PC at `http://10.0.2.2:8080`). Emulator camera/gallery or a real phone for photos.
- Logins: Manoj, Tara, Divya (section 0). Tara may use a one-time code or password.

| # | Do | Expect | Result |
| --- | --- | --- | --- |
| 3.1 | Sign in as Manoj. Open the menu | OPERATIONS has My Tasks; MARKETING has Marketing Calendar | [ ] Pass  [ ] Fail |
| 3.2 | Sign in as Tara; then Divya | Tara: neither entry. Divya: both, tasks read-only | [ ] Pass  [ ] Fail |
| 3.3 | Home as Manoj | Cards "N visits today" and "N overdue, M due today" match the web. Each opens its list. Home appears within 5 seconds (SC-001): ____ s | [ ] Pass  [ ] Fail |
| 3.4 | Visits list | Today's visit first, then next week's, then last week's marked Missed. Filter by Missed, then Completed. Month arrows work. Open a visit: target, date, kind, colleagues as on the web | [ ] Pass  [ ] Fail |
| 3.5 | Complete a visit: leave outcome empty | Send is disabled | [ ] Pass  [ ] Fail |
| 3.6 | Write an outcome, set a follow-up date, take a photo (real camera), Send. Time it (target under 90 s): ____ s | Visit shows Completed with outcome; photo uploads and shows; a follow-up task appears in My Tasks | [ ] Pass  [ ] Fail |
| 3.7 | Complete a visit that was already completed on the web (do it on the web first, then on the phone) | The server's reason is shown and your entries are kept | [ ] Pass  [ ] Fail |
| 3.8 | Plan a visit: search a prospect by typing, choose kind and date, add a colleague | Planned visit appears in the list | [ ] Pass  [ ] Fail |
| 3.9 | Reschedule it | Detail shows "Moved from" | [ ] Pass  [ ] Fail |
| 3.10 | Cancel another visit with a reason | Shows Cancelled with the reason. A completed visit offers none of the three buttons | [ ] Pass  [ ] Fail |
| 3.11 | Tasks: Today, Overdue, All views | Match the web | [ ] Pass  [ ] Fail |
| 3.12 | Open an overdue task; try Complete | Not offered until a reason of at least 5 characters is given; give one with a follow-up date | [ ] Pass  [ ] Fail |
| 3.13 | Another task: Acknowledge, start, then Complete with a note of at least 10 characters (the hint counts down) and a photo. Time it (target under 60 s): ____ s | Status Completed; moves to Awaiting verification | [ ] Pass  [ ] Fail |
| 3.14 | Evidence: add photos to a task until it has 5 | The add control disappears at 5 | [ ] Pass  [ ] Fail |
| 3.15 | On the web cancel a task that Manoj has open on the phone; on the phone tap Acknowledge | Server's reason shown; the task reloads | [ ] Pass  [ ] Fail |
| 3.16 | Add a task for me, due tomorrow (as Manoj) | Appears under Upcoming. As Divya the control is not offered | [ ] Pass  [ ] Fail |
| 3.17 | Airplane mode, then Send a visit completion and a task step | Each says "No connection. Nothing was sent." and keeps entries; nothing shows as done | [ ] Pass  [ ] Fail |
| 3.18 | On the web remove Edit from the Manager's TASKS or MARKETING permission; reload the app screen | Matching buttons are gone. Restore the permission afterwards | [ ] Pass  [ ] Fail |
| 3.19 | Android Back button on a visit or task detail | Closes the detail before leaving the screen | [ ] Pass  [ ] Fail |
| 3.20 | Date chooser and prospect search on a small screen; photo thumbnails after upload | Usable, nothing cut off | [ ] Pass  [ ] Fail |
| 3.21 | TalkBack on the visit list and the complete-visit form | Controls announced with labels, sensible focus order | [ ] Pass  [ ] Fail |

Record the times and results in `specs/034-mobile-visits-tasks/quickstart-results.md` and tick T050 in tasks.md.

Findings: ______________________________________________

---

## 4. Spec 018: Android foundation on an emulator (T086)

Source: `specs/018-android-app-foundation/quickstart.md` (scenarios 1 to 17), `quickstart-results.md`; setup
`docs/running-mobile.md`. The manual pass has not been run. T086 asks for the emulator plus one physical phone, TalkBack
and the SC-001 / SC-002 timings.

Prerequisites: Android Studio emulator (Pixel 7, Android 14 with Google APIs), backend + database + web app running with demo
data, app built. Set the emulator location first (Extended controls -> Location). Web sign-in as Asha for the audit screens.
Accounts: Tara (code), Manoj, Divya, Asha (refused), Sunil (refused), and a user with Admin plus Teacher if present in the seed
(if not found, mark N/A and say so).

| # | Do | Expect | Result |
| --- | --- | --- | --- |
| 4.1 | Sign in as Tara with a one-time code (read it from the backend terminal). Kill and reopen the app | Teacher home, no role chooser. Reopen goes straight to Home, no credentials asked | [ ] Pass  [ ] Fail |
| 4.2 | Sign in as Manoj with phone + password, then with user name + password. Try a wrong password, then 5 wrong passwords | Same generic message as web (does not name the wrong part); five wrong show the lockout time | [ ] Pass  [ ] Fail |
| 4.3 | Sign in as Asha (Admin only) | "Your account uses the HLS web application." and no session (web, as Asha, Account -> Sessions shows none from the app) | [ ] Pass  [ ] Fail |
| 4.4 | Admin + Teacher user, if present | Allowed; menu shows only items the app has screens for | [ ] Pass  [ ] Fail  [ ] N/A |
| 4.5 | Forgot password -> code -> new password -> sign in with it. Restore the password afterwards | Works | [ ] Pass  [ ] Fail |
| 4.6 | Menus: Tara, Manoj, Divya in turn. On the web as Asha remove a grant for a role; restart the app (or wait 5 minutes in background) | Each menu is that role's own; the menu follows the change with no new build. Restore the grant | [ ] Pass  [ ] Fail |
| 4.7 | Open a deep link to a route not in the user's menu | "Not authorized" with a way home | [ ] Pass  [ ] Fail |
| 4.8 | Logout, press Back, reopen. Then repeat Logout in airplane mode | Sign In shown, no previous name or menu; logout works offline | [ ] Pass  [ ] Fail |
| 4.9 | Sessions: sign in on the emulator and in a browser; app Profile -> Signed-in devices | Both listed, current one marked, browser one marked Web. End the browser one from the app: browser signs out on its next request. End the app's one from the web: app returns to Sign In with an explanation | [ ] Pass  [ ] Fail |
| 4.10 | Location allowed ("while using"): sign in, open ACCOUNT. Web as Asha: AUDIT -> Login History, filter source ANDROID | Row shows coordinates, accuracy, capture time, app version. A profile edit in the app shows a location in User Activity | [ ] Pass  [ ] Fail |
| 4.11 | Deny location; separately turn device location off | Sign-in and every call still work without extra wait; rows show PERMISSION_DENIED / SERVICES_OFF (NO_FIX if no fix) | [ ] Pass  [ ] Fail |
| 4.12 | Leave the app idle, then 10 minutes in background | No location use in the device's location log, no new audit rows | [ ] Pass  [ ] Fail |
| 4.13 | Open Home; web as Asha: AUDIT -> API Access for that user and day | One row per request (method, route pattern without ids/query, status, location); none for `/mobile/app-config` | [ ] Pass  [ ] Fail |
| 4.14 | As Tara, Manoj, Divya (web and app) look for location screens; Asha and Sunil check audit | Teacher/Manager/Director see no location anywhere; System and Admin can see it | [ ] Pass  [ ] Fail |
| 4.15 | Invalid location / rooted device / version gate (quickstart scenarios 13 to 15) | These need a proxy or backend setting (`hls.mobile.min-app-version`); ask a developer to do them. Mark N/A if not done | [ ] Pass  [ ] Fail  [ ] N/A |
| 4.16 | Airplane mode, open app while signed in | "No connection" and Retry, no menu. Turn it off, Retry: Home loads | [ ] Pass  [ ] Fail |
| 4.17 | Dark mode (device) and the in-app override on Sign In, Home, ACCOUNT, Sessions, Not authorized | Readable, contrast acceptable in both | [ ] Pass  [ ] Fail |
| 4.18 | TalkBack on the same screens | Every control labelled, sensible focus order, touch targets at least 48 dp | [ ] Pass  [ ] Fail |
| 4.19 | Timings on the emulator, then on one physical mid-range phone: SC-001 ____ s, SC-002 ____ s | Within the targets in spec.md SC-001/SC-002 | [ ] Pass  [ ] Fail |

Record in `specs/018-android-app-foundation/quickstart-results.md` and tick T086.
Open owner items from the results file (decisions, not tests): retention period for API Access/location data, approved
privacy-notice wording for the permission text, distribution channel and app name/icon, Play Integrity yes/no.

Findings: ______________________________________________

---

## 5. Spec 028: Management dashboard dark mode and phone width (T068)

Source: `specs/028-management-dashboard/quickstart.md` section 8 and the Results block, `tasks.md` T068. Sections 1 to 7 are
covered by automated tests; the browser part below is what is left: dark mode, phone width, real colour contrast, clicking
each tile through to its list.

Prerequisites: web stack with demo data (overdue task, missed visit, a Manager with no check-in, a Teacher absent with an open
substitution, one pending Manager leave, a payroll run awaiting approval). Logins: Divya, Asha, Manoj (Zone 1), a second Manager
in Zone 2 if present, Tara, Sunil. Phone width: Chrome DevTools device toolbar at 375 px, or resize the window.

| # | Do | Expect | Result |
| --- | --- | --- | --- |
| 5.1 | As Divya open Dashboard (light, desktop) | Areas Managers, Schools, Recruitment, Teachers, Attendance and leave, Payroll with tiles and a "figures taken at" time; no blank area, no placeholder figure | [ ] Pass  [ ] Fail |
| 5.2 | Click every tile in turn | Lands on the listed screen with the filter applied; the list count equals the tile (check overdue tasks, Final Stage, Teachers away, uncovered substitutions) | [ ] Pass  [ ] Fail |
| 5.3 | Tiles with newly wired filters: Marketing calendar missed, Teacher attendance away, Manager attendance missing, induction ready to deploy, payroll owed | Each opens at the right place (the Marketing calendar shows a Show all button) | [ ] Pass  [ ] Fail |
| 5.4 | Switch to dark mode (Account -> Settings theme) and view the Dashboard | All figures, labels, tile borders and chips readable; no light patches; contrast good by eye | [ ] Pass  [ ] Fail |
| 5.5 | Phone width 375 px, light then dark | No sideways page scroll; tiles stack; figures readable | [ ] Pass  [ ] Fail |
| 5.6 | Check formats | Rupees in Indian grouping, dates DD/MM/YYYY | [ ] Pass  [ ] Fail |
| 5.7 | Keyboard: Tab through tiles; screen reader on one tile | Every tile reachable; figure and label read together | [ ] Pass  [ ] Fail |
| 5.8 | Zone picker (Director): Zone 1, then last month | Zone-aware tiles change; payroll and recruitment say "organization-wide"; "today" tiles stay | [ ] Pass  [ ] Fail |
| 5.9 | Approvals inbox: pending leave and payroll run | Two entries, oldest first; open one, decide it, Refresh: it is gone | [ ] Pass  [ ] Fail |
| 5.10 | Refresh: change a task elsewhere, leave the page open | Nothing changes until Refresh; Refresh updates the time | [ ] Pass  [ ] Fail |
| 5.11 | As Manoj | Only his Zone's figures; no comparison table, payroll or inbox | [ ] Pass  [ ] Fail |
| 5.12 | As Tara and Sunil | Own dashboards unchanged, no management tiles | [ ] Pass  [ ] Fail |

Note: the Manager incentives tile has since moved from the Payroll area to the Managers area (spec 028 FR-015, PR #182); look
for it there.

Record at the end of `specs/028-management-dashboard/quickstart.md` and tick T068.

Findings: ______________________________________________

---

## 6. Manual parts: spec 014b (T033) and spec 040 (T049)

### 6A. Spec 014b School, collections and margin reports

Source: `specs/014b-school-reports-margin/quickstart.md`, `quickstart-results.md`.
**Automated (Selenium `e2e/test_school_margin_reports.py`, 19 passed):** steps 1, 2, 5, 6, 7, 8, 9.
**Human-only / not automated:** step 3 (transfer mid-month, then billing and payroll), step 4 (remove a payslip line), and the
"compare by eye with the other screens" judgement. The steps below are those plus a spot check.

Prerequisites: stack with demo data, a pay run and billing for a month (the 022 and 013b seeders). Logins: Asha, Manoj, Tara.

| # | Do | Expect | Result |
| --- | --- | --- | --- |
| 6A.1 | REPORTS -> Margin, a month with a pay run. For three Teachers compare Fee with the School Billing due line, Pay with the payslip net | Margin = Fee - Pay for each | [ ] Pass  [ ] Fail |
| 6A.2 | Month with a draft run; month with no run | Banner "payroll not approved, figures may change"; "no data yet" | [ ] Pass  [ ] Fail |
| 6A.3 | Move a Teacher between Schools mid-month (Transfers), run billing and payroll for that month | Two Margin rows for the Teacher; the two Pay values add up to the payslip net | [ ] Pass  [ ] Fail |
| 6A.4 | Remove a payslip line (or bill a Teacher with no payslip) | Row says "not matched" with the reason; School and Zone totals do not change | [ ] Pass  [ ] Fail |
| 6A.5 | Spot check Collections and School reports against the School ledger, Teachers list, REPORTS -> Attendance, Substitutions, Teacher Performance, Marketing pipeline | Figures agree | [ ] Pass  [ ] Fail |
| 6A.6 | Teacher login | No REPORTS items for these reports | [ ] Pass  [ ] Fail |

Note from tasks.md T033: the backend `ReportingModuleRulesTest` failed on `SameDayReportService` from spec 040, not on 014b code.
That is a developer item, not a walkthrough step.

Findings: ______________________________________________

### 6B. Spec 040 Same-day flexibility (T049)

Source: `specs/040-same-day-flexibility/quickstart.md`, `quickstart-results.md`. The implementation is merged to main
(PRs #150, #157 and fix #177); run this on the normal local stack at http://localhost:5173 (see `docs/running-locally.md`).
**Automated (Selenium `e2e/test_same_day_flexibility.py`, 12 passed):** web parts of steps 1, 3, 6, 8, 10.
**Left for a person (T049 stays open):** mobile steps (7 offline), notification inboxes, steps needing a second day, and the
hand-computed pay (step 4), Manager cover (5), range leave (2).

Prerequisites: stack with demo data, a working day today (on a weekly off or holiday expect "not a working day"), Tara placed at
a School whose Manager is Manoj. Logins: Tara, Manoj, Asha. For 6B.12 and 6B.13 a phone/emulator (section 3 setup).

| # | Do | Expect | Result |
| --- | --- | --- | --- |
| 6B.1 | As Tara: Apply Leave, first and last date today, any type, a reason | Page says it takes effect at once. Status Approved, chip "Same-day", no Pending step | [ ] Pass  [ ] Fail |
| 6B.2 | Look at Tara's My Attendance; Manoj's notifications; Substitution list | L for today; Manoj has a notification; an open requirement for today exists | [ ] Pass  [ ] Fail |
| 6B.3 | As Manoj: Leave Management -> Record same-day leave for another Teacher in scope; then try one outside scope | Same result with "Recorded by" Manoj; outside scope: "not found" | [ ] Pass  [ ] Fail |
| 6B.4 | Apply from today to the day after tomorrow (use another Teacher if today is already used) | History shows two parts: today Approved (Same-day), later days Pending, linked. Manoj rejects the later part: today stays Approved | [ ] Pass  [ ] Fail |
| 6B.5 | Guards: locked month; non-working day; a day Manoj marked Present; a day Manoj marked Absent | First three refused with reason; Absent day accepted, mark becomes leave, earlier Absent kept in the day's history | [ ] Pass  [ ] Fail |
| 6B.6 | Pay: Teacher with an approved full Casual day and an approved half day; run payroll for the month | Unpaid days equal 1.5 days' worth by hand from the month's pay policy | [ ] Pass  [ ] Fail |
| 6B.7 | Manager cover: on the open requirement choose "I covered" with a note | Covered by Manager; row in Substitution -> Manager covers (label "Manager cover", chip "Same-day") | [ ] Pass  [ ] Fail |
| 6B.8 | Assign a Teacher substitute instead | Cover row shows Ended with history kept; the Teacher gets the credit; a Teacher's credit list never shows the cover | [ ] Pass  [ ] Fail |
| 6B.9 | Manoj: My Tasks -> Log for today (School visit, note, Completed); Observation for a Teacher with an open due; "Done on the spot" on a task Asha planned for today | Under Done, not Awaiting verification, chip "Same-day"; observation due clears; spot-done is final with assigner kept in History | [ ] Pass  [ ] Fail |
| 6B.10 | Visits -> Log a visit for today | Completed, in Activity Calendar with the chip | [ ] Pass  [ ] Fail |
| 6B.11 | As Asha: filter by Same-day on Leave Management, Substitution, Manager Tasks, Visits, Activity Calendar; open Change History for one item; open `/reports/same-day`, pick a Zone | Exactly the items above; history shows who recorded it, for whom, when, "auto-accepted"; counts match; a Zone narrows every section | [ ] Pass  [ ] Fail |
| 6B.12 | Phone offline: airplane mode, log a task for today, reconnect, press retry twice | Syncs once (one record) with the day it was logged as its date | [ ] Pass  [ ] Fail |
| 6B.13 | Phone offline from the start: Visits -> Log a visit for today | Schools last loaded on the phone are offered (search works); visit kept as same-day and sent on reconnect. With no School list saved the form says so | [ ] Pass  [ ] Fail |
| 6B.14 | Regression: Tara applies leave for tomorrow; Asha assigns a task to Manoj | Tomorrow's leave is Pending and goes through approval; the task still needs Acknowledge, Complete, Verify | [ ] Pass  [ ] Fail |

Developer item (not a person step): quickstart step 9, `SameDayLeaveConcurrencyTest`.
Record in `specs/040-same-day-flexibility/quickstart-results.md` and tick T049.

Findings: ______________________________________________

---

## 7. Go-live smoke test across modules

A short pass on the real (staging/production) site after deployment, assembled from the section checks above and the roles in
`docs/running-locally.md`. Use real accounts created for go-live, not the demo seed (the demo-data flag must be off).
About 30 minutes. Stop and report on the first Fail.

Tester: ______________  Environment URL: ______________________  Date: ____________

| # | Do | Expect | Result |
| --- | --- | --- | --- |
| 7.1 | Open the site address (https), sign in as Admin with password | Dashboard loads; no browser certificate warning; menu shows Admin items | [ ] Pass  [ ] Fail |
| 7.2 | Sign in as Admin with a one-time code (the SMS must really arrive) | Code received and accepted | [ ] Pass  [ ] Fail |
| 7.3 | Wrong password 5 times for a test user | Lockout message with the time | [ ] Pass  [ ] Fail |
| 7.4 | Master data: open Zones, Schools, Managers, Teachers, Holiday Calendar, Designations | Lists load with real data; Holiday Calendar shows the year's holidays | [ ] Pass  [ ] Fail |
| 7.5 | Attendance: as a Teacher mark today; as the Manager open Teacher Attendance and correct a cell | Mark appears in the grid; a Manager's mark cannot be changed by the Teacher | [ ] Pass  [ ] Fail |
| 7.6 | Leave: Teacher applies for tomorrow; Manager approves | L marks appear in the grid for the covered working days | [ ] Pass  [ ] Fail |
| 7.7 | Substitution: a requirement for a day, a substitute allocated and marked Present (section 1) | Day shows on Substitute Payouts | [ ] Pass  [ ] Fail |
| 7.8 | Payroll: compute a draft run for a closed month; open one payslip | Lines, working and net agree with a hand calculation for one person | [ ] Pass  [ ] Fail |
| 7.9 | Do not approve a real run during the smoke test; approve only with the Director and the pay owner present | n/a | [ ] Pass  [ ] Fail  [ ] N/A |
| 7.10 | Reports: open Margin and Collections for a month; export CSV | Pages load; CSV opens in Excel | [ ] Pass  [ ] Fail |
| 7.11 | Expenses: submit a small expense with a bill (about 5 MB); try a 15 MB bill | 5 MB accepted; 15 MB refused at the 10 MB limit | [ ] Pass  [ ] Fail |
| 7.12 | Content: Contributor uploads a file just under the video limit chosen in 2A, through the public address | Succeeds (or is refused with a clear message if over the limit) | [ ] Pass  [ ] Fail |
| 7.13 | Management Dashboard as Director, phone-width browser and dark mode | Loads, readable, no sideways scroll (section 5) | [ ] Pass  [ ] Fail |
| 7.14 | As Admin open AUDIT -> Change History | Your steps above are listed with the right user and time | [ ] Pass  [ ] Fail |
| 7.15 | Role scope: sign in as Manager and as Teacher | Manager sees only own Schools/Teachers; Teacher sees only own data | [ ] Pass  [ ] Fail |
| 7.16 | Mobile: install the staging/release app on one phone, sign in as Teacher (code) and Manager (password); open Home | Home within 5 seconds; menu correct; an Admin account is refused with the "web application" message | [ ] Pass  [ ] Fail |
| 7.17 | Notifications: trigger one (for example a leave decision) | Notification appears in the inbox | [ ] Pass  [ ] Fail |
| 7.18 | Sign out on web and app | Back to Sign In; Back button does not show old data | [ ] Pass  [ ] Fail |
| 7.19 | Ask the server owner to confirm: demo-data flag off, backups running (database and content folder), disk alarm set, `COOKIE_SECURE` true | Confirmed in writing | [ ] Pass  [ ] Fail |

Go / No-go decision: ______________  Signed: ______________  Date: ____________
