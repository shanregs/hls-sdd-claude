import { useCallback, useEffect, useState } from "react";
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  MenuItem,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { useGrantedActions } from "../common/useGrantedActions";
import { formatDate } from "../teachers/formatters";
import { ReplacementForm } from "./ReplacementForm";
import { RequestDetail } from "./RequestDetail";
import { TransferForm } from "./TransferForm";
import {
  KIND_LABELS,
  STATUS_LABELS,
  TRANSFERS_ROUTE,
  getRequest,
  listRequests,
  type MovementRequest,
} from "./transfersApi";

type Rows =
  | { kind: "loading" }
  | { kind: "error"; reason: string }
  | { kind: "ready"; rows: MovementRequest[] };

const DEFAULT = "PENDING,APPROVED";

/** OPERATIONS -> Transfers and Replacements (spec 027): the request list, filters and the two request forms. */
export function TransfersPage() {
  const { authFetch } = useAuth();
  const granted = useGrantedActions(TRANSFERS_ROUTE);
  const [status, setStatus] = useState(DEFAULT);
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [rows, setRows] = useState<Rows>({ kind: "loading" });
  const [transferring, setTransferring] = useState(false);
  const [replacing, setReplacing] = useState(false);
  const [open, setOpen] = useState<MovementRequest | null>(null);

  const load = useCallback(async () => {
    setRows({ kind: "loading" });
    const list = await listRequests(authFetch, { status, from, to });
    setRows(
      list.ok
        ? { kind: "ready", rows: list.data.content }
        : { kind: "error", reason: list.reason },
    );
  }, [authFetch, status, from, to]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- initial and filter-driven fetch
    void load();
  }, [load]);

  async function openRequest(id: string) {
    const result = await getRequest(authFetch, id);
    if (result.ok) setOpen(result.data);
  }

  function saved(request: MovementRequest) {
    setTransferring(false);
    setReplacing(false);
    setOpen(request);
    void load();
  }

  return (
    <Box>
      <Stack
        direction={{ xs: "column", sm: "row" }}
        spacing={1}
        sx={{ alignItems: { sm: "center" }, justifyContent: "space-between", mb: 2 }}
      >
        <Typography variant="h5" component="h1">
          Transfers and Replacements
        </Typography>
        {granted.has("CREATE") && (
          <Stack direction="row" spacing={1}>
            <Button variant="contained" onClick={() => setTransferring(true)}>
              Request a transfer
            </Button>
            <Button variant="outlined" onClick={() => setReplacing(true)}>
              Replace a Teacher
            </Button>
          </Stack>
        )}
      </Stack>

      <Stack direction={{ xs: "column", sm: "row" }} spacing={2} sx={{ mb: 2 }}>
        <TextField
          select
          size="small"
          label="Status"
          value={status}
          onChange={(e) => setStatus(e.target.value)}
          sx={{ minWidth: 200 }}
        >
          <MenuItem value={DEFAULT}>Pending and Approved</MenuItem>
          {Object.entries(STATUS_LABELS).map(([value, label]) => (
            <MenuItem key={value} value={value}>
              {label}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          size="small"
          label="Effective from"
          type="date"
          value={from}
          onChange={(e) => setFrom(e.target.value)}
          slotProps={{ inputLabel: { shrink: true } }}
        />
        <TextField
          size="small"
          label="Effective to"
          type="date"
          value={to}
          onChange={(e) => setTo(e.target.value)}
          slotProps={{ inputLabel: { shrink: true } }}
        />
      </Stack>

      {rows.kind === "loading" && <CircularProgress aria-label="Loading requests" />}
      {rows.kind === "error" && <Alert severity="error">{rows.reason}</Alert>}
      {rows.kind === "ready" && rows.rows.length === 0 && (
        <Typography color="text.secondary">No requests here.</Typography>
      )}
      {rows.kind === "ready" && rows.rows.length > 0 && (
        <TableContainer>
          <Table size="small" aria-label="Transfer and replacement requests">
            <TableHead>
              <TableRow>
                <TableCell>Effective</TableCell>
                <TableCell>Type</TableCell>
                <TableCell>Teacher</TableCell>
                <TableCell>School</TableCell>
                <TableCell>Status</TableCell>
                <TableCell />
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.rows.map((r) => (
                <TableRow key={r.id} hover>
                  <TableCell>{formatDate(r.effectiveOn)}</TableCell>
                  <TableCell>{KIND_LABELS[r.kind]}</TableCell>
                  <TableCell>{r.teacher.name}</TableCell>
                  <TableCell>
                    {r.kind === "TRANSFER"
                      ? `${r.fromSchool.name} to ${r.toSchool.name}`
                      : r.fromSchool.name}
                  </TableCell>
                  <TableCell>
                    <Chip size="small" label={STATUS_LABELS[r.status]} />
                  </TableCell>
                  <TableCell>
                    <Button size="small" onClick={() => void openRequest(r.id)}>
                      Open
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      {transferring && <TransferForm onClose={() => setTransferring(false)} onSaved={saved} />}
      {replacing && <ReplacementForm onClose={() => setReplacing(false)} onSaved={saved} />}
      {open && (
        <RequestDetail
          request={open}
          onClose={() => setOpen(null)}
          onChanged={(r) => {
            setOpen(r);
            void load();
          }}
        />
      )}
    </Box>
  );
}
