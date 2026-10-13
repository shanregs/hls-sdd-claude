import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material/styles";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { axe } from "vitest-axe";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { RouteGuard } from "../../../app/RouteGuard";
import { buildMuiTheme, type ThemeMode } from "../../../theme/tokens";
import { ManagerAttendanceGridPage } from "../ManagerAttendanceGridPage";

const authFetch = vi.fn();
let navigation: { section: string; items: unknown[] }[] = [];
let roles: string[] = ["ADMIN"];

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

const GRID_ROUTE = "/operations/manager-attendance";

const nav = (actions: string[]) => [
  {
    section: "OPERATIONS",
    items: [{ label: "Manager Attendance", route: GRID_ROUTE, actions }],
  },
];

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

const thisMonth = new Date().toISOString().slice(0, 7);
const DAYS = 30;

const GRID = {
  month: thisMonth,
  firstDate: `${thisMonth}-01`,
  daysInMonth: DAYS,
  managers: [
    {
      managerId: "m1",
      name: "Asha Manager",
      employeeId: "E-1",
      locked: false,
      figures: {
        workingDays: 22,
        daysWorked: 18,
        leavePaid: 0,
        leaveUnpaid: 0,
        unpaid: 2,
        fieldDays: 1,
        pending: [],
      },
      figuresUnavailable: null,
      statuses: Array.from({ length: DAYS }, (_, i) =>
        i === 0 ? "WORKED" : i === 1 ? "ABSENT" : "WEEKLY_OFF",
      ),
    },
    {
      managerId: "m2",
      name: "Ravi Manager",
      employeeId: null,
      locked: true,
      figures: null,
      figuresUnavailable: "The joining date has not been recorded.",
      statuses: Array.from({ length: DAYS }, () => "NOT_EMPLOYED"),
    },
  ],
};

const emptyDay = (date: string, status: string, extra: object = {}) => ({
  date,
  status,
  id: null,
  dayType: null,
  purpose: null,
  checkInAt: null,
  checkOutAt: null,
  late: false,
  hasPhoto: false,
  leave: null,
  flags: [],
  activities: [],
  checkInPlace: null,
  checkOutPlace: null,
  history: [],
  ...extra,
});

const DETAIL = {
  month: thisMonth,
  managerId: "m1",
  managerName: "Asha Manager",
  locked: false,
  figures: GRID.managers[0].figures,
  figuresUnavailable: null,
  days: [
    emptyDay(`${thisMonth}-01`, "WORKED", {
      id: "d1",
      dayType: "OFFICE",
      checkInAt: "2026-10-01T12:00:00Z",
      checkOutAt: "2026-10-01T13:00:00Z",
      checkInPlace: { latitude: 12.97, longitude: 77.59, accuracyMeters: 10 },
      flags: ["CORRECTED"],
      history: [
        {
          id: "h1",
          action: "CORRECTED",
          changedBy: "u1",
          changedByName: "Asha Admin",
          changedAt: "2026-10-02T05:00:00Z",
          reason: "Left a note",
          beforeJson: null,
          afterJson: null,
        },
      ],
    }),
    emptyDay(`${thisMonth}-02`, "ABSENT"),
  ],
};

function respond(
  overrides: { detail?: object; put?: Response; grid?: Response } = {},
) {
  authFetch.mockImplementation(async (url: string, init?: RequestInit) => {
    const u = String(url);
    if (init?.method === "PUT") return overrides.put ?? json(DETAIL.days[0]);
    if (u.includes("/api/v1/zones")) {
      return json({
        content: [{ id: "z1", name: "North" }],
        page: 0,
        size: 200,
        totalElements: 1,
      });
    }
    if (u.includes("/manager-attendance/grid")) {
      return overrides.grid ?? json(GRID);
    }
    if (u.includes("/manager-attendance/managers/")) {
      return json({ ...DETAIL, ...overrides.detail });
    }
    return json({}, 404);
  });
}

function renderPage(mode: ThemeMode = "light") {
  return render(
    <ThemeProvider theme={buildMuiTheme(mode)}>
      <MemoryRouter initialEntries={[GRID_ROUTE]}>
        <Routes>
          <Route
            path={GRID_ROUTE}
            element={
              <RouteGuard>
                <ManagerAttendanceGridPage />
              </RouteGuard>
            }
          />
        </Routes>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

const puts = () => authFetch.mock.calls.filter((c) => c[1]?.method === "PUT");
const gridCalls = () =>
  authFetch.mock.calls
    .map((c) => String(c[0]))
    .filter((u) => u.includes("/manager-attendance/grid"));

beforeEach(() => {
  authFetch.mockReset();
  roles = ["ADMIN"];
  navigation = nav(["VIEW", "EDIT"]);
  respond();
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("Manager attendance grid (spec 032 US5)", () => {
  it("lists every Manager with a status per day and the month figures", async () => {
    renderPage();
    const table = await screen.findByRole("table", {
      name: "Manager attendance by day",
    });
    expect(within(table).getByText("Asha Manager")).toBeInTheDocument();
    expect(within(table).getByText("Ravi Manager")).toBeInTheDocument();
    expect(
      within(table).getByRole("button", {
        name: `Asha Manager, ${thisMonth}-01: Worked`,
      }),
    ).toBeInTheDocument();
    expect(
      within(table).getByRole("button", {
        name: `Asha Manager, ${thisMonth}-02: Absent`,
      }),
    ).toBeInTheDocument();
    const ashaRow = within(table).getByRole("row", { name: /Asha Manager/ });
    expect(within(ashaRow).getByText("18")).toBeInTheDocument();
    expect(within(table).getByText("Locked")).toBeInTheDocument();
  });

  it("asks for the grid by Zone and by name", async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByRole("table", { name: "Manager attendance by day" });

    await user.click(screen.getByRole("combobox", { name: "Zone" }));
    await user.click(await screen.findByRole("option", { name: "North" }));
    await waitFor(() =>
      expect(gridCalls().some((u) => u.includes("zoneId=z1"))).toBe(true),
    );

    await user.type(screen.getByLabelText(/Search by name/), "ravi");
    await waitFor(() =>
      expect(gridCalls().some((u) => u.includes("q=ravi"))).toBe(true),
    );
  });

  it("moves to another month", async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByRole("table", { name: "Manager attendance by day" });
    await user.click(screen.getByRole("button", { name: "Previous month" }));
    await waitFor(() =>
      expect(gridCalls().filter((u) => !u.includes(thisMonth)).length).toBe(1),
    );
  });

  it("says so when no Manager matches", async () => {
    respond({ grid: json({ ...GRID, managers: [] }) });
    renderPage();
    expect(await screen.findByText("No Managers match.")).toBeInTheDocument();
  });

  it("shows the server's refusal", async () => {
    respond({
      grid: json({ reason: "The month must look like 2026-11." }, 400),
    });
    renderPage();
    expect(
      await screen.findByText("The month must look like 2026-11."),
    ).toBeInTheDocument();
  });

  it("opens a day with its locations and history", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(
      await screen.findByRole("button", {
        name: `Asha Manager, ${thisMonth}-01: Worked`,
      }),
    );
    const dialog = await screen.findByRole("dialog");
    expect(
      await within(dialog).findByText(/Check-in place: 12.97000/),
    ).toBeInTheDocument();
    expect(within(dialog).getByText(/by Asha Admin/)).toBeInTheDocument();
    expect(within(dialog).getByText(/Reason: Left a note/)).toBeInTheDocument();
  });

  it("requires a reason before sending a correction", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(
      await screen.findByRole("button", {
        name: `Asha Manager, ${thisMonth}-01: Worked`,
      }),
    );
    const dialog = await screen.findByRole("dialog");
    await user.click(
      await within(dialog).findByRole("button", { name: "Save correction" }),
    );
    expect(
      await within(dialog).findByText("A reason is required."),
    ).toBeInTheDocument();
    expect(puts()).toHaveLength(0);
  });

  it("sends a correction with its reason and refreshes the grid", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(
      await screen.findByRole("button", {
        name: `Asha Manager, ${thisMonth}-01: Worked`,
      }),
    );
    const dialog = await screen.findByRole("dialog");
    await user.type(
      await within(dialog).findByLabelText("Reason"),
      "Left a note",
    );
    const before = gridCalls().length;
    await user.click(
      within(dialog).getByRole("button", { name: "Save correction" }),
    );
    await waitFor(() => expect(puts()).toHaveLength(1));
    expect(String(puts()[0][0])).toMatch(
      new RegExp(`/managers/m1/days/${thisMonth}-01$`),
    );
    expect(JSON.parse(puts()[0][1].body as string)).toMatchObject({
      reason: "Left a note",
      dayType: "OFFICE",
    });
    await waitFor(() => expect(gridCalls().length).toBeGreaterThan(before));
  });

  it("shows the server's refusal of a correction", async () => {
    respond({ put: json({ reason: "That month is locked." }, 409) });
    const user = userEvent.setup();
    renderPage();
    await user.click(
      await screen.findByRole("button", {
        name: `Asha Manager, ${thisMonth}-01: Worked`,
      }),
    );
    const dialog = await screen.findByRole("dialog");
    await user.type(await within(dialog).findByLabelText("Reason"), "Fix");
    await user.click(
      within(dialog).getByRole("button", { name: "Save correction" }),
    );
    expect(
      await within(dialog).findByText("That month is locked."),
    ).toBeInTheDocument();
  });

  it("hides the correction form without EDIT", async () => {
    navigation = nav(["VIEW"]);
    const user = userEvent.setup();
    renderPage();
    await user.click(
      await screen.findByRole("button", {
        name: `Asha Manager, ${thisMonth}-01: Worked`,
      }),
    );
    const dialog = await screen.findByRole("dialog");
    await within(dialog).findByText(/Check-in place/);
    expect(
      within(dialog).queryByRole("button", { name: "Save correction" }),
    ).not.toBeInTheDocument();
  });

  it("hides the correction form for a locked month", async () => {
    respond({ detail: { locked: true } });
    const user = userEvent.setup();
    renderPage();
    await user.click(
      await screen.findByRole("button", {
        name: `Asha Manager, ${thisMonth}-01: Worked`,
      }),
    );
    const dialog = await screen.findByRole("dialog");
    expect(
      await within(dialog).findByText(/locked for payroll/),
    ).toBeInTheDocument();
    expect(
      within(dialog).queryByRole("button", { name: "Save correction" }),
    ).not.toBeInTheDocument();
  });

  it("is refused for a role without the page", async () => {
    roles = ["MANAGER"];
    navigation = [];
    renderPage();
    await waitFor(() =>
      expect(
        screen.queryByRole("table", { name: "Manager attendance by day" }),
      ).not.toBeInTheDocument(),
    );
    expect(gridCalls()).toHaveLength(0);
  });

  it.each<ThemeMode>(["light", "dark"])(
    "has no accessibility violations in the %s theme",
    async (mode) => {
      const { container } = renderPage(mode);
      await screen.findByRole("table", { name: "Manager attendance by day" });
      const results = await axe(container, {
        runOnly: { type: "tag", values: ["wcag2a", "wcag2aa", "wcag22aa"] },
      });
      expect(results.violations.map((v) => `${v.id}: ${v.help}`)).toEqual([]);
    },
    30_000,
  );
});
