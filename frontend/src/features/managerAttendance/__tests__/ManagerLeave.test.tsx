import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material/styles";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { axe } from "vitest-axe";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { RouteGuard } from "../../../app/RouteGuard";
import { buildMuiTheme, type ThemeMode } from "../../../theme/tokens";
import { ManagerLeaveDecisionsPage } from "../ManagerLeaveDecisionsPage";
import { MyManagerLeavePage } from "../MyManagerLeavePage";

const authFetch = vi.fn();
let navigation: { section: string; items: unknown[] }[] = [];
let roles: string[] = ["MANAGER"];

vi.mock("../../../auth/useAuth", () => ({ useAuth: () => ({ authFetch }) }));

vi.mock("../../../access-model/useAccessModel", async () => {
  const actual = await vi.importActual<
    typeof import("../../../access-model/useAccessModel")
  >("../../../access-model/useAccessModel");
  return {
    ...actual,
    useAccessModel: () => ({
      loading: false,
      accessModel: { roles, dataScope: {}, navigation },
    }),
  };
});

const MY_ROUTE = "/manager-attendance/me/leave";
const DECISIONS_ROUTE = "/operations/manager-leave";

const nav = (section: string, route: string, actions: string[]) => [
  { section, items: [{ label: "Page", route, actions }] },
];

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

const TYPES = [
  { id: "t1", name: "Casual", lossOfPay: false },
  { id: "t2", name: "Loss of Pay", lossOfPay: true },
];

const LEAVE = {
  id: "l1",
  managerId: "m1",
  managerName: "Asha Manager",
  leaveTypeId: "t1",
  leaveTypeName: "Casual",
  lossOfPay: false,
  firstDate: "2026-09-03",
  lastDate: "2026-09-04",
  firstHalf: false,
  lastHalf: false,
  reason: "Family function",
  status: "PENDING",
  requestedAt: "2026-09-01T05:00:00Z",
  decidedBy: null,
  decidedAt: null,
  decisionReason: null,
};

const CHECK_IN = {
  id: "r1",
  managerId: "m2",
  managerName: "Ravi Manager",
  date: "2026-09-02",
  proposedTime: "09:15:00",
  dayType: "OFFICE",
  purpose: null,
  reason: "Phone was off",
  status: "PENDING",
  decisionReason: null,
};

function respondMine(overrides: { requests?: object[]; post?: Response } = {}) {
  authFetch.mockImplementation(async (url: string, init?: RequestInit) => {
    if (init?.method === "POST") return overrides.post ?? json(LEAVE, 201);
    if (init?.method === "DELETE") return json({}, 204);
    if (String(url).endsWith("/me/leave/types")) return json(TYPES);
    return json(overrides.requests ?? []);
  });
}

function respondDecisions(
  overrides: { leave?: object[]; post?: Response } = {},
) {
  authFetch.mockImplementation(async (url: string, init?: RequestInit) => {
    if (init?.method === "POST") return overrides.post ?? json(LEAVE);
    if (String(url).includes("/manager-attendance/regularizations")) {
      return json([CHECK_IN]);
    }
    return json(overrides.leave ?? [LEAVE]);
  });
}

function renderAt(
  route: string,
  element: React.ReactNode,
  mode: ThemeMode = "light",
) {
  return render(
    <ThemeProvider theme={buildMuiTheme(mode)}>
      <MemoryRouter initialEntries={[route]}>
        <Routes>
          <Route path={route} element={<RouteGuard>{element}</RouteGuard>} />
        </Routes>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

const posts = () => authFetch.mock.calls.filter((c) => c[1]?.method === "POST");
const lastBody = () =>
  JSON.parse(posts()[posts().length - 1][1].body as string);

beforeEach(() => {
  authFetch.mockReset();
  roles = ["MANAGER"];
  navigation = nav("MY ATTENDANCE", MY_ROUTE, ["VIEW", "CREATE", "DELETE"]);
});

afterEach(() => {
  vi.restoreAllMocks();
});

async function fillLeave(
  user: ReturnType<typeof userEvent.setup>,
  over: { type?: string; first?: string; last?: string; reason?: string } = {},
) {
  await screen.findByRole("button", { name: "Send request" });
  if (over.type !== "") {
    await user.click(screen.getByRole("combobox", { name: "Leave type" }));
    await user.click(
      await screen.findByRole("option", { name: over.type ?? "Casual" }),
    );
  }
  await user.type(
    screen.getByLabelText("First day"),
    over.first ?? "2026-09-03",
  );
  await user.type(screen.getByLabelText("Last day"), over.last ?? "2026-09-04");
  if (over.reason !== "") {
    await user.type(
      screen.getByLabelText("Reason"),
      over.reason ?? "Family function",
    );
  }
}

describe("My leave (spec 032 US3)", () => {
  it("shows a Manager the request form and their requests", async () => {
    respondMine({ requests: [LEAVE] });
    renderAt(MY_ROUTE, <MyManagerLeavePage />);
    expect(
      await screen.findByRole("heading", { name: "Ask for leave" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/Casual, 03\/09\/2026 to 04\/09\/2026/),
    ).toBeInTheDocument();
    expect(screen.getByText("Pending")).toBeInTheDocument();
  });

  it("marks the Loss of Pay type as unpaid in the list", async () => {
    respondMine();
    const user = userEvent.setup();
    renderAt(MY_ROUTE, <MyManagerLeavePage />);
    await screen.findByRole("button", { name: "Send request" });
    await user.click(screen.getByRole("combobox", { name: "Leave type" }));
    expect(
      await screen.findByRole("option", { name: "Loss of Pay (unpaid)" }),
    ).toBeInTheDocument();
  });

  it("refuses an incomplete request before sending", async () => {
    respondMine();
    const user = userEvent.setup();
    renderAt(MY_ROUTE, <MyManagerLeavePage />);
    await user.click(
      await screen.findByRole("button", { name: "Send request" }),
    );
    expect(await screen.findByText("Choose a leave type.")).toBeInTheDocument();
    expect(posts()).toHaveLength(0);
  });

  it("refuses reversed dates, a missing reason and two half days of one day", async () => {
    respondMine();
    const user = userEvent.setup();
    renderAt(MY_ROUTE, <MyManagerLeavePage />);
    await fillLeave(user, { first: "2026-09-04", last: "2026-09-03" });
    await user.click(screen.getByRole("button", { name: "Send request" }));
    expect(
      await screen.findByText(/last day cannot be before the first day/),
    ).toBeInTheDocument();

    await user.clear(screen.getByLabelText("First day"));
    await user.type(screen.getByLabelText("First day"), "2026-09-03");
    await user.clear(screen.getByLabelText("Last day"));
    await user.type(screen.getByLabelText("Last day"), "2026-09-03");
    await user.click(
      screen.getByRole("checkbox", { name: "First day is a half day" }),
    );
    await user.click(
      screen.getByRole("checkbox", { name: "Last day is a half day" }),
    );
    await user.click(screen.getByRole("button", { name: "Send request" }));
    expect(await screen.findByText(/two half days/)).toBeInTheDocument();

    await user.click(
      screen.getByRole("checkbox", { name: "Last day is a half day" }),
    );
    await user.clear(screen.getByLabelText("Reason"));
    await user.click(screen.getByRole("button", { name: "Send request" }));
    expect(
      await screen.findByText("A reason is required."),
    ).toBeInTheDocument();
    expect(posts()).toHaveLength(0);
  });

  it("sends a complete request", async () => {
    respondMine();
    const user = userEvent.setup();
    renderAt(MY_ROUTE, <MyManagerLeavePage />);
    await fillLeave(user);
    await user.click(screen.getByRole("button", { name: "Send request" }));
    await waitFor(() => expect(posts()).toHaveLength(1));
    expect(lastBody()).toEqual({
      leaveTypeId: "t1",
      firstDate: "2026-09-03",
      lastDate: "2026-09-04",
      firstHalf: false,
      lastHalf: false,
      reason: "Family function",
    });
  });

  it("shows the server's refusal", async () => {
    respondMine({
      post: json(
        { reason: "You already have a pending or approved leave request." },
        409,
      ),
    });
    const user = userEvent.setup();
    renderAt(MY_ROUTE, <MyManagerLeavePage />);
    await fillLeave(user);
    await user.click(screen.getByRole("button", { name: "Send request" }));
    expect(
      await screen.findByText(/already have a pending or approved/),
    ).toBeInTheDocument();
  });

  it("cancels a pending request but offers no cancel on an approved one", async () => {
    respondMine({
      requests: [LEAVE, { ...LEAVE, id: "l2", status: "APPROVED" }],
    });
    const user = userEvent.setup();
    renderAt(MY_ROUTE, <MyManagerLeavePage />);
    const cancels = await screen.findAllByRole("button", {
      name: /Cancel the leave/,
    });
    expect(cancels).toHaveLength(1);
    await user.click(cancels[0]);
    await waitFor(() =>
      expect(
        authFetch.mock.calls.some(
          (c) =>
            c[1]?.method === "DELETE" && String(c[0]).endsWith("/me/leave/l1"),
        ),
      ).toBe(true),
    );
  });

  it("hides the form and the cancel button without CREATE and DELETE", async () => {
    navigation = nav("MY ATTENDANCE", MY_ROUTE, ["VIEW"]);
    respondMine({ requests: [LEAVE] });
    renderAt(MY_ROUTE, <MyManagerLeavePage />);
    await screen.findByRole("heading", { name: "My requests" });
    expect(
      screen.queryByRole("button", { name: "Send request" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /Cancel the leave/ }),
    ).not.toBeInTheDocument();
  });

  it("is refused for a role without the page", async () => {
    roles = ["ADMIN"];
    navigation = [];
    respondMine();
    renderAt(MY_ROUTE, <MyManagerLeavePage />);
    await waitFor(() =>
      expect(
        screen.queryByRole("heading", { name: "My leave" }),
      ).not.toBeInTheDocument(),
    );
    expect(authFetch).not.toHaveBeenCalled();
  });

  it.each<ThemeMode>(["light", "dark"])(
    "has no accessibility violations in the %s theme",
    async (mode) => {
      respondMine({ requests: [LEAVE] });
      const { container } = renderAt(MY_ROUTE, <MyManagerLeavePage />, mode);
      await screen.findByRole("heading", { name: "My requests" });
      const results = await axe(container, {
        runOnly: { type: "tag", values: ["wcag2a", "wcag2aa", "wcag22aa"] },
      });
      expect(results.violations.map((v) => `${v.id}: ${v.help}`)).toEqual([]);
    },
    30_000,
  );
});

describe("Manager leave decisions (spec 032 US3)", () => {
  beforeEach(() => {
    roles = ["ADMIN"];
    navigation = nav("OPERATIONS", DECISIONS_ROUTE, ["VIEW", "APPROVE"]);
  });

  it("shows pending leave and forgotten check-ins to an Admin", async () => {
    respondDecisions();
    renderAt(DECISIONS_ROUTE, <ManagerLeaveDecisionsPage />);
    expect(await screen.findByText(/Asha Manager/)).toBeInTheDocument();
    expect(screen.getByText(/Ravi Manager/)).toBeInTheDocument();
    expect(screen.getByText(/Phone was off/)).toBeInTheDocument();
  });

  it("approves a leave request", async () => {
    respondDecisions();
    const user = userEvent.setup();
    renderAt(DECISIONS_ROUTE, <ManagerLeaveDecisionsPage />);
    await user.click(
      await screen.findByRole("button", {
        name: /Approve leave of Asha Manager/,
      }),
    );
    await waitFor(() => expect(posts()).toHaveLength(1));
    expect(String(posts()[0][0])).toMatch(/\/leave\/l1\/approve$/);
  });

  it("asks for a reason before rejecting and sends it", async () => {
    respondDecisions();
    const user = userEvent.setup();
    renderAt(DECISIONS_ROUTE, <ManagerLeaveDecisionsPage />);
    await user.click(
      await screen.findByRole("button", {
        name: /Reject leave of Asha Manager/,
      }),
    );
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Reject" }));
    expect(
      await within(dialog).findByText("A reason is required."),
    ).toBeInTheDocument();
    expect(posts()).toHaveLength(0);

    await user.type(within(dialog).getByLabelText("Reason"), "Month end");
    await user.click(within(dialog).getByRole("button", { name: "Reject" }));
    await waitFor(() => expect(posts()).toHaveLength(1));
    expect(String(posts()[0][0])).toMatch(/\/leave\/l1\/reject$/);
    expect(lastBody()).toEqual({ reason: "Month end" });
  });

  it("revokes an approved request with a reason", async () => {
    respondDecisions({ leave: [{ ...LEAVE, status: "APPROVED" }] });
    const user = userEvent.setup();
    renderAt(DECISIONS_ROUTE, <ManagerLeaveDecisionsPage />);
    await user.click(
      await screen.findByRole("button", {
        name: /Revoke leave of Asha Manager/,
      }),
    );
    const dialog = await screen.findByRole("dialog");
    await user.type(within(dialog).getByLabelText("Reason"), "Needed at work");
    await user.click(within(dialog).getByRole("button", { name: "Revoke" }));
    await waitFor(() => expect(posts()).toHaveLength(1));
    expect(String(posts()[0][0])).toMatch(/\/leave\/l1\/revoke$/);
    expect(lastBody()).toEqual({ reason: "Needed at work" });
  });

  it("approves and rejects a forgotten check-in", async () => {
    respondDecisions();
    const user = userEvent.setup();
    renderAt(DECISIONS_ROUTE, <ManagerLeaveDecisionsPage />);
    await user.click(
      await screen.findByRole("button", {
        name: /Approve check-in of Ravi Manager/,
      }),
    );
    await waitFor(() => expect(posts()).toHaveLength(1));
    expect(String(posts()[0][0])).toMatch(/\/regularizations\/r1\/approve$/);

    await user.click(
      screen.getByRole("button", { name: /Reject check-in of Ravi Manager/ }),
    );
    const dialog = await screen.findByRole("dialog");
    await user.type(within(dialog).getByLabelText("Reason"), "No such visit");
    await user.click(within(dialog).getByRole("button", { name: "Reject" }));
    await waitFor(() => expect(posts()).toHaveLength(2));
    expect(String(posts()[1][0])).toMatch(/\/regularizations\/r1\/reject$/);
  });

  it("shows the server's refusal of a decision", async () => {
    respondDecisions({
      post: json({ reason: "The month 2026-09 is locked for payroll." }, 409),
    });
    const user = userEvent.setup();
    renderAt(DECISIONS_ROUTE, <ManagerLeaveDecisionsPage />);
    await user.click(
      await screen.findByRole("button", {
        name: /Approve leave of Asha Manager/,
      }),
    );
    expect(await screen.findByText(/locked for payroll/)).toBeInTheDocument();
  });

  it("asks the server for the status that was chosen", async () => {
    respondDecisions();
    const user = userEvent.setup();
    renderAt(DECISIONS_ROUTE, <ManagerLeaveDecisionsPage />);
    await screen.findByText(/Asha Manager/);
    await user.click(screen.getByRole("combobox", { name: "Show" }));
    await user.click(await screen.findByRole("option", { name: "Approved" }));
    await waitFor(() =>
      expect(
        authFetch.mock.calls.some((c) =>
          String(c[0]).includes("/leave?status=APPROVED"),
        ),
      ).toBe(true),
    );
  });

  it("hides every decision button without APPROVE", async () => {
    navigation = nav("OPERATIONS", DECISIONS_ROUTE, ["VIEW"]);
    respondDecisions();
    renderAt(DECISIONS_ROUTE, <ManagerLeaveDecisionsPage />);
    await screen.findByText(/Asha Manager/);
    expect(
      screen.queryByRole("button", { name: /Approve/ }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /Reject/ }),
    ).not.toBeInTheDocument();
  });

  it("is refused for a Manager and for a Teacher", async () => {
    for (const role of ["MANAGER", "TEACHER"]) {
      roles = [role];
      navigation = [];
      authFetch.mockReset();
      respondDecisions();
      const { unmount } = renderAt(
        DECISIONS_ROUTE,
        <ManagerLeaveDecisionsPage />,
      );
      await waitFor(() =>
        expect(
          screen.queryByRole("heading", { name: "Manager leave" }),
        ).not.toBeInTheDocument(),
      );
      expect(authFetch).not.toHaveBeenCalled();
      unmount();
    }
  });

  it.each<ThemeMode>(["light", "dark"])(
    "has no accessibility violations in the %s theme",
    async (mode) => {
      respondDecisions();
      const { container } = renderAt(
        DECISIONS_ROUTE,
        <ManagerLeaveDecisionsPage />,
        mode,
      );
      await screen.findByText(/Asha Manager/);
      const results = await axe(container, {
        runOnly: { type: "tag", values: ["wcag2a", "wcag2aa", "wcag22aa"] },
      });
      expect(results.violations.map((v) => `${v.id}: ${v.help}`)).toEqual([]);
    },
    30_000,
  );
});
