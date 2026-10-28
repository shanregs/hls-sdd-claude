import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { CssBaseline } from "@mui/material";
import { ThemeProvider } from "@mui/material/styles";
import { useOptionalAuth } from "../auth/useAuth";
import {
  DEFAULT_PREFERENCE,
  deleteTheme,
  getTheme,
  putTheme,
  type PreferredMode,
  type ThemePreference,
  type ThemeSaveResult,
} from "./themeApi";
import {
  BUILTIN_CATALOGUE,
  buildMuiTheme,
  findPreset,
  type ThemeCatalogue,
  type ThemeMode,
} from "./tokens";

/** The spec 001 light/dark value; read once to migrate it to the server (spec 039 research section 1). */
const LEGACY_MODE_KEY = "hls-theme-mode";
/** The last applied preference and catalogue, so the first paint already has the right theme. */
const CACHE_KEY = "hls-theme-preference";

interface Cached {
  preference: ThemePreference;
  catalogue: ThemeCatalogue;
}

function readStorage(key: string): string | null {
  try {
    return window.localStorage.getItem(key);
  } catch {
    // Storage unavailable (e.g. private browsing) - fall back to defaults (FR-024).
    return null;
  }
}

function writeStorage(key: string, value: string | null): void {
  try {
    if (value === null) window.localStorage.removeItem(key);
    else window.localStorage.setItem(key, value);
  } catch {
    // Nothing to persist to; the app still renders correctly for this session.
  }
}

function readCache(): Cached | null {
  const text = readStorage(CACHE_KEY);
  if (!text) return null;
  try {
    const parsed = JSON.parse(text) as Partial<Cached>;
    if (!parsed.preference) return null;
    return {
      preference: { ...DEFAULT_PREFERENCE, ...parsed.preference },
      catalogue: parsed.catalogue ?? BUILTIN_CATALOGUE,
    };
  } catch {
    return null;
  }
}

function initialState(): Cached {
  const cached = readCache();
  if (cached) return cached;
  const legacy = readStorage(LEGACY_MODE_KEY);
  const mode: PreferredMode =
    legacy === "dark" ? "dark" : legacy === "light" ? "light" : "device";
  return {
    preference: { ...DEFAULT_PREFERENCE, mode },
    catalogue: BUILTIN_CATALOGUE,
  };
}

function deviceIsDark(): boolean {
  try {
    return window.matchMedia?.("(prefers-color-scheme: dark)").matches ?? false;
  } catch {
    return false;
  }
}

export type PreferencePatch = Partial<Omit<ThemePreference, "changedAt">>;

interface ThemeModeContextValue {
  /** The variant in use right now (the "device" choice already resolved). */
  mode: ThemeMode;
  toggleMode: () => void;
  preference: ThemePreference;
  catalogue: ThemeCatalogue;
  /** Applies a change at once, then saves it; a refused accent is reverted and reported. */
  savePreference: (patch: PreferencePatch) => Promise<ThemeSaveResult | null>;
  resetPreference: () => Promise<boolean>;
  /** True while the last change could not be saved (the screen shows a notice and a retry). */
  unsaved: boolean;
  retrySave: () => Promise<void>;
}

const ThemeModeContext = createContext<ThemeModeContextValue | null>(null);

/**
 * Wires MUI's ThemeProvider to the user's saved theme: preset, mode (light, dark or device), own
 * accent and sidebar option (spec 039). Applies the local cache before the first render, then loads
 * the server's copy after sign-in. Introduced in spec 001 as a single light/dark switch.
 */
export function ThemeModeProvider({ children }: { children: ReactNode }) {
  const auth = useOptionalAuth();
  const authFetch = auth?.authFetch;
  const userId = auth?.user?.id ?? null;

  const [state, setState] = useState<Cached>(initialState);
  const [systemDark, setSystemDark] = useState<boolean>(deviceIsDark);
  const [unsaved, setUnsaved] = useState(false);
  const latest = useRef(state);

  useEffect(() => {
    latest.current = state;
  }, [state]);

  useEffect(() => {
    const query = window.matchMedia?.("(prefers-color-scheme: dark)");
    if (!query) return;
    const onChange = () => setSystemDark(query.matches);
    query.addEventListener?.("change", onChange);
    return () => query.removeEventListener?.("change", onChange);
  }, []);

  const apply = useCallback((next: Cached) => {
    latest.current = next;
    setState(next);
    writeStorage(CACHE_KEY, JSON.stringify(next));
  }, []);

  // After sign-in: adopt the server's copy; a user with no saved theme who had a spec 001 choice
  // uploads it once.
  useEffect(() => {
    if (!authFetch || !userId) return;
    let cancelled = false;
    void getTheme(authFetch).then(async (response) => {
      if (cancelled || !response) return;
      const legacy = readStorage(LEGACY_MODE_KEY);
      if (response.isDefault && (legacy === "light" || legacy === "dark")) {
        const changedAt = new Date().toISOString();
        const saved = await putTheme(authFetch, {
          ...DEFAULT_PREFERENCE,
          mode: legacy,
          changedAt,
          variant: legacy,
        });
        writeStorage(LEGACY_MODE_KEY, null);
        if (cancelled) return;
        if (saved.ok) {
          apply({
            preference: saved.data.preference,
            catalogue: saved.data.catalogue,
          });
          return;
        }
      }
      writeStorage(LEGACY_MODE_KEY, null);
      apply({
        preference: response.preference,
        catalogue: response.catalogue,
      });
      setUnsaved(false);
    });
    return () => {
      cancelled = true;
    };
  }, [authFetch, userId, apply]);

  const mode: ThemeMode =
    state.preference.mode === "device"
      ? systemDark
        ? "dark"
        : "light"
      : state.preference.mode;

  const send = useCallback(
    async (preference: ThemePreference, variant: ThemeMode) => {
      if (!authFetch || !userId) return null;
      return putTheme(authFetch, {
        preset: preference.preset,
        mode: preference.mode,
        customAccent: preference.customAccent,
        sidebarDark: preference.sidebarDark,
        changedAt: preference.changedAt ?? new Date().toISOString(),
        variant,
      });
    },
    [authFetch, userId],
  );

  const savePreference = useCallback(
    async (patch: PreferencePatch) => {
      const before = latest.current;
      const next: Cached = {
        catalogue: before.catalogue,
        preference: {
          ...before.preference,
          ...patch,
          changedAt: new Date().toISOString(),
        },
      };
      apply(next);
      const variant: ThemeMode =
        next.preference.mode === "device"
          ? deviceIsDark()
            ? "dark"
            : "light"
          : next.preference.mode;
      const result = await send(next.preference, variant);
      if (result === null) return null;
      if (result.ok) {
        setUnsaved(false);
        if (result.data.applied === false) {
          apply({
            preference: result.data.preference,
            catalogue: result.data.catalogue,
          });
        }
        return result;
      }
      if (result.code === "NETWORK" || result.code === "FAILED") {
        setUnsaved(true);
      } else {
        apply(before);
      }
      return result;
    },
    [apply, send],
  );

  const retrySave = useCallback(async () => {
    const result = await send(latest.current.preference, mode);
    if (result?.ok) setUnsaved(false);
  }, [send, mode]);

  const resetPreference = useCallback(async () => {
    apply({
      preference: DEFAULT_PREFERENCE,
      catalogue: latest.current.catalogue,
    });
    writeStorage(LEGACY_MODE_KEY, null);
    if (!authFetch || !userId) return true;
    const ok = await deleteTheme(authFetch);
    setUnsaved(!ok);
    return ok;
  }, [apply, authFetch, userId]);

  const toggleMode = useCallback(() => {
    void savePreference({ mode: mode === "light" ? "dark" : "light" });
  }, [mode, savePreference]);

  const theme = useMemo(() => {
    const preset = findPreset(state.catalogue, state.preference.preset);
    return buildMuiTheme(mode, {
      colours: preset[mode],
      customAccent: state.preference.customAccent,
      sidebarDark: state.preference.sidebarDark,
      catalogue: state.catalogue,
    });
  }, [mode, state]);

  const contextValue = useMemo(
    () => ({
      mode,
      toggleMode,
      preference: state.preference,
      catalogue: state.catalogue,
      savePreference,
      resetPreference,
      unsaved,
      retrySave,
    }),
    [
      mode,
      toggleMode,
      state,
      savePreference,
      resetPreference,
      unsaved,
      retrySave,
    ],
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
