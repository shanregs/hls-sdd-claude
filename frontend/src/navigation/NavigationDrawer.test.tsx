import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ThemeProvider } from "@mui/material/styles";
import { buildMuiTheme } from "../theme/tokens";
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

  describe("collapsible sections", () => {
    beforeEach(() => {
      const store = new Map<string, string>();
      vi.stubGlobal("localStorage", {
        getItem: (k: string) => store.get(k) ?? null,
        setItem: (k: string, v: string) => void store.set(k, v),
        removeItem: (k: string) => void store.delete(k),
        clear: () => store.clear(),
      });
      mockAccessModel.mockReturnValue({
        accessModel: {
          roles: ["ADMIN"],
          navigation: [
            {
              section: "SYSTEM",
              items: [
                {
                  label: "User Management",
                  route: "/identity/users",
                  actions: ["VIEW"],
                },
              ],
            },
            {
              section: "AUDIT",
              items: [
                {
                  label: "Audit Logs",
                  route: "/audit/logs",
                  actions: ["VIEW"],
                },
              ],
            },
          ],
          dataScope: {},
        },
        loading: false,
      });
    });

    it("starts expanded and hides a section's items when its header is clicked", async () => {
      const user = userEvent.setup();
      renderDrawer();

      const header = screen.getByRole("button", { name: "SYSTEM" });
      expect(header).toHaveAttribute("aria-expanded", "true");
      expect(screen.getByText("User Management")).toBeInTheDocument();

      await user.click(header);

      expect(header).toHaveAttribute("aria-expanded", "false");
      await waitFor(() =>
        expect(screen.queryByText("User Management")).not.toBeInTheDocument(),
      );
      // Other sections are unaffected.
      expect(screen.getByText("Audit Logs")).toBeInTheDocument();
    });

    it("shows the items again on a second click", async () => {
      const user = userEvent.setup();
      renderDrawer();
      const header = screen.getByRole("button", { name: "AUDIT" });

      await user.click(header);
      await user.click(header);

      expect(header).toHaveAttribute("aria-expanded", "true");
      expect(screen.getByText("Audit Logs")).toBeInTheDocument();
    });

    it("remembers which sections were hidden", async () => {
      const user = userEvent.setup();
      const first = renderDrawer();
      await user.click(screen.getByRole("button", { name: "SYSTEM" }));
      first.unmount();

      renderDrawer();

      expect(screen.getByRole("button", { name: "SYSTEM" })).toHaveAttribute(
        "aria-expanded",
        "false",
      );
      expect(screen.queryByText("User Management")).not.toBeInTheDocument();
    });
  });
});

describe("NavigationDrawer darker sidebar (spec 039 US5, T030)", () => {
  const fixture = {
    accessModel: {
      roles: ["TEACHER"],
      navigation: [
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
      dataScope: {},
    },
    loading: false,
  };

  function renderThemed(sidebarDark: boolean) {
    mockAccessModel.mockReturnValue(fixture);
    const theme = buildMuiTheme("light", { sidebarDark });
    render(
      <ThemeProvider theme={theme}>
        <MemoryRouter>
          <NavigationDrawer
            mobileOpen={false}
            onMobileClose={vi.fn()}
            collapsed={false}
          />
        </MemoryRouter>
      </ThemeProvider>,
    );
    return theme;
  }

  /** jsdom ignores selector specificity in getComputedStyle, so the emitted CSS rule is checked instead. */
  function paperRules(): string {
    const rules = document.head.innerHTML.match(
      /[^{}]*\.MuiDrawer-paper\{[^}]*\}/g,
    );
    return (rules ?? []).join(" ");
  }

  it("paints the sidebar in the dark shade when the user opted in", () => {
    const theme = renderThemed(true);
    const { background, text } = theme.hls.sidebar;
    expect(paperRules().toLowerCase()).toContain(
      `background-color:${background.toLowerCase()}`,
    );
    expect(paperRules().toLowerCase()).toContain(`color:${text.toLowerCase()}`);
  });

  it("keeps the normal sidebar by default", () => {
    // Earlier tests leave their rules in the document; start from a clean head.
    document.head
      .querySelectorAll("style[data-emotion]")
      .forEach((n) => n.remove());
    const theme = renderThemed(false);
    expect(paperRules().toLowerCase()).not.toContain(
      `background-color:${theme.hls.sidebar.background.toLowerCase()}`,
    );
  });
});
