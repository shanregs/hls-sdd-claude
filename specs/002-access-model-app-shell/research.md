# Phase 0 Research: Access Model & App Shell

No `NEEDS CLARIFICATION` markers were left in the Technical Context — every open technical question
below was resolved during specification/planning discussion rather than deferred. Recorded here so
the decisions and their reasoning survive past this conversation.

## 1. Frontend routing

- **Decision**: React Router (v7).
- **Rationale**: the spec requires a "real client-side router with route guards" (FR-010) and a
  "not authorized" page on any unauthorized deep link. React Router is the de facto standard for
  exactly this — nested routes, loader/guard patterns, programmatic redirects — with first-class
  React 19 support and no serious competing option in the React ecosystem for this job.
- **Alternatives considered**: hand-rolled routing (rejected — reinvents guard/redirect logic the
  library already solves); TanStack Router (a newer, type-safe alternative — rejected only because
  it adds a less-common pattern for a team of one to maintain, with no concrete benefit for this
  feature's needs).

## 2. UI component library and theming

- **Decision**: MUI (Material UI) core, with `ThemeProvider` driving light/dark palettes from a
  single token set.
- **Rationale**: the constitution's own Principle IV wording — "one design system (**component
  library** plus design tokens for color, spacing, and type)" — already points at a component
  library, not a headless/hand-styled approach. For an internal, CRUD-heavy, 18-module admin
  application built largely solo, a mature component library with built-in accessible components,
  built-in light/dark theming, and a large support surface minimizes both build time and long-term
  maintenance, at the acceptable cost of a recognizable "Material" visual baseline (fully
  retextured via theme tokens, and irrelevant for an internal ops tool with no brand requirement).
- **Alternatives considered**: Radix UI primitives + hand-written CSS (rejected — maximizes visual
  control at the cost of building every table/form/dialog's behavior and styling from scratch across
  18 modules); Tailwind CSS + shadcn/ui (a reasonable second choice — rejected because shadcn
  components are copied into the repo and then owned/maintained by the project, versus MUI's
  externally maintained component set, which better serves the "ease of maintenance" priority).

## 3. Data grid (view/edit lists)

- **Decision**: MUI X DataGrid, Community (free, MIT-licensed) edition.
- **Rationale**: pairs natively with the MUI theme (no second visual language to maintain), and the
  Community tier already includes everything the constitution requires on every list screen —
  sorting, filtering, pagination — plus inline cell/row editing, which this feature and every later
  master-data spec (005, 007, etc.) need for quick edits. No Pro/Premium features (row grouping, tree
  data, Excel export) are required by any current spec.
- **Alternatives considered**: AG Grid Community (rejected — brings its own theming system,
  conflicting with Principle IV's "one design system used consistently"); TanStack Table (rejected —
  headless, meaning the grid UI, editing affordances, and accessibility behavior would need to be
  built by hand, which MUI X already provides).

**Addendum (2026-10-04) — the matrix screen is not a DataGrid.** The Role & Permissions screen was
first built as a DataGrid with one row per `(role, module, action)`, which is several hundred rows
and hard to read as a whole. It is now a compact **Module × Role table** (a plain MUI `Table`, which
the theme borders like every other table), one row per module and one column per role, with an icon
per applicable action in each cell (`VisibilityOutlined`, `AddCircleOutlineOutlined`,
`EditOutlined`, `DeleteOutlined`, `PlayCircleOutlined`, `FileDownloadOutlined`, `TaskAltOutlined`):
coloured when granted, grey when applicable but not granted, a dash when nothing applies. The grid
needs no sorting, filtering or paging (about 18 modules by 5 roles), so DataGrid adds nothing there;
DataGrid remains the choice for the master-data lists. Which actions apply is defined in code
(`PermissionModule.actions()` and `PermissionEligibility`), not stored, so later specs add a module
by adding one enum constant with its actions — no migration, no screen change. Icons carry the state
in their accessible name and a tooltip, so colour is never the only cue (WCAG 1.4.1). A click opens
the existing confirmation dialog rather than toggling at once, to keep a permission change
deliberate and to reuse the audited, safeguarded edit path.

## 4. Form handling (matrix-edit dialog, and later record create/edit dialogs)

- **Decision**: react-hook-form.
- **Rationale**: quick edits (a single grant toggle) use the DataGrid's inline editing; full
  multi-field edits (starting with this spec's grant-edit dialog, and every later master-data
  create/edit form) use a MUI `Dialog` with react-hook-form managing validation state. It is small,
  has minimal re-render overhead, and integrates cleanly with MUI's controlled inputs — a
  low-maintenance, widely-used default rather than a new pattern per form.
- **Alternatives considered**: Formik (rejected — heavier, more re-renders, less actively evolved);
  uncontrolled plain-HTML forms (rejected — insufficient for cross-field validation like the
  last-manager safeguard's client-side hint).

## 5. Navigation model: code constant vs. database table

- **Decision**: the navigation tree (sections, items, which module/action each requires) is a small,
  versioned constant in backend code (`NavigationCatalog`), not a database table. The
  `permission_matrix` table only stores which role has which action grant; the access-model resolver
  filters the code-defined catalog by the matrix at request time.
- **Rationale**: avoids a second source of truth for "what screens exist" — screens are added by
  writing code (each spec's own PR), not by a runtime data-entry process. The matrix is the only
  thing that legitimately needs runtime editing (Constitution Principle II); the catalog of possible
  screens does not.
- **Alternatives considered**: fully data-driven navigation (rejected — over-engineered for a fixed,
  code-deployed set of screens; adds a migration/admin-UI burden with no corresponding requirement).

## 6. Theme preference persistence

- **Decision**: `localStorage`, scoped per device/browser, read before first paint to avoid a flash
  of the wrong theme.
- **Rationale**: matches FR-014's requirement ("persist... on the same device across sign-out/sign-
  in") without requiring a backend round-trip before the shell can render. Explicitly documented as
  an Assumption in spec.md that cross-device sync of theme choice is a future enhancement, not
  required now.
- **Alternatives considered**: storing theme on the User record server-side (rejected for this spec
  — adds a write path and a fetch-before-render dependency for a per-device cosmetic preference with
  no compliance or security implication).

## 7. Access-model computation and refresh timing

- **Decision**: resolved server-side on sign-in and on every session renewal (spec 001's ≤15-minute
  cycle); the frontend fetches it once per app load/renewal and holds it in memory for that session,
  with no independent client-side cache duration to reason about.
- **Rationale**: directly satisfies FR-015 (matrix/role changes take effect within one renewal cycle
  without forcing logout) using the renewal mechanism spec 001 already provides, rather than
  inventing a second expiry/invalidation scheme.
- **Alternatives considered**: embedding the resolved menu/action list inside the JWT itself
  (rejected — would require re-issuing tokens on every matrix edit to propagate changes, which is
  more disruptive than recomputing on the existing renewal cadence).

## 8. Last-matrix-manager safeguard algorithm

- **Decision**: on every matrix edit, before committing, count how many *role* grants of
  `identity.permissions.manage` (the Role & Permissions capability) would remain across Admin,
  Director, and System. If the edit would bring that count to zero, reject the whole edit
  transactionally with an explanation; nothing is partially applied.
- **Rationale**: directly implements FR-004. Checking at the role-grant level (not "is there a
  currently-active user in that role") keeps the rule simple, deterministic, and independent of how
  many actual users exist at edit time — matching Principle II's wording ("removes the last user able
  to manage permissions") conservatively.
- **Alternatives considered**: checking live user counts instead of role grants (rejected — more
  complex, and would allow a technically-reachable-but-currently-empty role to be stripped of the
  capability, which is riskier if a user is added to that role later expecting to manage the matrix).

## 9. Backend authorization enforcement

- **Decision**: a Spring Security `AuthorizationManager` (or equivalent request-level check) backed
  by `PermissionMatrixService`, applied to every controller endpoint independently of what the
  frontend renders.
- **Rationale**: Constitution Principle X — "the backend is the security control... permission
  checks MUST fail closed." The frontend's hidden menus (FR-007) are a UX affordance only; this is
  the actual enforcement point, testable per role and per module/action (Principle IX).
- **Alternatives considered**: relying on frontend route guards alone (rejected outright — violates
  Principle X explicitly).

## 10. Automated accessibility verification

- **Decision**: axe-core, run against the shell, navigation, and all five dashboards in both themes
  as part of the frontend test suite, to satisfy SC-007 (zero critical WCAG 2.2 AA violations).
- **Rationale**: axe-core is the standard automated accessibility engine, integrates with Vitest/
  Testing Library via `jest-axe`-style assertions, and catches the majority of contrast, labeling,
  and keyboard-navigation issues before manual review.
- **Alternatives considered**: manual-only accessibility review (rejected — not repeatable per
  change, and SC-007 requires an automated check to remain verifiable in CI).
