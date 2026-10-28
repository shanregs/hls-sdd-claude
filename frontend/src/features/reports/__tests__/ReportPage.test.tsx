import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material/styles";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { axe } from "vitest-axe";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { RouteGuard } from "../../../app/RouteGuard";
import { buildMuiTheme, type ThemeMode } from "../../../theme/tokens";
import { ReportPage } from "../ReportPage";

const authFetch = vi.fn();
let navigation: { section: string; items: unknown[] }[] = [];

vi.mock("../../../auth/useAuth", () => ({ useAuth: () => ({ authFetch }) }));

vi.mock("../../../access-model/useAccessModel", async () => {
  const actual = await vi.importActual<
    typeof import("../../../access-model/useAccessModel")
  >("../../../access-model/useAccessModel");
  return {
    ...actual,
    useAccessModel: () => ({
      loading: false,
      accessModel: { roles: ["ADMIN"], dataScope: {}, navigation },
    }),
  };
});

function grantFor(route: string, actions: string[]) {
  navigation = [
    { section: "REPORTS", items: [{ label: "Report", route, actions }] },
  ];
}

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

const cell = (value: number | string) => ({ state: "VALUE", value });
const noData = { state: "NO_DATA", value: null };

const MENU = {
  reports: [
    {
      key: "attendance",
      title: "Attendance and LOP",
      filters: ["ZONE", "SCHOOL", "TEACHER", "MANAGER"],
      hasMonthState: true,
      canExport: true,
      defaultMonth: "2026-09",
    },
    {
      key: "payroll",
      title: "Payroll cost",
      filters: ["ZONE", "SCHOOL"],
      hasMonthState: true,
      canExport: true,
      defaultMonth: "2026-09",
    },
    {
      key: "manager",
      title: "Manager productivity",
      filters: ["ZONE", "MANAGER"],
      hasMonthState: true,
      canExport: true,
      defaultMonth: "2026-09",
    },
    {
      key: "expenses",
      title: "Expenses",
      filters: ["ZONE"],
      hasMonthState: false,
      canExport: true,
      defaultMonth: "2026-09",
    },
    {
      key: "teacher",
      title: "Teacher report",
      filters: ["ZONE", "SCHOOL", "TEACHER", "MANAGER"],
      hasMonthState: true,
      canExport: true,
      defaultMonth: "2026-09",
    },
    {
      key: "recruitment",
      title: "Recruitment",
      filters: ["SEASON"],
      hasMonthState: false,
      canExport: true,
      defaultMonth: "2026-09",
    },
  ],
  zones: [{ id: "z1", name: "North" }],
  schools: [{ id: "s1", name: "Greenfield" }],
};

const ATTENDANCE = {
  key: "attendance",
  title: "Attendance and LOP",
  month: "2026-09",
  monthState: "OPEN",
  notes: [
    "This month is still open: figures can change until attendance is locked.",
  ],
  sections: [
    {
      key: "teachers",
      title: "Teachers",
      columns: [
        { key: "school", label: "School", unit: "TEXT" },
        { key: "workingDays", label: "Working days", unit: "DAYS" },
        { key: "percent", label: "Attendance %", unit: "PERCENT" },
      ],
      rows: [
        {
          id: "t1",
          label: "Asha Rao",
          cells: [cell("Greenfield"), cell(25), cell(80.0)],
        },
        {
          id: "t2",
          label: "Bala K",
          cells: [cell("Greenfield"), cell(0), noData],
        },
      ],
      totals: {
        id: "total",
        label: "Organization",
        cells: [cell(""), cell(25), cell(80.0)],
      },
      failed: false,
      note: null,
    },
    {
      key: "managers",
      title: "Managers",
      columns: [
        { key: "percent", label: "Manager attendance %", unit: "PERCENT" },
      ],
      rows: [],
      totals: null,
      failed: true,
      note: "Could not be read",
    },
  ],
};

const PAYROLL = {
  key: "payroll",
  title: "Payroll cost",
  month: "2026-09",
  monthState: "NONE",
  notes: ["No pay run for this month yet."],
  sections: [
    {
      key: "cycle",
      title: "Month by month",
      columns: [
        { key: "state", label: "Run state", unit: "TEXT" },
        { key: "net", label: "Net", unit: "RUPEES" },
      ],
      rows: [
        {
          id: "2026-08",
          label: "2026-08",
          cells: [cell("APPROVED"), cell(114400)],
        },
        { id: "2026-09", label: "2026-09", cells: [noData, noData] },
      ],
      totals: null,
      failed: false,
      note: null,
    },
  ],
};

const notAvailable = { state: "NOT_AVAILABLE", value: null };

const RECRUITMENT = {
  key: "recruitment",
  title: "Recruitment",
  month: "2026-09",
  monthState: "NONE",
  notes: ["Season 2026-27."],
  sections: [
    {
      key: "colleges",
      title: "Colleges",
      columns: [
        { key: "city", label: "City", unit: "TEXT" },
        { key: "selected", label: "Selected", unit: "COUNT" },
        { key: "ratio", label: "Joining ratio", unit: "TEXT" },
        { key: "inducted", label: "Induction completed", unit: "COUNT" },
      ],
      rows: [
        {
          id: "c1",
          label: "Hill College",
          cells: [cell("Pune"), cell(0), noData, notAvailable],
        },
      ],
      totals: null,
      failed: false,
      note: null,
    },
  ],
};

function onePage(
  key: string,
  title: string,
  monthState: string,
  columns: { key: string; label: string; unit: string }[],
  cells: unknown[],
) {
  return {
    key,
    title,
    month: "2026-09",
    monthState,
    notes: [],
    sections: [
      {
        key: "main",
        title: "Main",
        columns,
        rows: [{ id: "r1", label: "Row one", cells }],
        totals: null,
        failed: false,
        note: null,
      },
    ],
  };
}

// The pages of the 014c reports. The screen is the generic one: columns and cells come from the server.
const PEOPLE_PAGES: Record<string, ReturnType<typeof onePage>> = {
  manager: onePage(
    "manager",
    "Manager productivity",
    "LOCKED",
    [
      { key: "percent", label: "Attendance %", unit: "PERCENT" },
      { key: "claimed", label: "Expense claimed", unit: "RUPEES" },
    ],
    [cell(92.5), noData],
  ),
  expenses: onePage(
    "expenses",
    "Expenses",
    "NONE",
    [
      { key: "actual", label: "Actual", unit: "RUPEES" },
      { key: "budget", label: "Budget", unit: "RUPEES" },
    ],
    [cell(114400), noData],
  ),
  teacher: onePage(
    "teacher",
    "Teacher report",
    "OPEN",
    [
      { key: "tenure", label: "Tenure (days)", unit: "DAYS" },
      { key: "observations", label: "Observations done", unit: "COUNT" },
    ],
    [cell(120), notAvailable],
  ),
};

const saved: string[] = [];

function respond(url: string): Response {
  if (url === "/api/v1/reports") return json(MENU);
  if (url.startsWith("/api/v1/reports/attendance/export")) {
    return {
      ok: true,
      status: 200,
      headers: new Headers({
        "Content-Disposition":
          'attachment; filename="attendance-report-2026-09.csv"',
      }),
      blob: async () => new Blob(["Report,Attendance"], { type: "text/csv" }),
    } as unknown as Response;
  }
  if (url.startsWith("/api/v1/reports/attendance")) return json(ATTENDANCE);
  if (url.startsWith("/api/v1/reports/payroll")) return json(PAYROLL);
  if (url.startsWith("/api/v1/reports/recruitment")) return json(RECRUITMENT);
  for (const [k, page] of Object.entries(PEOPLE_PAGES)) {
    if (url.startsWith(`/api/v1/reports/${k}`)) return json(page);
  }
  return json({}, 404);
}

function renderPage(key: string, mode: ThemeMode = "light") {
  return render(
    <ThemeProvider theme={buildMuiTheme(mode)}>
      <MemoryRouter initialEntries={[`/reports/${key}`]}>
        <Routes>
          <Route
            path="/reports/:key"
            element={
              <RouteGuard>
                <ReportPage />
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
  saved.length = 0;
  grantFor("/reports/attendance", ["VIEW", "EXPORT"]);
  authFetch.mockImplementation(async (url: string) => respond(url));
  URL.createObjectURL = vi.fn(() => "blob:csv");
  URL.revokeObjectURL = vi.fn();
  vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function (
    this: HTMLAnchorElement,
  ) {
    saved.push(this.download);
  });
});

afterEach(() => vi.restoreAllMocks());

describe("Reports (spec 014a)", () => {
  it("shows the month banner, the tables, the totals and says no data instead of 0", async () => {
    renderPage("attendance");

    expect(await screen.findByText("Asha Rao")).toBeInTheDocument();
    expect(screen.getByText("Month state: Open")).toBeInTheDocument();
    expect(screen.getByText(/still open/)).toBeInTheDocument();
    const teachers = screen.getByRole("table", { name: "Teachers" });
    expect(within(teachers).getByText("Organization")).toBeInTheDocument();
    expect(within(teachers).getAllByText("80%")).toHaveLength(2);
    expect(within(teachers).getByText("No data")).toBeInTheDocument();
    expect(authFetch).toHaveBeenCalledWith(
      "/api/v1/reports/attendance?month=2026-09",
    );
  });

  it("shows one failed section as a warning while the others stay", async () => {
    renderPage("attendance");

    expect(
      await screen.findByText(/Managers could not be read/),
    ).toBeInTheDocument();
    expect(screen.getByText("Asha Rao")).toBeInTheDocument();
  });

  it("asks again with the Zone, School and month the person picks", async () => {
    const user = userEvent.setup();
    renderPage("attendance");
    await screen.findByText("Asha Rao");

    await user.click(screen.getByRole("combobox", { name: "School" }));
    await user.click(await screen.findByRole("option", { name: "Greenfield" }));

    await waitFor(() =>
      expect(authFetch).toHaveBeenCalledWith(
        "/api/v1/reports/attendance?month=2026-09&schoolId=s1",
      ),
    );
  });

  it("offers Export only with the Export grant and downloads with the same filter", async () => {
    const user = userEvent.setup();
    renderPage("attendance");

    await user.click(await screen.findByRole("button", { name: "Export CSV" }));

    await waitFor(() =>
      expect(saved).toEqual(["attendance-report-2026-09.csv"]),
    );
    expect(authFetch).toHaveBeenCalledWith(
      "/api/v1/reports/attendance/export?month=2026-09",
    );
  });

  it("hides the Export button without the Export grant", async () => {
    grantFor("/reports/attendance", ["VIEW"]);
    renderPage("attendance");
    await screen.findByText("Asha Rao");

    expect(
      screen.queryByRole("button", { name: "Export CSV" }),
    ).not.toBeInTheDocument();
  });

  it("shows the payroll cycle table with rupees and a run with no data", async () => {
    grantFor("/reports/payroll", ["VIEW", "EXPORT"]);
    renderPage("payroll");

    const table = await screen.findByRole("table", { name: "Month by month" });
    expect(within(table).getByText("Approved")).toBeInTheDocument();
    expect(within(table).getByText(/1,14,400/)).toBeInTheDocument();
    expect(within(table).getAllByText("No data").length).toBeGreaterThan(0);
    expect(
      screen.getByText(/No pay run for this month yet/),
    ).toBeInTheDocument();
  });

  it.each([
    ["manager", "92.5%", "No data"],
    ["expenses", /1,14,400/, "No data"],
    ["teacher", "120", "Not available"],
  ] as const)(
    "shows the %s report from what the server sends, saying why a figure is missing",
    async (key, figure, missing) => {
      grantFor(`/reports/${key}`, ["VIEW", "EXPORT"]);
      renderPage(key);

      const table = await screen.findByRole("table", { name: "Main" });
      expect(within(table).getByText(figure)).toBeInTheDocument();
      expect(within(table).getByText(missing)).toBeInTheDocument();
    },
  );

  it("asks for a season instead of a month and sends the season the person types", async () => {
    grantFor("/reports/recruitment", ["VIEW", "EXPORT"]);
    const user = userEvent.setup();
    renderPage("recruitment");
    await screen.findByText("Hill College");

    expect(screen.queryByLabelText("Month")).not.toBeInTheDocument();
    await user.type(screen.getByLabelText("Season"), "2026-27{Enter}");

    await waitFor(() =>
      expect(authFetch).toHaveBeenCalledWith(
        expect.stringMatching(
          /^\/api\/v1\/reports\/recruitment\?.*season=2026-27/,
        ),
      ),
    );
  });

  it("says why a recruitment figure is missing instead of showing 0", async () => {
    grantFor("/reports/recruitment", ["VIEW"]);
    renderPage("recruitment");

    const table = await screen.findByRole("table", { name: "Colleges" });
    expect(within(table).getByText("No data")).toBeInTheDocument();
    expect(within(table).getByText("Not available")).toBeInTheDocument();
    expect(screen.getByText("Season 2026-27.")).toBeInTheDocument();
  });

  it("refuses a report that is not in the person menu", async () => {
    grantFor("/reports/nothing", ["VIEW"]);
    renderPage("nothing");

    expect(
      await screen.findByText("This report is not available to you."),
    ).toBeInTheDocument();
  });

  it.each(["light", "dark"] as const)(
    "has no accessibility violations in %s mode",
    async (mode) => {
      const { container } = renderPage("attendance", mode);
      await screen.findByText("Asha Rao");
      const results = await axe(container, {
        runOnly: { type: "tag", values: ["wcag2a", "wcag2aa", "wcag22aa"] },
      });
      expect(results.violations.map((v) => `${v.id}: ${v.help}`)).toEqual([]);
    },
    20_000,
  );
});
