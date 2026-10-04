import { useEffect, useState } from "react";
import { DataGrid, type GridColDef } from "@mui/x-data-grid";
import {
  Alert,
  Box,
  Checkbox,
  ListItemText,
  MenuItem,
  Select,
  Typography,
  type SelectChangeEvent,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { AuditFilterBar, type AuditFilters } from "./AuditFilterBar";
import { useAuditExport } from "./useAuditExport";
import { dateRangeToInstants } from "./dateRangeToInstants";
import { formatLocation, sourceLabel, type OriginFields } from "./origin";

interface AuditLogRow extends OriginFields {
  id: string;
  occurredAt: string;
  type: "LOGIN" | "CHANGE" | "ACTIVITY";
  actorUserId: string | null;
  summary: string;
}

const ALL_TYPES = ["LOGIN", "CHANGE", "ACTIVITY"] as const;

const TYPE_LABELS: Record<string, string> = {
  LOGIN: "Login",
  CHANGE: "Change",
  ACTIVITY: "Activity",
};

const columns: GridColDef<AuditLogRow>[] = [
  { field: "occurredAt", headerName: "Occurred At", flex: 1.2 },
  {
    field: "type",
    headerName: "Type",
    flex: 0.6,
    valueGetter: (_value, row) => TYPE_LABELS[row.type] ?? row.type,
  },
  { field: "actorUserId", headerName: "Actor", flex: 1 },
  { field: "summary", headerName: "Summary", flex: 2 },
  {
    field: "source",
    headerName: "Source",
    flex: 0.7,
    valueGetter: (_value, row) => sourceLabel(row.source),
  },
  { field: "appVersion", headerName: "App version", flex: 0.6 },
  {
    field: "location",
    headerName: "Location",
    flex: 1.5,
    valueGetter: (_value, row) => formatLocation(row.location),
  },
];

function buildQuery(filters: AuditFilters, types: string[]): URLSearchParams {
  const { from, to } = dateRangeToInstants(filters.from, filters.to);
  const query = new URLSearchParams();
  if (from) query.set("from", from);
  if (to) query.set("to", to);
  if (filters.userId) query.set("userId", filters.userId);
  if (types.length > 0 && types.length < ALL_TYPES.length) {
    for (const t of types) {
      query.append("type", t);
    }
  }
  return query;
}

/** User Story 4: the unified, searchable, exportable Audit Logs feed (FR-008/FR-009). */
export function AuditLogsPage() {
  const { authFetch } = useAuth();
  const exportCsv = useAuditExport();
  const [filters, setFilters] = useState<AuditFilters>({
    from: "",
    to: "",
    userId: "",
  });
  const [types, setTypes] = useState<string[]>([]);
  const [rows, setRows] = useState<AuditLogRow[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const query = buildQuery(filters, types);
        const response = await authFetch(
          `/api/v1/audit/logs?${query.toString()}`,
        );
        if (!response.ok) {
          throw new Error("Could not load Audit Logs.");
        }
        const data = (await response.json()) as { content: AuditLogRow[] };
        if (!cancelled) {
          setRows(
            data.content.map((row, index) => ({
              ...row,
              id: `${row.occurredAt}-${row.type}-${index}`,
            })),
          );
          setError(null);
        }
      } catch {
        if (!cancelled) {
          setError("Could not load Audit Logs.");
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch, filters, types]);

  const handleExport = () => {
    const query = buildQuery(filters, types);
    const params: Record<string, string | undefined> = {};
    for (const [key, value] of query.entries()) {
      params[key] = value;
    }
    const { from, to } = dateRangeToInstants(filters.from, filters.to);
    exportCsv("/api/v1/audit/logs/export", { ...params, from, to }).catch(
      () => {
        setError("Could not export Audit Logs.");
      },
    );
  };

  const handleTypeChange = (event: SelectChangeEvent<string[]>) => {
    const value = event.target.value;
    setTypes(typeof value === "string" ? value.split(",") : value);
  };

  return (
    <Box>
      <Typography variant="h5" component="h1" gutterBottom>
        Audit Logs
      </Typography>
      <AuditFilterBar
        filters={filters}
        onChange={setFilters}
        onExport={handleExport}
      >
        <Select
          multiple
          displayEmpty
          size="small"
          value={types}
          onChange={handleTypeChange}
          renderValue={(selected) =>
            selected.length === 0
              ? "All types"
              : selected.map((t) => TYPE_LABELS[t] ?? t).join(", ")
          }
          sx={{ minWidth: 160 }}
          inputProps={{ "aria-label": "Filter by type" }}
        >
          {ALL_TYPES.map((type) => (
            <MenuItem key={type} value={type}>
              <Checkbox checked={types.includes(type)} />
              <ListItemText primary={TYPE_LABELS[type]} />
            </MenuItem>
          ))}
        </Select>
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
