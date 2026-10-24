import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { formatTime } from "../format";
import { ManagementDashboard } from "../ManagementDashboard";

const authFetch = vi.fn();

vi.mock("../../../auth/useAuth", () => ({
  useAuth: () => ({ authFetch }),
}));

function jsonResponse(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

function tileDef(key: string, label: string) {
  return {
    key,
    label,
    periodKind: "TODAY",
    zoneAware: true,
    link: { path: "/operations/tasks", query: {} },
  };
}

const manifest = {
  areas: [
    {
      area: "MANAGERS",
      title: "Managers",
      tiles: [tileDef("m.overdue", "Overdue tasks")],
    },
    {
      area: "SCHOOLS",
      title: "Schools",
      tiles: [tileDef("s.active", "Active schools")],
    },
  ],
  filters: { zones: [], defaultPeriod: "2026-10" },
  inbox: false,
};

function okTile(key: string, label: string, value: number, takenAt: string) {
  return {
    key,
    state: "OK",
    values: [
      {
        label,
        value,
        unit: "COUNT",
        link: { path: "/operations/tasks", query: { view: "overdue" } },
      },
    ],
    organizationWide: false,
    note: null,
    takenAt,
    table: null,
  };
}

function area(area: string, tiles: unknown[], takenAt: string) {
  return { area, takenAt, tiles };
}

function renderDashboard() {
  return render(
    <MemoryRouter>
      <ManagementDashboard title="Director overview" />
    </MemoryRouter>,
  );
}

const FIRST = "2026-10-21T06:30:12Z";
const SECOND = "2026-10-21T09:45:00Z";

describe("ManagementDashboard (spec 028 US1)", () => {
  beforeEach(() => {
    authFetch.mockReset();
  });

  it("renders the areas in manifest order with their figures and the time they were taken", async () => {
    authFetch.mockImplementation(async (url: string) => {
      if (url === "/api/v1/dashboard") return jsonResponse(manifest);
      if (url.startsWith("/api/v1/dashboard/areas/MANAGERS"))
        return jsonResponse(
          area("MANAGERS", [okTile("m.overdue", "Overdue", 7, FIRST)], FIRST),
        );
      return jsonResponse(
        area("SCHOOLS", [okTile("s.active", "Active", 120, FIRST)], FIRST),
      );
    });

    renderDashboard();

    expect(await screen.findByText("7")).toBeInTheDocument();
    expect(await screen.findByText("120")).toBeInTheDocument();
    const headings = screen
      .getAllByRole("heading", { level: 3 })
      .map((h) => h.textContent);
    expect(headings).toEqual(["Managers", "Schools"]);
    expect(
      screen.getByText(`Figures taken at ${formatTime(FIRST)}`),
    ).toBeInTheDocument();
    // the manifest is loaded once
    const manifestCalls = authFetch.mock.calls.filter(
      (c) => c[0] === "/api/v1/dashboard",
    );
    expect(manifestCalls).toHaveLength(1);
  });

  it("shows an area as soon as it is ready while another is still loading", async () => {
    let releaseManagers: (r: Response) => void = () => {};
    authFetch.mockImplementation((url: string) => {
      if (url === "/api/v1/dashboard")
        return Promise.resolve(jsonResponse(manifest));
      if (url.startsWith("/api/v1/dashboard/areas/MANAGERS"))
        return new Promise<Response>((resolve) => {
          releaseManagers = resolve;
        });
      return Promise.resolve(
        jsonResponse(
          area("SCHOOLS", [okTile("s.active", "Active", 120, FIRST)], FIRST),
        ),
      );
    });

    renderDashboard();

    expect(await screen.findByText("120")).toBeInTheDocument();
    const managers = screen
      .getByRole("heading", { name: "Managers" })
      .closest("section") as HTMLElement;
    expect(within(managers).getByText(/loading/i)).toBeInTheDocument();

    releaseManagers(
      jsonResponse(
        area("MANAGERS", [okTile("m.overdue", "Overdue", 7, FIRST)], FIRST),
      ),
    );
    expect(await screen.findByText("7")).toBeInTheDocument();
  });

  it("shows every state distinctly and never as 0", async () => {
    const states = [
      { key: "a", state: "NO_DATA", note: "No data yet" },
      { key: "b", state: "NOT_AVAILABLE", note: "Not available yet" },
      { key: "c", state: "NOT_WORKING_DAY", note: "Weekly off" },
      { key: "d", state: "ERROR", note: "Could not be read" },
    ].map((t) => ({
      ...t,
      values: [],
      organizationWide: false,
      takenAt: FIRST,
      table: null,
    }));
    authFetch.mockImplementation(async (url: string) => {
      if (url === "/api/v1/dashboard")
        return jsonResponse({
          ...manifest,
          areas: [
            {
              area: "MANAGERS",
              title: "Managers",
              tiles: states.map((s) => tileDef(s.key, `Tile ${s.key}`)),
            },
          ],
        });
      return jsonResponse(area("MANAGERS", states, FIRST));
    });

    renderDashboard();

    expect(await screen.findByText("No data yet")).toBeInTheDocument();
    expect(screen.getByText("Not available yet")).toBeInTheDocument();
    expect(screen.getByText("Weekly off")).toBeInTheDocument();
    expect(screen.getByText("Could not be read")).toBeInTheDocument();
    expect(screen.queryByText("0")).not.toBeInTheDocument();
  });

  it("retries one tile through the tile endpoint", async () => {
    authFetch.mockImplementation(async (url: string) => {
      if (url === "/api/v1/dashboard")
        return jsonResponse({
          ...manifest,
          areas: [manifest.areas[0]],
        });
      if (url.includes("/tiles/m.overdue"))
        return jsonResponse(okTile("m.overdue", "Overdue", 9, SECOND));
      return jsonResponse(
        area(
          "MANAGERS",
          [
            {
              key: "m.overdue",
              state: "ERROR",
              values: [],
              organizationWide: false,
              note: "Could not be read",
              takenAt: FIRST,
              table: null,
            },
          ],
          FIRST,
        ),
      );
    });

    renderDashboard();
    await userEvent.click(
      await screen.findByRole("button", { name: /retry/i }),
    );

    expect(await screen.findByText("9")).toBeInTheDocument();
    expect(authFetch.mock.calls.map((c) => String(c[0]))).toContain(
      "/api/v1/dashboard/areas/MANAGERS/tiles/m.overdue",
    );
  });

  it("reads figures only on load and on Refresh, and Refresh updates the time", async () => {
    let taken = FIRST;
    let value = 7;
    authFetch.mockImplementation(async (url: string) => {
      if (url === "/api/v1/dashboard")
        return jsonResponse({ ...manifest, areas: [manifest.areas[0]] });
      return jsonResponse(
        area("MANAGERS", [okTile("m.overdue", "Overdue", value, taken)], taken),
      );
    });

    renderDashboard();
    expect(await screen.findByText("7")).toBeInTheDocument();
    const areaCalls = () =>
      authFetch.mock.calls.filter((c) => String(c[0]).includes("/areas/"))
        .length;
    expect(areaCalls()).toBe(1);

    taken = SECOND;
    value = 8;
    await userEvent.click(screen.getByRole("button", { name: /refresh/i }));

    expect(await screen.findByText("8")).toBeInTheDocument();
    expect(
      screen.getByText(`Figures taken at ${formatTime(SECOND)}`),
    ).toBeInTheDocument();
    expect(areaCalls()).toBe(2);
  });

  it("shows an error with Retry when the area cannot be loaded", async () => {
    authFetch.mockImplementation(async (url: string) => {
      if (url === "/api/v1/dashboard")
        return jsonResponse({ ...manifest, areas: [manifest.areas[0]] });
      return jsonResponse({}, 500);
    });

    renderDashboard();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      /could not load this area/i,
    );
    expect(
      screen.getByRole("button", { name: /try again/i }),
    ).toBeInTheDocument();
  });

  it("shows an error when the Dashboard itself cannot be loaded", async () => {
    authFetch.mockResolvedValue(jsonResponse({}, 500));
    renderDashboard();
    expect(await screen.findByRole("alert")).toHaveTextContent(
      /could not load the dashboard/i,
    );
  });

  it("shows an empty state, and no Refresh, when the manifest has no areas", async () => {
    authFetch.mockResolvedValue(jsonResponse({ ...manifest, areas: [] }));
    renderDashboard();
    await waitFor(() =>
      expect(screen.getByText(/nothing to show/i)).toBeInTheDocument(),
    );
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("shows a loading state before the manifest arrives", () => {
    authFetch.mockReturnValue(new Promise(() => {}));
    renderDashboard();
    expect(screen.getByText(/loading/i)).toBeInTheDocument();
  });
});
