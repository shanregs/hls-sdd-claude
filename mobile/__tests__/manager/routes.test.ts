import { MANAGER_CHECK_IN_ROUTE, screenFor } from "../../src/access/screenRegistry";
import { buildMenu } from "../../src/access/useMenu";
import { managerFieldModel } from "../support/managerFixtures";

/** Spec 033 T012: the three routes the server offers a Manager under MY ATTENDANCE map to screens. */
describe("Manager routes", () => {
  it("maps the three routes of the server's navigation catalog to the three screens", () => {
    expect(screenFor("/manager-attendance/me")).toBe("managerCheckIn");
    expect(screenFor("/manager-attendance/me/month")).toBe("managerMonth");
    expect(screenFor("/manager-attendance/me/leave")).toBe("managerLeave");
    expect(MANAGER_CHECK_IN_ROUTE).toBe("/manager-attendance/me");
  });

  it("still shows nothing for a route the registry does not know", () => {
    expect(screenFor("/manager-attendance/me/unknown")).toBeUndefined();
    expect(screenFor("/operations/manager-attendance")).toBeUndefined();
  });

  it("builds MY ATTENDANCE with the three entries from the server's access model, and drops an entry the model lacks", () => {
    const full = buildMenu(managerFieldModel());
    expect(full.find((s) => s.section === "MY ATTENDANCE")?.items.map((i) => i.label)).toEqual([
      "Check-in",
      "My Month",
      "My Leave",
    ]);

    const model = managerFieldModel();
    model.navigation = model.navigation.map((section) =>
      section.section === "MY ATTENDANCE" ? { ...section, items: section.items.slice(0, 2) } : section,
    );
    expect(buildMenu(model).find((s) => s.section === "MY ATTENDANCE")?.items.map((i) => i.label)).toEqual([
      "Check-in",
      "My Month",
    ]);
  });
});
