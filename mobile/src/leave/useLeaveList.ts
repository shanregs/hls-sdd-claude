import { useCallback, useEffect, useRef, useState } from "react";
import type { LeaveRequest, LeaveStatus } from "../api/leaveApi";
import { NoConnectionError } from "../api/errors";

export type LeaveListState = "loading" | "ready" | "error" | "noConnection";

export interface LeavePage {
  content: LeaveRequest[];
  totalElements: number;
  pendingCount?: number;
}

export interface LeaveListResult {
  state: LeaveListState;
  items: LeaveRequest[];
  total: number;
  /** The count of Pending requests in scope, when the server sent it (Leave Management). */
  pendingCount: number | null;
  loadingMore: boolean;
  loadMore: () => void;
  /** Loads page 0 again. `quiet` keeps the rows on screen until the new ones arrive (after an action). */
  reload: (options?: { quiet?: boolean }) => void;
}

interface Outcome {
  status: LeaveStatus | undefined;
  tick: number;
  state: Exclude<LeaveListState, "loading">;
  items: LeaveRequest[];
  total: number;
  pendingCount: number | null;
  page: number;
}

/**
 * A paged list of leave requests for one status (undefined means the server's default). Rows belong to the
 * status they were loaded for: another status shows loading, never the old rows, and a failed load drops them
 * (spec 020 FR-019). Pages of 25 are appended; a failed extra page keeps the earlier ones.
 */
export function useLeaveList(
  loader: (status: LeaveStatus | undefined, page: number) => Promise<LeavePage>,
  status: LeaveStatus | undefined,
): LeaveListResult {
  const [outcome, setOutcome] = useState<Outcome | null>(null);
  const [request, setRequest] = useState({ tick: 0, quiet: false });
  const [loadingMore, setLoadingMore] = useState(false);
  const loaderRef = useRef(loader);
  useEffect(() => {
    loaderRef.current = loader;
  });

  useEffect(() => {
    let cancelled = false;
    const { tick } = request;
    loaderRef
      .current(status, 0)
      .then((loaded) => {
        if (cancelled) return;
        setOutcome({
          status,
          tick,
          state: "ready",
          items: loaded.content,
          total: loaded.totalElements,
          pendingCount: loaded.pendingCount ?? null,
          page: 0,
        });
      })
      .catch((error: unknown) => {
        if (cancelled) return;
        setOutcome({
          status,
          tick,
          state: error instanceof NoConnectionError ? "noConnection" : "error",
          items: [],
          total: 0,
          pendingCount: null,
          page: 0,
        });
      });
    return () => {
      cancelled = true;
    };
  }, [status, request]);

  const reload = useCallback((options?: { quiet?: boolean }) => {
    setRequest((r) => ({ tick: r.tick + 1, quiet: options?.quiet ?? false }));
  }, []);

  const current = outcome && outcome.status === status && (request.quiet || outcome.tick === request.tick) ? outcome : null;

  const loadMore = useCallback(() => {
    if (!current || current.state !== "ready" || loadingMore) return;
    setLoadingMore(true);
    const next = current.page + 1;
    loaderRef
      .current(status, next)
      .then((loaded) => {
        setOutcome((o) =>
          o && o.status === status
            ? {
                ...o,
                items: [...o.items, ...loaded.content],
                total: loaded.totalElements,
                pendingCount: loaded.pendingCount ?? o.pendingCount,
                page: next,
              }
            : o,
        );
      })
      .catch((error: unknown) => {
        setOutcome((o) =>
          o && o.status === status
            ? { ...o, state: error instanceof NoConnectionError ? "noConnection" : "error" }
            : o,
        );
      })
      .finally(() => setLoadingMore(false));
  }, [current, loadingMore, status]);

  if (!current) {
    return { state: "loading", items: [], total: 0, pendingCount: null, loadingMore: false, loadMore, reload };
  }
  return {
    state: current.state,
    items: current.items,
    total: current.total,
    pendingCount: current.pendingCount,
    loadingMore,
    loadMore,
    reload,
  };
}
