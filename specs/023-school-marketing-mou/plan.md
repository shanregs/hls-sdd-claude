# Implementation Plan: School Marketing and MoU Pipeline

**Branch**: `023-school-marketing-mou` | **Date**: 2026-10-05 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/023-school-marketing-mou/spec.md`

## Summary

Adds the sales side of a School's life, up to the signature, as the `marketing` sub-package of the `recruitment`
module (Constitution Principle VII names `recruitment` "with a `marketing` sub-package"), plus one small shared module
for files:

- **Prospects** (name, board, address, Zone, contact, expected Teachers, owner), duplicate refusal, owner history.
- **Marketing activity** (visit, call, proposal meeting, follow-up) with status planned, completed, missed (derived),
  rescheduled (history) or cancelled; an outcome is required to complete; attendees; a month calendar and list.
- **Attachments on visits**, through a new shared `files` module (store, open, remove; never changed) that spec 015
  (bills), 031 (MoU documents) and 032 (check-in photos) reuse.
- **Pipeline**: Prospect, Contacted, Visit, Follow-up, Interested, Negotiation, Final Stage, plus On Hold and Lost,
  with stage history. **Final Stage is reviewed** by a Director or the Zone Manager of the prospect's Zone (decision
  D5); an approved prospect is **won**. The stages **MoU** and **Active** are not stored: they are derived from spec
  012 (a live contract; a Teacher placed), so nothing here can disagree with the contract.
- **Proposal revisions** (Teacher count, start month, same salary for all or one per position), kept, never edited.
- **Win and hand-off**: winning creates the School in spec 005 (Admin or Director, through a new `school.api`
  interface) and opens spec 012's MoU form pre-filled; the contract itself stays only in 012.
- **Overdue MoU flag** with a setting (default 14 days) and one in-app notification (spec 010) per prospect.
- **Follow-up tasks (spec 025)** by event: a scheduled follow-up publishes `FollowUpScheduled`; 025 will create the
  task; until then only the overdue flag shows.
- **Dashboard**: visits, prospects by stage, win rate, Schools won per Zone and owner, and demand (vacant positions of
  won Schools) against supply (recruits ready to deploy).
- **Planned activities (contract C1)**: visits are exposed through `MarketingActivities`; **incentive hook**: the
  owner of a School's prospect is exposed through `ProspectOwners` for spec 030.

Small changes elsewhere (amendments): `school.api.SchoolRegistry` (create a School), `organization.api`
`ManagerQueries.managersOfZone`, spec 012's `SchoolContracts.occupancyOf` (A6) and its MoU form accepting initial
values (A7), the notification type and listener for the overdue event.

## Technical Context

**Language/Version**: Java 25 (Spring Boot 4.1.1-based); TypeScript 5.7 with React 19. Unchanged.

**Primary Dependencies**: Spring Web (multipart for uploads), Spring Data JPA, Spring Security, Spring Modulith, Flyway,
ArchUnit; `@EnableScheduling` already on. No new library; file bytes go to a configured directory on the server.

**Storage**: PostgreSQL via `V23__create_marketing_tables.sql` (see [data-model.md](./data-model.md)); the same
migration extends the `notification.type` check. File bytes live under `hls.files.directory` (a Docker volume on the
single VM); metadata and a SHA-256 checksum live in `stored_file`. If another migration takes V23 first (spec 016 takes
V22), this one is renumbered at merge.

**Testing**: JUnit 5, Spring Boot Test, Testcontainers (`IntegrationTestBase`). Rule unit tests first (stage
transitions, duplicate prospect, proposal immutability, completing a visit needs an outcome, Final Stage approval
rule, derived MoU and Active stage, overdue threshold). Per-role and per-Zone tests on every endpoint (Zone Manager A
against Zone Manager B). Upload tests (type, size, checksum, download as attachment, removal by Admin or Director
only). Event tests (`FollowUpScheduled`, the overdue notification once). Module rules (`MarketingModuleRulesTest`).
Frontend: Vitest + Testing Library with a fixture per role, axe in both themes.

**Target Platform**: Browser; single Spring Boot deployable.

**Project Type**: Web application (frontend + backend).

**Performance Goals**: prospect list and board for 1,000 prospects in under 1 s (indexed filters, no per-row queries);
dashboard in under 1 s; a 10 MB upload in under 5 s on the VM.

**Constraints**: scope by Zone on every list, search, detail, export and file download (Principle III); a proposal
revision and an attachment never change; the contract data is never copied here; files are served only to callers who
may see the visit, always as attachments with a fixed content type (no inline HTML); the module works with 012 or 016
absent (FR-016): supply shows "not available" when no `SupplySource` is provided.

**Scale/Scope**: a few hundred prospects a year, a few thousand activities; about 35 endpoints; 8 screens.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle I (Auditable)** - PASS: every prospect, activity, stage change, proposal revision, win, loss,
  reassignment, attachment (add and removal) and hand-off is audited through `ChangeRecorder`.
- **Principle II (Roles, configurable permissions)** - PASS with two additions to the spec's table: a module `MARKETING_SETTINGS` guards the settings screen (so it stays configurable), and module `MARKETING`
  also has an `APPROVE` action (Final Stage review, decision D5), seeded to Director and Zone Manager and not to Admin.
  Runtime-editable; Teacher and System ineligible. The spec's Role & Permission section is updated to say so.
- **Principle III (Data scope)** - PASS: a Zone Manager reads and writes only their Zones' prospects, activities,
  proposals and files; Admin and Director are organization-wide; tests per boundary.
- **Principle IV (Role-Based Experience)** - **needs the same amendment as 016**: a MARKETING navigation section
  (added to the constitution with 016's RECRUITMENT section, MINOR 2.4.0, or as 2.5.0 if 016 has merged). All other UI
  rules are met.
- **Principle VII (Modular Monolith)** - PASS with one new module: `files` (shared storage) is added to the module list
  in the same amendment. New public interfaces: `school.api.SchoolRegistry`, `ManagerQueries.managersOfZone`,
  `schoolbilling.api.SchoolContracts.occupancyOf`, `recruitment.api` (`MarketingActivities`, `ProspectOwners`,
  `SupplySource`, events), `files.api.FileStore`. Nothing depends on `*.internal`; ArchUnit and Modulith verify.
- **Principle IX (Reliability, Testability)** - PASS, needs the tests above.
- **Principle X (Security)** - PASS with attention: uploads are checked for type (extension and content sniffing), size
  (10 MB) and checksum; stored under generated names, never the client's path; served with
  `Content-Disposition: attachment` and `X-Content-Type-Options: nosniff`; contact details of prospects are plain
  business data but never logged in plain text.
- **Principles V, VI, VIII, XI** - not applicable (XI's marketing tracked to outcome is what this delivers).

One structural addition (the `files` module) is justified: four specs need file storage and building it once avoids
four copies. Complexity Tracking is not needed.

## Project Structure

### Documentation (this feature)

```text
specs/023-school-marketing-mou/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── marketing-api.md
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/src/main/java/com/hls/
├── identity/
│   ├── permissions/{PermissionModule,PermissionEligibility,PermissionMatrixService}.java   # + MARKETING(VIEW, CREATE, EDIT, APPROVE), MARKETING_SETTINGS(VIEW, EDIT)
│   └── accessmodel/{NavigationCatalog,AccessModelService}.java        # + MARKETING section, Zone scope
├── files/
│   ├── api/FileStore.java                  # store, open, remove; FileRef(id, name, contentType, size, addedBy, addedAt)
│   └── internal/{StoredFile,StoredFileRepository,FileStoreImpl,FileTypePolicy}.java
├── school/
│   ├── api/SchoolRegistry.java             # new: create a School (actor, place, profile)
│   └── internal/SchoolRegistryImpl.java    # over SchoolService.create
├── organization/
│   ├── api/ManagerQueries.java             # + managersOfZone(zoneId)
│   └── internal/                           # implementation
├── schoolbilling/
│   ├── api/SchoolContracts.java            # A6: + occupancyOf(schoolId, date)
│   └── internal/SchoolContractsImpl.java
├── notification/
│   ├── api/NotificationType.java           # + MOU_NOT_RECORDED
│   └── internal/MarketingEventListener.java
└── recruitment/
    ├── api/                                # MarketingActivities, ProspectOwners, SupplySource, ProspectWon,
    │                                       #   FollowUpScheduled, WonProspectOverdue (records)
    └── marketing/
        ├── internal/                       # Prospect, StageHistory, MarketingActivity, ActivityAttendee, ActivityDateHistory,
        │                                   #   ActivityAttachment, ProposalRevision, ProposalPosition, MarketingSetting, OwnerHistory;
        │                                   #   repositories; ProspectService, PipelineService, ActivityService, AttachmentService,
        │                                   #   ProposalService, WinService, ContractStatusReader, OverdueJob, DashboardService,
        │                                   #   SettingsService, MarketingDevSeeder, DefaultSupplySource
        └── web/                            # ProspectController, ActivityController, ProposalController, PipelineController,
                                            #   DashboardController, SettingsController, FileController

backend/src/main/resources/db/migration/V23__create_marketing_tables.sql
backend/src/test/java/com/hls/recruitment/marketing/      # rules, scope, authorization, upload, events, overdue, module rules

frontend/src/features/marketing/
├── ProspectsPage.tsx           # list and filters, add prospect
├── ProspectDetailPage.tsx      # overview, activities, proposal, MoU status, history
├── PipelineBoard.tsx           # board by stage
├── MarketingCalendar.tsx       # month and list
├── ActivityDialog.tsx, ProposalForm.tsx, WinDialog.tsx, AttachmentList.tsx
├── MarketingDashboard.tsx, MarketingSettingsPage.tsx
└── marketingApi.ts
frontend/src/features/schoolbilling/{SchoolContractPage,MouFormDialog}.tsx   # A7: accept initial values from the hand-off
frontend/src/App.tsx                                  # + guarded routes under /marketing
docs/postman/                                         # new folder "023 School Marketing"
.specify/memory/constitution.md                       # MARKETING section, `files` module, matrix row
docs/spec-roadmap.md                                  # 023 status; the shared file storage contract C7 now built here
```

**Structure Decision**: Web application (unchanged layout). One new sub-package of `recruitment`, one new small module
`files`, additive public interfaces in `school`, `organization`, `schoolbilling` and `notification`, and one new
frontend feature folder.

### Delivery slices (for tasks.md, each independently demoable)

1. **Foundation**: migration, `MARKETING` permission and navigation, the `files` module, `SchoolRegistry`,
   `managersOfZone`, `occupancyOf`, the notification type, module skeleton and rules.
2. **Prospects, activities, calendar, attachments (US1)**.
3. **Pipeline and Final Stage review (US2)**.
4. **Proposals (US3)**.
5. **Win, hand-off, overdue flag and notification, follow-up event (US4)**.
6. **Dashboard and polish (US5)**.

## Complexity Tracking

No violations. Table intentionally omitted.
