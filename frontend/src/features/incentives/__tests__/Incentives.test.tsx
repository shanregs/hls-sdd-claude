import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material/styles";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { axe } from "vitest-axe";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { RouteGuard } from "../../../app/RouteGuard";
import { buildMuiTheme } from "../../../theme/tokens";
import { IncentivesPage } from "../IncentivesPage";

const authFetch = vi.fn();
let roles: string[] = ["DIRECTOR"];
let actions: string[] = ["VIEW", "APPROVE", "EXPORT"];

vi.mock("../../../auth/useAuth", () => ({ useAuth: () => ({ authFetch }) }));

vi.mock("../../../access-model/useAccessModel", async () => {
  const actual = await vi.importActual<typeof import("../../../access-model/useAccessModel")>(
    "../../../access-model/useAccessModel",
  );
  return {
    ...actual,
    useAccessModel: () => ({
      loading: false,
      accessModel: {
        roles,
        dataScope: {},
        navigation: [
          { section: "PAYROLL & FINANCE", items: [{ label: "Incentives", route: "/operations/incentives", actions }] },
        ],
      },
    }),
  };
});

function as(role: string, grants: string[]) {
  roles = [role];
  actions = grants;
}

function row(overrides: Record<string, unknown> = {}) {
  return {
    id: "i-1",
    schoolId: "s-1",
    schoolName: "Sunrise School",
    zoneName: "North Zone",
    earnerManagerId: "m-1",
    earnerName: "Mira Manager",
    contractId: "c-1",
    closureDate: "2026-10-12",
    amount: 5000,
    status: "PENDING_APPROVAL",
    mouChanged: false,
    holdReason: null,
    decidedById: null,
    decidedBy: null,
    decidedAt: null,
    approvedByEarner: false,
    rejectReason: null,
    runMonth: null,
    payrollStatus: null,
    version: 3,
    actions: ["approve", "reject"],
    ...overrides,
  };
}

const HISTORY = [{ kind: "EARNED", actor: "System", detail: "Earned by Mira Manager", at: "2026-10-12T05:00:00Z" }];

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body, blob: async () => new Blob(["x"]) } as Response;
}

function mockApi(rows: unknown[] = [row()], detail: Record<string, unknown> = row()) {
  authFetch.mockImplementation(async (url: string, init?: RequestInit) => {
    const path = String(url);
    if (init?.method === "POST" && path.endsWith("/approve")) {
      return json({ incentive: row({ status: "APPROVED", actions: [], version: 4, payrollStatus: "Waiting for the next pay run" }), history: HISTORY });
    }
    if (init?.method === "POST" && path.endsWith("/reject")) {
      return json({ incentive: row({ status: "REJECTED", actions: ["reopen"], version: 4, rejectReason: "Not closed" }), history: HISTORY });
    }
    if (init?.method === "POST" && path.endsWith("/reopen")) {
      return json({ incentive: row({ version: 5 }), history: HISTORY });
    }
    if (path.includes("/summary")) return json({ byStatus: [], byManagerMonth: [], total: 5000 });
    if (path.includes("/no-earner")) {
      return json([
        {
          id: "n-1",
          schoolId: "s-9",
          schoolName: "Legacy School",
          zoneName: "South Zone",
          contractId: null,
          reason: "APPROVED_BEFORE_APPROVAL_STEP",
          reasonText: "Excluded: the MoU was approved before the approval step",
          detail: "Approved before the approval step",
          resolved: false,
          createdAt: "2026-10-12T05:00:00Z",
        },
      ]);
    }
    if (/\/api\/v1\/incentives\/i-1$/.test(path)) return json({ incentive: detail, history: HISTORY });
    return json({ content: rows, page: 0, size: 100, totalElements: rows.length });
  });
}

function renderAt(path: string, mode: "light" | "dark" = "light") {
  return render(
    <ThemeProvider theme={buildMuiTheme(mode)}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/operations/incentives" element={<RouteGuard><IncentivesPage /></RouteGuard>} />
          <Route path="/operations/incentives/:id" element={<RouteGuard><IncentivesPage /></RouteGuard>} />
        </Routes>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

beforeEach(() => {
  authFetch.mockReset();
  as("DIRECTOR", ["VIEW", "APPROVE", "EXPORT"]);
  mockApi();
});

describe("Incentives list (spec 030)", () => {
  it("shows the Director every incentive with Indian-format rupees, DD/MM/YYYY dates, the total and the three tabs", async () => {
    renderAt("/operations/incentives");
    expect(await screen.findByText("Sunrise School")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Incentives" })).toBeInTheDocument();
    expect(screen.getByText("12/10/2026")).toBeInTheDocument();
    expect(screen.getAllByText("₹5,000.00").length).toBeGreaterThan(0);
    expect(screen.getByText("Waiting for approval")).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "All incentives" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "No earner" })).toBeInTheDocument();
    expect(screen.queryByRole("tab", { name: "Mine" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Export CSV" })).toBeInTheDocument();
  });

  it("gives a Manager their own tab and no export, no No-earner tab", async () => {
    as("MANAGER", ["VIEW", "APPROVE"]);
    renderAt("/operations/incentives");
    expect(await screen.findByRole("heading", { name: "My Incentives" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Mine" })).toBeInTheDocument();
    expect(screen.queryByRole("tab", { name: "No earner" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Export CSV" })).not.toBeInTheDocument();
    await waitFor(() => expect(authFetch.mock.calls.some(([u]) => String(u).includes("/api/v1/incentives/mine"))).toBe(true));
  });

  it("asks the server for the status, month and text the filters name", async () => {
    const user = userEvent.setup();
    renderAt("/operations/incentives");
    await screen.findByText("Sunrise School");
    authFetch.mockClear();
    await user.type(screen.getByLabelText("Search School or Manager"), "sun");
    await waitFor(() => expect(authFetch.mock.calls.some(([u]) => String(u).includes("q=sun"))).toBe(true));
    await user.click(screen.getByLabelText("Status"));
    await user.click(await screen.findByRole("option", { name: "Approved" }));
    await waitFor(() => expect(authFetch.mock.calls.some(([u]) => String(u).includes("status=APPROVED"))).toBe(true));
  });

  it("opens on the status named in a link from the dashboard", async () => {
    renderAt("/operations/incentives?status=PENDING_APPROVAL");
    await screen.findByText("Sunrise School");
    const lists = authFetch.mock.calls.map(([u]) => String(u)).filter((u) => u.includes("/api/v1/incentives?"));
    expect(lists.every((u) => u.includes("status=PENDING_APPROVAL"))).toBe(true);
  });

  it("shows an empty state and an error state", async () => {
    mockApi([]);
    const first = renderAt("/operations/incentives");
    expect(await screen.findByText("No incentives here yet.")).toBeInTheDocument();
    first.unmount();
    authFetch.mockReset();
    authFetch.mockResolvedValue(json({ reason: "Could not load" }, 500));
    renderAt("/operations/incentives");
    expect(await screen.findByRole("alert")).toHaveTextContent("Could not load");
  });

  it("lists the Schools with no earner and says why", async () => {
    const user = userEvent.setup();
    renderAt("/operations/incentives");
    await screen.findByText("Sunrise School");
    await user.click(screen.getByRole("tab", { name: "No earner" }));
    expect(await screen.findByText("Legacy School")).toBeInTheDocument();
    expect(screen.getByText("Excluded: the MoU was approved before the approval step")).toBeInTheDocument();
  });
});

describe("Incentive decisions", () => {
  it("opens the detail from a row and offers approve and reject only when the server allows them", async () => {
    const user = userEvent.setup();
    renderAt("/operations/incentives");
    await user.click(await screen.findByRole("button", { name: "Sunrise School" }));
    const drawer = await screen.findByRole("presentation");
    expect(await within(drawer).findByRole("button", { name: "Approve" })).toBeInTheDocument();
    expect(within(drawer).getByRole("button", { name: "Reject" })).toBeInTheDocument();
    expect(within(drawer).getByText("Earned by System")).toBeInTheDocument();
  });

  it("shows no decision button when the server sends no actions", async () => {
    mockApi([row({ actions: [] })], row({ actions: [] }));
    renderAt("/operations/incentives/i-1");
    expect(await screen.findByText("Earned by System")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Approve" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Reject" })).not.toBeInTheDocument();
  });

  it("approves with the version the server gave and shows the payroll status", async () => {
    const user = userEvent.setup();
    renderAt("/operations/incentives/i-1");
    await user.click(await screen.findByRole("button", { name: "Approve" }));
    expect(await screen.findByText("Waiting for the next pay run")).toBeInTheDocument();
    const call = authFetch.mock.calls.find(([u, init]) => String(u).endsWith("/approve") && init?.method === "POST");
    expect(JSON.parse(String(call?.[1]?.body))).toEqual({ version: 3 });
    expect(screen.queryByRole("button", { name: "Approve" })).not.toBeInTheDocument();
  });

  it("will not reject without a reason, then rejects with it", async () => {
    const user = userEvent.setup();
    renderAt("/operations/incentives/i-1");
    await user.click(await screen.findByRole("button", { name: "Reject" }));
    const dialog = await screen.findByRole("dialog", { name: "Reject this incentive" });
    await user.click(within(dialog).getByRole("button", { name: "Reject" }));
    expect(await within(dialog).findByText("Reason is required.")).toBeInTheDocument();
    expect(authFetch.mock.calls.some(([u]) => String(u).endsWith("/reject"))).toBe(false);
    await user.type(within(dialog).getByLabelText(/Reason/), "Not closed");
    await user.click(within(dialog).getByRole("button", { name: "Reject" }));
    await waitFor(() => expect(screen.queryByRole("dialog", { name: "Reject this incentive" })).not.toBeInTheDocument());
    const call = authFetch.mock.calls.find(([u]) => String(u).endsWith("/reject"));
    expect(JSON.parse(String(call?.[1]?.body))).toEqual({ version: 3, reason: "Not closed" });
    expect(await screen.findByRole("button", { name: "Reopen" })).toBeInTheDocument();
  });

  it("shows a conflict from the server and keeps the buttons", async () => {
    authFetch.mockImplementation(async (url: string, init?: RequestInit) => {
      if (init?.method === "POST") return json({ reason: "This incentive was already decided: approved by Dana on 12/10/2026 09:00." }, 409);
      if (String(url).includes("/summary")) return json({ byStatus: [], byManagerMonth: [], total: 0 });
      if (/i-1$/.test(String(url))) return json({ incentive: row(), history: HISTORY });
      return json({ content: [row()], page: 0, size: 100, totalElements: 1 });
    });
    const user = userEvent.setup();
    renderAt("/operations/incentives/i-1");
    await user.click(await screen.findByRole("button", { name: "Approve" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("already decided");
    expect(screen.getByRole("button", { name: "Approve" })).toBeInTheDocument();
  });

  it("marks an incentive approved by its earner and warns when the MoU changed during a run", async () => {
    mockApi([row()], row({ status: "APPROVED", actions: [], approvedByEarner: true, mouChanged: true, payrollStatus: "In a pay run awaiting approval" }));
    renderAt("/operations/incentives/i-1");
    expect(await screen.findByText("Approved by the earner")).toBeInTheDocument();
    expect(screen.getByText(/no longer counts as approved/)).toBeInTheDocument();
    expect(screen.getByText("In a pay run awaiting approval")).toBeInTheDocument();
  });

  it("shows no Reopen button on a rejected incentive unless the server's actions include it", async () => {
    mockApi([row({ status: "REJECTED", actions: [] })], row({ status: "REJECTED", actions: [], rejectReason: "Not closed" }));
    renderAt("/operations/incentives/i-1");
    expect(await screen.findByText("Not closed")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Reopen" })).not.toBeInTheDocument();
  });

  it("reopens a rejected incentive with a reason", async () => {
    mockApi([row({ status: "REJECTED", actions: ["reopen"] })], row({ status: "REJECTED", actions: ["reopen"], rejectReason: "Not closed" }));
    const user = userEvent.setup();
    renderAt("/operations/incentives/i-1");
    await user.click(await screen.findByRole("button", { name: "Reopen" }));
    const dialog = await screen.findByRole("dialog", { name: "Reopen this incentive" });
    await user.type(within(dialog).getByLabelText(/Reason/), "Rejected by mistake");
    await user.click(within(dialog).getByRole("button", { name: "Reopen" }));
    await waitFor(() => expect(authFetch.mock.calls.some(([u]) => String(u).endsWith("/reopen"))).toBe(true));
  });
});

describe("Incentives accessibility", () => {
  it.each(["light", "dark"] as const)(
    "has no accessibility violations in %s mode (list, detail, dialog)",
    async (mode) => {
      const options = { runOnly: { type: "tag" as const, values: ["wcag2a", "wcag2aa", "wcag22aa"] } };
      const list = renderAt("/operations/incentives", mode);
      await screen.findByText("Sunrise School");
      expect((await axe(list.container, options)).violations.map((v) => `${v.id}: ${v.help}`)).toEqual([]);
      list.unmount();

      const user = userEvent.setup();
      const detail = renderAt("/operations/incentives/i-1", mode);
      await user.click(await screen.findByRole("button", { name: "Reject" }));
      await screen.findByRole("dialog", { name: "Reject this incentive" });
      expect((await axe(document.body, options)).violations.map((v) => `${v.id}: ${v.help}`)).toEqual([]);
      detail.unmount();
    },
    30_000,
  );

  it("reaches the decision buttons with the keyboard", async () => {
    const user = userEvent.setup();
    renderAt("/operations/incentives/i-1");
    const approve = await screen.findByRole("button", { name: "Approve" });
    approve.focus();
    await user.tab();
    expect(screen.getByRole("button", { name: "Reject" })).toHaveFocus();
  });
});
