import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  Checkbox,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  FormControlLabel,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { useGrantedActions } from "../common/useGrantedActions";
import { MonthField } from "./MonthField";
import {
  getYear,
  listSchoolOptions,
  saveYear,
  type SchoolOption,
} from "./schoolWorkingDaysApi";
import { FINALISED_NOTICE, figureProblem, monthName } from "./workingDaysRules";
import {
  applyResult,
  buildRequest,
  dirtyMonths,
  fillSameFigure,
  hasEmptyMonths,
  fillWithCalendarCounts,
  initialState,
  localProblem,
  setTyped,
  type YearEntryState,
} from "./yearEntryState";

const ROUTE = "/master-data/school-working-days";
const WINDOW = 5;

/** A School or year change waiting for the user to confirm discarding what they typed. */
interface PendingChange {
  schoolId: string;
  year: number;
}

/**
 * MASTER DATA -> School Working Days -> Enter a year (spec 038): one School and one year, twelve month fields and one
 * Save. Months that cannot be saved are reported by name and keep what the user typed; the others are stored.
 */
export function YearEntryPage() {
  const { authFetch } = useAuth();
  // a renewed sign-in token gives a new authFetch; the load below must not rerun for it and wipe what the user typed
  const authFetchRef = useRef(authFetch);
  useEffect(() => {
    authFetchRef.current = authFetch;
  }, [authFetch]);
  const navigate = useNavigate();
  const actions = useGrantedActions(ROUTE);
  const canCreate = actions.has("CREATE");
  const canEdit = actions.has("EDIT");

  const thisYear = new Date().getFullYear();
  const years = useMemo(
    () =>
      Array.from({ length: WINDOW * 2 + 1 }, (_, i) => thisYear - WINDOW + i),
    [thisYear],
  );
  const [params, setParams] = useSearchParams();
  const requestedYear = Number(params.get("year"));
  const year = years.includes(requestedYear) ? requestedYear : thisYear;
  const schoolId = params.get("schoolId") ?? "";

  const [schools, setSchools] = useState<SchoolOption[]>([]);
  const [state, setState] = useState<YearEntryState | null>(null);
  const [loadedKey, setLoadedKey] = useState<string | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [reloadCount, setReloadCount] = useState(0);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [pending, setPending] = useState<PendingChange | null>(null);
  const [leaving, setLeaving] = useState(false);
  const [sameFigureOpen, setSameFigureOpen] = useState(false);
  const [sameFigureText, setSameFigureText] = useState("");
  const [replaceAll, setReplaceAll] = useState(false);
  const [skippedNotice, setSkippedNotice] = useState<string | null>(null);
  const [focusToken, setFocusToken] = useState(0);

  const queryKey = JSON.stringify([schoolId, year, reloadCount]);
  const loading = Boolean(schoolId) && loadedKey !== queryKey;
  const dirty = state ? dirtyMonths(state).length > 0 : false;

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
    if (!schoolId) return;
    let cancelled = false;
    (async () => {
      const result = await getYear(authFetchRef.current, schoolId, year);
      if (cancelled) return;
      setLoadedKey(queryKey);
      if (result.ok) {
        setLoadError(null);
        setState(initialState(result.data));
        setSaveError(null);
        setSkippedNotice(null);
      } else {
        setLoadError(result.reason);
        setState(null);
      }
    })();
    return () => {
      cancelled = true;
    };
    // queryKey covers schoolId, year and reloadCount
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [queryKey]);

  // closing or reloading the tab with unsaved changes (the left menu cannot be intercepted, plan research section 9)
  useEffect(() => {
    if (!dirty) return;
    const warn = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty]);

  // focus the first month the last Save refused
  useEffect(() => {
    if (focusToken === 0 || !state) return;
    const first = Object.keys(state.failed)
      .map(Number)
      .sort((a, b) => a - b)[0];
    if (first !== undefined) document.getElementById(`month-${first}`)?.focus();
    // only when a Save has just ended
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [focusToken]);

  const writeParams = useCallback(
    (next: PendingChange) => {
      setParams(
        (previous) => {
          const copy = new URLSearchParams(previous);
          if (next.schoolId) copy.set("schoolId", next.schoolId);
          else copy.delete("schoolId");
          copy.set("year", String(next.year));
          return copy;
        },
        { replace: true },
      );
    },
    [setParams],
  );

  const requestChange = (next: PendingChange) => {
    if (dirty) setPending(next);
    else writeParams(next);
  };

  const gridPath = () => {
    const query = new URLSearchParams({ year: String(year) });
    if (schoolId) query.set("schoolId", schoolId);
    return `${ROUTE}?${query.toString()}`;
  };

  const goBack = () => {
    if (dirty) setLeaving(true);
    else navigate(gridPath());
  };

  const toSend = state ? buildRequest(state) : [];

  const save = async () => {
    if (!state || toSend.length === 0) return;
    setSaving(true);
    setSaveError(null);
    const result = await saveYear(authFetch, {
      schoolId,
      year,
      months: toSend,
    });
    setSaving(false);
    if (result.ok) {
      setState((s) => (s ? applyResult(s, result.data) : s));
      setSkippedNotice(null);
      setFocusToken((n) => n + 1);
    } else {
      // nothing is shown as saved and every typed value stays
      setSaveError(result.reason);
    }
  };

  const useCalendar = () => {
    setState((s) => (s ? fillWithCalendarCounts(s) : s));
    setSkippedNotice(null);
  };

  const applySameFigure = () => {
    if (!state) return;
    const { state: filled, skipped } = fillSameFigure(
      state,
      sameFigureText,
      canReplace && replaceAll,
    );
    setState(filled);
    setSkippedNotice(
      skipped.length > 0
        ? `Not filled (${sameFigureText.trim()} does not fit): ${skipped.map(monthName).join(", ")}.`
        : null,
    );
    setSameFigureOpen(false);
    setReplaceAll(false);
  };

  const allStored = state
    ? state.loaded.months.every((m) => m.workingDays !== null)
    : false;
  // a figure may be replaced across the year only for a future year, and only by a user who may edit
  const canReplace = canEdit && year > thisYear;
  const anyEmpty = state ? hasEmptyMonths(state) : false;
  const readOnlyFor = (stored: boolean) => (stored ? !canEdit : !canCreate);
  const mayChangeAny = canCreate || canEdit;
  const sameFigureProblem =
    sameFigureText.trim() === ""
      ? "Enter a number of working days."
      : state
        ? // valid for at least one month of the year, so the helper has something to fill
          state.loaded.months.every(
            (m) =>
              figureProblem(sameFigureText, state.loaded.year, m.month) !==
              null,
          )
          ? "Enter 0 to 31, in half days."
          : null
        : null;

  return (
    <Box sx={{ maxWidth: "100%", minWidth: 0 }}>
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
          Enter a year of working days
        </Typography>
        <Button onClick={goBack}>Back to the grid</Button>
      </Stack>

      <Stack direction={{ xs: "column", sm: "row" }} spacing={2} sx={{ mb: 2 }}>
        <TextField
          select
          label="School"
          size="small"
          value={schools.some((s) => s.id === schoolId) ? schoolId : ""}
          onChange={(e) => requestChange({ schoolId: e.target.value, year })}
          sx={{ minWidth: { sm: 260 } }}
        >
          <MenuItem value="">Choose a School</MenuItem>
          {schools.map((s) => (
            <MenuItem key={s.id} value={s.id}>
              {s.name}
              {s.active ? "" : " (inactive)"}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          select
          label="Year"
          size="small"
          value={year}
          onChange={(e) =>
            requestChange({ schoolId, year: Number(e.target.value) })
          }
          sx={{ width: { sm: 130 } }}
        >
          {years.map((y) => (
            <MenuItem key={y} value={y}>
              {y}
            </MenuItem>
          ))}
        </TextField>
      </Stack>

      {!schoolId && (
        <Alert severity="info">Choose a School to see its twelve months.</Alert>
      )}
      {schoolId && loading && (
        <Box sx={{ display: "flex", justifyContent: "center", py: 4 }}>
          <CircularProgress aria-label="Loading the year" />
        </Box>
      )}
      {schoolId && !loading && loadError && (
        <Alert
          severity="error"
          action={
            <Button
              color="inherit"
              onClick={() => setReloadCount((n) => n + 1)}
            >
              Retry
            </Button>
          }
        >
          {loadError}
        </Alert>
      )}

      {schoolId && !loading && !loadError && state && (
        <>
          <Typography variant="subtitle1" sx={{ mb: 1 }}>
            {state.loaded.schoolName}
            {state.loaded.zoneName ? ` · ${state.loaded.zoneName}` : ""} ·{" "}
            {year}
          </Typography>

          {mayChangeAny && (
            <Stack
              direction="row"
              spacing={1}
              sx={{ mb: 2, flexWrap: "wrap", gap: 1 }}
            >
              {canCreate && (
                <Button onClick={useCalendar} disabled={!anyEmpty}>
                  Use calendar counts
                </Button>
              )}
              {canCreate && (
                <Button
                  onClick={() => {
                    setSameFigureText("");
                    setReplaceAll(false);
                    setSameFigureOpen(true);
                  }}
                  disabled={!anyEmpty && !canReplace}
                >
                  Same figure for all empty months
                </Button>
              )}
            </Stack>
          )}
          {mayChangeAny && allStored && (
            <Alert severity="info" sx={{ mb: 2 }}>
              Every month already has a figure, so there is nothing to fill.
              {canReplace
                ? ' Use "Same figure" and tick "Also replace months that already have a figure" to set them all again.'
                : " Click a figure to replace it, or pick a year with empty months."}
            </Alert>
          )}
          {skippedNotice && (
            <Alert severity="info" sx={{ mb: 2 }}>
              {skippedNotice}
            </Alert>
          )}

          <Box
            component="ul"
            aria-label={`Working days by month, ${year}`}
            sx={{
              p: 0,
              m: 0,
              display: "grid",
              gap: 1.5,
              gridTemplateColumns: {
                xs: "minmax(0, 1fr)",
                sm: "repeat(2, minmax(0, 1fr))",
              },
            }}
          >
            {state.loaded.months.map((m) => (
              <MonthField
                key={m.month}
                year={year}
                state={m}
                text={state.typed[m.month] ?? ""}
                onChange={(text) =>
                  setState((s) => (s ? setTyped(s, m.month, text) : s))
                }
                readOnly={readOnlyFor(m.workingDays !== null)}
                problem={localProblem(state, m.month)}
                failed={state.failed[m.month] ?? null}
                helperFilled={state.helperFilled.includes(m.month)}
                onUseStored={() =>
                  setState((s) =>
                    s ? setTyped(s, m.month, String(m.workingDays ?? "")) : s,
                  )
                }
              />
            ))}
          </Box>

          <Stack spacing={1.5} sx={{ mt: 2 }}>
            <Alert severity="info" icon={false}>
              {FINALISED_NOTICE}
            </Alert>
            {saveError && <Alert severity="error">{saveError}</Alert>}
            <Box role="status" aria-live="polite">
              {state.summary && (
                <Alert
                  severity={state.summary.notSaved > 0 ? "warning" : "success"}
                >
                  {state.summary.created} created, {state.summary.updated}{" "}
                  updated, {state.summary.unchanged} unchanged
                  {state.summary.notSaved > 0
                    ? `, ${state.summary.notSaved} not saved: ${Object.keys(
                        state.failed,
                      )
                        .map(Number)
                        .sort((a, b) => a - b)
                        .map(monthName)
                        .join(", ")}`
                    : ""}
                  .
                </Alert>
              )}
            </Box>
            {mayChangeAny && (
              <Stack direction="row" spacing={2} sx={{ alignItems: "center" }}>
                <Button
                  variant="contained"
                  onClick={save}
                  disabled={saving || toSend.length === 0}
                >
                  {saving ? "Saving…" : "Save"}
                </Button>
                {toSend.length === 0 && (
                  <Typography variant="body2" color="text.secondary">
                    {dirty
                      ? "Fix the figures marked in red."
                      : "Nothing to save"}
                  </Typography>
                )}
              </Stack>
            )}
          </Stack>
        </>
      )}

      <Dialog open={pending !== null} onClose={() => setPending(null)}>
        <DialogTitle>Discard your changes?</DialogTitle>
        <DialogContent>
          <DialogContentText>
            You have figures that are not saved. Changing the School or year
            discards them.
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPending(null)}>Keep editing</Button>
          <Button
            color="error"
            onClick={() => {
              if (pending) writeParams(pending);
              setPending(null);
            }}
          >
            Discard
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={leaving} onClose={() => setLeaving(false)}>
        <DialogTitle>Leave without saving?</DialogTitle>
        <DialogContent>
          <DialogContentText>
            You have figures that are not saved. Going back to the grid discards
            them.
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setLeaving(false)}>Keep editing</Button>
          <Button color="error" onClick={() => navigate(gridPath())}>
            Discard and go back
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog
        open={sameFigureOpen}
        onClose={() => setSameFigureOpen(false)}
        fullWidth
        maxWidth="xs"
      >
        <DialogTitle>Same figure for all empty months</DialogTitle>
        <DialogContent>
          <TextField
            autoFocus
            fullWidth
            margin="dense"
            label="Working days"
            value={sameFigureText}
            onChange={(e) => setSameFigureText(e.target.value)}
            error={sameFigureText !== "" && sameFigureProblem !== null}
            helperText={
              sameFigureText !== "" && sameFigureProblem
                ? sameFigureProblem
                : "Fills empty months only. Nothing is saved until you press Save."
            }
            slotProps={{ htmlInput: { inputMode: "decimal" } }}
          />
          {canReplace && (
            <FormControlLabel
              control={
                <Checkbox
                  checked={replaceAll}
                  onChange={(e) => setReplaceAll(e.target.checked)}
                />
              }
              label="Also replace months that already have a figure"
            />
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setSameFigureOpen(false)}>Cancel</Button>
          <Button
            variant="contained"
            onClick={applySameFigure}
            disabled={sameFigureProblem !== null}
          >
            Fill
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
