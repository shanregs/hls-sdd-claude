import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { CssBaseline } from "@mui/material";
import { ThemeProvider } from "@mui/material/styles";
import { buildMuiTheme, type ThemeMode } from "./tokens";

const STORAGE_KEY = "hls-theme-mode";

function readStoredMode(): ThemeMode {
  try {
    const stored = window.localStorage.getItem(STORAGE_KEY);
    return stored === "dark" ? "dark" : "light";
  } catch {
    // Storage unavailable (e.g. private browsing) - fall back to a sensible default (FR-024).
    return "light";
  }
}

function storeMode(mode: ThemeMode): void {
  try {
    window.localStorage.setItem(STORAGE_KEY, mode);
  } catch {
    // Nothing to persist to; the app still renders correctly for this session.
  }
}

interface ThemeModeContextValue {
  mode: ThemeMode;
  toggleMode: () => void;
}

const ThemeModeContext = createContext<ThemeModeContextValue | null>(null);

/**
 * Wires MUI's ThemeProvider to one persisted light/dark preference (FR-014, research.md §6).
 * Introduced here in spec 001; spec 002 wraps its shell in this same provider unchanged.
 */
export function ThemeModeProvider({ children }: { children: ReactNode }) {
  const [mode, setMode] = useState<ThemeMode>(readStoredMode);

  const toggleMode = useCallback(() => {
    setMode((previous) => {
      const next: ThemeMode = previous === "light" ? "dark" : "light";
      storeMode(next);
      return next;
    });
  }, []);

  const theme = useMemo(() => buildMuiTheme(mode), [mode]);
  const contextValue = useMemo(
    () => ({ mode, toggleMode }),
    [mode, toggleMode],
  );

  return (
    <ThemeModeContext.Provider value={contextValue}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        {children}
      </ThemeProvider>
    </ThemeModeContext.Provider>
  );
}

export function useThemeMode(): ThemeModeContextValue {
  const context = useContext(ThemeModeContext);
  if (!context) {
    throw new Error("useThemeMode must be used within a ThemeModeProvider");
  }
  return context;
}
