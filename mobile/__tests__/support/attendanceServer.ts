import type {
  AttendanceCalendar,
  DayHistoryEntry,
  MarkRequest,
  StatusCode,
  TeacherGridRow,
  TeacherMonthView,
} from "../../src/api/attendanceApi";
import { calendar, gridPage, mark, monthView, statusCodes } from "./attendanceFixtures";
import type { FakeReply, FakeServer } from "./fakeServer";

const BASE = "/api/v1/attendance";

export interface AttendanceFake {
  /** Marks saved by the Teacher through `PUT /me/marks/{date}`, keyed by date. */
  mine: Map<string, MarkRequest>;
  /** Marks saved by a Manager, keyed by `teacherId/date`. */
  teachers: Map<string, MarkRequest>;
  cleared: string[];
  /** Replies to make marks fail: `reply(date)` returns a refusal, or undefined to accept. */
  refuseMine?: (date: string, body: MarkRequest) => FakeReply | undefined;
  refuseTeacher?: (teacherId: string, date: string, body: MarkRequest) => FakeReply | undefined;
  /** Month views the server returns, by month (own) or `teacherId/month`. */
  ownMonth: (month: string) => TeacherMonthView | FakeReply;
  teacherMonth: (teacherId: string, month: string) => TeacherMonthView | FakeReply;
  statuses: StatusCode[];
  calendar: AttendanceCalendar;
  grid: TeacherGridRow[];
  histories: Record<string, DayHistoryEntry[]>;
}

const isReply = (v: unknown): v is FakeReply => typeof v === "object" && v !== null && "status" in v && !("days" in v);

/**
 * Registers the attendance routes of spec 008 on a FakeServer. Saves are applied to the returned
 * state, so a month re-fetched after a save shows the new mark exactly as the real server would.
 */
export function installAttendance(server: FakeServer, over: Partial<AttendanceFake> = {}): AttendanceFake {
  const state: AttendanceFake = {
    mine: new Map(),
    teachers: new Map(),
    cleared: [],
    ownMonth: (month) => monthView(month),
    teacherMonth: (teacherId, month) => monthView(month, { teacherId, viewer: "SUPERVISOR" }),
    statuses: statusCodes(),
    calendar: calendar(),
    grid: [],
    histories: {},
    ...over,
  };

  const own = (month: string): TeacherMonthView | FakeReply => {
    const base = state.ownMonth(month);
    if (isReply(base)) return base;
    // Apply marks saved during the test over the base month.
    const days = base.days.map((day) => {
      const saved = state.mine.get(day.date);
      if (!saved) return day;
      const label = state.statuses.find((s) => s.shortCode === saved.statusCode);
      return {
        ...day,
        state: "MARKED" as const,
        mark: mark({
          date: day.date,
          code: saved.statusCode,
          codeName: label?.name ?? saved.statusCode,
          category: label?.category ?? "WORKED",
          dayValue: saved.dayValue,
          note: saved.note ?? null,
          version: (day.mark?.version ?? 0) + 1,
        }),
      };
    });
    return { ...base, days };
  };

  server.on(`GET ${BASE}/me`, (request) => {
    const view = own(request.query.month);
    return isReply(view) ? view : { status: 200, body: view };
  });
  server.on(`PUT ${BASE}/me/marks/{date}`, (request) => {
    const body = request.body as MarkRequest;
    const refusal = state.refuseMine?.(request.params.date, body);
    if (refusal) return refusal;
    state.mine.set(request.params.date, body);
    return { status: 200, body: mark({ date: request.params.date, code: body.statusCode, dayValue: body.dayValue }) };
  });
  server.on(`GET ${BASE}/status-codes`, () => ({ status: 200, body: state.statuses }));
  server.on(`GET ${BASE}/calendar`, () => ({ status: 200, body: state.calendar }));
  server.on(`GET ${BASE}/teacher-grid`, (request) => {
    const query = (request.query.query ?? "").toLowerCase();
    const page = Number(request.query.page ?? 0);
    const size = Number(request.query.size ?? 25);
    const filtered = state.grid.filter((row) => row.name.toLowerCase().includes(query));
    return {
      status: 200,
      body: gridPage(filtered.slice(page * size, page * size + size), {
        month: request.query.month,
        page,
        size,
        totalElements: filtered.length,
      }),
    };
  });
  server.on(`GET ${BASE}/teachers/{id}`, (request) => {
    const view = state.teacherMonth(request.params.id, request.query.month);
    if (isReply(view)) return view;
    const days = view.days.map((day) => {
      const saved = state.teachers.get(`${request.params.id}/${day.date}`);
      if (saved) {
        const label = state.statuses.find((s) => s.shortCode === saved.statusCode);
        return {
          ...day,
          state: "MARKED" as const,
          mark: mark({
            date: day.date,
            code: saved.statusCode,
            codeName: label?.name ?? saved.statusCode,
            category: label?.category ?? "WORKED",
            dayValue: saved.dayValue,
            note: saved.note ?? null,
            setByKind: "SUPERVISOR",
            setByName: "Manoj",
            version: (day.mark?.version ?? 0) + 1,
          }),
        };
      }
      if (state.cleared.includes(`${request.params.id}/${day.date}`)) {
        return { ...day, state: "UNMARKED" as const, mark: null };
      }
      return day;
    });
    return { status: 200, body: { ...view, days } };
  });
  server.on(`PUT ${BASE}/teachers/{id}/marks/{date}`, (request) => {
    const body = request.body as MarkRequest;
    const refusal = state.refuseTeacher?.(request.params.id, request.params.date, body);
    if (refusal) return refusal;
    state.cleared = state.cleared.filter((key) => key !== `${request.params.id}/${request.params.date}`);
    state.teachers.set(`${request.params.id}/${request.params.date}`, body);
    return { status: 200, body: mark({ date: request.params.date, code: body.statusCode, setByKind: "SUPERVISOR" }) };
  });
  server.on(`DELETE ${BASE}/teachers/{id}/marks/{date}`, (request) => {
    const refusal = state.refuseTeacher?.(request.params.id, request.params.date, {
      statusCode: "",
      dayValue: 1,
    });
    if (refusal) return refusal;
    state.teachers.delete(`${request.params.id}/${request.params.date}`);
    state.cleared.push(`${request.params.id}/${request.params.date}`);
    return { status: 204 };
  });
  server.on(`GET ${BASE}/teachers/{id}/marks/{date}/history`, (request) => ({
    status: 200,
    body: state.histories[`${request.params.id}/${request.params.date}`] ?? [],
  }));

  return state;
}
