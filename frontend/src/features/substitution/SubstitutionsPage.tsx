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
import { Link } from "react-router-dom";
import { useAuth } from "../../auth/useAuth";
import { formatDate } from "../attendance/monthUtils";
import {
  listManagerCovers,
  listRequirements,
  STATUS_LABELS,
  type ManagerCover,
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
  const [day] = useState(() =>
    params.get("date") === "today" ? localIsoDate() : "",
  );
  const [view, setView] = useState<"REQUIREMENTS" | "COVERS">("REQUIREMENTS");
  const [sameDayOnly, setSameDayOnly] = useState(false);
  const [covers, setCovers] = useState<ManagerCover[] | null>(null);
  const [page, setPage] = useState(0);
  const [rows, setRows] = useState<Requirement[] | null>(null);
  const [total, setTotal] = useState(0);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      if (view === "COVERS") {
        const result = await listManagerCovers(authFetch, {
          sameDay: sameDayOnly,
          page,
          size: PAGE_SIZE,
        });
        if (cancelled) return;
        if (result.ok) {
          setCovers(result.data.content);
          setTotal(result.data.totalElements);
          setError(null);
        } else {
          setError(result.reason);
        }
        return;
      }
      const result = await listRequirements(authFetch, {
        status,
        page,
        size: PAGE_SIZE,
        from: day || undefined,
        to: day || undefined,
        sameDay: sameDayOnly,
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
  }, [authFetch, status, page, day, view, sameDayOnly]);

  return (
    <Box>
      <Typography variant="h1" sx={{ fontSize: "1.5rem", mb: 2 }}>
        Substitutions
      </Typography>
      <Stack
        direction="row"
        spacing={2}
        sx={{ flexWrap: "wrap", mb: 2 }}
        useFlexGap
      >
        <TextField
          select
          size="small"
          label="Show"
          value={view}
          onChange={(e) => {
            setView(e.target.value as "REQUIREMENTS" | "COVERS");
            setPage(0);
          }}
          sx={{ minWidth: 200 }}
        >
          <MenuItem value="REQUIREMENTS">Requirements</MenuItem>
          <MenuItem value="COVERS">Manager covers</MenuItem>
        </TextField>
        <TextField
          select
          size="small"
          label="Recorded"
          value={sameDayOnly ? "SAME_DAY" : "ALL"}
          onChange={(e) => {
            setSameDayOnly(e.target.value === "SAME_DAY");
            setPage(0);
          }}
          sx={{ minWidth: 180 }}
        >
          <MenuItem value="ALL">All</MenuItem>
          <MenuItem value="SAME_DAY">Same-day only</MenuItem>
        </TextField>
      </Stack>
      {view === "REQUIREMENTS" && (
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
      )}
      {error && <Alert severity="error">{error}</Alert>}
      {view === "COVERS" && covers && covers.length === 0 && !error && (
        <Alert severity="info">No Manager covers.</Alert>
      )}
      {view === "COVERS" && covers && covers.length > 0 && (
        <TableContainer component={Paper}>
          <Table size="small" aria-label="Manager covers">
            <TableHead>
              <TableRow>
                <TableCell>Date</TableCell>
                <TableCell>School</TableCell>
                <TableCell>Covered for</TableCell>
                <TableCell>Manager</TableCell>
                <TableCell>Day</TableCell>
                <TableCell>State</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {covers.map((c) => (
                <TableRow key={c.requirementId}>
                  <TableCell>
                    <MuiLink
                      component={Link}
                      to={`/operations/substitutions/${c.requirementId}`}
                    >
                      {formatDate(c.date)}
                    </MuiLink>
                  </TableCell>
                  <TableCell>{c.schoolName}</TableCell>
                  <TableCell>{c.replacedTeacherName}</TableCell>
                  <TableCell>
                    {c.managerName ?? c.recordedByName ?? "-"}
                  </TableCell>
                  <TableCell>
                    {c.dayValue === 0.5 ? "Half day" : "Whole day"}
                  </TableCell>
                  <TableCell>
                    <Chip size="small" variant="outlined" label={c.label} />
                    {c.sameDay && (
                      <Chip
                        size="small"
                        variant="outlined"
                        label="Same-day"
                        sx={{ ml: 0.5 }}
                      />
                    )}
                    {c.state === "ENDED" && (
                      <Chip
                        size="small"
                        variant="outlined"
                        label="Ended"
                        sx={{ ml: 0.5 }}
                      />
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
      {view === "REQUIREMENTS" && rows && rows.length === 0 && !error && (
        <Alert severity="info">No substitution requirements.</Alert>
      )}
      {view === "REQUIREMENTS" && rows && rows.length > 0 && (
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
                    <MuiLink
                      component={Link}
                      to={`/operations/substitutions/${r.id}`}
                    >
                      {formatDate(r.dutyDate)}
                    </MuiLink>
                  </TableCell>
                  <TableCell>{r.schoolName}</TableCell>
                  <TableCell>{r.teacherName}</TableCell>
                  <TableCell>
                    {r.dayValue === 0.5 ? "Half day" : "Whole day"}
                  </TableCell>
                  <TableCell>
                    <Chip
                      size="small"
                      variant="outlined"
                      label={STATUS_LABELS[r.status]}
                    />
                    {r.sameDay && (
                      <Chip
                        size="small"
                        variant="outlined"
                        label="Same-day"
                        sx={{ ml: 0.5 }}
                      />
                    )}
                  </TableCell>
                  <TableCell>
                    {r.substituteName ?? r.coverName ?? "-"}
                  </TableCell>
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
