import { useCallback, useEffect, useState } from "react";
import {
  Alert,
  Box,
  Chip,
  CircularProgress,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useParams } from "react-router-dom";
import { useAuth } from "../../auth/useAuth";
import { formatDate } from "./format";
import {
  STATUS_LABELS,
  TYPE_LABELS,
  getHistory,
  type TrainingHistory,
} from "./trainingApi";

const OUTCOME_LABEL: Record<string, string> = {
  PRESENT: "Present",
  ABSENT: "Absent",
  EXCUSED: "Excused",
};

interface Props {
  /** Show the caller's own history instead of the person in the route. */
  mine?: boolean;
  embedded?: boolean;
}

/**
 * Training history of one person with a year picker (spec 024 US6): Admin and Director from the calendar
 * (/operations/training/history/:kind/:id), a Manager as part of My Training and a Teacher as My Training History.
 */
export function HistoryPage({ mine = false, embedded = false }: Props) {
  const { authFetch } = useAuth();
  const params = useParams();
  const kind = params.kind?.toUpperCase();
  const id = params.id;
  const [yearText, setYearText] = useState(String(new Date().getFullYear()));
  const year = /^\d{4}$/.test(yearText)
    ? Number(yearText)
    : new Date().getFullYear();
  const [history, setHistory] = useState<TrainingHistory | null>(null);
  const [failure, setFailure] = useState<string | null>(null);

  const load = useCallback(async () => {
    const person = !mine && kind && id ? { kind, id } : undefined;
    const result = await getHistory(authFetch, year, person);
    if (result.ok) {
      setHistory(result.data);
      setFailure(null);
    } else {
      setFailure(result.reason);
    }
  }, [authFetch, year, mine, kind, id]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- year driven fetch
    void load();
  }, [load]);

  const title = mine
    ? "My Training History"
    : `Training history${history ? `: ${history.person.name}` : ""}`;
  return (
    <Box>
      <Typography
        variant={embedded ? "h6" : "h5"}
        component={embedded ? "h2" : "h1"}
        sx={{ mb: 2 }}
      >
        {title}
      </Typography>
      <TextField
        size="small"
        label="Year"
        type="number"
        value={yearText}
        onChange={(e) => setYearText(e.target.value)}
        sx={{ mb: 2, width: 120 }}
      />
      {failure && <Alert severity="error">{failure}</Alert>}
      {!history && !failure && <CircularProgress aria-label="Loading" />}
      {history && (
        <Stack spacing={2}>
          <Typography>
            {history.year}: attended {history.yearly.attended} of{" "}
            {history.yearly.expected} expected sessions.
          </Typography>
          {history.induction.length > 0 && (
            <Box component="section" aria-label="Induction">
              <Typography variant="subtitle2" component="h3">
                Induction
              </Typography>
              <ul>
                {history.induction.map((i) => (
                  <li key={i.batchId}>
                    {i.batchName}, {formatDate(i.startsOn)} to{" "}
                    {formatDate(i.endsOn)}
                    {i.result
                      ? `: ${i.result === "COMPLETED" ? "Completed" : "Not completed"}`
                      : ""}
                    {i.signedAt ? `, signed off ${formatDate(i.signedAt)}` : ""}
                  </li>
                ))}
              </ul>
            </Box>
          )}
          {history.entries.length === 0 && (
            <Typography>No training recorded for {history.year}.</Typography>
          )}
          <Stack
            component="ul"
            spacing={1}
            sx={{ listStyle: "none", p: 0, m: 0 }}
          >
            {history.entries.map((e) => (
              <li key={e.sessionId}>
                <Stack
                  direction="row"
                  spacing={1}
                  useFlexGap
                  sx={{ alignItems: "center", flexWrap: "wrap" }}
                >
                  <span>{formatDate(e.date)}</span>
                  <strong>{e.topic}</strong>
                  <span>{TYPE_LABELS[e.sessionType]}</span>
                  <Chip
                    size="small"
                    variant="outlined"
                    label={STATUS_LABELS[e.sessionStatus]}
                  />
                  <Chip
                    size="small"
                    label={
                      e.role === "REQUIRED"
                        ? "Required"
                        : e.role === "WALK_IN"
                          ? "Walk-in"
                          : "Registered"
                    }
                  />
                  {e.outcome && (
                    <Chip
                      size="small"
                      color={
                        e.outcome === "PRESENT"
                          ? "success"
                          : e.outcome === "ABSENT"
                            ? "error"
                            : "default"
                      }
                      label={OUTCOME_LABEL[e.outcome]}
                    />
                  )}
                  {e.exceptionStatus && (
                    <Chip
                      size="small"
                      color="warning"
                      label={`Exception: ${e.exceptionStatus.toLowerCase()}`}
                    />
                  )}
                </Stack>
                {e.reason && (
                  <Typography variant="body2">Reason: {e.reason}</Typography>
                )}
                {e.certificateNote && (
                  <Typography variant="body2">
                    Note: {e.certificateNote}
                  </Typography>
                )}
                {e.internalRemark && (
                  <Typography variant="body2" color="text.secondary">
                    Internal remark: {e.internalRemark}
                  </Typography>
                )}
              </li>
            ))}
          </Stack>
        </Stack>
      )}
    </Box>
  );
}
