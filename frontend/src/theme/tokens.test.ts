import { describe, expect, it } from "vitest";
import presetFile from "../../../backend/src/main/resources/themes/theme-presets.json";
import { passes } from "./contrast";
import {
  BUILTIN_CATALOGUE,
  buildMuiTheme,
  CLASSIC_PRESET,
  findPreset,
  resolveColours,
  type ThemeCatalogue,
} from "./tokens";

const server = presetFile as unknown as ThemeCatalogue;

describe("theme tokens (spec 039)", () => {
  it("the built-in Classic equals the server's Classic", () => {
    expect(CLASSIC_PRESET).toEqual(
      server.presets.find((p) => p.id === "classic"),
    );
  });

  it("the built-in status chips equal the server's", () => {
    expect(BUILTIN_CATALOGUE.statusChips).toEqual(server.statusChips);
  });

  it("builds the palette from the chosen preset and mode", () => {
    const teal = findPreset(server, "teal-coral");
    const light = buildMuiTheme("light", { colours: teal.light });
    expect(light.palette.primary.main).toBe(teal.light.primary);
    expect(light.palette.background.default).toBe(teal.light.background);
    const dark = buildMuiTheme("dark", { colours: teal.dark });
    expect(dark.palette.mode).toBe("dark");
    expect(dark.palette.background.paper).toBe(teal.dark.surface);
  });

  it("falls back to Classic for an unknown preset", () => {
    expect(findPreset(server, "no-such-preset").id).toBe("classic");
  });

  it("keeps an own accent that passes as entered", () => {
    const c = resolveColours("light", { customAccent: "#1e5aa8" });
    expect(c.primary).toBe("#1E5AA8");
    expect(c.onPrimary).toBe("#FFFFFF");
  });

  it("re-derives an own accent per variant so it stays readable", () => {
    for (const mode of ["light", "dark"] as const) {
      const base = CLASSIC_PRESET[mode];
      const c = resolveColours(mode, { customAccent: "#FFEB3B" });
      expect(passes(c.primary, base.surface, base.background)).toBe(true);
    }
  });

  it("uses a 12 px corner radius and a card border in dark", () => {
    const dark = buildMuiTheme("dark");
    expect(dark.shape.borderRadius).toBe(12);
    const card = dark.components?.MuiCard?.styleOverrides?.root as {
      border: string;
    };
    expect(card.border).toContain("1px solid");
  });
});
