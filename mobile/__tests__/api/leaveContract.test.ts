import {
  approveLeave,
  cancelMyLeave,
  getLeave,
  listLeave,
  listLeaveTypes,
  listMyLeave,
  previewLeave,
  rejectLeave,
  revokeLeave,
  submitLeave,
  type LeaveDraftBody,
} from "../../src/api/leaveApi";
import { installLocationHeaders } from "../../src/location/LocationGate";
import { setAccessToken } from "../../src/security/memoryToken";
import { newServer, type FakeServer } from "../support/fakeServer";
import { leaveRequest } from "../support/leaveFixtures";
import { installLeave } from "../support/leaveServer";

/**
 * Spec 020 T033: the leave calls of the app speak exactly the contract in
 * specs/020-mobile-leave/contracts/mobile-leave-screens.md (the API of spec 009, unchanged).
 */
let server: FakeServer;
let removeLocation: () => void;

const body: LeaveDraftBody = {
  leaveTypeId: "type-casual",
  firstDate: "2026-10-12",
  lastDate: "2026-10-14",
  halfDayStart: false,
  halfDayEnd: true,
  reason: "Pongal travel",
};

beforeEach(() => {
  server = newServer();
  installLeave(server, { requests: [leaveRequest({ id: "r1", as: "supervisor" })] });
  setAccessToken("access-1");
  removeLocation = installLocationHeaders();
});

afterEach(() => {
  removeLocation();
  setAccessToken(null);
});

const last = () => server.calls[server.calls.length - 1];

describe("each leave call", () => {
  it("GET /me/leave/types", async () => {
    await listLeaveTypes();
    expect(last()).toMatchObject({ method: "GET", path: "/api/v1/me/leave/types" });
  });

  it("POST /me/leave/preview and POST /me/leave carry the draft body", async () => {
    await previewLeave(body);
    expect(last()).toMatchObject({ method: "POST", path: "/api/v1/me/leave/preview", body });
    await submitLeave(body);
    expect(last()).toMatchObject({ method: "POST", path: "/api/v1/me/leave", body });
  });

  it("GET /me/leave with upper-case status, page and size, and no status for all", async () => {
    await listMyLeave({ status: "APPROVED", page: 2, size: 25 });
    expect(last()).toMatchObject({
      method: "GET",
      path: "/api/v1/me/leave",
      query: { status: "APPROVED", page: "2", size: "25" },
    });
    await listMyLeave();
    expect(last().query).toEqual({ page: "0", size: "25" });
  });

  it("POST /me/leave/{id}/cancel with no body", async () => {
    await cancelMyLeave("r1").catch(() => undefined);
    expect(last()).toMatchObject({ method: "POST", path: "/api/v1/me/leave/r1/cancel" });
    expect(last().body).toBeUndefined();
  });

  it("GET /leave with status, page and size", async () => {
    await listLeave({ status: "PENDING", size: 1 });
    expect(last()).toMatchObject({
      method: "GET",
      path: "/api/v1/leave",
      query: { status: "PENDING", page: "0", size: "1" },
    });
  });

  it("GET /leave/{id}", async () => {
    await getLeave("r1");
    expect(last()).toMatchObject({ method: "GET", path: "/api/v1/leave/r1" });
  });

  it("POST approve with {note, version}, reject and revoke with {reason, version}", async () => {
    await approveLeave("r1", { note: "ok", version: 0 });
    expect(last()).toMatchObject({ method: "POST", path: "/api/v1/leave/r1/approve", body: { note: "ok", version: 0 } });
    await rejectLeave("r1", { reason: "no", version: 1 }).catch(() => undefined);
    expect(last()).toMatchObject({ method: "POST", path: "/api/v1/leave/r1/reject", body: { reason: "no", version: 1 } });
    await revokeLeave("r1", { reason: "no", version: 1 }).catch(() => undefined);
    expect(last()).toMatchObject({ method: "POST", path: "/api/v1/leave/r1/revoke", body: { reason: "no", version: 1 } });
  });
});

describe("every leave call carries the client identity, the location and the sign-in", () => {
  const calls: [string, () => Promise<unknown>][] = [
    ["listLeaveTypes", () => listLeaveTypes()],
    ["previewLeave", () => previewLeave(body)],
    ["submitLeave", () => submitLeave(body)],
    ["listMyLeave", () => listMyLeave()],
    ["cancelMyLeave", () => cancelMyLeave("r1").catch(() => undefined)],
    ["listLeave", () => listLeave()],
    ["getLeave", () => getLeave("r1")],
    ["approveLeave", () => approveLeave("r1", { version: 0 })],
    ["rejectLeave", () => rejectLeave("r1", { reason: "x", version: 0 }).catch(() => undefined)],
    ["revokeLeave", () => revokeLeave("r1", { reason: "x", version: 0 }).catch(() => undefined)],
  ];

  it.each(calls)("%s", async (_name, call) => {
    await call();
    const { headers } = last();
    expect(headers["X-HLS-Client"]).toMatch(/^android\/\d+\.\d+\.\d+$/);
    expect(headers.Authorization).toBe("Bearer access-1");
    expect(headers["X-HLS-Location"] ?? headers["X-HLS-Location-Status"]).toBeTruthy();
  });

  it("sends dates as YYYY-MM-DD", async () => {
    await previewLeave(body);
    const sent = last().body as LeaveDraftBody;
    expect(sent.firstDate).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(sent.lastDate).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });
});
