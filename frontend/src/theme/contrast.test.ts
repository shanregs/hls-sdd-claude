import { describe, expect, it } from "vitest";
import {
  accentForVariant,
  adjustAccent,
  headerTextOn,
  isHex,
  MIN_TEXT_RATIO,
  passes,
  ratio,
} from "./contrast";
import vectorFile from "../../../specs/039-user-themes/contracts/contrast-vectors.json";
import presetFile from "../../../backend/src/main/resources/themes/theme-presets.json";
import type { ThemePreset } from "./tokens";

interface Vector {
  accent: string;
  variant: "light" | "dark";
  preset: string;
  accentOnSurface: number;
  suggested: string;
  pass: boolean;
}

const vectors = (vectorFile as { cases: Vector[] }).cases;

const catalogue = presetFile as unknown as { presets: ThemePreset[] };

function variantOf(vector: Vector) {
  const preset = catalogue.presets.find((p) => p.id === vector.preset)!;
  return preset[vector.variant];
}

describe("contrast rules (spec 039, shared vectors)", () => {
  it("has vectors to check", () => {
    expect(vectors.length).toBeGreaterThan(10);
  });

  it.each(vectors.map((v) => [`${v.accent} ${v.variant}`, v] as const))(
    "%s matches the shared vector",
    (_label, vector) => {
      const colours = variantOf(vector);
      expect(ratio(vector.accent, colours.surface)).toBeCloseTo(
        vector.accentOnSurface,
        1,
      );
      expect(passes(vector.accent, colours.surface, colours.background)).toBe(
        vector.pass,
      );
      const suggested = vector.pass
        ? vector.accent
        : adjustAccent(
            vector.accent,
            vector.variant === "dark",
            colours.surface,
            colours.background,
          );
      expect(suggested).toBe(vector.suggested);
    },
  );

  it("every shipped preset variant is readable (SC-002)", () => {
    for (const preset of catalogue.presets) {
      for (const variant of ["light", "dark"] as const) {
        const c = preset[variant];
        const label = `${preset.id} ${variant}`;
        expect(
          ratio(c.text, c.surface),
          `${label} text`,
        ).toBeGreaterThanOrEqual(MIN_TEXT_RATIO);
        expect(
          ratio(c.text, c.background),
          `${label} text on background`,
        ).toBeGreaterThanOrEqual(MIN_TEXT_RATIO);
        expect(
          ratio(c.primary, c.surface),
          `${label} primary`,
        ).toBeGreaterThanOrEqual(MIN_TEXT_RATIO);
        expect(
          ratio(c.accent, c.surface),
          `${label} accent`,
        ).toBeGreaterThanOrEqual(MIN_TEXT_RATIO);
        expect(
          ratio(c.onPrimary, c.primary),
          `${label} header text`,
        ).toBeGreaterThanOrEqual(MIN_TEXT_RATIO);
      }
    }
  });

  it("picks white or dark header text, whichever reads better", () => {
    expect(headerTextOn("#1E5AA8")).toBe("#FFFFFF");
    expect(headerTextOn("#8AB4F8")).toBe("#0B1B33");
  });

  it("uses a passing accent as entered and adjusts a failing one", () => {
    expect(accentForVariant("#1e5aa8", false, "#FFFFFF", "#F0F4FA")).toBe(
      "#1E5AA8",
    );
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
