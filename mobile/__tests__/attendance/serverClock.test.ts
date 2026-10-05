import { api } from "../../src/api/httpClient";
import { recordServerDate, resetServerClock, serverNow } from "../../src/attendance/serverClock";

afterEach(() => {
  resetServerClock();
  jest.restoreAllMocks();
});

describe("server clock", () => {
  it("falls back to the phone clock when no header was seen", () => {
    const before = Date.now();
    const now = serverNow().getTime();
    expect(now).toBeGreaterThanOrEqual(before);
    expect(now - before).toBeLessThan(1000);
  });

  it("applies the offset between the Date header and the phone clock", () => {
    jest.spyOn(Date, "now").mockReturnValue(Date.parse("2026-10-04T10:00:00Z"));
    recordServerDate("Tue, 06 Oct 2026 10:00:00 GMT");

    expect(serverNow().toISOString()).toBe("2026-10-06T10:00:00.000Z");
  });

  it("ignores a missing or malformed header", () => {
    jest.spyOn(Date, "now").mockReturnValue(Date.parse("2026-10-04T10:00:00Z"));
    recordServerDate("Tue, 06 Oct 2026 10:00:00 GMT");
    recordServerDate(undefined);
    recordServerDate("not a date");

    expect(serverNow().toISOString()).toBe("2026-10-06T10:00:00.000Z");
  });

  it("learns the offset from a real response through the HTTP client", async () => {
    jest.spyOn(Date, "now").mockReturnValue(Date.parse("2026-10-04T10:00:00Z"));
    globalThis.fetch = jest.fn(
      async () => new Response("{}", { status: 200, headers: { Date: "Wed, 07 Oct 2026 10:00:00 GMT" } }),
    ) as unknown as typeof fetch;

    await api.get("/api/v1/anything", { auth: false });

    expect(serverNow().toISOString()).toBe("2026-10-07T10:00:00.000Z");
  });

  it("reads the attendance refusal text from `reason`", async () => {
    globalThis.fetch = jest.fn(
      async () => new Response(JSON.stringify({ reason: "This month is locked." }), { status: 409 }),
    ) as unknown as typeof fetch;

    await expect(api.put("/api/v1/attendance/me/marks/2026-10-01", {}, { auth: false })).rejects.toMatchObject({
      status: 409,
      message: "This month is locked.",
    });
  });
});
