import { useCallback, useEffect, useState } from "react";
import { Link as RouterLink } from "react-router-dom";
import { Badge, IconButton, Tooltip } from "@mui/material";
import NotificationsOutlined from "@mui/icons-material/NotificationsOutlined";
import { useAuth } from "../../auth/useAuth";
import { useGrantedActions } from "../common/useGrantedActions";
import { NOTIFICATIONS_CHANGED, getUnreadCount } from "./notificationsApi";

export const NOTIFICATIONS_ROUTE = "/account/notifications";
export const POLL_MS = 30_000;

/**
 * Header bell with the unread count (spec 010): polls every 30 s while the tab is visible and on focus,
 * links to the Notifications page, and renders only when the access model grants that page.
 */
export function NotificationBell() {
  const { authFetch } = useAuth();
  const allowed = useGrantedActions(NOTIFICATIONS_ROUTE).has("VIEW");
  const [unread, setUnread] = useState(0);

  const refresh = useCallback(async () => {
    if (document.visibilityState !== "visible") return;
    const result = await getUnreadCount(authFetch);
    if (result.ok) setUnread(result.data);
  }, [authFetch]);

  useEffect(() => {
    if (!allowed) return;
    // eslint-disable-next-line react-hooks/set-state-in-effect -- initial fetch of the count
    void refresh();
    const timer = setInterval(() => void refresh(), POLL_MS);
    window.addEventListener("focus", refresh);
    window.addEventListener(NOTIFICATIONS_CHANGED, refresh);
    document.addEventListener("visibilitychange", refresh);
    return () => {
      clearInterval(timer);
      window.removeEventListener("focus", refresh);
      window.removeEventListener(NOTIFICATIONS_CHANGED, refresh);
      document.removeEventListener("visibilitychange", refresh);
    };
  }, [allowed, refresh]);

  if (!allowed) return null;

  const label =
    unread === 0
      ? "Notifications, none unread"
      : `Notifications, ${unread} unread`;

  return (
    <Tooltip title="Notifications">
      <IconButton
        component={RouterLink}
        to={NOTIFICATIONS_ROUTE}
        aria-label={label}
        aria-live="polite"
      >
        <Badge
          badgeContent={unread}
          max={99}
          color="error"
          invisible={unread === 0}
        >
          <NotificationsOutlined />
        </Badge>
      </IconButton>
    </Tooltip>
  );
}
