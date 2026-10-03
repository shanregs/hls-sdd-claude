/** Mirrors backend `AccessModelDtos` (contracts/access-model-api.md). */
export interface NavItemView {
  label: string;
  route: string;
  actions: string[];
}

export interface NavSection {
  section: string;
  items: NavItemView[];
}

export interface AccessModel {
  roles: string[];
  navigation: NavSection[];
  dataScope: Record<string, string>;
}
