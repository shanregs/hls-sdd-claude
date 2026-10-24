import { useEffect, useState } from "react";
import {
  Alert,
  CircularProgress,
  List,
  ListItem,
  ListItemText,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { useGrantedActions } from "../common/useGrantedActions";
import { formatDate } from "../teachers/formatters";
import {
  TRANSFERS_ROUTE,
  schoolMovements,
  teacherMovements,
  type MovementLine,
} from "./transfersApi";

const KIND: Record<string, string> = {
  INITIAL: "Initial assignment",
  DIRECT: "Direct move",
  TRANSFER: "Transfer",
  REPLACEMENT: "Replacement",
};

type State =
  | { kind: "loading" }
  | { kind: "error"; reason: string }
  | { kind: "ready"; lines: MovementLine[] };

/** A Teacher's or a School's movement history; shown only to roles that hold the Transfers View grant. */
export function MovementHistory({
  teacherId,
  schoolId,
}: {
  teacherId?: string;
  schoolId?: string;
}) {
  const { authFetch } = useAuth();
  const granted = useGrantedActions(TRANSFERS_ROUTE);
  const [state, setState] = useState<State>({ kind: "loading" });
  const allowed = granted.has("VIEW");

  useEffect(() => {
    if (!allowed) return;
    let cancelled = false;
    (async () => {
      const result = teacherId
        ? await teacherMovements(authFetch, teacherId)
        : await schoolMovements(authFetch, schoolId ?? "");
      if (cancelled) return;
      setState(
        result.ok
          ? { kind: "ready", lines: result.data }
          : { kind: "error", reason: result.reason },
      );
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch, teacherId, schoolId, allowed]);

  if (!allowed) return null;
  if (state.kind === "loading")
    return <CircularProgress aria-label="Loading movement history" />;
  if (state.kind === "error")
    return <Alert severity="error">{state.reason}</Alert>;
  if (state.lines.length === 0)
    return <Typography color="text.secondary">No movements yet.</Typography>;
  return (
    <List dense aria-label="Movement history">
      {state.lines.map((l, i) => (
        <ListItem key={`${l.requestId ?? "row"}-${i}`}>
          <ListItemText
            primary={`${l.school.name}${l.positionNumber ? ` (position ${l.positionNumber})` : ""}: ${
              l.from ? formatDate(l.from) : "earlier"
            } to ${l.to ? formatDate(l.to) : "now"} - ${KIND[l.kind] ?? l.kind}`}
            secondary={[
              schoolId ? l.teacher.name : null,
              l.otherTeacher ? `with ${l.otherTeacher.name}` : null,
              l.otherSchool ? `other School: ${l.otherSchool.name}` : null,
              l.reason,
              l.requestedBy ? `requested by ${l.requestedBy}` : null,
              l.approvedBy ? `approved by ${l.approvedBy}` : null,
            ]
              .filter(Boolean)
              .join("; ")}
          />
        </ListItem>
      ))}
    </List>
  );
}
