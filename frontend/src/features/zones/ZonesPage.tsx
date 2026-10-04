import { useEffect, useState } from "react";
import { DataGrid, type GridColDef } from "@mui/x-data-grid";
import {
  Alert,
  Box,
  Button,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { useGrantedActions } from "../common/useGrantedActions";
import { BulkImportPlacesDialog } from "./BulkImportPlacesDialog";
import { PlaceLookup } from "./PlaceLookup";
import { PlacesPanel } from "./PlacesPanel";
import { ZoneDialog } from "./ZoneDialog";
import { deleteZone, listZones, type ZoneSummary } from "./zonesApi";
import { RowActionButton } from "../common/RowActionButton";

const ROUTE = "/master-data/zones";

/** Zone management (FR-001/FR-002): list, create, rename, delete, and the Places of a Zone.
 * Controls show only when the access model grants the matching action (FR-027). */
export function ZonesPage() {
  const { authFetch } = useAuth();
  const actions = useGrantedActions(ROUTE);
  const canCreate = actions.has("CREATE");
  const canEdit = actions.has("EDIT");
  const canDelete = actions.has("DELETE");

  const [query, setQuery] = useState("");
  const [paging, setPaging] = useState({ page: 0, pageSize: 25 });
  const [rows, setRows] = useState<ZoneSummary[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [reloadCount, setReloadCount] = useState(0);
  const [creating, setCreating] = useState(false);
  const [renaming, setRenaming] = useState<ZoneSummary | null>(null);
  const [selected, setSelected] = useState<ZoneSummary | null>(null);
  const [importing, setImporting] = useState(false);
  const [placesReload, setPlacesReload] = useState(0);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const result = await listZones(
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

  const remove = async (zone: ZoneSummary) => {
    setActionError(null);
    const result = await deleteZone(authFetch, zone.id);
    if (!result.ok) {
      setActionError(result.reason);
      return;
    }
    if (selected?.id === zone.id) setSelected(null);
    reload();
  };

  const columns: GridColDef<ZoneSummary>[] = [
    { field: "name", headerName: "Zone", flex: 1.4, sortable: false },
    { field: "placeCount", headerName: "Places", flex: 0.6, sortable: false },
    { field: "schoolCount", headerName: "Schools", flex: 0.6, sortable: false },
    {
      field: "managerCount",
      headerName: "Managers",
      flex: 0.6,
      sortable: false,
      valueGetter: (_value, row) => row.managerCount ?? 0,
    },
    {
      field: "actions",
      headerName: "Actions",
      flex: 1.8,
      sortable: false,
      renderCell: (params) => (
        <Stack direction="row" spacing={1}>
          <RowActionButton
            action="View"
            subject={params.row.name}
            onClick={() => setSelected(params.row)}
          />
          {canEdit && (
            <RowActionButton
              action="Edit"
              subject={params.row.name}
              onClick={() => setRenaming(params.row)}
            />
          )}
          {canDelete && (
            <RowActionButton
              action="Delete"
              subject={params.row.name}
              onClick={() => remove(params.row)}
            />
          )}
        </Stack>
      ),
    },
  ];

  return (
    <Box>
      <Stack
        direction="row"
        sx={{ alignItems: "center", justifyContent: "space-between", mb: 2 }}
      >
        <Typography variant="h5" component="h1">
          Zones
        </Typography>
        {canCreate && (
          <Button variant="contained" onClick={() => setCreating(true)}>
            Create zone
          </Button>
        )}
      </Stack>

      <PlaceLookup />

      <TextField
        label="Search zones"
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
      {actionError && (
        <Alert severity="error" sx={{ mb: 2 }} role="alert">
          {actionError}
        </Alert>
      )}
      <Box sx={{ height: 420, width: "100%", bgcolor: "background.paper" }}>
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
          localeText={{ noRowsLabel: "No zones match your search." }}
        />
      </Box>

      {selected && (
        <PlacesPanel
          zone={selected}
          canEdit={canEdit}
          reloadKey={placesReload}
          onChanged={reload}
          toolbar={
            canCreate ? (
              <Button variant="outlined" onClick={() => setImporting(true)}>
                Import places
              </Button>
            ) : undefined
          }
        />
      )}
      {selected && importing && (
        <BulkImportPlacesDialog
          zone={selected}
          onClose={() => setImporting(false)}
          onDone={() => {
            setImporting(false);
            setPlacesReload((c) => c + 1);
            reload();
          }}
        />
      )}
      {creating && (
        <ZoneDialog
          onClose={() => setCreating(false)}
          onSaved={() => {
            setCreating(false);
            reload();
          }}
        />
      )}
      {renaming && (
        <ZoneDialog
          zone={renaming}
          onClose={() => setRenaming(null)}
          onSaved={() => {
            setRenaming(null);
            reload();
          }}
        />
      )}
    </Box>
  );
}
