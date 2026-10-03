import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ManagerDashboard } from "./ManagerDashboard";

const authFetch = vi.fn();

vi.mock("../auth/useAuth", () => ({
  useAuth: () => ({ authFetch }),
}));

function jsonResponse(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

function mockCounts(zones: number, schools: number, teachers: number) {
  authFetch.mockImplementation(async (url: string) => {
    if (url.startsWith("/api/v1/me/scope")) {
      return jsonResponse({
        orgWide: false,
        zoneCount: zones,
        schoolCount: schools,
        zones: [],
      });
    }
    return jsonResponse({
      content: [],
      page: 0,
      size: 1,
      totalElements: teachers,
    });
  });
}

function renderDashboard() {
  return render(
    <MemoryRouter>
      <ManagerDashboard />
    </MemoryRouter>,
  );
}

describe("ManagerDashboard (User Story 6)", () => {
  beforeEach(() => {
    authFetch.mockReset();
  });

  it("shows the assigned zone, school and teacher counts with links to the scoped lists", async () => {
    mockCounts(2, 5, 12);

    renderDashboard();

    expect(await screen.findByText("My assigned zones")).toBeInTheDocument();
    expect(screen.getByText("2")).toBeInTheDocument();
    expect(screen.getByText("5")).toBeInTheDocument();
    expect(screen.getByText("12")).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: /view teachers/i }),
    ).toHaveAttribute("href", "/master-data/teachers");
    expect(screen.getByRole("link", { name: /view schools/i })).toHaveAttribute(
      "href",
      "/master-data/schools",
    );
    expect(authFetch.mock.calls.map((c) => String(c[0]))).toContain(
      "/api/v1/teachers?size=1",
    );
  });

  it("shows an empty state, not zeros or an error, when nothing is assigned", async () => {
    mockCounts(0, 0, 0);

    renderDashboard();

    expect(
      await screen.findByText(/nothing has been assigned to you yet/i),
    ).toBeInTheDocument();
    expect(screen.queryByText("My assigned zones")).not.toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("shows an error when the overview cannot be loaded", async () => {
    authFetch.mockResolvedValue(jsonResponse({}, 500));

    renderDashboard();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      /could not load your assigned overview/i,
    );
  });
});
