import { useEffect, useMemo, useState, type ElementType } from "react";
import {
  Alert,
  Box,
  IconButton,
  Paper,
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

/** The five fixed roles in column order: System first (Constitution Principle II). */
export const ROLES = ["SYSTEM", "ADMIN", "DIRECTOR", "MANAGER", "TEACHER"];

/** Every action, in the order its sub-column appears under each role. */
const ACTIONS: Record<string, { label: string; Icon: ElementType }> = {
  VIEW: { label: "View", Icon: VisibilityOutlined },
  CREATE: { label: "Create", Icon: AddCircleOutline },
  EDIT: { label: "Edit", Icon: EditOutlined },
  DELETE: { label: "Delete", Icon: DeleteOutline },
  APPROVE: { label: "Approve", Icon: TaskAltOutlined },
  PROCESS: { label: "Process", Icon: PlayCircleOutline },
  EXPORT: { label: "Export", Icon: FileDownloadOutlined },
};

/** A sub-column is narrow: one icon wide. */
const CELL_WIDTH = 28;

const ROLE_DIVIDER = { borderLeft: 2, borderLeftColor: "text.disabled" };

/**
 * One icon for one action of one role on one module (spec 002 FR-004a): coloured when granted, grey when
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
    <Tooltip title={`${role} - ${label}: ${state}`}>
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
 * Role &amp; Permissions as one compact grid (spec 002 FR-002, FR-004a): a row per module, and under each
 * role a small cell per action. A cell holds a coloured icon when the action is granted, a grey icon
 * when it could be granted but is not, and is shaded and empty when it does not apply to that module
 * and role. Admin, Director and System (the server gates the route) toggle a grant by clicking its
 * icon; the change is confirmed in {@link EditGrantDialog}, which also surfaces the server's refusals.
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

  // Only actions some module actually offers get a sub-column (Approve has none yet).
  const actions = useMemo(() => {
    const used = new Set(
      matrix?.modules.flatMap((m) => Object.values(m.eligible).flat()),
    );
    return Object.keys(ACTIONS).filter((a) => used.has(a));
  }, [matrix]);

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
          <Typography
            variant="caption"
            color="text.secondary"
            component="p"
            sx={{ mb: 1 }}
          >
            Coloured icon = granted · grey icon = not granted · shaded cell =
            does not apply
            {canEdit ? " · click an icon to change it" : ""}
          </Typography>
          <Paper variant="outlined">
            <TableContainer sx={{ maxHeight: "74vh" }}>
              <Table
                size="small"
                stickyHeader
                aria-label="Role and permission matrix"
                sx={{ width: "auto", minWidth: "100%" }}
              >
                <TableHead>
                  <TableRow>
                    <TableCell
                      rowSpan={2}
                      sx={{
                        fontWeight: 700,
                        minWidth: 190,
                        position: "sticky",
                        left: 0,
                        zIndex: 3,
                        verticalAlign: "bottom",
                      }}
                    >
                      Module
                    </TableCell>
                    {ROLES.map((role) => (
                      <TableCell
                        key={role}
                        colSpan={actions.length}
                        scope="colgroup"
                        align="center"
                        sx={{ fontWeight: 700, ...ROLE_DIVIDER }}
                      >
                        {role}
                      </TableCell>
                    ))}
                  </TableRow>
                  <TableRow>
                    {ROLES.flatMap((role) =>
                      actions.map((action, i) => {
                        const { label, Icon } = ACTIONS[action];
                        return (
                          <TableCell
                            key={`${role}|${action}`}
                            scope="col"
                            align="center"
                            aria-label={`${role} ${label}`}
                            sx={{
                              top: 37,
                              p: 0.25,
                              width: CELL_WIDTH,
                              minWidth: CELL_WIDTH,
                              ...(i === 0 && ROLE_DIVIDER),
                            }}
                          >
                            <Tooltip title={label}>
                              <Icon
                                fontSize="small"
                                sx={{ color: "text.secondary" }}
                              />
                            </Tooltip>
                          </TableCell>
                        );
                      }),
                    )}
                  </TableRow>
                </TableHead>
                <TableBody>
                  {matrix.modules.map((m) => (
                    <TableRow key={m.module} hover>
                      <TableCell
                        component="th"
                        scope="row"
                        sx={{
                          py: 0.25,
                          position: "sticky",
                          left: 0,
                          zIndex: 1,
                          bgcolor: "background.paper",
                        }}
                      >
                        {m.module}
                      </TableCell>
                      {ROLES.flatMap((role) =>
                        actions.map((action, i) => {
                          const eligible = (m.eligible[role] ?? []).includes(
                            action,
                          );
                          const isGranted = granted.has(
                            `${role}|${m.module}|${action}`,
                          );
                          return (
                            <TableCell
                              key={`${role}|${action}`}
                              align="center"
                              sx={{
                                p: 0.25,
                                width: CELL_WIDTH,
                                minWidth: CELL_WIDTH,
                                ...(i === 0 && ROLE_DIVIDER),
                                ...(!eligible && { bgcolor: "action.hover" }),
                              }}
                            >
                              {eligible && (
                                <GrantIcon
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
                              )}
                            </TableCell>
                          );
                        }),
                      )}
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
