# Build plan for the simplified HLS system

Written 2026-11-07. A recommendation for the owner, not a decision. It builds on `docs/simplified-requirements.md` (read from `origin/main`; the owner's uncommitted local edits to that file in the main checkout are not reflected here) and on `docs/simplified/schema.sql` (51 tables, validated on PostgreSQL 16, with `schema-smoke-test.sql` proving the key constraints). Scale: 200 Teachers, 50 Schools, 10 Zones, about 250 users who sign in, at most 100 at once.

## 1. Headline recommendation

**One Spring Boot application (a single deployable jar) with PostgreSQL, a React web app served by the same jar and installable as a progressive web app (PWA), on one small AWS server, with a nightly dump copied to S3.**

Reasons, in order of weight:

1. The hard part of this system is money rules (pay, loss of pay, billing pro-rata, substitute day rate), and those rules already exist in Java with about 527 test files behind them. Staying on Java and Spring lets us port rules and tests almost line by line instead of re-deriving them in a new language.
2. A modular monolith without events, caches or a second store is exactly what the requirements document asks for; Spring Boot does this with the least ceremony of any option the current owner/team already knows.
3. The React front end exists. A PWA means one set of screens for desktop and phone and no second mobile codebase (requirements section 2, decision 018).

## 2. Stack options and trade-offs

| Option | What it is | For | Against | Verdict |
| --- | --- | --- | --- | --- |
| **A. Spring Boot monolith + React PWA in the same jar** | Java 21 or 25, Spring Boot, plain SQL via Spring JDBC (or jOOQ), Flyway, React built once and copied into `static/` | Reuses payroll/billing logic and tests, reuses React components and design, one artifact to deploy, strong typing for money | Two languages (Java, TypeScript); React build step; Java needs 1 GB heap headroom | **Recommended** |
| B. Spring Boot + server-rendered pages (Thymeleaf + htmx) | No SPA, HTML from the server | Single language, no JS build, very small, easy to hand over | Throws away existing React screens; phone UX (camera upload, geolocation) needs a little JS anyway; most of the front-end rewrite effort lands in the MVP | Good if the team has no front-end developer |
| C. Django (or Rails/Laravel) full-stack | Python, built-in admin, ORM, forms | Fastest CRUD screens; Django admin covers many master-data screens for free; cheaper developers in some markets | Every money rule and test must be re-implemented and re-verified; no reuse of the 527 test files; the risk is in payroll, where bugs cost real money | Only if no Java skill remains in the team |
| D. Keep the current 22-module system, delete features | Remove code rather than rewrite | No migration; nothing to relearn | Does not remove the 146-table model, the event bus, the 79-FK gaps or the module-boundary rules that make change slow; deletion across 22 modules is itself a large job | Compare cost before deciding; see risks |
| E. Low-code / spreadsheet+forms | Airtable, Google Sheets, Retool | Fast start | Payroll audit and append-only guarantees are weak; 250 users with row-level scope per Zone is awkward | Not recommended for payroll |

**Structure (option A).** One Maven module, packages by area, direct method calls between them in one transaction, no published-interface ceremony:

```
com.hls
  platform/   auth, users, audit, settings, notification, files, scope
  masterdata/ zone, school, employee, designation, holiday, working days
  contract/   contract, positions, placement
  attendance/ teacher marks, manager check-in, leave, month lock
  substitution/
  payroll/    PayRules (pure), run, lines, adjustments, payslip, CSV
  billing/    invoice, payment, adjustment, ageing
  expense/
  report/     SQL queries + CSV
  (phase 2)   recruitment/ training/ marketing/ task/ performance/ transfer/ content/
```

Rules for the code: one `@Transactional` service method per user action; pure calculators (no clock, no database) for payroll and billing so they can be unit-tested with plain inputs; SQL written out in repository classes (no ORM lazy loading, so what a screen runs is visible); one `Scope` helper that every list query goes through. One lightweight architecture test (packages do not reach into each other's repositories) is enough; the full ArchUnit module-boundary suite is not carried over.

**Libraries.** Spring Security with a custom session-token filter (no OAuth server), Flyway for `schema.sql` split into migrations, Spring JDBC `JdbcClient` or jOOQ, Testcontainers for PostgreSQL in tests, OpenPDF or browser print for payslips (browser print is the plan; no PDF library), Apache Commons CSV, one SMS gateway client behind a two-method interface (send code, check balance).

## 3. Responsive web / PWA instead of a native app

- One React app, mobile-first layouts for Teacher and Manager screens (attendance, leave, check-in, expense photo upload, tasks), denser desktop layouts for Admin and Director.
- PWA features that are worth building: install to home screen, HTTPS, web manifest, a small service worker that caches the app shell only. No offline data and no background sync (requirements decision).
- Geolocation at Manager check-in and check-out uses the browser API over HTTPS. Photo capture uses `<input type="file" accept="image/*" capture>`.
- Honest limits: no push notifications on every phone, no background tracking, some in-app browsers (for example inside messaging apps) block geolocation. A Play Store wrapper (a Trusted Web Activity around the same PWA) is a one-week add-on if the owner needs a store listing; it is not a second codebase.
- Decision gate (open question 1 and 2 of the requirements): before Phase 1, run a one-day field check with 5 Teachers and 3 Managers on their own phones in their Schools (signal, geolocation prompt, camera). If it fails, the fallback is the existing React Native app talking to the same REST API, costed separately.

## 4. Authentication and security

- Sign-in with phone or username plus password, or SMS one-time code. Passwords hashed with Argon2id or bcrypt; minimum 10 characters.
- One opaque session token (about 12 h, renewed on activity) in an HTTP-only, Secure, SameSite=Lax cookie; stored hashed in `login_session`. Admin "sign out user" revokes rows. Lockout 5 failures / 30 minutes, 3 code requests per phone per minute, from `setting`.
- Authorisation: five roles in `app_user.roles`; a `Capabilities` class holds the role x action table from requirements section 3; a `Scope` helper turns the caller into the Zones, Schools and employees they may see. Every controller method checks a capability; every list query joins the scope. A test per role walks the table.
- Salary and payroll visible to Admin and Director only (Teacher sees own payslip). Zone Manager sees margin per School but never payroll.
- Transport and platform: TLS (Caddy or an AWS load balancer certificate), security headers, request size limits, CSRF token on state-changing requests, uploads checked for type and size and stored outside the web root with random names, rate limit on sign-in at the web server. See `docs/aws-deployment-and-ddos-protection.md` for the existing AWS notes; a managed WAF is optional at this size.
- Last-Admin guard and "cannot deactivate yourself" as one transactional check.
- Backups are encrypted (S3 server-side encryption), the database is not exposed to the internet.

## 5. Deployment and backup

**Target: one small AWS server.**

| Item | Choice | Approximate size |
| --- | --- | --- |
| Compute | One EC2 (or Lightsail) instance, 2 vCPU and 4 GB RAM (for example t4g.medium, ARM, Linux), 40 GB disk | Peak load is a few requests per second |
| Runtime | systemd service running the jar; Caddy in front for HTTPS and static caching | No containers needed; Docker is optional |
| Database | PostgreSQL 16 on the same instance (simplest), or RDS `db.t4g.micro` Single-AZ if the owner prefers a managed database | Data under 5 GB for a decade |
| Files | A folder on the data disk (`/var/hls/files`), path stored in `stored_file` | Bills, MoU PDFs, content; videos beyond 100 MB live on a shared drive as a link |
| Backup | Nightly `pg_dump` plus `rsync` of the files folder to S3 (versioned bucket, 30 daily and 12 monthly copies, lifecycle rule); weekly EBS snapshot | RPO 24 h |
| Restore drill | Restore the latest dump into a scratch database and run the smoke test every month | Calendar reminder; this is the check people skip |
| Monitoring | `/health` URL watched by a free uptime check that e-mails; server log; disk-space alarm | Nothing else |
| Environments | Local developer machine, one staging copy (can be the same instance on another port or a stop-when-idle instance), production | |
| Deploy | CI builds the jar and runs tests; a script copies it, runs Flyway, restarts the service; keep the last 3 jars for rollback | A deploy takes about two minutes |
| Cost | About 25 to 40 US dollars per month plus SMS | Indicative; check current AWS prices |

Zero-downtime deployments and auto-scaling are not needed: a deploy at night is fine for 250 users. Schema changes are forward-only migrations; each migration is run against a copy of production data in staging first.

## 6. MVP slice order

The MVP is requirements R1 to R29 (33 tables). Order is chosen so that every slice ends with something usable and so that the riskiest thing, payroll, is proven early with real data.

| Slice | Content | Tables | Done when |
| --- | --- | --- | --- |
| 0 Foundation | Repo, CI, Flyway with `schema.sql`, Testcontainers, sign-in, session, roles and capability table, audit helper, settings page, file store, app shell with role menus and PWA manifest, health URL | `app_user`, `otp_code`, `login_session`, `setting`, `audit_log`, `stored_file`, `notification` | Admin signs in on a phone and desktop, creates a user, change appears in audit |
| 1 Master data | Zones, Schools and contacts, designations, employees (all kinds), salary history, holidays, weekly offs, working-days screen (year layout), scope helper | `zone`, `school`, `school_contact`, `designation`, `employee`, `employee_salary`, `holiday`, `school_month_days` | Manager sees only own Zone; import script can load real people |
| 2 Contracts and placement | MoU, positions, approval, signed PDF, placement with exclusion constraints, MoU replacement re-map, no-contract list | `school_contract`, `contract_position`, `placement` | A Teacher is placed; second placement overlap is refused |
| 3 Attendance and leave | Teacher self-mark (today + 3 days), Manager corrections, month grid and CSV, month lock and reopen, leave request/decision, same-day leave, Manager check-in/out and Manager leave | `attendance_mark`, `leave_request`, `manager_checkin`, `month_lock` | A month of real attendance can be entered and locked |
| 4 Substitution | Requirement on absence, Zone Substitute list, assign or Manager cover, School informed, day mark Present/Absent | `substitution` | Absence produces a requirement; day is marked |
| 5 Payroll | `PayRules` port, run computation, flags, recompute while Draft, adjustments, approve and freeze, payslip print, CSV, payment entry | `payroll_run`, `payroll_line`, `payroll_adjustment` | Run for one past month reproduces the August spreadsheet to the rupee for a sample, then the whole month |
| 6 Billing | Monthly invoice per School with pro-rata lines, immutable, payments and allocation, credit adjustments, ageing, not-billed list | `invoice`, `invoice_line`, `payment`, `payment_allocation`, `invoice_adjustment` | Invoices for a past month equal the ones actually sent |
| 7 Expenses and incentive | Claim with bills, approval, payroll reimbursement; incentive adjustment on MoU approval | `expense_claim`, `expense_bill` | Claim reimbursed through a payroll run |
| 8 Dashboard, reports, notifications | About 10 dashboard tiles, five reports with CSV, bell for about 10 events | none new | Director uses it for a week |
| 9 Import, UAT, go-live | Section 9 below | | Cut-over complete |

Phase 2, each a separate release, in the order the owner values them: (a) recruitment and induction (R30, R38, R39); (b) prospects and visits, then tasks (R31, R32); (c) transfers and observations (R33, R34); (d) training sessions (R35); (e) content (R36); (f) expense budgets (R37) only if confirmed.

## 7. What to port from the current system

Port the rules and the test cases, not the code. The current code is shaped around module interfaces and an event bus that the new build does not have.

### 7.1 Payroll (highest value)

Sources: `backend/src/main/java/com/hls/payroll/internal/PayRulesImpl.java`, `LossOfPayCalculator`, `PayableDaysCalculator`, `UnpaidDayClassifier`, `run/internal/LineCalculator.java`; tests `LossOfPayCalculatorTest`, `PayableDaysCalculatorTest`, `UnpaidDayClassifierTest`, `PayRulesBoundaryTest`, `PayRulesEarnedPayTest`, `PayRulesCalendarTest`, `TeacherLeaveDeductionTest`, `PayRunComputationTest`, `PayRunRecomputeTest`, `PayRunFreezingTest`, `PayRunImmutabilityTriggerTest`, `PayRunTrainingDayTest`, `PayAdjustmentTest`, `PayslipTest`.

How:
1. Write the rules as one short specification first (a table of inputs and expected outputs), extracted from the existing unit tests. Rules to carry: day value = salary in effect / working days of that School-month; LOP = unpaid days x day value; pro-rata for joining and exit; Teacher paid only for days placed in a School (D13); training unpaid (D2); every Teacher leave day is a deduction whatever its type (spec 040); a person who cannot be computed is flagged with a reason and never blocks the run; rounding rule (check the existing code for the exact rounding mode and where it is applied).
2. Port the three pure calculators first as plain Java classes with the same signatures where sensible, and copy the pure unit tests (they have no Spring context). Keep them passing before building anything around them.
3. Build the run service on top; port the run tests as integration tests against Testcontainers PostgreSQL. The freezing test becomes a test of `payroll_line_freeze` in `schema.sql`.
4. Golden-file check: run both systems on the same month of data (exported from the current database) and compare per person net pay. Differences must be explained (usually a rule deliberately dropped), not tolerated.

Deliberate differences to document in the specification: no supplementary run (late people go to the next run as adjustments); no stored payslip PDF; payment status lives on the line; salary lookup uses `employee_salary` only (no designation fallback).

### 7.2 School billing

Sources: `schoolbilling/internal` (due raising, assignment service, collection service), tests `RaiseDueApiTest`, `OccupancyTest`, `RemapTest`, `PositionAssignmentTest`, `SchoolReceivablesTest`, `LedgerApiTest`, `TeacherFirstAssignedTest`. Rules to carry: one invoice per School per month, immutable (D6); fee from the position (or uniform fee); pro-rata only for joining, transfer and exit, absences never reduce it; due date = raised date + setting; payment allocation never exceeds the payment; ageing buckets 0-30, 31-60, 61-90, 90+. Port the pure pro-rata and ageing arithmetic tests as unit tests; port the API tests as service tests; replace the old status column tests with tests of the `invoice_balance` view.

### 7.3 Substitute day-rate

Sources: `substitution/internal`, `substitution/payout` tests (`PayoutApiTest`, `PayoutAuditTest`), `DayRateApiTest` in payroll, `SubstitutionCoverTest`, `ManagerCoverApiTest`. Rules: one requirement per absent Teacher per date; credit goes to the substitute; pay = Present days x the rate snapshotted when marked; Manager cover pays nothing; a day marked after payroll approval is paid in the next run (`paid_in_run_id` is null until then); a Substitute cannot be booked twice on one day (partial unique index). Port the test scenarios as service tests with a table of expected payroll lines.

### 7.4 Other behaviour worth a regression test

Attendance (one mark per Teacher per date, 3-day window, month lock), leave (approval writes marks; same-day auto-approval), placement overlap and MoU re-map with no gap, last-Admin guard, lockout, scope per role. Everything else (tiles, reports, audit views) is rebuilt from the new requirements.

### 7.5 What not to port

The event bus and outbox, the snapshot cache, `ReportSource` and published-figure parity, the module-boundary tests, the permission matrix tables, API access trail, rotating refresh tokens, the React Native app.

## 8. Test strategy

| Layer | What | Tooling | Volume |
| --- | --- | --- | --- |
| Unit | Pure calculators: pay, LOP, payable days, pro-rata billing, ageing, working-day default | JUnit 5, no Spring | Many, fast, ported from section 7 |
| Database | `schema.sql` constraints (the smoke test grows into JUnit tests): exclusion constraints, append-only triggers, check constraints, `invoice_balance` | Testcontainers PostgreSQL 16 | One test per constraint that matters for money |
| Service/API | One test per user action and role: allowed, refused by capability, refused by scope, audit row written, notification row written | Spring Boot test, MockMvc, Testcontainers | About 150 to 250 |
| Golden month | Whole payroll and billing for a past month compared with the spreadsheet / old system | A script and a diff report | Before go-live, then each month for 3 months in parallel run |
| End to end | A few browser walks (phone-size viewport): Teacher marks attendance and applies for leave; Manager approves; Admin runs payroll; Director approves; Admin raises invoice; payment allocated | Selenium (existing `e2e/` suite is Python Selenium and can be reused for the walkthrough structure) | 6 to 10 scenarios |
| Security | Capability-by-role matrix test, scope test with two Zones, upload type/size, session expiry, lockout | JUnit | One generated test per row of the role table |
| Not built | Load tests, 10k-row pagination, cache parity, module-boundary suite | | Scale does not need them |

Rule: no money-affecting code merges without a test that states the expected rupee amount. CI runs everything except the browser walks on each push; the browser walks run nightly and before a release.

## 9. Migration and import of existing data

The open question is what the owner has: the running system (146 tables), or the August spreadsheet (`docs/Aug-26-HLS-School-attendace-salary-expense.xlsx`), or both. Plan covers both.

1. **Decide what to bring.** Recommended: master data and live state only. Zones, Schools and contacts, designations, employees (Teachers, Managers, staff, Substitutes) with joining/exit dates, current and historical salary, current MoUs and positions, current placements, holidays and working days for the current year, and the last 12 months of attendance, approved payroll lines (for history and comparison), invoices, payments and allocations, open expense claims. Leave older history in a read-only dump of the old database kept for 3 years (cheap, and answers any later "what happened" question).
2. **Export.** SQL queries against the old PostgreSQL write one CSV per target table, with the old ids kept in a staging column (`legacy_id`) for traceability. Mapping rules come from `er-diagram.md` section 3 (for example: `teacher` and `manager` become `employee`; the three salary stores merge into `employee_salary` taking the latest entry per effective date; `school_manager_assignment` current row becomes `school.manager_id`; `contract_assignment` becomes `placement` with dates; `due_*` become `invoice*`).
3. **Load.** A one-off import program (or `psql \copy` into staging tables, then `INSERT ... SELECT` into the real tables) in dependency order (the same order as the slices). It runs inside one transaction against an empty database and stops at the first constraint violation.
4. **Reconcile.** Report counts and totals per table old vs new; per-employee salary in effect; per-School active contract and placed Teachers; sum of invoice balances; payroll run totals per month. Differences are listed and signed off by the Admin.
5. **Data clean-up that the new constraints will force into the open.** Overlapping placements, a Teacher with two current Schools, a second live contract for a School, a Substitute without a Zone, duplicate phone numbers, bad pincodes, missing employee codes. Each is a decision for the Admin, not for the import script. Budget time for this (see estimate).
6. **Passwords.** Do not import password hashes. Send each user a first-sign-in SMS code; forced password change.
7. **Parallel run.** Run payroll in the new system for one month alongside the old one and compare line by line; then switch. Billing the same.
8. **Cut-over.** Freeze the old system at month end, import the final state, open the new system. Keep the old stack read-only for 3 months.

If only the spreadsheet exists, the import is smaller but the clean-up is larger: the spreadsheet becomes the source for employees, salaries, Schools and one month of attendance, and the first payroll run in the new system is the proof.

## 10. Effort estimate

Assumptions (change them and the numbers change):
- Two developers, both comfortable with Java/Spring and React, plus part-time use of someone who knows the business rules (the owner or Admin, about half a day a week) and a tester for the last two weeks of each release.
- Person-weeks of 5 working days of focused development including writing tests and fixing review comments; calendar time is longer.
- Reuse of the existing React components and the existing test cases is assumed. If the team starts from nothing, add about 25 percent.
- Requirements stay as in `docs/simplified-requirements.md`; open questions 1 to 12 are answered before the slice that depends on them.
- No GST, no offline mode, no native app. If any of these return, add the amounts in section 10.3.
- Accuracy: plus or minus 30 percent.

### 10.1 MVP

| Phase / slice | Person-weeks | Notes |
| --- | --- | --- |
| 0 Foundation (auth, roles, audit, settings, files, shell, CI, deploy pipeline) | 4 | Includes SMS gateway and PWA shell |
| 1 Master data and working days | 3 | Straightforward CRUD, scope helper is the careful part |
| 2 Contracts and placement | 3 | Exclusion constraints, re-map on new MoU |
| 3 Attendance, leave, Manager check-in, month lock | 5 | Largest set of screens; phone-first |
| 4 Substitution | 1.5 | One table, simple flow |
| 5 Payroll (port, run, payslip, CSV, payment) | 5 | Includes golden-month comparison |
| 6 Billing and collections | 3.5 | Pro-rata and allocation |
| 7 Expenses and incentive | 2 | |
| 8 Dashboard, five reports, notifications | 3 | |
| 9 Data import, UAT, parallel run, go-live | 4 | Clean-up of old data dominates |
| **MVP total** | **34** | About 17 calendar weeks for two developers; allow 5 months with review and holidays |

### 10.2 Phase 2

| Release | Person-weeks |
| --- | --- |
| Recruitment, induction, offer printout, CSV import | 4 |
| Prospects, visits, Final Stage approval, MoU pre-fill | 3 |
| Tasks with evidence | 2 |
| Transfers and replacements | 2 |
| Observations and follow-up | 2 |
| Training sessions | 1 |
| Content with versions | 1.5 |
| Expense budgets | 0.5 |
| **Phase 2 total** | **16** |

Total about 50 person-weeks for everything, 34 for the MVP. For comparison, only the MVP is needed to run payroll and billing.

### 10.3 Add-ons if the answers go the other way

| Item | Person-weeks |
| --- | --- |
| Play Store wrapper (Trusted Web Activity) | 1 |
| Offline attendance marking with later sync (attendance only) | 4 to 6 |
| Native app (React Native) reusing the current one against the new API | 8 to 12 |
| GST tax lines and invoice format | 3 |
| Per-term or annual billing | 3 |
| Four-eyes approval on every payroll line | 1 |

## 11. Risks

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Rule drift: a rewrite relearns subtle rules (D6, D13, leave deduction, pro-rata) | Wrong pay or invoices | Rule specification from tests (7.1); golden-month comparison; one month parallel run |
| PWA cannot do what field staff need (geolocation blocked, poor signal) | Rework toward a native app | Field check before Phase 1 (section 3); fallback costed in 10.3 |
| Working-day figures wrong or unlocked attendance at run time | Wrong pay | Run refuses a month not locked; dashboard tile "working days missing"; Admin checklist |
| Collapsing history into `audit_log` makes some questions hard | Cannot answer "what was true on date X" | Dated rows for placement and salary (already in the schema); audit only for who-did-what |
| Old data does not satisfy the new constraints | Import stalls | Clean-up budget in slice 9; run the import against a copy early (in Phase 0, with fake loads of 200 Teachers) |
| SMS gateway outage or cost; Indian DLT template registration lead time | Users cannot sign in | Start DLT registration in Phase 0; Admin can set a temporary password; password sign-in always works |
| One server is a single point of failure | Outage until restore | Nightly dump to S3; restore drill monthly; a documented rebuild takes under half a day; accept this at 250 users |
| Admin has all powers including audit (System role dropped) | Separation of duties lost | Director can view audit; payroll needs Director approval; revisit if the owner wants four-eyes |
| Scope creep from the dropped features returning one by one | Schedule | Each dropped feature has a stated fallback in the requirements; reopen only through the add-on table with an estimate |
| Key-person dependency on the one person who knows the business rules | Slow answers | Keep the rule specification and test list in the repository; owner sign-off per slice |
| Cost of the rewrite versus deleting code from the existing system (option D) | Possible waste | Spend one week on an honest estimate of option D before committing; the 146-table model and event bus are the main costs of keeping it |

## 12. Open questions for the owner

These add to the twelve in `docs/simplified-requirements.md` section 7.

1. Rewrite or reduce? Is a fresh build (about 34 person-weeks for the MVP) preferred over trimming the current system? Should one week be spent costing option D first?
2. Who builds it, and do they know Java, or should option B or C be considered? This changes the stack more than any other answer.
3. Data to bring: only the running system, only the August spreadsheet, or both? How much attendance and payroll history must be searchable in the new system rather than in an archived dump?
4. Hosting: is a single server on the owner's AWS account acceptable, or must it be a managed database from day one? Any rule about data residency (India region)?
5. SMS: which gateway, and is DLT registration already done?
6. Is a Play Store listing required (adds one week for a wrapper)?
7. Who can sign off a payroll golden-month comparison, and which month is the reference?
8. Rounding: payroll rounds to rupees or paise, and at which step (per day value or per line)? This must be fixed before slice 5; the current system's rule will be copied unless told otherwise.
9. Availability window: can the system be down for deployments at night and for the restore drill, and who is on call when it is not working on the 1st of the month (payroll day)?
10. Retention: three years of audit and 10 years of financial rows are assumed. Is there a legal retention rule that is longer?
