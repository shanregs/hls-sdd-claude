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

vi.mock("../../../../auth/useAuth", () => ({
  useAuth: () => ({ authFetch, user: { id: "u-admin" } }),
}));

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

const APPROVED_RUN = {
  id: "r1",
  month: "2026-09",
  kind: "PRIMARY",
  sequence: 1,
  status: "APPROVED",
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
  unpaidCount: 3,
  paidAmount: "0.00",
  unpaidAmount: "123000.00",
  startedBy: { id: "u-admin", name: "Asha Admin" },
  startedAt: "2026-10-01T10:00:00Z",
  submittedBy: { id: "u-admin", name: "Asha Admin" },
  submittedAt: "2026-10-02T10:00:00Z",
  approvedBy: { id: "u-dir", name: "Divya Director" },
  approvedAt: "2026-10-03T10:00:00Z",
  cancelledBy: null,
  cancelledAt: null,
  cancelReason: null,
  policy: null,
  version: 3,
  events: [],
  recomputeSummary: null,
  adjustmentsWaitingSinceComputed: 0,
};

const OWED = [
  {
    kind: "MANAGER",
    personId: "p2",
    name: "Mohan Manager",
    reason: "Manager attendance for September 2026 is not locked yet.",
    resolved: true,
  },
  {
    kind: "TEACHER",
    personId: "p3",
    name: "Meena Selvi",
    reason: "No salary on 01/09/2026: No salary has been recorded.",
    resolved: false,
  },
];

let run: Record<string, unknown> = APPROVED_RUN;
let owed: Record<string, unknown>[] = OWED;
let startResult: Response | null = null;

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

function respond(url: string, init?: RequestInit) {
  if (url.endsWith("/owed")) return json(owed);
  if (init?.method === "POST" && url.endsWith("/api/v1/payroll/runs")) {
    return (
      startResult ??
      json({ ...run, id: "r2", kind: "SUPPLEMENTARY", status: "DRAFT" }, 202)
    );
  }
  if (url.includes("/lines")) {
    return json({ content: [], page: 0, size: 50, totalElements: 0 });
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
  run = APPROVED_RUN;
  owed = OWED;
  startResult = null;
  grantFor(["VIEW", "PROCESS", "EXPORT"]);
  authFetch.mockImplementation(async (url: string, init?: RequestInit) =>
    respond(url, init),
  );
});

describe("Owed people and the supplementary run (spec 013b US5)", () => {
  it("lists who is still to be paid, with the stored reason and the live hint", async () => {
    renderPage();

    const section = (
      await screen.findByRole("heading", {
        name: "Still to be paid for September 2026",
      })
    ).closest("section")!;
    const mohan = (await within(section).findByText("Mohan Manager")).closest(
      "tr",
    )!;
    expect(
      within(mohan).getByText(
        /Manager attendance for September 2026 is not locked/,
      ),
    ).toBeInTheDocument();
    expect(within(mohan).getByText("Can be paid now")).toBeInTheDocument();
    const meena = within(section).getByText("Meena Selvi").closest("tr")!;
    expect(within(meena).getByText("Still blocked")).toBeInTheDocument();
  });

  it("starts a supplementary run for the month and opens it", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(
      await screen.findByRole("button", { name: "Start supplementary run" }),
    );

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
      month: "2026-09",
      supplementary: true,
    });
  });

  it("shows the server's refusal when a supplementary run cannot be started", async () => {
    startResult = json(
      {
        reason:
          "A supplementary run for September 2026 is already open (a draft). Finish or cancel it first.",
      },
      409,
    );
    const user = userEvent.setup();
    renderPage();
    await user.click(
      await screen.findByRole("button", { name: "Start supplementary run" }),
    );

    expect(await screen.findByText(/already open/)).toBeInTheDocument();
  });

  it("says when nobody is waiting, and offers no button", async () => {
    owed = [];
    renderPage();

    expect(
      await screen.findByText("Nobody is waiting to be paid for this month."),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Start supplementary run" }),
    ).not.toBeInTheDocument();
  });

  it("lets a Director see who is owed but not start the run", async () => {
    grantFor(["VIEW", "APPROVE", "EXPORT"]);
    renderPage();

    expect(await screen.findByText("Mohan Manager")).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Start supplementary run" }),
    ).not.toBeInTheDocument();
  });

  it("shows nothing about owed people on a run that is not approved", async () => {
    run = {
      ...APPROVED_RUN,
      status: "DRAFT",
      approvedBy: null,
      approvedAt: null,
    };
    renderPage();
    await screen.findByRole("heading", { name: /September 2026/ });

    expect(screen.queryByText(/Still to be paid/)).not.toBeInTheDocument();
    expect(
      authFetch.mock.calls.some(([url]) => String(url).endsWith("/owed")),
    ).toBe(false);
  });

  it.each(["light", "dark"] as const)(
    "has no accessibility violations in %s mode",
    async (mode) => {
      const { container } = renderPage(mode);
      await screen.findByText("Mohan Manager");
      const results = await axe(container, {
        runOnly: { type: "tag", values: ["wcag2a", "wcag2aa", "wcag22aa"] },
      });
      expect(results.violations.map((v) => `${v.id}: ${v.help}`)).toEqual([]);
    },
    30_000,
  );
});
