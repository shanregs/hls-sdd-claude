# Feature Specification: Access Model & App Shell

**Feature Branch**: `002-access-model-app-shell`

**Created**: 2026-09-23

**Status**: Draft

**Input**: User description: "Access Model & App Shell (roadmap spec 002, modules `identity` + frontend shell) under Constitution v2.2.0. Depends on 001-identity-access. Scope: seed the role→permission matrix (module × action) from the Constitution's default access-matrix table, with Admin/Director/System able to edit it at runtime (audited, last-admin-safeguard); expose a per-user access-model API returning the union of the signed-in user's roles' menus, actions, and data scope; build the shared frontend app shell — persistent left navigation (collapsible, becomes a drawer on tablet/phone), main content area, light/dark theme (persisted, matches the toggle introduced in 001), a real client-side router with route guards and a "not authorized" page for unauthorized deep links; role-based landing dashboards for each of the five roles (Admin, Director, Manager, Teacher, System) showing skeleton widgets appropriate to that role, and ACCOUNT section (Profile, Logout) reusing 001's Profile screen. Only authorized menu items/actions render (hidden, not disabled). Menus are never hard-coded per role in the frontend; they come from the server-provided navigation model. Out of scope: audit screens (003), user & role management screens (004), business-module menus/widgets beyond skeletons (005+), mobile app."

## Clarifications

### Session 2026-10-04

- Q: How should the Role & Permissions screen look? → A: One compact **Module × Role grid**: a row per
  module, a column per role (Admin, Director, Manager, Teacher, System), and in each cell an icon per
  action that applies to that module (View, Create, Edit, Delete, Process, Export, Approve). An icon
  is **coloured when the action is granted** and **grey when it could be granted but is not**; a
  cell where nothing applies to that role shows a dash. Sketch (V = view, E = edit, D = delete; a
  letter in backticks is a coloured icon, a plain letter a grey one):

  | Module          | ADMIN   | DIRECTOR | MANAGER |
  | --------------- | ------- | -------- | ------- |
  | DASHBOARD       | `V`     | `V`      | `V`     |
  | ACCOUNT_PROFILE | `V E`   | `V E`    | `V E`   |
  | USERS           | `V E D` | `V` E D  | `V` E D |
  | REPORTS         | `V`     | `V`      | —       |
- Q: What does "eligible" mean? → A: An action is eligible for a role on a module when the module
  offers that action and the Constitution does not rule the combination out: only Admin, Director
  and System can hold Role & Permissions, and System holds no teacher or school business module.
  The server refuses a grant that is not eligible, so the screen and the API agree.
- Q: In what order do the navigation sections appear? → A: Dashboard, MASTER DATA, OPERATIONS,
  SYSTEM, AUDIT, ACCOUNT (previously Dashboard, SYSTEM, AUDIT, MASTER DATA, OPERATIONS, ACCOUNT),
  so the everyday business screens come first and the administrative ones after them. A Teacher's
  MY ATTENDANCE sits in the OPERATIONS position; System's SYSTEM CONFIGURATION in the SYSTEM one.
- Q: How are the permission icons laid out? → A: Each action has its own small cell under each
  role, and the roles run System, Admin, Director, Manager, Teacher (this refines the first bullet
  above, which put all of a role's icons in one cell). The Module column is as wide as the longest
  module name plus 5 characters.
- Q: How is a grant changed? → A: Clicking an icon (for a user who may edit the matrix) opens the
  existing confirmation to switch it on or off; the audit record and the last-manager safeguard are
  unchanged. A user who can only view the matrix sees the same icons without any click action.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Every Role Lands on Its Own Dashboard With Its Own Menu (Priority: P1)

A signed-in user of any role — Admin, Director, Manager, Teacher, or System — is taken straight to
a landing dashboard that matches their role(s), with a navigation panel showing only the sections
and items they are allowed to use. A user holding more than one role sees the combined menu and
dashboard for all of their roles, never a role picker.

**Why this priority**: this is the payoff of signing in — a coherent, role-appropriate home screen
and menu. Every later business spec plugs its screens into this shell, so it must exist first.

**Independent Test**: sign in as a user holding each of the five roles in turn (and once as a user
holding two roles) and confirm each lands on a dashboard and left-nav that matches exactly their
role grants, with no manual role selection at any point.

**Acceptance Scenarios**:

1. **Given** a user holding only the Teacher role, **When** they sign in, **Then** they land on a
   Teacher dashboard and see only the Teacher's self-service navigation sections.
2. **Given** a user holding both Admin and Director, **When** they sign in, **Then** their
   navigation and dashboard combine both roles' grants, with no duplicate menu items.
3. **Given** a user holding the Manager role, **When** they view their dashboard, **Then** it shows
   placeholder widgets scoped to "assigned", not organization-wide, content.
4. **Given** a user holding the System role, **When** they sign in, **Then** they see the System's
   own dashboard and navigation sections and no business-module items.
5. **Given** any signed-in user, **When** they look at the navigation panel, **Then** every section
   shown contains at least one item they are authorized for; sections with none are not shown.

---

### User Story 2 - Unauthorized Areas Are Invisible and Unreachable (Priority: P1)

A user cannot see, and cannot reach by typing or bookmarking a URL, any screen their role does not
grant. If they try, they land on a plain "not authorized" page instead of the screen or its data.

**Why this priority**: hiding a menu item is not security by itself. The routing layer must enforce
the same rule the navigation does, or the shell is unsafe for every module built on top of it.

**Independent Test**: as a Manager, note a route only Admin can reach (from documentation, not the
UI), navigate to it directly by URL, and confirm a "not authorized" page appears with no data from
that route ever loaded into the browser.

**Acceptance Scenarios**:

1. **Given** a signed-in Manager, **When** they navigate directly to a URL reserved for Admin,
   **Then** they see a "not authorized" page and no data or component for that screen is fetched.
2. **Given** a signed-in Teacher, **When** they use the browser back/forward buttons to return to a
   page they are no longer authorized for (for example after a role change), **Then** they see the
   "not authorized" page, not a cached view of the screen.
3. **Given** the "not authorized" page, **When** the user selects the offered way back, **Then**
   they return to their own landing dashboard.
4. **Given** a signed-in user, **When** they inspect the menu, **Then** no item they cannot access
   is present even in a disabled state — unauthorized items are absent, not greyed out.

---

### User Story 3 - Admin, Director, and System Maintain the Permission Matrix via a Role & Permissions Menu (Priority: P2)

A user holding Admin, Director, or System sees a "Role & Permissions" menu item and can open it to
change which roles are granted which actions on which module, at any time, without a deployment.
Manager and Teacher never see this menu item. Every change is recorded, and the system refuses a
change that would leave nobody able to manage the matrix.

**Why this priority**: the matrix is what makes roles configurable per Constitution Principle II. It
depends on Story 1 (the access model must exist before it can be edited).

**Independent Test**: as Admin, toggle an existing grant off and confirm the affected role loses
that menu/action within one session-renewal cycle without needing to sign out. Then attempt an edit
that would leave no Admin, Director, or System user able to manage the matrix, and confirm it is
rejected.

**Acceptance Scenarios**:

1. **Given** a user holding Admin, Director, or System, **When** they change a role's grant for a
   module/action, **Then** the change applies and is captured as a change record with the actor,
   timestamp, and before/after values.
2. **Given** a user holding only Manager or Teacher, **When** they look at their navigation menu,
   **Then** no "Role & Permissions" item appears, and a direct attempt to reach the capability by
   URL is refused with the "not authorized" page.
3. **Given** an edit that would remove every remaining Admin/Director/System grant to manage the
   matrix, **When** it is submitted, **Then** it is rejected with a clear explanation and no change
   is applied.
4. **Given** a matrix change just took effect, **When** an affected, already-signed-in user's
   session next renews (at most 15 minutes), **Then** their menu and actions reflect the change
   without requiring them to log out.
5. **Given** the Role & Permissions screen, **When** it opens, **Then** it shows one row per module
   and one column per role, with an icon for each action that applies to that module in each cell:
   a coloured icon where the action is granted, a grey icon where it could be granted but is not,
   and a dash where no action applies to that role; each icon names the role, action, module and
   whether it is granted (never colour alone), and a tooltip reads, for example, "Edit - granted".
6. **Given** a user who may edit the matrix, **When** they click an icon, **Then** a confirmation
   opens to switch that grant on or off, the matrix reloads on save, and a refusal (the last-manager
   safeguard, or a grant that does not apply) is shown inside the confirmation; **Given** a user who
   may only view the matrix, **Then** the icons are shown but cannot be clicked.
7. **Given** a grant that does not apply (for example Delete on Dashboard, Role & Permissions for a
   Teacher, or any business module for System), **When** it is submitted directly to the API,
   **Then** it is refused with a plain-language reason and nothing changes.

---

### User Story 4 - Theme Choice Persists (Priority: P2)

A user picks light or dark mode once and the application remembers it on that device across future
visits, without needing to reselect it every time they sign in.

**Why this priority**: a clean, deliberate visual presentation is one of the two things explicitly
asked for in this foundation. It is independent of the access-model work but ships in the same
shell.

**Independent Test**: switch to dark mode, sign out, close the browser, reopen it, and sign back in;
confirm dark mode is still active before any content loads.

**Acceptance Scenarios**:

1. **Given** a user switches the theme toggle, **When** the page re-renders, **Then** every part of
   the shell, navigation, and dashboard immediately reflects the chosen theme with no unstyled
   flash.
2. **Given** a theme was chosen on a device, **When** the same browser returns later (signed out or
   signed back in), **Then** the same theme is applied automatically.
3. **Given** a device where persisted storage is unavailable (for example private browsing), **When**
   the app loads, **Then** it falls back to a sensible default theme without an error.
4. **Given** either theme, **When** any shell screen is checked, **Then** text and interactive
   elements meet WCAG 2.2 AA contrast requirements.

---

### User Story 5 - Navigation Adapts to Screen Size (Priority: P3)

The same shell works from a desktop monitor down to a phone screen. On narrow screens the
navigation panel becomes a drawer instead of a fixed column, and nothing requires horizontal
scrolling.

**Why this priority**: Directors and Managers are expected to also use tablets per the
Constitution's technology stack; this polish is not required to validate Stories 1-4.

**Independent Test**: load the shell at desktop, tablet, and 360px phone widths and confirm the
navigation panel is a persistent column on desktop and tablet-wide, and a collapsible drawer at
phone width, with no horizontal page scroll at any width.

**Acceptance Scenarios**:

1. **Given** a desktop-width screen, **When** the shell loads, **Then** the navigation panel is a
   persistent column occupying roughly 25-30% of the width, collapsible by the user.
2. **Given** a 360px-wide screen, **When** the shell loads, **Then** the navigation panel is hidden
   behind a menu control and opens as an overlay drawer, and no page requires horizontal scrolling.
3. **Given** the drawer is open on a narrow screen, **When** the user selects a menu item, **Then**
   the drawer closes and the selected screen is shown.

---

### Edge Cases

- **A user holds two roles with different grants on the same module/action**: the union wins — if
  either role grants an action, the user has it.
- **A permission-matrix edit would leave no user able to manage the matrix**: rejected outright with
  an explanation; the prior matrix state is unchanged.
- **A role has no authorized items in a navigation section**: the whole section is omitted, not
  shown empty.
- **A signed-in user's roles or grants change while their session is active**: the new access model
  takes effect at their next session renewal (within 15 minutes, matching spec 001) or next sign-in,
  never mid-request.
- **A bookmarked URL was authorized when saved but later revoked**: the next visit shows "not
  authorized"; nothing about the former screen is exposed.
- **Persistent storage is unavailable for the theme preference**: the app still renders correctly
  with a default theme, without throwing an error to the user.
- **A user holds zero business-facing roles (System only)**: their dashboard and menu show only
  System's own sections; no business-module placeholder appears for them.
- **Two browser tabs signed in as the same user, one changes the theme**: each tab reflects its own
  loaded preference; there is no requirement for live cross-tab sync in this feature.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST seed, on first startup, a role→permission matrix covering exactly the
  module/action pairs that exist as of this feature: Dashboard (View) for all five roles, and
  Account/Profile (View, Edit-own) for all five roles. It MUST NOT seed entries for modules not yet
  built; each later spec adds its own rows without requiring this feature to change.
- **FR-002**: The system MUST present a single "Role & Permissions" menu item (SYSTEM section for
  Admin/Director; SYSTEM CONFIGURATION section for System) through which the permission-matrix
  capability (view current grants, change a grant) is reached. This item MUST be visible, and the
  capability reachable, only to users holding Admin, Director, or System. Manager and Teacher MUST
  NOT see the menu item or reach the capability by any route.
- **FR-003**: Every change to the matrix MUST produce a change record capturing the actor, timestamp,
  the module/action/role changed, and the before and after values, published for the Audit module
  (spec 003) to surface. This feature does not build an audit screen itself.
- **FR-004**: The system MUST reject any matrix edit that would leave no user, through any role,
  able to view and edit the matrix, and MUST explain why the edit was refused.
- **FR-004a**: The Role & Permissions screen MUST present the matrix as a compact grid of modules
  (rows) by roles (columns), with an icon per applicable action in each cell — coloured when
  granted in a colour specific to its action (View blue, Create green, Edit orange, Delete red,
  Process purple, Export teal, Approve indigo), grey when applicable but not granted, and a dash when nothing applies to that role —
  with a tooltip and an accessible name stating the role, action, module and state. A user who may
  edit the matrix MUST be able to change a grant by clicking its icon and confirming; a view-only
  user MUST see the icons without a click action.
- **FR-004b**: The system MUST define, for each module, which actions apply to it, and, for each
  role, which modules it may hold at all (only Admin, Director and System may hold Role &
  Permissions; System holds no teacher or school business module). The matrix response MUST say
  which actions are eligible per module and role, and an edit for an ineligible grant MUST be
  refused with a reason and no change.
- **FR-005**: The system MUST expose an access-model capability for the current signed-in user that
  resolves, from the union of their roles' grants: the navigation sections and items they may see,
  the actions available within each, and a data-scope classification (Org-wide / Assigned / Own /
  None) for each item.
- **FR-006**: The frontend MUST render navigation, dashboard content, and in-screen actions strictly
  from the access model returned for the current user. No per-role menu or action logic MAY be
  hard-coded in frontend source.
- **FR-007**: An item the current user is not authorized for MUST NOT appear anywhere in the
  returned access model or the rendered UI. Unauthorized items are absent, never shown disabled.
- **FR-008**: A navigation section with no authorized item for the current user MUST be omitted
  entirely.
- **FR-008a**: The navigation sections MUST always appear in this order: **Dashboard, MASTER DATA,
  OPERATIONS, SYSTEM, AUDIT, ACCOUNT**. A Teacher's MY ATTENDANCE section takes the OPERATIONS
  position, and for the System role SYSTEM DASHBOARD takes the Dashboard position and SYSTEM
  CONFIGURATION the SYSTEM position. The order is decided by the server in the access model (the
  frontend renders the sections as given), and the items inside a section keep their own order.
- **FR-009**: The application MUST provide one persistent left navigation panel on desktop and
  tablet widths (roughly 25-30% of the width, user-collapsible) and a collapsible overlay drawer at
  phone widths, with no horizontal page scrolling at 360px width.
- **FR-010**: A real client-side router MUST guard every route against the current access model.
  Reaching a route by menu, typed URL, bookmark, or browser navigation that the user is not
  authorized for MUST show a "not authorized" page instead of the screen, and MUST NOT fetch that
  screen's data. The page MUST offer a way back to the user's own dashboard.
- **FR-011**: After sign-in and on reload, the application MUST route the user to a landing
  dashboard reflecting the union of their roles. It MUST NOT ask the user to choose a role.
- **FR-012**: Each of the five roles MUST have a distinct landing dashboard: Admin and Director
  (organization-wide placeholder widgets), Manager (assigned-scope placeholder widgets), Teacher
  (self-service placeholder widgets: my attendance, my leave, my notifications), System (system
  health, users, and audit activity placeholders). Since no business module exists yet, widgets MUST
  show a clear empty/"coming soon" state rather than fabricated data.
- **FR-013**: Every role MUST have an ACCOUNT section with Profile (Teacher: "My Profile"), Settings
  and Sessions, and Logout. Profile shows the user's own details; Settings edits them and changes the
  password; Sessions is spec 001's session-management grid (FR-015). The Sessions item needs
  `MY_SESSIONS.VIEW`, which every role holds by default. System additionally has SYSTEM
  CONFIGURATION → All Sessions (`SESSION_MANAGEMENT.VIEW`, spec 001 FR-015a).
- **FR-014**: The application MUST offer a light/dark theme toggle applying consistent design tokens
  (color, spacing, type) across the shell, navigation, and dashboards; MUST meet WCAG 2.2 AA in both
  themes; and MUST persist the chosen theme on the same device across sign-out/sign-in, defaulting
  sensibly when persisted storage is unavailable.
- **FR-015**: The access model MUST be recomputed at least at every sign-in and every session
  renewal, so that role or permission-matrix changes take effect within one renewal cycle (at most
  15 minutes, per spec 001) without requiring the affected user to log out.
- **FR-016**: The System role's access model MUST NOT include any business-module menu item, action,
  or widget, now or as later specs add their own entries, consistent with Constitution Principle II.

### Key Entities

- **Permission Matrix Entry**: one grant of one action on one module to one role (granted or not),
  with metadata on who last changed it and when.
- **Navigation Item**: one menu entry (section, label, target screen, required module/action),
  ordered within its section.
- **Access Model**: the resolved, per-user result for the current session — the navigation items,
  actions, and data-scope classification the user's combined roles authorize.
- **Theme Preference**: the chosen display mode (light/dark) for a device.

## Role & Permission Impact *(mandatory — Constitution Principles II–IV)*

| Role     | Menu (section → item)                                   | Default actions                       | Data scope |
| -------- | -------------------------------------------------------- | -------------------------------------- | ---------- |
| Admin    | Dashboard; SYSTEM → Role & Permissions; ACCOUNT → Profile | View (Dashboard); View/Edit (matrix)   | Org-wide   |
| Director | Dashboard; SYSTEM → Role & Permissions; ACCOUNT → Profile | View (Dashboard); View/Edit (matrix)   | Org-wide   |
| Manager  | Dashboard; ACCOUNT → Profile                              | View                                   | Assigned   |
| Teacher  | Dashboard; ACCOUNT → My Profile                           | View                                   | Own        |
| System   | SYSTEM DASHBOARD; SYSTEM → Role & Permissions; ACCOUNT → Profile | View (Dashboard); View/Edit (matrix) | None (business) |

**New permission keys**: `dashboard.view` (all five roles), `account.profile.view` /
`account.profile.edit` (all five roles, own record only — carried over from spec 001), and
`identity.permissions.manage` (Admin, Director, System only).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In 100% of tested role combinations, a signed-in user sees exactly the menu items and
  actions their roles grant — no extra items, no missing items.
- **SC-002**: A signed-in user reaches a role-appropriate landing dashboard within 3 seconds of
  successful sign-in.
- **SC-003**: 100% of attempts to reach an unauthorized route by URL, bookmark, or browser navigation
  result in the "not authorized" page, with zero bytes of that screen's data ever sent to the
  browser, verified by automated route-guard tests covering every defined route.
- **SC-004**: 100% of permission-matrix edits by an authorized user apply and are visible to affected
  already-signed-in users within one session-renewal cycle (15 minutes), without requiring logout.
- **SC-005**: 100% of matrix edits that would remove the last remaining matrix-manager are rejected.
- **SC-005a**: The whole matrix (every module and role) is readable on one screen without leaving
  the page, and a grant can be changed in two clicks (icon, then confirm); 100% of edits for a grant
  that does not apply are refused by the server.
- **SC-006**: A chosen theme persists across 100% of tested sign-out/sign-in cycles on the same
  device.
- **SC-007**: The shell, navigation, and all five dashboards pass an automated WCAG 2.2 AA check with
  zero critical violations in both themes, and render with no horizontal scrolling at 360px width.
- **SC-008**: In a hallway test, at least 90% of first-time users correctly identify what their own
  role's menu is for without being told, without seeing any item belonging to another role.

## Assumptions

- Only the Dashboard and Account/Profile capabilities are seeded into the permission matrix by this
  feature. Every later spec (003 onward) adds its own module/action rows and menu items into the
  same matrix and access-model contract, so this feature's schema is built to extend without a
  breaking migration, though that extensibility work belongs to planning, not this spec.
- Theme preference is persisted per device via browser storage in this feature. Syncing a user's
  theme choice across their own devices is a possible future enhancement, not required now.
- Role dashboards show empty/"coming soon" placeholder widgets rather than sample data, since no
  business module has shipped yet; specs 005 onward replace the placeholders with real widgets.
- The data-scope classification (Org-wide/Assigned/Own/None) is carried in the access-model contract
  now even though only Dashboard uses it today, so specs 005+ can attach real scoped data without a
  contract change.
- "Not authorized" is a generic page with no information about what the blocked screen contains,
  consistent with spec 001's practice of not revealing information to unauthorized parties.
- Dependencies: depends on 001-identity-access for the signed-in identity and roles. Specs 003
  (audit trail for matrix changes), 004 (screens for user and role management), and 005 onward
  (business menus, actions, and dashboard widgets) depend on this spec.
