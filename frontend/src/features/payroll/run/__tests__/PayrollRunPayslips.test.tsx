import { render, screen, waitFor, within } from "@testing-library/react";
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

const WORKING_LINE = {
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
  adjustments: "2000.00",
  net: "26818.00",
  warning: null,
  computedAt: "2026-10-01T10:00:00Z",
  adjustmentLines: null,
  working: {
    state: "PAYABLE",
    policy: {
      id: "p1",
      effectiveOn: "2000-01-01",
      rounding: "NEAREST_RUPEE",
      halfDayFraction: "0.500",
    },
    salaryRows: [
      { from: "2026-09-01", to: "2026-09-15", monthly: "100000.00" },
      { from: "2026-09-16", to: "2026-09-30", monthly: "125000.00" },
    ],
    earned: { unrounded: "26000.0000000000", rounded: "26000.00" },
    deduction: { unrounded: "1181.8181818182", rounded: "1182.00" },
    unpaid: [
      {
        date: "2026-09-04",
        dayValue: "1",
        salary: "26000.00",
        divisor: 22,
        amount: "1181.8181818182",
        reason: "Full day without pay",
      },
    ],
    adjustments: [
      {
        id: "a1",
        kind: "CORRECTION",
        amount: "2000.00",
        reason: "August underpaid",
      },
    ],
  },
};

const CANNOT_LINE = {
  ...WORKING_LINE,
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
  working: null,
};

let run: Record<string, unknown> = APPROVED_RUN;
let lines: Record<string, unknown>[] = [WORKING_LINE, CANNOT_LINE];
let fileResult: Response | null = null;

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

function file(name: string, type: string) {
  return {
    ok: true,
    status: 200,
    headers: new Headers({
      "Content-Disposition": `attachment; filename="${name}"`,
    }),
    blob: async () => new Blob(["bytes"], { type }),
  } as unknown as Response;
}

function respond(url: string) {
  if (url.endsWith("/owed")) return json([]);
  if (url.endsWith("/payslips")) {
    return (
      fileResult ?? file("Payslips-2026-09-primary.zip", "application/zip")
    );
  }
  if (url.endsWith("/payslip")) {
    return (
      fileResult ?? file("Payslip-Tara-Teacher-2026-09.pdf", "application/pdf")
    );
  }
  const detail = /\/lines\/(l\d+)$/.exec(url);
  if (detail) {
    const found = lines.find((l) => l.id === detail[1]);
    return found
      ? json(found)
      : json({ reason: "Pay run line not found." }, 404);
  }
  if (url.includes("/lines")) {
    return json({
      content: lines,
      page: 0,
      size: 50,
      totalElements: lines.length,
    });
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

const saved: { href: string; download: string }[] = [];

beforeEach(() => {
  authFetch.mockReset();
  run = APPROVED_RUN;
  lines = [WORKING_LINE, CANNOT_LINE];
  fileResult = null;
  saved.length = 0;
  grantFor(["VIEW", "PROCESS", "EXPORT"]);
  authFetch.mockImplementation(async (url: string) => respond(url));
  URL.createObjectURL = vi.fn(() => "blob:payslip");
  URL.revokeObjectURL = vi.fn();
  vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function (
    this: HTMLAnchorElement,
  ) {
    saved.push({ href: this.href, download: this.download });
  });
});

afterEach(() => vi.restoreAllMocks());

async function openWorking(
  user: ReturnType<typeof userEvent.setup>,
  who: string,
) {
  await user.click(
    await screen.findByRole("button", { name: `Show working for ${who}` }),
  );
  return screen.findByRole("dialog");
}

describe("Payslip downloads (spec 013b US6)", () => {
  it("offers a payslip for a paid line of an approved run and saves the PDF under the server's name", async () => {
    const user = userEvent.setup();
    renderPage();
    const dialog = await openWorking(user, "Tara Teacher");

    await user.click(
      await within(dialog).findByRole("button", { name: "Download payslip" }),
    );

    await waitFor(() => expect(saved).toHaveLength(1));
    expect(saved[0].download).toBe("Payslip-Tara-Teacher-2026-09.pdf");
    expect(authFetch).toHaveBeenCalledWith(
      "/api/v1/payroll/runs/r1/lines/l1/payslip",
    );
  });

  it("offers no payslip for a person who could not be paid", async () => {
    lines = [CANNOT_LINE];
    const user = userEvent.setup();
    renderPage();
    const dialog = await openWorking(user, "Mohan Manager");

    expect(
      await within(dialog).findByText(/cannot be paid in this run/),
    ).toBeInTheDocument();
    expect(
      within(dialog).queryByRole("button", { name: "Download payslip" }),
    ).not.toBeInTheDocument();
  });

  it("shows the server's message when a payslip cannot be downloaded", async () => {
    fileResult = json({ reason: "No payslip for this line." }, 404);
    const user = userEvent.setup();
    renderPage();
    const dialog = await openWorking(user, "Tara Teacher");

    await user.click(
      await within(dialog).findByRole("button", { name: "Download payslip" }),
    );

    expect(
      await within(dialog).findByText("No payslip for this line."),
    ).toBeInTheDocument();
    expect(saved).toHaveLength(0);
  });

  it("downloads every payslip of an approved run as a zip", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: "Download all payslips" }),
    );

    await waitFor(() => expect(saved).toHaveLength(1));
    expect(saved[0].download).toBe("Payslips-2026-09-primary.zip");
    expect(authFetch).toHaveBeenCalledWith("/api/v1/payroll/runs/r1/payslips");
  });

  it("offers no payslips before the run is approved", async () => {
    run = {
      ...APPROVED_RUN,
      status: "AWAITING_APPROVAL",
      approvedBy: null,
      approvedAt: null,
    };
    const user = userEvent.setup();
    renderPage();

    expect(
      await screen.findByRole("button", {
        name: "Show working for Tara Teacher",
      }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Download all payslips" }),
    ).not.toBeInTheDocument();
    const dialog = await openWorking(user, "Tara Teacher");
    expect(
      within(dialog).queryByRole("button", { name: "Download payslip" }),
    ).not.toBeInTheDocument();
  });

  it.each(["light", "dark"] as const)(
    "has no accessibility violations in %s mode",
    async (mode) => {
      const { container } = renderPage(mode);
      await screen.findByRole("button", { name: "Download all payslips" });
      const results = await axe(container, {
        runOnly: { type: "tag", values: ["wcag2a", "wcag2aa", "wcag22aa"] },
      });
      expect(results.violations.map((v) => `${v.id}: ${v.help}`)).toEqual([]);
    },
    30_000,
  );
});
