import {
  MD3DarkTheme,
  MD3LightTheme,
  type MD3Theme,
} from "react-native-paper";
import { paletteFor, type ThemeMode } from "./tokens";

export function buildPaperTheme(mode: ThemeMode): MD3Theme {
  const base = mode === "dark" ? MD3DarkTheme : MD3LightTheme;
  const p = paletteFor(mode);
  return {
    ...base,
    roundness: 2,
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
