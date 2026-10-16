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
let currentUser: { id: string } | null = { id: "u-admin" };

vi.mock("../../../../auth/useAuth", () => ({
  useAuth: () => ({ authFetch, user: currentUser }),
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

const ADMIN = { id: "u-admin", name: "Asha Admin" };

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
  startedBy: ADMIN,
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

let run: Record<string, unknown> = BASE_RUN;
let lines: Record<string, unknown>[] = [WORKING_LINE, CANNOT_LINE];
let submitResult: Response | null = null;

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

function respond(url: string, init?: RequestInit) {
  const detail = /\/lines\/(l\d+)$/.exec(url);
  if (detail) {
    const found = lines.find((l) => l.id === detail[1]);
    return found
      ? json(found)
      : json({ reason: "Pay run line not found." }, 404);
  }
  if (init?.method === "POST" && url.endsWith("/submit")) {
    return (
      submitResult ??
      json({ ...run, status: "AWAITING_APPROVAL", submittedBy: ADMIN })
    );
  }
  if (init?.method === "POST" && url.endsWith("/approve")) {
    return json({
      ...run,
      status: "APPROVED",
      approvedBy: { id: "u-dir", name: "Divya Director" },
    });
  }
  if (init?.method === "POST" && url.endsWith("/return")) {
    return json({ ...run, status: "DRAFT" });
  }
  if (url.includes("/lines")) {
    const state = new URL(url, "http://x").searchParams.get("state");
    const content = lines.filter((l) => !state || l.state === state);
    return json({ content, page: 0, size: 50, totalElements: content.length });
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

function asDirector() {
  grantFor(["VIEW", "APPROVE", "EXPORT"]);
  currentUser = { id: "u-dir" };
}

const AWAITING = { status: "AWAITING_APPROVAL", submittedBy: ADMIN };

beforeEach(() => {
  authFetch.mockReset();
  run = BASE_RUN;
  lines = [WORKING_LINE, CANNOT_LINE];
  submitResult = null;
  currentUser = { id: "u-admin" };
  grantFor(["VIEW", "PROCESS", "EXPORT"]);
  authFetch.mockImplementation(async (url: string, init?: RequestInit) =>
    respond(url, init),
  );
});

describe("Pay run review and approval (spec 013b US2)", () => {
  it("lets an Admin submit a computed draft and shows that it now waits", async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText("Tara Teacher");

    await user.click(
      screen.getByRole("button", { name: "Submit for approval" }),
    );

    await waitFor(() =>
      expect(authFetch).toHaveBeenCalledWith(
        "/api/v1/payroll/runs/r1/submit",
        expect.objectContaining({ method: "POST" }),
      ),
    );
    expect(await screen.findByText(/waiting for approval/)).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Submit for approval" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Compute again" }),
    ).not.toBeInTheDocument();
  });

  it("shows the refusal when a line has a negative net pay", async () => {
    submitResult = json(
      {
        reason:
          "The net pay of Tara Teacher is below zero (-₹100.00). Correct it with an adjustment, then compute again.",
      },
      409,
    );
    const user = userEvent.setup();
    renderPage();
    await screen.findByText("Tara Teacher");

    await user.click(
      screen.getByRole("button", { name: "Submit for approval" }),
    );

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "The net pay of Tara Teacher is below zero",
    );
  });

  it("does not allow Submit while the run is still computing", async () => {
    run = { ...BASE_RUN, computeState: "RUNNING", progressDone: 1 };
    renderPage();
    await screen.findByText(/Computing/);
    expect(
      screen.getByRole("button", { name: "Submit for approval" }),
    ).toBeDisabled();
  });

  it("gives a Director Approve and Return but no Submit, Compute again or Cancel", async () => {
    asDirector();
    run = { ...BASE_RUN, ...AWAITING };
    renderPage();
    await screen.findByText("Tara Teacher");

    expect(screen.getByRole("button", { name: "Approve" })).toBeEnabled();
    expect(
      screen.getByRole("button", { name: "Return to draft" }),
    ).toBeEnabled();
    for (const name of ["Submit for approval", "Compute again", "Cancel run"]) {
      expect(screen.queryByRole("button", { name })).not.toBeInTheDocument();
    }
  });

  it("approves and then shows the run as final and locked", async () => {
    asDirector();
    run = { ...BASE_RUN, ...AWAITING };
    const user = userEvent.setup();
    renderPage();
    await screen.findByText("Tara Teacher");

    await user.click(screen.getByRole("button", { name: "Approve" }));

    await waitFor(() =>
      expect(authFetch).toHaveBeenCalledWith(
        "/api/v1/payroll/runs/r1/approve",
        expect.objectContaining({ method: "POST" }),
      ),
    );
    expect(await screen.findByText(/final and locked/)).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Approve" }),
    ).not.toBeInTheDocument();
  });

  it("hides Approve from the person who submitted the run and says why", async () => {
    asDirector();
    currentUser = { id: "u-admin" };
    run = { ...BASE_RUN, ...AWAITING };
    renderPage();
    await screen.findByText("Tara Teacher");

    expect(
      screen.queryByRole("button", { name: "Approve" }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByText(
        "You submitted this run, so someone else must approve it.",
      ),
    ).toBeInTheDocument();
  });

  it("gives an Admin no Approve or Return button", async () => {
    run = { ...BASE_RUN, ...AWAITING };
    renderPage();
    await screen.findByText("Tara Teacher");

    for (const name of ["Approve", "Return to draft"]) {
      expect(screen.queryByRole("button", { name })).not.toBeInTheDocument();
    }
  });

  it("returns a run only with a reason", async () => {
    asDirector();
    run = { ...BASE_RUN, ...AWAITING };
    const user = userEvent.setup();
    renderPage();
    await screen.findByText("Tara Teacher");
    await user.click(screen.getByRole("button", { name: "Return to draft" }));

    const dialog = await screen.findByRole("dialog");
    await user.click(
      within(dialog).getByRole("button", { name: "Return to draft" }),
    );
    expect(
      await within(dialog).findByText("A reason is required."),
    ).toBeInTheDocument();
    expect(
      authFetch.mock.calls.some(([url]) => String(url).endsWith("/return")),
    ).toBe(false);

    await user.type(
      within(dialog).getByLabelText(/Reason for returning/),
      "Check the deduction",
    );
    await user.click(
      within(dialog).getByRole("button", { name: "Return to draft" }),
    );
    await waitFor(() =>
      expect(authFetch).toHaveBeenCalledWith(
        "/api/v1/payroll/runs/r1/return",
        expect.objectContaining({
          method: "POST",
          body: JSON.stringify({ reason: "Check the deduction" }),
        }),
      ),
    );
  });

  it("shows the Admin the reason a draft was returned with, and the history", async () => {
    run = {
      ...BASE_RUN,
      events: [
        {
          at: "2026-10-02T10:00:00Z",
          actor: { id: "u-dir", name: "Divya Director" },
          kind: "RETURNED",
          reason: "Check the deduction",
          detail: null,
        },
      ],
    };
    renderPage();

    expect(
      await screen.findByText(
        /Returned by Divya Director: Check the deduction/,
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "History" }),
    ).toBeInTheDocument();
  });

  it("opens the working of a line so the net pay can be reproduced", async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText("Tara Teacher");

    await user.click(
      screen.getByRole("button", { name: "Show working for Tara Teacher" }),
    );

    const dialog = await screen.findByRole("dialog");
    expect(
      await within(dialog).findByText("₹1,00,000.00 a month"),
    ).toBeInTheDocument();
    expect(
      within(dialog).getByText("01/09/2026 to 15/09/2026"),
    ).toBeInTheDocument();
    expect(
      within(dialog).getByText("16/09/2026 to 30/09/2026"),
    ).toBeInTheDocument();
    expect(
      within(dialog).getByText("22 of 22 working days"),
    ).toBeInTheDocument();
    expect(within(dialog).getByText("04/09/2026")).toBeInTheDocument();
    expect(
      within(dialog).getByText("Full day without pay"),
    ).toBeInTheDocument();
    expect(
      within(dialog).getByText("Correction: August underpaid"),
    ).toBeInTheDocument();
    expect(within(dialog).getByText("₹26,818.00")).toBeInTheDocument();
    expect(
      within(dialog).getByText(/rounded nearest rupee/),
    ).toBeInTheDocument();
  });

  it("shows why a person cannot be paid in the working dialog", async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText("Tara Teacher");
    await user.click(screen.getByRole("combobox", { name: "Show" }));
    await user.click(
      await screen.findByRole("option", { name: "Cannot be paid" }),
    );
    await user.click(
      await screen.findByRole("button", {
        name: "Show working for Mohan Manager",
      }),
    );

    const dialog = await screen.findByRole("dialog");
    expect(
      await within(dialog).findByText(
        /Mohan Manager cannot be paid in this run/,
      ),
    ).toBeInTheDocument();
  });

  it.each(["light", "dark"] as const)(
    "the working dialog has no accessibility violations in %s mode",
    async (mode) => {
      const user = userEvent.setup();
      renderPage(mode);
      await screen.findByText("Tara Teacher");
      await user.click(
        screen.getByRole("button", { name: "Show working for Tara Teacher" }),
      );
      const dialog = await screen.findByRole("dialog");
      await within(dialog).findByText("₹26,818.00");
      const results = await axe(dialog, {
        runOnly: { type: "tag", values: ["wcag2a", "wcag2aa", "wcag22aa"] },
      });
      expect(results.violations.map((v) => `${v.id}: ${v.help}`)).toEqual([]);
    },
    30_000,
  );

  it.each(["light", "dark"] as const)(
    "an approved run has no accessibility violations in %s mode",
    async (mode) => {
      asDirector();
      run = {
        ...BASE_RUN,
        status: "APPROVED",
        approvedBy: { id: "u-dir", name: "Divya Director" },
        approvedAt: "2026-10-03T10:00:00Z",
        submittedBy: ADMIN,
        events: [
          {
            at: "2026-10-03T10:00:00Z",
            actor: { id: "u-dir", name: "Divya Director" },
            kind: "APPROVED",
            reason: null,
            detail: null,
          },
        ],
      };
      const { container } = renderPage(mode);
      await screen.findByText("Tara Teacher");
      const results = await axe(container, {
        runOnly: { type: "tag", values: ["wcag2a", "wcag2aa", "wcag22aa"] },
      });
      expect(results.violations.map((v) => `${v.id}: ${v.help}`)).toEqual([]);
    },
    30_000,
  );
});
