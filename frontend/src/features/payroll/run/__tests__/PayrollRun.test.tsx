import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material/styles";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { axe } from "vitest-axe";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { RouteGuard } from "../../../../app/RouteGuard";
import { buildMuiTheme, type ThemeMode } from "../../../../theme/tokens";
import { PayrollRunPage } from "../PayrollRunPage";

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

const BASE_RUN = {
  id: "r1",
  month: "2026-09",
  kind: "PRIMARY",
  sequence: 1,
  status: "DRAFT",
  computeState: "DONE",
  stalled: false,
  progressDone: 4,
  progressTotal: 4,
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
  events: [],
  recomputeSummary: null,
};

const PAYABLE_LINE = {
  id: "l1",
  personKind: "TEACHER",
  personId: "p1",
  name: "Tara Teacher",
  label: "Teacher",
  state: "PAYABLE",
  reason: null,
  payableDays: 22,
  workingDays: "22",
  unpaidDays: "1",
  salary: "26000.00",
  earned: "26000.00",
  deduction: "1182.00",
  adjustments: "0.00",
  net: "24818.00",
  warning: null,
  computedAt: "2026-10-01T10:00:00Z",
  working: null,
  adjustmentLines: null,
};

const CANNOT_LINE = {
  ...PAYABLE_LINE,
  id: "l2",
  personId: "p2",
  name: "Mohan Manager",
  personKind: "MANAGER",
  label: "Manager",
  state: "CANNOT_PAY",
  reason: "Manager attendance for September 2026 is not locked yet.",
  earned: null,
  deduction: null,
  adjustments: null,
  net: null,
};

let run: Record<string, unknown> = BASE_RUN;
let lines: Record<string, unknown>[] = [PAYABLE_LINE, CANNOT_LINE];

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

function respond(url: string, init?: RequestInit) {
  if (url.includes("/lines")) {
    const state = new URL(url, "http://x").searchParams.get("state");
    const content = lines.filter((l) => !state || l.state === state);
    return json({ content, page: 0, size: 50, totalElements: content.length });
  }
  if (init?.method === "POST" && url.endsWith("/recompute")) {
    return json({ ...run, computeState: "QUEUED" }, 202);
  }
  if (init?.method === "POST" && url.endsWith("/cancel")) {
    return json({ ...run, status: "CANCELLED", cancelReason: "Wrong month" });
  }
  return json(run);
}

function renderPage(mode: ThemeMode = "light") {
  return render(
    <ThemeProvider theme={buildMuiTheme(mode)}>
      <MemoryRouter initialEntries={["/operations/payroll/r1"]}>
        <Routes>
          <Route
            path="/operations/payroll/:runId"
            element={
              <RouteGuard>
                <PayrollRunPage />
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
  run = BASE_RUN;
  lines = [PAYABLE_LINE, CANNOT_LINE];
  grantFor(["VIEW", "PROCESS", "EXPORT"]);
  authFetch.mockImplementation(async (url: string, init?: RequestInit) =>
    respond(url, init),
  );
});

describe("Pay run page (spec 013b US1)", () => {
  it("shows the header, the totals in rupees and every figure of a line", async () => {
    renderPage();
    expect(
      await screen.findByRole("heading", { name: /September 2026/ }),
    ).toBeInTheDocument();
    expect(screen.getByText("₹1,23,000.00")).toBeInTheDocument();
    const lineRow = (await screen.findByText("Tara Teacher")).closest("tr")!;
    expect(within(lineRow).getByText("₹26,000.00")).toBeInTheDocument();
    expect(within(lineRow).getByText("₹1,182.00")).toBeInTheDocument();
    expect(within(lineRow).getByText("₹24,818.00")).toBeInTheDocument();
  });

  it("lists the people who cannot be paid apart, with the reason and no amounts", async () => {
    renderPage();
    const section = (
      await screen.findByRole("heading", { name: "Cannot be paid in this run" })
    ).closest("section")!;
    expect(within(section).getByText("Mohan Manager")).toBeInTheDocument();
    expect(
      within(section).getByText(
        "Manager attendance for September 2026 is not locked yet.",
      ),
    ).toBeInTheDocument();
    expect(within(section).queryByText(/₹/)).not.toBeInTheDocument();
  });

  it("filters the lines by state, role and name through the server", async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText("Tara Teacher");
    authFetch.mockClear();
    await user.type(screen.getByLabelText("Search by name"), "tar");
    await waitFor(() =>
      expect(authFetch).toHaveBeenCalledWith(
        expect.stringContaining("query=tar"),
      ),
    );
    expect(authFetch).toHaveBeenCalledWith(
      expect.stringContaining("state=PAYABLE"),
    );
  });

  it("shows the progress while computing and polls until done", async () => {
    run = {
      ...BASE_RUN,
      computeState: "RUNNING",
      progressDone: 2,
      progressTotal: 4,
    };
    renderPage();
    expect(
      await screen.findByText(/Computing: 2 of 4 people done/),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("progressbar", { name: "Computation progress" }),
    ).toHaveAttribute("aria-valuenow", "50");
    expect(screen.queryByText("Tara Teacher")).not.toBeInTheDocument();
    run = BASE_RUN;
    expect(
      await screen.findByText("Tara Teacher", {}, { timeout: 5000 }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("progressbar")).not.toBeInTheDocument();
  }, 15_000);

  it("offers Compute again when the computation went stale", async () => {
    run = { ...BASE_RUN, computeState: "RUNNING", stalled: true };
    renderPage();
    expect(
      await screen.findByText(/stopped before it finished/),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Compute again" })).toBeEnabled();
  });

  it("computes a draft again and shows what changed", async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText("Tara Teacher");
    run = {
      ...BASE_RUN,
      recomputeSummary: {
        added: [],
        removed: [],
        changed: [
          {
            kind: "TEACHER",
            personId: "p1",
            name: "Tara Teacher",
            oldNet: "24000.00",
            newNet: "24818.00",
            reason: "The figures this line is built from changed",
          },
        ],
        moved: [
          {
            kind: "MANAGER",
            personId: "p2",
            name: "Mohan Manager",
            from: "CANNOT_PAY",
            to: "PAYABLE",
            reason: "Can now be paid",
          },
        ],
      },
    };
    await user.click(screen.getByRole("button", { name: "Compute again" }));
    await waitFor(() =>
      expect(authFetch).toHaveBeenCalledWith(
        "/api/v1/payroll/runs/r1/recompute",
        expect.objectContaining({ method: "POST" }),
      ),
    );
    expect(
      await screen.findByRole(
        "heading",
        { name: "What changed when it was computed again" },
        { timeout: 5000 },
      ),
    ).toBeInTheDocument();
    expect(screen.getByText(/₹24,000.00 to ₹24,818.00/)).toBeInTheDocument();
    expect(screen.getByText(/Can now be paid/)).toBeInTheDocument();
  }, 15_000);

  it("cancels a draft only with a reason", async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText("Tara Teacher");
    await user.click(screen.getByRole("button", { name: "Cancel run" }));
    await user.click(screen.getByRole("button", { name: "Cancel the run" }));
    expect(
      await screen.findByText("A reason is required."),
    ).toBeInTheDocument();
    expect(
      authFetch.mock.calls.some(([, init]) => init?.method === "POST"),
    ).toBe(false);

    await user.type(
      screen.getByLabelText(/Reason for cancelling/),
      "Wrong month",
    );
    await user.click(screen.getByRole("button", { name: "Cancel the run" }));
    await waitFor(() =>
      expect(authFetch).toHaveBeenCalledWith(
        "/api/v1/payroll/runs/r1/cancel",
        expect.objectContaining({
          method: "POST",
          body: JSON.stringify({ reason: "Wrong month" }),
        }),
      ),
    );
    expect(
      await screen.findByText(/Cancelled.*Wrong month/),
    ).toBeInTheDocument();
  });

  it("shows the server's refusal when Compute again is refused", async () => {
    authFetch.mockImplementation(async (url: string, init?: RequestInit) =>
      init?.method === "POST"
        ? json(
            { reason: "The run is being computed now. Wait for it to finish." },
            409,
          )
        : respond(url, init),
    );
    const user = userEvent.setup();
    renderPage();
    await screen.findByText("Tara Teacher");
    await user.click(screen.getByRole("button", { name: "Compute again" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "being computed now",
    );
  });

  it("gives a Director (view and approve only) no way to start, compute or cancel", async () => {
    grantFor(["VIEW", "APPROVE", "EXPORT"]);
    renderPage();
    await screen.findByText("Tara Teacher");
    expect(
      screen.queryByRole("button", { name: "Compute again" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Cancel run" }),
    ).not.toBeInTheDocument();
  });

  it("offers no actions on an approved run", async () => {
    run = { ...BASE_RUN, status: "APPROVED" };
    renderPage();
    await screen.findByText("Tara Teacher");
    expect(screen.getAllByText("Approved").length).toBeGreaterThan(0);
    expect(
      screen.queryByRole("button", { name: "Compute again" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Cancel run" }),
    ).not.toBeInTheDocument();
  });

  it("says when everyone can be paid", async () => {
    lines = [PAYABLE_LINE];
    renderPage();
    expect(
      await screen.findByText("Everyone in this run can be paid."),
    ).toBeInTheDocument();
  });

  it("shows an error when the run cannot be loaded", async () => {
    authFetch.mockResolvedValue(json({ reason: "Pay run not found." }, 404));
    renderPage();
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Pay run not found.",
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
      await screen.findByText("Tara Teacher");
      await screen.findByText("Mohan Manager");
      const results = await axe(container, {
        runOnly: { type: "tag", values: ["wcag2a", "wcag2aa", "wcag22aa"] },
      });
      expect(results.violations.map((v) => `${v.id}: ${v.help}`)).toEqual([]);
    },
    30_000,
  );
});
