# Quickstart results: Substitute Teachers, Day-Rate Pay and Reconciliation

Run on 2026-11-01 against the local Docker stack built from branch `impl/017b-payout-reconciliation`, with the dev demo data
(Sunita Substitute: three Present days last month, the month locked; Suresh Substitute: a Present day, an Absent day and one day
still to come). Browser steps are `e2e/test_substitute_payouts.py` (Selenium, Chrome headless). Timings from a script are only a
floor for the SC-00x figures.

| Step | What | How | Result |
| --- | --- | --- | --- |
| 1 | A Teacher is a Substitute: badge on the Teachers page, no Placement, Link account or Salary action; designation and Edit stay | Selenium `test_4` | Pass |
| 2 | Day rate by designation under Salary Structures, with history | Selenium `test_5` (Cover Teacher, ₹800.00) | Pass |
| 3 | The list shows every allocated day with who, where and its status; totals over the filter | Selenium `test_1` | Pass |
| 4 | Mark a day Present then Absent again; a day still to come is refused with the reason | Selenium `test_3`, `test_2` | Pass |
| 5 | The pay run pays Sunita by the day: one line, 2.50 days at ₹800.00 = ₹2,000.00, no deduction; the working shows the days | Selenium `test_6` | Pass |
| 6 | Mismatches and the Admin Correction | `SubstitutePayFlowIT.aDayAddedAfterTheMonthWasPaidIsACorrectionThatTheNextRunPays` (API, real database) | Pass |
| 7 | Back-pay: a Substitute missing from the approved primary run is paid by a supplementary run, original month kept; go-live migration | `SubstitutePayFlowIT` supplementary test, `MigrationTest` | Pass |
| 8 | Views per role and scope, summary, one substitute's month, CSV equal to the list | `PayoutApiTest` | Pass |
| 9 | The repair job recreates a missing day once, cancels a day whose allocation ended, restates a drifted status, one run at a time | `ReconcileTest` | Pass |
| 10 | A Zone Manager sees no lock, export or go-live control | Selenium `test_7` | Pass |
| 11 | Phone width (375 px): no horizontal page scroll | Selenium `test_8`; WCAG 2.2 AA, light and dark, list, needs-attention tab and the correction dialog in `SubstitutePayouts.test.tsx`, the day-rate tab and the run-line working | Pass |

## Findings that changed the design

- A person is paid once per person per month (`payroll_paid_month` key), so a day recorded after the month was paid is a Correction, not a
  supplementary line. A supplementary run pays a Substitute who was not in that month's primary run. See research.md R11.
- A Teacher keeps only the current designation, so every day is priced at the rate of the designation held now.

## Not walked in a browser

- Raising a Correction and the go-live move through the page (covered by web tests with a mocked API and by the API tests above).
- Locking and reopening a month through the page against real data (web tests with a mocked API; API tests `DayApiTest`).

## Side effect on the demo database

`test_6` recomputes the draft pay run of last month so that it contains Sunita's line. It is the demo run the seed leaves as a draft.
