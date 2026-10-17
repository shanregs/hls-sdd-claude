import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material/styles";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { axe } from "vitest-axe";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { RouteGuard } from "../../../../app/RouteGuard";
import { buildMuiTheme, type ThemeMode } from "../../../../theme/tokens";
import { PayrollRunPage } from "../PayrollRunPage";
import { PersonPayrollHistory } from "../PersonPayrollHistory";

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

const PAYABLE = {
  id: "l1",
  personKind: "TEACHER",
  personId: "p1",
  name: "Tara Teacher",
  label: "Teacher",
  state: "PAYABLE",
  reason: null,
  payableDays: 22,
  workingDays: "22",
  unpaidDays: "0",
  salary: "26000.00",
  earned: "26000.00",
  deduction: "0.00",
  adjustments: "0.00",
  net: "26000.00",
  warning: null,
  computedAt: "2026-10-01T10:00:00Z",
  adjustmentLines: null,
  working: null,
  payment: null as unknown,
};

const CANNOT_PAY = {
  ...PAYABLE,
  id: "l2",
  personId: "p2",
  name: "Mohan Manager",
  personKind: "MANAGER",
  state: "CANNOT_PAY",
  reason: "Manager attendance is not locked yet.",
  net: null,
  earned: null,
  deduction: null,
  adjustments: null,
};

interface Entry {
  id: string;
  paid: boolean;
  paidOn: string | null;
  mode: string | null;
  reference: string | null;
  enteredBy: string;
  enteredByName: string;
  enteredAt: string;
}

const entry = (over: Partial<Entry>): Entry => ({
  id: "e1",
  paid: true,
  paidOn: "2026-10-02",
  mode: "CASH",
  reference: null,
  enteredBy: "u-admin",
  enteredByName: "Asha Admin",
  enteredAt: "2026-10-02T10:00:00Z",
  ...over,
});

let run: Record<string, unknown> = APPROVED_RUN;
let entries: Entry[] = [];
let putResult: { status: number; body: unknown } | null = null;
let putBodies: Record<string, unknown>[] = [];
let history: unknown[] = [];

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

const newestFirst = () => [...entries].reverse();

function paymentBody() {
  const all = newestFirst();
  return { lineId: "l1", current: all[0] ?? null, earlier: all.slice(1) };
}

function respond(url: string, init?: RequestInit) {
  if (url.endsWith("/owed")) return json([]);
  if (url.endsWith("/payment")) {
    if (init?.method === "PUT") {
      const body = JSON.parse(String(init.body)) as Record<string, unknown>;
      putBodies.push(body);
      if (putResult) return json(putResult.body, putResult.status);
      entries.push(
        entry({
          id: `e${entries.length + 1}`,
          paid: body.paid as boolean,
          paidOn: (body.paidOn as string | null) ?? null,
          mode: (body.mode as string | null) ?? null,
          reference: (body.reference as string | null) ?? null,
          enteredAt: "2026-10-05T10:00:00Z",
        }),
      );
      return json(paymentBody());
    }
    return json(paymentBody());
  }
  if (url.includes("/history")) return json(history);
  if (/\/lines\/(l\d+)$/.test(url)) return json(PAYABLE);
  if (url.includes("/lines")) {
    return json({
      content: [{ ...PAYABLE, payment: newestFirst()[0] ?? null }, CANNOT_PAY],
      page: 0,
      size: 50,
      totalElements: 2,
    });
  }
  const paid = entries.length > 0 && newestFirst()[0].paid;
  return json({
    ...run,
    paidCount: paid ? 1 : 0,
    unpaidCount: paid ? 2 : 3,
    paidAmount: paid ? "26000.00" : "0.00",
  });
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

function renderHistory(mode: ThemeMode = "light") {
  return render(
    <ThemeProvider theme={buildMuiTheme(mode)}>
      <MemoryRouter initialEntries={["/operations/payroll/people/TEACHER/p1"]}>
        <Routes>
          <Route
            path="/operations/payroll/people/:kind/:personId"
            element={
              <RouteGuard>
                <PersonPayrollHistory />
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
  entries = [];
  putResult = null;
  putBodies = [];
  history = [];
  grantFor(["VIEW", "PROCESS", "EXPORT"]);
  authFetch.mockImplementation(async (url: string, init?: RequestInit) =>
    respond(url, init),
  );
});

afterEach(() => vi.restoreAllMocks());

async function openPayment(user: ReturnType<typeof userEvent.setup>) {
  await user.click(
    await screen.findByRole("button", { name: "Payment for Tara Teacher" }),
  );
  return screen.findByRole("dialog");
}

async function chooseMode(dialog: HTMLElement, label: string) {
  const user = userEvent.setup();
  await user.click(within(dialog).getByRole("combobox", { name: /Paid by/ }));
  await user.click(await screen.findByRole("option", { name: label }));
}

describe("Recording payment (spec 013b US7)", () => {
  it("saves a paid entry with the date, mode and reference, then shows it in the history and the line", async () => {
    const user = userEvent.setup();
    renderPage();
    const dialog = await openPayment(user);
    await within(dialog).findByText("Nothing has been recorded yet.");

    await user.clear(within(dialog).getByLabelText(/Payment date/));
    await user.type(
      within(dialog).getByLabelText(/Payment date/),
      "2026-10-04",
    );
    await chooseMode(dialog, "UPI");
    await user.type(within(dialog).getByLabelText(/Reference/), "UTR 99");
    await user.click(
      within(dialog).getByRole("button", { name: "Save payment" }),
    );

    await within(dialog).findByText(
      /Paid on 04\/10\/2026 by UPI, reference UTR 99/,
    );
    expect(putBodies).toEqual([
      { paid: true, paidOn: "2026-10-04", mode: "UPI", reference: "UTR 99" },
    ]);
    await user.click(within(dialog).getByRole("button", { name: "Close" }));
    expect(
      await screen.findByText("Paid on 04/10/2026 by UPI"),
    ).toBeInTheDocument();
    expect(await screen.findByText("Marked paid")).toBeInTheDocument();
  }, 20_000);

  it("asks for a mode before it sends anything, and refuses a date in the future", async () => {
    const user = userEvent.setup();
    renderPage();
    const dialog = await openPayment(user);
    await within(dialog).findByText("Nothing has been recorded yet.");

    await user.click(
      within(dialog).getByRole("button", { name: "Save payment" }),
    );
    expect(
      await within(dialog).findByText("Choose how it was paid."),
    ).toBeInTheDocument();

    await chooseMode(dialog, "Cash");
    await user.clear(within(dialog).getByLabelText(/Payment date/));
    await user.type(
      within(dialog).getByLabelText(/Payment date/),
      "2999-01-01",
    );
    await user.click(
      within(dialog).getByRole("button", { name: "Save payment" }),
    );
    expect(
      await within(dialog).findByText(
        "The payment date cannot be in the future.",
      ),
    ).toBeInTheDocument();
    expect(putBodies).toHaveLength(0);
  });

  it("keeps the entered details and shows the server's reason when it refuses", async () => {
    putResult = {
      status: 409,
      body: { reason: "Payment can be recorded only on an approved run." },
    };
    const user = userEvent.setup();
    renderPage();
    const dialog = await openPayment(user);
    await within(dialog).findByText("Nothing has been recorded yet.");
    await chooseMode(dialog, "Cheque");
    await user.type(within(dialog).getByLabelText(/Reference/), "No. 7");

    await user.click(
      within(dialog).getByRole("button", { name: "Save payment" }),
    );

    expect(
      await within(dialog).findByText(
        "Payment can be recorded only on an approved run.",
      ),
    ).toBeInTheDocument();
    expect(within(dialog).getByLabelText(/Reference/)).toHaveValue("No. 7");
  });

  it("keeps the earlier entry in the history when a payment is corrected", async () => {
    entries = [entry({ reference: "first" })];
    const user = userEvent.setup();
    renderPage();
    const dialog = await openPayment(user);
    await within(dialog).findByText(
      /Paid on 02\/10\/2026 by Cash, reference first/,
    );

    await user.clear(within(dialog).getByLabelText(/Reference/));
    await user.type(within(dialog).getByLabelText(/Reference/), "second");
    await user.click(
      within(dialog).getByRole("button", { name: "Save payment" }),
    );

    await within(dialog).findByText(/reference second.*current/);
    expect(within(dialog).getByText(/reference first/)).toBeInTheDocument();
  });

  it("offers the payment dialog only for a paid line of an approved run", async () => {
    renderPage();
    await screen.findByRole("button", { name: "Payment for Tara Teacher" });

    expect(
      screen.queryByRole("button", { name: "Payment for Mohan Manager" }),
    ).not.toBeInTheDocument();
  });

  it("shows no payment column before the run is approved", async () => {
    run = { ...APPROVED_RUN, status: "AWAITING_APPROVAL" };
    renderPage();
    await screen.findByRole("button", {
      name: "Show working for Tara Teacher",
    });

    expect(
      screen.queryByRole("button", { name: "Payment for Tara Teacher" }),
    ).not.toBeInTheDocument();
    expect(screen.queryByText("Marked paid")).not.toBeInTheDocument();
  });

  it("lets a Director with only View read the entries but not record", async () => {
    grantFor(["VIEW", "APPROVE", "EXPORT"]);
    entries = [entry({})];
    const user = userEvent.setup();
    renderPage();
    const dialog = await openPayment(user);

    await within(dialog).findByText(/Paid on 02\/10\/2026 by Cash/);
    expect(
      within(dialog).queryByRole("button", { name: "Save payment" }),
    ).not.toBeInTheDocument();
    expect(
      within(dialog).queryByLabelText(/Payment date/),
    ).not.toBeInTheDocument();
  });

  it.each(["light", "dark"] as const)(
    "has no accessibility violations with the payment dialog open in %s mode",
    async (mode) => {
      const user = userEvent.setup();
      renderPage(mode);
      const dialog = await openPayment(user);
      await within(dialog).findByText("Nothing has been recorded yet.");
      const results = await axe(document.body, {
        runOnly: { type: "tag", values: ["wcag2a", "wcag2aa", "wcag22aa"] },
      });
      expect(results.violations.map((v) => `${v.id}: ${v.help}`)).toEqual([]);
    },
    30_000,
  );
});

describe("A person's payroll history (spec 013b US7)", () => {
  const ROW = {
    runId: "r1",
    lineId: "l1",
    month: "2026-09",
    runKind: "PRIMARY",
    sequence: 1,
    status: "APPROVED",
    state: "PAYABLE",
    reason: null,
    net: "26000.00",
    payment: entry({
      mode: "BANK_TRANSFER",
      reference: "UTR 1",
    }),
  };

  it("lists each run with the month, net pay, status and payment", async () => {
    history = [
      ROW,
      {
        ...ROW,
        runId: "r0",
        lineId: "l0",
        month: "2026-08",
        net: null,
        state: "CANNOT_PAY",
        payment: null,
      },
    ];
    renderHistory();

    const table = await screen.findByRole("table", { name: "Payroll history" });
    expect(within(table).getByText("September 2026")).toBeInTheDocument();
    expect(
      within(table).getByText(
        "Paid on 02/10/2026 by Bank transfer, reference UTR 1",
      ),
    ).toBeInTheDocument();
    expect(within(table).getByText("August 2026")).toBeInTheDocument();
    expect(within(table).getByText("Not paid in this run")).toBeInTheDocument();
    expect(authFetch).toHaveBeenCalledWith(
      "/api/v1/payroll/people/TEACHER/p1/history",
    );
  });

  it("says so when the person has not been in a run", async () => {
    renderHistory();

    expect(
      await screen.findByText("This person has not been in a pay run yet."),
    ).toBeInTheDocument();
  });

  it("shows the server's message when the history cannot be loaded", async () => {
    authFetch.mockImplementation(async () =>
      json({ reason: "Could not load this person's payroll history." }, 500),
    );
    renderHistory();

    expect(
      await screen.findByText("Could not load this person's payroll history."),
    ).toBeInTheDocument();
  });

  it.each(["light", "dark"] as const)(
    "has no accessibility violations in %s mode",
    async (mode) => {
      history = [ROW];
      const { container } = renderHistory(mode);
      await screen.findByRole("table", { name: "Payroll history" });
      const results = await axe(container, {
        runOnly: { type: "tag", values: ["wcag2a", "wcag2aa", "wcag22aa"] },
      });
      expect(results.violations.map((v) => `${v.id}: ${v.help}`)).toEqual([]);
    },
    30_000,
  );
});
