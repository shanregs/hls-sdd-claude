import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { NavigationDrawer } from "./NavigationDrawer";

const mockAccessModel = vi.fn();

vi.mock("../access-model/useAccessModel", () => ({
  useAccessModel: () => mockAccessModel(),
}));

function renderDrawer() {
  return render(
    <MemoryRouter>
      <NavigationDrawer
        mobileOpen={false}
        onMobileClose={vi.fn()}
        collapsed={false}
      />
    </MemoryRouter>,
  );
}

describe("NavigationDrawer (User Story 1, FR-006/FR-007/FR-008)", () => {
  it("renders only the sections and items the access model returns, in order", () => {
    mockAccessModel.mockReturnValue({
      accessModel: {
        roles: ["TEACHER"],
        navigation: [
          {
            section: "Dashboard",
            items: [
              { label: "Dashboard", route: "/dashboard", actions: ["VIEW"] },
            ],
          },
          {
            section: "ACCOUNT",
            items: [
              {
                label: "My Profile",
                route: "/account/profile",
                actions: ["VIEW"],
              },
            ],
          },
        ],
        dataScope: { DASHBOARD: "OWN" },
      },
      loading: false,
    });

    renderDrawer();

    // "Dashboard" is both the section heading and the item label in this fixture.
    expect(screen.getAllByText("Dashboard")).toHaveLength(2);
    expect(screen.getByText("My Profile")).toBeInTheDocument();
    expect(screen.getByText("ACCOUNT")).toBeInTheDocument();
  });

  it("never renders an item belonging to another role (e.g. Role & Permissions for a Manager fixture)", () => {
    mockAccessModel.mockReturnValue({
      accessModel: {
        roles: ["MANAGER"],
        navigation: [
          {
            section: "Dashboard",
            items: [
              { label: "Dashboard", route: "/dashboard", actions: ["VIEW"] },
            ],
          },
          {
            section: "ACCOUNT",
            items: [
              {
                label: "Profile",
                route: "/account/profile",
                actions: ["VIEW"],
              },
            ],
          },
        ],
        dataScope: { DASHBOARD: "ASSIGNED" },
      },
      loading: false,
    });

    renderDrawer();

    expect(screen.queryByText("Role & Permissions")).not.toBeInTheDocument();
    expect(screen.queryByText("SYSTEM")).not.toBeInTheDocument();
  });

  it("omits a section entirely rather than rendering it empty when there is no access model yet", () => {
    mockAccessModel.mockReturnValue({ accessModel: null, loading: true });

    renderDrawer();

    expect(screen.queryByText("Dashboard")).not.toBeInTheDocument();
    expect(
      screen.getByRole("navigation", { name: /main navigation/i }),
    ).toBeInTheDocument();
  });
});
