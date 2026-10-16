import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material/styles";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { axe } from "vitest-axe";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { RouteGuard } from "../../../../app/RouteGuard";
import { buildMuiTheme, type ThemeMode } from "../../../../theme/tokens";
import { AdjustmentsPage } from "../AdjustmentsPage";
import { amountProblem } from "../format";

const authFetch = vi.fn();
let navigation: { section: string; items: unknown[] }[] = [];

vi.mock("../../../../auth/useAuth", () => ({
  useAuth: () => ({ authFetch, user: { id: "u1" } }),
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

const WAITING = {
  id: "a1",
  personKind: "TEACHER",
  personId: "p1",
  personName: "Tara Teacher",
  kind: "CORRECTION",
  amount: "2000.00",
  reason: "August was underpaid",
  status: "WAITING",
  attachedRunId: null,
  appliedRunId: null,
  appliedLineId: null,
  createdBy: { id: "u1", name: "Asha Admin" },
  createdAt: "2026-10-02T10:00:00Z",
  withdrawnBy: null,
  withdrawnAt: null,
  withdrawReason: null,
};
const APPLIED = {
  ...WAITING,
  id: "a2",
  personName: "Mohan Manager",
  amount: "-500.00",
  reason: "Advance recovered",
  status: "APPLIED",
  appliedRunId: "r1",
};

const PEOPLE = {
  content: [
    {
      kind: "TEACHER",
      personId: "p1",
      name: "Tara Teacher",
      designation: "Teacher",
      inEffect: null,
      joiningDate: null,
      exitDate: null,
      active: true,
      salaryEditable: false,
      roles: [],
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
};

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

function respond(url: string, init?: RequestInit) {
  if (url.includes("/salary-structures/people")) return json(PEOPLE);
  if (init?.method === "POST" && url.endsWith("/withdraw")) {
    return json({
      ...WAITING,
      status: "WITHDRAWN",
      withdrawReason: "Wrong person",
    });
  }
  if (init?.method === "POST") return json(WAITING, 201);
  return json({
    content: [WAITING, APPLIED],
    page: 0,
    size: 50,
    totalElements: 2,
  });
}

function renderPage(mode: ThemeMode = "light") {
  return render(
    <ThemeProvider theme={buildMuiTheme(mode)}>
      <MemoryRouter initialEntries={["/operations/payroll/adjustments"]}>
        <Routes>
          <Route
            path="/operations/payroll/adjustments"
            element={
              <RouteGuard>
                <AdjustmentsPage />
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
  grantFor(["VIEW", "PROCESS", "EXPORT"]);
  authFetch.mockImplementation(async (url: string, init?: RequestInit) =>
    respond(url, init),
  );
});

describe("Adjustments page (spec 013b US4)", () => {
  it("lists the adjustments with their amounts in rupees and their status", async () => {
    renderPage();
    const row = (await screen.findByText("August was underpaid")).closest(
      "tr",
    )!;
    expect(within(row).getByText("₹2,000.00")).toBeInTheDocument();
    expect(within(row).getByText("Waiting for a run")).toBeInTheDocument();
    const applied = screen.getByText("Advance recovered").closest("tr")!;
    expect(within(applied).getByText("-₹500.00")).toBeInTheDocument();
    expect(within(applied).getByText("Applied")).toBeInTheDocument();
    // only a waiting adjustment can be withdrawn
    expect(
      within(applied).queryByRole("button", { name: /Withdraw/ }),
    ).not.toBeInTheDocument();
  });

  it("lists the three reserved kinds disabled, naming the spec that will create them", async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText("August was underpaid");
    await user.click(screen.getByRole("combobox", { name: /Kind/ }));

    for (const [name, spec] of [
      ["Incentive", "spec 030"],
      ["Reimbursement", "spec 015"],
      ["Substitute credit", "spec 017b"],
    ]) {
      const option = screen.getByRole("option", {
        name: new RegExp(`${name} \\(created by ${spec}\\)`),
      });
      expect(option).toHaveAttribute("aria-disabled", "true");
    }
  });

  it("refuses a form with no person, a bad amount and no reason, sending nothing", async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText("August was underpaid");
    authFetch.mockClear();

    await user.type(screen.getByLabelText(/^Amount/), "0");
    await user.click(screen.getByRole("button", { name: "Record adjustment" }));

    expect(screen.getByText("Choose the person.")).toBeInTheDocument();
    expect(screen.getByText("The amount cannot be zero.")).toBeInTheDocument();
    expect(screen.getByText("A reason is required.")).toBeInTheDocument();
    expect(
      authFetch.mock.calls.some(([, init]) => init?.method === "POST"),
    ).toBe(false);
  });

  it("records a correction for the chosen person", async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText("August was underpaid");

    await user.click(screen.getByLabelText(/^Person/));
    await user.click(
      await screen.findByRole("option", { name: /Tara Teacher/ }),
    );
    await user.type(screen.getByLabelText(/^Amount/), "-750.50");
    await user.type(screen.getByLabelText(/^Reason/), "Advance recovered");
    await user.click(screen.getByRole("button", { name: "Record adjustment" }));

    await waitFor(() =>
      expect(authFetch).toHaveBeenCalledWith(
        "/api/v1/payroll/adjustments",
        expect.objectContaining({ method: "POST" }),
      ),
    );
    const post = authFetch.mock.calls.find(
      ([, init]) => init?.method === "POST",
    );
    expect(JSON.parse(post![1].body)).toEqual({
      personKind: "TEACHER",
      personId: "p1",
      kind: "CORRECTION",
      amount: "-750.50",
      reason: "Advance recovered",
    });
    expect(
      await screen.findByText(/The adjustment was recorded/),
    ).toBeInTheDocument();
  });

  it("shows the server's refusal", async () => {
    authFetch.mockImplementation(async (url: string, init?: RequestInit) =>
      init?.method === "POST"
        ? json({ reason: "The amount can have at most two decimals." }, 400)
        : respond(url, init),
    );
    const user = userEvent.setup();
    renderPage();
    await screen.findByText("August was underpaid");
    await user.click(screen.getByLabelText(/^Person/));
    await user.click(
      await screen.findByRole("option", { name: /Tara Teacher/ }),
    );
    await user.type(screen.getByLabelText(/^Amount/), "10.5");
    await user.type(screen.getByLabelText(/^Reason/), "x");
    await user.click(screen.getByRole("button", { name: "Record adjustment" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "at most two decimals",
    );
  });

  it("withdraws a waiting adjustment only with a reason", async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText("August was underpaid");
    await user.click(
      screen.getByRole("button", {
        name: "Withdraw the adjustment for Tara Teacher",
      }),
    );

    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Withdraw" }));
    expect(
      await within(dialog).findByText("A reason is required."),
    ).toBeInTheDocument();
    expect(
      authFetch.mock.calls.some(([url]) => String(url).endsWith("/withdraw")),
    ).toBe(false);

    await user.type(
      within(dialog).getByLabelText(/Reason for withdrawing/),
      "Wrong person",
    );
    await user.click(within(dialog).getByRole("button", { name: "Withdraw" }));
    await waitFor(() =>
      expect(authFetch).toHaveBeenCalledWith(
        "/api/v1/payroll/adjustments/a1/withdraw",
        expect.objectContaining({
          method: "POST",
          body: JSON.stringify({ reason: "Wrong person" }),
        }),
      ),
    );
  });

  it("lets a Director see the adjustments but not record or withdraw", async () => {
    grantFor(["VIEW", "APPROVE", "EXPORT"]);
    renderPage();
    await screen.findByText("August was underpaid");
    expect(screen.queryByText("Record an adjustment")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /Withdraw/ }),
    ).not.toBeInTheDocument();
  });

  it("says so when there are none, and shows an error when they cannot be loaded", async () => {
    authFetch.mockResolvedValue(
      json({ content: [], page: 0, size: 50, totalElements: 0 }),
    );
    const { unmount } = renderPage();
    expect(
      await screen.findByText(/No adjustment has been recorded yet/),
    ).toBeInTheDocument();
    unmount();

    authFetch.mockResolvedValue(
      json({ reason: "Could not load the adjustments." }, 500),
    );
    renderPage();
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Could not load the adjustments.",
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
      await screen.findByText("August was underpaid");
      const results = await axe(container, {
        runOnly: { type: "tag", values: ["wcag2a", "wcag2aa", "wcag22aa"] },
      });
      expect(results.violations.map((v) => `${v.id}: ${v.help}`)).toEqual([]);
    },
    30_000,
  );
});

describe("amountProblem", () => {
  it.each([
    ["2000", null],
    ["-500.50", null],
    ["0.01", null],
    ["", "The amount is required."],
    ["abc", "as a number"],
    ["0", "cannot be zero"],
    ["0.00", "cannot be zero"],
    ["10.123", "at most two decimals"],
    ["99999999", "too large"],
  ])("%s -> %s", (input, expected) => {
    const problem = amountProblem(input);
    if (expected === null) expect(problem).toBeNull();
    else expect(problem).toContain(expected);
  });
});
