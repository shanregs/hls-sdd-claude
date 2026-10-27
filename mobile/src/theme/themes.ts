import { MD3DarkTheme, MD3LightTheme, type MD3Theme } from "react-native-paper";
import { paletteFromColours, resolveColours, type ThemeMode, type ThemeOptions } from "./tokens";

export function buildPaperTheme(mode: ThemeMode, options: ThemeOptions = {}): MD3Theme {
  const base = mode === "dark" ? MD3DarkTheme : MD3LightTheme;
  const p = paletteFromColours(resolveColours(mode, options));
  return {
    ...base,
    // Paper multiplies roundness by 4 dp, so 3 gives the 12 dp corners of spec 039.
    roundness: 3,
    colors: {
      ...base.colors,
      primary: p.primary,
      onPrimary: p.onPrimary,
      secondary: p.secondary,
      error: p.error,
      background: p.background,
      surface: p.surface,
      onSurface: p.text,
      onSurfaceVariant: p.textMuted,
      outline: p.border,
    },
  };
}
