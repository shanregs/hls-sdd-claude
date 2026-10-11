import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material/styles";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { axe } from "vitest-axe";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { RouteGuard } from "../../../app/RouteGuard";
import { buildMuiTheme, type ThemeMode } from "../../../theme/tokens";
import { PayPolicyPage } from "../PayPolicyPage";
import { fractionProblem } from "../validation";

const authFetch = vi.fn();
let navigation: { section: string; items: unknown[] }[] = [];

vi.mock("../../../auth/useAuth", () => ({ useAuth: () => ({ authFetch }) }));

vi.mock("../../../access-model/useAccessModel", async () => {
  const actual = await vi.importActual<
    typeof import("../../../access-model/useAccessModel")
  >("../../../access-model/useAccessModel");
  return {
    ...actual,
    useAccessModel: () => ({
      loading: false,
      accessModel: { roles: ["ADMIN"], dataScope: {}, navigation },
    }),
  };
});

function grantFor(actions: string[]) {
  navigation = [
    {
      section: "OPERATIONS",
      items: [
        {
          label: "Salary Structures",
          route: "/operations/salary-structures",
          actions,
        },
      ],
    },
  ];
}

const VERSION_1 = {
  id: "p1",
  effectiveOn: "2000-01-01",
  lopDivisor: "WORKING_DAYS",
  halfDayFraction: "0.500",
  rounding: "NEAREST_RUPEE",
  payMonth: "CALENDAR_MONTH",
  note: "Standard rules",
  recordedBy: null,
  recordedAt: "2026-01-01T00:00:00Z",
};

const POLICY = { inEffect: VERSION_1, history: [VERSION_1] };

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

function renderPage(mode: ThemeMode = "light") {
  return render(
    <ThemeProvider theme={buildMuiTheme(mode)}>
      <MemoryRouter initialEntries={["/operations/salary-structures/policy"]}>
        <Routes>
          <Route
            path="/operations/salary-structures/policy"
            element={
              <RouteGuard>
                <PayPolicyPage />
              </RouteGuard>
            }
          />
        </Routes>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

beforeEach(() => {
  authFetch.mockReset();
  grantFor(["VIEW", "CREATE"]);
  authFetch.mockImplementation(async (_url: string, init?: RequestInit) =>
    init?.method ? json({ ...VERSION_1, id: "p2" }, 201) : json(POLICY),
  );
});

describe("Pay policy (spec 013a US2)", () => {
  it("shows the standard rules in effect and the history", async () => {
    renderPage();
    expect(
      await screen.findByRole("heading", { name: "In effect today" }),
    ).toBeInTheDocument();
    expect(screen.getByText("0.500 of a day")).toBeInTheDocument();
    // in the rules in effect, in the history row and as the form's starting choice
    expect(screen.getAllByText("Nearest rupee").length).toBeGreaterThanOrEqual(
      2,
    );
    expect(screen.getByText("Calendar month")).toBeInTheDocument();
    // the installation's own version has no author ("Standard rules") and carries the note "Standard rules"
    expect(
      screen.getAllByText("Standard rules", { selector: "td" }),
    ).toHaveLength(2);
  });

  it.each([
    ["0", "more than 0 and less than 1"],
    ["1", "more than 0 and less than 1"],
    ["-0.5", "more than 0 and less than 1"],
    ["1.5", "more than 0 and less than 1"],
    ["0.5555", "at most three decimal places"],
    ["abc", "as a number"],
  ])(
    "refuses a half-day fraction of %s with the problem named",
    async (value, problem) => {
      const user = userEvent.setup();
      renderPage();
      await screen.findByRole("heading", { name: "In effect today" });
      authFetch.mockClear();
      const field = screen.getByLabelText(/half day counts as/i);
      await user.clear(field);
      await user.type(field, value);
      await user.click(screen.getByRole("button", { name: "Record version" }));
      expect(screen.getByText(new RegExp(problem))).toBeInTheDocument();
      expect(authFetch).not.toHaveBeenCalled();
    },
  );

  it("records a new version carrying the rules that stay the same", async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByRole("heading", { name: "In effect today" });
    const field = screen.getByLabelText(/half day counts as/i);
    await user.clear(field);
    await user.type(field, "0.4");
    await user.click(screen.getByRole("button", { name: "Record version" }));
    await waitFor(() =>
      expect(authFetch).toHaveBeenCalledWith(
        "/api/v1/salary-structures/policy",
        expect.objectContaining({ method: "POST" }),
      ),
    );
    const post = authFetch.mock.calls.find(
      ([, init]) => init?.method === "POST",
    );
    expect(JSON.parse(post![1].body)).toMatchObject({
      halfDayFraction: "0.4",
      lopDivisor: "WORKING_DAYS",
      payMonth: "CALENDAR_MONTH",
      rounding: "NEAREST_RUPEE",
    });
    expect(
      await screen.findByText("The new version was recorded."),
    ).toBeInTheDocument();
  });

  it("hides the form from a viewer without CREATE but still shows the rules", async () => {
    grantFor(["VIEW"]);
    renderPage();
    await screen.findByRole("heading", { name: "In effect today" });
    expect(screen.queryByText("New version")).not.toBeInTheDocument();
    expect(screen.getByText("History")).toBeInTheDocument();
  });

  it("shows 'Not authorized' and fetches nothing without the menu item", async () => {
    navigation = [];
    renderPage();
    expect(await screen.findByText("Not authorized")).toBeInTheDocument();
    expect(authFetch).not.toHaveBeenCalled();
  });

  it("shows an error when the policy cannot be loaded", async () => {
    authFetch.mockResolvedValue(
      json({ reason: "Could not load the pay policy." }, 500),
    );
    renderPage();
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Could not load the pay policy.",
    );
  });

  it.each(["light", "dark"] as const)(
    "has no accessibility violations in %s mode",
    async (mode) => {
      const { container } = renderPage(mode);
      await screen.findByRole("heading", { name: "In effect today" });
      const results = await axe(container, {
        runOnly: { type: "tag", values: ["wcag2a", "wcag2aa", "wcag22aa"] },
      });
      expect(results.violations.map((v) => `${v.id}: ${v.help}`)).toEqual([]);
    },
    30_000,
  );
});

describe("fractionProblem", () => {
  it("accepts the boundaries inside the range", () => {
    expect(fractionProblem("0.001")).toBeNull();
    expect(fractionProblem("0.999")).toBeNull();
    expect(fractionProblem("0.5")).toBeNull();
  });
});
