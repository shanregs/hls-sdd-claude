# Contracts: new reports and read-only amendments (014b)

All additive. Callers apply their own permission and scope (the owning module does not). Bulk forms use a fixed number of queries.

## New reports (`ReportSource` beans)

| Key | Module key | Filters | Sections | Month state |
| --- | --- | --- | --- | --- |
| `margin` | `REPORT_MARGIN` | ZONE, SCHOOL | `schools`, `zones`, `teachers` | pay run (none / draft / approved) |
| `collections` | `REPORT_COLLECTIONS` | ZONE, SCHOOL | `month`, `ageing` | none, noted "as of <date>" |
| `school` | `REPORT_SCHOOL` | ZONE, SCHOOL | `schools` | attendance lock |

Endpoints are the 014a ones: `GET /api/v1/reports`, `/api/v1/reports/{key}`, `/api/v1/reports/{key}/export`. Audit entity types: `REPORT_EXPORT_MARGIN`, `REPORT_EXPORT_COLLECTIONS`, `REPORT_EXPORT_SCHOOL`.

Permission keys (View, Export): Admin and Director org-wide; Manager for own Schools; Teacher and System not eligible. `REPORT_PAYROLL` is unchanged (Admin, Director).

## Amendments

**A1 (022)** `schoolbilling.api.SchoolReceivables`
- `List<TeacherSchoolBilled> billedPerTeacherAndSchool(Collection<UUID> teacherIds, Collection<UUID> schoolIds, YearMonth from, YearMonth to)`; an empty collection means an empty result, never "any": an org-wide caller passes all School ids, a Zone Manager with no Schools gets nothing; `TeacherSchoolBilled(teacherId, schoolId, month, amount, workedDays, monthDays)`. Same cancelled-due rule as `billedPerTeacher`; one grouped query.
- `Map<UUID, SchoolAgeing> ageing(Collection<UUID> schoolIds, LocalDate asOf)`; `SchoolAgeing(schoolId, outstanding, notYetDue, d0to30, d31to60, d61to90, over90)`, built with the same bucket rule as the Ageing screen (`LedgerQueryService.bucketsOf`).
- Test: an empty School set returns nothing; per Teacher and School the amounts add up to `billedPerTeacher`; ageing equals the 022 Ageing screen for the same School and date; buckets plus not yet due equal outstanding.

**A2 (022)** `schoolbilling.api.SchoolContracts`
- `Map<UUID, ContractView> contractsOfAll(Collection<UUID> schoolIds, LocalDate date)`; a School with no contract in effect is absent.
- Test: equals `contractOf` for each School; fixed query count.

**A3 (017a)** `substitution.api.SubstitutionCover` (new)
- `Map<UUID, SchoolCover> coverBySchool(Collection<UUID> schoolIds, LocalDate from, LocalDate to)`; `SchoolCover(schoolId, bySubstitute, byManager, open)` as day values (a half day counts 0.5); cancelled requirements excluded.
- Test: equals the Substitutions screen counts for the same School and range.

**A4 (026)** `performance.api.PerformanceSummary`
- `Map<UUID, Counts> bySchool(LocalDate from, LocalDate to)`: same `Counts` and rules as `byManager`, keyed by School id.
- Test: sum over a Manager's Schools equals `byManager`.

**A5 (023)** `recruitment.api.SchoolStages` (new)
- `Map<UUID, String> stageOf(Collection<UUID> schoolIds)`: the effective stage of the prospect linked to each School; absent when the School has no prospect record. Uses the same resolver as the pipeline screen.
- Test: equals the pipeline stage shown for the linked prospect.

**A6 (008)** `attendance.api.SchoolAttendance` (new)
- `Map<UUID, SchoolRollup> rollupsBySchool(Collection<UUID> schoolIds, YearMonth month)`; `SchoolRollup(schoolId, teachers, working, present, absent, leave, lossOfPay)`. `AttendanceReportSource`'s `schools` section is changed to read the same grouping, so the two cannot differ.
- Test: equals the Attendance report `schools` row for the same School and month; the existing Attendance report tests stay green.

**A7 (005)** `teacher.api.TeacherDirectory`
- `Map<UUID, Integer> placedCountBySchool(Collection<UUID> schoolIds, LocalDate date)`: Teachers whose ACTIVE placement covers the date.
- Test: equals the Teachers list filtered by School for the same date.

**A8 (002 / 003)** `PermissionModule` +`REPORT_MARGIN`, `REPORT_COLLECTIONS`, `REPORT_SCHOOL`; `PermissionEligibility` (Admin, Director, Manager); defaults; `NavigationCatalog` +3 REPORTS items; `AuditVisibility` +3 entries; update the access-model menu tests.

No change to `PayrollFigures` (014a already published it) and no change to 027.
