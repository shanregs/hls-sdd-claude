import { useEffect, useState } from "react";
import { Link as RouterLink } from "react-router-dom";
import { DataGrid, type GridColDef } from "@mui/x-data-grid";
import {
  Alert,
  Box,
  Button,
  Chip,
  Link,
  Paper,
  Stack,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { useGrantedActions } from "../common/useGrantedActions";
import { RowActionButton } from "../common/RowActionButton";
import { DesignationDialog } from "./DesignationDialog";
import {
  getDesignationSummary,
  KIND_LABELS,
  listDesignations,
  updateDesignation,
  type DesignationRow,
  type DesignationSummary,
} from "./designationsApi";

const ROUTE = "/master-data/designations";

/** The list of designations (spec 005a US1) and the counts of people with details missing (US4). */
export function DesignationsPage() {
  const { authFetch } = useAuth();
  const actions = useGrantedActions(ROUTE);
  const canCreate = actions.has("CREATE");
  const canEdit = actions.has("EDIT");

  const [rows, setRows] = useState<DesignationRow[]>([]);
  const [summary, setSummary] = useState<DesignationSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadCount, setReloadCount] = useState(0);
  const [adding, setAdding] = useState(false);
  const [renaming, setRenaming] = useState<DesignationRow | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const [list, counts] = await Promise.all([
        listDesignations(authFetch),
        getDesignationSummary(authFetch),
      ]);
      if (cancelled) return;
      setLoading(false);
      if (list.ok) {
        setError(null);
        setRows(list.data);
      } else {
        setError(list.reason);
      }
      if (counts.ok) setSummary(counts.data);
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch, reloadCount]);

  const reload = () => setReloadCount((c) => c + 1);

  const toggleRetired = async (row: DesignationRow) => {
    const result = await updateDesignation(authFetch, row, {
      retired: !row.retired,
    });
    if (!result.ok) {
      setError(result.reason);
      return;
    }
    reload();
  };

  const columns: GridColDef<DesignationRow>[] = [
    { field: "name", headerName: "Designation", flex: 1.6, sortable: false },
    {
      field: "kind",
      headerName: "For",
      flex: 0.8,
      sortable: false,
      valueGetter: (_value, row) => KIND_LABELS[row.kind],
    },
    {
      field: "retired",
      headerName: "Status",
      flex: 0.8,
      sortable: false,
      renderCell: (params) => (
        <Chip
          size="small"
          label={params.row.retired ? "Retired" : "Active"}
          color={params.row.retired ? "default" : "success"}
        />
      ),
    },
    { field: "holders", headerName: "People", flex: 0.6, sortable: false },
  ];
  if (canEdit) {
    columns.push({
      field: "actions",
      headerName: "Actions",
      flex: 1.4,
      sortable: false,
      renderCell: (params) => (
        <Stack direction="row" spacing={1}>
          <RowActionButton
            action="Edit"
            subject={params.row.name}
            onClick={() => setRenaming(params.row)}
          />
          <Button size="small" onClick={() => toggleRetired(params.row)}>
            {params.row.retired ? "Reactivate" : "Retire"}
          </Button>
        </Stack>
      ),
    });
  }

  const missingTotal = summary
    ? summary.teachersMissingDesignation +
      summary.managersMissingDesignation +
      summary.managersMissingJoiningDate +
      summary.managersMissingExitDate
    : 0;

  return (
    <Box>
      <Stack
        direction="row"
        sx={{ alignItems: "center", justifyContent: "space-between", mb: 2 }}
      >
        <Typography variant="h5" component="h1">
          Designations
        </Typography>
        {canCreate && (
          <Button variant="contained" onClick={() => setAdding(true)}>
            Add designation
          </Button>
        )}
      </Stack>

      {summary && (
        <Paper variant="outlined" sx={{ p: 2, mb: 2 }}>
          <Typography variant="h6" component="h2" gutterBottom>
            Details missing before payroll
          </Typography>
          {missingTotal === 0 ? (
            <Typography>
              Every Manager and Teacher has the details recorded.
            </Typography>
          ) : (
            <Stack spacing={0.5}>
              <Typography>
                Teachers without a designation:{" "}
                <Link
                  component={RouterLink}
                  to="/master-data/teachers?missingDesignation=true"
                >
                  {summary.teachersMissingDesignation}
                </Link>
              </Typography>
              <Typography>
                Managers without a designation:{" "}
                <Link
                  component={RouterLink}
                  to="/master-data/managers?missing=true"
                >
                  {summary.managersMissingDesignation}
                </Link>
              </Typography>
              <Typography>
                Managers without a joining date:{" "}
                <Link
                  component={RouterLink}
                  to="/master-data/managers?missing=true"
                >
                  {summary.managersMissingJoiningDate}
                </Link>
              </Typography>
              <Typography>
                Inactive Managers without an exit date:{" "}
                <Link
                  component={RouterLink}
                  to="/master-data/managers?missing=true"
                >
                  {summary.managersMissingExitDate}
                </Link>
              </Typography>
            </Stack>
          )}
        </Paper>
      )}

      {error && (
        <Alert severity="error" sx={{ mb: 2 }} role="alert">
          {error}
        </Alert>
      )}
      <Box sx={{ height: 520, width: "100%", bgcolor: "background.paper" }}>
        <DataGrid
          rows={rows}
          columns={columns}
          loading={loading}
          disableRowSelectionOnClick
          disableColumnFilter
          pageSizeOptions={[25, 50, 100]}
          initialState={{ pagination: { paginationModel: { pageSize: 25 } } }}
          localeText={{ noRowsLabel: "No designations yet." }}
        />
      </Box>
      {adding && (
        <DesignationDialog
          onClose={() => setAdding(false)}
          onSaved={() => {
            setAdding(false);
            reload();
          }}
        />
      )}
      {renaming && (
        <DesignationDialog
          designation={renaming}
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
