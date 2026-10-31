# Research: User Themes

## 1. Existing preference stores

None on the server. Spec 001 FR-014 persists web light/dark in localStorage (`hls-theme-mode`, `ThemeModeProvider.tsx`); spec 018 FR-016 persists the phone's system/light/dark in AsyncStorage (`hls.themePreference`, `ThemeProvider.tsx`). Specs 001 and 004 define no preference table. Decision: new table; migrate the local value once (upload on first sign-in when the server row is absent).

## 2. Where the catalogue lives

Options: bundle in each client (drift risk, current copy approach) or serve from the API. Decision: the server holds `theme-presets.json` and returns it in `GET /api/v1/me/theme`; clients cache the last response and carry a built-in Classic. A test on each client asserts its built-in Classic equals the server's. This removes the mobile "copy of web tokens" for presets; spacing and font tokens stay as they are.

## 3. Contrast rule and verified preset values

WCAG relative luminance. Computed ratios:

| Variant | text on surface | primary on surface | accent on surface | header text on primary |
| --- | --- | --- | --- | --- |
| Classic light | 16.6 | 6.8 | 5.2 (secondary) | 6.8 (white) |
| Classic dark | 13.6 | 7.9 | 9.4 | 8.2 (dark text) |
| Indigo light | 16.6 | 6.9 | 2.15 FAIL | 6.9 |
| Indigo dark | 13.6 | 7.2 | 9.9 | 7.5 |
| Teal light | 16.6 | 5.3 | 2.74 FAIL | 5.3 |
| Teal dark | 12.9 | 6.4 | 6.8 | 7.1 |
| Deep Blue light | 16.6 | 5.8 | 5.1 | 5.8 |
| Deep Blue dark | 13.0 | 7.1 | 7.9 | 7.8 |

Light-variant primary on the tinted background: 4.8 (Teal) is the lowest, still passing. Fixes: Indigo light accent #A94E08 (5.56 on white, 4.92 on its background); Teal light accent #BF360C (5.60, 5.07). Header text is white on light primaries and #0B1B33 on dark primaries; for a custom accent the header text colour is chosen automatically (white or dark, whichever is at least 4.5:1) and the accent is refused if neither passes.

## 4. Custom accent adjustment

Convert to HSL, keep hue and saturation, step lightness toward darker (light variant) or lighter (dark variant) in 1 percent steps until accent-as-text on surface and on background is at least 4.5:1 and a header text colour reaches 4.5:1. Deterministic, pure, same code on server and clients, with shared vectors in `contracts/contrast-vectors.json`.

## 5. Offline and conflict

Phone stores `{preset, mode, accent, sidebar, changedAt}` in AsyncStorage on every apply. Offline changes queue through the spec 018 outbox as a single replace-the-whole-preference request carrying `changedAt`; the server keeps the row with the later `changedAt` (last write wins) and returns the winning row. The web retries in memory.

## 6. Status chips and shadows

Chip colours are semantic tokens independent of the preset (spec Assumptions). Card shadow is a soft elevation in light and a 1 px border in dark; radius 12 via the `shape.borderRadius` token (MUI) and Paper `roundness`.

## 7. Constitution

Principle IV already covers it; no amendment. An optional PATCH 3.0.1 would add "user-selectable themes" to the Visual quality bullet; defer to the user.

## 8. Audit

Publish `ThemeChanged(userId, preset, mode)` in `identity.activity` style; no colour history.
