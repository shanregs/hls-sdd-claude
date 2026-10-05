import type {
  LeaveAction,
  LeaveDetail,
  LeaveDraftBody,
  LeavePreview,
  LeaveRequest,
  LeaveStatus,
  LeaveType,
  PreviewDay,
} from "../../src/api/leaveApi";
import type { FakeReply, FakeServer } from "./fakeServer";
import { defaultActions, leaveTypes, teacherPage, supervisorPage } from "./leaveFixtures";

const ME = "/api/v1/me/leave";
const BASE = "/api/v1/leave";

export interface Decision {
  action: "approve" | "reject" | "revoke";
  id: string;
  body: { note?: string; reason?: string; version: number };
}

export interface LeaveFake {
  types: LeaveType[];
  /** Every request on the server; newest first. */
  requests: LeaveRequest[];
  /** The signed-in Teacher's own record, for the Teacher routes. */
  ownTeacherId: string;
  /** Which requests the signed-in supervisor may see (default: all). The server decides scope, not the app. */
  inScope: (request: LeaveRequest) => boolean;
  /** Makes `GET /me/leave/types` and the other Teacher routes answer 404 as for an unlinked profile. */
  profileMissing: boolean;
  /** Replaces the default preview (every date counted except Sundays, empty reason a problem). */
  previewReply?: (body: LeaveDraftBody) => LeavePreview | FakeReply;
  /** Returns a refusal to make a submit fail, or undefined to accept it. */
  submitRefusal?: (body: LeaveDraftBody) => FakeReply | undefined;
  cancelRefusal?: (request: LeaveRequest) => FakeReply | undefined;
  decisionRefusal?: (decision: Decision, request: LeaveRequest) => FakeReply | undefined;
  /** The days and problems the supervisor detail shows for a request. */
  detailOf?: (request: LeaveRequest) => Pick<LeaveDetail, "days" | "problems">;
  /** Calls the tests inspect. */
  submitted: LeaveDraftBody[];
  cancelled: string[];
  decisions: Decision[];
}

const pad = (n: number) => String(n).padStart(2, "0");

export function datesBetween(first: string, last: string): string[] {
  const out: string[] = [];
  const end = Date.parse(`${last}T00:00:00Z`);
  for (let t = Date.parse(`${first}T00:00:00Z`); t <= end; t += 86_400_000) {
    const d = new Date(t);
    out.push(`${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())}`);
  }
  return out;
}

function defaultPreview(body: LeaveDraftBody): LeavePreview {
  const dates = datesBetween(body.firstDate, body.lastDate);
  const counted = dates.filter((d) => new Date(`${d}T00:00:00Z`).getUTCDay() !== 0);
  const days: PreviewDay[] = counted.map((date, index) => ({
    date,
    value:
      (body.halfDayStart && index === 0) || (body.halfDayEnd && index === counted.length - 1) ? 0.5 : 1,
  }));
  return {
    workingDays: days.reduce((sum, d) => sum + d.value, 0),
    days,
    problems: body.reason.trim() === "" ? ["Give a reason."] : [],
  };
}

const isReply = (v: unknown): v is FakeReply => typeof v === "object" && v !== null && "status" in v;

const NOT_FOUND: FakeReply = { status: 404, body: { reason: "Leave request not found." } };

/**
 * Registers the leave routes of spec 009 on a FakeServer. Submits, cancels and decisions are applied to the
 * returned state, so a list re-fetched afterwards shows the result exactly as the real server would.
 */
export function installLeave(server: FakeServer, over: Partial<LeaveFake> = {}): LeaveFake {
  const state: LeaveFake = {
    types: leaveTypes(),
    requests: [],
    ownTeacherId: "t-1",
    inScope: () => true,
    profileMissing: false,
    submitted: [],
    cancelled: [],
    decisions: [],
    ...over,
  };

  const missing: FakeReply = { status: 404, body: { reason: "Your profile has not been set up yet." } };
  const own = () => state.requests.filter((r) => r.teacherId === state.ownTeacherId);
  const scoped = () => state.requests.filter((r) => state.inScope(r));
  const byStatus = (list: LeaveRequest[], status: string | undefined, fallback: LeaveStatus | null) => {
    const wanted = status ? (status.toUpperCase() as LeaveStatus) : fallback;
    return wanted ? list.filter((r) => r.status === wanted) : list;
  };
  const slice = (list: LeaveRequest[], page: number, size: number) => list.slice(page * size, page * size + size);

  server.on(`GET ${ME}/types`, () => (state.profileMissing ? missing : { status: 200, body: state.types }));

  server.on(`POST ${ME}/preview`, (request) => {
    if (state.profileMissing) return missing;
    const body = request.body as LeaveDraftBody;
    const reply = state.previewReply ? state.previewReply(body) : defaultPreview(body);
    return isReply(reply) ? reply : { status: 200, body: reply };
  });

  server.on(`POST ${ME}`, (request) => {
    if (state.profileMissing) return missing;
    const body = request.body as LeaveDraftBody;
    const refusal = state.submitRefusal?.(body);
    if (refusal) return refusal;
    state.submitted.push(body);
    const computed = defaultPreview(body);
    const type = state.types.find((t) => t.id === body.leaveTypeId);
    const created: LeaveRequest = {
      id: `new-${state.submitted.length}`,
      teacherId: state.ownTeacherId,
      teacherName: "Tara",
      schoolId: "school-1",
      schoolName: "Demo School One",
      leaveType: type?.name ?? "Casual",
      firstDate: body.firstDate,
      lastDate: body.lastDate,
      halfDayStart: body.halfDayStart,
      halfDayEnd: body.halfDayEnd,
      workingDays: computed.workingDays,
      reason: body.reason.trim(),
      status: "PENDING",
      decidedByName: null,
      decidedAt: null,
      decisionNote: null,
      cancelledBy: null,
      createdAt: "2026-10-05T04:30:00Z",
      version: 0,
      allowedActions: ["CANCEL"],
    };
    state.requests = [created, ...state.requests];
    return { status: 201, body: created };
  });

  server.on(`GET ${ME}`, (request) => {
    if (state.profileMissing) return missing;
    const page = Number(request.query.page ?? 0);
    const size = Number(request.query.size ?? 25);
    const list = byStatus(own(), request.query.status, null);
    return { status: 200, body: teacherPage(slice(list, page, size), { page, size, totalElements: list.length }) };
  });

  server.on(`POST ${ME}/{id}/cancel`, (request) => {
    const found = own().find((r) => r.id === request.params.id);
    if (!found) return NOT_FOUND;
    const refusal = state.cancelRefusal?.(found);
    if (refusal) return refusal;
    if (!found.allowedActions.includes("CANCEL")) {
      return { status: 409, body: { reason: "This leave has already started. Ask your Manager to revoke it." } };
    }
    const updated: LeaveRequest = {
      ...found,
      status: "CANCELLED",
      cancelledBy: "TEACHER",
      allowedActions: [],
      version: found.version + 1,
    };
    state.cancelled.push(found.id);
    state.requests = state.requests.map((r) => (r.id === found.id ? updated : r));
    return { status: 200, body: updated };
  });

  server.on(`GET ${BASE}`, (request) => {
    const page = Number(request.query.page ?? 0);
    const size = Number(request.query.size ?? 25);
    const list = byStatus(scoped(), request.query.status, "PENDING");
    const pending = scoped().filter((r) => r.status === "PENDING").length;
    return {
      status: 200,
      body: supervisorPage(slice(list, page, size), pending, { page, size, totalElements: list.length }),
    };
  });

  server.on(`GET ${BASE}/{id}`, (request) => {
    const found = scoped().find((r) => r.id === request.params.id);
    if (!found) return NOT_FOUND;
    const extra = state.detailOf?.(found) ?? { days: [], problems: [] as string[] };
    const body: LeaveDetail = { request: found, ...extra };
    return { status: 200, body };
  });

  const decide = (action: Decision["action"]) => (request: { params: Record<string, string>; body: unknown }) => {
    const found = scoped().find((r) => r.id === request.params.id);
    if (!found) return NOT_FOUND;
    const body = request.body as Decision["body"];
    const decision: Decision = { action, id: found.id, body };
    const refusal = state.decisionRefusal?.(decision, found);
    if (refusal) return refusal;
    if (body.version !== found.version) return { status: 409, body: { reason: "This record was changed by someone else. Reload and try again." } };
    const allowed: LeaveAction = action === "approve" ? "APPROVE" : action === "reject" ? "REJECT" : "REVOKE";
    if (!found.allowedActions.includes(allowed)) {
      return { status: 409, body: { reason: `This request was already ${found.status.toLowerCase()}.` } };
    }
    state.decisions.push(decision);
    const status: LeaveStatus = action === "approve" ? "APPROVED" : action === "reject" ? "REJECTED" : "CANCELLED";
    const updated: LeaveRequest = {
      ...found,
      status,
      decidedByName: "Manoj",
      decidedAt: "2026-10-05T05:00:00Z",
      decisionNote: body.note ?? body.reason ?? null,
      version: found.version + 1,
      allowedActions: defaultActions(status, "supervisor"),
    };
    state.requests = state.requests.map((r) => (r.id === found.id ? updated : r));
    return { status: 200, body: updated };
  };
  server.on(`POST ${BASE}/{id}/approve`, decide("approve"));
  server.on(`POST ${BASE}/{id}/reject`, decide("reject"));
  server.on(`POST ${BASE}/{id}/revoke`, decide("revoke"));

  return state;
}
