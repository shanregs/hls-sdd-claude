import { act, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AdminDashboard } from "./AdminDashboard";
import { DirectorDashboard } from "./DirectorDashboard";
import { ManagerDashboard } from "./ManagerDashboard";
import { SystemDashboard } from "./SystemDashboard";
import { TeacherDashboard } from "./TeacherDashboard";

const authFetch = vi.fn();

vi.mock("../auth/useAuth", () => ({
  useAuth: () => ({ authFetch, user: { roles: [] } }),
}));

function jsonResponse(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

const manifest = {
  areas: [
    {
      area: "MANAGERS",
      title: "Managers",
      tiles: [
        {
          key: "m.overdue",
          label: "Overdue tasks",
          periodKind: "TODAY",
          zoneAware: true,
          link: { path: "/operations/tasks", query: {} },
        },
      ],
    },
  ],
  filters: { zones: [], defaultPeriod: "2026-10" },
  inbox: false,
};

beforeEach(() => {
  authFetch.mockReset();
  authFetch.mockImplementation(async (url: string) => {
    if (url === "/api/v1/dashboard") return jsonResponse(manifest);
    if (url.startsWith("/api/v1/dashboard/areas/"))
      return jsonResponse({
        area: "MANAGERS",
        takenAt: "2026-10-21T06:30:12Z",
        tiles: [
          {
            key: "m.overdue",
            state: "OK",
            values: [{ label: "Overdue", value: 7, unit: "COUNT", link: null }],
            organizationWide: false,
            note: null,
            takenAt: "2026-10-21T06:30:12Z",
            table: null,
          },
        ],
      });
    return jsonResponse({ content: [], page: 0, size: 1, totalElements: 0 });
  });
});

describe("Admin and Director dashboards (spec 028 FR-016)", () => {
  it.each([
    ["Admin", AdminDashboard],
    ["Director", DirectorDashboard],
  ])(
    "%s renders the management Dashboard and no placeholder boxes",
    async (_name, Dashboard) => {
      render(
        <MemoryRouter>
          <Dashboard />
        </MemoryRouter>,
      );

      expect(await screen.findByText("Overdue tasks")).toBeInTheDocument();
      expect(await screen.findByText("7")).toBeInTheDocument();
      expect(screen.queryByText(/coming soon/i)).not.toBeInTheDocument();
      expect(
        screen.queryByText("Organization headcount"),
      ).not.toBeInTheDocument();
      expect(
        screen.queryByText("Recent audit activity"),
      ).not.toBeInTheDocument();
      expect(authFetch.mock.calls.map((c) => String(c[0]))).toContain(
        "/api/v1/dashboard",
      );
    },
  );

  it("leaves the Teacher and System dashboards as they were, without calling the management API", async () => {
    render(
      <MemoryRouter>
        <TeacherDashboard />
        <SystemDashboard />
      </MemoryRouter>,
    );
    await act(async () => {});
    expect(authFetch.mock.calls.map((c) => String(c[0]))).not.toContain(
      "/api/v1/dashboard",
    );
  });

  it("leaves the Manager dashboard's own cards in place", async () => {
    authFetch.mockImplementation(async (url: string) =>
      url.startsWith("/api/v1/me/scope")
        ? jsonResponse({
            orgWide: false,
            zoneCount: 1,
            schoolCount: 1,
            zones: [],
          })
        : jsonResponse({ content: [], page: 0, size: 1, totalElements: 4 }),
    );
    render(
      <MemoryRouter>
        <ManagerDashboard />
      </MemoryRouter>,
    );
    expect(await screen.findByText("My assigned zones")).toBeInTheDocument();
  });
});
