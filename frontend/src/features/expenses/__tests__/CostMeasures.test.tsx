import { screen } from "@testing-library/react";
import { axe } from "vitest-axe";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { CostMeasuresPage } from "../CostMeasuresPage";
import { ROW, json, renderAt } from "./fixtures";

const authFetch = vi.fn();

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
        navigation: [{ section: "OPERATIONS", items: [{ label: "Expenses", route: "/operations/expenses", actions: ["VIEW"] }] }],
      },
    }),
  };
});

const ROUTES = [{ path: "/operations/expenses/costs", element: <CostMeasuresPage /> }];

function mockMeasures(school: unknown, teacher: unknown) {
  authFetch.mockImplementation(async (url: string) =>
    json(url.includes("per-school") ? school : teacher),
  );
}

beforeEach(() => {
  authFetch.mockReset();
});

describe("Cost measures (spec 015 US6)", () => {
  it("shows the cost per School and per Teacher with the divisor and the claims behind", async () => {
    mockMeasures(
      { cost: "30000.00", divisor: 3, perUnit: "10000.00", state: "OK", claims: [{ ...ROW, categoryLabel: "Marketing", amount: "30000.00" }] },
      { cost: "60000.00", divisor: 12, perUnit: "5000.00", state: "OK", claims: [] },
    );
    renderAt("/operations/expenses/costs", ROUTES);
    const school = await screen.findByLabelText("Cost per School acquired");
    expect(school).toHaveTextContent("₹10,000.00");
    expect(school).toHaveTextContent("Underlying cost ₹30,000.00; Schools acquired: 3");
    expect(screen.getByLabelText("Claims behind Cost per School acquired")).toHaveTextContent("Meera Manager, Marketing, 12/10/2026");
    expect(screen.getByLabelText("Cost per Teacher trained")).toHaveTextContent("₹5,000.00");
  });

  it("says No Schools acquired and No Teachers trained yet instead of dividing by zero", async () => {
    mockMeasures(
      { cost: "2500.00", divisor: 0, perUnit: null, state: "NO_SCHOOLS", claims: [] },
      { cost: "0.00", divisor: 0, perUnit: null, state: "NO_TEACHERS", claims: [] },
    );
    renderAt("/operations/expenses/costs", ROUTES);
    const school = await screen.findByLabelText("Cost per School acquired");
    expect(school).toHaveTextContent("No Schools acquired");
    expect(school).toHaveTextContent("Underlying cost ₹2,500.00");
    expect(screen.getByLabelText("Cost per Teacher trained")).toHaveTextContent("No Teachers trained yet");
  });

  it("shows an error state", async () => {
    authFetch.mockResolvedValue(json({ reason: "Could not load" }, 500));
    renderAt("/operations/expenses/costs", ROUTES);
    expect(await screen.findAllByRole("alert")).not.toHaveLength(0);
  });

  it.each(["light", "dark"] as const)("has no accessibility violations in %s mode", async (mode) => {
    mockMeasures(
      { cost: "30000.00", divisor: 3, perUnit: "10000.00", state: "OK", claims: [] },
      { cost: "0.00", divisor: 0, perUnit: null, state: "NO_TEACHERS", claims: [] },
    );
    const { container } = renderAt("/operations/expenses/costs", ROUTES, mode);
    await screen.findByLabelText("Cost per School acquired");
    const results = await axe(container, { runOnly: { type: "tag", values: ["wcag2a", "wcag2aa", "wcag22aa"] } });
    expect(results.violations.map((v) => `${v.id}: ${v.help}`)).toEqual([]);
  }, 30_000);
});
