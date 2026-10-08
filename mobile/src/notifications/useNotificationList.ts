import { useCallback, useEffect, useState } from "react";
import { NoConnectionError } from "../api/errors";
import { listNotifications, type NotificationRow } from "../api/notificationsApi";
import { useNotifications } from "./NotificationsProvider";

export type NotificationListState = "loading" | "ready" | "error" | "noConnection";

export interface NotificationListResult {
  state: NotificationListState;
  items: NotificationRow[];
  /** The caller's whole unread count from the last page the server sent. */
  unread: number | null;
  hasMore: boolean;
  loadingMore: boolean;
  loadMore: () => void;
  /** Loads page 0 again. `quiet` keeps the rows on screen until the new ones arrive (after an action). */
  reload: (options?: { quiet?: boolean }) => void;
  /** Takes a row out after the server confirmed its deletion (or said it no longer exists). */
  remove: (id: string) => void;
  /** Shows a row as read after the server confirmed it. */
  markLocalRead: (id: string) => void;
}

interface Outcome {
  unreadOnly: boolean;
  tick: number;
  state: Exclude<NotificationListState, "loading">;
  items: NotificationRow[];
  total: number;
  unread: number | null;
  page: number;
}

/**
 * A paged list of the signed-in user's own notifications, for All or Unread only. Rows belong to the filter they
 * were loaded for: another filter shows loading, never the old rows, and a failed load drops them (spec 021 FR-014).
 * Every page response also passes its `unread` to the bell, so list and bell never disagree after a load.
 */
export function useNotificationList(unreadOnly: boolean): NotificationListResult {
  const { setCount } = useNotifications();
  const [outcome, setOutcome] = useState<Outcome | null>(null);
  const [request, setRequest] = useState({ tick: 0, quiet: false });
  const [loadingMore, setLoadingMore] = useState(false);

  useEffect(() => {
    let cancelled = false;
    const { tick } = request;
    listNotifications({ unreadOnly, page: 0 })
      .then((loaded) => {
        if (cancelled) return;
        setCount(loaded.unread);
        setOutcome({
          unreadOnly,
          tick,
          state: "ready",
          items: loaded.content,
          total: loaded.totalElements,
          unread: loaded.unread,
          page: 0,
        });
      })
      .catch((error: unknown) => {
        if (cancelled) return;
        setOutcome({
          unreadOnly,
          tick,
          state: error instanceof NoConnectionError ? "noConnection" : "error",
          items: [],
          total: 0,
          unread: null,
          page: 0,
        });
      });
    return () => {
      cancelled = true;
    };
  }, [unreadOnly, request, setCount]);

  const reload = useCallback((options?: { quiet?: boolean }) => {
    setRequest((r) => ({ tick: r.tick + 1, quiet: options?.quiet ?? false }));
  }, []);

  const current =
    outcome && outcome.unreadOnly === unreadOnly && (request.quiet || outcome.tick === request.tick) ? outcome : null;

  const loadMore = useCallback(() => {
    if (!current || current.state !== "ready" || loadingMore) return;
    setLoadingMore(true);
    const next = current.page + 1;
    listNotifications({ unreadOnly, page: next })
      .then((loaded) => {
        setCount(loaded.unread);
        setOutcome((o) =>
          o && o.unreadOnly === unreadOnly
            ? { ...o, items: [...o.items, ...loaded.content], total: loaded.totalElements, unread: loaded.unread, page: next }
            : o,
        );
      })
      .catch((error: unknown) => {
        setOutcome((o) =>
          o && o.unreadOnly === unreadOnly ? { ...o, state: error instanceof NoConnectionError ? "noConnection" : "error" } : o,
        );
      })
      .finally(() => setLoadingMore(false));
  }, [current, loadingMore, unreadOnly, setCount]);

  const remove = useCallback((id: string) => {
    setOutcome((o) => (o ? { ...o, items: o.items.filter((n) => n.id !== id), total: Math.max(0, o.total - 1) } : o));
  }, []);

  const markLocalRead = useCallback((id: string) => {
    setOutcome((o) => (o ? { ...o, items: o.items.map((n) => (n.id === id ? { ...n, read: true } : n)) } : o));
  }, []);

  if (!current) {
    return { state: "loading", items: [], unread: null, hasMore: false, loadingMore: false, loadMore, reload, remove, markLocalRead };
  }
  return {
    state: current.state,
    items: current.items,
    unread: current.unread,
    hasMore: current.items.length < current.total,
    loadingMore,
    loadMore,
    reload,
    remove,
    markLocalRead,
  };
}
