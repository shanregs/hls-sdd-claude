import { useEffect, useState } from "react";
import { Link as RouterLink } from "react-router-dom";
import {
  Alert,
  Box,
  Link,
  MenuItem,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Stack,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import {
  getJson,
  type ApiResult,
  type AuthFetch,
} from "../common/masterDataApi";
import { getMenu, type Named } from "../reports/reportsApi";

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
  zoneId?: string,
): Promise<ApiResult<SameDayReportData>> {
  const zone = zoneId ? `&zoneId=${encodeURIComponent(zoneId)}` : "";
  return getJson(
    authFetch,
    `/api/v1/reports/same-day?month=${encodeURIComponent(month)}${zone}`,
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
  const [zoneId, setZoneId] = useState("");
  const [zones, setZones] = useState<Named[]>([]);
  const [data, setData] = useState<SameDayReportData | null>(null);
  const [error, setError] = useState<string | null>(null);

  // the Zones the caller may filter by (the same list the other reports offer); no list means no filter
  useEffect(() => {
    let cancelled = false;
    void (async () => {
      const menu = await getMenu(authFetch);
      if (!cancelled && menu.ok) setZones(menu.data.zones);
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch]);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      const result = await getSameDayReport(
        authFetch,
        month,
        zoneId || undefined,
      );
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
  }, [authFetch, month, zoneId]);

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
      <Stack
        direction="row"
        spacing={2}
        sx={{ mb: 2, flexWrap: "wrap", rowGap: 1 }}
      >
        <TextField
          type="month"
          size="small"
          label="Month"
          value={month}
          onChange={(e) => e.target.value && setMonth(e.target.value)}
          slotProps={{ inputLabel: { shrink: true } }}
        />
        {zones.length > 0 && (
          <TextField
            select
            size="small"
            label="Zone"
            value={zoneId}
            onChange={(e) => setZoneId(e.target.value)}
            sx={{ minWidth: 200 }}
          >
            <MenuItem value="">All Zones</MenuItem>
            {zones.map((z) => (
              <MenuItem key={z.id} value={z.id}>
                {z.name}
              </MenuItem>
            ))}
          </TextField>
        )}
      </Stack>
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
