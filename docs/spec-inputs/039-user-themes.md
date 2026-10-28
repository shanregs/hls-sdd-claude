# 039 User Themes: `/speckit-specify` input

2026-10-27. Roadmap row 039 (new). Cross-cutting UI spec for web and mobile. Depends on 001 (light/dark toggle),
002 (app shell), 018 (Android foundation). Adds one small API and one table; changes no business rule.

## Decisions already made by the user

1. Several preset themes, each with a light and a dark variant, plus "system" (follow the device).
2. A user can pick a preset AND set their own accent (primary) colour. A contrast check refuses or adjusts
   unreadable colours (WCAG AA: text on surface >= 4.5:1, Constitution Principle IV).
3. The choice is stored on the server per user, so it follows them across web and phone (small API + table, for
   example `user_preferences`). Reuse any existing preference store if one exists (checked: none exists; the web
   `ThemeModeProvider` keeps `hls-theme-mode` in localStorage and the mobile `ThemeProvider` keeps
   `hls.themePreference` in AsyncStorage; both are device-only today).
4. Personal only: no Admin control; everyone starts on the default.

## Presets (keep the current look as "Classic")

| Preset | Light: primary / accent / background / surface | Dark: primary / accent / background / surface |
| --- | --- | --- |
| Classic (default) | current values | current values |
| Indigo and Amber | #3F51B5 / #F59E0B / #EEF1FB / #FFFFFF | #9FA8DA / #FBBF24 / #0F1220 / #191D33 |
| Teal and Coral | #00796B / #FF7043 / #EAF6F4 / #FFFFFF | #4DB6AC / #FF8A65 / #0C1A19 / #14262A |
| Deep Blue and Green | #1565C0 / #2E7D32 / #E8F0FA / #FFFFFF | #64B5F6 / #81C784 / #0B1522 / #13233A |

Verify every pair's contrast and adjust where it fails.

## Less-flat look

Tinted page background, primary-coloured app bar/header, 12 px card radius with soft shadows, status chips
(Approved/Pending/Rejected) as tinted semantic colours, optional darker primary sidebar on web.

## Existing sources

`frontend/src/theme/tokens.ts`, `ThemeModeProvider.tsx`; `mobile/src/theme/tokens.ts`, `themes.ts`,
`ThemeProvider.tsx`. Mobile tokens are a copy of web tokens: keep one source of truth, consider a shared preset
list in the API response.

## Screens

Web Account/Profile theme settings page; mobile Profile theme section; live preview; reset to default.
Mobile offline or signed out: fall back to the last stored theme on the device.

## Housekeeping

Migration numbering checked against main (V95 029, V100 031, V110 024, V120 022, V130 planned for 030): use V140.
Constitution is 3.0.0; Principle IV already says "Light and dark themes are supported" and WCAG 2.2 AA, so a PATCH
(3.0.1) only clarifies wording if wanted; no rule changes.

## Feature description (paste as the argument to `/speckit-specify`)

039-user-themes: Personal themes for the HLS web app and Android app. Each user picks one of several preset
themes (Classic, Indigo and Amber, Teal and Coral, Deep Blue and Green), each with light and dark variants, or
"follow my device"; may set their own accent colour, which is checked for readability and refused or adjusted if
unreadable; sees a live preview and can reset to the default. The choice is saved per user on the server and
follows them across web and phone; the phone remembers the last choice for offline and signed-out use. Includes a
less flat look (tinted page background, coloured header, rounded cards with soft shadows, tinted status chips,
optional darker sidebar on web). Personal only: no Admin control.
