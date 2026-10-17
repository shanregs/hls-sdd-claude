import { useAccessModel } from "./AccessModelProvider";

export interface MenuActions {
  canCreate: boolean;
  canEdit: boolean;
  canDelete: boolean;
}

/**
 * What the server's menu grants on the screen at one route. The actions of the menu item with this route decide which
 * buttons are shown; nothing here looks at a role. The server refuses an action regardless of what a button shows.
 */
export function useMenuActions(route: string): MenuActions {
  const { model } = useAccessModel();
  const actions = new Set<string>();
  for (const section of model?.navigation ?? []) {
    for (const item of section.items) {
      if (item.route === route) item.actions.forEach((a) => actions.add(a));
    }
  }
  return { canCreate: actions.has("CREATE"), canEdit: actions.has("EDIT"), canDelete: actions.has("DELETE") };
}
