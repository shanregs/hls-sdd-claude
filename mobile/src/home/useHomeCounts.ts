import { useCallback, useEffect, useState } from "react";
import { listVisits } from "../api/marketingApi";
import { getTaskSummary } from "../api/tasksApi";
import { businessToday } from "../attendance/serverClock";

export interface Counted<T> {
  status: "loading" | "ready" | "error";
  value: T | null;
  reload: () => void;
}

/** Loads one number or pair for a Home card. A failed load shows an error, never an old figure; `enabled` false loads nothing. */
function useCounted<T>(load: () => Promise<T>, enabled: boolean): Counted<T> {
  const [tick, setTick] = useState(0);
  const [outcome, setOutcome] = useState<{ tick: number; value: T | null } | null>(null);

  useEffect(() => {
    if (!enabled) return;
    let cancelled = false;
    load()
      .then((value) => {
        if (!cancelled) setOutcome({ tick, value });
      })
      .catch(() => {
        if (!cancelled) setOutcome({ tick, value: null });
      });
    return () => {
      cancelled = true;
    };
    // `load` is built from the call itself, so only the tick (a reload) and `enabled` start a new request
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tick, enabled]);

  const reload = useCallback(() => setTick((t) => t + 1), []);
  if (!enabled || !outcome) return { status: "loading", value: null, reload };
  return { status: outcome.value === null ? "error" : "ready", value: outcome.value, reload };
}

/** How many of the Manager's visits today the server still calls planned (spec 034 FR-013). */
export function useVisitsToday(enabled: boolean): Counted<number> {
  return useCounted(async () => {
    const today = businessToday();
    const visits = await listVisits(today, today);
    return visits.filter((v) => v.effectiveStatus === "PLANNED").length;
  }, enabled);
}

/** The server's counts of overdue tasks and tasks due today. */
export function useTaskCounts(enabled: boolean): Counted<{ overdue: number; today: number }> {
  return useCounted(async () => {
    const summary = await getTaskSummary();
    return { overdue: summary.overdue, today: summary.today };
  }, enabled);
}
