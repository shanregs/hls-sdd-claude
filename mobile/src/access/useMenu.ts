import type { AccessModel, NavItem, NavSection } from "../api/accessModelApi";
import { screenFor } from "./screenRegistry";

/**
 * The menu to show: the server's navigation filtered to routes the app has a screen for, with
 * duplicates removed (a user holding several roles can receive the same route twice) and sections
 * left empty by the filter dropped (spec FR-010, FR-011).
 */
export function buildMenu(model: AccessModel | null): NavSection[] {
  if (!model) return [];
  const seen = new Set<string>();
  const sections: NavSection[] = [];
  for (const section of model.navigation) {
    const items: NavItem[] = [];
    for (const item of section.items) {
      if (!screenFor(item.route) || seen.has(item.route)) continue;
      seen.add(item.route);
      items.push(item);
    }
    if (items.length > 0) sections.push({ section: section.section, items });
  }
  return sections;
}

/** True when the route is in the user's server-provided navigation and the app can show it. */
export function canOpenRoute(model: AccessModel | null, route: string): boolean {
  if (!model || !screenFor(route)) return false;
  return model.navigation.some((section) => section.items.some((item) => item.route === route));
}
