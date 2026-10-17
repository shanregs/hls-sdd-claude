import { useCallback, useEffect, useRef, useState } from "react";
import { managerRefusalText } from "./managerMessages";

export interface ActionState<A extends unknown[], R> {
  /**
   * Runs the action. While one run is outstanding a second call is ignored and returns the first run's promise, so a
   * double tap sends one request. Resolves with the result, or undefined when the action failed (the reason is in
   * `error`). Nothing is reported as done before the promise resolves with a result.
   */
  run: (...args: A) => Promise<R | undefined>;
  sending: boolean;
  /** The server's reason, or the no-connection text, of the last failed run; cleared by the next run. */
  error: string | null;
  clearError: () => void;
}

/** One button's request: in flight or not, and what went wrong (spec 033 research §3). */
export function useAction<A extends unknown[], R>(action: (...args: A) => Promise<R>): ActionState<A, R> {
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const inFlight = useRef<Promise<R | undefined> | null>(null);
  const actionRef = useRef(action);
  const mounted = useRef(true);
  useEffect(() => {
    actionRef.current = action;
  });
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  const run = useCallback((...args: A): Promise<R | undefined> => {
    if (inFlight.current) return inFlight.current;
    setSending(true);
    setError(null);
    const started = (async () => {
      try {
        return await actionRef.current(...args);
      } catch (failure) {
        if (mounted.current) setError(managerRefusalText(failure));
        return undefined;
      } finally {
        inFlight.current = null;
        if (mounted.current) setSending(false);
      }
    })();
    inFlight.current = started;
    return started;
  }, []);

  const clearError = useCallback(() => setError(null), []);
  return { run, sending, error, clearError };
}
