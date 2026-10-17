import { screenFor, TASKS_ROUTE, VISITS_ROUTE } from "../../src/access/screenRegistry";
import { buildMenu } from "../../src/access/useMenu";
import { directorVisitTaskModel, visitTaskModel } from "../support/visitTaskFixtures";

/** Spec 034 T011: the two routes the server offers map to screens, and nothing else of MARKETING does. */
describe("visit and task routes", () => {
  it("maps the Marketing Calendar and My Tasks routes to the two screens", () => {
    expect(screenFor("/marketing/calendar")).toBe("visits");
    expect(screenFor("/operations/tasks")).toBe("tasks");
    expect(VISITS_ROUTE).toBe("/marketing/calendar");
    expect(TASKS_ROUTE).toBe("/operations/tasks");
  });

  it("still shows nothing for the other MARKETING routes the web has", () => {
    for (const route of ["/marketing/prospects", "/marketing/pipeline", "/marketing/dashboard", "/marketing/settings"]) {
      expect(screenFor(route)).toBeUndefined();
    }
  });

  it("builds the two menu entries from the server's access model, for a Manager and a Director", () => {
    const labels = (model: ReturnType<typeof visitTaskModel>) =>
      buildMenu(model).flatMap((section) => section.items.map((item) => `${section.section}/${item.label}`));

    expect(labels(visitTaskModel())).toEqual(expect.arrayContaining(["OPERATIONS/My Tasks", "MARKETING/Marketing Calendar"]));
    expect(labels(directorVisitTaskModel())).toEqual(
      expect.arrayContaining(["OPERATIONS/Manager Tasks", "MARKETING/Marketing Calendar"]),
    );
  });
});
