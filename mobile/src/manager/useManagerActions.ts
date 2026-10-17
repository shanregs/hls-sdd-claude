import { useMenuActions } from "../access/useMenuActions";

export interface ManagerActions {
  canCreate: boolean;
  canDelete: boolean;
}

/**
 * What the server's menu grants on one of the Manager's own attendance screens (spec 033 FR-014). It reads the same
 * menu actions as every other screen; nothing here looks at a role.
 */
export function useManagerActions(route: string): ManagerActions {
  const { canCreate, canDelete } = useMenuActions(route);
  return { canCreate, canDelete };
}
