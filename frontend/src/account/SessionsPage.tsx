import { useEffect, useState } from "react";
import {
  Alert,
  Box,
  IconButton,
  Stack,
  Tooltip,
  Typography,
} from "@mui/material";
import DeleteSweepOutlined from "@mui/icons-material/DeleteSweepOutlined";
import { useAuth } from "../auth/useAuth";
import { ConfirmDialog } from "../features/common/ConfirmDialog";
import { useGrantedActions } from "../features/common/useGrantedActions";
import { SessionsTable } from "./SessionsTable";
import {
  endAllMySessions,
  endMySession,
  listMySessions,
  type SessionRow,
} from "./sessionsApi";

type Pending =
  { kind: "one"; row: SessionRow; number: number } | { kind: "all" };

/**
 * Sessions (every signed-in user): the devices signed in as you, as a grid with the session number, when
 * it started and which client started it. Each row has a delete icon, and an icon at the top deletes
 * them all. Deleting the session you are using, or all of them, signs you out (spec 001 FR-015).
 */
export function SessionsPage() {
  const { authFetch, logout } = useAuth();
  const canDelete = useGrantedActions("/account/sessions").has("DELETE");
  const [rows, setRows] = useState<SessionRow[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState<Pending | null>(null);
  const [busy, setBusy] = useState(false);

  const [reloadCount, setReloadCount] = useState(0);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const result = await listMySessions(authFetch);
      if (cancelled) return;
      if (result.ok) {
        setError(null);
        setRows(result.data);
      } else {
        setError(result.reason);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch, reloadCount]);

  const confirm = async () => {
    if (!pending) return;
    setBusy(true);
    const result =
      pending.kind === "all"
        ? await endAllMySessions(authFetch)
        : await endMySession(authFetch, pending.row.id);
    setBusy(false);
    setPending(null);
    if (!result.ok) {
      setError("Could not end the session. Please try again.");
      return;
    }
    // The server stops honouring a session as soon as it is ended, so the current one needs a sign-out.
    if (pending.kind === "all" || pending.row.current) {
      await logout();
      return;
    }
    setReloadCount((n) => n + 1);
  };

  const signsOut =
    pending?.kind === "all" || (pending?.kind === "one" && pending.row.current);

  return (
    <Box sx={{ maxWidth: 820 }}>
      <Stack
        direction="row"
        sx={{ alignItems: "center", justifyContent: "space-between", mb: 2 }}
      >
        <Typography variant="h5" component="h1">
          My sessions
        </Typography>
        {canDelete && (
          <Tooltip title="Delete all sessions (signs you out)">
            <span>
              <IconButton
                color="error"
                aria-label="Delete all my sessions"
                disabled={!rows || rows.length === 0}
                onClick={() => setPending({ kind: "all" })}
              >
                <DeleteSweepOutlined />
              </IconButton>
            </span>
          </Tooltip>
        )}
      </Stack>
      {error && (
        <Alert severity="error" sx={{ mb: 2 }} role="alert">
          {error}
        </Alert>
      )}
      {!rows && !error && <Typography>Loading…</Typography>}
      {rows && rows.length === 0 && (
        <Typography>No active sessions.</Typography>
      )}
      {rows && rows.length > 0 && (
        <SessionsTable
          rows={rows}
          onDelete={
            canDelete
              ? (row, number) => setPending({ kind: "one", row, number })
              : undefined
          }
        />
      )}
      {pending && (
        <ConfirmDialog
          title={
            pending.kind === "all"
              ? "Delete all sessions?"
              : `Delete session ${pending.number}?`
          }
          message={
            signsOut
              ? "This ends the session you are using, so you will be signed out."
              : "That device will be signed out."
          }
          confirmLabel="Delete"
          destructive
          busy={busy}
          onConfirm={confirm}
          onCancel={() => setPending(null)}
        />
      )}
    </Box>
  );
}
