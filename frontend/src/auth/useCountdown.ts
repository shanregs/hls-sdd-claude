import { useEffect, useRef, useState } from "react";

/** A simple second-by-second countdown, used to reflect the server's OTP resend cooldown /
 * consecutive-request lockout in the UI (FR-028/FR-029) without hardcoding the duration twice. */
export function useCountdown() {
  const [secondsRemaining, setSecondsRemaining] = useState(0);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
      }
    };
  }, []);

  function start(seconds: number) {
    if (intervalRef.current) {
      clearInterval(intervalRef.current);
    }
    setSecondsRemaining(seconds);
    intervalRef.current = setInterval(() => {
      setSecondsRemaining((remaining) => {
        if (remaining <= 1) {
          if (intervalRef.current) {
            clearInterval(intervalRef.current);
          }
          return 0;
        }
        return remaining - 1;
      });
    }, 1000);
  }

  return { secondsRemaining, start };
}
