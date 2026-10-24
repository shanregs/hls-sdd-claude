import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import {
  Alert,
  Box,
  Chip,
  Link as MuiLink,
  MenuItem,
  Pagination,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import { Link } from "react-router-dom";
import { useAuth } from "../../auth/useAuth";
import { formatDate } from "../attendance/monthUtils";
import {
  listRequirements,
  STATUS_LABELS,
  type Requirement,
} from "./substitutionApi";

const PAGE_SIZE = 25;

function localIsoDate(): string {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const date = String(now.getDate()).padStart(2, "0");
  return `${now.getFullYear()}-${month}-${date}`;
}

/** OPERATIONS -> Substitutions (spec 017a): the requirements in the caller's scope. */
export function SubstitutionsPage() {
  const { authFetch } = useAuth();
  // The dashboard opens this list with ?date=today&status=... (spec 028).
  const [params] = useSearchParams();
  const [status, setStatus] = useState(() => params.get("status") ?? "");
  const [day] = useState(() => (params.get("date") === "today" ? localIsoDate() : ""));
  const [page, setPage] = useState(0);
  const [rows, setRows] = useState<Requirement[] | null>(null);
  const [total, setTotal] = useState(0);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const result = await listRequirements(authFetch, {
        status,
        page,
        size: PAGE_SIZE,
        from: day || undefined,
        to: day || undefined,
      });
      if (cancelled) return;
      if (result.ok) {
        setRows(result.data.content);
        setTotal(result.data.totalElements);
        setError(null);
      } else {
        setError(result.reason);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch, status, page, day]);

  return (
    <Box>
      <Typography variant="h1" sx={{ fontSize: "1.5rem", mb: 2 }}>
        Substitutions
      </Typography>
      <TextField
        select
        size="small"
        label="Status"
        value={status}
        onChange={(e) => {
          setStatus(e.target.value);
          setPage(0);
        }}
        sx={{ minWidth: 220, mb: 2 }}
      >
        <MenuItem value="">Open and assigned</MenuItem>
        <MenuItem value="OPEN">Open</MenuItem>
        <MenuItem value="ASSIGNED">Assigned</MenuItem>
        <MenuItem value="MANAGER_COVER">Covered by Manager</MenuItem>
        <MenuItem value="CANCELLED">Cancelled</MenuItem>
        <MenuItem value="ALL">All</MenuItem>
      </TextField>
      {error && <Alert severity="error">{error}</Alert>}
      {rows && rows.length === 0 && !error && (
        <Alert severity="info">No substitution requirements.</Alert>
      )}
      {rows && rows.length > 0 && (
        <TableContainer component={Paper}>
          <Table size="small" aria-label="Substitution requirements">
            <TableHead>
              <TableRow>
                <TableCell>Date</TableCell>
                <TableCell>School</TableCell>
                <TableCell>Absent Teacher</TableCell>
                <TableCell>Day</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Substitute</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((r) => (
                <TableRow key={r.id}>
                  <TableCell>
                    <MuiLink component={Link} to={`/operations/substitutions/${r.id}`}>
                      {formatDate(r.dutyDate)}
                    </MuiLink>
                  </TableCell>
                  <TableCell>{r.schoolName}</TableCell>
                  <TableCell>{r.teacherName}</TableCell>
                  <TableCell>{r.dayValue === 0.5 ? "Half day" : "Whole day"}</TableCell>
                  <TableCell>
                    <Chip size="small" variant="outlined" label={STATUS_LABELS[r.status]} />
                  </TableCell>
                  <TableCell>{r.substituteName ?? r.coverName ?? "-"}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
      {total > PAGE_SIZE && (
        <Pagination
          sx={{ mt: 2 }}
          count={Math.ceil(total / PAGE_SIZE)}
          page={page + 1}
          onChange={(_, p) => setPage(p - 1)}
        />
      )}
    </Box>
  );
}
