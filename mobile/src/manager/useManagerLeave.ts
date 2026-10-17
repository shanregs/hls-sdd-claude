import { useCallback, useEffect, useState } from "react";
import { ApiError, NoConnectionError } from "../api/errors";
import {
  listManagerLeave,
  listManagerLeaveTypes,
  type ManagerLeave,
  type ManagerLeaveType,
} from "../api/managerLeaveApi";

export type ManagerLeaveStatus = "loading" | "ready" | "error" | "noConnection";

export interface ManagerLeaveState {
  state: ManagerLeaveStatus;
  /** The leave types the server offers; empty until loaded. */
  types: ManagerLeaveType[];
  /** The Manager's requests, newest first, as the server returns them. */
  items: ManagerLeave[];
  /** The server's reason for a failed load. */
  message: string | null;
  /** Loads the requests again. A quiet reload keeps the list on screen until the new one arrives. */
  reload: (options?: { quiet?: boolean }) => void;
}

interface Outcome {
  tick: number;
  state: Exclude<ManagerLeaveStatus, "loading">;
  types: ManagerLeaveType[];
  items: ManagerLeave[];
  message: string | null;
}

/**
 * Loads the leave types and the Manager's requests (spec 033 US5). A failed load drops both, so nothing stale is shown as
 * current; the types are read again with every load because the server decides which are active.
 */
export function useManagerLeave(): ManagerLeaveState {
  const [outcome, setOutcome] = useState<Outcome | null>(null);
  const [request, setRequest] = useState({ tick: 0, quiet: false });

  useEffect(() => {
    let cancelled = false;
    const { tick } = request;
    Promise.all([listManagerLeaveTypes(), listManagerLeave()])
      .then(([types, items]) => {
        if (!cancelled) setOutcome({ tick, state: "ready", types, items, message: null });
      })
      .catch((error: unknown) => {
        if (cancelled) return;
        setOutcome({
          tick,
          state: error instanceof NoConnectionError ? "noConnection" : "error",
          types: [],
          items: [],
          message: error instanceof ApiError && error.message.trim() !== "" ? error.message : null,
        });
      });
    return () => {
      cancelled = true;
    };
  }, [request]);

  const reload = useCallback((options?: { quiet?: boolean }) => {
    setRequest((r) => ({ tick: r.tick + 1, quiet: options?.quiet ?? false }));
  }, []);

  const stale = !outcome || (!request.quiet && outcome.tick !== request.tick);
  if (stale || !outcome) return { state: "loading", types: [], items: [], message: null, reload };
  return { state: outcome.state, types: outcome.types, items: outcome.items, message: outcome.message, reload };
}
