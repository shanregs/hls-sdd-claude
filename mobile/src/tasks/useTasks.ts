import { useCallback, useEffect, useState } from "react";
import { ApiError, NoConnectionError } from "../api/errors";
import { getTaskSummary, listTasks, type TaskRow, type TaskSummary, type TaskView } from "../api/tasksApi";

export const TASK_PAGE_SIZE = 25;

export type TasksStatus = "loading" | "ready" | "error" | "noConnection";

export interface TasksState {
  status: TasksStatus;
  rows: TaskRow[];
  total: number;
  message: string | null;
  /** The server's reason when a later page could not be loaded; the earlier pages stay. */
  moreProblem: string | null;
  loadingMore: boolean;
  loadMore: () => void;
  reload: (options?: { quiet?: boolean }) => void;
}

interface Outcome {
  key: string;
  status: Exclude<TasksStatus, "loading">;
  rows: TaskRow[];
  total: number;
  page: number;
  message: string | null;
}

function problemText(error: unknown, fallback: string): string {
  if (error instanceof NoConnectionError) return "No connection.";
  return error instanceof ApiError && error.message.trim() !== "" ? error.message : fallback;
}

/**
 * The Manager's tasks in one of the server's views, 25 at a time (spec 034 FR-008). A result belongs to the view it was loaded
 * for, a failed first page drops the rows, and a failed later page keeps the earlier ones and reports itself.
 */
export function useTasks(view: TaskView): TasksState {
  const [tick, setTick] = useState({ n: 0, quiet: false });
  const [outcome, setOutcome] = useState<Outcome | null>(null);
  const [loadingMore, setLoadingMore] = useState(false);
  const [moreProblem, setMoreProblem] = useState<string | null>(null);
  const key = `${view}|${tick.n}`;

  useEffect(() => {
    let cancelled = false;
    listTasks(view, 0, TASK_PAGE_SIZE)
      .then((page) => {
        if (cancelled) return;
        setMoreProblem(null);
        setOutcome({ key, status: "ready", rows: page.content, total: page.totalElements, page: 0, message: null });
      })
      .catch((error: unknown) => {
        if (cancelled) return;
        setOutcome({
          key,
          status: error instanceof NoConnectionError ? "noConnection" : "error",
          rows: [],
          total: 0,
          page: 0,
          message: problemText(error, "The tasks could not be loaded."),
        });
      });
    return () => {
      cancelled = true;
    };
  }, [view, key]);

  const loadMore = useCallback(() => {
    if (!outcome || outcome.status !== "ready" || loadingMore || outcome.rows.length >= outcome.total) return;
    setLoadingMore(true);
    setMoreProblem(null);
    const next = outcome.page + 1;
    listTasks(view, next, TASK_PAGE_SIZE)
      .then((page) => setOutcome({ ...outcome, rows: [...outcome.rows, ...page.content], total: page.totalElements, page: next }))
      .catch((error: unknown) => setMoreProblem(problemText(error, "More tasks could not be loaded.")))
      .finally(() => setLoadingMore(false));
  }, [outcome, loadingMore, view]);

  const reload = useCallback((options?: { quiet?: boolean }) => setTick((t) => ({ n: t.n + 1, quiet: options?.quiet ?? false })), []);

  // A result for another view, or a loud reload still in flight, shows as loading; a quiet reload keeps the rows meanwhile.
  const stale = !outcome || (outcome.key.split("|")[0] !== view) || (!tick.quiet && outcome.key !== key);
  if (stale || !outcome) {
    return { status: "loading", rows: [], total: 0, message: null, moreProblem: null, loadingMore: false, loadMore, reload };
  }
  return {
    status: outcome.status,
    rows: outcome.rows,
    total: outcome.total,
    message: outcome.message,
    moreProblem,
    loadingMore,
    loadMore,
    reload,
  };
}

export interface SummaryState {
  status: "loading" | "ready" | "error";
  summary: TaskSummary | null;
  reload: () => void;
}

/** The counts of the server's views, or an error; a failed load shows no count rather than an old one. */
export function useTaskSummary(): SummaryState {
  const [tick, setTick] = useState(0);
  const [outcome, setOutcome] = useState<{ tick: number; summary: TaskSummary | null } | null>(null);

  useEffect(() => {
    let cancelled = false;
    getTaskSummary()
      .then((summary) => {
        if (!cancelled) setOutcome({ tick, summary });
      })
      .catch(() => {
        if (!cancelled) setOutcome({ tick, summary: null });
      });
    return () => {
      cancelled = true;
    };
  }, [tick]);

  const reload = useCallback(() => setTick((t) => t + 1), []);
  if (!outcome) return { status: "loading", summary: null, reload };
  return { status: outcome.summary ? "ready" : "error", summary: outcome.summary, reload };
}
