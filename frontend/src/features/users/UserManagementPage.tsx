import { useCallback, useEffect, useMemo, useState } from "react";
import { DataGrid, type GridColDef } from "@mui/x-data-grid";
import {
  Alert,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { useAccessModel } from "../../access-model/useAccessModel";
import { CreateUserDialog } from "./CreateUserDialog";
import { EditRolesDialog } from "./EditRolesDialog";
import { ResetPasswordDialog } from "./ResetPasswordDialog";
import {
  ALL_ROLES,
  listUsers,
  setActive,
  type ListParams,
  type Role,
  type UserSummary,
} from "./userManagementApi";

const ROUTE = "/identity/users";

/** List, search, filter and manage user accounts (FR-001..FR-006, FR-012). Row and toolbar
 * actions are shown only when the access model grants `CREATE` / `EDIT` for this route. */
export function UserManagementPage() {
  const { authFetch, user: me } = useAuth();
  const { accessModel } = useAccessModel();
  const grantedActions = useMemo(() => {
    const item = accessModel?.navigation
      .flatMap((section) => section.items)
      .find((i) => i.route === ROUTE);
    return new Set(item?.actions ?? []);
  }, [accessModel]);
  const canCreate = grantedActions.has("CREATE");
  const canEdit = grantedActions.has("EDIT");

  const [filters, setFilters] = useState<Omit<ListParams, "page" | "size">>({
    query: "",
    role: "",
    active: "",
  });
  const [paging, setPaging] = useState({ page: 0, pageSize: 25 });
  const [rows, setRows] = useState<UserSummary[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [reloadCount, setReloadCount] = useState(0);

  const [creating, setCreating] = useState(false);
  const [editingRoles, setEditingRoles] = useState<UserSummary | null>(null);
  const [resetting, setResetting] = useState<UserSummary | null>(null);
  const [confirmSelfDeactivate, setConfirmSelfDeactivate] =
    useState<UserSummary | null>(null);

  const reload = useCallback(() => setReloadCount((c) => c + 1), []);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const result = await listUsers(authFetch, {
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

  const toggleActive = async (target: UserSummary) => {
    setActionError(null);
    const result = await setActive(authFetch, target.id, !target.active);
    if (!result.ok) {
      setActionError(result.reason);
      return;
    }
    reload();
  };

  const requestToggle = (target: UserSummary) => {
    if (target.active && target.id === me?.id) {
      setConfirmSelfDeactivate(target);
      return;
    }
    void toggleActive(target);
  };

  const columns: GridColDef<UserSummary>[] = [
    { field: "displayName", headerName: "Name", flex: 1.2, sortable: false },
    { field: "phone", headerName: "Phone", flex: 1, sortable: false },
    {
      field: "roles",
      headerName: "Roles",
      flex: 1.4,
      sortable: false,
      valueGetter: (_value, row) => row.roles.join(", "),
    },
    {
      field: "active",
      headerName: "Status",
      flex: 0.8,
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
      flex: 2,
      sortable: false,
      renderCell: (params) => (
        <Stack direction="row" spacing={1}>
          <Button size="small" onClick={() => setEditingRoles(params.row)}>
            Roles
          </Button>
          <Button size="small" onClick={() => requestToggle(params.row)}>
            {params.row.active ? "Deactivate" : "Reactivate"}
          </Button>
          <Button size="small" onClick={() => setResetting(params.row)}>
            Reset password
          </Button>
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
          User Management
        </Typography>
        {canCreate && (
          <Button variant="contained" onClick={() => setCreating(true)}>
            Create user
          </Button>
        )}
      </Stack>

      <Stack direction={{ xs: "column", sm: "row" }} spacing={2} sx={{ mb: 2 }}>
        <TextField
          label="Search by name or phone"
          size="small"
          value={filters.query}
          onChange={(e) => {
            setPaging((p) => ({ ...p, page: 0 }));
            setFilters((f) => ({ ...f, query: e.target.value }));
          }}
        />
        <TextField
          select
          label="Role"
          size="small"
          sx={{ minWidth: 160 }}
          value={filters.role}
          onChange={(e) => {
            setPaging((p) => ({ ...p, page: 0 }));
            setFilters((f) => ({ ...f, role: e.target.value as Role | "" }));
          }}
        >
          <MenuItem value="">All roles</MenuItem>
          {ALL_ROLES.map((role) => (
            <MenuItem key={role} value={role}>
              {role}
            </MenuItem>
          ))}
        </TextField>
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
              active: e.target.value as ListParams["active"],
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
          localeText={{ noRowsLabel: "No users match your search." }}
        />
      </Box>

      {creating && (
        <CreateUserDialog
          onClose={() => setCreating(false)}
          onCreated={() => {
            setCreating(false);
            reload();
          }}
        />
      )}
      {editingRoles && (
        <EditRolesDialog
          user={editingRoles}
          onClose={() => setEditingRoles(null)}
          onSaved={() => {
            setEditingRoles(null);
            reload();
          }}
        />
      )}
      {resetting && (
        <ResetPasswordDialog
          user={resetting}
          isSelf={resetting.id === me?.id}
          onClose={() => setResetting(null)}
          onDone={() => {
            setResetting(null);
            reload();
          }}
        />
      )}
      {confirmSelfDeactivate && (
        <Dialog open onClose={() => setConfirmSelfDeactivate(null)}>
          <DialogTitle>Deactivate your own account?</DialogTitle>
          <DialogContent>
            <DialogContentText>
              Your current session will end immediately and you will not be able
              to sign in until another administrator reactivates your account.
            </DialogContentText>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setConfirmSelfDeactivate(null)}>
              Cancel
            </Button>
            <Button
              color="error"
              variant="contained"
              onClick={() => {
                const target = confirmSelfDeactivate;
                setConfirmSelfDeactivate(null);
                void toggleActive(target);
              }}
            >
              Deactivate
            </Button>
          </DialogActions>
        </Dialog>
      )}
    </Box>
  );
}
