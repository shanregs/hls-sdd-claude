import { createContext, useContext, useEffect, useRef } from "react";

/**
 * Lets a screen with its own sub-screen (for example a Teacher's month opened from the list) take
 * the Android back button first: the shell asks the registered handler before it goes home.
 */
export type InnerBackHandler = () => boolean;

export interface InnerBackRegistry {
  set: (handler: InnerBackHandler) => void;
  clear: (handler: InnerBackHandler) => void;
}

export const InnerBackContext = createContext<InnerBackRegistry | null>(null);

/** While `active`, back closes the sub-screen through `onBack` instead of leaving the screen. */
export function useInnerBack(active: boolean, onBack: () => void): void {
  const registry = useContext(InnerBackContext);
  const latest = useRef(onBack);
  useEffect(() => {
    latest.current = onBack;
  });
  useEffect(() => {
    if (!registry || !active) return undefined;
    const handler: InnerBackHandler = () => {
      latest.current();
      return true;
    };
    registry.set(handler);
    return () => registry.clear(handler);
  }, [registry, active]);
}
