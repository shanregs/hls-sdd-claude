import { render, screen } from "@testing-library/react";
import { ThemeProvider } from "@mui/material/styles";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import presetFile from "../../../backend/src/main/resources/themes/theme-presets.json";
import { ratio } from "../theme/contrast";
import {
  buildMuiTheme,
  resolveColours,
  type ThemePreset,
} from "../theme/tokens";
import { AppShell } from "./AppShell";

vi.mock("../auth/useAuth", () => ({
  useAuth: () => ({
    user: { id: "u1", displayName: "Priya Manager", roles: ["MANAGER"] },
    logout: vi.fn(),
  }),
}));
vi.mock("../theme/ThemeModeProvider", () => ({
  useThemeMode: () => ({ mode: "light", toggleMode: vi.fn() }),
}));
vi.mock("../navigation/NavigationDrawer", () => ({
  NavigationDrawer: () => null,
}));
vi.mock("../features/notifications/NotificationBell", () => ({
  NotificationBell: () => null,
}));

const presets = (presetFile as unknown as { presets: ThemePreset[] }).presets;

describe("AppShell header (spec 039 US5)", () => {
  it.each(
    presets.flatMap((p) =>
      (["light", "dark"] as const).map((mode) => [p.id, mode, p] as const),
    ),
  )(
    "%s %s: the header takes the primary colour and its text reads on it",
    (_id, mode, preset) => {
      const theme = buildMuiTheme(mode, { colours: preset[mode] });
      render(
        <ThemeProvider theme={theme}>
          <MemoryRouter>
            <AppShell />
          </MemoryRouter>
        </ThemeProvider>,
      );

      const header = screen.getByRole("banner");
      expect(header).toHaveStyle({
        backgroundColor: preset[mode].primary,
        color: preset[mode].onPrimary,
      });
      expect(
        ratio(preset[mode].onPrimary, preset[mode].primary),
      ).toBeGreaterThanOrEqual(4.5);
    },
  );

  it("keeps the header readable with an own accent", () => {
    const theme = buildMuiTheme("light", { customAccent: "#FFEB3B" });
    render(
      <ThemeProvider theme={theme}>
        <MemoryRouter>
          <AppShell />
        </MemoryRouter>
      </ThemeProvider>,
    );

    const c = resolveColours("light", { customAccent: "#FFEB3B" });
    expect(screen.getByRole("banner")).toHaveStyle({
      backgroundColor: c.primary,
      color: c.onPrimary,
    });
    expect(ratio(c.onPrimary, c.primary)).toBeGreaterThanOrEqual(4.5);
  });
});
