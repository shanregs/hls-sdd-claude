import { useCallback, useEffect, useMemo, useState } from "react";
import { useParams } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  MenuItem,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { useGrantedActions } from "../common/useGrantedActions";
import { cellText, MONTH_STATE_LABELS } from "./format";
import {
  downloadReport,
  getMenu,
  getReport,
  saveReportFile,
  type Menu,
  type MenuItem as ReportMenuItem,
  type Named,
  type ReportFilters,
  type ReportPageData,
  type Row,
  type Section,
} from "./reportsApi";

type State =
  | { kind: "loading" }
  | { kind: "error"; reason: string }
  | { kind: "ready"; menu: Menu; item: ReportMenuItem };

const ALL = "";

function optionsFrom(section: Section | undefined): Named[] {
  return (section?.rows ?? []).map((r) => ({ id: r.id, name: r.label }));
}

/**
 * One generic report screen (spec 014a): the month and the filters the report offers, a banner for the state of the month,
 * the sections with their totals, and the Export button for those who hold the Export grant. Every figure is shown exactly
 * as the server sent it; a missing figure says why instead of showing 0.
 */
export function ReportPage() {
  const { key = "" } = useParams();
  const { authFetch } = useAuth();
  const canExport = useGrantedActions(`/reports/${key}`).has("EXPORT");

  const [state, setState] = useState<State>({ kind: "loading" });
  const [filters, setFilters] = useState<ReportFilters>({});
  const [page, setPage] = useState<ReportPageData | null>(null);
  const [pageError, setPageError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);
  const [teacherOptions, setTeacherOptions] = useState<Named[]>([]);
  const [managerOptions, setManagerOptions] = useState<Named[]>([]);

  useEffect(() => {
    void (async () => {
      const menu = await getMenu(authFetch);
      if (!menu.ok) {
        setState({ kind: "error", reason: menu.reason });
        return;
      }
      const item = menu.data.reports.find((r) => r.key === key);
      if (!item) {
        setState({
          kind: "error",
          reason: "This report is not available to you.",
        });
        return;
      }
      setFilters({ month: item.defaultMonth });
      setState({ kind: "ready", menu: menu.data, item });
    })();
  }, [authFetch, key]);

  const load = useCallback(
    async (wanted: ReportFilters) => {
      setBusy(true);
      setPageError(null);
      const result = await getReport(authFetch, key, wanted);
      setBusy(false);
      if (!result.ok) {
        setPage(null);
        setPageError(result.reason);
        return;
      }
      setPage(result.data);
      if (!wanted.teacherId) {
        setTeacherOptions(
          optionsFrom(result.data.sections.find((s) => s.key === "teachers")),
        );
      }
      if (!wanted.managerId) {
        setManagerOptions(
          optionsFrom(result.data.sections.find((s) => s.key === "managers")),
        );
      }
    },
    [authFetch, key],
  );

  useEffect(() => {
    if (state.kind !== "ready" || !filters.month) return;
    void (async () => {
      await load(filters);
    })();
  }, [state.kind, filters, load]);

  const change = (patch: Partial<ReportFilters>) =>
    setFilters((current) => ({ ...current, ...patch }));

  const exportFile = async () => {
    setExportError(null);
    const result = await downloadReport(authFetch, key, filters);
    if (!result.ok) {
      setExportError(result.reason);
      return;
    }
    saveReportFile(result.data);
  };

  const title = useMemo(
    () => (state.kind === "ready" ? state.item.title : "Report"),
    [state],
  );

  if (state.kind === "loading") {
    return (
      <Stack direction="row" sx={{ gap: 1.5, alignItems: "center", p: 3 }}>
        <CircularProgress size={22} aria-label="Loading the report" />
        <Typography>Loading the report…</Typography>
      </Stack>
    );
  }
  if (state.kind === "error") {
    return (
      <Box sx={{ p: { xs: 2, md: 3 } }}>
        <Alert severity="error">{state.reason}</Alert>
      </Box>
    );
  }

  const { menu, item } = state;
  const offers = new Set(item.filters);

  return (
    <Stack spacing={3} sx={{ p: { xs: 2, md: 3 } }}>
      <Box>
        <Typography variant="h1" sx={{ fontSize: "1.75rem" }}>
          {title}
        </Typography>
        <Typography color="text.secondary">
          Pick a month and, where offered, narrow it down. You see only the
          people and Schools in your area.
        </Typography>
      </Box>

      <Paper variant="outlined" sx={{ p: 2 }}>
        <Stack
          direction="row"
          sx={{ gap: 2, flexWrap: "wrap", alignItems: "flex-start" }}
        >
          <TextField
            label="Month"
            type="month"
            value={filters.month ?? ""}
            onChange={(e) => change({ month: e.target.value })}
            slotProps={{ inputLabel: { shrink: true } }}
            sx={{ minWidth: 200 }}
          />
          {offers.has("ZONE") && menu.zones.length > 0 && (
            <TextField
              select
              label="Zone"
              value={filters.zoneId ?? ALL}
              onChange={(e) =>
                change({
                  zoneId: e.target.value || undefined,
                  schoolId: undefined,
                })
              }
              sx={{ minWidth: 200 }}
            >
              <MenuItem value={ALL}>All Zones</MenuItem>
              {menu.zones.map((z) => (
                <MenuItem key={z.id} value={z.id}>
                  {z.name}
                </MenuItem>
              ))}
            </TextField>
          )}
          {offers.has("SCHOOL") && menu.schools.length > 0 && (
            <TextField
              select
              label="School"
              value={filters.schoolId ?? ALL}
              onChange={(e) =>
                change({ schoolId: e.target.value || undefined })
              }
              sx={{ minWidth: 200 }}
            >
              <MenuItem value={ALL}>All Schools</MenuItem>
              {menu.schools.map((s) => (
                <MenuItem key={s.id} value={s.id}>
                  {s.name}
                </MenuItem>
              ))}
            </TextField>
          )}
          {offers.has("TEACHER") && teacherOptions.length > 0 && (
            <TextField
              select
              label="Teacher"
              value={filters.teacherId ?? ALL}
              onChange={(e) =>
                change({ teacherId: e.target.value || undefined })
              }
              sx={{ minWidth: 200 }}
            >
              <MenuItem value={ALL}>All Teachers</MenuItem>
              {teacherOptions.map((t) => (
                <MenuItem key={t.id} value={t.id}>
                  {t.name}
                </MenuItem>
              ))}
            </TextField>
          )}
          {offers.has("MANAGER") && managerOptions.length > 0 && (
            <TextField
              select
              label="Manager"
              value={filters.managerId ?? ALL}
              onChange={(e) =>
                change({ managerId: e.target.value || undefined })
              }
              sx={{ minWidth: 200 }}
            >
              <MenuItem value={ALL}>All Managers</MenuItem>
              {managerOptions.map((m) => (
                <MenuItem key={m.id} value={m.id}>
                  {m.name}
                </MenuItem>
              ))}
            </TextField>
          )}
          {item.canExport && canExport && (
            <Button
              variant="outlined"
              onClick={() => void exportFile()}
              disabled={busy}
              sx={{ minHeight: 56 }}
            >
              Export CSV
            </Button>
          )}
        </Stack>
        {exportError && (
          <Alert severity="error" sx={{ mt: 2 }}>
            {exportError}
          </Alert>
        )}
      </Paper>

      {busy && (
        <Stack direction="row" sx={{ gap: 1.5, alignItems: "center" }}>
          <CircularProgress size={22} aria-label="Loading the figures" />
          <Typography>Loading the figures…</Typography>
        </Stack>
      )}
      {pageError && <Alert severity="error">{pageError}</Alert>}

      {page && (
        <>
          {item.hasMonthState && (
            <Alert
              severity={
                page.monthState === "OPEN" || page.monthState === "DRAFT"
                  ? "warning"
                  : "info"
              }
              icon={false}
            >
              <Chip
                size="small"
                label={`Month state: ${MONTH_STATE_LABELS[page.monthState]}`}
                sx={{ mr: 1 }}
              />
              {page.notes.join(" ")}
            </Alert>
          )}
          {page.sections.map((section) => (
            <SectionTable key={section.key} section={section} />
          ))}
        </>
      )}
    </Stack>
  );
}

function SectionTable({ section }: { section: Section }) {
  return (
    <Box component="section" aria-label={section.title}>
      <Typography variant="h2" sx={{ fontSize: "1.1rem", mb: 1 }}>
        {section.title}
      </Typography>
      {section.failed ? (
        <Alert severity="warning">
          {section.title} could not be read right now. The other tables are
          still correct.
        </Alert>
      ) : section.rows.length === 0 && !section.totals ? (
        <Alert severity="info">Nothing to show for this month.</Alert>
      ) : (
        <TableContainer component={Paper} variant="outlined">
          <Table size="small" aria-label={section.title}>
            <TableHead>
              <TableRow>
                <TableCell>Name</TableCell>
                {section.columns.map((c) => (
                  <TableCell
                    key={c.key}
                    align={c.unit === "TEXT" ? "left" : "right"}
                  >
                    {c.label}
                  </TableCell>
                ))}
              </TableRow>
            </TableHead>
            <TableBody>
              {section.rows.map((row) => (
                <ReportRow key={row.id} row={row} section={section} />
              ))}
              {section.totals && (
                <ReportRow row={section.totals} section={section} total />
              )}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </Box>
  );
}

function ReportRow({
  row,
  section,
  total = false,
}: {
  row: Row;
  section: Section;
  total?: boolean;
}) {
  return (
    <TableRow
      hover={!total}
      sx={total ? { "& td, & th": { fontWeight: 700 } } : undefined}
    >
      <TableCell component="th" scope="row">
        {row.label}
      </TableCell>
      {row.cells.map((cell, i) => {
        const column = section.columns[i];
        return (
          <TableCell
            key={column?.key ?? i}
            align={column?.unit === "TEXT" ? "left" : "right"}
          >
            {cellText(cell, column?.unit ?? "COUNT")}
          </TableCell>
        );
      })}
    </TableRow>
  );
}
