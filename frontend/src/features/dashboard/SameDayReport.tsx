import { useEffect, useState } from "react";
import { Link as RouterLink } from "react-router-dom";
import {
  Alert,
  Box,
  Link,
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
import { useAuth } from "../../auth/useAuth";
import {
  getJson,
  type ApiResult,
  type AuthFetch,
} from "../common/masterDataApi";

export interface SameDayRow {
  id: string;
  name: string;
  count: number;
}

export interface SameDaySection {
  byManager: SameDayRow[];
  bySchool: SameDayRow[];
}

export type SameDayReportData = Record<string, SameDaySection>;

export function getSameDayReport(
  authFetch: AuthFetch,
  month: string,
): Promise<ApiResult<SameDayReportData>> {
  return getJson(
    authFetch,
    `/api/v1/reports/same-day?month=${encodeURIComponent(month)}`,
    "Could not load the same-day report.",
  );
}

const TITLES: Record<string, string> = {
  leave: "Leave days recorded for the day",
  covers: "Manager cover days",
  tasks: "Tasks logged for the day",
  visits: "Visits logged for the day",
};

function currentMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
}

function CountTable({ label, rows }: { label: string; rows: SameDayRow[] }) {
  return (
    <TableContainer component={Paper} variant="outlined" sx={{ mb: 2 }}>
      <Table size="small" aria-label={label}>
        <TableHead>
          <TableRow>
            <TableCell>{label}</TableCell>
            <TableCell align="right">Count</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.length === 0 ? (
            <TableRow>
              <TableCell colSpan={2}>None this month.</TableCell>
            </TableRow>
          ) : (
            rows.map((r) => (
              <TableRow key={r.id}>
                <TableCell>{r.name}</TableCell>
                <TableCell align="right">{r.count}</TableCell>
              </TableRow>
            ))
          )}
        </TableBody>
      </Table>
    </TableContainer>
  );
}

/**
 * What was recorded for the day without prior approval or planning (spec 040 US4): leave, Manager covers, tasks and visits,
 * counted per Manager and per School for a month. There is nothing to approve here; it is a record. The server leaves out
 * a section the caller may not view.
 */
export function SameDayReport() {
  const { authFetch } = useAuth();
  const [month, setMonth] = useState(currentMonth());
  const [data, setData] = useState<SameDayReportData | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      const result = await getSameDayReport(authFetch, month);
      if (cancelled) return;
      if (result.ok) {
        setData(result.data);
        setError(null);
      } else {
        setData(null);
        setError(result.reason);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch, month]);

  return (
    <Box>
      <Link component={RouterLink} to="/dashboard">
        Back to the Dashboard
      </Link>
      <Typography variant="h5" component="h1" sx={{ my: 1 }}>
        Same-day report
      </Typography>
      <Typography color="text.secondary" sx={{ mb: 2 }}>
        Leave, covers, tasks and visits recorded for the day without prior
        approval. Nothing here waits for a decision.
      </Typography>
      <TextField
        type="month"
        size="small"
        label="Month"
        value={month}
        onChange={(e) => e.target.value && setMonth(e.target.value)}
        slotProps={{ inputLabel: { shrink: true } }}
        sx={{ mb: 2 }}
      />
      {error && (
        <Alert severity="error" role="alert">
          {error}
        </Alert>
      )}
      {data === null && !error && <Typography>Loading…</Typography>}
      {data &&
        Object.entries(data).map(([key, section]) => (
          <Box component="section" key={key} aria-label={TITLES[key] ?? key}>
            <Typography variant="h6" component="h2" gutterBottom>
              {TITLES[key] ?? key}
            </Typography>
            <CountTable label="Manager" rows={section.byManager} />
            <CountTable label="School" rows={section.bySchool} />
          </Box>
        ))}
    </Box>
  );
}
