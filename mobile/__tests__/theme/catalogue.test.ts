import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { BUILTIN_CATALOGUE, CLASSIC_PRESET, findPreset, type ThemeCatalogue } from "../../src/theme/builtinClassic";
import { passes } from "../../src/theme/contrast";
import { buildPaperTheme } from "../../src/theme/themes";
import { darkPalette, lightPalette, resolveColours } from "../../src/theme/tokens";

const server = JSON.parse(
  readFileSync(resolve(__dirname, "../../../backend/src/main/resources/themes/theme-presets.json"), "utf8"),
) as ThemeCatalogue;

describe("theme catalogue on the phone (spec 039 T014)", () => {
  it("the built-in Classic equals the server's Classic", () => {
    expect(CLASSIC_PRESET).toEqual(server.presets.find((p) => p.id === "classic"));
  });

  it("the built-in status chips equal the server's", () => {
    expect(BUILTIN_CATALOGUE.statusChips).toEqual(server.statusChips);
  });

  it("the palettes come from the catalogue, not a copy", () => {
    expect(lightPalette.primary).toBe(CLASSIC_PRESET.light.primary);
    expect(lightPalette.secondary).toBe(CLASSIC_PRESET.light.accent);
    expect(darkPalette.background).toBe(CLASSIC_PRESET.dark.background);
  });

  it("falls back to Classic for an unknown preset", () => {
    expect(findPreset(server, "no-such-preset").id).toBe("classic");
  });

  it("builds the Paper theme from the chosen preset and mode, with 12 dp corners", () => {
    const teal = findPreset(server, "teal-coral");
    const light = buildPaperTheme("light", { colours: teal.light });
    expect(light.colors.primary).toBe(teal.light.primary);
    expect(light.colors.background).toBe(teal.light.background);
    expect(light.roundness * 4).toBe(12);
    const dark = buildPaperTheme("dark", { colours: teal.dark });
    expect(dark.dark).toBe(true);
    expect(dark.colors.surface).toBe(teal.dark.surface);
  });

  it("keeps an own accent that passes and re-derives one that does not, per variant", () => {
    expect(resolveColours("light", { customAccent: "#1e5aa8" }).primary).toBe("#1E5AA8");
    for (const mode of ["light", "dark"] as const) {
      const base = CLASSIC_PRESET[mode];
      const c = resolveColours(mode, { customAccent: "#FFEB3B" });
      expect(passes(c.primary, base.surface, base.background)).toBe(true);
    }
  });
});
