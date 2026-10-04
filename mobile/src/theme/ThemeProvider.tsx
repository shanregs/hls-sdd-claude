import AsyncStorage from "@react-native-async-storage/async-storage";
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { useColorScheme } from "react-native";
import { PaperProvider } from "react-native-paper";
import { buildPaperTheme } from "./themes";
import type { ThemeMode } from "./tokens";

export type ThemePreference = "system" | "light" | "dark";

const STORAGE_KEY = "hls.themePreference";

interface ThemeContextValue {
  /** The theme actually in use. */
  mode: ThemeMode;
  /** What the user chose: follow the device, or force light or dark (spec FR-016). */
  preference: ThemePreference;
  setPreference: (preference: ThemePreference) => void;
}

const ThemeContext = createContext<ThemeContextValue | null>(null);

/**
 * Light and dark themes: follows the device setting by default, with an in-app override that is
 * remembered (spec FR-016). The choice is a plain, non-sensitive preference, so ordinary storage is
 * used; if storage fails the app still renders in the device theme.
 */
export function ThemeProvider({ children }: { children: ReactNode }) {
  const device = useColorScheme();
  const [preference, setPreferenceState] = useState<ThemePreference>("system");

  useEffect(() => {
    let cancelled = false;
    AsyncStorage.getItem(STORAGE_KEY)
      .then((stored) => {
        if (!cancelled && (stored === "light" || stored === "dark" || stored === "system")) {
          setPreferenceState(stored);
        }
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, []);

  const setPreference = useCallback((next: ThemePreference) => {
    setPreferenceState(next);
    AsyncStorage.setItem(STORAGE_KEY, next).catch(() => undefined);
  }, []);

  const mode: ThemeMode = preference === "system" ? (device === "dark" ? "dark" : "light") : preference;
  const value = useMemo(() => ({ mode, preference, setPreference }), [mode, preference, setPreference]);
  const paperTheme = useMemo(() => buildPaperTheme(mode), [mode]);

  return (
    <ThemeContext.Provider value={value}>
      <PaperProvider theme={paperTheme}>{children}</PaperProvider>
    </ThemeContext.Provider>
  );
}

export function useThemePreference(): ThemeContextValue {
  const context = useContext(ThemeContext);
  if (!context) throw new Error("useThemePreference must be used inside ThemeProvider");
  return context;
}
