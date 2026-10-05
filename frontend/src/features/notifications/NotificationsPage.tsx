import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  FormControlLabel,
  List,
  ListItem,
  ListItemButton,
  ListItemText,
  Stack,
  Switch,
  Typography,
} from "@mui/material";
import FiberManualRecord from "@mui/icons-material/FiberManualRecord";
import { useAuth } from "../../auth/useAuth";
import { ConfirmDialog } from "../common/ConfirmDialog";
import { RowActionButton } from "../common/RowActionButton";
import { useGrantedActions } from "../common/useGrantedActions";
import {
  clearRead,
  deleteNotification,
  listNotifications,
  markAllRead,
  markRead,
  type NotificationRow,
} from "./notificationsApi";

const PAGE_SIZE = 25;

type Pending = { kind: "one"; row: NotificationRow } | { kind: "clear" };

/**
 * Notifications (spec 010): the caller's own notifications, newest first. Opening one marks it read and
 * goes to its link; unread rows carry a marker and "Unread" text, not colour alone. Delete controls show
 * only with the DELETE action.
 */
export function NotificationsPage() {
  const { authFetch } = useAuth();
  const navigate = useNavigate();
  const canDelete = useGrantedActions("/account/notifications").has("DELETE");
  const [rows, setRows] = useState<NotificationRow[] | null>(null);
  const [unread, setUnread] = useState(0);
  const [total, setTotal] = useState(0);
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState<Pending | null>(null);
  const [busy, setBusy] = useState(false);
  const [reloadCount, setReloadCount] = useState(0);
  const [loadedPage, setLoadedPage] = useState(0);

  const reload = () => setReloadCount((n) => n + 1);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const result = await listNotifications(authFetch, {
        unreadOnly,
        page: 0,
        size: PAGE_SIZE,
      });
      if (cancelled) return;
      if (result.ok) {
        setError(null);
        setRows(result.data.content);
        setUnread(result.data.unread);
        setTotal(result.data.totalElements);
        setLoadedPage(0);
      } else {
        setError(result.reason);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch, unreadOnly, reloadCount]);

  const loadMore = useCallback(async () => {
    const next = loadedPage + 1;
    const result = await listNotifications(authFetch, {
      unreadOnly,
      page: next,
      size: PAGE_SIZE,
    });
    if (result.ok) {
      setRows((current) => [...(current ?? []), ...result.data.content]);
      setUnread(result.data.unread);
      setTotal(result.data.totalElements);
      setLoadedPage(next);
    } else {
      setError(result.reason);
    }
  }, [authFetch, unreadOnly, loadedPage]);

  const open = async (row: NotificationRow) => {
    if (!row.read) {
      const result = await markRead(authFetch, row.id);
      if (!result.ok) {
        setError("Could not mark the notification as read.");
        return;
      }
    }
    if (row.link) {
      navigate(row.link);
    } else {
      reload();
    }
  };

  const readAll = async () => {
    const result = await markAllRead(authFetch);
    if (result.ok) reload();
    else setError("Could not mark notifications as read.");
  };

  const confirm = async () => {
    if (!pending) return;
    setBusy(true);
    const result =
      pending.kind === "clear"
        ? await clearRead(authFetch)
        : await deleteNotification(authFetch, pending.row.id);
    setBusy(false);
    setPending(null);
    if (result.ok) reload();
    else setError("Could not delete. Please try again.");
  };

  return (
    <Box sx={{ maxWidth: 820 }}>
      <Stack
        direction="row"
        sx={{
          alignItems: "center",
          justifyContent: "space-between",
          flexWrap: "wrap",
          gap: 1,
          mb: 2,
        }}
      >
        <Typography variant="h5" component="h1">
          Notifications
        </Typography>
        <Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
          <FormControlLabel
            control={
              <Switch
                checked={unreadOnly}
                onChange={(e) => setUnreadOnly(e.target.checked)}
              />
            }
            label="Unread only"
          />
          <Button onClick={readAll} disabled={unread === 0}>
            Mark all as read
          </Button>
          {canDelete && (
            <Button color="error" onClick={() => setPending({ kind: "clear" })}>
              Clear read
            </Button>
          )}
        </Stack>
      </Stack>
      {error && (
        <Alert severity="error" sx={{ mb: 2 }} role="alert">
          {error}
        </Alert>
      )}
      {!rows && !error && <Typography>Loading…</Typography>}
      {rows && rows.length === 0 && (
        <Typography>
          {unreadOnly ? "No unread notifications." : "No notifications."}
        </Typography>
      )}
      {rows && rows.length > 0 && (
        <List aria-label="Notifications" disablePadding>
          {rows.map((row) => (
            <ListItem
              key={row.id}
              divider
              disablePadding
              secondaryAction={
                canDelete && (
                  <RowActionButton
                    action="Delete"
                    subject={row.title}
                    onClick={() => setPending({ kind: "one", row })}
                  />
                )
              }
            >
              <ListItemButton onClick={() => void open(row)} sx={{ pr: 7 }}>
                <FiberManualRecord
                  fontSize="small"
                  color="primary"
                  aria-hidden
                  sx={{ mr: 1.5, visibility: row.read ? "hidden" : "visible" }}
                />
                <ListItemText
                  primary={
                    <>
                      {!row.read && (
                        <Box
                          component="span"
                          sx={{
                            fontWeight: 700,
                            mr: 1,
                            textTransform: "uppercase",
                            fontSize: "0.7rem",
                          }}
                        >
                          Unread
                        </Box>
                      )}
                      {row.title}
                    </>
                  }
                  secondary={`${row.message} · ${new Date(row.createdAt).toLocaleString()}`}
                  slotProps={{
                    primary: { sx: { fontWeight: row.read ? 400 : 700 } },
                  }}
                />
              </ListItemButton>
            </ListItem>
          ))}
        </List>
      )}
      {rows && rows.length < total && (
        <Button onClick={() => void loadMore()} sx={{ mt: 1 }}>
          Load more
        </Button>
      )}
      {pending && (
        <ConfirmDialog
          title={
            pending.kind === "clear"
              ? "Clear read notifications?"
              : "Delete this notification?"
          }
          message={
            pending.kind === "clear"
              ? "Every notification you have already read will be deleted."
              : pending.row.title
          }
          confirmLabel={pending.kind === "clear" ? "Clear read" : "Delete"}
          destructive
          busy={busy}
          onConfirm={confirm}
          onCancel={() => setPending(null)}
        />
      )}
    </Box>
  );
}
