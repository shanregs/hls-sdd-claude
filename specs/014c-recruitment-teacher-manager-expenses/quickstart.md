# Quickstart: 014c reports

Prerequisite: 014a merged (framework, `REPORTS` menu). Dev seeders for 015, 016, 023, 025, 026, 027 and 035 give data.

1. Run the backend and web app as in `docs/running-locally.md`; sign in as Admin.
2. REPORTS → **Manager productivity**: pick the last locked month. Compare each common column with Dashboard → Manager comparison; they must match. Toggle an unlocked month: the open label shows.
3. REPORTS → **Expenses**: compare spend by area with Expenses → Budgets, pending with Expenses → Pending, cost per School and per Teacher with the cost screens.
4. REPORTS → **Teacher**: status counts equal the Teachers list; open an exited-in-month Teacher; compare issue and observation counts with Performance.
5. REPORTS → **Recruitment**: choose the season; compare each college with Recruitment → Dashboard and demand with Marketing → Dashboard.
6. Export each report: rows equal the screen; Audit shows four export entries.
7. Sign in as a Zone Manager: menu has Recruitment, Teacher, Manager productivity only; Manager productivity has one row; `GET /api/reports/expenses` returns 403.
8. Sign in as a Teacher: no REPORTS items.
