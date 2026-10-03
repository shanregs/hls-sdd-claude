import { useEffect, useState } from "react";
import { DataGrid, type GridColDef } from "@mui/x-data-grid";
import { Alert, Box, Typography } from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { AuditFilterBar, type AuditFilters } from "./AuditFilterBar";
import { useAuditExport } from "./useAuditExport";
import { dateRangeToInstants } from "./dateRangeToInstants";

interface LoginHistoryRow {
  id: string;
  occurredAt: string;
  userId: string | null;
  phoneMasked: string;
  method: string;
  eventType: string;
  outcome: string;
}

const columns: GridColDef<LoginHistoryRow>[] = [
  { field: "occurredAt", headerName: "Occurred At", flex: 1.2 },
  { field: "phoneMasked", headerName: "Phone", flex: 1 },
  { field: "method", headerName: "Method", flex: 0.8 },
  { field: "eventType", headerName: "Event Type", flex: 1 },
  { field: "outcome", headerName: "Outcome", flex: 1 },
];

/** User Story 1: search/filter/export Login History (FR-001/FR-005/FR-009). */
export function LoginHistoryPage() {
  const { authFetch } = useAuth();
  const exportCsv = useAuditExport();
  const [filters, setFilters] = useState<AuditFilters>({
    from: "",
    to: "",
    userId: "",
  });
  const [rows, setRows] = useState<LoginHistoryRow[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const { from, to } = dateRangeToInstants(filters.from, filters.to);
        const query = new URLSearchParams();
        if (from) query.set("from", from);
        if (to) query.set("to", to);
        if (filters.userId) query.set("userId", filters.userId);
        const response = await authFetch(
          `/api/v1/audit/login-history?${query.toString()}`,
        );
        if (!response.ok) {
          throw new Error("Could not load Login History.");
        }
        const data = (await response.json()) as { content: LoginHistoryRow[] };
        if (!cancelled) {
          setRows(
            data.content.map((row) => ({
              ...row,
              id: `${row.occurredAt}-${row.phoneMasked}-${row.method}`,
            })),
          );
          setError(null);
        }
      } catch {
        if (!cancelled) {
          setError("Could not load Login History.");
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch, filters]);

  const handleExport = () => {
    const { from, to } = dateRangeToInstants(filters.from, filters.to);
    exportCsv("/api/v1/audit/login-history/export", {
      ...filters,
      from,
      to,
    }).catch(() => {
      setError("Could not export Login History.");
    });
  };

  return (
    <Box>
      <Typography variant="h5" component="h1" gutterBottom>
        Login History
      </Typography>
      <AuditFilterBar
        filters={filters}
        onChange={setFilters}
        onExport={handleExport}
      />
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
