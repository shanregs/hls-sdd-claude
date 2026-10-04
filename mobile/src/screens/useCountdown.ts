import { useCallback, useEffect, useState } from "react";

/** Counts down whole seconds once started; `remaining` is 0 when idle. */
export function useCountdown() {
  const [remaining, setRemaining] = useState(0);

  useEffect(() => {
    if (remaining <= 0) return undefined;
    const timer = setTimeout(() => setRemaining((r) => r - 1), 1000);
    return () => clearTimeout(timer);
  }, [remaining]);

  const start = useCallback((seconds: number) => setRemaining(Math.max(0, Math.ceil(seconds))), []);
  return { remaining, start };
}
