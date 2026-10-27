---

description: "Task list for feature implementation"
---

# Tasks: User Themes

**Input**: `/specs/039-user-themes/` (spec.md, plan.md, research.md, data-model.md, contracts/user-theme-api.md, quickstart.md)

**Tests**: included. Contrast rules are tested first against shared vectors. Per-role endpoint tests are mandatory (Principle IX). Run one Maven test class at a time; Docker must be running for Testcontainers.

**Organization**: Setup, Foundation, US1 (presets and mode), US2 (custom accent), US3 (preview and reset), US4 (phone offline), US5 (less flat look), Polish.

## Format: `[ID] [P?] [Story] Description`

## Phase 1: Setup

- [ ] T001 Verify `frontend/src/theme/{tokens.ts,ThemeModeProvider.tsx}`, `mobile/src/theme/{tokens.ts,themes.ts,ThemeProvider.tsx}`, `frontend/src/account/SettingsPage.tsx`, `mobile/src/screens/ProfileScreen.tsx`, the spec 018 outbox, and that the highest migration on main is V120 (V130 reserved for 030); STOP and report if different
- [ ] T002 [P] Create `backend/src/main/resources/themes/theme-presets.json` with the four presets (spec Assumptions values), status chip colours, derived-colour rules
- [ ] T003 [P] Create `specs/039-user-themes/contracts/contrast-vectors.json` (colour, variant, expected ratio, pass, suggested), including every preset pair and #FFEB3B

## Phase 2: Foundation

- [ ] T004 Flyway `V140__create_user_preferences.sql` per data-model.md
- [ ] T005 [P] Tests for `ContrastRules` (ratio, check, adjust) against the vectors in `backend/src/test/java/com/hls/identity/preferences/ContrastRulesTest.java`
- [ ] T006 Implement `ContrastRules` and `ThemeCatalogue` loader in `backend/src/main/java/com/hls/identity/preferences/`
- [ ] T007 [P] Test that every shipped preset variant passes the rule (SC-002) in `ThemeCatalogueTest.java`
- [ ] T008 Entity, repository, `ThemeChanged` event and audit listener for `user_preferences`

## Phase 3: US1 - Pick a preset and mode (P1)

- [ ] T009 [P] [US1] API tests: GET default, PUT, last-write-wins, unknown preset, 401, no cross-user access for each of the five roles in `UserThemeControllerTest.java`
- [ ] T010 [US1] `UserThemeService` and `UserThemeController` (GET, PUT) per contract
- [ ] T011 [P] [US1] Web `themeApi.ts` and `tokens.ts` rebuilt from the catalogue (`buildMuiTheme(variant)`); keep Classic built in
- [ ] T012 [US1] Extend `ThemeModeProvider.tsx`: load preference after sign-in, apply, save on change, local storage cache, migrate `hls-theme-mode` once; update `ThemeModeProvider.test.tsx`
- [ ] T013 [P] [US1] Web `ThemeSettingsPage.tsx` (preset cards, mode control) wired into Account with route and tests
- [ ] T014 [P] [US1] Mobile `themeApi.ts`, `builtinClassic.ts` (with equality test against the server Classic), replace the tokens copy in `tokens.ts`/`themes.ts`
- [ ] T015 [US1] Extend `ThemeProvider.tsx` (load, apply, save, migrate `hls.themePreference`) and the `ProfileScreen.tsx` theme section with tests

## Phase 4: US2 - Custom accent (P1)

- [ ] T016 [P] [US2] Tests for the PUT 422 `ACCENT_UNREADABLE` and `POST /contrast-check` in `UserThemeControllerTest.java`
- [ ] T017 [US2] Add `customAccent` handling, validation, suggestion and the contrast-check endpoint
- [ ] T018 [P] [US2] Web `contrast.ts` and mobile `contrast.ts` with tests loading `contrast-vectors.json`
- [ ] T019 [US2] Web accent field with colour picker, inline errors, accept-suggestion button; per-variant re-derivation in `buildMuiTheme`
- [ ] T020 [US2] Mobile accent entry (hex field plus swatches), suggestion flow, per-variant re-derivation; touch targets 48 dp

## Phase 5: US3 - Preview and reset (P2)

- [ ] T021 [P] [US3] Test DELETE reset and its audit event
- [ ] T022 [US3] Implement DELETE
- [ ] T023 [P] [US3] Web `ThemePreview.tsx` (header, button, card, table row, chips, ratios) and Reset with confirmation
- [ ] T024 [P] [US3] Mobile preview section and Reset

## Phase 6: US4 - Phone offline (P2)

- [ ] T025 [US4] Persist last applied theme in AsyncStorage, apply before first paint and on the sign-in screen
- [ ] T026 [US4] Queue offline changes via the spec 018 outbox with `changedAt`; resolve with the server winner; tests for offline start, queued change, newer server row
- [ ] T027 [P] [US4] Web: apply local cache before render to avoid a flash; unsaved notice and retry on save failure

## Phase 7: US5 - Less flat look (P2)

- [ ] T028 [P] [US5] Web tokens: tinted background, primary app bar, 12 px radius, soft shadow (border in dark), tests
- [ ] T029 [P] [US5] Shared status chip component/tokens on web and mobile; replace existing ad hoc status colours; tests per mode
- [ ] T030 [US5] Web optional dark sidebar (`sidebarDark`) in AppShell and settings toggle; text contrast test
- [ ] T031 [P] [US5] Mobile header (primary app bar), card radius and elevation in Paper theme

## Phase 8: Polish

- [ ] T032 Run the existing a11y suite across all eight variants (SC-005); fix findings
- [ ] T033 [P] Update `docs/` (running notes), Postman collection entries for the four endpoints
- [ ] T034 Run quickstart.md on web and device; record results
- [ ] T035 If the user asks: constitution PATCH 3.0.1 wording for Principle IV (not required)

## Dependencies

Foundation blocks all stories. US1 before US2 to US5 (US3 to US5 independent of each other after US1). Polish last.
