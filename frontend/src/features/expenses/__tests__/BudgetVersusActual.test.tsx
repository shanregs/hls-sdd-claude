import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { axe } from "vitest-axe";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { BudgetVersusActualPage } from "../BudgetVersusActualPage";
import { json, renderAt } from "./fixtures";

const authFetch = vi.fn();
let actions = ["VIEW", "CREATE", "EDIT", "APPROVE", "EXPORT"];

vi.mock("../../../auth/useAuth", () => ({ useAuth: () => ({ authFetch }) }));

vi.mock("../../../access-model/useAccessModel", async () => {
  const actual = await vi.importActual<
    typeof import("../../../access-model/useAccessModel")
  >("../../../access-model/useAccessModel");
  return {
    ...actual,
    useAccessModel: () => ({
      loading: false,
      accessModel: {
        roles: ["DIRECTOR"],
        dataScope: {},
        navigation: [
          {
            section: "OPERATIONS",
            items: [
              { label: "Expenses", route: "/operations/expenses", actions },
            ],
          },
        ],
      },
    }),
  };
});

const ROUTES = [
  { path: "/operations/expenses/budgets", element: <BudgetVersusActualPage /> },
];

const NORTH = {
  zoneId: "z-1",
  areaName: "North",
  total: false,
  budgetId: "b-1",
  budgetVersion: 3,
  budget: "20000.00",
  actual: "22000.00",
  pending: "5000.00",
  remaining: "-2000.00",
  percentUsed: 110,
  over: true,
};
const SOUTH = {
  zoneId: "z-2",
  areaName: "South",
  total: false,
  budgetId: null,
  budgetVersion: null,
  budget: null,
  actual: "400.00",
  pending: "0.00",
  remaining: null,
  percentUsed: null,
  over: false,
};
const HEAD = {
  zoneId: null,
  areaName: "Head office",
  total: false,
  budgetId: null,
  budgetVersion: null,
  budget: null,
  actual: "0.00",
  pending: "0.00",
  remaining: null,
  percentUsed: null,
  over: false,
};
const TOTAL = {
  ...NORTH,
  zoneId: null,
  areaName: "All areas",
  total: true,
  budgetId: null,
  budgetVersion: null,
};

beforeEach(() => {
  authFetch.mockReset();
  actions = ["VIEW", "CREATE", "EDIT", "APPROVE", "EXPORT"];
  authFetch.mockImplementation(async (url: string, init?: RequestInit) => {
    if (init?.method === "PUT") return json({ budgetId: "b-2", created: true });
    if (url.includes("/budgets/detail")) {
      return json({
        month: "2026-10",
        areaName: "North",
        byCategory: [
          {
            category: "TRAVEL_FUEL",
            categoryLabel: "Travel and Fuel",
            count: 2,
            total: "1500.00",
          },
        ],
        byClaimant: [
          {
            claimantId: "u-1",
            claimantName: "Meera Manager",
            count: 2,
            total: "1500.00",
          },
        ],
        claims: [],
      });
    }
    return json({
      month: "2026-10",
      areas: [HEAD, NORTH, SOUTH],
      total: TOTAL,
    });
  });
});

describe("Budget versus actual (spec 015 US5)", () => {
  it("shows budget, actual, pending, remaining, percentage and the over marker in words", async () => {
    renderAt("/operations/expenses/budgets", ROUTES);
    const table = await screen.findByRole("table", {
      name: "Budget versus actual",
    });
    const north = within(table).getByRole("row", { name: /^North/ });
    expect(north).toHaveTextContent("₹20,000.00");
    expect(north).toHaveTextContent("₹22,000.00");
    expect(north).toHaveTextContent("₹5,000.00");
    expect(north).toHaveTextContent("110%");
    expect(north).toHaveTextContent("Over budget");
  });

  it("says No budget set instead of zero", async () => {
    renderAt("/operations/expenses/budgets", ROUTES);
    const table = await screen.findByRole("table", {
      name: "Budget versus actual",
    });
    expect(
      within(table).getByRole("row", { name: /^South/ }),
    ).toHaveTextContent("No budget set");
    expect(
      within(table).getByRole("row", { name: /^South/ }),
    ).not.toHaveTextContent("Over budget");
  });

  it("drills down by category and claimant", async () => {
    const user = userEvent.setup();
    renderAt("/operations/expenses/budgets", ROUTES);
    await screen.findByRole("table", { name: "Budget versus actual" });
    await user.click(screen.getByRole("button", { name: "Claims for North" }));
    const detail = await screen.findByLabelText("Claims behind actual");
    expect(detail).toHaveTextContent("Travel and Fuel: ₹1,500.00 (2)");
    expect(detail).toHaveTextContent("Meera Manager: ₹1,500.00 (2)");
  });

  it("sets a budget through a dialog", async () => {
    const user = userEvent.setup();
    renderAt("/operations/expenses/budgets", ROUTES);
    await screen.findByRole("table", { name: "Budget versus actual" });
    await user.click(
      screen.getByRole("button", { name: "Set budget for South" }),
    );
    const dialog = screen.getByRole("dialog", { name: "Budget for South" });
    await user.type(within(dialog).getByLabelText(/Monthly budget/), "15000");
    await user.click(
      within(dialog).getByRole("button", { name: "Save budget" }),
    );
    const sent = authFetch.mock.calls.find((c) => c[1]?.method === "PUT");
    expect(JSON.parse(String(sent?.[1].body))).toMatchObject({
      zoneId: "z-2",
      amount: "15000",
    });
  });

  it("hides the set-budget buttons without Create or Edit", async () => {
    actions = ["VIEW"];
    renderAt("/operations/expenses/budgets", ROUTES);
    await screen.findByRole("table", { name: "Budget versus actual" });
    expect(
      screen.queryByRole("button", { name: /Set budget for/ }),
    ).not.toBeInTheDocument();
  });

  it.each(["light", "dark"] as const)(
    "has no accessibility violations in %s mode",
    async (mode) => {
      const { container } = renderAt(
        "/operations/expenses/budgets",
        ROUTES,
        mode,
      );
      await screen.findByRole("table", { name: "Budget versus actual" });
      const results = await axe(container, {
        runOnly: { type: "tag", values: ["wcag2a", "wcag2aa", "wcag22aa"] },
      });
      expect(results.violations.map((v) => `${v.id}: ${v.help}`)).toEqual([]);
    },
    30_000,
  );
});
