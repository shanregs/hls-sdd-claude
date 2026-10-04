import { useEffect, useState, type ReactNode } from "react";
import {
  Alert,
  Box,
  Button,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import {
  clearTeacherMark,
  getTeacherMonth,
  setTeacherMark,
  type DayView,
  type GridCell,
  type GridParams,
  type GridResponse,
  type GridRow,
} from "./attendanceApi";
import type { ApiResult, AuthFetch } from "../common/masterDataApi";
import { AttendanceGrid } from "./AttendanceGrid";
import { MarkDialog } from "./MarkDialog";
import { currentMonth, monthLabel, shiftMonth } from "./monthUtils";
import { TeacherMonthPanel } from "./TeacherMonthPanel";

const PAGE_SIZE = 50;

type PanelTeacher = Pick<GridRow, "teacherId" | "name">;

interface AttendanceGridScreenProps {
  title: string;
  loadGrid: (
    authFetch: AuthFetch,
    params: GridParams,
  ) => Promise<ApiResult<GridResponse>>;
  /** May mark and correct days. */
  canMark: boolean;
  /** May clear a mark. */
  canClear: boolean;
  /** Extra filters merged into the request (Zone, School, Manager, status). */
  extraParams?: Partial<GridParams>;
  /** Filter controls rendered beside the search box. */
  filters?: ReactNode;
  /** Page-level actions rendered at the top right, given the month and a reload function. */
  actions?: (ctx: {
    month: string;
    reload: () => void;
    openTeacher: (teacherId: string, name: string) => void;
  }) => ReactNode;
  /** Extra content for the Teacher side panel (lock controls). */
  panelExtras?: (ctx: {
    row: PanelTeacher;
    month: string;
    reload: () => void;
    refreshKey: number;
  }) => ReactNode;
}

/**
 * The month grid page shared by the Manager and Admin/Director screens (spec 008 US2, US4): month
 * navigation, name search, paged rows, cell marking and the Teacher side panel.
 */
export function AttendanceGridScreen({
  title,
  loadGrid,
  canMark,
  canClear,
  extraParams,
  filters,
  actions,
  panelExtras,
}: AttendanceGridScreenProps) {
  const { authFetch } = useAuth();
  const [month, setMonth] = useState(currentMonth());
  const [query, setQuery] = useState("");
  const key = JSON.stringify([month, query, extraParams ?? {}]);
  const [pageState, setPageState] = useState({ key, page: 0 });
  const page = pageState.key === key ? pageState.page : 0;
  const [grid, setGrid] = useState<GridResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [reloadCount, setReloadCount] = useState(0);
  const [editing, setEditing] = useState<{
    row: GridRow;
    day: DayView;
  } | null>(null);
  const [panelRow, setPanelRow] = useState<PanelTeacher | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const result = await loadGrid(authFetch, {
        month,
        query,
        page,
        size: PAGE_SIZE,
        ...extraParams,
      });
      if (cancelled) return;
      if (result.ok) {
        setError(null);
        setGrid(result.data);
      } else {
        setError(result.reason);
      }
    })();
    return () => {
      cancelled = true;
    };
    // The key already captures month, query and extraParams.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [authFetch, loadGrid, key, page, reloadCount]);

  const reload = () => setReloadCount((c) => c + 1);

  const openCell = async (row: GridRow, cell: GridCell) => {
    const result = await getTeacherMonth(authFetch, row.teacherId, month);
    if (!result.ok) {
      setError(result.reason);
      return;
    }
    const day = result.data.days.find((d) => d.date === cell.date);
    if (day) setEditing({ row, day });
  };

  const total = grid?.totalElements ?? 0;
  const from = total === 0 ? 0 : page * PAGE_SIZE + 1;
  const to = Math.min(total, (page + 1) * PAGE_SIZE);

  return (
    <Box>
      <Stack
        direction="row"
        sx={{
          alignItems: "center",
          justifyContent: "space-between",
          mb: 2,
          flexWrap: "wrap",
          gap: 1,
        }}
      >
        <Typography variant="h5" component="h1">
          {title}
        </Typography>
        {actions?.({
          month,
          reload,
          openTeacher: (teacherId, name) => setPanelRow({ teacherId, name }),
        })}
      </Stack>

      <Stack
        direction="row"
        spacing={2}
        sx={{ mb: 2, alignItems: "center", flexWrap: "wrap", gap: 1 }}
      >
        <Button onClick={() => setMonth(shiftMonth(month, -1))}>
          Previous month
        </Button>
        <Typography
          component="span"
          sx={{ fontWeight: 700, minWidth: 140, textAlign: "center" }}
          aria-live="polite"
        >
          {monthLabel(month)}
        </Typography>
        <Button onClick={() => setMonth(shiftMonth(month, 1))}>
          Next month
        </Button>
        <TextField
          size="small"
          label="Search teachers"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        {filters}
      </Stack>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }} role="alert">
          {error}
        </Alert>
      )}
      {!grid && !error && <Typography>Loading attendance…</Typography>}
      {grid && grid.content.length === 0 && (
        <Typography>No teachers are placed in {monthLabel(month)}.</Typography>
      )}
      {grid && grid.content.length > 0 && (
        <>
          <AttendanceGrid
            rows={grid.content}
            canEdit={canMark}
            onCell={openCell}
            onOpenTeacher={setPanelRow}
          />
          <Stack
            direction="row"
            spacing={2}
            sx={{ mt: 1, alignItems: "center" }}
          >
            <Typography variant="body2">
              Showing {from}-{to} of {total}
            </Typography>
            <Button
              disabled={page === 0}
              onClick={() => setPageState({ key, page: page - 1 })}
            >
              Previous page
            </Button>
            <Button
              disabled={to >= total}
              onClick={() => setPageState({ key, page: page + 1 })}
            >
              Next page
            </Button>
          </Stack>
        </>
      )}

      {editing && (
        <MarkDialog
          subject={editing.row.name}
          date={editing.day.date}
          existing={editing.day.mark}
          onSave={(body) =>
            setTeacherMark(
              authFetch,
              editing.row.teacherId,
              editing.day.date,
              body,
            )
          }
          onClear={
            canClear
              ? () =>
                  clearTeacherMark(
                    authFetch,
                    editing.row.teacherId,
                    editing.day.date,
                  )
              : undefined
          }
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            reload();
          }}
        />
      )}
      {panelRow && (
        <TeacherMonthPanel
          teacherId={panelRow.teacherId}
          teacherName={panelRow.name}
          month={month}
          refreshKey={reloadCount}
          onClose={() => setPanelRow(null)}
          extras={panelExtras?.({
            row: panelRow,
            month,
            reload,
            refreshKey: reloadCount,
          })}
        />
      )}
    </Box>
  );
}
