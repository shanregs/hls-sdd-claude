import { useCallback, useEffect, useState } from "react";
import { listLeave } from "../api/leaveApi";

export type PendingCountState =
  | { state: "off" }
  | { state: "loading" }
  | { state: "ready"; count: number }
  | { state: "error" };

interface Outcome {
  tick: number;
  result: { state: "ready"; count: number } | { state: "error" };
}

/**
 * The number of Pending leave requests in the user's scope, for the Home widget. It asks the server only when
 * `enabled` (the user's menu offers Leave Management); the same call the web widget makes, reading
 * `pendingCount` from a page of one. A failure shows an error state in the widget alone.
 */
export function usePendingCount(enabled: boolean): PendingCountState & { reload: () => void } {
  const [tick, setTick] = useState(0);
  const [outcome, setOutcome] = useState<Outcome | null>(null);

  useEffect(() => {
    if (!enabled) return undefined;
    let cancelled = false;
    listLeave({ status: "PENDING", size: 1 })
      .then((page) => {
        if (!cancelled) setOutcome({ tick, result: { state: "ready", count: page.pendingCount } });
      })
      .catch(() => {
        if (!cancelled) setOutcome({ tick, result: { state: "error" } });
      });
    return () => {
      cancelled = true;
    };
  }, [enabled, tick]);

  const reload = useCallback(() => setTick((n) => n + 1), []);
  if (!enabled) return { state: "off", reload };
  if (!outcome || outcome.tick !== tick) return { state: "loading", reload };
  return { ...outcome.result, reload };
}
