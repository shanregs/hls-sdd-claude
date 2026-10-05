# Data Model: Mobile Leave

No data is stored by this feature, on the phone or on the server. These are the shapes the app reads from
the leave endpoints of spec 009 (copied from the code, see [research.md](research.md) §2) and the
in-memory draft it builds. The app computes none of the figures or permissions they carry.

## Leave Type (`GET /api/v1/me/leave/types`)

| Field | Type | Notes |
| ----- | ---- | ----- |
| `id` | string (uuid) | sent as `leaveTypeId` |
| `code` | string | informational |
| `name` | string | shown to the Teacher; the list is the server's active types, none hard-coded |

## Leave Request (the server's `LeaveView`)

| Field | Type | Notes |
| ----- | ---- | ----- |
| `id` | string (uuid) | |
| `teacherId`, `teacherName` | string | shown on Leave Management |
| `schoolId`, `schoolName` | string | School of the first working day |
| `leaveType` | string | the type's name |
| `firstDate`, `lastDate` | string `YYYY-MM-DD` | shown as DD/MM/YYYY |
| `halfDayStart`, `halfDayEnd` | boolean | |
| `workingDays` | number | e.g. 2.5; shown as sent |
| `reason` | string | the Teacher's reason |
| `status` | `PENDING` \| `APPROVED` \| `REJECTED` \| `CANCELLED` | |
| `decidedByName`, `decidedAt`, `decisionNote` | string \| null | decision details; the note is the approval note or the rejection or revoke reason |
| `cancelledBy` | string \| null | `"TEACHER"` when the Teacher cancelled |
| `createdAt` | instant | |
| `version` | number | sent back with every decision |
| `allowedActions` | string[] | `CANCEL` (Teacher); `APPROVE`, `REJECT`, `REVOKE` (supervisor); empty when none |

Status transitions are the server's: Pending to Approved, Rejected or Cancelled; Approved to Cancelled (by
revoke, or by the Teacher before the first day). The app shows a button only for an action in
`allowedActions`.

## Pages

| Shape | Fields |
| ----- | ------ |
| Teacher list | `content: LeaveRequest[]`, `page`, `size`, `totalElements` |
| Supervisor list | `content: LeaveRequest[]`, `page`, `size`, `totalElements`, `pendingCount` |
| Supervisor detail | `request: LeaveRequest`, `days: PreviewDay[]` (the days approving would mark), `problems: string[]` (Pending only) |

## Preview (`POST /api/v1/me/leave/preview`, never stored)

| Field | Type | Notes |
| ----- | ---- | ----- |
| `workingDays` | number | the server's total |
| `days` | `{ date, value }[]` | counted days only; `value` is 1 or 0.5 |
| `problems` | string[] | every reason the draft cannot be submitted |

## Draft (in memory on Apply Leave only)

| Field | Rule |
| ----- | ---- |
| `leaveTypeId` | required; chosen from the server's list |
| `firstDate`, `lastDate` | required; `lastDate` not before `firstDate` (the only date rule the app checks) |
| `halfDayStart`, `halfDayEnd` | optional booleans; sent as false when unset |
| `reason` | up to 500 characters; required by the server (research §4) |
| `preview` | the last preview; cleared on any change to the fields above |

Request body for preview and submit: `{ leaveTypeId, firstDate, lastDate, halfDayStart, halfDayEnd, reason }`.
Decision bodies: approve `{ note, version }`; reject and revoke `{ reason, version }`.

## Validation rules in the app (everything else is the server's)

- A leave type and both dates are chosen; the last date is not before the first.
- Reject and Revoke text is not empty and is at most 500 characters; the Approve note is at most 500.
- Nothing is sent while a previous send is in progress.
