import { useEffect, useState } from "react";
import { DataGrid, type GridColDef } from "@mui/x-data-grid";
import { Alert, Box, Button, Typography } from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { EditGrantDialog } from "./EditGrantDialog";
import type { MatrixEntry, MatrixRow } from "./types";

/** Lists every matrix entry (FR-002/FR-005), gated to ADMIN/DIRECTOR/SYSTEM by the server
 * (`RouteGuard` also keeps Manager/Teacher from ever reaching this route). Uses MUI X DataGrid
 * Community for sorting/filtering/pagination (research.md §3); edits go through
 * {@link EditGrantDialog} rather than the grid's own inline editing. */
export function RolePermissionsGrid() {
  const { authFetch } = useAuth();
  const [rows, setRows] = useState<MatrixRow[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState<MatrixRow | null>(null);
  const [reloadCount, setReloadCount] = useState(0);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const response = await authFetch("/api/v1/identity/permission-matrix");
        if (!response.ok) {
          throw new Error("Could not load the permission matrix.");
        }
        const data = (await response.json()) as { entries: MatrixEntry[] };
        if (!cancelled) {
          setRows(
            data.entries.map((entry) => ({
              id: `${entry.role}|${entry.module}|${entry.action}`,
              ...entry,
            })),
          );
        }
      } catch {
        if (!cancelled) {
          setError("Could not load the permission matrix.");
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch, reloadCount]);

  const columns: GridColDef<MatrixRow>[] = [
    { field: "role", headerName: "Role", flex: 1 },
    { field: "module", headerName: "Module", flex: 1.4 },
    { field: "action", headerName: "Action", flex: 1 },
    {
      field: "granted",
      headerName: "Granted",
      flex: 1,
      sortable: false,
      renderCell: (params) => (
        <Button
          size="small"
          variant={params.row.granted ? "contained" : "outlined"}
          color={params.row.granted ? "success" : "inherit"}
          onClick={() => setEditing(params.row)}
        >
          {params.row.granted ? "Granted" : "Not granted"}
        </Button>
      ),
    },
  ];

  return (
    <Box>
      <Typography variant="h5" component="h1" gutterBottom>
        Role &amp; Permissions
      </Typography>
      {error && (
        <Alert severity="error" sx={{ mb: 2 }} role="alert">
          {error}
        </Alert>
      )}
      <Box sx={{ height: 560, width: "100%", bgcolor: "background.paper" }}>
        <DataGrid rows={rows} columns={columns} disableRowSelectionOnClick />
      </Box>
      {editing && (
        <EditGrantDialog
          row={editing}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            setReloadCount((count) => count + 1);
          }}
        />
      )}
    </Box>
  );
}
