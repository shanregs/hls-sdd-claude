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
import { AppState } from "react-native";
import { fetchAccessModel, type AccessModel } from "../api/accessModelApi";
import { NoConnectionError } from "../api/errors";
import { ACCESS_MODEL_REFRESH_AFTER_BACKGROUND_MS } from "../config/constants";
import { canOpenRoute } from "./useMenu";

type Status = "loading" | "ready" | "error" | "noConnection";

interface AccessModelContextValue {
  model: AccessModel | null;
  status: Status;
  refresh: () => void;
  canOpen: (route: string) => boolean;
}

const AccessModelContext = createContext<AccessModelContextValue | null>(null);

/**
 * Loads the caller's access model after sign-in and keeps it fresh: it is fetched on mount (which
 * is app start for a restored session, or right after sign-in), and again when the app returns from
 * the background after 5 minutes, so permission changes made on the web reach the menu without an
 * app update (spec FR-012, SC-005). The model lives in memory only.
 */
export function AccessModelProvider({ children }: { children: ReactNode }) {
  const [model, setModel] = useState<AccessModel | null>(null);
  const [status, setStatus] = useState<Status>("loading");
  const [reloadKey, setReloadKey] = useState(0);
  const refresh = useCallback(() => setReloadKey((k) => k + 1), []);
  const backgroundedAt = useRef<number | null>(null);

  useEffect(() => {
    let cancelled = false;
    fetchAccessModel()
      .then((loaded) => {
        if (cancelled) return;
        setModel(loaded);
        setStatus("ready");
      })
      .catch((error: unknown) => {
        if (cancelled) return;
        // Keep showing the last good model on a failed refresh; only a first load shows the error.
        setStatus((previous) => (previous === "ready" ? previous : error instanceof NoConnectionError ? "noConnection" : "error"));
      });
    return () => {
      cancelled = true;
    };
  }, [reloadKey]);

  useEffect(() => {
    const subscription = AppState.addEventListener("change", (next) => {
      if (next === "background" || next === "inactive") {
        backgroundedAt.current ??= Date.now();
      } else if (next === "active") {
        const since = backgroundedAt.current;
        backgroundedAt.current = null;
        if (since !== null && Date.now() - since >= ACCESS_MODEL_REFRESH_AFTER_BACKGROUND_MS) {
          refresh();
        }
      }
    });
    return () => subscription.remove();
  }, [refresh]);

  const canOpen = useCallback((route: string) => canOpenRoute(model, route), [model]);
  const value = useMemo(() => ({ model, status, refresh, canOpen }), [model, status, refresh, canOpen]);

  return <AccessModelContext.Provider value={value}>{children}</AccessModelContext.Provider>;
}

export function useAccessModel(): AccessModelContextValue {
  const context = useContext(AccessModelContext);
  if (!context) throw new Error("useAccessModel must be used inside AccessModelProvider");
  return context;
}
