import { CLASSIC_PRESET, type ThemeMode, type VariantColours } from "./builtinClassic";
import { accentForVariant, headerTextOn } from "./contrast";

/**
 * Design tokens for the mobile app.
 *
 * Colours come from the theme catalogue (spec 039): the server's presets, with Classic built in
 * (`builtinClassic.ts`) as the fallback, so the phone no longer keeps its own copy of the web
 * palette. Spacing and the touch target stay here (Constitution Principle IV).
 */
export const spacingUnit = 8;

export const fontFamily = undefined; // use the platform default (Roboto) on Android

export type { ThemeMode } from "./builtinClassic";

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

export function paletteFromColours(c: VariantColours): Palette {
  return {
    primary: c.primary,
    onPrimary: c.onPrimary,
    secondary: c.accent,
    error: c.error,
    background: c.background,
    surface: c.surface,
    text: c.text,
    textMuted: c.textMuted,
    border: c.border,
  };
}

export const lightPalette: Palette = paletteFromColours(CLASSIC_PRESET.light);

export const darkPalette: Palette = paletteFromColours(CLASSIC_PRESET.dark);

export const paletteFor = (mode: ThemeMode): Palette => (mode === "dark" ? darkPalette : lightPalette);

export interface ThemeOptions {
  /** The chosen preset's colours in this variant; Classic when omitted. */
  colours?: VariantColours;
  /** The user's own accent (primary) colour, re-derived for this variant when it does not pass. */
  customAccent?: string | null;
}

/** The colours a variant actually uses once a custom accent is applied (as on the web). */
export function resolveColours(mode: ThemeMode, options: ThemeOptions = {}): VariantColours {
  const base = options.colours ?? CLASSIC_PRESET[mode];
  if (!options.customAccent) return base;
  const primary = accentForVariant(options.customAccent, mode === "dark", base.surface, base.background);
  return { ...base, primary, onPrimary: headerTextOn(primary) };
}

/** Minimum touch target on Android (Material guidance and spec FR-016). */
export const minTouchTarget = 48;
