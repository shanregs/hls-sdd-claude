import { useCallback, useEffect, useState } from "react";
import {
  Alert,
  Box,
  CircularProgress,
  Link,
  List,
  ListItem,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { formatDate } from "../teachers/formatters";
import {
  KIND_LABELS,
  getNeedsAttention,
  type CalendarItem,
  type NeedsAttentionResponse,
} from "./activityCalendarApi";

type State =
  | { kind: "loading" }
  | { kind: "error"; reason: string }
  | { kind: "ready"; data: NeedsAttentionResponse };

function lastDay(month: string): string {
  const [year, mon] = month.split("-").map(Number);
  const day = new Date(year, mon, 0).getDate();
  return `${month}-${String(day).padStart(2, "0")}`;
}

/** Missed and rescheduled items of the month, grouped by Manager, oldest first. Choosing one opens its detail. */
export function NeedsAttention({
  month,
  managerId,
  zoneId,
  kind,
  onOpen,
}: {
  month: string;
  managerId: string;
  zoneId: string;
  kind: string;
  onOpen: (item: CalendarItem) => void;
}) {
  const { authFetch } = useAuth();
  const [state, setState] = useState<State>({ kind: "loading" });

  const load = useCallback(async () => {
    setState({ kind: "loading" });
    const result = await getNeedsAttention(authFetch, {
      from: `${month}-01`,
      to: lastDay(month),
      managerId: managerId || undefined,
      zoneId: zoneId || undefined,
      kind: kind || undefined,
    });
    setState(
      result.ok
        ? { kind: "ready", data: result.data }
        : { kind: "error", reason: result.reason },
    );
  }, [authFetch, month, managerId, zoneId, kind]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- filter-driven fetch
    void load();
  }, [load]);

  if (state.kind === "loading") return <CircularProgress aria-label="Loading" />;
  if (state.kind === "error") return <Alert severity="error">{state.reason}</Alert>;
  const groups = state.data.groups ?? [];
  if (groups.length === 0) {
    return (
      <Typography color="text.secondary">
        Nothing needs attention in this period.
      </Typography>
    );
  }
  return (
    <Box>
      {state.data.partial && (
        <Alert severity="warning" sx={{ mb: 1 }}>
          Some activities could not be loaded, so this list may be incomplete.
        </Alert>
      )}
      {groups.map((group) => (
        <Box key={group.userId} component="section" sx={{ mb: 2 }}>
          <Typography variant="subtitle1" component="h3" sx={{ fontWeight: 600 }}>
            {group.name}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {`Missed ${group.missed} · Rescheduled ${group.rescheduled}`}
          </Typography>
          <List dense>
            {group.entries.map((entry) => {
              const item = entry.item;
              const parts = [
                KIND_LABELS[item.kind],
                item.originalDate
                  ? `first planned ${formatDate(item.originalDate)}, now ${formatDate(item.date)}`
                  : formatDate(item.date),
              ];
              if (entry.daysLate != null) parts.push(`${entry.daysLate} days late`);
              if (entry.daysMoved != null) parts.push(`moved ${entry.daysMoved} days`);
              if (item.zoneName) parts.push(item.zoneName);
              return (
                <ListItem key={item.key} disableGutters>
                  <Link component="button" type="button" onClick={() => onOpen(item)} sx={{ textAlign: "left" }}>
                    {parts.join(" · ")}
                  </Link>
                </ListItem>
              );
            })}
          </List>
        </Box>
      ))}
    </Box>
  );
}
