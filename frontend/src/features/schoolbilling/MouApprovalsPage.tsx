import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Alert,
  Button,
  Pagination,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { formatDate } from "../teachers/formatters";
import { listPendingApprovals, type PendingRow } from "./mouApprovalApi";
import { documentLabel, formatInstant } from "./mouFormat";

const PAGE_SIZE = 25;

type State =
  | { kind: "loading" }
  | { kind: "error"; reason: string }
  | { kind: "ready"; rows: PendingRow[]; total: number };

/**
 * OPERATIONS -> MoU Approvals (spec 031 US3): the MoUs waiting for a decision in the caller scope, oldest first. A
 * Director sees every School; a Zone Manager only their own.
 */
export function MouApprovalsPage() {
  const { authFetch } = useAuth();
  const navigate = useNavigate();
  const [state, setState] = useState<State>({ kind: "loading" });
  const [page, setPage] = useState(0);

  const load = useCallback(async () => {
    setState({ kind: "loading" });
    const result = await listPendingApprovals(authFetch, page, PAGE_SIZE);
    setState(
      result.ok
        ? {
            kind: "ready",
            rows: result.data.content,
            total: result.data.totalElements,
          }
        : { kind: "error", reason: result.reason },
    );
  }, [authFetch, page]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- initial and page-driven fetch
    void load();
  }, [load]);

  return (
    <Paper sx={{ p: 2 }}>
      <Stack spacing={2}>
        <Typography variant="h5" component="h1">
          MoU Approvals
        </Typography>
        <Typography variant="body2" color="text.secondary">
          MoUs sent for approval and not yet decided, oldest first. Open one to
          read the terms and the signed document, then approve or reject it.
        </Typography>
        {state.kind === "loading" && (
          <Typography role="status">Loading approvals...</Typography>
        )}
        {state.kind === "error" && (
          <Alert severity="error" role="alert">
            {state.reason}
          </Alert>
        )}
        {state.kind === "ready" && state.rows.length === 0 && (
          <Alert severity="info">No MoU is waiting for approval.</Alert>
        )}
        {state.kind === "ready" && state.rows.length > 0 && (
          <>
            <TableContainer>
              <Table size="small" aria-label="MoUs waiting for approval">
                <TableHead>
                  <TableRow>
                    <TableCell>School</TableCell>
                    <TableCell>Zone Manager</TableCell>
                    <TableCell>Submitted</TableCell>
                    <TableCell>Round</TableCell>
                    <TableCell>Document</TableCell>
                    <TableCell>Terms</TableCell>
                    <TableCell>
                      <span style={{ position: "absolute", left: -9999 }}>
                        Actions
                      </span>
                    </TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {state.rows.map((row) => (
                    <TableRow key={row.contractId} hover>
                      <TableCell>{row.schoolName}</TableCell>
                      <TableCell>{row.zoneManagerName ?? "None"}</TableCell>
                      <TableCell>
                        {formatInstant(row.submittedAt)}
                        {row.submittedBy && ` by ${row.submittedBy.name}`}
                      </TableCell>
                      <TableCell>{row.round}</TableCell>
                      <TableCell>{documentLabel(row.documentState)}</TableCell>
                      <TableCell>
                        {row.teacherCount ?? "-"} Teacher
                        {row.teacherCount === 1 ? "" : "s"}, from{" "}
                        {formatDate(row.startsOn)}
                      </TableCell>
                      <TableCell align="right">
                        <Button
                          size="small"
                          aria-label={`Open MoU of ${row.schoolName}`}
                          onClick={() =>
                            navigate(
                              `/operations/school-contracts/schools/${row.schoolId}`,
                            )
                          }
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
