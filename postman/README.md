# HLS Postman collection

- `HLS.postman_collection.json` — one folder per spec. When a spec adds endpoints, add a folder
  (or requests) in the same commit.
- `HLS-Local|Dev|Prod.postman_environment.json` — `baseUrl`, `loginIdentifier`, `loginPassword`
  (secret), `loginPhone`. Dev and Prod `baseUrl` are placeholders; set the real hosts. Never commit
  credentials; fill them in Postman only.

Run **Password Login** first; it stores `accessToken` (collection variable, Bearer auth) for the
other requests.

## Folders

`001 Identity & Access`, `002 Access Model & Permission Matrix`, `003 Audit`, `004 User & Role
Management`, `005 Master Data` (Zones & Places, Schools, Managers & assignments, Teachers, Teacher
salary). The 005 requests save `zoneId`, `placeId`, `schoolId`, `managerId`, `teacherId` and their
versions from create responses so the next request works in sequence.

## Local environment

`HLS-Local.postman_environment.json` is pre-filled with the **dev-only demo** Admin
(`asha.admin` / `Password123!`, created by `scripts/run-backend.ps1`'s demo seed) and the demo
Teacher phone. Dev and Prod stay blank on purpose. See `docs/running-locally.md`.
