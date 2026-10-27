# Quickstart: Reports Framework, Attendance and Payroll Reports (014a)

How to see the reports working, and how to add a report.

## Run it

1. Start the stack as usual (`docker compose up`, backend and frontend). No migration is needed.
2. Sign in as the Admin. The menu has a **REPORTS** section with **Attendance Report** and **Payroll Report**.
3. Open Attendance Report. The month opens on the last month that is fully locked, else the current month. Change the month, Zone or School; the Teacher and Manager filters are filled from the rows.
4. Check the banner: **Open** means someone in scope is still unlocked, **Locked** means everyone is. Rows with no working days show "No data" for the %, never 0.
5. Click **Export CSV**. The file has a header block (report, month, filter, month state, time made) and the same rows as the screen. Every export is one row in Audit, Change History (entity `REPORT_EXPORT_ATTENDANCE` or `REPORT_EXPORT_PAYROLL`).
6. Open Payroll Report. With no pay run for the month every figure says "No data" and a note says so; with a draft run the banner says the figures can change. The last table is the twelve months ending with the chosen one.
7. Sign in as a Zone Manager: only Attendance Report shows, with your own Teachers, Schools and your own Manager row. A Zone or School of another area is refused (403). Payroll is refused by direct request too.
8. Sign in as a Teacher: **MY ATTENDANCE, My Report** shows your own six months of attendance and leave and your payslip history. There is no filter, so no way to ask for another Teacher.
9. In Role & Permissions, switch off Manager, Attendance Report, Export: the Export button disappears and a direct request is refused (403).

## Where things are

| What | Where |
| --- | --- |
| Contract (`ReportSource`, `ReportPage`, `Cell`, `ReportScope`) | `backend/.../reporting/api` |
| Registry, scope, CSV, audit, service | `backend/.../reporting/internal/report` |
| Endpoints `GET /api/v1/reports`, `/{key}`, `/{key}/export` | `backend/.../reporting/web/ReportController` |
| Attendance report | `attendance/internal/AttendanceReportSource` (uses the new bulk `AttendanceReadApi.rollupsOf`) |
| Payroll report, My Report | `payroll/run/internal/PayrollReportSource`, `MyReportSource` (use the new `payroll.api.PayrollFigures`) |
| Screens | `frontend/src/features/reports` (one generic page for `/reports/:key`) |

## Add a report

Publish a Spring bean implementing `ReportSource` in the module that owns the figures: give it a `ReportDefinition` (key, title, the `PermissionModule` whose View and Export grants gate it, the filters it offers) and one or more `SectionSource`s. Each section is read on its own, in a read-only transaction and within a time limit (`hls.reports.section-limit-ms`, default 10 s), so a section that throws shows as "could not be read" while the others load. The menu entry, the page, the CSV and the audit row need no framework change (`ReportFrameworkTest` proves this with a fixture report). A new `PermissionModule` also needs its eligibility, seeded defaults and a navigation item.

## Tests

`AttendanceReportTest`, `PayrollReportTest`, `MyReportTest`, `ReportFrameworkTest`, `ReportCsvWriterTest`, `RollupsOfTest`, `PayrollFiguresTest`, `ReportsPerformanceTest` (500 Teachers and 50 Managers: first page under 3 s, export under 30 s, statements do not grow with the volume); the web tests are in `frontend/src/features/reports/__tests__`.
