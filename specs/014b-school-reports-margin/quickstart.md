# Quickstart: 014b reports

Prerequisites: 014a and 014c merged (framework, REPORTS menu). Dev seeders for 022 (billing), 013b (a pay run), 017a, 026 and 023 give data. JDK 25 for Maven.

1. Run the backend and web app as in `docs/running-locally.md`; sign in as Admin.
2. REPORTS -> **Margin**: pick a month with a pay run. For three Teachers, compare Fee with the School Billing due line, Pay with the payslip net, and check Margin = Fee - Pay. Choose a month with a draft run: the banner says "payroll not approved, figures may change". Choose a month with no run: Pay and Margin say "no data yet".
3. Move a Teacher between Schools mid-month (Transfers) and run billing and payroll for the month: two Margin rows for the Teacher; the two Pay values add up to the payslip net.
4. Remove a payslip line (or bill a Teacher with no payslip): the row says "not matched" with the reason and the School and Zone totals do not change.
5. REPORTS -> **Collections**: compare the `month` section with the School ledger for the same month, and the `ageing` section with School Billing -> Ageing. Not yet due plus the four buckets equals Outstanding on every row.
6. REPORTS -> **School**: for one School compare Teachers placed with the Teachers list, Attendance % with REPORTS -> Attendance (schools section), substitution figures with Substitutions, observations and open issues with Teacher Performance, billed/collected/outstanding with Collections, and the stage with the Marketing pipeline.
7. Export each report: rows equal the screen; Audit -> Change History shows three export entries (`REPORT_EXPORT_MARGIN`, `_COLLECTIONS`, `_SCHOOL`).
8. Sign in as a Zone Manager: the menu has Margin, Collections and School; every report lists only your Schools; Margin shows your Teachers' fee, pay and margin; `GET /api/v1/reports/payroll` returns 403; a Zone of another area returns 403.
9. Sign in as a Teacher: no REPORTS items for these three; direct requests return 403.
10. Tests: `mvn -o test -Dtest=PaySplitTest` then each `schoolreports` class one at a time.
