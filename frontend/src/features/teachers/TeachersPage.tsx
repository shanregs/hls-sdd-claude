import { useEffect, useState } from "react";
import { DataGrid, type GridColDef } from "@mui/x-data-grid";
import {
  Alert,
  Box,
  Button,
  Checkbox,
  Chip,
  FormControlLabel,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { useAccessModel } from "../../access-model/useAccessModel";
import { useGrantedActions } from "../common/useGrantedActions";
import { formatDate } from "./formatters";
import { LinkAccountDialog } from "./LinkAccountDialog";
import { PlacementDialog } from "./PlacementDialog";
import { SalaryDialog } from "./SalaryDialog";
import { StatusDialog } from "./StatusDialog";
import { TeacherEmploymentDialog } from "./TeacherEmploymentDialog";
import { TeacherDialog } from "./TeacherDialog";
import {
  listTeachers,
  STATUS_LABELS,
  type TeacherListParams,
  type TeacherSummary,
} from "./teachersApi";
import { RowActionButton } from "../common/RowActionButton";

const ROUTE = "/master-data/teachers";
const DESIGNATIONS_ROUTE = "/master-data/designations";

/**
 * Teacher management (FR-011..FR-015): list/search/filter, create, edit contact, change status,
 * place/move. Admin/Director hold CREATE; a Manager (VIEW+EDIT only) edits contact details of
 * Teachers in their Schools. Controls show only when the access model grants the action.
 */
export function TeachersPage() {
  const { authFetch } = useAuth();
  const actions = useGrantedActions(ROUTE);
  const canCreate = actions.has("CREATE");
  const canEdit = actions.has("EDIT");
  const limitedEdit = canEdit && !canCreate;
  // designation and employee id need DESIGNATIONS EDIT, not TEACHERS EDIT (spec 005a FR-011)
  const canEditEmployment = useGrantedActions(DESIGNATIONS_ROUTE).has("EDIT");
  // Salary is shown only when the access model reports a salary scope (TEACHER_SALARY granted).
  const { accessModel } = useAccessModel();
  const canSeeSalary = Boolean(accessModel?.dataScope?.TEACHER_SALARY);

  const [filters, setFilters] = useState<
    Omit<TeacherListParams, "page" | "size">
  >({
    query: "",
    status: "",
    // the Designations screen links here with ?missingDesignation=true
    missingDesignation:
      new URLSearchParams(window.location.search).get("missingDesignation") ===
      "true",
  });
  const [paging, setPaging] = useState({ page: 0, pageSize: 25 });
  const [rows, setRows] = useState<TeacherSummary[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadCount, setReloadCount] = useState(0);
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<TeacherSummary | null>(null);
  const [statusFor, setStatusFor] = useState<TeacherSummary | null>(null);
  const [placementFor, setPlacementFor] = useState<TeacherSummary | null>(null);
  const [salaryFor, setSalaryFor] = useState<TeacherSummary | null>(null);
  const [accountFor, setAccountFor] = useState<TeacherSummary | null>(null);
  const [employmentFor, setEmploymentFor] = useState<TeacherSummary | null>(
    null,
  );

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const result = await listTeachers(authFetch, {
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

  const columns: GridColDef<TeacherSummary>[] = [
    { field: "name", headerName: "Teacher", flex: 1.3, sortable: false },
    { field: "phone", headerName: "Phone", flex: 1, sortable: false },
    {
      field: "status",
      headerName: "Status",
      flex: 0.9,
      sortable: false,
      renderCell: (params) => (
        <Chip
          size="small"
          label={STATUS_LABELS[params.row.status]}
          color={
            params.row.status === "ACTIVE"
              ? "success"
              : params.row.status === "EXITED"
                ? "default"
                : "info"
          }
        />
      ),
    },
    {
      field: "school",
      headerName: "School",
      flex: 1.6,
      sortable: false,
      renderCell: (params) => (
        <Stack>
          <span>{params.row.school?.name ?? "Not placed"}</span>
          {params.row.pendingPlacement && (
            <Typography variant="caption" color="text.secondary">
              Scheduled: {params.row.pendingPlacement.schoolName} from{" "}
              {formatDate(params.row.pendingPlacement.startsOn)}
            </Typography>
          )}
        </Stack>
      ),
    },
    {
      field: "designation",
      headerName: "Designation",
      flex: 1.1,
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
          <Chip size="small" color="warning" label="designation missing" />
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
      field: "manager",
      headerName: "Manager",
      flex: 1,
      sortable: false,
      valueGetter: (_value, row) => row.manager?.displayName ?? "",
    },
  ];
  if (canEdit || canEditEmployment) {
    columns.push({
      field: "actions",
      headerName: "Actions",
      flex: 1.8,
      sortable: false,
      renderCell: (params) => (
        <Stack direction="row" spacing={1}>
          {canEdit && (
            <RowActionButton
              action="Edit"
              subject={params.row.name}
              onClick={() => setEditing(params.row)}
            />
          )}
          {canEditEmployment && (
            <Button size="small" onClick={() => setEmploymentFor(params.row)}>
              Employment
            </Button>
          )}
          {canEdit && !limitedEdit && (
            <Button size="small" onClick={() => setStatusFor(params.row)}>
              Status
            </Button>
          )}
          {canEdit && !limitedEdit && params.row.status !== "EXITED" && (
            <Button size="small" onClick={() => setAccountFor(params.row)}>
              {params.row.userId ? "Account" : "Link account"}
            </Button>
          )}
          {canEdit && canSeeSalary && (
            <Button size="small" onClick={() => setSalaryFor(params.row)}>
              Salary
            </Button>
          )}
          {canEdit && !limitedEdit && params.row.status !== "EXITED" && (
            <Button size="small" onClick={() => setPlacementFor(params.row)}>
              Placement
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
          Teachers
        </Typography>
        {canCreate && (
          <Button variant="contained" onClick={() => setCreating(true)}>
            Create teacher
          </Button>
        )}
      </Stack>

      <Stack direction={{ xs: "column", sm: "row" }} spacing={2} sx={{ mb: 2 }}>
        <TextField
          label="Search teachers"
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
          value={filters.status}
          onChange={(e) => {
            setPaging((p) => ({ ...p, page: 0 }));
            setFilters((f) => ({
              ...f,
              status: e.target.value as TeacherListParams["status"],
            }));
          }}
        >
          <MenuItem value="">All</MenuItem>
          {(Object.keys(STATUS_LABELS) as (keyof typeof STATUS_LABELS)[]).map(
            (s) => (
              <MenuItem key={s} value={s}>
                {STATUS_LABELS[s]}
              </MenuItem>
            ),
          )}
        </TextField>
        <FormControlLabel
          control={
            <Checkbox
              checked={Boolean(filters.missingDesignation)}
              onChange={(e) => {
                setPaging((p) => ({ ...p, page: 0 }));
                setFilters((f) => ({
                  ...f,
                  missingDesignation: e.target.checked,
                }));
              }}
            />
          }
          label="Missing designation"
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
          localeText={{ noRowsLabel: "No teachers match your search." }}
        />
      </Box>

      {creating && (
        <TeacherDialog
          onClose={() => setCreating(false)}
          onSaved={() => {
            setCreating(false);
            reload();
          }}
        />
      )}
      {editing && (
        <TeacherDialog
          teacher={editing}
          limited={limitedEdit}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            reload();
          }}
        />
      )}
      {statusFor && (
        <StatusDialog
          teacher={statusFor}
          onClose={() => setStatusFor(null)}
          onSaved={() => {
            setStatusFor(null);
            reload();
          }}
        />
      )}
      {placementFor && (
        <PlacementDialog
          teacher={placementFor}
          onClose={() => setPlacementFor(null)}
          onSaved={() => {
            setPlacementFor(null);
            reload();
          }}
        />
      )}
      {employmentFor && (
        <TeacherEmploymentDialog
          teacher={employmentFor}
          onClose={() => setEmploymentFor(null)}
          onSaved={() => {
            setEmploymentFor(null);
            reload();
          }}
        />
      )}
      {accountFor && (
        <LinkAccountDialog
          teacher={accountFor}
          onClose={() => setAccountFor(null)}
          onSaved={() => {
            setAccountFor(null);
            reload();
          }}
        />
      )}
      {salaryFor && (
        <SalaryDialog teacher={salaryFor} onClose={() => setSalaryFor(null)} />
      )}
    </Box>
  );
}
