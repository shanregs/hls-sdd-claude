import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material/styles";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { axe } from "vitest-axe";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { RouteGuard } from "../../../../app/RouteGuard";
import { buildMuiTheme, type ThemeMode } from "../../../../theme/tokens";
import { PayrollRunsPage } from "../PayrollRunsPage";
import { endedMonths, monthLabel } from "../format";

const authFetch = vi.fn();
let navigation: { section: string; items: unknown[] }[] = [];

vi.mock("../../../../auth/useAuth", () => ({ useAuth: () => ({ authFetch }) }));

vi.mock("../../../../access-model/useAccessModel", async () => {
  const actual = await vi.importActual<
    typeof import("../../../../access-model/useAccessModel")
  >("../../../../access-model/useAccessModel");
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
      items: [{ label: "Payroll", route: "/operations/payroll", actions }],
    },
  ];
}

const RUN = {
  id: "r1",
  month: "2026-09",
  kind: "PRIMARY",
  sequence: 1,
  status: "DRAFT",
  computeState: "DONE",
  stalled: false,
  progressDone: 3,
  progressTotal: 3,
  totals: {
    earned: "125000.00",
    deduction: "2000.00",
    adjustments: "0.00",
    net: "123000.00",
  },
  payableCount: 3,
  cannotPayCount: 1,
  paidCount: 0,
  unpaidCount: 0,
  paidAmount: "0.00",
  unpaidAmount: "0.00",
  startedBy: { id: "u1", name: "Asha Admin" },
  startedAt: "2026-10-01T10:00:00Z",
  submittedBy: null,
  submittedAt: null,
  approvedBy: null,
  approvedAt: null,
  cancelledBy: null,
  cancelledAt: null,
  cancelReason: null,
  policy: null,
  version: 0,
  events: null,
  recomputeSummary: null,
};

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

function page(content: unknown[]) {
  return { content, page: 0, size: 100, totalElements: content.length };
}

function renderPage(mode: ThemeMode = "light") {
  return render(
    <ThemeProvider theme={buildMuiTheme(mode)}>
      <MemoryRouter initialEntries={["/operations/payroll"]}>
        <Routes>
          <Route
            path="/operations/payroll"
            element={
              <RouteGuard>
                <PayrollRunsPage />
              </RouteGuard>
            }
          />
          <Route
            path="/operations/payroll/:runId"
            element={<h1>The run page</h1>}
          />
        </Routes>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

beforeEach(() => {
  authFetch.mockReset();
  grantFor(["VIEW", "PROCESS", "EXPORT"]);
  authFetch.mockImplementation(async (_url: string, init?: RequestInit) =>
    init?.method === "POST" ? json(RUN, 202) : json(page([RUN])),
  );
});

describe("Payroll runs list (spec 013b US1)", () => {
  it("lists the runs with their month, status and totals in rupees", async () => {
    renderPage();
    expect(
      await screen.findByRole("link", { name: "September 2026" }),
    ).toHaveAttribute("href", "/operations/payroll/r1");
    expect(screen.getByText("Draft")).toBeInTheDocument();
    expect(screen.getByText("₹1,23,000.00")).toBeInTheDocument();
    expect(screen.getByText("Primary run")).toBeInTheDocument();
  });

  it("lets an Admin (Process) start a run for an ended month and opens it", async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByRole("link", { name: "September 2026" });
    await user.click(screen.getByRole("button", { name: "Start run" }));
    await waitFor(() =>
      expect(authFetch).toHaveBeenCalledWith(
        "/api/v1/payroll/runs",
        expect.objectContaining({ method: "POST" }),
      ),
    );
    const post = authFetch.mock.calls.find(
      ([, init]) => init?.method === "POST",
    );
    expect(JSON.parse(post![1].body)).toEqual({
      month: endedMonths(12)[0],
      supplementary: false,
    });
    expect(
      await screen.findByRole("heading", { name: "The run page" }),
    ).toBeInTheDocument();
  });

  it("offers only months that have ended", async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByRole("link", { name: "September 2026" });
    await user.click(screen.getByRole("combobox", { name: "Month" }));
    const options = screen.getAllByRole("option").map((o) => o.textContent);
    expect(options).toHaveLength(12);
    expect(options[0]).toBe(monthLabel(endedMonths(12)[0]));
    const current = new Date();
    expect(options).not.toContain(
      monthLabel(
        `${current.getFullYear()}-${String(current.getMonth() + 1).padStart(2, "0")}`,
      ),
    );
  });

  it("shows the server's refusal when a run already exists", async () => {
    authFetch.mockImplementation(async (_url: string, init?: RequestInit) =>
      init?.method === "POST"
        ? json(
            {
              reason: "A pay run already exists for September 2026 (a draft).",
            },
            409,
          )
        : json(page([RUN])),
    );
    const user = userEvent.setup();
    renderPage();
    await screen.findByRole("link", { name: "September 2026" });
    await user.click(screen.getByRole("button", { name: "Start run" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "A pay run already exists for September 2026",
    );
  });

  it("hides Start run from a Director who may only view", async () => {
    grantFor(["VIEW", "APPROVE", "EXPORT"]);
    renderPage();
    await screen.findByRole("link", { name: "September 2026" });
    expect(
      screen.queryByRole("button", { name: "Start run" }),
    ).not.toBeInTheDocument();
    expect(screen.queryByText("Start a pay run")).not.toBeInTheDocument();
  });

  it("says so when no run exists and offers to start one", async () => {
    authFetch.mockResolvedValue(json(page([])));
    renderPage();
    expect(
      await screen.findByText(/No pay run has been started yet/),
    ).toBeInTheDocument();
  });

  it("shows an error when the runs cannot be loaded", async () => {
    authFetch.mockResolvedValue(
      json({ reason: "Could not load the pay runs." }, 500),
    );
    renderPage();
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Could not load the pay runs.",
    );
  });

  it("shows 'Not authorized' and fetches nothing for a role without the menu item", async () => {
    navigation = [];
    renderPage();
    expect(await screen.findByText("Not authorized")).toBeInTheDocument();
    expect(authFetch).not.toHaveBeenCalled();
  });

  it.each(["light", "dark"] as const)(
    "has no accessibility violations in %s mode",
    async (mode) => {
      const { container } = renderPage(mode);
      await screen.findByRole("link", { name: "September 2026" });
      const results = await axe(container, {
        runOnly: { type: "tag", values: ["wcag2a", "wcag2aa", "wcag22aa"] },
      });
      expect(results.violations.map((v) => `${v.id}: ${v.help}`)).toEqual([]);
    },
    30_000,
  );
});
