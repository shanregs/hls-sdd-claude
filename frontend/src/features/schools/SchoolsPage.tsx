import { useEffect, useState } from "react";
import { DataGrid, type GridColDef } from "@mui/x-data-grid";
import {
  Alert,
  Box,
  Button,
  Chip,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { useGrantedActions } from "../common/useGrantedActions";
import { ChangePlaceDialog } from "./ChangePlaceDialog";
import { AssignSchoolManagerDialog } from "./AssignSchoolManagerDialog";
import { SchoolDialog } from "./SchoolDialog";
import {
  listSchools,
  setSchoolActive,
  type SchoolListParams,
  type SchoolSummary,
} from "./schoolsApi";

const ROUTE = "/master-data/schools";

/** School management (FR-004..FR-006): list/search/filter, create, edit, move, (de)activate.
 * Admin/Director hold CREATE; a Manager (VIEW+EDIT only) edits contact fields of assigned Schools. */
export function SchoolsPage() {
  const { authFetch } = useAuth();
  const actions = useGrantedActions(ROUTE);
  const canCreate = actions.has("CREATE");
  const canEdit = actions.has("EDIT");
  // Only Admin/Director hold CREATE; a Manager's edit is limited to contact fields (FR-006).
  const limitedEdit = canEdit && !canCreate;
  const canAssignManager = useGrantedActions("/master-data/managers").has(
    "EDIT",
  );

  const [filters, setFilters] = useState<
    Omit<SchoolListParams, "page" | "size">
  >({
    query: "",
    zoneId: "",
    active: "",
  });
  const [paging, setPaging] = useState({ page: 0, pageSize: 25 });
  const [rows, setRows] = useState<SchoolSummary[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [reloadCount, setReloadCount] = useState(0);
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<SchoolSummary | null>(null);
  const [moving, setMoving] = useState<SchoolSummary | null>(null);
  const [assigning, setAssigning] = useState<SchoolSummary | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const result = await listSchools(authFetch, {
        ...filters,
        page: paging.page,
        size: paging.pageSize,
      });
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
  }, [authFetch, filters, paging, reloadCount]);

  const reload = () => setReloadCount((c) => c + 1);

  const toggleActive = async (school: SchoolSummary) => {
    setActionError(null);
    const result = await setSchoolActive(authFetch, school.id, !school.active);
    if (!result.ok) {
      setActionError(result.reason);
      return;
    }
    reload();
  };

  const columns: GridColDef<SchoolSummary>[] = [
    { field: "name", headerName: "School", flex: 1.4, sortable: false },
    {
      field: "place",
      headerName: "Place",
      flex: 1,
      sortable: false,
      valueGetter: (_value, row) => `${row.place.name} (${row.place.pinCode})`,
    },
    {
      field: "zone",
      headerName: "Zone",
      flex: 1,
      sortable: false,
      valueGetter: (_value, row) => row.zone.name,
    },
    {
      field: "manager",
      headerName: "Manager",
      flex: 1.1,
      sortable: false,
      renderCell: (params) =>
        params.row.manager ? (
          <span>{params.row.manager.displayName}</span>
        ) : (
          <Chip size="small" color="warning" label="Needs a Manager" />
        ),
    },
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
      flex: 2.4,
      sortable: false,
      renderCell: (params) => (
        <Stack direction="row" spacing={1}>
          <Button size="small" onClick={() => setEditing(params.row)}>
            Edit
          </Button>
          {!limitedEdit && (
            <Button size="small" onClick={() => setMoving(params.row)}>
              Move
            </Button>
          )}
          {!limitedEdit && canAssignManager && (
            <Button size="small" onClick={() => setAssigning(params.row)}>
              Manager
            </Button>
          )}
          {!limitedEdit && (
            <Button size="small" onClick={() => toggleActive(params.row)}>
              {params.row.active ? "Deactivate" : "Reactivate"}
            </Button>
          )}
        </Stack>
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
          Schools
        </Typography>
        {canCreate && (
          <Button variant="contained" onClick={() => setCreating(true)}>
            Create school
          </Button>
        )}
      </Stack>

      <Stack direction={{ xs: "column", sm: "row" }} spacing={2} sx={{ mb: 2 }}>
        <TextField
          label="Search schools"
          size="small"
          value={filters.query}
          onChange={(e) => {
            setPaging((p) => ({ ...p, page: 0 }));
            setFilters((f) => ({ ...f, query: e.target.value }));
          }}
        />
        <TextField
          select
          label="Status"
          size="small"
          sx={{ minWidth: 160 }}
          value={filters.active}
          onChange={(e) => {
            setPaging((p) => ({ ...p, page: 0 }));
            setFilters((f) => ({
              ...f,
              active: e.target.value as SchoolListParams["active"],
            }));
          }}
        >
          <MenuItem value="">All</MenuItem>
          <MenuItem value="true">Active</MenuItem>
          <MenuItem value="false">Inactive</MenuItem>
        </TextField>
      </Stack>

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
          localeText={{ noRowsLabel: "No schools match your search." }}
        />
      </Box>

      {creating && (
        <SchoolDialog
          onClose={() => setCreating(false)}
          onSaved={() => {
            setCreating(false);
            reload();
          }}
        />
      )}
      {editing && (
        <SchoolDialog
          school={editing}
          limited={limitedEdit}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            reload();
          }}
        />
      )}
      {moving && (
        <ChangePlaceDialog
          school={moving}
          onClose={() => setMoving(null)}
          onSaved={() => {
            setMoving(null);
            reload();
          }}
        />
      )}
      {assigning && (
        <AssignSchoolManagerDialog
          school={assigning}
          onClose={() => setAssigning(null)}
          onSaved={() => {
            setAssigning(null);
            reload();
          }}
        />
      )}
    </Box>
  );
}
