import {
  clearTeacherMark,
  getCalendar,
  getDayHistory,
  getMyMonth,
  getTeacherGrid,
  getTeacherMonth,
  listStatusCodes,
  saveMyMark,
  saveTeacherMark,
} from "../../src/api/attendanceApi";
import { installLocationHeaders } from "../../src/location/LocationGate";
import { setAccessToken } from "../../src/security/memoryToken";
import { installAttendance } from "../support/attendanceServer";
import { newServer, type FakeServer } from "../support/fakeServer";

/**
 * Spec 019 T035: the app's attendance calls speak exactly the contract in
 * specs/019-mobile-attendance/contracts/mobile-attendance-screens.md (spec 008's API, unchanged).
 */
let server: FakeServer;
let removeLocation: () => void;

beforeEach(() => {
  server = newServer();
  installAttendance(server);
  setAccessToken("access-1");
  removeLocation = installLocationHeaders();
});

afterEach(() => {
  removeLocation();
  setAccessToken(null);
});

const last = () => server.calls[server.calls.length - 1];

describe("each attendance call", () => {
  it("GET /me?month=YYYY-MM", async () => {
    await getMyMonth("2026-10");
    expect(last()).toMatchObject({ method: "GET", path: "/api/v1/attendance/me", query: { month: "2026-10" } });
  });

  it("PUT /me/marks/{date} with the mark in the body", async () => {
    await saveMyMark("2026-10-05", { statusCode: "P", dayValue: 1, note: "n", version: 2 });
    expect(last()).toMatchObject({
      method: "PUT",
      path: "/api/v1/attendance/me/marks/2026-10-05",
      body: { statusCode: "P", dayValue: 1, note: "n", version: 2 },
    });
  });

  it("GET /status-codes with only the active codes", async () => {
    await listStatusCodes();
    expect(last()).toMatchObject({
      method: "GET",
      path: "/api/v1/attendance/status-codes",
      query: { activeOnly: "true" },
    });
  });

  it("GET /calendar", async () => {
    await getCalendar();
    expect(last()).toMatchObject({ method: "GET", path: "/api/v1/attendance/calendar" });
  });

  it("GET /teacher-grid with month, search, page and size", async () => {
    await getTeacherGrid({ month: "2026-10", query: " asha ", page: 2, size: 25 });
    expect(last()).toMatchObject({
      method: "GET",
      path: "/api/v1/attendance/teacher-grid",
      query: { month: "2026-10", query: "asha", page: "2", size: "25" },
    });
  });

  it("leaves the search out when it is blank and defaults to page 0 of 25", async () => {
    await getTeacherGrid({ month: "2026-10", query: "   " });
    expect(last().query).toEqual({ month: "2026-10", page: "0", size: "25" });
  });

  it("GET /teachers/{id}?month=", async () => {
    await getTeacherMonth("t-1", "2026-09");
    expect(last()).toMatchObject({
      method: "GET",
      path: "/api/v1/attendance/teachers/t-1",
      query: { month: "2026-09" },
    });
  });

  it("PUT and DELETE /teachers/{id}/marks/{date}", async () => {
    await saveTeacherMark("t-1", "2026-10-01", { statusCode: "A", dayValue: 0.5 });
    expect(last()).toMatchObject({
      method: "PUT",
      path: "/api/v1/attendance/teachers/t-1/marks/2026-10-01",
      body: { statusCode: "A", dayValue: 0.5 },
    });
    await clearTeacherMark("t-1", "2026-10-01");
    expect(last()).toMatchObject({ method: "DELETE", path: "/api/v1/attendance/teachers/t-1/marks/2026-10-01" });
    expect(last().body).toBeUndefined();
  });

  it("GET /teachers/{id}/marks/{date}/history", async () => {
    await getDayHistory("t-1", "2026-10-02");
    expect(last()).toMatchObject({
      method: "GET",
      path: "/api/v1/attendance/teachers/t-1/marks/2026-10-02/history",
    });
  });
});

describe("every attendance call carries the client identity, the location and the sign-in", () => {
  const calls: [string, () => Promise<unknown>][] = [
    ["getMyMonth", () => getMyMonth("2026-10")],
    ["saveMyMark", () => saveMyMark("2026-10-05", { statusCode: "P", dayValue: 1 })],
    ["listStatusCodes", () => listStatusCodes()],
    ["getCalendar", () => getCalendar()],
    ["getTeacherGrid", () => getTeacherGrid({ month: "2026-10" })],
    ["getTeacherMonth", () => getTeacherMonth("t-1", "2026-10")],
    ["saveTeacherMark", () => saveTeacherMark("t-1", "2026-10-05", { statusCode: "P", dayValue: 1 })],
    ["clearTeacherMark", () => clearTeacherMark("t-1", "2026-10-05")],
    ["getDayHistory", () => getDayHistory("t-1", "2026-10-05")],
  ];

  it.each(calls)("%s", async (_name, call) => {
    await call();
    const { headers } = last();
    expect(headers["X-HLS-Client"]).toMatch(/^android\/\d+\.\d+\.\d+$/);
    expect(headers.Authorization).toBe("Bearer access-1");
    // Either the position or the reason there is none, never neither.
    expect(headers["X-HLS-Location"] ?? headers["X-HLS-Location-Status"]).toBeTruthy();
  });

  it("sends month parameters as YYYY-MM and dates as YYYY-MM-DD", async () => {
    await getMyMonth("2026-10");
    await getTeacherMonth("t-1", "2026-10");
    await saveMyMark("2026-10-05", { statusCode: "P", dayValue: 1 });
    for (const call of server.calls.filter((c) => c.path.startsWith("/api/v1/attendance"))) {
      if (call.query.month) expect(call.query.month).toMatch(/^\d{4}-\d{2}$/);
      const date = call.path.split("/").find((part) => /^\d{4}-\d{2}-\d{2}$/.test(part));
      if (call.path.includes("/marks/")) expect(date).toBeDefined();
    }
  });
});
