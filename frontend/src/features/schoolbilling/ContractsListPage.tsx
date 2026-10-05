import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Alert,
  Button,
  MenuItem,
  Pagination,
  Paper,
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
import { formatDate } from "../teachers/formatters";
import { ContractStatusChip } from "./ContractStatusChip";
import { statusLabel } from "./contractStatus";
import {
  listContracts,
  type ContractListRow,
  type ContractStatus,
} from "./schoolContractsApi";

const PAGE_SIZE = 25;
const STATUSES: ContractStatus[] = [
  "ACTIVE",
  "ENDS_SOON",
  "MOU_PENDING",
  "NONE",
  "ENDED",
];

type State =
  | { kind: "loading" }
  | { kind: "error"; reason: string }
  | { kind: "ready"; rows: ContractListRow[]; total: number };

/**
 * OPERATIONS -> School Contracts (spec 012 US4): every School in scope with its MoU status, filled and
 * vacant positions, and the Teachers not yet mapped to the contract in effect.
 */
export function ContractsListPage() {
  const { authFetch } = useAuth();
  const navigate = useNavigate();
  const [state, setState] = useState<State>({ kind: "loading" });
  const [status, setStatus] = useState<ContractStatus | "">("");
  const [page, setPage] = useState(0);

  const load = useCallback(async () => {
    setState({ kind: "loading" });
    const result = await listContracts(authFetch, {
      status: status || undefined,
      page,
      size: PAGE_SIZE,
    });
    setState(
      result.ok
        ? {
            kind: "ready",
            rows: result.data.content,
            total: result.data.totalElements,
          }
        : { kind: "error", reason: result.reason },
    );
  }, [authFetch, status, page]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- initial and filter-driven fetch
    void load();
  }, [load]);

  return (
    <Paper sx={{ p: 2 }}>
      <Stack spacing={2}>
        <Typography variant="h5" component="h1">
          School Contracts
        </Typography>
        <Typography variant="body2" color="text.secondary">
          The MoU for each School: how many Teachers it covers, the salary the
          School pays, and who is mapped to each position.
        </Typography>
        <TextField
          select
          size="small"
          label="Status"
          value={status}
          onChange={(e) => {
            setStatus(e.target.value as ContractStatus | "");
            setPage(0);
          }}
          sx={{ maxWidth: 240 }}
        >
          <MenuItem value="">All</MenuItem>
          {STATUSES.map((s) => (
            <MenuItem key={s} value={s}>
              {statusLabel(s)}
            </MenuItem>
          ))}
        </TextField>

        {state.kind === "loading" && (
          <Typography role="status">Loading contracts...</Typography>
        )}
        {state.kind === "error" && (
          <Alert severity="error" role="alert">
            {state.reason}
          </Alert>
        )}
        {state.kind === "ready" && state.rows.length === 0 && (
          <Alert severity="info">
            No Schools match. Contracts appear here once a School has a MoU.
          </Alert>
        )}
        {state.kind === "ready" && state.rows.length > 0 && (
          <>
            <TableContainer>
              <Table size="small" aria-label="School contracts">
                <TableHead>
                  <TableRow>
                    <TableCell>School</TableCell>
                    <TableCell>Zone Manager</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell>Teachers</TableCell>
                    <TableCell>Dates</TableCell>
                    <TableCell>
                      <span style={{ position: "absolute", left: -9999 }}>
                        Actions
                      </span>
                    </TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {state.rows.map((row) => (
                    <TableRow key={row.schoolId} hover>
                      <TableCell>{row.schoolName}</TableCell>
                      <TableCell>{row.zoneManagerName ?? "None"}</TableCell>
                      <TableCell>
                        <ContractStatusChip status={row.status} />
                      </TableCell>
                      <TableCell>
                        {row.teacherCount === null
                          ? row.unmapped > 0
                            ? `${row.unmapped} not mapped`
                            : "-"
                          : `${row.filled} filled, ${row.vacant} vacant${
                              row.unmapped > 0
                                ? `, ${row.unmapped} not mapped`
                                : ""
                            }`}
                      </TableCell>
                      <TableCell>
                        {row.startsOn
                          ? `${formatDate(row.startsOn)}${
                              row.endsOn
                                ? ` to ${formatDate(row.endsOn)}`
                                : " onwards"
                            }`
                          : "-"}
                      </TableCell>
                      <TableCell align="right">
                        <Button
                          size="small"
                          onClick={() =>
                            navigate(
                              `/operations/school-contracts/schools/${row.schoolId}`,
                            )
                          }
                          aria-label={`Open contract of ${row.schoolName}`}
                        >
                          Open
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
            {state.total > PAGE_SIZE && (
              <Pagination
                count={Math.ceil(state.total / PAGE_SIZE)}
                page={page + 1}
                onChange={(_e, value) => setPage(value - 1)}
              />
            )}
          </>
        )}
      </Stack>
    </Paper>
  );
}
