import { statusLook } from "../../src/leave/statusPresentation";

describe("statusLook", () => {
  it("labels each status", () => {
    expect(statusLook("PENDING", null, "light").label).toBe("Pending");
    expect(statusLook("APPROVED", null, "light").label).toBe("Approved");
    expect(statusLook("REJECTED", null, "light").label).toBe("Rejected");
    expect(statusLook("CANCELLED", null, "light").label).toBe("Cancelled");
  });

  it("says who cancelled, as the server reports it", () => {
    expect(statusLook("CANCELLED", "TEACHER", "light").spoken).toBe("Cancelled by the teacher");
    expect(statusLook("CANCELLED", "SUPERVISOR", "light").spoken).toBe("Cancelled by the supervisor");
    expect(statusLook("CANCELLED", null, "light").spoken).toBe("Cancelled");
    expect(statusLook("APPROVED", "TEACHER", "light").spoken).toBe("Approved");
  });

  it("uses a different colour for each status and different ones in dark mode", () => {
    const statuses = ["PENDING", "APPROVED", "REJECTED", "CANCELLED"] as const;
    expect(new Set(statuses.map((s) => statusLook(s, null, "light").background)).size).toBe(4);
    for (const s of statuses) {
      expect(statusLook(s, null, "dark").background).not.toBe(statusLook(s, null, "light").background);
      expect(statusLook(s, null, "dark").textColor).not.toBe(statusLook(s, null, "light").textColor);
    }
  });
});
