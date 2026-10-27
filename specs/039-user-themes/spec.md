# Feature Specification: User Themes

**Feature Branch**: `spec/039-user-themes`

**Created**: 2026-10-27

**Status**: Draft

**Input**: User description: "Personal themes for the HLS web app and Android app: preset themes with light and dark variants, own accent colour with a readability check, live preview, reset, saved per user on the server, last choice remembered on the phone, a less flat look." (see docs/spec-inputs/039-user-themes.md)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Pick a preset theme and mode (Priority: P1)

A signed-in user opens the theme settings (web: Account, Theme; phone: Profile, Theme), sees the presets (Classic,
Indigo and Amber, Teal and Coral, Deep Blue and Green) and a mode choice (Light, Dark, Device), picks one, sees the
whole app change at once, and the choice is saved.

**Why this priority**: the core value; everything else builds on it.

**Independent Test**: pick Teal and Coral with Dark on the web; sign in on the phone: it shows Teal and Coral dark.

**Acceptance Scenarios**:

1. **Given** a user who has never chosen, **When** they open the app, **Then** it shows Classic with the Device mode.
2. **Given** the settings page, **When** the user picks a preset or mode, **Then** the app restyles immediately and the choice is saved without a separate Save step.
3. **Given** a choice saved on the web, **When** the same user signs in on the phone (or the reverse), **Then** the same preset, mode and accent apply.
4. **Given** mode Device, **When** the device switches between light and dark, **Then** the app follows.

---

### User Story 2 - Set my own accent colour safely (Priority: P1)

A user enters or picks their own accent (primary) colour on top of a preset. The page checks it for readability. An
unreadable colour is not applied as chosen: the user is shown the problem and an adjusted nearby colour that passes,
which they may accept.

**Why this priority**: the user explicitly asked for it, and the check protects accessibility (Principle IV).

**Independent Test**: enter pale yellow #FFEB3B: the page shows it fails, offers a darker passing yellow-ochre, and only the accepted colour is saved.

**Acceptance Scenarios**:

1. **Given** a custom accent that passes in both light and dark variants, **When** it is chosen, **Then** it is applied and saved.
2. **Given** a custom accent that fails (as text on surface, or as header colour under white or dark header text, below 4.5:1) in the active variant, **When** it is chosen, **Then** it is not saved as entered; an adjusted passing colour is offered with its ratio shown.
3. **Given** a value that is not a colour, **When** entered, **Then** an inline error is shown and nothing changes.
4. **Given** a saved custom accent, **When** the user switches between light and dark, **Then** the accent is adjusted automatically per variant so it stays readable.
5. **Given** a direct request to the server with an unreadable colour, **Then** it is refused with the suggested adjustment.

---

### User Story 3 - Live preview and reset (Priority: P2)

Before committing, the user sees a preview panel (header, button, card, table row, status chips) in the chosen
combination. A "Reset to default" action returns to Classic, Device mode, no custom accent.

**Why this priority**: builds trust in the choice; the page works without it.

**Independent Test**: change preset, observe the preview; press Reset; the app returns to Classic.

**Acceptance Scenarios**:

1. **Given** a custom accent being edited, **When** the value changes, **Then** the preview updates within one second and shows each contrast ratio.
2. **Given** any saved choice, **When** the user confirms Reset, **Then** the stored choice is cleared and the default applies on every device.

---

### User Story 4 - Works offline and signed out on the phone (Priority: P2)

The phone remembers the last applied theme on the device. With no connection, or on the sign-in screen, it uses that
stored theme; when the server answers, a newer server choice replaces it.

**Why this priority**: avoids a jarring wrong-theme flash and keeps field use consistent.

**Independent Test**: choose Indigo on the phone, turn on airplane mode, relaunch the app: Indigo is shown.

**Acceptance Scenarios**:

1. **Given** no connection, **When** the app starts, **Then** the last stored theme is used and the settings can still be previewed.
2. **Given** a change made offline, **When** the connection returns, **Then** it is sent to the server; if the server holds a newer choice from another device, the newest change wins.
3. **Given** a signed-out phone, **When** the sign-in screen opens, **Then** it uses the last stored theme (or Classic if none).

---

### User Story 5 - The less flat look (Priority: P2)

Every screen on web and phone gets a tinted page background, a primary-coloured header, cards with a 12 px radius
and soft shadows, and Approved / Pending / Rejected status chips as tinted semantic colours (green, amber, red).
On the web, the user may switch on a darker primary sidebar.

**Why this priority**: the visual refresh that makes presets worthwhile; it rides on the same tokens.

**Independent Test**: open a list screen with status chips in every preset and both modes; chips and header text meet 4.5:1.

**Acceptance Scenarios**:

1. **Given** any preset and mode, **When** a screen shows a status chip, **Then** the chip uses its semantic colours (not the preset's accent) with readable text.
2. **Given** the sidebar option off (default), **When** the web app loads, **Then** the sidebar keeps the neutral surface; on, it uses a darker shade of the primary with readable text.
3. **Given** Classic, **When** compared to before, **Then** colours are unchanged except the added tint, header, radius, shadows and chips.

---

### Edge Cases

- Server unreachable when saving on the web: the choice applies locally, a notice says it is not yet saved, and it retries; it is never lost silently.
- A stored preset that no longer exists (removed in a later release): fall back to Classic and keep the mode.
- Two devices change the theme at nearly the same time: the later change wins; the other device picks it up at its next load.
- Custom accent that is near-white or near-black: refused with an adjusted suggestion like any other.
- Browser storage blocked: the app still renders from the server choice or Classic.
- OS high-contrast / forced-colours mode is not overridden by presets.
- A user whose role changes keeps their theme (it is personal, not role-based).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST offer four presets: Classic (the current look, default), Indigo and Amber, Teal and Coral, Deep Blue and Green, each with a light and a dark variant.
- **FR-002**: The system MUST offer a mode choice of Light, Dark, or Device; the default is Device.
- **FR-003**: Users MUST be able to set their own accent (primary) colour on top of any preset, and clear it.
- **FR-004**: Every preset variant MUST meet WCAG AA: body text on surface and background at least 4.5:1; primary and accent used as text at least 4.5:1 on surface; header text on the primary at least 4.5:1; non-text boundaries at least 3:1. Supplied values below that are adjusted before shipping (see Assumptions).
- **FR-005**: A custom accent MUST be checked in the active variant; one that fails is not saved as entered, and an adjusted passing colour (same hue, lightness changed) is offered with the ratios shown.
- **FR-006**: The server MUST validate the same rule independently and refuse failing colours with the suggested adjustment.
- **FR-007**: The saved custom accent MUST be re-derived per variant so it passes in both light and dark.
- **FR-008**: The choice (preset, mode, custom accent, sidebar option) MUST be stored per user on the server and apply on web and phone after sign-in.
- **FR-009**: A user who has never chosen MUST get Classic with Device mode; there is no Admin or Director control over anyone's theme.
- **FR-010**: Changes MUST apply immediately in the running app and be saved without a separate Save step; the user may reset to the default at any time.
- **FR-011**: The settings page MUST show a live preview (header, button, card, table row, status chips) and the contrast ratios for the custom accent.
- **FR-012**: The phone MUST store the last applied theme on the device and use it when offline or signed out; the web MAY do the same to avoid a flash of the wrong theme.
- **FR-013**: When connectivity returns, a change made offline MUST be sent to the server; the most recent change (by time of change) wins.
- **FR-014**: The app MUST use a tinted page background, a primary-coloured header, and 12 px card radius with soft shadows (a border instead of a shadow in dark mode) on every screen through the shared design tokens.
- **FR-015**: Approved, Pending and Rejected status chips MUST use fixed tinted semantic colours that do not change with the preset, with at least 4.5:1 text contrast in light and dark.
- **FR-016**: The web MUST offer an optional darker primary sidebar (default off) that keeps text at least 4.5:1.
- **FR-017**: The preset list and its colour values MUST have one source of truth that both clients use, so web and phone cannot drift; a built-in Classic copy remains on each client for first start without a network.
- **FR-018**: Changing the theme MUST NOT change any business data, permission or menu; saving a theme is allowed to every signed-in user and needs no permission key.
- **FR-019**: Theme saves MUST be written to the audit trail as a low-volume preference change (user, time, preset and mode only, not the colour history).
- **FR-020**: Layout, text size and focus indicators MUST stay unchanged across presets; focus rings remain visible at 3:1 in every variant.

### Key Entities

- **Theme preset**: id, name, light and dark colour sets (primary, accent, background, surface, derived text and header-text colours). Read-only catalogue.
- **User theme preference**: the user, preset id, mode (light/dark/device), optional custom accent, sidebar option, last-changed time. At most one per user.
- **Contrast result**: for a colour in a variant, the checked pairs, their ratios, pass or fail, and the suggested adjusted colour.

## Role & Permission Impact *(mandatory — Constitution Principles II–IV)*

| Role     | Menu (section → item) | Default actions | Data scope |
| -------- | --------------------- | --------------- | ---------- |
| Admin    | Account → Theme (every signed-in user) | View, Edit own theme | Own |
| Director | Account → Theme | View, Edit own theme | Own |
| Manager  | Account → Theme (phone: Profile → Theme) | View, Edit own theme | Own |
| Teacher  | Account → Theme (phone: Profile → Theme) | View, Edit own theme | Own |
| System   | none | none | None |

**New permission keys**: none. Like change-password and profile, theme settings belong to the signed-in user and are not in the permission matrix; no role can read or change another user's theme.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can change preset and mode and see the result in under 3 seconds, within 3 taps or clicks from the settings page.
- **SC-002**: 100 percent of preset variants and 100 percent of accepted custom accents meet the 4.5:1 readable-text rule, checked by automated tests.
- **SC-003**: A choice made on one device shows on the other after the next sign-in or app start with no further action, in 100 percent of test cases.
- **SC-004**: With no network, the phone starts in the last chosen theme with no flash of a different theme in 100 percent of test starts.
- **SC-005**: Every existing screen renders in all eight preset variants with no unreadable text; the existing accessibility checks still pass.
- **SC-006**: Reset returns every device to Classic with Device mode within one app start.

## Assumptions

- Defaults for open choices (also in clarification-questions.md): (1) an unreadable custom accent is offered an adjusted colour for the user to accept rather than being silently changed; (2) the darker sidebar is a per-user opt-in, default off; (3) one custom accent is stored and re-derived for each variant.
- Preset adjustments after contrast checking: Indigo and Amber light accent changed from #F59E0B (2.15:1 on white) to #A94E08 (5.56:1); Teal and Coral light accent changed from #FF7043 (2.74:1) to #BF360C (5.60:1). All other supplied values pass (primaries 5.3:1 to 7.9:1 on surface, dark accents 6.8:1 to 9.9:1, white or dark header text on every primary 6.4:1 or better). The original brighter hues may be used for fills only, never for text.
- Classic keeps today's primary, secondary, error and surface values; its page background gains a light tint.
- Status chip colours (text on fill), light: Approved #1B5E20 on #E3F2E4, Pending #8A4B00 on #FFF1D6, Rejected #B3261E on #FDE7E5; dark: #A5D6A7 on #1F3A24, #FFD08A on #4A3410, #F2B8B5 on #4A1F1C (all above 5.5:1).
- Existing device-only preferences (web local storage `hls-theme-mode`, mobile `hls.themePreference`) are migrated: on first sign-in with no server choice, the local light/dark value is uploaded once.
- Constitution Principle IV already requires light/dark themes and WCAG 2.2 AA; no rule changes, so no version bump is required (an optional PATCH 3.0.1 may add "user-selectable themes" wording to the Visual quality bullet).
- Fonts, spacing, density and language are out of scope; no per-school or organisation-wide branding.
- Offline phone changes are queued with the existing outbox mechanism of spec 018.
