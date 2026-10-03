import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { NavigationDrawer } from "./NavigationDrawer";

vi.mock("../access-model/useAccessModel", () => ({
  useAccessModel: () => ({
    accessModel: {
      roles: ["ADMIN"],
      navigation: [
        {
          section: "Dashboard",
          items: [
            { label: "Dashboard", route: "/dashboard", actions: ["VIEW"] },
          ],
        },
      ],
      dataScope: {},
    },
    loading: false,
  }),
}));

const mediaQueryMock = vi.fn();
vi.mock("@mui/material/useMediaQuery", () => ({
  default: (...args: unknown[]) => mediaQueryMock(...args),
}));

describe("NavigationDrawer responsive behavior (User Story 5, FR-009)", () => {
  it("renders as a persistent column at desktop/tablet widths", () => {
    mediaQueryMock.mockReturnValue(false); // not a phone width
    render(
      <MemoryRouter>
        <NavigationDrawer
          mobileOpen={false}
          onMobileClose={vi.fn()}
          collapsed={false}
        />
      </MemoryRouter>,
    );

    expect(
      screen.getByRole("button", { name: "Dashboard" }),
    ).toBeInTheDocument();
    expect(document.querySelector(".MuiDrawer-docked")).not.toBeNull();
  });

  it("renders as a closed overlay drawer at phone width and closes on item selection", async () => {
    mediaQueryMock.mockReturnValue(true); // phone width
    const onMobileClose = vi.fn();
    const user = userEvent.setup();

    render(
      <MemoryRouter>
        <NavigationDrawer
          mobileOpen={true}
          onMobileClose={onMobileClose}
          collapsed={false}
        />
      </MemoryRouter>,
    );

    const item = await screen.findByRole("button", { name: "Dashboard" });
    await user.click(item);

    expect(onMobileClose).toHaveBeenCalled();
  });
});
