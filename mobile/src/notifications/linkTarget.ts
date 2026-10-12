/** Where a notification link leads inside the app: a route the shell knows, and for My Attendance the month to open. */
export interface LinkTarget {
  route: string;
  month?: string;
}

const MONTH = /^\d{4}-(0[1-9]|1[0-2])$/;

/**
 * Maps the link a notification carries (a web route, spec 010) to a screen of the app (spec 021 FR-006, research §7):
 * `/leave/history`, `/operations/leave`, and `/my-attendance` with an optional `month=YYYY-MM`. Anything else is not
 * followable and gives null. The mapping grants nothing: the caller still checks that the user's menu offers the route.
 */
export function linkTarget(link: string | null | undefined): LinkTarget | null {
  if (typeof link !== "string" || !link.startsWith("/") || link.startsWith("//")) return null;
  const at = link.indexOf("?");
  const path = at < 0 ? link : link.slice(0, at);
  const query = at < 0 ? "" : link.slice(at + 1);

  if (path === "/leave/history" || path === "/operations/leave") {
    return query === "" ? { route: path } : null;
  }
  if (path === "/my-attendance") {
    if (query === "") return { route: path };
    const params = query.split("&");
    if (params.length !== 1) return null;
    const [name, ...rest] = params[0].split("=");
    if (name !== "month") return null;
    const month = rest.join("=");
    return MONTH.test(month) ? { route: path, month } : { route: path };
  }
  return null;
}
