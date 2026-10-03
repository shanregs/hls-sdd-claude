import { useEffect, useState } from "react";
import { DataGrid, type GridColDef } from "@mui/x-data-grid";
import {
  Alert,
  Box,
  Button,
  Chip,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { useGrantedActions } from "../common/useGrantedActions";
import { AssignZonesDialog } from "./AssignZonesDialog";
import { ManagerDialog } from "./ManagerDialog";
import { listManagers, type ManagerSummary } from "./managersApi";

const ROUTE = "/master-data/managers";

/** Manager records (FR-007/FR-008): list, create and assign Zones. School assignment is done from
 * the Schools screen. */
export function ManagersPage() {
  const { authFetch } = useAuth();
  const actions = useGrantedActions(ROUTE);
  const canCreate = actions.has("CREATE");
  const canEdit = actions.has("EDIT");

  const [query, setQuery] = useState("");
  const [paging, setPaging] = useState({ page: 0, pageSize: 25 });
  const [rows, setRows] = useState<ManagerSummary[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadCount, setReloadCount] = useState(0);
  const [creating, setCreating] = useState(false);
  const [zonesFor, setZonesFor] = useState<ManagerSummary | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const result = await listManagers(
        authFetch,
        query,
        paging.page,
        paging.pageSize,
      );
      if (cancelled) return;
      setLoading(false);
      if (result.ok) {
        setError(null);
        setRows(result.data.content);
        setTotal(result.data.totalElements);
      } else {
        setError(result.reason);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch, query, paging, reloadCount]);

  const reload = () => setReloadCount((c) => c + 1);

  const columns: GridColDef<ManagerSummary>[] = [
    { field: "displayName", headerName: "Manager", flex: 1.2, sortable: false },
    { field: "phone", headerName: "Phone", flex: 1, sortable: false },
    {
      field: "zones",
      headerName: "Zones",
      flex: 1.6,
      sortable: false,
      renderCell: (params) => (
        <Stack direction="row" spacing={0.5} sx={{ flexWrap: "wrap" }}>
          {params.row.zones.map((zone) => (
            <Chip key={zone.id} size="small" label={zone.name} />
          ))}
        </Stack>
      ),
    },
    { field: "schoolCount", headerName: "Schools", flex: 0.6, sortable: false },
    {
      field: "teacherCount",
      headerName: "Teachers",
      flex: 0.6,
      sortable: false,
      valueGetter: (_value, row) => row.teacherCount ?? 0,
    },
    {
      field: "active",
      headerName: "Status",
      flex: 0.7,
      sortable: false,
      renderCell: (params) => (
        <Chip
          size="small"
          label={params.row.active ? "Active" : "Inactive"}
          color={params.row.active ? "success" : "default"}
        />
      ),
    },
  ];
  if (canEdit) {
    columns.push({
      field: "actions",
      headerName: "Actions",
      flex: 1,
      sortable: false,
      renderCell: (params) => (
        <Button size="small" onClick={() => setZonesFor(params.row)}>
          Zones
        </Button>
      ),
    });
  }

  return (
    <Box>
      <Stack
        direction="row"
        sx={{ alignItems: "center", justifyContent: "space-between", mb: 2 }}
      >
        <Typography variant="h5" component="h1">
          Managers
        </Typography>
        {canCreate && (
          <Button variant="contained" onClick={() => setCreating(true)}>
            Create manager
          </Button>
        )}
      </Stack>
      <TextField
        label="Search managers"
        size="small"
        sx={{ mb: 2 }}
        value={query}
        onChange={(e) => {
          setPaging((p) => ({ ...p, page: 0 }));
          setQuery(e.target.value);
        }}
      />
      {error && (
        <Alert severity="error" sx={{ mb: 2 }} role="alert">
          {error}
        </Alert>
      )}
      <Box sx={{ height: 560, width: "100%", bgcolor: "background.paper" }}>
        <DataGrid
          rows={rows}
          columns={columns}
          loading={loading}
          disableRowSelectionOnClick
          disableColumnFilter
          paginationMode="server"
          rowCount={total}
          pageSizeOptions={[25, 50, 100]}
          paginationModel={{ page: paging.page, pageSize: paging.pageSize }}
          onPaginationModelChange={(model) => setPaging(model)}
          localeText={{ noRowsLabel: "No managers match your search." }}
        />
      </Box>
      {creating && (
        <ManagerDialog
          onClose={() => setCreating(false)}
          onSaved={() => {
            setCreating(false);
            reload();
          }}
        />
      )}
      {zonesFor && (
        <AssignZonesDialog
          manager={zonesFor}
          onClose={() => setZonesFor(null)}
          onSaved={() => {
            setZonesFor(null);
            reload();
          }}
        />
      )}
    </Box>
  );
}
