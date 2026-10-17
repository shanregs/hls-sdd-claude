import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material/styles";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { axe } from "vitest-axe";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
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

const RUN = {
  id: "r1",
  month: "2026-09",
  kind: "PRIMARY",
  sequence: 1,
  status: "APPROVED",
  computeState: "DONE",
  stalled: false,
  progressDone: 1,
  progressTotal: 1,
  totals: {
    earned: "26000.00",
    deduction: "0.00",
    adjustments: "0.00",
    net: "26000.00",
  },
  payableCount: 1,
  cannotPayCount: 0,
  paidCount: 0,
  unpaidCount: 1,
  paidAmount: "0.00",
  unpaidAmount: "26000.00",
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

let run: Record<string, unknown> = RUN;
let exportResult: Response | null = null;

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

function csv(name: string) {
  return {
    ok: true,
    status: 200,
    headers: new Headers({
      "Content-Disposition": `attachment; filename="${name}"`,
    }),
    blob: async () => new Blob(["Month,2026-09"], { type: "text/csv" }),
  } as unknown as Response;
}

function respond(url: string) {
  if (url.endsWith("/owed")) return json([]);
  if (url.endsWith("/export")) {
    return exportResult ?? csv("Payroll-2026-09-primary-approved.csv");
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

const saved: { download: string }[] = [];

beforeEach(() => {
  authFetch.mockReset();
  run = RUN;
  exportResult = null;
  saved.length = 0;
  grantFor(["VIEW", "PROCESS", "EXPORT"]);
  authFetch.mockImplementation(async (url: string) => respond(url));
  URL.createObjectURL = vi.fn(() => "blob:csv");
  URL.revokeObjectURL = vi.fn();
  vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function (
    this: HTMLAnchorElement,
  ) {
    saved.push({ download: this.download });
  });
});

afterEach(() => vi.restoreAllMocks());

describe("Export CSV (spec 013b US8)", () => {
  it("saves the file under the server's name when the caller has Export", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "Export CSV" }));

    await waitFor(() => expect(saved).toHaveLength(1));
    expect(saved[0].download).toBe("Payroll-2026-09-primary-approved.csv");
    expect(authFetch).toHaveBeenCalledWith("/api/v1/payroll/runs/r1/export");
  }, 20_000);

  it("is not offered without the Export permission", async () => {
    grantFor(["VIEW", "PROCESS"]);
    renderPage();
    await screen.findByText("Download all payslips");

    expect(
      screen.queryByRole("button", { name: "Export CSV" }),
    ).not.toBeInTheDocument();
  });

  it("warns that a draft file is not final", async () => {
    run = { ...RUN, status: "DRAFT", approvedBy: null, approvedAt: null };
    renderPage();

    await screen.findByRole("button", { name: "Export CSV" });
    expect(
      screen.getByText(/not approved yet, so the file is marked draft/),
    ).toBeInTheDocument();
  });

  it("shows the server's message when the export fails", async () => {
    exportResult = json({ reason: "Pay run not found." }, 404);
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "Export CSV" }));

    expect(await screen.findByText("Pay run not found.")).toBeInTheDocument();
    expect(saved).toHaveLength(0);
  });

  it("is not offered for a cancelled run", async () => {
    run = { ...RUN, status: "CANCELLED", cancelReason: "Wrong month" };
    renderPage();
    await screen.findByText(/Wrong month/);

    expect(
      screen.queryByRole("button", { name: "Export CSV" }),
    ).not.toBeInTheDocument();
  });

  it.each(["light", "dark"] as const)(
    "has no accessibility violations in %s mode",
    async (mode) => {
      const { container } = renderPage(mode);
      await screen.findByRole("button", { name: "Export CSV" });
      const results = await axe(container, {
        runOnly: { type: "tag", values: ["wcag2a", "wcag2aa", "wcag22aa"] },
      });
      expect(results.violations.map((v) => `${v.id}: ${v.help}`)).toEqual([]);
    },
    30_000,
  );
});
