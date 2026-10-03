import { useMemo } from "react";
import { useAccessModel } from "../../access-model/useAccessModel";

/** The actions the access model grants for a route (Constitution Principle IV: hide what the
 * caller may not do). Empty until the access model has loaded. */
export function useGrantedActions(route: string): Set<string> {
  const { accessModel } = useAccessModel();
  return useMemo(() => {
    const item = accessModel?.navigation
      .flatMap((section) => section.items)
      .find((i) => i.route === route);
    return new Set(item?.actions ?? []);
  }, [accessModel, route]);
}
