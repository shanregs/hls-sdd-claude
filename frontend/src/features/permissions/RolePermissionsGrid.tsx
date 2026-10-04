import { useEffect, useMemo, useState, type ElementType } from "react";
import {
  Alert,
  Box,
  IconButton,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tooltip,
  Typography,
} from "@mui/material";
import AddCircleOutline from "@mui/icons-material/AddCircleOutlineOutlined";
import DeleteOutline from "@mui/icons-material/DeleteOutlined";
import EditOutlined from "@mui/icons-material/EditOutlined";
import FileDownloadOutlined from "@mui/icons-material/FileDownloadOutlined";
import PlayCircleOutline from "@mui/icons-material/PlayCircleOutlined";
import TaskAltOutlined from "@mui/icons-material/TaskAltOutlined";
import VisibilityOutlined from "@mui/icons-material/VisibilityOutlined";
import { useAuth } from "../../auth/useAuth";
import { useGrantedActions } from "../common/useGrantedActions";
import { EditGrantDialog } from "./EditGrantDialog";
import type { MatrixResponse, MatrixRow } from "./types";

const ROUTE = "/identity/permissions";

/** The five fixed roles, in the order the columns appear (Constitution Principle II). */
const ROLES = ["ADMIN", "DIRECTOR", "MANAGER", "TEACHER", "SYSTEM"];

const ACTIONS: Record<string, { label: string; Icon: ElementType }> = {
  VIEW: { label: "View", Icon: VisibilityOutlined },
  CREATE: { label: "Create", Icon: AddCircleOutline },
  EDIT: { label: "Edit", Icon: EditOutlined },
  DELETE: { label: "Delete", Icon: DeleteOutline },
  APPROVE: { label: "Approve", Icon: TaskAltOutlined },
  PROCESS: { label: "Process", Icon: PlayCircleOutline },
  EXPORT: { label: "Export", Icon: FileDownloadOutlined },
};

/**
 * One icon for one action of one role on one module (spec 002 FR-005): coloured when granted, grey when
 * the action could be granted but is not. For someone who may edit the matrix it is a toggle button
 * that opens the confirmation dialog; otherwise it is a plain labelled icon.
 */
function GrantIcon({
  role,
  module,
  action,
  granted,
  canEdit,
  onToggle,
}: {
  role: string;
  module: string;
  action: string;
  granted: boolean;
  canEdit: boolean;
  onToggle: () => void;
}) {
  const { label, Icon } = ACTIONS[action] ?? {
    label: action,
    Icon: EditOutlined,
  };
  const state = granted ? "granted" : "not granted";
  const icon = (
    <Icon
      fontSize="small"
      sx={{ color: granted ? "primary.main" : "action.disabled" }}
    />
  );
  const name = `${role} ${label} ${module}: ${state}`;
  return (
    <Tooltip title={`${label} - ${state}`}>
      {canEdit ? (
        <IconButton
          size="small"
          aria-label={name}
          aria-pressed={granted}
          onClick={onToggle}
          sx={{ p: 0.25 }}
        >
          {icon}
        </IconButton>
      ) : (
        <Box
          component="span"
          role="img"
          aria-label={name}
          sx={{ display: "inline-flex", p: 0.25 }}
        >
          {icon}
        </Box>
      )}
    </Tooltip>
  );
}

/**
 * Role &amp; Permissions as one compact Module x Role grid (spec 002 FR-002, FR-005): each cell shows an icon
 * per action that applies to that module and role, coloured when granted and grey when not, with a dash
 * where nothing applies. Admin, Director and System (the server gates the route) toggle a grant by
 * clicking its icon; the change is confirmed in {@link EditGrantDialog}, which also surfaces the
 * server's refusals.
 */
export function RolePermissionsGrid() {
  const { authFetch } = useAuth();
  const canEdit = useGrantedActions(ROUTE).has("EDIT");
  const [matrix, setMatrix] = useState<MatrixResponse | null>(null);
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
        const data = (await response.json()) as MatrixResponse;
        if (!cancelled) {
          setError(null);
          setMatrix(data);
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

  const granted = useMemo(() => {
    const set = new Set<string>();
    for (const e of matrix?.entries ?? []) {
      if (e.granted) set.add(`${e.role}|${e.module}|${e.action}`);
    }
    return set;
  }, [matrix]);

  // The legend only lists actions some module actually offers (Approve has none yet).
  const usedActions = useMemo(
    () =>
      new Set(matrix?.modules.flatMap((m) => Object.values(m.eligible).flat())),
    [matrix],
  );

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
      {!matrix && !error && (
        <Typography>Loading the permission matrix…</Typography>
      )}
      {matrix && (
        <>
          <Stack
            direction="row"
            spacing={3}
            sx={{ mb: 1, alignItems: "center", flexWrap: "wrap", gap: 1 }}
          >
            {Object.entries(ACTIONS)
              .filter(([key]) => usedActions.has(key))
              .map(([key, { label, Icon }]) => (
                <Stack
                  key={key}
                  direction="row"
                  spacing={0.5}
                  sx={{ alignItems: "center" }}
                >
                  <Icon fontSize="small" sx={{ color: "primary.main" }} />
                  <Typography variant="caption">{label}</Typography>
                </Stack>
              ))}
            <Typography variant="caption" color="text.secondary">
              Coloured icon = granted · grey icon = not granted · — = does not
              apply
              {canEdit ? " · click an icon to change it" : ""}
            </Typography>
          </Stack>
          <Paper variant="outlined">
            <TableContainer sx={{ maxHeight: "72vh" }}>
              <Table
                size="small"
                stickyHeader
                aria-label="Role and permission matrix"
              >
                <TableHead>
                  <TableRow>
                    <TableCell sx={{ fontWeight: 700, minWidth: 190 }}>
                      Module
                    </TableCell>
                    {ROLES.map((role) => (
                      <TableCell
                        key={role}
                        align="center"
                        sx={{ fontWeight: 700 }}
                      >
                        {role}
                      </TableCell>
                    ))}
                  </TableRow>
                </TableHead>
                <TableBody>
                  {matrix.modules.map((m) => (
                    <TableRow key={m.module} hover>
                      <TableCell component="th" scope="row" sx={{ py: 0.25 }}>
                        {m.module}
                      </TableCell>
                      {ROLES.map((role) => {
                        const actions = m.eligible[role] ?? [];
                        return (
                          <TableCell
                            key={role}
                            align="center"
                            sx={{ py: 0.25, px: 0.5 }}
                          >
                            {actions.length === 0 ? (
                              <Typography
                                component="span"
                                color="text.secondary"
                                aria-label={`${role} ${m.module}: does not apply`}
                              >
                                —
                              </Typography>
                            ) : (
                              <Stack
                                direction="row"
                                spacing={0.25}
                                sx={{ justifyContent: "center" }}
                              >
                                {actions.map((action) => {
                                  const isGranted = granted.has(
                                    `${role}|${m.module}|${action}`,
                                  );
                                  return (
                                    <GrantIcon
                                      key={action}
                                      role={role}
                                      module={m.module}
                                      action={action}
                                      granted={isGranted}
                                      canEdit={canEdit}
                                      onToggle={() =>
                                        setEditing({
                                          id: `${role}|${m.module}|${action}`,
                                          role,
                                          module: m.module,
                                          action,
                                          granted: isGranted,
                                        })
                                      }
                                    />
                                  );
                                })}
                              </Stack>
                            )}
                          </TableCell>
                        );
                      })}
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </Paper>
        </>
      )}
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
