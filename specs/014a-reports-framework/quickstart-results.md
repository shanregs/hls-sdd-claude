
## Browser run (2026-10-29, Selenium, headless Chrome 156, local Docker stack)

`e2e/test_reports.py` (run `python -m pytest test_reports.py -v` in `e2e/`): 12 of 12 pass. Covers the 014a "Run it" steps 2 to 8 and the 014c menu check.

| Step | Result |
| --- | --- |
| 2 Admin menu lists Attendance, Payroll, Recruitment, Teacher, Manager and Expenses reports | Pass |
| 3, 4 each of the six reports loads with its heading, no error banner, and a table or "Nothing to show"; Attendance and Payroll show the "Month state:" banner | Pass |
| 5 Export CSV on Attendance downloads a CSV whose header block names the report | Pass (the audit row was not checked in the browser) |
| 7 Manager: menu has Attendance, Recruitment, Teacher and Manager reports, no Payroll or Expenses; a direct visit to Payroll is refused | Pass |
| 8 Teacher: only My Report, with no Teacher filter | Pass |
| all six report pages at 375 px: no horizontal page scroll | Pass |

Not run in the browser: step 9 (switching an Export grant off), the Season field and per-figure comparisons, the Zone Manager's Manager report row count, and the Attendance "No data" cells. The headings differ from the menu labels (for example "Attendance and LOP" under "Attendance Report").
