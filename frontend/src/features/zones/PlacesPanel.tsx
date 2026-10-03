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
import { PlaceDialog } from "./PlaceDialog";
import {
  deletePlace,
  listPlaces,
  type PlaceSummary,
  type ZoneSummary,
} from "./zonesApi";

interface PlacesPanelProps {
  zone: ZoneSummary;
  canEdit: boolean;
  /** Extra toolbar content (for example the bulk-import button). */
  toolbar?: React.ReactNode;
  reloadKey?: number;
  onChanged: () => void;
}

/** Places of the selected Zone (FR-002): searchable, paginated, add/edit/delete when permitted. */
export function PlacesPanel({
  zone,
  canEdit,
  toolbar,
  reloadKey = 0,
  onChanged,
}: PlacesPanelProps) {
  const { authFetch } = useAuth();
  const [query, setQuery] = useState("");
  const [paging, setPaging] = useState({ page: 0, pageSize: 25 });
  const [rows, setRows] = useState<PlaceSummary[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadCount, setReloadCount] = useState(0);
  const [adding, setAdding] = useState(false);
  const [editing, setEditing] = useState<PlaceSummary | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const result = await listPlaces(
        authFetch,
        zone.id,
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
  }, [authFetch, zone.id, query, paging, reloadCount, reloadKey]);

  const refresh = () => {
    setReloadCount((c) => c + 1);
    onChanged();
  };

  const remove = async (place: PlaceSummary) => {
    const result = await deletePlace(authFetch, place.id);
    if (!result.ok) {
      setError(result.reason);
      return;
    }
    refresh();
  };

  const columns: GridColDef<PlaceSummary>[] = [
    { field: "name", headerName: "Place", flex: 1.4, sortable: false },
    { field: "pinCode", headerName: "PIN code", flex: 1, sortable: false },
  ];
  if (canEdit) {
    columns.push({
      field: "actions",
      headerName: "Actions",
      flex: 1.2,
      sortable: false,
      renderCell: (params) => (
        <Stack direction="row" spacing={1}>
          <Button size="small" onClick={() => setEditing(params.row)}>
            Edit
          </Button>
          <Button size="small" color="error" onClick={() => remove(params.row)}>
            Delete
          </Button>
        </Stack>
      ),
    });
  }

  return (
    <Box sx={{ mt: 3 }}>
      <Stack
        direction="row"
        sx={{ alignItems: "center", justifyContent: "space-between", mb: 1 }}
      >
        <Typography variant="h6" component="h2">
          Places in {zone.name}
        </Typography>
        <Stack direction="row" spacing={1}>
          {toolbar}
          {canEdit && (
            <Button variant="contained" onClick={() => setAdding(true)}>
              Add place
            </Button>
          )}
        </Stack>
      </Stack>
      <TextField
        label="Search places by name or PIN"
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
          localeText={{ noRowsLabel: "No places match your search." }}
        />
      </Box>
      {adding && (
        <PlaceDialog
          zoneId={zone.id}
          onClose={() => setAdding(false)}
          onSaved={() => {
            setAdding(false);
            refresh();
          }}
        />
      )}
      {editing && (
        <PlaceDialog
          zoneId={zone.id}
          place={editing}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            refresh();
          }}
        />
      )}
    </Box>
  );
}
