import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { fireEvent, screen } from "@testing-library/react-native";
import { canOpenRoute } from "../../src/access/useMenu";
import { openAttendanceScreen } from "../support/attendanceApp";
import { DIRECTOR_MODEL, MANAGER_MODEL, TEACHER_MODEL } from "../support/fixtures";
import { openMenu } from "../support/navigation";
import { openVisitTaskScreen } from "../support/visitTaskApp";
import { directorVisitTaskModel, visit, visitTaskModel } from "../support/visitTaskFixtures";

/** Spec 034 T041 (US6): the screens and their buttons exist only because the server's access model says so. */
const press = async (label: string) => fireEvent.press(await screen.findByLabelText(label));

async function menuLabels(): Promise<string[]> {
  await openMenu();
  await screen.findByLabelText("Log out");
  return ["My Tasks", "Manager Tasks", "Marketing Calendar"].filter((label) => screen.queryByLabelText(label) !== null);
}

describe("the menu", () => {
  it("shows a Manager My Tasks and Marketing Calendar exactly as the server's access model returns them", async () => {
    await openVisitTaskScreen(null);

    expect(await menuLabels()).toEqual(["My Tasks", "Marketing Calendar"]);
    expect(screen.getByText("OPERATIONS")).toBeTruthy();
    expect(screen.getByText("MARKETING")).toBeTruthy();
  });

  it("shows a Director the same two screens under the web's names", async () => {
    await openAttendanceScreen(null, { model: directorVisitTaskModel() });

    expect(await menuLabels()).toEqual(["Manager Tasks", "Marketing Calendar"]);
  });

  it.each([
    ["a Teacher", TEACHER_MODEL],
    ["a Director whose menu lacks them", DIRECTOR_MODEL],
    ["a Manager whose menu lacks them", MANAGER_MODEL],
  ])("shows %s neither screen", async (_who, model) => {
    await openAttendanceScreen(null, { model });

    expect(await menuLabels()).toEqual([]);
  });

  it("does not open a route the menu lacks", () => {
    for (const route of ["/marketing/calendar", "/operations/tasks"]) {
      expect(canOpenRoute(TEACHER_MODEL, route)).toBe(false);
      expect(canOpenRoute(visitTaskModel(), route)).toBe(true);
    }
  });
});

describe("without the Create grant", () => {
  it("hides Plan a visit, Add photo on a visit and Add a task, while the screens still load", async () => {
    await openVisitTaskScreen("Marketing Calendar", {
      model: visitTaskModel({ visits: { create: false }, tasks: { create: false } }),
      marketing: { visits: [visit()] },
    });

    await screen.findByText("Green Valley Public School");
    expect(screen.queryByLabelText("Plan a visit")).toBeNull();
    await press("Visit with Green Valley Public School, 06/10/2026, planned");
    await screen.findByText("Attending: Manoj Manager");
    expect(screen.queryByText("Add photo")).toBeNull();
    expect(screen.getByText("Complete visit")).toBeTruthy();
  });
});

describe("without the Edit grant", () => {
  it("hides Complete visit, Reschedule and Cancel visit but still shows the visit", async () => {
    await openVisitTaskScreen("Marketing Calendar", {
      model: visitTaskModel({ visits: { edit: false } }),
      marketing: { visits: [visit()] },
    });
    await press("Visit with Green Valley Public School, 06/10/2026, planned");

    await screen.findByText("Attending: Manoj Manager");
    for (const label of ["Complete visit", "Reschedule", "Cancel visit"]) expect(screen.queryByText(label)).toBeNull();
  });
});

describe("no role decides anything in the visit and task code", () => {
  function files(dir: string): string[] {
    return readdirSync(dir).flatMap((name) => {
      const path = join(dir, name);
      if (statSync(path).isDirectory()) return files(path);
      return /\.(ts|tsx)$/.test(name) ? [path] : [];
    });
  }
  const src = join(__dirname, "..", "..", "src");
  const mine = [
    ...files(join(src, "visits")),
    ...files(join(src, "tasks")),
    ...files(join(src, "media")),
    ...files(join(src, "home")),
    join(src, "screens", "VisitsScreen.tsx"),
    join(src, "screens", "TasksScreen.tsx"),
    join(src, "api", "marketingApi.ts"),
    join(src, "api", "tasksApi.ts"),
    join(src, "access", "useMenuActions.ts"),
  ];

  it.each(mine.map((f) => [f.replace(/.*[\\/]src[\\/]/, "src/"), f]))("%s never reads the user's roles", (_name, file) => {
    const text = readFileSync(file, "utf8");
    expect(text).not.toMatch(/\.roles\b/);
    expect(text).not.toMatch(/["'`](ADMIN|DIRECTOR|MANAGER|TEACHER|SYSTEM)["'`]/);
  });
});
