import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ManagementDashboard } from "../ManagementDashboard";

const authFetch = vi.fn();

vi.mock("../../../auth/useAuth", () => ({
  useAuth: () => ({ authFetch }),
}));

function jsonResponse(body: unknown) {
  return { ok: true, status: 200, json: async () => body } as Response;
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
  filters: {
    zones: [
      { id: "z1", name: "North" },
      { id: "z2", name: "South" },
    ],
    defaultPeriod: "2026-10",
  },
  inbox: false,
};

describe("DashboardFilters (spec 028 US8)", () => {
  beforeEach(() => {
    authFetch.mockReset();
    authFetch.mockImplementation(async (url: string) =>
      url === "/api/v1/dashboard"
        ? jsonResponse(manifest)
        : jsonResponse({
            area: "MANAGERS",
            takenAt: "2026-10-21T06:30:12Z",
            tiles: [],
          }),
    );
  });

  function renderIt() {
    render(
      <MemoryRouter>
        <ManagementDashboard title="Overview" />
      </MemoryRouter>,
    );
  }

  it("defaults the month to the current one and lists only the manifest Zones", async () => {
    renderIt();
    const month = await screen.findByLabelText("Month");
    expect(month).toHaveValue("2026-10");
    const user = userEvent.setup();
    await user.click(screen.getByRole("combobox", { name: "Zone" }));
    const options = within(screen.getByRole("listbox"))
      .getAllByRole("option")
      .map((o) => o.textContent);
    expect(options).toEqual(["All Zones", "North", "South"]);
  });

  it("reloads the areas with the chosen Zone", async () => {
    renderIt();
    const user = userEvent.setup();
    await user.click(await screen.findByRole("combobox", { name: "Zone" }));
    await user.click(screen.getByRole("option", { name: "South" }));
    await waitFor(() =>
      expect(
        authFetch.mock.calls.some((c) => String(c[0]).includes("zoneId=z2")),
      ).toBe(true),
    );
  });
});
