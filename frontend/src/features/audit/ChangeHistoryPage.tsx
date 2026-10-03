import { useEffect, useState } from "react";
import { DataGrid, type GridColDef } from "@mui/x-data-grid";
import { Alert, Box, Typography } from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { AuditFilterBar, type AuditFilters } from "./AuditFilterBar";
import { useAuditExport } from "./useAuditExport";
import { dateRangeToInstants } from "./dateRangeToInstants";

interface ChangeHistoryRow {
  id: string;
  occurredAt: string;
  actorUserId: string;
  entityType: string;
  entityId: string;
  field: string;
  beforeValue: string | null;
  afterValue: string | null;
}

const columns: GridColDef<ChangeHistoryRow>[] = [
  { field: "occurredAt", headerName: "Occurred At", flex: 1.2 },
  { field: "entityId", headerName: "Entity", flex: 1.4 },
  { field: "field", headerName: "Field", flex: 0.8 },
  { field: "beforeValue", headerName: "Before", flex: 0.8 },
  { field: "afterValue", headerName: "After", flex: 0.8 },
];

/** User Story 2: search/filter/export Change History (FR-002/FR-006/FR-009). */
export function ChangeHistoryPage() {
  const { authFetch } = useAuth();
  const exportCsv = useAuditExport();
  const [filters, setFilters] = useState<AuditFilters>({
    from: "",
    to: "",
    userId: "",
  });
  const [rows, setRows] = useState<ChangeHistoryRow[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const { from, to } = dateRangeToInstants(filters.from, filters.to);
        const query = new URLSearchParams();
        if (from) query.set("from", from);
        if (to) query.set("to", to);
        if (filters.userId) query.set("actorUserId", filters.userId);
        const response = await authFetch(
          `/api/v1/audit/change-history?${query.toString()}`,
        );
        if (!response.ok) {
          throw new Error("Could not load Change History.");
        }
        const data = (await response.json()) as { content: ChangeHistoryRow[] };
        if (!cancelled) {
          setRows(
            data.content.map((row) => ({
              ...row,
              id: `${row.occurredAt}-${row.entityId}`,
            })),
          );
          setError(null);
        }
      } catch {
        if (!cancelled) {
          setError("Could not load Change History.");
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch, filters]);

  const handleExport = () => {
    const { from, to } = dateRangeToInstants(filters.from, filters.to);
    exportCsv("/api/v1/audit/change-history/export", {
      actorUserId: filters.userId,
      from,
      to,
    }).catch(() => {
      setError("Could not export Change History.");
    });
  };

  return (
    <Box>
      <Typography variant="h5" component="h1" gutterBottom>
        Change History
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
