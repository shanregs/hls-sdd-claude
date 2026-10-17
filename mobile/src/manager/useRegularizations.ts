import { useCallback, useEffect, useState } from "react";
import { ApiError, NoConnectionError } from "../api/errors";
import { listRegularizations, type Regularization } from "../api/managerAttendanceApi";

export interface RegularizationsState {
  state: "loading" | "ready" | "error" | "noConnection";
  items: Regularization[];
  message: string | null;
  reload: () => void;
}

interface Outcome {
  tick: number;
  state: "ready" | "error" | "noConnection";
  items: Regularization[];
  message: string | null;
}

/** The Manager's forgotten check-in requests (spec 033 US6). A failed load drops the list rather than keep a stale one. */
export function useRegularizations(): RegularizationsState {
  const [outcome, setOutcome] = useState<Outcome | null>(null);
  const [tick, setTick] = useState(0);

  useEffect(() => {
    let cancelled = false;
    listRegularizations()
      .then((items) => {
        if (!cancelled) setOutcome({ tick, state: "ready", items, message: null });
      })
      .catch((error: unknown) => {
        if (cancelled) return;
        setOutcome({
          tick,
          state: error instanceof NoConnectionError ? "noConnection" : "error",
          items: [],
          message: error instanceof ApiError && error.message.trim() !== "" ? error.message : null,
        });
      });
    return () => {
      cancelled = true;
    };
  }, [tick]);

  const reload = useCallback(() => setTick((t) => t + 1), []);
  // The list stays on screen while it reloads; only the first load shows as loading.
  if (!outcome) return { state: "loading", items: [], message: null, reload };
  return { state: outcome.state, items: outcome.items, message: outcome.message, reload };
}
