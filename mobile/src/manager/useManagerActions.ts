import { useAccessModel } from "../access/AccessModelProvider";

export interface ManagerActions {
  canCreate: boolean;
  canDelete: boolean;
}

/**
 * What the server's menu grants on one of the Manager's own attendance screens (spec 033 FR-014). The actions of the
 * menu item with this route decide which buttons are shown; nothing here looks at a role. The server refuses an
 * action regardless of what a button shows.
 */
export function useManagerActions(route: string): ManagerActions {
  const { model } = useAccessModel();
  const actions = new Set<string>();
  for (const section of model?.navigation ?? []) {
    for (const item of section.items) {
      if (item.route === route) item.actions.forEach((a) => actions.add(a));
    }
  }
  return { canCreate: actions.has("CREATE"), canDelete: actions.has("DELETE") };
}
