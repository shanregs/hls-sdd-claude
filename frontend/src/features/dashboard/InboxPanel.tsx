import { useCallback, useEffect, useState } from "react";
import { Link as RouterLink } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  Chip,
  Link,
  List,
  ListItem,
  Paper,
  Stack,
  Typography,
} from "@mui/material";
import RefreshIcon from "@mui/icons-material/Refresh";
import { useAuth } from "../../auth/useAuth";
import { fetchInbox, type InboxResponse, type TileLink } from "./dashboardApi";
import { formatDate } from "./format";

function target(link: TileLink) {
  const search = new URLSearchParams(link.query).toString();
  return { pathname: link.path, search: search === "" ? "" : `?${search}` };
}

const KIND_LABELS: Record<string, string> = {
  MANAGER_LEAVE: "Manager leave",
  FORGOTTEN_CHECK_IN: "Forgotten check-ins",
  FINAL_STAGE_PROSPECT: "Final Stage prospects",
  PAYROLL_RUN: "Pay runs",
  TASK_VERIFICATION: "Task verification",
};

function kindLabel(kind: string): string {
  return KIND_LABELS[kind] ?? kind;
}

type State =
  | { kind: "loading" }
  | { kind: "error"; reason: string }
  | { kind: "ready"; inbox: InboxResponse };

/**
 * The approvals waiting for the caller (spec 028 US6): totals per kind, then the oldest first with who asked, what for, when and
 * how many working days ago, each one click from the screen where it is decided.
 */
export function InboxPanel() {
  const { authFetch } = useAuth();
  const [state, setState] = useState<State>({ kind: "loading" });
  const [reload, setReload] = useState(0);

  const load = useCallback(async () => {
    const result = await fetchInbox(authFetch, { size: 50 });
    setState(
      result.ok
        ? { kind: "ready", inbox: result.data }
        : { kind: "error", reason: result.reason },
    );
  }, [authFetch]);

  useEffect(() => {
    void load();
  }, [load, reload]);

  return (
    <Box component="section" aria-labelledby="dashboard-inbox" sx={{ mb: 3 }}>
      <Stack direction="row" sx={{ alignItems: "center", mb: 1 }}>
        <Typography
          variant="h6"
          component="h3"
          id="dashboard-inbox"
          sx={{ flexGrow: 1 }}
        >
          Waiting for your decision
        </Typography>
        <Button
          size="small"
          startIcon={<RefreshIcon />}
          onClick={() => setReload((n) => n + 1)}
        >
          Refresh
        </Button>
      </Stack>
      {state.kind === "loading" && (
        <Typography variant="body2" color="text.secondary">
          Loading…
        </Typography>
      )}
      {state.kind === "error" && <Alert severity="error">{state.reason}</Alert>}
      {state.kind === "ready" && (
        <Paper variant="outlined" sx={{ p: 2 }}>
          <Stack direction="row" sx={{ flexWrap: "wrap", gap: 1, mb: 1 }}>
            {Object.entries(state.inbox.totals).map(([kind, count]) => (
              <Chip
                key={kind}
                size="small"
                variant="outlined"
                label={`${kindLabel(kind)}: ${count}`}
              />
            ))}
          </Stack>
          {state.inbox.items.length === 0 ? (
            <Typography variant="body2" color="text.secondary">
              Nothing is waiting for you.
            </Typography>
          ) : (
            <List dense disablePadding>
              {state.inbox.items.map((item) => (
                <ListItem
                  key={`${item.kind}-${item.itemId}`}
                  disableGutters
                  sx={{ display: "block" }}
                >
                  <Link
                    component={RouterLink}
                    to={target(item.link)}
                    underline="hover"
                  >
                    {`${item.subject} (${item.requester})`}
                  </Link>
                  <Typography variant="body2" color="text.secondary">
                    {`${kindLabel(item.kind)} · asked ${item.askedOn ? formatDate(item.askedOn) : "-"} · ${item.ageDays} working ${item.ageDays === 1 ? "day" : "days"} ago`}
                  </Typography>
                </ListItem>
              ))}
            </List>
          )}
        </Paper>
      )}
    </Box>
  );
}
