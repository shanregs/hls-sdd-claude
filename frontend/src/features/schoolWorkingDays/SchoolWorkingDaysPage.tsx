import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import type { GridPaginationModel, GridSortModel } from "@mui/x-data-grid";
import {
  Alert,
  Box,
  Button,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { useGrantedActions } from "../common/useGrantedActions";
import { AddRowDialog } from "./AddRowDialog";
import { CopyYearDialog } from "./CopyYearDialog";
import { DeleteDialog } from "./DeleteDialog";
import { DeletedRowsDialog } from "./DeletedRowsDialog";
import { HistoryDialog } from "./HistoryDialog";
import { toServerSort } from "./serverSort";
import { WorkingDaysGrid } from "./WorkingDaysGrid";
import {
  editWorkingDays,
  listSchoolOptions,
  listWorkingDays,
  type SchoolOption,
  type WorkingDaysRow,
} from "./schoolWorkingDaysApi";
import { figureProblem, formatFigure } from "./workingDaysRules";

const ROUTE = "/master-data/school-working-days";

/** MASTER DATA -> School Working Days: every School's monthly working days in a grid with add, edit and delete. */
export function SchoolWorkingDaysPage() {
  const { authFetch } = useAuth();
  const actions = useGrantedActions(ROUTE);
  const canCreate = actions.has("CREATE");
  const canEdit = actions.has("EDIT");
  const canDelete = actions.has("DELETE");

  // The filters live in the address, so they survive paging, a reload and a shared link.
  const [params, setParams] = useSearchParams();
  const thisYear = new Date().getFullYear();
  const year = Number(params.get("year")) || thisYear;
  const schoolId = params.get("schoolId") ?? "";
  const zoneId = params.get("zoneId") ?? "";
  const q = params.get("q") ?? "";

  const setFilter = useCallback(
    (key: string, value: string) => {
      setParams(
        (previous) => {
          const next = new URLSearchParams(previous);
          if (value) next.set(key, value);
          else next.delete(key);
          return next;
        },
        { replace: true },
      );
    },
    [setParams],
  );

  const [search, setSearch] = useState(q);
  const [yearText, setYearText] = useState(String(year));
  const [paging, setPaging] = useState<GridPaginationModel>({
    page: 0,
    pageSize: 25,
  });
  const [sortModel, setSortModel] = useState<GridSortModel>([]);
  const [rows, setRows] = useState<WorkingDaysRow[]>([]);
  const [total, setTotal] = useState(0);
  const [loadedKey, setLoadedKey] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [reloadCount, setReloadCount] = useState(0);
  const [schools, setSchools] = useState<SchoolOption[]>([]);

  const [editingId, setEditingId] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);
  const [deleting, setDeleting] = useState<WorkingDaysRow | null>(null);
  const [history, setHistory] = useState<WorkingDaysRow | null>(null);
  const [deletedView, setDeletedView] = useState(false);
  const [copying, setCopying] = useState(false);
  // the row a refused add pointed at; edited in place as soon as the grid has loaded it
  const pendingEdit = useRef<string | null>(null);

  const serverSort = toServerSort(sortModel);
  const queryKey = JSON.stringify([
    year,
    schoolId,
    zoneId,
    q,
    serverSort,
    paging.page,
    paging.pageSize,
    reloadCount,
  ]);
  const loading = loadedKey !== queryKey;

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const result = await listSchoolOptions(authFetch);
      if (!cancelled && result.ok) setSchools(result.data);
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch]);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const result = await listWorkingDays(authFetch, {
        year,
        schoolId: schoolId || undefined,
        zoneId: zoneId || undefined,
        q: q || undefined,
        sort: serverSort,
        page: paging.page,
        size: paging.pageSize,
      });
      if (cancelled) return;
      setLoadedKey(queryKey);
      if (result.ok) {
        setError(null);
        setRows(result.data.items);
        setTotal(result.data.total);
        const wanted = pendingEdit.current;
        if (wanted && result.data.items.some((r) => r.id === wanted)) {
          pendingEdit.current = null;
          setEditingId(wanted);
        }
      } else {
        setError(result.reason);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [
    authFetch,
    year,
    schoolId,
    zoneId,
    q,
    serverSort,
    paging.page,
    paging.pageSize,
    queryKey,
  ]);

  // the search box applies a moment after typing stops
  useEffect(() => {
    if (search === q) return;
    const timer = setTimeout(() => {
      setFilter("q", search.trim());
      setPaging((p) => ({ ...p, page: 0 }));
    }, 300);
    return () => clearTimeout(timer);
  }, [search, q, setFilter]);

  const zones = useMemo(() => {
    const seen = new Map<string, string>();
    schools.forEach((s) => {
      if (s.zoneId) seen.set(s.zoneId, s.zoneName);
    });
    return [...seen.entries()].sort((a, b) => a[1].localeCompare(b[1]));
  }, [schools]);

  const reload = () => setReloadCount((c) => c + 1);

  const replaceRow = (row: WorkingDaysRow) =>
    setRows((previous) => previous.map((r) => (r.id === row.id ? row : r)));

  const removeRow = (id: string) => {
    setRows((previous) => previous.filter((r) => r.id !== id));
    setTotal((t) => Math.max(0, t - 1));
  };

  const saveEdit = async (
    row: WorkingDaysRow,
    text: string,
  ): Promise<string | null> => {
    const invalid = figureProblem(text, row.year, row.month);
    if (invalid) return invalid;
    const result = await editWorkingDays(authFetch, row, Number(text));
    if (result.ok) {
      replaceRow(result.data);
      setEditingId(null);
      setNotice(null);
      return null;
    }
    if (result.code === "STALE" && result.current) {
      replaceRow(result.current);
      return `Changed by someone else. The current figure is ${formatFigure(result.current.workingDays)}; save again to replace it.`;
    }
    if (result.code === "ROW_DELETED") {
      removeRow(row.id);
      setEditingId(null);
      setNotice("That figure was deleted by someone else.");
      return null;
    }
    return result.reason;
  };

  const applyYear = (text: string) => {
    setYearText(text);
    if (/^\d{4}$/.test(text)) {
      setFilter("year", text);
      setPaging((p) => ({ ...p, page: 0 }));
    }
  };

  const selectedSchool = schoolId || "";

  return (
    <Box sx={{ maxWidth: "100%" }}>
      <Stack
        direction="row"
        sx={{
          alignItems: "center",
          justifyContent: "space-between",
          flexWrap: "wrap",
          gap: 1,
          mb: 2,
        }}
      >
        <Typography variant="h5" component="h1">
          School Working Days
        </Typography>
        <Stack direction="row" spacing={1} sx={{ flexWrap: "wrap", gap: 1 }}>
          <Button onClick={() => setDeletedView(true)}>Deleted rows</Button>
          {canCreate && (
            <Button onClick={() => setCopying(true)}>Copy year</Button>
          )}
          {canCreate && (
            <Button variant="contained" onClick={() => setAdding(true)}>
              Add working days
            </Button>
          )}
        </Stack>
      </Stack>

      <Stack direction={{ xs: "column", sm: "row" }} spacing={2} sx={{ mb: 2 }}>
        <TextField
          label="Year"
          size="small"
          value={yearText}
          onChange={(e) => applyYear(e.target.value)}
          slotProps={{ htmlInput: { inputMode: "numeric", maxLength: 4 } }}
          sx={{ width: { sm: 110 } }}
        />
        <TextField
          select
          label="School"
          size="small"
          value={selectedSchool}
          onChange={(e) => {
            setFilter("schoolId", e.target.value);
            setPaging((p) => ({ ...p, page: 0 }));
          }}
          sx={{ minWidth: 200 }}
        >
          <MenuItem value="">All Schools</MenuItem>
          {schools.map((s) => (
            <MenuItem key={s.id} value={s.id}>
              {s.name}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          select
          label="Zone"
          size="small"
          value={zoneId}
          onChange={(e) => {
            setFilter("zoneId", e.target.value);
            setPaging((p) => ({ ...p, page: 0 }));
          }}
          sx={{ minWidth: 160 }}
        >
          <MenuItem value="">All zones</MenuItem>
          {zones.map(([id, name]) => (
            <MenuItem key={id} value={id}>
              {name}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          label="Search School name"
          size="small"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          sx={{ minWidth: 200 }}
        />
      </Stack>

      {error && (
        <Alert
          severity="error"
          role="alert"
          sx={{ mb: 2 }}
          action={
            <Button color="inherit" size="small" onClick={reload}>
              Retry
            </Button>
          }
        >
          {error}
        </Alert>
      )}
      {notice && (
        <Alert severity="info" sx={{ mb: 2 }} onClose={() => setNotice(null)}>
          {notice}
        </Alert>
      )}

      <WorkingDaysGrid
        rows={rows}
        total={total}
        loading={loading}
        paging={paging}
        onPagingChange={setPaging}
        sortModel={sortModel}
        onSortChange={(model) => {
          setSortModel(model);
          setPaging((p) => ({ ...p, page: 0 }));
        }}
        canCreate={canCreate}
        canEdit={canEdit}
        canDelete={canDelete}
        editingId={editingId}
        onStartEdit={(row) => setEditingId(row.id)}
        onCancelEdit={() => setEditingId(null)}
        onSaveEdit={saveEdit}
        onDelete={setDeleting}
        onHistory={setHistory}
      />

      {adding && (
        <AddRowDialog
          schools={schools}
          year={year}
          onClose={() => setAdding(false)}
          onSaved={(row) => {
            setAdding(false);
            setNotice(null);
            if (row.year !== year) {
              setFilter("year", String(row.year));
              setYearText(String(row.year));
            }
            reload();
          }}
          onEditExisting={(row) => {
            setAdding(false);
            setFilter("year", String(row.year));
            setYearText(String(row.year));
            setFilter("schoolId", row.schoolId);
            pendingEdit.current = row.id;
            reload();
          }}
        />
      )}
      {deleting && (
        <DeleteDialog
          row={deleting}
          onClose={() => setDeleting(null)}
          onDeleted={(alreadyGone) => {
            removeRow(deleting.id);
            setDeleting(null);
            setNotice(
              alreadyGone
                ? "That figure was already deleted by someone else."
                : "Deleted. The month now uses the calendar-derived count.",
            );
          }}
          onStale={(current) => {
            replaceRow(current);
            setDeleting(null);
            setNotice(
              `That figure was changed by someone else; the current figure is ${formatFigure(current.workingDays)}. Delete it again if you still want to.`,
            );
          }}
        />
      )}
      {history && (
        <HistoryDialog row={history} onClose={() => setHistory(null)} />
      )}
      {deletedView && (
        <DeletedRowsDialog
          schools={schools}
          schoolId={selectedSchool}
          year={year}
          onClose={() => setDeletedView(false)}
        />
      )}
      {copying && (
        <CopyYearDialog
          schools={schools}
          year={year}
          schoolId={selectedSchool}
          onClose={() => setCopying(false)}
          onCopied={reload}
        />
      )}
    </Box>
  );
}
