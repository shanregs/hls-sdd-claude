import { useEffect, useState } from "react";
import { DataGrid, type GridColDef } from "@mui/x-data-grid";
import {
  Alert,
  Box,
  Button,
  Checkbox,
  Chip,
  FormControlLabel,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { useGrantedActions } from "../common/useGrantedActions";
import { AssignZonesDialog } from "./AssignZonesDialog";
import { ManagerDialog } from "./ManagerDialog";
import { formatDate } from "../teachers/formatters";
import { ManagerEmploymentDialog } from "./ManagerEmploymentDialog";
import {
  listManagers,
  MISSING_LABELS,
  type ManagerSummary,
} from "./managersApi";
import { RowActionButton } from "../common/RowActionButton";

const ROUTE = "/master-data/managers";
const DESIGNATIONS_ROUTE = "/master-data/designations";

/** Manager records (FR-007/FR-008): list, create and assign Zones. School assignment is done from
 * the Schools screen. */
export function ManagersPage() {
  const { authFetch } = useAuth();
  const actions = useGrantedActions(ROUTE);
  const canCreate = actions.has("CREATE");
  const canEdit = actions.has("EDIT");
  // designation, employee id and dates need DESIGNATIONS EDIT, not MANAGERS EDIT (spec 005a FR-011)
  const canEditEmployment = useGrantedActions(DESIGNATIONS_ROUTE).has("EDIT");

  const [query, setQuery] = useState("");
  const [paging, setPaging] = useState({ page: 0, pageSize: 25 });
  const [rows, setRows] = useState<ManagerSummary[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadCount, setReloadCount] = useState(0);
  const [creating, setCreating] = useState(false);
  const [zonesFor, setZonesFor] = useState<ManagerSummary | null>(null);
  const [employmentFor, setEmploymentFor] = useState<ManagerSummary | null>(
    null,
  );
  // the Designations screen links here with ?missing=true
  const [missingOnly, setMissingOnly] = useState(
    () => new URLSearchParams(window.location.search).get("missing") === "true",
  );

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const result = await listManagers(
        authFetch,
        query,
        paging.page,
        paging.pageSize,
        missingOnly,
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
  }, [authFetch, query, paging, reloadCount, missingOnly]);

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
    {
      field: "designation",
      headerName: "Designation",
      flex: 1.2,
      sortable: false,
      renderCell: (params) => {
        const e = params.row.employment;
        if (!e) return null;
        return e.designation ? (
          <span>
            {e.designation.name}
            {e.designation.retired ? " (retired)" : ""}
          </span>
        ) : (
          <Chip
            size="small"
            color="warning"
            label={MISSING_LABELS.DESIGNATION}
          />
        );
      },
    },
    {
      field: "employeeId",
      headerName: "Employee id",
      flex: 0.8,
      sortable: false,
      valueGetter: (_value, row) => row.employment?.employeeId ?? "",
    },
    {
      field: "joiningDate",
      headerName: "Joined",
      flex: 0.9,
      sortable: false,
      renderCell: (params) => {
        const e = params.row.employment;
        if (!e) return null;
        return e.joiningDate ? (
          <span>{formatDate(e.joiningDate)}</span>
        ) : (
          <Chip
            size="small"
            color="warning"
            label={MISSING_LABELS.JOINING_DATE}
          />
        );
      },
    },
    {
      field: "exitDate",
      headerName: "Exit date",
      flex: 0.9,
      sortable: false,
      renderCell: (params) => {
        const e = params.row.employment;
        if (!e || params.row.active) return null;
        return e.exitDate ? (
          <span>{formatDate(e.exitDate)}</span>
        ) : (
          <Chip size="small" color="warning" label={MISSING_LABELS.EXIT_DATE} />
        );
      },
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
  if (canEdit || canEditEmployment) {
    columns.push({
      field: "actions",
      headerName: "Actions",
      flex: 1.4,
      sortable: false,
      renderCell: (params) => (
        <Stack direction="row" spacing={1}>
          {canEdit && (
            <RowActionButton
              action="Edit"
              subject={`zones of ${params.row.displayName}`}
              onClick={() => setZonesFor(params.row)}
            />
          )}
          {canEditEmployment && (
            <Button size="small" onClick={() => setEmploymentFor(params.row)}>
              Employment
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
          Managers
        </Typography>
        {canCreate && (
          <Button variant="contained" onClick={() => setCreating(true)}>
            Create manager
          </Button>
        )}
      </Stack>
      <Stack direction={{ xs: "column", sm: "row" }} spacing={2} sx={{ mb: 2 }}>
        <TextField
          label="Search managers"
          size="small"
          value={query}
          onChange={(e) => {
            setPaging((p) => ({ ...p, page: 0 }));
            setQuery(e.target.value);
          }}
        />
        <FormControlLabel
          control={
            <Checkbox
              checked={missingOnly}
              onChange={(e) => {
                setPaging((p) => ({ ...p, page: 0 }));
                setMissingOnly(e.target.checked);
              }}
            />
          }
          label="Missing details"
        />
      </Stack>
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
      {employmentFor && (
        <ManagerEmploymentDialog
          manager={employmentFor}
          onClose={() => setEmploymentFor(null)}
          onSaved={() => {
            setEmploymentFor(null);
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
