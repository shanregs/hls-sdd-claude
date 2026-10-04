import { api } from "./httpClient";

/** One menu item of the server-provided navigation (spec 002 contracts/access-model-api.md). */
export interface NavItem {
  label: string;
  route: string;
  actions: string[];
}

export interface NavSection {
  section: string;
  items: NavItem[];
}

/**
 * The caller's resolved access model: the union of the menus, actions and data scope of all their
 * roles. The app renders `navigation` as given and never recomputes grants from `roles`.
 */
export interface AccessModel {
  roles: string[];
  navigation: NavSection[];
  dataScope: Record<string, string>;
}

export const fetchAccessModel = () => api.get<AccessModel>("/api/v1/me/access-model");
