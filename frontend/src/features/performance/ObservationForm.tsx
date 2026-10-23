import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link as RouterLink, useParams } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Link,
  List,
  ListItem,
  ListItemText,
  MenuItem,
  Paper,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { useGrantedActions } from "../common/useGrantedActions";
import type { ApiResult } from "../common/masterDataApi";
import { formatDate } from "../teachers/formatters";
import { ResultChip } from "./Chips";
import {
  OBSERVATION_STATUS_LABELS,
  PERFORMANCE_ROUTE,
  RESULT_LABELS,
  correctObservation,
  downloadAttachment,
  getObservation,
  getObservationVersions,
  getOptions,
  markNotHeld,
  previewRatings,
  removeAttachment,
  saveObservation,
  submitObservation,
  uploadAttachment,
  type ObservationForm as FormBody,
  type ObservationView,
  type Options,
  type Preview,
  type Result,
} from "./performanceApi";
import { ReasonDialog } from "./ReasonDialog";

const MAX_FILES = 5;
const MAX_TEXT = 2000;

type Loaded =
  | { kind: "loading" }
  | { kind: "error"; reason: string }
  | { kind: "ready"; view: ObservationView };

/**
 * One observation (spec 026 US1): the form while it is a Draft, the finished record afterwards. A Submitted observation is
 * changed only by a correction, which asks for a reason and keeps the earlier version.
 */
export function ObservationForm() {
  const { id } = useParams();
  const { authFetch } = useAuth();
  const [loaded, setLoaded] = useState<Loaded>({ kind: "loading" });

  const load = useCallback(async () => {
    if (!id) return;
    const result = await getObservation(authFetch, id);
    setLoaded(
      result.ok
        ? { kind: "ready", view: result.data }
        : { kind: "error", reason: result.reason },
    );
  }, [authFetch, id]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- initial fetch
    void load();
  }, [load]);

  if (loaded.kind === "loading") {
    return <CircularProgress aria-label="Loading observation" />;
  }
  if (loaded.kind === "error") {
    return <Alert severity="error">{loaded.reason}</Alert>;
  }
  return (
    <ObservationBody
      key={`${loaded.view.id}-${loaded.view.version}`}
      view={loaded.view}
      onChanged={(view) => setLoaded({ kind: "ready", view })}
    />
  );
}

function ObservationBody({
  view,
  onChanged,
}: {
  view: ObservationView;
  onChanged: (view: ObservationView) => void;
}) {
  const { authFetch } = useAuth();
  const granted = useGrantedActions(PERFORMANCE_ROUTE);
  const [options, setOptions] = useState<Options | null>(null);
  const [correcting, setCorrecting] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [notHeld, setNotHeld] = useState(false);
  const [removing, setRemoving] = useState<string | null>(null);
  const [versions, setVersions] = useState<ObservationView[] | null>(null);
  const [guide, setGuide] = useState<Preview | null>(null);
  const fileInput = useRef<HTMLInputElement>(null);

  const [observedOn, setObservedOn] = useState(view.observedOn);
  const [className, setClassName] = useState(view.className ?? "");
  const [subject, setSubject] = useState(view.subject ?? "");
  const [duration, setDuration] = useState(
    view.durationMinutes === null ? "" : String(view.durationMinutes),
  );
  const [ratings, setRatings] = useState<Record<string, string>>(
    Object.fromEntries(
      Object.entries(view.ratings).map(([area, r]) => [
        area,
        r === null ? "" : String(r),
      ]),
    ),
  );
  const [overall, setOverall] = useState<string>(view.overallResult ?? "");
  const [comments, setComments] = useState(view.comments ?? "");
  const [strengths, setStrengths] = useState(view.strengths ?? "");
  const [improvements, setImprovements] = useState(view.improvements ?? "");
  const [principalComments, setPrincipalComments] = useState(
    view.principalComments ?? "",
  );
  const [permissionBy, setPermissionBy] = useState(view.permissionBy ?? "");
  const [permissionWay, setPermissionWay] = useState(view.permissionWay ?? "");
  const [permissionOn, setPermissionOn] = useState(view.permissionOn ?? "");
  const [reason, setReason] = useState("");

  const editable = view.actions.edit || correcting;
  const management = granted.has("EDIT");

  useEffect(() => {
    (async () => {
      const result = await getOptions(authFetch);
      if (result.ok) setOptions(result.data);
    })();
  }, [authFetch]);

  const rated = useMemo(() => {
    const out: Record<string, number> = {};
    for (const [area, value] of Object.entries(ratings)) {
      if (value) out[area] = Number(value);
    }
    return out;
  }, [ratings]);

  // the average and the suggested result are a guide only; the observer chooses the overall result
  useEffect(() => {
    if (!editable) return;
    let cancelled = false;
    (async () => {
      const result = await previewRatings(authFetch, rated);
      if (!cancelled && result.ok) setGuide(result.data);
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch, rated, editable]);

  function body(): FormBody {
    const way = permissionWay || null;
    const on = permissionOn || null;
    return {
      observedOn: observedOn || null,
      className: className.trim() || null,
      subject: subject.trim() || null,
      durationMinutes: duration ? Number(duration) : null,
      ratings: rated,
      overallResult: (overall || null) as Result | null,
      comments: comments.trim() || null,
      strengths: strengths.trim() || null,
      improvements: improvements.trim() || null,
      principalComments: principalComments.trim() || null,
      permission:
        permissionBy.trim() || way || on
          ? { by: permissionBy.trim(), way, on }
          : null,
      version: view.version,
    };
  }

  function invalid(forSubmit: boolean): string | null {
    if (duration && (Number(duration) < 5 || Number(duration) > 240)) {
      return "The duration must be 5 to 240 minutes.";
    }
    if (
      [comments, strengths, improvements, principalComments].some(
        (t) => t.length > MAX_TEXT,
      )
    ) {
      return `Each text can be at most ${MAX_TEXT} characters.`;
    }
    if ((permissionWay || permissionOn) && !permissionBy.trim()) {
      return "Say who gave the permission, or clear the permission details.";
    }
    if (!forSubmit) return null;
    if (Object.keys(rated).length === 0)
      return "Rate at least one teaching area.";
    if (!overall) return "Choose the overall result.";
    if (!strengths.trim() && !improvements.trim()) {
      return "Write the strengths or the areas for improvement.";
    }
    return null;
  }

  async function run(
    forSubmit: boolean,
    call: () => Promise<ApiResult<ObservationView>>,
  ) {
    const message = invalid(forSubmit);
    if (message) {
      setProblem(message);
      return;
    }
    setProblem(null);
    setBusy(true);
    const result = await call();
    setBusy(false);
    if (result.ok) onChanged(result.data);
    else setProblem(result.reason);
  }

  async function download(fileId: string, name: string) {
    const result = await downloadAttachment(authFetch, view.id, fileId);
    if (!result.ok) {
      setProblem(result.reason);
      return;
    }
    const url = URL.createObjectURL(result.data);
    const link = document.createElement("a");
    link.href = url;
    link.download = name;
    link.click();
    URL.revokeObjectURL(url);
  }

  async function attach(file: File) {
    setProblem(null);
    setBusy(true);
    const result = await uploadAttachment(authFetch, view.id, file);
    setBusy(false);
    if (!result.ok) {
      setProblem(result.reason);
      return;
    }
    const fresh = await getObservation(authFetch, view.id);
    if (fresh.ok) onChanged(fresh.data);
  }

  async function showVersions() {
    const result = await getObservationVersions(authFetch, view.id);
    if (result.ok) setVersions(result.data);
    else setProblem(result.reason);
  }

  const ratingOptions = ["1", "2", "3", "4", "5"];

  return (
    <Box>
      <Stack
        direction={{ xs: "column", sm: "row" }}
        spacing={1}
        sx={{ alignItems: { sm: "center" }, mb: 2 }}
      >
        <Typography variant="h5" component="h1">
          Observation of {view.teacherName}
        </Typography>
        <Chip
          size="small"
          label={OBSERVATION_STATUS_LABELS[view.status] ?? view.status}
        />
        {view.versionNo > 1 && (
          <Chip size="small" label={`Version ${view.versionNo}`} />
        )}
        {view.principalDeclined && (
          <Chip size="small" color="warning" label="Principal declined" />
        )}
        <ResultChip result={view.overallResult} />
      </Stack>
      <Typography color="text.secondary" sx={{ mb: 2 }}>
        <Link
          component={RouterLink}
          to={`${PERFORMANCE_ROUTE}/teachers/${view.teacherId}`}
        >
          {view.teacherName}
        </Link>
        {" at "}
        {view.schoolName}, observed by {view.observerName} on{" "}
        {formatDate(view.observedOn)}
      </Typography>

      {problem && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {problem}
        </Alert>
      )}
      {view.nextStep && view.issueId && (
        <Alert severity="info" sx={{ mb: 2 }}>
          {view.nextStep === "REFERRAL"
            ? "This result was Unsuitable. "
            : "This result needs improvement. "}
          <Link
            component={RouterLink}
            to={`${PERFORMANCE_ROUTE}/issues/${view.issueId}`}
          >
            {view.nextStep === "REFERRAL"
              ? "Record a referral"
              : "Record a corrective action"}
          </Link>
        </Alert>
      )}
      {view.status === "NOT_HELD" && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          The visit did not take place: {view.notHeldReasonLabel}
          {view.notHeldText ? ` - ${view.notHeldText}` : ""}
        </Alert>
      )}
      {view.correctionReason && (
        <Alert severity="info" sx={{ mb: 2 }}>
          Corrected: {view.correctionReason}
        </Alert>
      )}

      {view.status !== "NOT_HELD" && (
        <Paper variant="outlined" sx={{ p: 2, mb: 2 }}>
          <Stack spacing={2}>
            <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
              <TextField
                label="Date of the observation"
                type="date"
                value={observedOn}
                onChange={(e) => setObservedOn(e.target.value)}
                disabled={!editable}
                slotProps={{ inputLabel: { shrink: true } }}
              />
              <TextField
                label="Class"
                value={className}
                onChange={(e) => setClassName(e.target.value)}
                disabled={!editable}
                slotProps={{ htmlInput: { maxLength: 100 } }}
              />
              <TextField
                label="Subject"
                value={subject}
                onChange={(e) => setSubject(e.target.value)}
                disabled={!editable}
                slotProps={{ htmlInput: { maxLength: 100 } }}
              />
              <TextField
                label="Duration (minutes)"
                type="number"
                value={duration}
                onChange={(e) => setDuration(e.target.value)}
                disabled={!editable}
                slotProps={{ htmlInput: { min: 5, max: 240 } }}
              />
            </Stack>

            <Typography variant="h6" component="h2">
              Ratings
            </Typography>
            <Box
              sx={{
                display: "grid",
                gridTemplateColumns: { xs: "1fr", sm: "1fr 1fr" },
                gap: 2,
              }}
            >
              {(options?.areas ?? []).map((area) => (
                <TextField
                  key={area.value}
                  select
                  label={area.label}
                  value={ratings[area.value] ?? ""}
                  onChange={(e) =>
                    setRatings({ ...ratings, [area.value]: e.target.value })
                  }
                  disabled={!editable}
                >
                  <MenuItem value="">Not observed</MenuItem>
                  {ratingOptions.map((r) => (
                    <MenuItem key={r} value={r}>
                      {r}
                    </MenuItem>
                  ))}
                </TextField>
              ))}
            </Box>
            {!options && (
              <CircularProgress size={20} aria-label="Loading areas" />
            )}
            <Typography variant="body2" aria-live="polite">
              Average:{" "}
              {editable
                ? (guide?.average ?? "none yet")
                : (view.averageRating ?? "none")}
              {(editable ? guide?.suggestedResult : view.suggestedResult) &&
                `. Suggested result: ${
                  RESULT_LABELS[
                    (editable
                      ? guide?.suggestedResult
                      : view.suggestedResult) as Result
                  ]
                } (a guide only; you choose the result).`}
            </Typography>

            <TextField
              select
              label="Overall result"
              value={overall}
              onChange={(e) => setOverall(e.target.value)}
              disabled={!editable}
              sx={{ maxWidth: 280 }}
            >
              {Object.entries(RESULT_LABELS).map(([value, label]) => (
                <MenuItem key={value} value={value}>
                  {label}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              label="Strengths"
              value={strengths}
              onChange={(e) => setStrengths(e.target.value)}
              disabled={!editable}
              multiline
              minRows={2}
              slotProps={{ htmlInput: { maxLength: MAX_TEXT } }}
            />
            <TextField
              label="Areas for improvement"
              value={improvements}
              onChange={(e) => setImprovements(e.target.value)}
              disabled={!editable}
              multiline
              minRows={2}
              slotProps={{ htmlInput: { maxLength: MAX_TEXT } }}
            />
            <TextField
              label="Observer comments"
              value={comments}
              onChange={(e) => setComments(e.target.value)}
              disabled={!editable}
              multiline
              minRows={2}
              slotProps={{ htmlInput: { maxLength: MAX_TEXT } }}
            />
            <TextField
              label="Principal comments"
              value={principalComments}
              onChange={(e) => setPrincipalComments(e.target.value)}
              disabled={!editable}
              multiline
              minRows={2}
              slotProps={{ htmlInput: { maxLength: MAX_TEXT } }}
            />

            <Typography variant="h6" component="h2">
              Principal permission
            </Typography>
            {editable ? (
              <>
                <Typography variant="body2" color="text.secondary">
                  Optional. Leave it blank if permission was not given; it never
                  stops you submitting.
                </Typography>
                <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
                  <TextField
                    label="Given by"
                    value={permissionBy}
                    onChange={(e) => setPermissionBy(e.target.value)}
                    helperText={
                      view.defaultPrincipalName && !permissionBy
                        ? `The School's Principal: ${view.defaultPrincipalName}`
                        : undefined
                    }
                  />
                  <TextField
                    select
                    label="How it was given"
                    value={permissionWay}
                    onChange={(e) => setPermissionWay(e.target.value)}
                    sx={{ minWidth: 180 }}
                  >
                    <MenuItem value="">Not recorded</MenuItem>
                    {(options?.permissionWays ?? []).map((w) => (
                      <MenuItem key={w.value} value={w.value}>
                        {w.label}
                      </MenuItem>
                    ))}
                  </TextField>
                  <TextField
                    label="Date given"
                    type="date"
                    value={permissionOn}
                    onChange={(e) => setPermissionOn(e.target.value)}
                    slotProps={{ inputLabel: { shrink: true } }}
                  />
                  {view.defaultPrincipalName && !permissionBy && (
                    <Button
                      onClick={() =>
                        setPermissionBy(view.defaultPrincipalName!)
                      }
                    >
                      Use Principal name
                    </Button>
                  )}
                </Stack>
              </>
            ) : view.permissionRecorded ? (
              <Typography>
                Given by {view.permissionBy}
                {view.permissionWay
                  ? ` (${view.permissionWay.toLowerCase().replace("_", " ")})`
                  : ""}
                {view.permissionOn
                  ? ` on ${formatDate(view.permissionOn)}`
                  : ""}
              </Typography>
            ) : (
              <Typography color="text.secondary">
                {view.permissionLabel ?? "Permission not recorded"}
              </Typography>
            )}
          </Stack>
        </Paper>
      )}

      {correcting && (
        <TextField
          label="Reason for the correction"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          required
          fullWidth
          multiline
          minRows={2}
          sx={{ mb: 2 }}
        />
      )}

      <Typography variant="h6" component="h2">
        Files
      </Typography>
      {view.attachments.length === 0 ? (
        <Typography color="text.secondary" sx={{ mb: 1 }}>
          No files yet.
        </Typography>
      ) : (
        <List dense aria-label="Observation files">
          {view.attachments.map((f) => (
            <ListItem
              key={f.fileId}
              secondaryAction={
                view.actions.attach && granted.has("EDIT") ? (
                  <Button size="small" onClick={() => setRemoving(f.fileId)}>
                    Remove
                  </Button>
                ) : undefined
              }
            >
              <ListItemText
                primary={
                  <Link
                    component="button"
                    type="button"
                    onClick={() => void download(f.fileId, f.name)}
                  >
                    {f.name}
                  </Link>
                }
                secondary={`${Math.ceil(f.sizeBytes / 1024)} KB`}
              />
            </ListItem>
          ))}
        </List>
      )}

      <Stack direction="row" spacing={1} sx={{ flexWrap: "wrap", my: 2 }}>
        {view.actions.attach &&
          granted.has("EDIT") &&
          view.attachments.length < MAX_FILES && (
            <Button
              variant="outlined"
              disabled={busy}
              onClick={() => fileInput.current?.click()}
            >
              Attach file
            </Button>
          )}
        {view.actions.edit && granted.has("EDIT") && (
          <Button
            variant="outlined"
            disabled={busy}
            onClick={() =>
              void run(false, () => saveObservation(authFetch, view.id, body()))
            }
          >
            Save draft
          </Button>
        )}
        {view.actions.submit && granted.has("CREATE") && (
          <Button
            variant="contained"
            disabled={busy}
            onClick={() =>
              void run(true, () =>
                submitObservation(authFetch, view.id, body()),
              )
            }
          >
            Submit observation
          </Button>
        )}
        {view.actions.notHeld && granted.has("CREATE") && (
          <Button
            variant="outlined"
            disabled={busy}
            onClick={() => setNotHeld(true)}
          >
            Visit not held
          </Button>
        )}
        {view.actions.correct && management && !correcting && (
          <Button variant="outlined" onClick={() => setCorrecting(true)}>
            Correct
          </Button>
        )}
        {correcting && (
          <>
            <Button
              variant="contained"
              disabled={busy}
              onClick={() => {
                if (reason.trim().length < 5) {
                  setProblem("Write the reason for the correction.");
                  return;
                }
                void run(true, () =>
                  correctObservation(authFetch, view.id, reason.trim(), body()),
                );
              }}
            >
              Save correction
            </Button>
            <Button onClick={() => setCorrecting(false)}>Cancel</Button>
          </>
        )}
        {management && view.versionNo > 1 && versions === null && (
          <Button onClick={() => void showVersions()}>
            Show earlier versions
          </Button>
        )}
        <input
          ref={fileInput}
          type="file"
          hidden
          aria-label="Observation file"
          onChange={(e) => {
            const file = e.target.files?.[0];
            e.target.value = "";
            if (file) void attach(file);
          }}
        />
      </Stack>

      {versions && (
        <>
          <Typography variant="h6" component="h2">
            Versions
          </Typography>
          <List dense aria-label="Observation versions">
            {versions.map((v) => (
              <ListItem key={v.id}>
                <ListItemText
                  primary={`Version ${v.versionNo}${v.current ? " (current)" : ""}: ${
                    v.overallResult
                      ? RESULT_LABELS[v.overallResult]
                      : "no result"
                  }, average ${v.averageRating ?? "none"}`}
                  secondary={v.correctionReason ?? undefined}
                />
              </ListItem>
            ))}
          </List>
        </>
      )}

      {notHeld && options && (
        <NotHeldDialog
          options={options}
          onClose={() => setNotHeld(false)}
          onSubmit={async (reasonCode, text) => {
            const result = await markNotHeld(authFetch, view.id, {
              reason: reasonCode,
              text: text || null,
              version: view.version,
            });
            if (result.ok) {
              setNotHeld(false);
              onChanged(result.data);
              return null;
            }
            return result.reason;
          }}
        />
      )}
      {removing && (
        <ReasonDialog
          title="Remove file"
          label="Why is the file removed?"
          min={3}
          button="Remove"
          destructive
          onClose={() => setRemoving(null)}
          onSubmit={async (text) => {
            const result = await removeAttachment(
              authFetch,
              view.id,
              removing,
              text,
            );
            if (!result.ok) return result.reason;
            setRemoving(null);
            const fresh = await getObservation(authFetch, view.id);
            if (fresh.ok) onChanged(fresh.data);
            return null;
          }}
        />
      )}
    </Box>
  );
}

function NotHeldDialog({
  options,
  onClose,
  onSubmit,
}: {
  options: Options;
  onClose: () => void;
  onSubmit: (reason: string, text: string) => Promise<string | null>;
}) {
  const [reason, setReason] = useState("");
  const [text, setText] = useState("");
  const [problem, setProblem] = useState<string | null>(null);

  async function submit() {
    if (!reason) return setProblem("Choose why the visit did not take place.");
    if (reason === "OTHER" && !text.trim())
      return setProblem("Say why the visit did not take place.");
    setProblem(null);
    const failure = await onSubmit(reason, text.trim());
    if (failure) setProblem(failure);
  }

  return (
    <Dialog
      open
      onClose={onClose}
      fullWidth
      maxWidth="sm"
      aria-labelledby="not-held-title"
    >
      <DialogTitle id="not-held-title">Visit not held</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <Typography variant="body2" color="text.secondary">
            No ratings are needed. The observation stays due and this visit is
            kept in the history.
          </Typography>
          {problem && <Alert severity="error">{problem}</Alert>}
          <TextField
            select
            label="Why"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
          >
            {options.notHeldReasons.map((r) => (
              <MenuItem key={r.value} value={r.value}>
                {r.label}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            label="Explanation"
            value={text}
            onChange={(e) => setText(e.target.value)}
            multiline
            minRows={2}
            slotProps={{ htmlInput: { maxLength: MAX_TEXT } }}
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
        <Button variant="contained" onClick={() => void submit()}>
          Record visit not held
        </Button>
      </DialogActions>
    </Dialog>
  );
}
