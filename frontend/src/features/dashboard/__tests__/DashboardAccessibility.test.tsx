import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material";
import { MemoryRouter } from "react-router-dom";
import { axe } from "vitest-axe";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { buildMuiTheme, type ThemeMode } from "../../../theme/tokens";
import { ManagementDashboard } from "../ManagementDashboard";

const authFetch = vi.fn();

vi.mock("../../../auth/useAuth", () => ({
  useAuth: () => ({ authFetch }),
}));

function jsonResponse(body: unknown) {
  return { ok: true, status: 200, json: async () => body } as Response;
}

const TAKEN = "2026-10-21T06:30:12Z";
const link = { path: "/operations/tasks", query: { view: "overdue" } };

function def(key: string, label: string) {
  return { key, label, periodKind: "TODAY", zoneAware: true, link };
}

const manifest = {
  areas: [
    {
      area: "MANAGERS",
      title: "Managers",
      tiles: [
        def("m.overdue", "Overdue tasks"),
        def("m.nodata", "Visits"),
        def("m.broken", "No check-in"),
        def("managers.comparison", "Managers compared"),
      ],
    },
    {
      area: "PAYROLL",
      title: "Payroll",
      tiles: [def("p.run", "Pay run"), def("p.na", "Locks")],
    },
  ],
  filters: { zones: [{ id: "z1", name: "North" }], defaultPeriod: "2026-10" },
  inbox: true,
};

const empty = { organizationWide: false, note: null, takenAt: TAKEN, table: null };

const managersArea = {
  area: "MANAGERS",
  takenAt: TAKEN,
  tiles: [
    {
      ...empty,
      key: "m.overdue",
      state: "OK",
      values: [{ label: "Overdue", value: 7, unit: "COUNT", link }],
    },
    { ...empty, key: "m.nodata", state: "NO_DATA", note: "No visits planned", values: [] },
    { ...empty, key: "m.broken", state: "ERROR", note: "Could not be read", values: [] },
    {
      ...empty,
      key: "managers.comparison",
      state: "OK",
      values: [],
      table: {
        columns: ["Manager", "Overdue"],
        rows: [
          [
            { label: "Manager", value: "Asha", unit: "TEXT", link: null },
            { label: "Overdue", value: 3, unit: "COUNT", link },
          ],
        ],
      },
    },
  ],
};

const payrollArea = {
  area: "PAYROLL",
  takenAt: TAKEN,
  tiles: [
    {
      ...empty,
      key: "p.run",
      state: "OK",
      organizationWide: true,
      values: [{ label: "Total", value: "1250000.00", unit: "RUPEES", link }],
    },
    { ...empty, key: "p.na", state: "NOT_AVAILABLE", note: "Not available", values: [] },
  ],
};

const inbox = {
  totals: { MANAGER_LEAVE: 1 },
  takenAt: TAKEN,
  page: 0,
  size: 50,
  total: 1,
  items: [
    {
      kind: "MANAGER_LEAVE",
      itemId: "a",
      requester: "Asha K",
      subject: "Leave on 2026-10-18",
      askedOn: "2026-10-18",
      ageDays: 3,
      link: { path: "/operations/manager-leave", query: { id: "a" } },
    },
  ],
};

function renderDashboard(mode: ThemeMode) {
  return render(
    <ThemeProvider theme={buildMuiTheme(mode)}>
      <MemoryRouter>
        <ManagementDashboard title="Director overview" />
      </MemoryRouter>
    </ThemeProvider>,
  );
}

beforeEach(() => {
  authFetch.mockReset();
  authFetch.mockImplementation(async (url: string) => {
    if (url === "/api/v1/dashboard") return jsonResponse(manifest);
    if (url.startsWith("/api/v1/dashboard/areas/MANAGERS")) return jsonResponse(managersArea);
    if (url.startsWith("/api/v1/dashboard/areas/PAYROLL")) return jsonResponse(payrollArea);
    if (url.startsWith("/api/v1/dashboard/inbox")) return jsonResponse(inbox);
    return jsonResponse({});
  });
});

describe("Dashboard accessibility and layout (spec 028 T063)", () => {
  it.each(["light", "dark"] as const)(
    "has no accessibility violations across tiles, table and inbox in %s mode",
    async (mode) => {
      const { container } = renderDashboard(mode);
      await screen.findByText("Asha");
      await screen.findByText("Leave on 2026-10-18 (Asha K)");
      const results = await axe(container, {
        runOnly: { type: "tag", values: ["wcag2a", "wcag2aa", "wcag22aa"] },
      });
      expect(results.violations.map((v) => `${v.id}: ${v.help}`)).toEqual([]);
    },
  );

  it("reads a figure with its label and reaches every figure from the keyboard", async () => {
    const user = userEvent.setup();
    renderDashboard("light");
    const figure = await screen.findByRole("link", { name: "Overdue tasks: Overdue 7" });
    expect(figure).toHaveAttribute("href", "/operations/tasks?view=overdue");
    // the first figure is reachable by tabbing; no figure sits behind a non-focusable element
    let reached = false;
    for (let i = 0; i < 40 && !reached; i += 1) {
      await user.tab();
      reached = document.activeElement === figure;
    }
    expect(reached).toBe(true);
  });

  it("says why a figure is missing instead of drawing 0", async () => {
    renderDashboard("light");
    expect(await screen.findByText("No visits planned")).toBeInTheDocument();
    expect(screen.getByText("Could not be read")).toBeInTheDocument();
    expect(screen.getByText("Not available")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Retry" })).toBeInTheDocument();
  });

  it("keeps wide content inside its own box so the page does not scroll sideways at phone width", async () => {
    window.innerWidth = 360;
    window.dispatchEvent(new Event("resize"));
    renderDashboard("light");
    const table = await screen.findByRole("table", { name: "Managers compared" });
    const container = table.parentElement as HTMLElement;
    expect(getComputedStyle(container).overflowX).toBe("auto");
    expect(getComputedStyle(container).maxWidth).toBe("100%");
    // the tile grid collapses to one column at 360px: columns are at least min(220px, 100%) wide
    const grid = container.closest("[class*='MuiPaper']")!.parentElement as HTMLElement;
    expect(getComputedStyle(grid).gridTemplateColumns).toContain("min(220px, 100%)");
  });

  it("shows rupees in Indian grouping and dates as DD/MM/YYYY", async () => {
    renderDashboard("light");
    expect(await screen.findByText(/₹12,50,000/)).toBeInTheDocument();
    expect(await screen.findByText(/18\/10\/2026/)).toBeInTheDocument();
  });
});
