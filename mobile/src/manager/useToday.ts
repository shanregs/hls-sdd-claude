import { useCallback, useEffect, useState } from "react";
import { ApiError, NoConnectionError } from "../api/errors";
import { getToday, type Today } from "../api/managerAttendanceApi";

export type TodayStatus = "loading" | "ready" | "error" | "noConnection";

export interface TodayState {
  status: TodayStatus;
  /** Today's view; only present while `status` is "ready", and never kept after a failed load. */
  today: Today | null;
  /** The server's reason for a failed load, shown as given. */
  message: string | null;
  /** Loads again. A quiet reload keeps the current view on screen until the new one arrives. */
  reload: (options?: { quiet?: boolean }) => void;
}

interface Outcome {
  tick: number;
  status: Exclude<TodayStatus, "loading">;
  today: Today | null;
  message: string | null;
}

/**
 * Loads the Manager's day: status, planned activities and the days still missing a check-out. Call `reload` once the
 * server has confirmed an action that changes the day. A failed load drops the data so nothing stale is shown as
 * current (spec 033 FR-008).
 */
export function useToday(enabled = true): TodayState {
  const [outcome, setOutcome] = useState<Outcome | null>(null);
  const [request, setRequest] = useState({ tick: 0, quiet: false });
  useEffect(() => {
    if (!enabled) return;
    let cancelled = false;
    const { tick } = request;
    getToday()
      .then((today) => {
        if (!cancelled) setOutcome({ tick, status: "ready", today, message: null });
      })
      .catch((error: unknown) => {
        if (cancelled) return;
        if (error instanceof NoConnectionError) {
          setOutcome({ tick, status: "noConnection", today: null, message: null });
        } else {
          setOutcome({
            tick,
            status: "error",
            today: null,
            message: error instanceof ApiError && error.message.trim() !== "" ? error.message : null,
          });
        }
      });
    return () => {
      cancelled = true;
    };
  }, [enabled, request]);

  const reload = useCallback((options?: { quiet?: boolean }) => {
    setRequest((r) => ({ tick: r.tick + 1, quiet: options?.quiet ?? false }));
  }, []);

  const stale = !outcome || (!request.quiet && outcome.tick !== request.tick);
  if (!enabled || stale || !outcome) return { status: "loading", today: null, message: null, reload };
  return { status: outcome.status, today: outcome.today, message: outcome.message, reload };
}
