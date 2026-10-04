import { useEffect, useState } from "react";
import {
  Alert,
  Autocomplete,
  Box,
  Button,
  IconButton,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from "@mui/material";
import DeleteSweepOutlined from "@mui/icons-material/DeleteSweepOutlined";
import { SessionsTable } from "../../account/SessionsTable";
import {
  endSessionAsSystem,
  endSessionsAsSystem,
  listAllSessions,
  type SessionPage,
  type SessionRow,
} from "../../account/sessionsApi";
import { useAuth } from "../../auth/useAuth";
import { ConfirmDialog } from "../common/ConfirmDialog";
import { useGrantedActions } from "../common/useGrantedActions";
import { listUsers, type UserSummary } from "../users/userManagementApi";

const PAGE_SIZE = 25;

type Pending =
  | { kind: "one"; row: SessionRow; number: number }
  | { kind: "user"; user: UserSummary }
  | { kind: "everyone" };

/**
 * All Sessions (System only, spec 001 FR-015a): every user's active sessions as a grid, filtered to one
 * user if wanted. Each row has a delete icon; the icons at the top delete all of the chosen user's
 * sessions, or every user's. Deleting your own session, directly or among many, signs you out.
 */
export function AllSessionsPage() {
  const { authFetch, logout } = useAuth();
  const canDelete = useGrantedActions("/identity/sessions").has("DELETE");
  const [user, setUser] = useState<UserSummary | null>(null);
  const [userQuery, setUserQuery] = useState("");
  const [userOptions, setUserOptions] = useState<UserSummary[]>([]);
  const [page, setPage] = useState(0);
  const [data, setData] = useState<SessionPage | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState<Pending | null>(null);
  const [busy, setBusy] = useState(false);

  const [reloadCount, setReloadCount] = useState(0);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const result = await listAllSessions(authFetch, {
        userId: user?.id ?? "",
        page,
        size: PAGE_SIZE,
      });
      if (cancelled) return;
      if (result.ok) {
        setError(null);
        setData(result.data);
      } else {
        setError(result.reason);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch, user, page, reloadCount]);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const result = await listUsers(authFetch, {
        query: userQuery,
        role: "",
        active: "",
        page: 0,
        size: 20,
      });
      if (!cancelled && result.ok) setUserOptions(result.data.content);
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch, userQuery]);

  const confirm = async () => {
    if (!pending) return;
    setBusy(true);
    const result =
      pending.kind === "one"
        ? await endSessionAsSystem(authFetch, pending.row.id)
        : await endSessionsAsSystem(
            authFetch,
            pending.kind === "user" ? pending.user.id : "",
          );
    setBusy(false);
    setPending(null);
    if (!result.ok) {
      setError("Could not end the session(s). Please try again.");
      return;
    }
    if (result.data.includesCurrent) {
      await logout();
      return;
    }
    setPage(0);
    setReloadCount((n) => n + 1);
  };

  const total = data?.totalElements ?? 0;
  const from = total === 0 ? 0 : page * PAGE_SIZE + 1;
  const to = Math.min(total, (page + 1) * PAGE_SIZE);

  let dialog: { title: string; message: string } | null = null;
  if (pending?.kind === "one") {
    dialog = {
      title: `Delete session ${pending.number}?`,
      message: pending.row.current
        ? "This is the session you are using, so you will be signed out."
        : `${pending.row.userName ?? "That user"} will be signed out on that device.`,
    };
  } else if (pending?.kind === "user") {
    dialog = {
      title: `Delete all sessions of ${pending.user.displayName}?`,
      message: `${pending.user.displayName} will be signed out everywhere.`,
    };
  } else if (pending?.kind === "everyone") {
    dialog = {
      title: "Delete the sessions of every user?",
      message:
        "Everyone is signed out on every device, including you, and must sign in again.",
    };
  }

  return (
    <Box>
      <Typography variant="h5" component="h1" gutterBottom>
        All sessions
      </Typography>
      <Stack
        direction="row"
        spacing={1}
        sx={{ alignItems: "center", mb: 2, flexWrap: "wrap", gap: 1 }}
      >
        <Autocomplete
          size="small"
          sx={{ minWidth: 300 }}
          options={userOptions}
          value={user}
          filterOptions={(options) => options}
          getOptionLabel={(u) => `${u.displayName} (${u.phone})`}
          isOptionEqualToValue={(a, b) => a.id === b.id}
          onInputChange={(_, text, reason) => {
            if (reason === "input") setUserQuery(text);
          }}
          onChange={(_, value) => {
            setUser(value);
            setPage(0);
          }}
          renderInput={(params) => <TextField {...params} label="User" />}
        />
        {user && (
          <Button size="small" onClick={() => setUser(null)}>
            Show all users
          </Button>
        )}
        {canDelete && (
          <>
            <Tooltip
              title={
                user
                  ? `Delete all sessions of ${user.displayName}`
                  : "Choose a user to delete all of their sessions"
              }
            >
              <span>
                <IconButton
                  color="error"
                  aria-label={
                    user
                      ? `Delete all sessions of ${user.displayName}`
                      : "Delete all sessions of the chosen user"
                  }
                  disabled={!user}
                  onClick={() => user && setPending({ kind: "user", user })}
                >
                  <DeleteSweepOutlined />
                </IconButton>
              </span>
            </Tooltip>
            <Tooltip title="Delete the sessions of every user (signs you out too)">
              <IconButton
                color="error"
                aria-label="Delete all sessions of every user"
                onClick={() => setPending({ kind: "everyone" })}
              >
                <DeleteSweepOutlined fontSize="large" />
              </IconButton>
            </Tooltip>
          </>
        )}
      </Stack>
      {error && (
        <Alert severity="error" sx={{ mb: 2 }} role="alert">
          {error}
        </Alert>
      )}
      {!data && !error && <Typography>Loading…</Typography>}
      {data && data.content.length === 0 && (
        <Typography>No active sessions.</Typography>
      )}
      {data && data.content.length > 0 && (
        <>
          <SessionsTable
            rows={data.content}
            startNumber={from}
            showUser
            onDelete={
              canDelete
                ? (row, number) => setPending({ kind: "one", row, number })
                : undefined
            }
          />
          <Stack
            direction="row"
            spacing={2}
            sx={{ mt: 1, alignItems: "center" }}
          >
            <Typography variant="body2">
              Showing {from}-{to} of {total}
            </Typography>
            <Button disabled={page === 0} onClick={() => setPage(page - 1)}>
              Previous page
            </Button>
            <Button disabled={to >= total} onClick={() => setPage(page + 1)}>
              Next page
            </Button>
          </Stack>
        </>
      )}
      {dialog && (
        <ConfirmDialog
          title={dialog.title}
          message={dialog.message}
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
