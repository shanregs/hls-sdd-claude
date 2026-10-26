import { useCallback, useEffect, useState } from "react";
import {
  Alert,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { useAccessModel } from "../../access-model/useAccessModel";
import { formatDate } from "./format";
import {
  closeException,
  exceptionsExportUrl,
  getExceptions,
  type ExceptionRow,
} from "./trainingApi";

const STATUS_LABEL: Record<string, string> = {
  OPEN: "Open",
  CLOSED: "Closed",
  WITHDRAWN: "Withdrawn",
};

const KIND_LABEL: Record<string, string> = {
  EXCUSED: "Excused",
  MADE_UP: "Made up",
  ACKNOWLEDGED: "Acknowledged",
};

/**
 * OPERATIONS -> Training Exceptions (Admin, Director) and the exceptions panel of My Training (Manager, read only): assigned
 * people recorded absent, with the repeat mark and, for Admin and Director, a way to close each one.
 */
export function ExceptionsPage({ embedded = false }: { embedded?: boolean }) {
  const { authFetch } = useAuth();
  const { accessModel } = useAccessModel();
  const management = Boolean(
    accessModel?.roles.some((r) => r === "ADMIN" || r === "DIRECTOR"),
  );
  const [month, setMonth] = useState("");
  const [status, setStatus] = useState("");
  const [role, setRole] = useState("");
  const [rows, setRows] = useState<ExceptionRow[]>([]);
  const [failure, setFailure] = useState<string | null>(null);
  const [closing, setClosing] = useState<ExceptionRow | null>(null);
  const [kind, setKind] = useState("EXCUSED");
  const [note, setNote] = useState("");
  const [makeup, setMakeup] = useState("");
  const [closeError, setCloseError] = useState<string | null>(null);

  const load = useCallback(async () => {
    const result = await getExceptions(authFetch, {
      month: month || undefined,
      status: status || undefined,
      role: role || undefined,
      size: 200,
    });
    if (result.ok) {
      setRows(result.data.content ?? []);
      setFailure(null);
    } else {
      setFailure(result.reason);
    }
  }, [authFetch, month, status, role]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- filter driven fetch
    void load();
  }, [load]);

  async function download() {
    try {
      const response = await authFetch(
        exceptionsExportUrl({
          month: month || undefined,
          status: status || undefined,
          role: role || undefined,
        }),
      );
      if (!response.ok) {
        setFailure("Could not export the exceptions.");
        return;
      }
      const url = URL.createObjectURL(await response.blob());
      const link = document.createElement("a");
      link.href = url;
      link.download = "training-exceptions.csv";
      link.click();
      URL.revokeObjectURL(url);
    } catch {
      setFailure("Could not export the exceptions.");
    }
  }

  async function confirmClose() {
    if (!closing) return;
    if (kind !== "MADE_UP" && !note.trim()) {
      setCloseError("Enter a note.");
      return;
    }
    if (kind === "MADE_UP" && !makeup.trim()) {
      setCloseError(
        "Enter the id of the later session where the person attended.",
      );
      return;
    }
    const result = await closeException(authFetch, closing.id, {
      kind,
      note: note.trim() || undefined,
      makeupSessionId: kind === "MADE_UP" ? makeup.trim() : undefined,
      version: closing.version,
    });
    if (!result.ok) {
      setCloseError(result.reason);
      return;
    }
    setClosing(null);
    setNote("");
    setMakeup("");
    setCloseError(null);
    await load();
  }

  return (
    <Box>
      <Typography
        variant={embedded ? "h6" : "h5"}
        component={embedded ? "h2" : "h1"}
        sx={{ mb: 2 }}
      >
        {embedded ? "Exceptions" : "Training Exceptions"}
      </Typography>
      <Stack
        direction="row"
        spacing={1}
        useFlexGap
        sx={{ mb: 2, flexWrap: "wrap" }}
        role="group"
        aria-label="Filters"
      >
        <TextField
          size="small"
          label="Month"
          type="month"
          value={month}
          onChange={(e) => setMonth(e.target.value)}
          slotProps={{ inputLabel: { shrink: true } }}
        />
        <TextField
          select
          size="small"
          label="Status"
          value={status}
          onChange={(e) => setStatus(e.target.value)}
          slotProps={{ select: { native: true } }}
        >
          <option value="">Open and closed</option>
          <option value="OPEN">Open</option>
          <option value="CLOSED">Closed</option>
          <option value="WITHDRAWN">Withdrawn</option>
        </TextField>
        {management && (
          <TextField
            select
            size="small"
            label="Role"
            value={role}
            onChange={(e) => setRole(e.target.value)}
            slotProps={{ select: { native: true } }}
          >
            <option value="">Teachers and Managers</option>
            <option value="TEACHER">Teachers</option>
            <option value="MANAGER">Managers</option>
          </TextField>
        )}
        {management && (
          <Button onClick={() => void download()}>Export CSV</Button>
        )}
      </Stack>
      {failure && <Alert severity="error">{failure}</Alert>}
      {!failure && rows.length === 0 && <Typography>No exceptions.</Typography>}
      <Stack component="ul" spacing={1} sx={{ listStyle: "none", p: 0, m: 0 }}>
        {rows.map((r) => (
          <li key={r.id}>
            <Stack
              direction="row"
              spacing={1}
              useFlexGap
              sx={{ alignItems: "center", flexWrap: "wrap" }}
            >
              <strong>{r.name}</strong>
              <span>
                {r.topic}, {formatDate(r.date)}
              </span>
              <Chip
                size="small"
                label={STATUS_LABEL[r.status]}
                color={r.status === "OPEN" ? "warning" : "default"}
              />
              {r.closureKind && (
                <Chip
                  size="small"
                  variant="outlined"
                  label={KIND_LABEL[r.closureKind]}
                />
              )}
              {r.repeat && (
                <Chip
                  size="small"
                  color="error"
                  label={`Repeat (${r.countInWindow})`}
                />
              )}
              {r.managerName && <span>Manager: {r.managerName}</span>}
              {r.zoneName && <span>Zone: {r.zoneName}</span>}
              {management && r.status === "OPEN" && (
                <Button size="small" onClick={() => setClosing(r)}>
                  Close exception for {r.name}
                </Button>
              )}
            </Stack>
            {r.closureNote && (
              <Typography variant="body2" color="text.secondary">
                {r.closureNote}
              </Typography>
            )}
          </li>
        ))}
      </Stack>

      {closing && (
        <Dialog open onClose={() => setClosing(null)} fullWidth maxWidth="xs">
          <DialogTitle>Close this exception</DialogTitle>
          <DialogContent>
            <Stack spacing={2} sx={{ mt: 1 }}>
              {closeError && <Alert severity="error">{closeError}</Alert>}
              <TextField
                select
                label="Close as"
                value={kind}
                onChange={(e) => setKind(e.target.value)}
                slotProps={{ select: { native: true } }}
              >
                <option value="EXCUSED">Excused</option>
                <option value="MADE_UP">Made up</option>
                <option value="ACKNOWLEDGED">Acknowledged</option>
              </TextField>
              {kind === "MADE_UP" ? (
                <TextField
                  label="Later session id"
                  value={makeup}
                  onChange={(e) => setMakeup(e.target.value)}
                />
              ) : (
                <TextField
                  label="Note"
                  value={note}
                  onChange={(e) => setNote(e.target.value)}
                  slotProps={{ htmlInput: { maxLength: 300 } }}
                />
              )}
            </Stack>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setClosing(null)}>Back</Button>
            <Button variant="contained" onClick={() => void confirmClose()}>
              Close exception
            </Button>
          </DialogActions>
        </Dialog>
      )}
    </Box>
  );
}
