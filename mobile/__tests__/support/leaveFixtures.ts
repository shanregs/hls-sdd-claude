import type {
  LeaveAction,
  LeaveDetail,
  LeavePreview,
  LeaveRequest,
  LeaveStatus,
  LeaveType,
  PreviewDay,
  SupervisorLeavePage,
  TeacherLeavePage,
} from "../../src/api/leaveApi";

/**
 * Builders that copy the real response shapes of spec 009 (specs/020-mobile-leave/data-model.md), so tests
 * exercise the same JSON the server sends.
 */

/** The actions the server offers for a status, as `LeaveViewFactory.allowed` does (dates aside). */
export function defaultActions(status: LeaveStatus, as: "teacher" | "supervisor"): LeaveAction[] {
  if (as === "teacher") return status === "PENDING" || status === "APPROVED" ? ["CANCEL"] : [];
  if (status === "PENDING") return ["APPROVE", "REJECT"];
  if (status === "APPROVED") return ["REVOKE"];
  return [];
}

let counter = 0;

export function leaveRequest(
  over: Partial<LeaveRequest> & { as?: "teacher" | "supervisor" } = {},
): LeaveRequest {
  const { as = "teacher", ...rest } = over;
  counter += 1;
  const status = rest.status ?? "PENDING";
  return {
    id: `l-${counter}`,
    teacherId: "t-1",
    teacherName: "Tara",
    schoolId: "school-1",
    schoolName: "Demo School One",
    leaveType: "Casual",
    firstDate: "2026-10-12",
    lastDate: "2026-10-14",
    halfDayStart: false,
    halfDayEnd: false,
    workingDays: 3,
    reason: "Family function",
    status,
    decidedByName: null,
    decidedAt: null,
    decisionNote: null,
    cancelledBy: null,
    createdAt: "2026-10-05T04:00:00Z",
    version: 0,
    allowedActions: defaultActions(status, as),
    ...rest,
  };
}

export const leaveTypes = (): LeaveType[] => [
  { id: "type-casual", code: "CASUAL", name: "Casual" },
  { id: "type-sick", code: "SICK", name: "Sick" },
  { id: "type-personal", code: "PERSONAL", name: "Personal" },
  { id: "type-other", code: "OTHER", name: "Other" },
];

export const previewDays = (dates: string[], half: string[] = []): PreviewDay[] =>
  dates.map((date) => ({ date, value: half.includes(date) ? 0.5 : 1 }));

export function preview(over: Partial<LeavePreview> = {}): LeavePreview {
  const days = over.days ?? previewDays(["2026-10-12", "2026-10-13", "2026-10-14"]);
  return {
    workingDays: days.reduce((sum, d) => sum + d.value, 0),
    days,
    problems: [],
    ...over,
  };
}

export function teacherPage(content: LeaveRequest[], over: Partial<TeacherLeavePage> = {}): TeacherLeavePage {
  return { content, page: 0, size: 25, totalElements: content.length, ...over };
}

export function supervisorPage(
  content: LeaveRequest[],
  pendingCount: number,
  over: Partial<SupervisorLeavePage> = {},
): SupervisorLeavePage {
  return { content, page: 0, size: 25, totalElements: content.length, pendingCount, ...over };
}

export function detail(request: LeaveRequest, days?: PreviewDay[], problems: string[] = []): LeaveDetail {
  return {
    request,
    days: days ?? previewDays(["2026-10-12", "2026-10-13", "2026-10-14"]),
    problems,
  };
}

/** The server's refusal texts used across the tests (spec 009 code, see research §6). */
export const SERVER_TEXT = {
  giveReason: "Give a reason.",
  overlap: "These dates overlap your pending request from 2026-10-12 to 2026-10-14.",
  lookback: "Leave can start at most 30 days in the past. Ask your Manager to record older days.",
  span: "A request can cover at most 90 days. Split it into two.",
  notPlaced: "You are not placed at a School on these dates, so you cannot apply for leave.",
  noWorkingDay: "None of these dates is a working day for you.",
  bothHalf: "A single working day can be a half day at the start or at the end, not both.",
  locked: "Attendance is locked for a month in this range (month locked: 2026-09).",
  stale: "This record was changed by someone else. Reload and try again.",
} as const;
