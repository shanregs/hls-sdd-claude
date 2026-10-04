# Running HLS locally

Everything below was verified on a clean database: the backend started, Flyway created every table
(V1-V15) and the demo data seeded itself.

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
| Tara Teacher | `9800000004`, **one-time code only** (no password) | Teacher |
| Sunil System | `sunil.system` or `9800000005` / `Password123!` | System |

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
- Admin/Director: MASTER DATA -> **Attendance Setup** lists the status codes. P, L, T and N are
  built in; S (Substitution), H (Holiday) and A (Absent, like Leave) are ordinary codes you can edit.

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
