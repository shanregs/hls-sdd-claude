/**
 * Design tokens for the mobile app.
 *
 * Copied from the web theme so both clients share one design system (Constitution Principle IV):
 * source of truth is `frontend/src/theme/tokens.ts` (palettes, spacing unit, font family). When the
 * web tokens change, update this file too. A shared package is planned for when a second mobile
 * spec needs more shared types (research.md §2).
 */
export const spacingUnit = 8;

export const fontFamily = undefined; // use the platform default (Roboto) on Android

export type ThemeMode = "light" | "dark";

export interface Palette {
  primary: string;
  onPrimary: string;
  secondary: string;
  error: string;
  background: string;
  surface: string;
  text: string;
  textMuted: string;
  border: string;
}

export const lightPalette: Palette = {
  primary: "#1E5AA8",
  onPrimary: "#FFFFFF",
  secondary: "#0F7B6C",
  error: "#B3261E",
  background: "#F5F7FA",
  surface: "#FFFFFF",
  text: "#1B1F24",
  textMuted: "#4A525C",
  border: "rgba(0, 0, 0, 0.12)",
};

export const darkPalette: Palette = {
  primary: "#8AB4F8",
  onPrimary: "#0B1B33",
  secondary: "#5FD6C0",
  error: "#F2B8B5",
  background: "#111418",
  surface: "#1B1F24",
  text: "#E6E9ED",
  textMuted: "#A8B0BA",
  border: "rgba(255, 255, 255, 0.16)",
};

export const paletteFor = (mode: ThemeMode): Palette =>
  mode === "dark" ? darkPalette : lightPalette;

/** Minimum touch target on Android (Material guidance and spec FR-016). */
export const minTouchTarget = 48;
