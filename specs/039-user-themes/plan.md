# Implementation Plan: User Themes

**Branch**: `spec/039-user-themes` | **Date**: 2026-10-27 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/039-user-themes/spec.md`

## Summary

Personal presets, mode, custom accent and sidebar option, stored per user on the server (`user_preferences`, V140),
served with the preset catalogue by one small API, applied on web (MUI) and phone (React Native Paper). The server
owns the catalogue and the contrast rule; clients carry a built-in Classic copy and a pure contrast function that is
tested against shared vectors. Also lands the "less flat" tokens (tint, header, 12 px radius, shadows, status chips,
optional sidebar).

## Technical Context

**Language/Version**: Java 21 / Spring Boot (backend), TypeScript (React + MUI web; React Native + Paper mobile)

**Primary Dependencies**: existing only; no new libraries (contrast maths is about 40 lines)

**Storage**: PostgreSQL, one new table `user_preferences` (Flyway V140; V130 is planned for 030, V120 is 022)

**Testing**: JUnit + Testcontainers (one Maven class at a time), Vitest/RTL web, Jest mobile, shared contrast vectors

**Target Platform**: web SPA and Android app

**Project Type**: web + mobile + API

**Performance Goals**: theme applies in under 100 ms after a click; preference GET adds one small call after sign-in

**Constraints**: offline-capable on phone (last theme in AsyncStorage, queued change via spec 018 outbox); no flash of wrong theme at start

**Scale/Scope**: every signed-in user; 1 row per user; 4 presets x 2 variants

## Constitution Check

- Principle II (permissions): no new key; endpoints are self-service for the authenticated user (same class as change-password); per-role tests prove no user can read or write another user's row (endpoint has no user id).
- Principle IV (visual quality, WCAG 2.2 AA): met and enforced by tests over every preset variant. No rule changes, so no constitution amendment is needed. Optional PATCH 3.0.1 wording only.
- Audit (Principle on audit): a `ThemeChanged` event (preset, mode only) is published and recorded.
- Principle IX (tests): per-role endpoint tests, UI tests on both clients, a11y check across all variants.
- Migration numbering: V140 chosen above all planned numbers (V95 029, V100 031, V110 024, V120 022, V130 030).
- Result: PASS.

## Project Structure

### Documentation (this feature)

```text
specs/039-user-themes/
  spec.md  plan.md  research.md  data-model.md  quickstart.md  tasks.md
  clarification-questions.md
  contracts/user-theme-api.md
  contracts/contrast-vectors.json   (created in implementation, shared by backend and both clients)
  checklists/requirements.md
```

### Source Code (repository root)

```text
backend/src/main/java/com/hls/identity/preferences/    # new package: controller, service, entity, repository, ContrastRules, ThemeCatalogue, ThemeChanged event
backend/src/main/resources/db/migration/V140__create_user_preferences.sql
backend/src/main/resources/themes/theme-presets.json   # single source of truth for the catalogue
frontend/src/theme/      # tokens.ts (build from catalogue), ThemeModeProvider.tsx (extend), contrast.ts, themeApi.ts
frontend/src/account/ThemeSettingsPage.tsx             # new tab/page with preview
mobile/src/theme/        # tokens.ts (drop the copy), ThemeProvider.tsx (extend), contrast.ts, themeApi.ts, builtinClassic.ts
mobile/src/screens/ProfileScreen.tsx                   # replace the 3-way toggle with the theme section
```

**Structure Decision**: web + mobile + API. The catalogue JSON lives on the server and is returned by the API; each
client keeps a generated or hand-synced Classic fallback and tests that it equals the server's Classic.

## Complexity Tracking

None.
