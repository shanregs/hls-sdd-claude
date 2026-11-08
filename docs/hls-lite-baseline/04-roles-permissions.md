# Roles and permissions

## Roles

| Role | Who | Notes |
|------|-----|-------|
| Admin | Office administrator (one person is enough) | Runs payroll and billing. Also an employee on payroll. |
| Director | Owner or head | Approvals, oversight, acknowledges all pay. Also on payroll. |
| Manager | Zone Manager. The two names are interchangeable. | Sees all Schools in their Zones; is the named Manager of assigned Schools (approvals). Covers substitutions and observes Teachers. |
| Teacher | Classroom Teacher | Own data only. |
| Content Creator | Creates teaching content | Own items. On payroll. |
| System Engineer | Technical operator | Platform and health. On payroll. No HR or money data by default; can manage users and roles (A-13). |

Substitutes are people but not users: no login, no role.
A user may hold more than one role; permissions are the union.

## Scope rules (in code, not editable)

| Role | Data scope |
|------|------------|
| Admin, Director | All data |
| Manager | All Schools in the Zones they belong to, the Teachers placed there, and the Zone's Substitute pool. Approvals (MoU, transfer, Teacher expense) go to the named Manager of the School. |
| Teacher | Own records only |
| Content Creator | Own content items |
| System Engineer | No business data; platform data only |

## Editable matrix

- Permissions are a fixed list of keys defined in code (for example `attendance.mark_own`, `payroll.run`). Which role holds which key is data in `role_permission`, edited on an Admin page.
- Changes are audited and apply on the next request.
- Guardrail: Admin cannot lose `permissions.manage`.
- Proposed guardrail (confirm): System Engineer manages users and roles but cannot be granted payroll, salary, billing or HR-record permissions, so technical access cannot widen into money data.
- Scope is applied after the permission check; the matrix never widens scope.
- The matrix below is the seed from migration.

| Permission group | Admin | Director | Manager | Teacher | Content Creator | System Engineer |
|---|---|---|---|---|---|---|
| Users, roles, permission matrix | Manage | View | - | - | - | Manage |
| Settings | Business settings | View | - | - | - | Technical settings |
| Audit log | View | View | - | - | - | View |
| System health, metrics | View | View | - | - | - | View |
| Zones, Schools, designations | Edit | Edit | Zone Schools: view, edit contacts | - | - | - |
| Employees and salary history | Edit | Edit | View own Teachers (no salary) | Own profile | - | - |
| Contracts, MoU document | Create | Create, approve | Zone Schools: view, map Teachers; approve if named Manager | - | - | - |
| Placements | Map | Map | Map (Zone Schools) | View own | - | - |
| Holidays, weekly offs | Edit | Edit | View | View | - | - |
| Teacher attendance | Any, lock | Any | Zone Schools: mark, correct | Own: mark (today + 3 days) | - | - |
| Leave (Teacher) | Decide, record | Decide, record | Decide in scope, record | Apply, cancel | - | - |
| Manager check-in | Correct, lock | View | Own | - | - | - |
| Manager leave | Decide | Decide | Apply | - | - | - |
| Substitution | Assign, mark days | Assign, mark days | Zone Schools: assign, mark | - | - | - |
| Recruitment | Yes | Yes, issues offers | Create drives, candidates | - | - | - |
| Induction attendance, sign-off | Sign off | Sign off | Mark attendance | - | - | - |
| Training sessions | Create, view | Create, view | Create, mark attendance | View, attend | - | - |
| Prospects and activities | View | Yes, approves Final Stage | Own | - | - | - |
| Tasks | Create, verify | Create, verify | Own tasks | - | - | - |
| Observations | View | View | Create (Zone Schools) | - | - | - |
| Transfers | Raise | Raise, approve | Raise; approve for leaving School | - | - | - |
| Payroll run | Run, finalise | Acknowledge, view | - | - | - | - |
| Own payslip | Yes | Yes | Yes | Yes | Yes | Yes |
| Expenses | Raise own, approve per hierarchy | Raise own, approve per hierarchy | Raise own, approve Teacher claims | Raise own | Raise own | Raise own |
| Billing and collections | Raise, record | View | Zone Schools: view dues | - | - | - |
| Reports and dashboard | All | All incl. margin | Own scope; no margin, no payroll | Own | - | Health only |
| Content | Review | Review | Create | - | Create, submit own | - |

Notes
- Approval of one's own item is never allowed regardless of the matrix. The hierarchy in `03-business-rules.md` section 13 picks the next approver.
- Admin's expense claims route to the Director.
- Staff (Admin, Director, System Engineer, Content Creator) have no attendance or leave in the system (A-03).
