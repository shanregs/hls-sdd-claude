import { useCallback, useEffect, useState } from "react";
import {
  Alert,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { useAccessModel } from "../../access-model/useAccessModel";
import {
  addWalkIn,
  getAttendance,
  saveAttendance,
  syncAttendance,
  type AttendanceInput,
  type AttendanceRow,
  type Outcome,
  type RowResult,
} from "./trainingApi";
import { StatusChip } from "../../theme/StatusChip";

interface Props {
  sessionId: string;
  onSaved: () => void;
  onClose: () => void;
}

interface Edit {
  outcome: Outcome | "";
  dayValue: string;
  reason: string;
  certificateNote: string;
  internalRemark: string;
}

const OUTCOMES: { value: Outcome; label: string }[] = [
  { value: "PRESENT", label: "Present" },
  { value: "ABSENT", label: "Absent" },
  { value: "EXCUSED", label: "Excused" },
];

function initial(row: AttendanceRow): Edit {
  return {
    outcome: row.outcome ?? "",
    dayValue: row.dayValue != null ? String(row.dayValue) : "1",
    reason: row.reason ?? "",
    certificateNote: row.certificateNote ?? "",
    internalRemark: row.internalRemark ?? "",
  };
}

/** Record who attended (Manager for their own Teachers; Admin and Director for anyone). One save sends every changed row. */
export function AttendanceGrid({ sessionId, onSaved, onClose }: Props) {
  const { authFetch } = useAuth();
  const { accessModel } = useAccessModel();
  const staff = Boolean(
    accessModel?.roles.some(
      (r) => r === "ADMIN" || r === "DIRECTOR" || r === "MANAGER",
    ),
  );
  const [rows, setRows] = useState<AttendanceRow[]>([]);
  const [edits, setEdits] = useState<Record<string, Edit>>({});
  const [results, setResults] = useState<Record<string, RowResult>>({});
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [failure, setFailure] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [walkInOpen, setWalkInOpen] = useState(false);
  const [walkInId, setWalkInId] = useState("");

  const load = useCallback(async () => {
    const result = await getAttendance(authFetch, sessionId);
    if (!result.ok) {
      setFailure(result.reason);
      setLoading(false);
      return;
    }
    setRows(result.data);
    setEdits(
      Object.fromEntries(result.data.map((r) => [r.personId, initial(r)])),
    );
    setLoading(false);
  }, [authFetch, sessionId]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- loads the roster once opened
    void load();
  }, [load]);

  function change(id: string, patch: Partial<Edit>) {
    setEdits((prev) => ({ ...prev, [id]: { ...prev[id], ...patch } }));
  }

  function changedRows(): AttendanceRow[] {
    return rows.filter((r) => {
      const e = edits[r.personId];
      if (!e || e.outcome === "") return false;
      const base = initial(r);
      return JSON.stringify(e) !== JSON.stringify(base) || r.outcome === null;
    });
  }

  async function save() {
    const found: Record<string, string> = {};
    const toSend: AttendanceInput[] = [];
    for (const r of changedRows()) {
      const e = edits[r.personId];
      if (e.outcome !== "PRESENT" && !e.reason.trim() && !r.onLeave) {
        found[r.personId] = "Enter a reason for Absent or Excused.";
        continue;
      }
      toSend.push({
        personKind: r.personKind,
        personId: r.personId,
        outcome: e.outcome as Outcome,
        dayValue: e.outcome === "PRESENT" ? Number(e.dayValue) : null,
        reason: e.reason.trim() || undefined,
        certificateNote: e.certificateNote.trim() || undefined,
        internalRemark: e.internalRemark.trim() || undefined,
        version: r.version,
      });
    }
    setErrors(found);
    setFailure(null);
    if (toSend.length === 0) {
      if (Object.keys(found).length === 0) setFailure("Nothing has changed.");
      return;
    }
    setBusy(true);
    const result = await saveAttendance(authFetch, sessionId, toSend);
    setBusy(false);
    if (!result.ok) {
      setFailure(result.reason);
      return;
    }
    setResults(
      Object.fromEntries(result.data.results.map((x) => [x.personId, x])),
    );
    await load();
    onSaved();
  }

  async function walkIn() {
    if (!walkInId.trim()) return;
    const kind =
      rows.find((r) => r.personId === walkInId.trim())?.personKind ?? "TEACHER";
    const result = await addWalkIn(
      authFetch,
      sessionId,
      kind,
      walkInId.trim(),
      1,
    );
    if (!result.ok) {
      setFailure(result.reason);
      return;
    }
    setResults((prev) => ({
      ...prev,
      [walkInId.trim()]: result.data.results[0],
    }));
    setWalkInOpen(false);
    setWalkInId("");
    await load();
    onSaved();
  }

  async function sync(row: AttendanceRow) {
    if (!row.entryId) return;
    const result = await syncAttendance(authFetch, sessionId, row.entryId);
    if (!result.ok) {
      setFailure(result.reason);
      return;
    }
    setResults((prev) => ({ ...prev, [row.personId]: result.data.results[0] }));
    await load();
  }

  return (
    <Dialog
      open
      onClose={onClose}
      fullWidth
      maxWidth="lg"
      aria-labelledby="attendance-title"
    >
      <DialogTitle id="attendance-title">Record attendance</DialogTitle>
      <DialogContent>
        {failure && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {failure}
          </Alert>
        )}
        {loading && <Typography>Loading...</Typography>}
        {!loading && rows.length === 0 && (
          <Typography>Nobody is assigned or registered yet.</Typography>
        )}
        {rows.length > 0 && (
          <Table size="small" aria-label="Attendance">
            <TableHead>
              <TableRow>
                <TableCell>Name</TableCell>
                <TableCell>Outcome</TableCell>
                <TableCell>Day</TableCell>
                <TableCell>Reason</TableCell>
                <TableCell>Certificate note</TableCell>
                {staff && <TableCell>Internal remark (staff only)</TableCell>}
                <TableCell>Status</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((r) => {
                const e = edits[r.personId] ?? initial(r);
                const result = results[r.personId];
                return (
                  <TableRow key={r.personId}>
                    <TableCell>
                      {r.name}
                      <Stack direction="row" spacing={0.5} sx={{ mt: 0.5 }}>
                        {r.basis === "WALK_IN" && (
                          <Chip size="small" label="Walk-in" />
                        )}
                        {r.onLeave && (
                          <Chip size="small" color="info" label="On leave" />
                        )}
                        {r.syncState === "CLASH" && (
                          <StatusChip kind="pending" size="small" label="Clash" />
                        )}
                      </Stack>
                    </TableCell>
                    <TableCell>
                      <TextField
                        select
                        size="small"
                        label={`Outcome for ${r.name}`}
                        value={e.outcome}
                        onChange={(ev) =>
                          change(r.personId, {
                            outcome: ev.target.value as Outcome,
                          })
                        }
                        slotProps={{ select: { native: true } }}
                      >
                        <option value="">Not recorded</option>
                        {OUTCOMES.map((o) => (
                          <option key={o.value} value={o.value}>
                            {o.label}
                          </option>
                        ))}
                      </TextField>
                    </TableCell>
                    <TableCell>
                      {e.outcome === "PRESENT" && (
                        <TextField
                          select
                          size="small"
                          label={`Day for ${r.name}`}
                          value={e.dayValue}
                          onChange={(ev) =>
                            change(r.personId, { dayValue: ev.target.value })
                          }
                          slotProps={{ select: { native: true } }}
                        >
                          <option value="1">Whole day</option>
                          <option value="0.5">Half day</option>
                        </TextField>
                      )}
                    </TableCell>
                    <TableCell>
                      <TextField
                        size="small"
                        label={`Reason for ${r.name}`}
                        value={e.reason}
                        onChange={(ev) =>
                          change(r.personId, { reason: ev.target.value })
                        }
                        error={Boolean(errors[r.personId])}
                        helperText={errors[r.personId]}
                        slotProps={{ htmlInput: { maxLength: 300 } }}
                      />
                    </TableCell>
                    <TableCell>
                      <TextField
                        size="small"
                        label={`Certificate note for ${r.name}`}
                        value={e.certificateNote}
                        onChange={(ev) =>
                          change(r.personId, {
                            certificateNote: ev.target.value,
                          })
                        }
                        slotProps={{ htmlInput: { maxLength: 300 } }}
                      />
                    </TableCell>
                    {staff && (
                      <TableCell>
                        <TextField
                          size="small"
                          label={`Internal remark for ${r.name}`}
                          value={e.internalRemark}
                          onChange={(ev) =>
                            change(r.personId, {
                              internalRemark: ev.target.value,
                            })
                          }
                          slotProps={{ htmlInput: { maxLength: 500 } }}
                        />
                      </TableCell>
                    )}
                    <TableCell aria-live="polite">
                      {result?.status === "SAVED" && (
                        <StatusChip kind="approved" size="small" label="Saved" />
                      )}
                      {result?.status === "SAVED_CLASH" && (
                        <Typography variant="body2" color="warning.main">
                          Saved with a clash: {result.message}
                        </Typography>
                      )}
                      {result?.status === "REFUSED" && (
                        <Typography variant="body2" color="error">
                          Not saved: {result.message}
                        </Typography>
                      )}
                      {!result && r.clashNote && (
                        <Typography variant="body2" color="warning.main">
                          {r.clashNote}
                        </Typography>
                      )}
                      {r.syncState === "CLASH" && (
                        <Button size="small" onClick={() => void sync(r)}>
                          Try the attendance sync again for {r.name}
                        </Button>
                      )}
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        )}
        {walkInOpen && (
          <Stack direction="row" spacing={1} sx={{ mt: 2 }}>
            <TextField
              size="small"
              label="Walk-in person id"
              value={walkInId}
              onChange={(e) => setWalkInId(e.target.value)}
            />
            <Button variant="contained" onClick={() => void walkIn()}>
              Add walk-in
            </Button>
          </Stack>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={() => setWalkInOpen(true)}>Add a walk-in</Button>
        <Button onClick={onClose}>Close</Button>
        <Button variant="contained" onClick={() => void save()} disabled={busy}>
          Save attendance
        </Button>
      </DialogActions>
    </Dialog>
  );
}
