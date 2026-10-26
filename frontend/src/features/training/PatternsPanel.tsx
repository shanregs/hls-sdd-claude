import { useCallback, useEffect, useState } from "react";
import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { formatDate, formatTime } from "./format";
import { PatternDialog } from "./PatternDialog";
import {
  describeRule,
  getPatterns,
  skipMonth,
  stopPattern,
  unskipMonth,
  type PatternView,
} from "./trainingApi";

type Mode =
  | { kind: "edit" | "skip" | "stop"; pattern: PatternView }
  | { kind: "new" }
  | null;

/** The Patterns tab (Admin, Director): standing monthly rules with skip and stop. */
export function PatternsPanel({ onChanged }: { onChanged: () => void }) {
  const { authFetch } = useAuth();
  const [patterns, setPatterns] = useState<PatternView[]>([]);
  const [failure, setFailure] = useState<string | null>(null);
  const [mode, setMode] = useState<Mode>(null);
  const [month, setMonth] = useState("");
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    const result = await getPatterns(authFetch);
    if (result.ok) {
      setPatterns(result.data);
      setFailure(null);
    } else {
      setFailure(result.reason);
    }
  }, [authFetch]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- loads the patterns once
    void load();
  }, [load]);

  function close() {
    setMode(null);
    setMonth("");
    setReason("");
    setError(null);
  }

  async function done(call: Promise<{ ok: boolean; reason?: string }>) {
    const result = await call;
    if (!result.ok) {
      setError(result.reason ?? "Something went wrong. Please try again.");
      return;
    }
    close();
    await load();
    onChanged();
  }

  return (
    <Box>
      <Button
        variant="contained"
        onClick={() => setMode({ kind: "new" })}
        sx={{ mb: 2 }}
      >
        New pattern
      </Button>
      {failure && <Alert severity="error">{failure}</Alert>}
      {patterns.length === 0 && !failure && (
        <Typography>No patterns yet.</Typography>
      )}
      <Stack component="ul" spacing={2} sx={{ listStyle: "none", p: 0, m: 0 }}>
        {patterns.map((p) => (
          <li key={p.id}>
            <Typography>
              <strong>{p.topic}</strong>: {describeRule(p)},{" "}
              {formatTime(p.startsAt)} to {formatTime(p.endsAt)}
              {p.active ? "" : ` (stopped after ${formatDate(p.endMonth)})`}
            </Typography>
            {p.skips.length > 0 && (
              <ul>
                {p.skips.map((s) => (
                  <li key={s.month}>
                    Skipped {formatDate(s.month)}: {s.reason}{" "}
                    <Button
                      size="small"
                      onClick={() =>
                        void done(unskipMonth(authFetch, p.id, s.month))
                      }
                    >
                      Remove skip for {formatDate(s.month)}
                    </Button>
                  </li>
                ))}
              </ul>
            )}
            {p.active && (
              <Stack direction="row" spacing={1}>
                <Button
                  size="small"
                  onClick={() => setMode({ kind: "edit", pattern: p })}
                >
                  Change
                </Button>
                <Button
                  size="small"
                  onClick={() => setMode({ kind: "skip", pattern: p })}
                >
                  Skip a month
                </Button>
                <Button
                  size="small"
                  color="warning"
                  onClick={() => setMode({ kind: "stop", pattern: p })}
                >
                  Stop
                </Button>
              </Stack>
            )}
          </li>
        ))}
      </Stack>

      {mode?.kind === "new" && (
        <PatternDialog
          onCancel={close}
          onSaved={() => {
            close();
            void load();
            onChanged();
          }}
        />
      )}
      {mode?.kind === "edit" && (
        <PatternDialog
          pattern={mode.pattern}
          onCancel={close}
          onSaved={() => {
            close();
            void load();
            onChanged();
          }}
        />
      )}
      {(mode?.kind === "skip" || mode?.kind === "stop") && (
        <Dialog open onClose={close} fullWidth maxWidth="xs">
          <DialogTitle>
            {mode.kind === "skip" ? "Skip a month" : "Stop this pattern"}
          </DialogTitle>
          <DialogContent>
            <Stack spacing={2} sx={{ mt: 1 }}>
              {error && <Alert severity="error">{error}</Alert>}
              <TextField
                label={
                  mode.kind === "skip" ? "Month to skip" : "Stop from month"
                }
                type="date"
                value={month}
                onChange={(e) => setMonth(e.target.value)}
                slotProps={{ inputLabel: { shrink: true } }}
              />
              {mode.kind === "skip" && (
                <TextField
                  label="Reason"
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                  slotProps={{ htmlInput: { maxLength: 300 } }}
                />
              )}
            </Stack>
          </DialogContent>
          <DialogActions>
            <Button onClick={close}>Back</Button>
            <Button
              variant="contained"
              onClick={() => {
                if (!month) {
                  setError("Choose the month.");
                  return;
                }
                if (mode.kind === "skip") {
                  if (!reason.trim()) {
                    setError("Enter the reason for skipping.");
                    return;
                  }
                  void done(
                    skipMonth(authFetch, mode.pattern.id, month, reason.trim()),
                  );
                } else {
                  void done(
                    stopPattern(
                      authFetch,
                      mode.pattern.id,
                      month,
                      mode.pattern.version,
                    ),
                  );
                }
              }}
            >
              {mode.kind === "skip" ? "Skip month" : "Stop pattern"}
            </Button>
          </DialogActions>
        </Dialog>
      )}
    </Box>
  );
}
