import {
  Chip,
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
import { RowActionButton } from "../features/common/RowActionButton";
import { describeOrigin, formatDateTime } from "./sessionOrigin";
import type { SessionRow } from "./sessionsApi";

interface SessionsTableProps {
  rows: SessionRow[];
  /** The number shown for the first row (later pages continue the count). */
  startNumber?: number;
  /** Adds a User column, for System's all-users list. */
  showUser?: boolean;
  /** Absent when the caller may not delete sessions: the Delete column is then left out. */
  onDelete?: (row: SessionRow, number: number) => void;
}

/**
 * Active sessions as a grid: session number, who (System's list only), since when, which client started
 * it, and a delete icon (spec 001 FR-015, FR-015a). The row of the device you are using says so, since
 * deleting it signs you out.
 */
export function SessionsTable({
  rows,
  startNumber = 1,
  showUser = false,
  onDelete,
}: SessionsTableProps) {
  return (
    <Paper variant="outlined">
      <TableContainer>
        <Table size="small" aria-label="Sessions">
          <TableHead>
            <TableRow>
              <TableCell sx={{ width: 90 }}>Session</TableCell>
              {showUser && <TableCell>User</TableCell>}
              <TableCell>Signed in since</TableCell>
              <TableCell>Origin</TableCell>
              {onDelete && (
                <TableCell align="center" sx={{ width: 80 }}>
                  Delete
                </TableCell>
              )}
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.map((row, i) => {
              const number = startNumber + i;
              return (
                <TableRow key={row.id} hover>
                  <TableCell component="th" scope="row">
                    {number}
                  </TableCell>
                  {showUser && (
                    <TableCell>
                      <Typography variant="body2">
                        {row.userName ?? "Unknown user"}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {row.userPhone}
                      </Typography>
                    </TableCell>
                  )}
                  <TableCell>{formatDateTime(row.signedInAt)}</TableCell>
                  <TableCell>
                    <Stack
                      direction="row"
                      spacing={1}
                      sx={{ alignItems: "center" }}
                    >
                      <span title={row.deviceDescription ?? undefined}>
                        {describeOrigin(row)}
                      </span>
                      {row.current && (
                        <Chip
                          label="This device"
                          size="small"
                          color="primary"
                        />
                      )}
                    </Stack>
                  </TableCell>
                  {onDelete && (
                    <TableCell align="center">
                      <RowActionButton
                        action="Delete"
                        subject={`session ${number}`}
                        onClick={() => onDelete(row, number)}
                      />
                    </TableCell>
                  )}
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </TableContainer>
    </Paper>
  );
}
