# HLS Postman collection

Stored in Postman's file-based format (Postman 11+ "local resources"): open this `postman/` folder
as a workspace in Postman and it reads the collection and environments straight from these files.

- `HLS API/` - the collection, one folder per spec. When a spec adds endpoints, add a folder (or
  requests) in the same commit. Each request is a `*.request.yaml` file; folder settings are in
  `.resources/definition.yaml`, and the collection-level variables and Bearer auth are in
  `HLS API/.resources/definition.yaml`.
- `HLS - Local|Dev|Prod.environment.yaml` - `baseUrl`, `loginIdentifier`, `loginPassword` (secret),
  `loginPhone`. Dev and Prod `baseUrl` are placeholders; set the real hosts. Never commit
  credentials; fill them in Postman only.
- `.postman/resources.yaml` - tells Postman which of these files are the collection and the
  environments.

Run **Password Login** first; it stores `accessToken` (collection variable, Bearer auth) for the
other requests.

## Folders

`001 Identity & Access`, `002 Access Model & Permission Matrix`, `003 Audit`, `004 User & Role
Management`, `005 Master Data` (Zones & Places, Schools, Managers & assignments, Teachers, Teacher
salary), `008 Attendance` (Status codes & Holiday Calendar, Teacher self-service, Supervisors, Admin
and Director: grid, export, month lock), `009 Leave` (Teacher self-service: types, preview, submit, history,
cancel; Supervisors: list, get, approve, reject, revoke), `010 Notifications` (own notifications:
list, unread only, unread count, mark read, mark all read, delete, clear read), `012 School Contracts` (the MoU: list, a School's contracts, signatory candidates, record a new MoU, record on a pending contract, end, cancel, map Teachers to positions), `016 Campus Recruitment` (colleges, drives, candidates, outcomes and assessment, offers: draft, issue, replace, accept, decline, letter; induction batches, attendance, sign-off, ready to deploy; the recruitment dashboard; a School's principal and accountant contacts), `013a Salary Structures` (each person's own salary: list people, one person's history, record a salary, a staff member's dates; the suggested default of each designation; the pay policy: read, record a new version), `013b Payroll Run` (start, read, compute again, cancel, submit, return and approve a pay run; its lines, one line with its frozen working and the people still owed; adjustments; manual payment records and a person's history; payslips as PDF and zip; the CSV export), `025 Manager Tasks` (create, lists and counts, acknowledge, start, complete, overdue reason, verify, send back, cancel), `023 School Marketing` (prospects, stages and the Final Stage review, win and School creation, proposal revisions, visits and attachments, the dashboard and settings), `032 Manager Attendance` (Manager self-service: today, check-in and check-out with a location, completing a missing check-out, the optional photo, the month, forgotten check-ins, leave types and Manager leave; Admin and Director: the grid, missed check-ins, who is on leave, a Manager's month, corrections, the month lock and reopen, the check-in times; and the decisions on leave and forgotten check-ins).

The 005 requests save `zoneId`, `placeId`, `schoolId`, `managerId`, `teacherId` and their versions
from create responses so the next request works in sequence. The 008 requests use the collection
variables `month` (`YYYY-MM`), `markDate` (`YYYY-MM-DD`) and `teacherId`; set them before running a
request, and use a Teacher or Manager sign-in for the self-service and Manager requests (change
`loginIdentifier` and run Password Login again; a Teacher signs in with a password or a one-time code).

The 032 requests use the collection variables `month` (`YYYY-MM`), `date` (`YYYY-MM-DD`), `managerId`, `leaveTypeId`, `managerLeaveId` and `regularizationId`; the Grid, the lists and the create requests save the ones the next request needs. Sign in as a Manager (the demo Manager is `manoj.manager`) for Manager self-service and as an Admin or Director for the rest; only an Admin may lock or reopen a month.

## Local environment

`HLS - Local.environment.yaml` is pre-filled with the **dev-only demo** Admin
(`asha.admin` / `Password123!`, created by `scripts/run-backend.ps1`'s demo seed) and the demo
Teacher phone. Dev and Prod stay blank on purpose. See `docs/running-locally.md`.
