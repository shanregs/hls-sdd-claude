import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError, NoConnectionError } from "../api/errors";
import { listMyExpenses, type ClaimRow, type ClaimStatus } from "../api/expensesApi";
import { readListCache, updateListCache } from "../outbox/listCache";

export const EXPENSE_PAGE_SIZE = 50;

export type ExpensesStatus = "loading" | "ready" | "error" | "noConnection";

export interface MyExpensesState {
  status: ExpensesStatus;
  rows: ClaimRow[];
  total: number;
  /** The server's owed total ("1850.00"); from the cache when the list is out of date. */
  owed: string | null;
  message: string | null;
  /** Set when the list shown is the saved copy: the instant it was loaded. */
  outOfDate: string | null;
  moreProblem: string | null;
  loadingMore: boolean;
  loadMore: () => void;
  reload: (options?: { quiet?: boolean }) => void;
}

interface Outcome {
  key: string;
  status: Exclude<ExpensesStatus, "loading">;
  rows: ClaimRow[];
  total: number;
  page: number;
  owed: string | null;
  message: string | null;
  outOfDate: string | null;
}

function problemText(error: unknown, fallback: string): string {
  if (error instanceof NoConnectionError) return "No connection.";
  return error instanceof ApiError && error.message.trim() !== "" ? error.message : fallback;
}

/**
 * The claimant's own claims (spec 036 FR-013 to FR-015): a status filter, an optional month, 50 at a time, and the server's
 * owed total. A successful load of the unfiltered first page is saved (encrypted, per user); when a load fails the saved
 * copy is shown, marked out of date, and filtered here by status and month.
 */
export function useMyExpenses(userId: string, status: ClaimStatus | "ALL", month: string | null): MyExpensesState {
  const [tick, setTick] = useState({ n: 0, quiet: false });
  const [outcome, setOutcome] = useState<Outcome | null>(null);
  const [loadingMore, setLoadingMore] = useState(false);
  const [moreProblem, setMoreProblem] = useState<string | null>(null);
  const key = `${status}|${month ?? ""}|${tick.n}`;
  const mounted = useRef(true);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  useEffect(() => {
    let cancelled = false;
    const filter = { status: status === "ALL" ? undefined : status, month: month ?? undefined };
    listMyExpenses({ ...filter, page: 0, size: EXPENSE_PAGE_SIZE })
      .then((page) => {
        if (cancelled) return;
        setMoreProblem(null);
        setOutcome({ key, status: "ready", rows: page.content, total: page.totalElements, page: 0, owed: page.owed, message: null, outOfDate: null });
        if (!filter.status && !filter.month) {
          void updateListCache(userId, { loadedAt: new Date().toISOString(), owed: page.owed, rows: page.content });
        }
      })
      .catch(async (error: unknown) => {
        const cache = await readListCache(userId);
        if (cancelled) return;
        if (cache?.loadedAt) {
          const rows = cache.rows.filter((r) => (!filter.status || r.status === filter.status) && (!filter.month || r.expenseDate.startsWith(filter.month)));
          setOutcome({ key, status: "ready", rows, total: rows.length, page: 0, owed: cache.owed, message: null, outOfDate: cache.loadedAt });
          return;
        }
        setOutcome({
          key,
          status: error instanceof NoConnectionError ? "noConnection" : "error",
          rows: [],
          total: 0,
          page: 0,
          owed: null,
          message: problemText(error, "Your expenses could not be loaded."),
          outOfDate: null,
        });
      });
    return () => {
      cancelled = true;
    };
  }, [status, month, key, userId]);

  const loadMore = useCallback(() => {
    if (!outcome || outcome.status !== "ready" || outcome.outOfDate || loadingMore || outcome.rows.length >= outcome.total) return;
    setLoadingMore(true);
    setMoreProblem(null);
    const next = outcome.page + 1;
    listMyExpenses({ status: status === "ALL" ? undefined : status, month: month ?? undefined, page: next, size: EXPENSE_PAGE_SIZE })
      .then((page) => setOutcome({ ...outcome, rows: [...outcome.rows, ...page.content], total: page.totalElements, page: next, owed: page.owed }))
      .catch((error: unknown) => setMoreProblem(problemText(error, "More expenses could not be loaded.")))
      .finally(() => {
        if (mounted.current) setLoadingMore(false);
      });
  }, [outcome, loadingMore, status, month]);

  const reload = useCallback((options?: { quiet?: boolean }) => setTick((t) => ({ n: t.n + 1, quiet: options?.quiet ?? false })), []);

  // A result for another filter, or a loud reload still in flight, shows as loading; a quiet reload keeps the rows meanwhile.
  const filterKey = `${status}|${month ?? ""}|`;
  const stale = !outcome || !outcome.key.startsWith(filterKey) || (!tick.quiet && outcome.key !== key);
  if (stale || !outcome) {
    return { status: "loading", rows: [], total: 0, owed: null, message: null, outOfDate: null, moreProblem: null, loadingMore: false, loadMore, reload };
  }
  return {
    status: outcome.status,
    rows: outcome.rows,
    total: outcome.total,
    owed: outcome.owed,
    message: outcome.message,
    outOfDate: outcome.outOfDate,
    moreProblem,
    loadingMore,
    loadMore,
    reload,
  };
}
