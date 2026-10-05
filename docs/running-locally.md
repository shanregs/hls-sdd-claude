# Running HLS locally

Everything below was verified on a clean database: the backend started, Flyway created every table
(V1-V23) and the demo data seeded itself.

## Prerequisites

Docker Desktop, JDK 25, Maven, Node 20+ (and optionally Postman).

## 1. Start the database

```powershell
.\scripts\start-db.ps1
```

PostgreSQL runs in Docker on **localhost:5433** (database, user and password are all `hls`). It uses
its own compose project (`hls-local`) and a separate port, so it never touches another PostgreSQL
you already run on 5432. Tables are created by Flyway when the backend starts; nothing to run by hand.

## 2. Start the backend (terminal 1)

```powershell
.\scripts\run-backend.ps1
```

Serves http://localhost:8080. The script turns on the dev-only demo data
(`hls.seed.demo-data=true`) and `COOKIE_SECURE=false` for plain http. Seeding is idempotent, so
restarting is safe. To start from a completely empty database:

```powershell
docker compose -p hls-local down -v   # deletes the local HLS data only
.\scripts\start-db.ps1
```

## 3. Start the frontend (terminal 2)

```powershell
.\scripts\run-frontend.ps1
```

Opens http://localhost:5173 (it proxies `/api` to the backend on 8080).

## Demo data

| Who | Sign in with | Roles |
| --- | --- | --- |
| Asha Admin | `asha.admin` or `9800000001` / `Password123!` | Admin |
| Divya Director | `9800000002` / `Password123!` | Director |
| Manoj Manager | `manoj.manager` or `9800000003` / `Password123!` | Manager |
| Tara Teacher | `9800000004` / `Password123!` (or a one-time code) | Teacher |
| Sunil System | `sunil.system` or `9800000005` / `Password123!` | System |

Every user can sign in with a password or, as the other option (for example after forgetting the
password), with a one-time code: choose the One-time code tab, or the link under the password form.
The one-time code is not sent anywhere in dev: the backend console prints
`[DEV SMS STUB] To 9800000004: your HLS sign-in/reset code is NNNNNN`. Request a code on the sign-in screen, then read it from the
backend terminal.

Master data seeded for you: **Demo Zone** with two Places (Madurantakam 603306, Maraimalai Nagar
603209), **Demo School One** (Manager: Manoj, Teacher: Tara) and **Demo School Two** (no Manager,
no Teachers), and an unplaced **Unplaced Teacher**.

### Attendance demo data

With the demo flag on, the backend also seeds (idempotently):

- the **2026 Tamil Nadu public holidays** (Pongal, Republic Day, Tamil New Year, Independence Day,
  Gandhi Jayanthi, Deepavali, Christmas and the rest, 23 dates) as non-working dates, visible to every
  user under MASTER DATA -> **Holiday Calendar** and editable by Admin and Director. Festival dates
  that follow the lunar calendar should be checked against the official Government order before real use;
- three more placed Teachers (Meena Selvi and Karthik Raja in Demo School One, Lakshmi Priya in Demo
  School Two), placed 75 days back, with about three months of marks: mostly Present, plus some
  Leave, half days and Training. Sundays and holidays carry no marks. Today and yesterday are left
  unmarked so "Mark today" and the unmarked count have something to show, and every earlier month is
  complete, so you can try **Lock month** on a past month (Admin or Director, Attendance screen).

Demo Teachers are placed 75 days back only on a fresh database. On an existing database Tara keeps her
original placement date, so she has history only from then on.

## What to try

- As **Admin**: MASTER DATA -> Zones (add Places, import places by pasting `name,pinCode` lines),
  Schools (create, move, assign a Manager), Managers (assign Zones), Teachers (status, placement
  with a future date, salary).
- As **Manager** (Manoj): the dashboard shows 1 zone, 1 school, 1 teacher; Schools and Teachers
  list only what is assigned; the School edit only allows contact person, phone and address.
- As **Teacher** (Tara): ACCOUNT -> Profile shows her own details and no salary.
- As **System**: no MASTER DATA menu, and the Audit screens show no school/teacher entries.
- As **Admin** again: AUDIT -> Change History shows every change you made.

### Attendance

- As **Teacher** (Tara, one-time code): MY ATTENDANCE -> My Attendance shows the current month as a
  calendar. Click today (or use "Mark today") to mark it; you may also mark or correct the previous
  3 days, but not older days, future days, or a day your Manager already set. Attendance History
  shows earlier months, read-only, with a Locked banner for locked ones.
- As **Manager** (Manoj): OPERATIONS -> Teacher Attendance is the month grid of your Teachers. Click a
  cell to mark or correct any unlocked day (a day you set can no longer be changed by the Teacher);
  click a name for the rollup and each mark's history.
- As **Admin** (Asha) or **Director**: OPERATIONS -> Attendance is the organization-wide grid with Zone,
  School, Manager and status filters. Sundays read "Sun" on grey, holidays read "H" on amber, Leave
  and Absent show their code on red. **Export CSV** downloads the filtered month. To try locking, go
  back to a past month (the demo data marks every earlier month completely) and choose **Lock month**;
  open a Teacher's name to **Reopen** a locked month with a reason and **Lock again**. A month that
  has not ended, or has an unmarked working day, is refused with the list of what is missing.
- Everyone: MASTER DATA -> **Holiday Calendar** shows the year or a month with holidays highlighted
  (the demo data has the 2026 Tamil Nadu public holidays) and a download icon that saves the year or
  month as a PDF. Only Admin and Director see the editing controls below the calendar.
- Everyone: ACCOUNT -> **Profile** shows your details, **Settings** edits them and changes your password,
  and **Sessions** lists where you are signed in as a grid (number, since when, which client). Each row
  has a delete icon, and the icon at the top deletes them all, including the one you are using, which
  signs you out. In Role & Permissions, the `MY_SESSIONS` row controls this per role.
- As **System** (Sunil): SYSTEM CONFIGURATION -> **All Sessions** lists every user's sessions; filter to
  one user, delete one, delete all of one user's, or everyone's (yours included).
- Admin/Director: MASTER DATA -> **Attendance Setup** lists the status codes. P, L, T and N are
  built in; S (Substitution), H (Holiday) and A (Absent, like Leave) are ordinary codes you can edit.

### Leave

The demo data also adds sample leave requests (Tara: one Pending, one Approved with its Leave marks, one
Rejected; Meena and Karthik: one Pending each in Demo School One; Lakshmi: one Pending in Demo School Two,
which has no Manager, so only Admin and Director see it).

- As **Teacher** (Tara): LEAVE -> **Apply Leave** (pick a type and dates; the page shows how many working days
  it covers, not counting Sundays and holidays; a half day can be taken on the first or last working day),
  then **My Leave History** to follow it, see the reason if it is rejected, and cancel while it is Pending or
  Approved but not yet started.
- As **Manager** (Manoj), **Admin** or **Director**: the dashboard shows how many leave requests await a
  decision; OPERATIONS -> **Leave Management** lists them (Pending first). Approve (optional note) or reject
  (reason required). Approving writes **L** marks for the covered working days into the attendance grid
  (a day another supervisor already set is refused and listed; a locked month blocks it). Approved leave can
  be revoked with a reason. A Manager sees only the Teachers assigned to them.
- Signing in: every user can use a password or, for example after forgetting it, a one-time code (the link
  under the password form). Tara has the password `Password123!` too.

### Notifications

The bell in the top bar shows how many notifications you have not read (hidden at zero, "99+" above 99) and
refreshes every 30 seconds while the tab is open. It opens ACCOUNT -> **Notifications**: newest first, unread
ones marked "Unread", **Unread only** filter, **Mark all as read**, **Clear read**, a delete icon per row, and
**Load more**. Clicking a notification marks it read and goes to the related screen. Every signed-in business
role sees only its own; the System role has no notifications. Delete and Clear read need the Delete action.

What creates a notification (inside the same transaction as the action, so a refused action leaves none):

- A Manager, Admin or Director **approves, rejects or revokes** a leave request: the Teacher is told, with the
  reason when there is one.
- A Teacher **submits or cancels** a leave request: the Teacher's Manager is told; when the School has no active
  Manager, every active Admin and Director is told.
- A supervisor **sets or clears an attendance day** for a Teacher: the Teacher is told. Several changes by the
  same person within 10 minutes become one notification ("updated 3 days of your attendance in October 2026").
  The Teacher's own marks and the Leave marks made by an approved leave create none.
- A supervisor **locks or reopens** a month: each Teacher is told once.

Notifications older than 90 days are deleted every night at 02:30 (`hls.notification.retention.enabled`, default
on). The demo data adds: Tara a read "leave approved", an unread "leave rejected" and an attendance notice;
Manoj the leave requests of Meena and Karthik; Asha the one from Lakshmi (her School has no Manager).
To see it live: sign in as Manoj and reject Tara's pending request, then sign in as Tara and watch the bell.

### School contracts (MoU)

The business process is: **MoU contract, then Teacher mapping, then attendance capture, then month-end billing and
salary** (billing and salary come in later specs). OPERATIONS -> **School Contracts** (Admin, Director and the Zone
Manager) lists every School in scope with its status (Active, Ends soon, MoU pending, No MoU yet, Ended), the
filled and vacant positions, and the Teachers not mapped yet. Open a School to see its contract history.

- **Record a new MoU** (Admin, Director): the number of Teachers, the salary (the same for all Teachers, or a
  different one for each position), the dates, and the signing details: the date signed, who signed for the School
  (name and designation), and who signed for HLS (the School's Zone Manager and/or a Director). A new MoU ends
  the current contract the day before it starts. A contract is never edited: a change is a new MoU.
- **Map Teachers**: from Teachers -> a Teacher's School assignment (Admin, Director) choose the School and, for a
  different-salary contract, the position. The Zone Manager maps through the same endpoint for their own
  Schools (`POST /api/v1/teachers/{id}/placements`). After a new MoU, **Map Teachers to this MoU** on the School page
  moves the School's current Teachers to its positions in one step with no gap in their placement.
- A School whose Teachers were placed before an MoU existed shows **MoU pending**; **Record the MoU** on its page
  fills it in. Teachers on a contract that has ended stay placed (attendance keeps working) and are listed as not
  mapped to the MoU in effect.

The demo data adds a 4-Teacher MoU (one salary, INR 15,000) for **Demo School One**, signed by Manoj (Zone Manager)
and Divya (Director), with Tara, Meena and Karthik mapped to positions 1 to 3 and position 4 vacant. Demo School
Two has no Zone Manager, so it stays **MoU pending**.

### Recruitment, offers and induction

The recruitment flow is: **campus drive, candidates and assessment, job offer, acceptance, induction, ready to deploy,
placement in a School** (placement and the first salary come from the School contracts of spec 012). The RECRUITMENT menu
has Campus Drives, Candidates, Offers, Induction and Dashboard.

- **Campus Drives** (Admin, Director, Zone Manager): add a college (name, city, placement officer and principal),
  then schedule a drive (dates, venue, season, interviewers). Open a drive to add candidates one by one or import a
  CSV (header `name,phone,email,degree,year,notes`), set an outcome (selected, waitlisted, rejected) and record an
  assessment (speaking, English, communication, 1 to 5). A Zone Manager reads every drive but changes only drives they
  scheduled or attend.
- **Offers** (the Director sends, replaces, accepts and declines; Admin and Zone Manager read): a draft can change, an
  issued offer never does (a new offer replaces it). **Accept** creates the Teacher "in training" from the candidate
  with no salary yet, and enrols them in the next induction batch with room. If the person is already a Teacher the
  acceptance is refused and names them. **Letter** opens the printable offer letter.
- **Induction** (Admin, Director): create a batch, enrol recruits (or let acceptance do it), record attendance (a
  present or half day becomes a training-day mark in Attendance with no School), and sign off each recruit. A
  completed recruit becomes active and appears under **Ready to deploy**; map them to a School in School Contracts.
  The first assignment writes the salary of the accepted offer, once.
- A School now also keeps a **principal** and an **accountant** contact (Master Data -> Schools -> Edit).

The demo data adds **Demo College of Arts** with a held drive, six candidates, offers in several statuses, and
**Ready Rani**, whose offer was accepted and who completed the **Demo Induction** batch, so she is ready to deploy.

### School marketing and the MoU pipeline

The marketing flow is: **prospect, visits, proposal, Final Stage review, win (create the School), record the MoU in School
Contracts**. The MARKETING menu has Prospects, Calendar, Pipeline, Dashboard and Settings.

- **Prospects** (Admin, Director, Zone Manager): add a School you are approaching (name, board, Zone, contact, expected
  Teachers). A Zone Manager sees and changes only the prospects of the Zones they manage. A School already on the list
  (same name, board and Zone) is refused with a pointer to the existing one. Open a prospect to plan a visit, call or
  meeting, complete it (an **outcome** is required, with an optional follow-up date), reschedule it (the earlier date
  stays) or cancel it (a reason). A planned visit past its date shows as **missed**. Attach photos or documents to a
  visit (jpg, png, pdf, docx, xlsx, txt up to 10 MB, ten per visit); only Admin and Director remove one, with a reason.
- **Calendar** shows the month's visits and your recruitment drives side by side; a clash is shown, not blocked.
- **Pipeline** is the board: Prospect, Contacted, Visit, Follow-up, Interested, Negotiation, Final Stage, then Won, MoU and
  Active, with On Hold and Lost. **MoU and Active follow the contract** in School Contracts (a live contract, then a
  Teacher placed), they are not dragged. Moving to Final Stage needs a **proposal** (Teacher count, start month, the
  same salary for all or one per position; each revision is kept and labelled "Proposal (not a contract)").
- **Final Stage review**: the Director, or the Zone Manager of the prospect's Zone, approves or rejects (a rejection needs
  a reason and returns it to Negotiation); Admin cannot by default. An approved prospect is **won**.
- **Win**: Admin or Director opens **Create the School**, picks a Place of the prospect's Zone, confirms the billing
  contact (and may add the principal and accountant), and is taken to the MoU form in School Contracts with the proposal
  filled in. If a School of that name already exists in the Place, link it instead. A Zone Manager cannot create the School.
- A won prospect with no MoU after **14 days** (Settings, Admin and Director, 1 to 90) is flagged and its owner, the Zone
  Manager, the Admin and the Director get one in-app notification.
- **Dashboard**: visits, prospects by stage, win rate, Schools won per Zone and owner, and **demand** (vacant positions of
  won Schools) against **supply** (shown "not available" until the recruitment module provides ready-to-deploy recruits).

Uploaded files are stored under `hls.files.directory` (default `./data/files` next to the backend; in Docker mount it as a
volume so it survives a restart).

The demo data adds five prospects in **Demo Zone**: Green Valley Public School (Contacted), Sunrise Matriculation School
(Visit, with a completed visit whose follow-up is overdue), Lakeview International School (Negotiation, with a proposal and
a planned meeting), Hilltop Public School (Final Stage, waiting for review) and **Demo School One** (won, linked to the
School that already has its MoU, so it shows Active).

## Postman

Import `postman/HLS.postman_collection.json` and `postman/HLS-Local.postman_environment.json`
(already filled with the demo Admin credentials), select the **HLS - Local** environment, run
**001 Identity & Access -> Password Login** first, then any request. Folder order matches the
specs; the 005 folder stores ids (`zoneId`, `placeId`, `schoolId`, `managerId`, `teacherId`) from
the create responses so later requests work in sequence.

## Troubleshooting

- `password authentication failed for user "hls"`: you are talking to a different PostgreSQL on
  5432. Use the scripts (port 5433) or point `DB_URL` at the right port.
- The backend cannot create the `btree_gist` extension: run
  `CREATE EXTENSION btree_gist;` once in the `hls` database as a superuser (the Docker image
  does this automatically).
- Port 8080 or 5173 in use: stop the other process, or set `SERVER_PORT` for the backend (and
  update the proxy in `frontend/vite.config.ts`).
