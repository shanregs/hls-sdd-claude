import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { act, screen } from "@testing-library/react-native";
import { AppState, type AppStateStatus } from "react-native";
import { canOpenRoute } from "../../src/access/useMenu";
import { ACCESS_MODEL_REFRESH_AFTER_BACKGROUND_MS } from "../../src/config/constants";
import { openExpenseScreen } from "../support/expenseApp";
import {
  directorExpenseModel,
  expenseModel,
  managerExpenseModel,
  systemExpenseModel,
  teacherExpenseModel,
} from "../support/expenseFixtures";
import { openMenu } from "../support/navigation";

/** Spec 036 T041 (US4): the entry exists only because the server's access model says so, and review is never on the phone. */
async function menuLabels(): Promise<string[]> {
  await openMenu();
  await screen.findByLabelText("Log out");
  return ["My Expenses", "Expenses"].filter((label) => screen.queryByLabelText(label) !== null);
}

afterEach(() => jest.restoreAllMocks());

describe("the menu", () => {
  it.each([
    ["a Manager", managerExpenseModel],
    ["a Teacher", teacherExpenseModel],
    ["a Director", directorExpenseModel],
  ])("shows %s My Expenses and never the review item", async (_who, model) => {
    await openExpenseScreen({ openScreen: false, model: model() });
    expect(await menuLabels()).toEqual(["My Expenses"]);
  });

  it("shows a System user no expense entry, and the route is not openable", async () => {
    await openExpenseScreen({ openScreen: false, model: systemExpenseModel() });
    expect(await menuLabels()).toEqual([]);
    expect(canOpenRoute(systemExpenseModel(), "/operations/my-expenses")).toBe(false);
    expect(canOpenRoute(managerExpenseModel(), "/operations/my-expenses")).toBe(true);
  });

  it("does not open the review route even when the server's menu holds it", () => {
    // The app has no screen for it: the registry never maps it (see routes.test.ts).
    expect(canOpenRoute(directorExpenseModel(), "/operations/expenses")).toBe(false);
  });

  it("takes the entry away, and shows Not authorized, once a refresh finds the permission gone", async () => {
    const handlers: ((state: AppStateStatus) => void)[] = [];
    jest.spyOn(AppState, "addEventListener").mockImplementation(((_type: string, listener: (s: AppStateStatus) => void) => {
      handlers.push(listener);
      return { remove: () => undefined };
    }) as never);
    let now = Date.parse("2026-10-23T04:00:00Z");
    const { server } = await openExpenseScreen();
    jest.spyOn(Date, "now").mockImplementation(() => now);
    await screen.findByText("No expenses yet. Choose Add expense to raise one.");

    server.on("GET /api/v1/me/access-model", { status: 200, body: systemExpenseModel() });
    await act(async () => handlers.forEach((h) => h("background")));
    now += ACCESS_MODEL_REFRESH_AFTER_BACKGROUND_MS + 1000;
    await act(async () => handlers.forEach((h) => h("active")));

    expect((await screen.findAllByText(/not authorized/i)).length).toBeGreaterThan(0);
    expect(screen.queryByLabelText("Add expense")).toBeNull();
    expect(await menuLabels()).toEqual([]);
  });
});

describe("without the Create grant", () => {
  it("hides Add expense while the list still loads", async () => {
    await openExpenseScreen({ model: expenseModel({ grants: { create: false } }) });
    await screen.findByText("No expenses yet.");
    expect(screen.queryByLabelText("Add expense")).toBeNull();
  });
});

describe("no role decides anything in the expense code", () => {
  function files(dir: string): string[] {
    return readdirSync(dir).flatMap((name) => {
      const path = join(dir, name);
      if (statSync(path).isDirectory()) return files(path);
      return /\.(ts|tsx)$/.test(name) ? [path] : [];
    });
  }
  const src = join(__dirname, "..", "..", "src");
  const mine = [
    ...files(join(src, "expenses")),
    ...files(join(src, "outbox")),
    ...files(join(src, "bills")),
    join(src, "auth", "useSignOutGuard.tsx"),
    join(src, "screens", "MyExpensesScreen.tsx"),
    join(src, "api", "expensesApi.ts"),
  ];

  it.each(mine.map((f) => [f.replace(/.*[\\/]src[\\/]/, "src/"), f]))("%s never reads the user's roles", (_name, file) => {
    const text = readFileSync(file, "utf8");
    expect(text).not.toMatch(/\.roles\b/);
    expect(text).not.toMatch(/["'`](ADMIN|DIRECTOR|MANAGER|TEACHER|SYSTEM)["'`]/);
  });

  it("calls no review endpoint (spec 036 FR-021)", () => {
    for (const file of mine) {
      expect(readFileSync(file, "utf8")).not.toMatch(/\/api\/v1\/expenses/);
    }
  });
});
