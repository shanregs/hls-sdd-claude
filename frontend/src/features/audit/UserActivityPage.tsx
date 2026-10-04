import { useEffect, useState } from "react";
import { DataGrid, type GridColDef } from "@mui/x-data-grid";
import { Alert, Box, Typography } from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { AuditFilterBar, type AuditFilters } from "./AuditFilterBar";
import { useAuditExport } from "./useAuditExport";
import { dateRangeToInstants } from "./dateRangeToInstants";

interface UserActivityRow {
  id: string;
  occurredAt: string;
  actorUserId: string | null;
  affectedUserId: string;
  action: string;
  detail: string | null;
}

const ACTION_LABELS: Record<string, string> = {
  PASSWORD_RESET_REQUESTED: "Password reset requested",
  PASSWORD_RESET_COMPLETED: "Password reset completed",
  PASSWORD_CHANGED: "Password changed",
  PROFILE_UPDATED: "Profile updated",
  SESSION_ENDED: "Session ended",
  ACCOUNT_LOCKED: "Account locked",
  ACCOUNT_UNLOCKED: "Account unlocked",
  ACCOUNT_DEACTIVATED: "Account deactivated",
  ACCOUNT_REACTIVATED: "Account reactivated",
};

const columns: GridColDef<UserActivityRow>[] = [
  { field: "occurredAt", headerName: "Occurred At", flex: 1.2 },
  { field: "affectedUserId", headerName: "Affected User", flex: 1.2 },
  {
    field: "action",
    headerName: "Action",
    flex: 1,
    valueGetter: (_value, row) => ACTION_LABELS[row.action] ?? row.action,
  },
  { field: "detail", headerName: "Detail", flex: 1 },
];

/** User Story 3: search/filter/export User Activity (FR-003/FR-007/FR-009). */
export function UserActivityPage() {
  const { authFetch } = useAuth();
  const exportCsv = useAuditExport();
  const [filters, setFilters] = useState<AuditFilters>({
    from: "",
    to: "",
    userId: "",
  });
  const [rows, setRows] = useState<UserActivityRow[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const { from, to } = dateRangeToInstants(filters.from, filters.to);
        const query = new URLSearchParams();
        if (from) query.set("from", from);
        if (to) query.set("to", to);
        if (filters.userId) query.set("affectedUserId", filters.userId);
        const response = await authFetch(
          `/api/v1/audit/user-activity?${query.toString()}`,
        );
        if (!response.ok) {
          throw new Error("Could not load User Activity.");
        }
        const data = (await response.json()) as { content: UserActivityRow[] };
        if (!cancelled) {
          setRows(
            data.content.map((row) => ({
              ...row,
              id: `${row.occurredAt}-${row.affectedUserId}-${row.action}`,
            })),
          );
          setError(null);
        }
      } catch {
        if (!cancelled) {
          setError("Could not load User Activity.");
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch, filters]);

  const handleExport = () => {
    const { from, to } = dateRangeToInstants(filters.from, filters.to);
    exportCsv("/api/v1/audit/user-activity/export", {
      affectedUserId: filters.userId,
      from,
      to,
    }).catch(() => {
      setError("Could not export User Activity.");
    });
  };

  return (
    <Box>
      <Typography variant="h5" component="h1" gutterBottom>
        User Activity
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
