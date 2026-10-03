import type { ReactNode } from "react";
import { useLocation } from "react-router-dom";
import {
  authorizedRoutesOf,
  useAccessModel,
} from "../access-model/useAccessModel";
import { NotAuthorizedPage } from "./NotAuthorizedPage";

/**
 * Guards a route against the current access model (FR-010): the guarded screen's component is
 * only ever mounted (and so only ever fetches its own data) when the current path is authorized —
 * an unauthorized visit renders {@link NotAuthorizedPage} instead, and `children` is never
 * rendered, so no data-fetching effect inside it ever runs.
 */
export function RouteGuard({ children }: { children: ReactNode }) {
  const location = useLocation();
  const { accessModel, loading } = useAccessModel();

  if (loading) {
    return null;
  }

  const authorizedRoutes = authorizedRoutesOf(accessModel);
  if (!authorizedRoutes.has(location.pathname)) {
    return <NotAuthorizedPage />;
  }

  return <>{children}</>;
}
