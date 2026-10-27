import { createTheme, type Theme } from "@mui/material/styles";
import { accentForVariant, headerTextOn } from "./contrast";

/**
 * One token set drives every theme (Constitution Principle IV: "one design system ... design
 * tokens for color, spacing, and type"). Spec 002 reuses these for the app shell; spec 039 builds
 * the palette from a preset of the server's catalogue, with Classic built in as the fallback.
 */
export const spacingUnit = 8;

export const typography = {
  fontFamily: '"Inter", "Segoe UI", Roboto, Helvetica, Arial, sans-serif',
};

export type ThemeMode = "light" | "dark";

/** One variant (light or dark) of a preset; the server's `theme-presets.json` has the same shape. */
export interface VariantColours {
  primary: string;
  accent: string;
  background: string;
  surface: string;
  text: string;
  textMuted: string;
  onPrimary: string;
  border: string;
  error: string;
}

export interface ThemePreset {
  id: string;
  name: string;
  light: VariantColours;
  dark: VariantColours;
}

export interface ChipColours {
  text: string;
  background: string;
}

export type ChipKind = "approved" | "pending" | "rejected";

export interface ThemeCatalogue {
  version: number;
  presets: ThemePreset[];
  statusChips: Record<ThemeMode, Record<ChipKind, ChipColours>>;
}

export const DEFAULT_PRESET_ID = "classic";

/** Built in so the app themes itself before (or without) the server; equals the server's Classic. */
export const CLASSIC_PRESET: ThemePreset = {
  id: "classic",
  name: "Classic",
  light: {
    primary: "#1E5AA8",
    accent: "#0F7B6C",
    background: "#F0F4FA",
    surface: "#FFFFFF",
    text: "#1B1F24",
    textMuted: "#4A525C",
    onPrimary: "#FFFFFF",
    border: "rgba(0, 0, 0, 0.12)",
    error: "#B3261E",
  },
  dark: {
    primary: "#8AB4F8",
    accent: "#5FD6C0",
    background: "#111418",
    surface: "#1B1F24",
    text: "#E6E9ED",
    textMuted: "#A8B0BA",
    onPrimary: "#0B1B33",
    border: "rgba(255, 255, 255, 0.16)",
    error: "#F2B8B5",
  },
};

export const BUILTIN_CATALOGUE: ThemeCatalogue = {
  version: 1,
  presets: [CLASSIC_PRESET],
  statusChips: {
    light: {
      approved: { text: "#1B5E20", background: "#E3F2E4" },
      pending: { text: "#8A4B00", background: "#FFF1D6" },
      rejected: { text: "#B3261E", background: "#FDE7E5" },
    },
    dark: {
      approved: { text: "#A5D6A7", background: "#1F3A24" },
      pending: { text: "#FFD08A", background: "#4A3410" },
      rejected: { text: "#F2B8B5", background: "#4A1F1C" },
    },
  },
};

export function findPreset(
  catalogue: ThemeCatalogue,
  id: string | null | undefined,
): ThemePreset {
  return (
    catalogue.presets.find((p) => p.id === id) ??
    catalogue.presets.find((p) => p.id === DEFAULT_PRESET_ID) ??
    CLASSIC_PRESET
  );
}

export interface ThemeOptions {
  /** The colours of the chosen preset in this variant; Classic when omitted. */
  colours?: VariantColours;
  /** The user's own accent (primary) colour, re-derived for this variant when it does not pass. */
  customAccent?: string | null;
}

/** The colours a variant actually uses once a custom accent is applied. */
export function resolveColours(
  mode: ThemeMode,
  options: ThemeOptions = {},
): VariantColours {
  const base = options.colours ?? CLASSIC_PRESET[mode];
  if (!options.customAccent) return base;
  const primary = accentForVariant(
    options.customAccent,
    mode === "dark",
    base.surface,
    base.background,
  );
  return { ...base, primary, onPrimary: headerTextOn(primary) };
}

/** Card elevation: a soft shadow in light, a hairline border in dark (research.md section 6). */
const cardShadow =
  "0 1px 3px rgba(16, 24, 40, 0.10), 0 1px 2px rgba(16, 24, 40, 0.06)";

export function buildMuiTheme(
  mode: ThemeMode,
  options: ThemeOptions = {},
): Theme {
  const c = resolveColours(mode, options);
  return createTheme({
    palette: {
      mode,
      primary: { main: c.primary, contrastText: c.onPrimary },
      secondary: { main: c.accent },
      error: { main: c.error },
      background: { default: c.background, paper: c.surface },
      text: { primary: c.text, secondary: c.textMuted },
      divider: c.border,
    },
    typography,
    spacing: spacingUnit,
    shape: { borderRadius: 12 },
    components: {
      MuiCard: {
        styleOverrides: {
          root:
            mode === "light"
              ? { boxShadow: cardShadow }
              : { boxShadow: "none", border: `1px solid ${c.border}` },
        },
      },
      // Every plain table (attendance grids, audit lists, status codes) gets bordered cells.
      MuiTableCell: {
        styleOverrides: {
          root: { border: `1px solid ${c.border}` },
        },
      },
      // The MUI X DataGrid is styled by class name so no theme augmentation is needed.
      MuiCssBaseline: {
        styleOverrides: {
          ".MuiDataGrid-columnHeader, .MuiDataGrid-cell": {
            borderRight: `1px solid ${c.border}`,
          },
          ".MuiDataGrid-cell": {
            borderBottom: `1px solid ${c.border}`,
          },
        },
      },
    },
  });
}
