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
list, unread only, unread count, mark read, mark all read, delete, clear read).

The 005 requests save `zoneId`, `placeId`, `schoolId`, `managerId`, `teacherId` and their versions
from create responses so the next request works in sequence. The 008 requests use the collection
variables `month` (`YYYY-MM`), `markDate` (`YYYY-MM-DD`) and `teacherId`; set them before running a
request, and use a Teacher or Manager sign-in for the self-service and Manager requests (change
`loginIdentifier` and run Password Login again; a Teacher signs in with a password or a one-time code).

## Local environment

`HLS - Local.environment.yaml` is pre-filled with the **dev-only demo** Admin
(`asha.admin` / `Password123!`, created by `scripts/run-backend.ps1`'s demo seed) and the demo
Teacher phone. Dev and Prod stay blank on purpose. See `docs/running-locally.md`.
