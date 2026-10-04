import { useEffect, useState } from "react";
import { DataGrid, type GridColDef } from "@mui/x-data-grid";
import { Alert, Box, MenuItem, TextField, Typography } from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { AuditFilterBar, type AuditFilters } from "./AuditFilterBar";
import { useAuditExport } from "./useAuditExport";
import { dateRangeToInstants } from "./dateRangeToInstants";
import { formatLocation, sourceLabel, type OriginFields } from "./origin";

interface ApiAccessRow extends OriginFields {
  id: string;
  occurredAt: string;
  userId: string | null;
  sessionId: string | null;
  httpMethod: string;
  routeTemplate: string;
  statusCode: number;
}

const METHODS = ["GET", "POST", "PUT", "DELETE"] as const;

const LOCATION_STATUSES: Array<[string, string]> = [
  ["AVAILABLE", "Location available"],
  ["PERMISSION_DENIED", "Permission denied"],
  ["SERVICES_OFF", "Location services off"],
  ["NO_FIX", "No position found"],
  ["INVALID", "Invalid location"],
  ["OTHER", "Not provided"],
];

const columns: GridColDef<ApiAccessRow>[] = [
  { field: "occurredAt", headerName: "Occurred At", flex: 1.2 },
  { field: "userId", headerName: "User", flex: 1.1 },
  { field: "httpMethod", headerName: "Method", flex: 0.6 },
  { field: "routeTemplate", headerName: "Route", flex: 1.6 },
  { field: "statusCode", headerName: "Status", flex: 0.5 },
  {
    field: "source",
    headerName: "Source",
    flex: 0.8,
    valueGetter: (_value, row) => sourceLabel(row.source),
  },
  { field: "appVersion", headerName: "App version", flex: 0.7 },
  {
    field: "location",
    headerName: "Location",
    flex: 1.6,
    valueGetter: (_value, row) => formatLocation(row.location),
  },
];

/**
 * AUDIT → API Access (spec 018 FR-023a, FR-028): every request the Android app made, with where it
 * came from. Shown to Admin and System only (the server enforces this; the route is also absent
 * from everyone else's navigation). The rows hold no request content, only method, route pattern,
 * status and origin.
 */
export function ApiAccessPage() {
  const { authFetch } = useAuth();
  const exportCsv = useAuditExport();
  const [filters, setFilters] = useState<AuditFilters>({
    from: "",
    to: "",
    userId: "",
  });
  const [httpMethod, setHttpMethod] = useState("");
  const [locationStatus, setLocationStatus] = useState("");
  const [rows, setRows] = useState<ApiAccessRow[]>([]);
  const [error, setError] = useState<string | null>(null);

  const buildParams = () => {
    const { from, to } = dateRangeToInstants(filters.from, filters.to);
    return { from, to, userId: filters.userId, httpMethod, locationStatus };
  };

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const query = new URLSearchParams();
        for (const [key, value] of Object.entries(buildParams())) {
          if (value) query.set(key, value);
        }
        const response = await authFetch(
          `/api/v1/audit/api-access?${query.toString()}`,
        );
        if (!response.ok) {
          throw new Error("Could not load API Access.");
        }
        const data = (await response.json()) as { content: ApiAccessRow[] };
        if (!cancelled) {
          setRows(data.content);
          setError(null);
        }
      } catch {
        if (!cancelled) {
          setError("Could not load API Access.");
        }
      }
    })();
    return () => {
      cancelled = true;
    };
    // buildParams only reads the three pieces of state listed here.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [authFetch, filters, httpMethod, locationStatus]);

  const handleExport = () => {
    exportCsv("/api/v1/audit/api-access/export", buildParams()).catch(() => {
      setError("Could not export API Access.");
    });
  };

  return (
    <Box>
      <Typography variant="h5" component="h1" gutterBottom>
        API Access
      </Typography>
      <AuditFilterBar
        filters={filters}
        onChange={setFilters}
        onExport={handleExport}
      >
        <TextField
          select
          label="Method"
          size="small"
          value={httpMethod}
          onChange={(e) => setHttpMethod(e.target.value)}
          sx={{ minWidth: 120 }}
        >
          <MenuItem value="">All methods</MenuItem>
          {METHODS.map((m) => (
            <MenuItem key={m} value={m}>
              {m}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          select
          label="Location"
          size="small"
          value={locationStatus}
          onChange={(e) => setLocationStatus(e.target.value)}
          sx={{ minWidth: 200 }}
        >
          <MenuItem value="">Any location status</MenuItem>
          {LOCATION_STATUSES.map(([value, label]) => (
            <MenuItem key={value} value={value}>
              {label}
            </MenuItem>
          ))}
        </TextField>
      </AuditFilterBar>
      {error && (
        <Alert severity="error" sx={{ mb: 2 }} role="alert">
          {error}
        </Alert>
      )}
      <Box sx={{ height: 560, width: "100%", bgcolor: "background.paper" }}>
        <DataGrid rows={rows} columns={columns} disableRowSelectionOnClick />
      </Box>
    </Box>
  );
}
