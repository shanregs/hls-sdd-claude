import { linkTarget } from "../../src/notifications/linkTarget";
import { SERVER_TEXTS } from "../support/notificationsFixtures";

describe("linkTarget (spec 021 FR-006)", () => {
  it.each([
    ["/leave/history", { route: "/leave/history" }],
    ["/operations/leave", { route: "/operations/leave" }],
    ["/my-attendance", { route: "/my-attendance" }],
    ["/my-attendance?month=2026-10", { route: "/my-attendance", month: "2026-10" }],
    ["/my-attendance?month=2025-12", { route: "/my-attendance", month: "2025-12" }],
  ])("maps %s to its screen", (link, expected) => {
    expect(linkTarget(link)).toEqual(expected);
  });

  it.each(["/my-attendance?month=2026-13", "/my-attendance?month=2026-00", "/my-attendance?month=2026-1", "/my-attendance?month=abc", "/my-attendance?month="])(
    "opens My Attendance without a month for the malformed %s",
    (link) => {
      expect(linkTarget(link)).toEqual({ route: "/my-attendance" });
    },
  );

  it.each(["/my-attendance?month=2026-10&x=1", "/my-attendance?x=1", "/leave/history?x=1", "/operations/leave?month=2026-10"])(
    "does not follow %s: any other query parameter",
    (link) => {
      expect(linkTarget(link)).toBeNull();
    },
  );

  it.each([null, undefined, "", "leave/history", "https://example.com/leave/history", "//evil.example", "/unknown", "/leave/history#top", "/my-attendance/history"])(
    "does not follow %p",
    (link) => {
      expect(linkTarget(link as string | null)).toBeNull();
    },
  );

  it("gives a target or null for every text the server writes, and never throws", () => {
    for (const text of SERVER_TEXTS) {
      expect(() => linkTarget(text.link ?? null)).not.toThrow();
      expect(linkTarget(text.link ?? null)?.route).toBe((text.link ?? "").split("?")[0]);
    }
  });
});
