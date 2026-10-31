# Contract: User Theme API

Base `/api/v1/me/theme`. Any authenticated user; always acts on the caller; no user id parameter, no permission key.

## GET /api/v1/me/theme

200:
```json
{
  "preference": { "preset": "classic", "mode": "device", "customAccent": null, "sidebarDark": false, "changedAt": null },
  "isDefault": true,
  "catalogue": { "version": 1, "presets": [ { "id": "classic", "name": "Classic", "light": {"primary":"#1E5AA8","accent":"#0F7B6C","background":"#F5F7FA","surface":"#FFFFFF"}, "dark": {} } ], "statusChips": {} }
}
```

## PUT /api/v1/me/theme

Replaces the whole preference. Body: `preset`, `mode`, `customAccent` (nullable `#RRGGBB`), `sidebarDark`, `changedAt` (ISO instant).

- 200 returns the winning row (same shape as GET). If the stored `changedAt` is later, nothing is written and the stored row is returned with `"applied": false`.
- 422 `ACCENT_UNREADABLE`: `{ "code": "ACCENT_UNREADABLE", "failures": [ {"variant":"light","pair":"accent-on-surface","ratio":2.9,"required":4.5} ], "suggested": "#B35C00" }`.
- 422 `UNKNOWN_PRESET`, `INVALID_COLOUR`, `INVALID_MODE`.
- 401 when unauthenticated. Writes publish `ThemeChanged` (audit).

## DELETE /api/v1/me/theme

Reset to default. 204. Publishes `ThemeChanged` with preset classic, mode device.

## POST /api/v1/me/theme/contrast-check

Body `{ "accent": "#FFEB3B" }`. 200 `{ "light": {...ratios, "pass": false}, "dark": {...}, "suggested": "#8A7500" }`. Pure, no write; lets clients show the server's verdict.

## Shared vectors

`contracts/contrast-vectors.json` (created in T003) lists colours with expected ratio (+/-0.01), pass flag and suggested colour; backend, web and mobile tests all load it.
