/**
 * The catalogue shapes and the built-in Classic theme (spec 039 research section 2).
 *
 * The server owns the preset catalogue (`backend/src/main/resources/themes/theme-presets.json`) and
 * returns it with the user's preference. This built-in copy of Classic lets the app theme itself
 * before, or without, the server; a test asserts it equals the server's Classic.
 */
export type ThemeMode = "light" | "dark";

/** One variant (light or dark) of a preset; the server's JSON has the same shape. */
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

/** Status chip colours are semantic: they do not change with the preset (spec 039 US5). */
export const BUILTIN_STATUS_CHIPS: ThemeCatalogue["statusChips"] = {
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
};

export const BUILTIN_CATALOGUE: ThemeCatalogue = {
  version: 1,
  presets: [CLASSIC_PRESET],
  statusChips: BUILTIN_STATUS_CHIPS,
};

export function findPreset(catalogue: ThemeCatalogue, id: string | null | undefined): ThemePreset {
  return (
    catalogue.presets.find((p) => p.id === id) ??
    catalogue.presets.find((p) => p.id === DEFAULT_PRESET_ID) ??
    CLASSIC_PRESET
  );
}
