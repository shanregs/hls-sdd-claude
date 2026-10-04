# Phase 1 Data Model: Access Model & App Shell

Derived from spec.md's Key Entities section. Only `Permission Matrix Entry` is persisted; the other
three are resolved/held in memory per request or per device, as decided in research.md (§5, §6, §7).

## Permission Matrix Entry (persisted — table `permission_matrix`)

The only new persisted entity in this feature. One row per (role, module, action) combination.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID (PK) | |
| `role` | enum: `ADMIN`, `DIRECTOR`, `MANAGER`, `TEACHER`, `SYSTEM` | fixed set, Constitution Principle II — not a free-text column |
| `module` | enum-like code, e.g. `DASHBOARD`, `ACCOUNT_PROFILE`, `IDENTITY_PERMISSIONS` | grows as later specs add modules; no schema change needed, values are additive |
| `action` | enum: `VIEW`, `CREATE`, `EDIT`, `DELETE`, `APPROVE`, `PROCESS`, `EXPORT` | per Constitution Principle II's action set |
| `granted` | boolean | whether this role currently has this action on this module |
| `updated_by` | user id (FK, nullable for seed rows) | who last changed this row |
| `updated_at` | timestamp | when it was last changed |

**Constraints**:
- Unique on `(role, module, action)` — one grant state per combination, never duplicated.
- Seeded on first startup (FR-001) with exactly: `(role, DASHBOARD, VIEW, true)` for all five roles,
  and `(role, ACCOUNT_PROFILE, VIEW, true)` / `(role, ACCOUNT_PROFILE, EDIT, true)` for all five
  roles (own-record only, enforced at the resolver, not a matrix column). Seeding MUST NOT
  overwrite existing rows on restart (idempotent).
- `(role, IDENTITY_PERMISSIONS, VIEW, true)` and `(role, IDENTITY_PERMISSIONS, EDIT, true)` are
  seeded `true` only for `ADMIN`, `DIRECTOR`, `SYSTEM`; `false`/absent for `MANAGER`, `TEACHER`.
- **Invariant enforced on every write** (FR-004): after applying a proposed edit, at least one of
  `ADMIN`, `DIRECTOR`, `SYSTEM` must still have `(role, IDENTITY_PERMISSIONS, EDIT, true)`. Violating
  edits are rejected in the same transaction; nothing is partially applied.
- Every write produces a change record (actor, timestamp, before/after) published for spec 003's
  Audit module. The change record is not owned by this feature — see Assumptions in spec.md.

**Eligibility (added 2026-10-04, not persisted)**: which `(role, module, action)` rows may exist is
defined in code, not in the table. Each module declares the actions that apply to it
(`PermissionModule.actions()`, e.g. `DASHBOARD` → View; `USER_MANAGEMENT` → View, Create, Edit;
`ATTENDANCE` → View, Create, Edit, Delete, Process, Export), and `PermissionEligibility` removes the
combinations the Constitution rules out: `IDENTITY_PERMISSIONS` for any role other than `ADMIN`,
`DIRECTOR`, `SYSTEM`, and every teacher/school business module (`ZONES`, `SCHOOLS`, `MANAGERS`,
`TEACHERS`, `TEACHER_SALARY`, `ATTENDANCE`, `TEACHER_ATTENDANCE`, `MY_ATTENDANCE`,
`ATTENDANCE_SETUP`) for `SYSTEM`. A missing row for an eligible combination means "not granted".
`updateGrant` refuses an ineligible combination before anything else. No schema change; every row
seeded by any spec is eligible (checked by a test).

No state machine: a row is either granted or not, toggled directly. No soft-delete/versioning of
rows themselves; history lives in the published change records, not in this table.

## Navigation Item (code-defined constant, not persisted)

Not a database entity — a versioned in-code catalog (`NavigationCatalog`, per research.md §5) that
the access-model resolver filters against `Permission Matrix Entry`.

| Field | Type | Notes |
|---|---|---|
| `section` | string | e.g. `Dashboard`, `SYSTEM`, `SYSTEM CONFIGURATION`, `ACCOUNT` |
| `label` | string | e.g. "Role & Permissions", "Profile" |
| `route` | string | frontend route path this item links to |
| `requiredModule` / `requiredAction` | reference into the matrix's enums | the grant that makes this item visible |
| `order` | integer | position within its section **and** the section's position in the menu: the catalogue is sorted by `order` and a section appears where its first item sits. Fixed bands (FR-008a): Dashboard 10, MASTER DATA 20-29, OPERATIONS 30-34, MY ATTENDANCE 35-39, SYSTEM / SYSTEM CONFIGURATION 40-49, AUDIT 50-59, ACCOUNT 90-99 |

At this spec's scope the catalog holds exactly: Dashboard, under section "Dashboard" (Admin,
Director, Manager, Teacher only — System does not use this section); Dashboard, under section
"SYSTEM DASHBOARD" (System only, per Constitution Principle IV); Role & Permissions
(ADMIN/DIRECTOR/SYSTEM only, under SYSTEM or SYSTEM CONFIGURATION per role); and Profile/My Profile
(all roles, under ACCOUNT). Later specs append entries; this feature's resolver logic does not
change shape when they do.

## Access Model (resolved per request, not persisted)

The response shape returned by the access-model endpoint (see `contracts/access-model-api.md`).

| Field | Type | Notes |
|---|---|---|
| `roles` | list of the 5-role enum | the signed-in user's roles, as established by spec 001 |
| `navigation` | list of sections, each with its authorized items | built by filtering `NavigationCatalog` through the union of the user's roles' grants; empty sections omitted (FR-008) |
| `actions` | map of module → authorized actions | union across the user's roles (Constitution Principle II: "union of their roles' grants") |
| `dataScope` | map of module → one of `ORG_WIDE` / `ASSIGNED` / `OWN` / `NONE` | carried per FR-005/FR-016; only `DASHBOARD` is populated meaningfully at this stage |

Computed fresh at sign-in and at each session renewal (research.md §7); never persisted server-side
beyond the source-of-truth matrix rows it was computed from.

## Theme Preference (client-side only, not persisted server-side)

| Field | Type | Notes |
|---|---|---|
| `mode` | `light` \| `dark` | stored in `localStorage`, keyed per device/browser (research.md §6) |

No backend entity. Read before first paint; falls back to a fixed default when storage is
unavailable (edge case in spec.md).

## Relationships

```text
User (spec 001, identity.user)
  └─ holds ─> Role (fixed enum, no table of its own)
                └─ referenced by ─> Permission Matrix Entry (role, module, action, granted)
                                       └─ filters ─> Navigation Item (code catalog)
                                                        └─ resolved into ─> Access Model (per request)
```

No new relationship is added to the `User` entity itself; this feature reads the roles spec 001
already attaches to the signed-in principal.
