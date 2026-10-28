import AsyncStorage from "@react-native-async-storage/async-storage";
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
import { useColorScheme } from "react-native";
import { PaperProvider } from "react-native-paper";
import { ApiError, NoConnectionError } from "../api/errors";
import {
  DEFAULT_PREFERENCE,
  deleteTheme,
  getTheme,
  putTheme,
  type SavedTheme,
  type ThemeResponse,
  type WireMode,
} from "../api/themeApi";
import { BUILTIN_CATALOGUE, findPreset, type ThemeCatalogue } from "./builtinClassic";
import { buildPaperTheme } from "./themes";
import type { ThemeMode } from "./tokens";

/** What the user chose for light and dark: follow the device, or force light or dark (spec 018 FR-016). */
export type ThemePreference = "system" | "light" | "dark";

/** The spec 018 value; read once to migrate it into the saved theme. */
const LEGACY_KEY = "hls.themePreference";
/** The last applied theme and catalogue: applied before the first paint, also on the sign-in screen (spec 039 US4). */
const CACHE_KEY = "hls.theme";
/** A change waiting to be sent (one slot: the last write wins, so only the newest matters). */
const PENDING_KEY = "hls.theme.pending";

interface Cached {
  saved: SavedTheme;
  catalogue: ThemeCatalogue;
}

interface Pending {
  saved: SavedTheme;
  /** The user who made the change; a different user signing in discards it. */
  userId: string | null;
  variant: ThemeMode;
}

export type SaveOutcome =
  | { ok: true; queued: boolean }
  | { ok: false; code: string; reason: string; suggested: string | null };

interface ThemeContextValue {
  /** The theme actually in use. */
  mode: ThemeMode;
  /** The light/dark choice: follow the device, or force light or dark. */
  preference: ThemePreference;
  setPreference: (preference: ThemePreference) => void;
  /** The saved theme (preset, own accent, sidebar, wire mode). */
  saved: SavedTheme;
  catalogue: ThemeCatalogue;
  /** Applies a change at once, then saves it; offline it waits and is sent later. A refused accent is reverted. */
  saveTheme: (patch: Partial<Omit<SavedTheme, "changedAt">>) => Promise<SaveOutcome>;
  resetTheme: () => Promise<void>;
  /** True while a change waits to be sent. */
  waiting: boolean;
  /** Used by `ThemeSync` (inside the sign-in provider): load after sign-in, resend a waiting change. */
  syncAfterSignIn: (userId: string) => Promise<void>;
  signedOut: () => void;
  flush: () => Promise<void>;
}

const ThemeContext = createContext<ThemeContextValue | null>(null);

const toPreference = (mode: WireMode): ThemePreference => (mode === "device" ? "system" : mode);
const toWire = (preference: ThemePreference): WireMode => (preference === "system" ? "device" : preference);

async function readJson<T>(key: string): Promise<T | null> {
  try {
    const text = await AsyncStorage.getItem(key);
    return text ? (JSON.parse(text) as T) : null;
  } catch {
    return null;
  }
}

function writeJson(key: string, value: unknown): void {
  AsyncStorage.setItem(key, JSON.stringify(value)).catch(() => undefined);
}

function remove(key: string): void {
  AsyncStorage.removeItem(key).catch(() => undefined);
}

/**
 * The user's theme (spec 039): preset, light/dark mode, own accent. Follows the device by default.
 * The last applied theme is cached on the phone and used before the first paint, so it shows on the
 * sign-in screen and offline; after sign-in the server's copy is loaded, and a change made offline
 * waits and is sent when the connection is back (the server keeps the later change).
 */
export function ThemeProvider({ children }: { children: ReactNode }) {
  const device = useColorScheme();
  const [state, setState] = useState<Cached>({ saved: DEFAULT_PREFERENCE, catalogue: BUILTIN_CATALOGUE });
  const [loaded, setLoaded] = useState(false);
  const [waiting, setWaiting] = useState(false);
  const latest = useRef(state);
  const userRef = useRef<string | null>(null);
  const flushing = useRef<Promise<void> | null>(null);
  /** True when the starting theme came from the spec 018 light/dark choice and is not on the server yet. */
  const fromLegacy = useRef(false);

  const apply = useCallback((next: Cached) => {
    latest.current = next;
    setState(next);
    writeJson(CACHE_KEY, next);
  }, []);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const cached = await readJson<Cached>(CACHE_KEY);
      let initial: Cached | null = null;
      if (cached?.saved) {
        initial = {
          saved: { ...DEFAULT_PREFERENCE, ...cached.saved },
          catalogue: cached.catalogue ?? BUILTIN_CATALOGUE,
        };
      } else {
        // Migrate the spec 018 light/dark/system choice once.
        try {
          const legacy = await AsyncStorage.getItem(LEGACY_KEY);
          if (legacy === "light" || legacy === "dark" || legacy === "system") {
            initial = { saved: { ...DEFAULT_PREFERENCE, mode: toWire(legacy) }, catalogue: BUILTIN_CATALOGUE };
            fromLegacy.current = true;
          }
        } catch {
          // Storage failed: the app still renders in the device theme.
        }
      }
      const pending = await readJson<Pending>(PENDING_KEY);
      if (cancelled) return;
      if (initial) {
        latest.current = initial;
        setState(initial);
      }
      setWaiting(pending !== null);
      setLoaded(true);
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const resolve = useCallback(
    (saved: SavedTheme): ThemeMode =>
      saved.mode === "device" ? (device === "dark" ? "dark" : "light") : saved.mode,
    [device],
  );
  const mode = resolve(state.saved);

  /** Sends the waiting change; the answer is the winning row (the server keeps the later change). */
  const flush = useCallback(async () => {
    if (flushing.current) return flushing.current;
    const run = (async () => {
      const pending = await readJson<Pending>(PENDING_KEY);
      if (!pending) {
        setWaiting(false);
        return;
      }
      if (pending.userId !== userRef.current) {
        // Not this user's change (or nobody is signed in yet): leave it until its owner signs in, or drop it.
        if (userRef.current !== null) {
          remove(PENDING_KEY);
          setWaiting(false);
        }
        return;
      }
      try {
        const answer: ThemeResponse = await putTheme({
          preset: pending.saved.preset,
          mode: pending.saved.mode,
          customAccent: pending.saved.customAccent,
          sidebarDark: pending.saved.sidebarDark,
          changedAt: pending.saved.changedAt ?? new Date().toISOString(),
          variant: pending.variant,
        });
        remove(PENDING_KEY);
        setWaiting(false);
        apply({ saved: answer.preference, catalogue: answer.catalogue });
      } catch (e) {
        if (e instanceof ApiError && !(e instanceof NoConnectionError) && e.status === 422) {
          remove(PENDING_KEY);
          setWaiting(false);
        }
        // Offline or a server problem: it stays and is tried again.
      }
    })().finally(() => {
      flushing.current = null;
    });
    flushing.current = run;
    return run;
  }, [apply]);

  const signedOut = useCallback(() => {
    userRef.current = null;
  }, []);

  const saveTheme = useCallback(
    async (patch: Partial<Omit<SavedTheme, "changedAt">>): Promise<SaveOutcome> => {
      const before = latest.current;
      const saved: SavedTheme = { ...before.saved, ...patch, changedAt: new Date().toISOString() };
      apply({ saved, catalogue: before.catalogue });
      const pending: Pending = { saved, userId: userRef.current, variant: resolve(saved) };
      writeJson(PENDING_KEY, pending);
      setWaiting(true);
      if (userRef.current === null) return { ok: true, queued: true };
      try {
        const answer = await putTheme({
          preset: saved.preset,
          mode: saved.mode,
          customAccent: saved.customAccent,
          sidebarDark: saved.sidebarDark,
          changedAt: saved.changedAt!,
          variant: pending.variant,
        });
        remove(PENDING_KEY);
        setWaiting(false);
        if (answer.applied === false) apply({ saved: answer.preference, catalogue: answer.catalogue });
        return { ok: true, queued: false };
      } catch (e) {
        if (e instanceof ApiError && !(e instanceof NoConnectionError) && e.status === 422) {
          remove(PENDING_KEY);
          setWaiting(false);
          apply(before);
          const body = (e.body ?? {}) as { code?: string; suggested?: string | null };
          return { ok: false, code: body.code ?? "REFUSED", reason: e.message, suggested: body.suggested ?? null };
        }
        return { ok: true, queued: true };
      }
    },
    [apply, resolve],
  );

  const syncAfterSignIn = useCallback(
    async (userId: string) => {
      userRef.current = userId;
      const pending = await readJson<Pending>(PENDING_KEY);
      if (pending && pending.userId === userId) {
        await flush();
        return;
      }
      if (pending) {
        remove(PENDING_KEY);
        setWaiting(false);
      }
      try {
        const answer = await getTheme();
        if (answer.isDefault && fromLegacy.current && latest.current.saved.mode !== "device") {
          // A user with no saved theme who had a light/dark choice on this phone: upload it once.
          fromLegacy.current = false;
          remove(LEGACY_KEY);
          const kept = latest.current;
          apply({ saved: kept.saved, catalogue: answer.catalogue });
          await saveTheme({});
          return;
        }
        fromLegacy.current = false;
        remove(LEGACY_KEY);
        apply({ saved: answer.preference, catalogue: answer.catalogue });
      } catch {
        // No connection: the cached theme stays.
      }
    },
    [apply, flush, saveTheme],
  );

  const resetTheme = useCallback(async () => {
    apply({ saved: DEFAULT_PREFERENCE, catalogue: latest.current.catalogue });
    remove(PENDING_KEY);
    setWaiting(false);
    remove(LEGACY_KEY);
    if (userRef.current === null) return;
    try {
      await deleteTheme();
    } catch {
      // Offline: send the defaults when the connection is back (same result as a reset).
      writeJson(PENDING_KEY, {
        saved: { ...DEFAULT_PREFERENCE, changedAt: new Date().toISOString() },
        userId: userRef.current,
        variant: "light",
      } satisfies Pending);
      setWaiting(true);
    }
  }, [apply]);

  const setPreference = useCallback(
    (next: ThemePreference) => {
      void saveTheme({ mode: toWire(next) });
    },
    [saveTheme],
  );

  const paperTheme = useMemo(
    () =>
      buildPaperTheme(mode, {
        colours: findPreset(state.catalogue, state.saved.preset)[mode],
        customAccent: state.saved.customAccent,
      }),
    [mode, state],
  );

  const value = useMemo(
    () => ({
      mode,
      preference: toPreference(state.saved.mode),
      setPreference,
      saved: state.saved,
      catalogue: state.catalogue,
      saveTheme,
      resetTheme,
      waiting,
      syncAfterSignIn,
      signedOut,
      flush,
    }),
    [mode, state, setPreference, saveTheme, resetTheme, waiting, syncAfterSignIn, signedOut, flush],
  );

  // Hold the first render until the cached theme is read (a few milliseconds) so the screen never flashes the wrong one.
  if (!loaded) return null;

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
