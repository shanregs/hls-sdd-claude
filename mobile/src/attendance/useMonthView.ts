import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError, NoConnectionError } from "../api/errors";

export type MonthViewStatus = "loading" | "ready" | "error" | "noConnection" | "notFound";

export interface MonthViewState<T> {
  status: MonthViewStatus;
  /** The loaded view; only present while `status` is "ready", never kept after a failed load (FR-020). */
  view: T | null;
  /** The server's text for a not-found or failed load, shown as given. */
  message: string | null;
  /** Loads again. A quiet reload keeps the current view on screen until the new one arrives. */
  reload: (options?: { quiet?: boolean }) => void;
}

interface Outcome<T> {
  month: string;
  tick: number;
  status: Exclude<MonthViewStatus, "loading">;
  view: T | null;
  message: string | null;
}

/**
 * Loads one month of attendance and exposes its state. A failed load never leaves the previous
 * month's data shown as current: the view is dropped and an error state is shown with a way to retry.
 */
export function useMonthView<T>(loader: (month: string) => Promise<T>, month: string): MonthViewState<T> {
  const [outcome, setOutcome] = useState<Outcome<T> | null>(null);
  const [request, setRequest] = useState({ tick: 0, quiet: false });
  const loaderRef = useRef(loader);
  useEffect(() => {
    loaderRef.current = loader;
  });

  useEffect(() => {
    let cancelled = false;
    const { tick } = request;
    loaderRef
      .current(month)
      .then((view) => {
        if (!cancelled) setOutcome({ month, tick, status: "ready", view, message: null });
      })
      .catch((error: unknown) => {
        if (cancelled) return;
        if (error instanceof NoConnectionError) {
          setOutcome({ month, tick, status: "noConnection", view: null, message: null });
        } else if (error instanceof ApiError && error.status === 404) {
          setOutcome({ month, tick, status: "notFound", view: null, message: error.message });
        } else {
          setOutcome({
            month,
            tick,
            status: "error",
            view: null,
            message: error instanceof ApiError ? error.message : null,
          });
        }
      });
    return () => {
      cancelled = true;
    };
  }, [month, request]);

  const reload = useCallback((options?: { quiet?: boolean }) => {
    setRequest((r) => ({ tick: r.tick + 1, quiet: options?.quiet ?? false }));
  }, []);

  // Another month, or a loud reload still in flight, shows as loading: old data is never current.
  const stale = !outcome || outcome.month !== month || (!request.quiet && outcome.tick !== request.tick);
  if (stale || !outcome) return { status: "loading", view: null, message: null, reload };
  return { status: outcome.status, view: outcome.view, message: outcome.message, reload };
}
