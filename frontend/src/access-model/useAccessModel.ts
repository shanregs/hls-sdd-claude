import { useEffect, useState } from "react";
import { useAuth } from "../auth/useAuth";
import type { AccessModel } from "./types";

interface UseAccessModelResult {
  accessModel: AccessModel | null;
  /** True until the fetch for the current access token has settled. */
  loading: boolean;
}

interface FetchedFor {
  token: string;
  model: AccessModel | null;
}

/**
 * Fetches the current user's access model on sign-in and on every session renewal (research.md
 * §7, FR-015): `authFetch`'s silent renewal updates `accessToken` in `useAuth`, and this hook
 * re-fetches whenever that token changes, so a matrix/role change lands within one renewal cycle
 * with no separate cache-invalidation call needed (contracts/access-model-api.md).
 *
 * `accessModel`/`loading` are derived from the last fetch's token rather than reset via a
 * separate effect-triggered `setState` call, so logging out (accessToken becoming null) clears
 * them immediately without an extra render.
 */
export function useAccessModel(): UseAccessModelResult {
  const { accessToken, authFetch } = useAuth();
  const [fetched, setFetched] = useState<FetchedFor | null>(null);

  useEffect(() => {
    if (!accessToken) {
      return;
    }

    let cancelled = false;
    (async () => {
      try {
        const response = await authFetch("/api/v1/me/access-model");
        if (!response.ok) {
          throw new Error("Could not load the access model.");
        }
        const data = (await response.json()) as AccessModel;
        if (!cancelled) {
          setFetched({ token: accessToken, model: data });
        }
      } catch {
        if (!cancelled) {
          setFetched({ token: accessToken, model: null });
        }
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [accessToken, authFetch]);

  const accessModel =
    accessToken && fetched?.token === accessToken ? fetched.model : null;
  const loading = Boolean(accessToken) && fetched?.token !== accessToken;

  return { accessModel, loading };
}

/** The full set of routes the given access model authorizes (FR-010's `RouteGuard` checks against this). */
export function authorizedRoutesOf(
  accessModel: AccessModel | null,
): Set<string> {
  if (!accessModel) {
    return new Set();
  }
  return new Set(
    accessModel.navigation.flatMap((section) =>
      section.items.map((item) => item.route),
    ),
  );
}

/**
 * A path is authorized when it is a route of the access model or lies below one (a detail page such as
 * `/operations/school-contracts/schools/:id` or `/recruitment/drives/:id` belongs to its list's menu item, so it
 * needs the same grant and never has a menu item of its own).
 */
export function isAuthorizedPath(path: string, routes: Set<string>): boolean {
  if (routes.has(path)) {
    return true;
  }
  for (const route of routes) {
    if (route !== "/" && path.startsWith(`${route}/`)) {
      return true;
    }
  }
  return false;
}
