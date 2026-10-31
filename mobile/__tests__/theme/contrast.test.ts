import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import type { ThemePreset } from "../../src/theme/builtinClassic";
import {
  accentForVariant,
  adjustAccent,
  headerTextOn,
  isHex,
  MIN_TEXT_RATIO,
  passes,
  ratio,
} from "../../src/theme/contrast";

/** The shared vectors and the server's catalogue live at the repository root (spec 039 T003). */
const repo = (path: string) => resolve(__dirname, "../../..", path);

interface Vector {
  accent: string;
  variant: "light" | "dark";
  preset: string;
  accentOnSurface: number;
  suggested: string;
  pass: boolean;
}

const vectors = (
  JSON.parse(readFileSync(repo("specs/039-user-themes/contracts/contrast-vectors.json"), "utf8")) as { cases: Vector[] }
).cases;

const catalogue = JSON.parse(readFileSync(repo("backend/src/main/resources/themes/theme-presets.json"), "utf8")) as {
  presets: ThemePreset[];
};

describe("contrast rules on the phone (spec 039, shared vectors)", () => {
  it("has vectors to check", () => {
    expect(vectors.length).toBeGreaterThan(10);
  });

  it.each(vectors.map((v) => [`${v.accent} ${v.variant}`, v] as const))("%s matches the shared vector", (_label, vector) => {
    const colours = catalogue.presets.find((p) => p.id === vector.preset)![vector.variant];
    expect(ratio(vector.accent, colours.surface)).toBeCloseTo(vector.accentOnSurface, 1);
    expect(passes(vector.accent, colours.surface, colours.background)).toBe(vector.pass);
    const suggested = vector.pass
      ? vector.accent
      : adjustAccent(vector.accent, vector.variant === "dark", colours.surface, colours.background);
    expect(suggested).toBe(vector.suggested);
  });

  it("every shipped preset variant is readable (SC-002)", () => {
    for (const preset of catalogue.presets) {
      for (const variant of ["light", "dark"] as const) {
        const c = preset[variant];
        const label = `${preset.id} ${variant}`;
        expect(ratio(c.text, c.surface)).toBeGreaterThanOrEqual(MIN_TEXT_RATIO);
        expect(ratio(c.primary, c.surface)).toBeGreaterThanOrEqual(MIN_TEXT_RATIO);
        expect(ratio(c.accent, c.surface)).toBeGreaterThanOrEqual(MIN_TEXT_RATIO);
        expect(ratio(c.onPrimary, c.primary)).toBeGreaterThanOrEqual(MIN_TEXT_RATIO);
        expect(label).toBeTruthy();
      }
    }
  });

  it("picks white or dark header text, whichever reads better", () => {
    expect(headerTextOn("#1E5AA8")).toBe("#FFFFFF");
    expect(headerTextOn("#8AB4F8")).toBe("#0B1B33");
  });

  it("uses a passing accent as entered and adjusts a failing one", () => {
    expect(accentForVariant("#1e5aa8", false, "#FFFFFF", "#F0F4FA")).toBe("#1E5AA8");
    const adjusted = accentForVariant("#FFEB3B", false, "#FFFFFF", "#F0F4FA");
    expect(adjusted).toBe("#786C00");
    expect(passes(adjusted, "#FFFFFF", "#F0F4FA")).toBe(true);
  });

  it("recognises only #RRGGBB colours", () => {
    expect(isHex("#1E5AA8")).toBe(true);
    expect(isHex("1E5AA8")).toBe(false);
    expect(isHex("#12345")).toBe(false);
    expect(isHex(null)).toBe(false);
  });
});
