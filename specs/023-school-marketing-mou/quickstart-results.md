# Quickstart results: School Marketing and the MoU Pipeline (spec 023)

Date: 2026-10-06. Branch `feature/023-school-marketing-mou` (on top of spec 016's branch).

**How these were checked.** Every scenario of [quickstart.md](./quickstart.md) is exercised end to end by the backend
integration tests below (real HTTP against PostgreSQL via Testcontainers, as each role) and by the frontend component
tests. `DevSeedTest` starts the whole application with the demo seeders. **A manual walk through the screens in a browser
has not been done yet**, nor the keyboard-only, screen-reader and phone-width passes; those stay open (below).

| Scenario | Result | Evidence |
| --- | --- | --- |
| Prospects, duplicate rule, owner changes, Zone scope | Pass | `ProspectApiTest`, `MarketingAuditTest`; `marketing.test.tsx` |
| Visits: plan, complete with an outcome, reschedule, cancel, missed, follow-up overdue, the follow-up event | Pass | `ActivityApiTest` |
| Attachments (type, size, ten files, download as attachment with nosniff, visibility, removal with a reason) | Pass | `AttachmentTest`, `FileStoreTest` |
| Calendar with the person's drives | Pass | `CalendarDrivesTest`; `marketing.test.tsx` |
| Pipeline, hold, lost, reopen, Final Stage needs a proposal, the review (Director, Zone Manager of the Zone, not Admin) | Pass | `PipelineApiTest`; `marketing.test.tsx` |
| Proposals: modes, validation, immutability by service and by SQL | Pass | `ProposalApiTest` |
| Win: School created from the prospect, link instead of duplicate, hand-off, contacts | Pass | `WinServiceTest`; `WinDialog` tests |
| MoU and Active derived from spec 012; the signed MoU beside the proposal | Pass | `ContractStatusTest` |
| Overdue flag and one notification each | Pass | `OverdueJobTest`, `MarketingNotificationTest` |
| Settings (1 to 90, audited, Admin and Director only) | Pass | `SettingsApiTest` |
| Dashboard: counts, win rate, demand, supply "not available" | Pass | `DashboardTest` |
| Amendments A6 (occupancy) and A7 (MoU form pre-filled) | Pass | `OccupancyTest`; `SchoolContractPage.test.tsx` |
| Module boundaries | Pass | `MarketingModuleRulesTest`, `ApplicationModulesTest` |

Accessibility: every new page and dialog is in the axe harness (`a11y.test.tsx`) in light and dark mode with no critical
violations.

## Still to do by hand

- Walk the scenarios in a browser on the local app (database on port 5433, demo data) as Asha, Divya and Manoj.
- Keyboard-only pass over the visit, proposal and win dialogs; one screen-reader pass; phone-width check of the board and
  tables.
- Counting the queries behind the dashboard (task T045 asks for a fixed query count for 50 won prospects); the dashboard
  makes one batch call to spec 012 for every won School, but no test counts queries yet.
