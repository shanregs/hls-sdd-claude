import { useCallback, useEffect, useRef, useState } from "react";
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
  Divider,
  Link,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { useAccessModel } from "../../access-model/useAccessModel";
import { formatDate } from "../teachers/formatters";
import {
  CONTENT_ROUTE,
  MY_CONTENT_ROUTE,
  approveContent,
  checkFile,
  downloadVersion,
  fileSize,
  getContent,
  getLimits,
  megabytes,
  removeFile,
  returnContent,
  submitVersion,
  type ContentDetail,
  type TypeLimit,
} from "./contentApi";
import { StatusChip } from "../../theme/StatusChip";

type State =
  | { kind: "loading" }
  | { kind: "error"; reason: string }
  | { kind: "ready"; detail: ContentDetail };

function when(iso: string): string {
  return new Date(iso).toLocaleString("en-GB");
}

/**
 * One content piece (spec 029): its facts, every version with its file, the reviews and comments, the history, and exactly the
 * actions the server says the caller may take (submit a version, approve, return with a comment, remove a file).
 */
export function ContentDetailPage() {
  const { id = "" } = useParams();
  const { authFetch } = useAuth();
  const { accessModel } = useAccessModel();
  const management = Boolean(accessModel?.roles.some((r) => r === "ADMIN" || r === "DIRECTOR"));
  const [state, setState] = useState<State>({ kind: "loading" });
  const [limits, setLimits] = useState<TypeLimit[]>([]);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [note, setNote] = useState("");
  const [returning, setReturning] = useState(false);
  const [removing, setRemoving] = useState<number | null>(null);
  const fileInput = useRef<HTMLInputElement>(null);

  const load = useCallback(async () => {
    const result = await getContent(authFetch, id);
    setState(result.ok ? { kind: "ready", detail: result.data } : { kind: "error", reason: result.reason });
  }, [authFetch, id]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- initial and filter-driven fetch
    void load();
  }, [load]);

  useEffect(() => {
    void getLimits(authFetch).then((result) => {
      if (result.ok) setLimits(result.data.types);
    });
  }, [authFetch]);

  async function run(promise: Promise<{ ok: true; data: ContentDetail } | { ok: false; reason: string }>) {
    setBusy(true);
    setError("");
    const result = await promise;
    setBusy(false);
    if (result.ok) {
      setState({ kind: "ready", detail: result.data });
      setNote("");
    } else {
      setError(result.reason);
    }
  }

  function onPick(file: File | undefined) {
    if (!file) return;
    const refusal = limits.length > 0 ? checkFile(file, limits) : null;
    if (refusal) {
      setError(refusal);
      return;
    }
    void run(submitVersion(authFetch, id, file, note));
    if (fileInput.current) fileInput.current.value = "";
  }

  if (state.kind === "loading") return <CircularProgress aria-label="Loading content" />;
  if (state.kind === "error") {
    return (
      <Alert severity="error" role="alert">
        {state.reason}
      </Alert>
    );
  }
  const detail = state.detail;
  const actions = detail.actions;

  return (
    <Box>
      <Link component={RouterLink} to={management ? CONTENT_ROUTE : MY_CONTENT_ROUTE}>
        Back to the list
      </Link>
      <Stack direction="row" spacing={1} sx={{ mt: 1, mb: 2, alignItems: "center", flexWrap: "wrap" }}>
        <Typography variant="h5" component="h1">
          {detail.title}
        </Typography>
        <Chip label={detail.statusLabel} />
        {detail.overdue && <StatusChip kind="pending" label="Overdue" />}
      </Stack>
      {error && (
        <Alert severity="error" role="alert" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}

      <Stack spacing={0.5} sx={{ mb: 2 }}>
        <Typography>ID: {detail.code}</Typography>
        <Typography>Category: {detail.category}</Typography>
        <Typography>Subject: {detail.subject}</Typography>
        <Typography>Author: {detail.authorName}</Typography>
        {detail.createdByUserId !== detail.authorUserId && <Typography>Created by: {detail.createdByName}</Typography>}
        {detail.dueOn && <Typography>Due: {formatDate(detail.dueOn)}</Typography>}
        {detail.description && <Typography>{detail.description}</Typography>}
        {detail.approvedOn && (
          <Typography>
            Approved on {formatDate(detail.approvedOn)} by {detail.reviewerName}
          </Typography>
        )}
        {detail.taskId && (
          <Link component={RouterLink} to={`/operations/tasks/${detail.taskId}`}>
            Linked task
          </Link>
        )}
      </Stack>

      {(actions.approve || actions.returnForRevision) && (
        <Stack direction="row" spacing={1} sx={{ mb: 2 }}>
          {actions.approve && (
            <Button variant="contained" disabled={busy} onClick={() => void run(approveContent(authFetch, id))}>
              Approve version {detail.currentVersion}
            </Button>
          )}
          {actions.returnForRevision && (
            <Button variant="outlined" disabled={busy} onClick={() => setReturning(true)}>
              Return for revision
            </Button>
          )}
        </Stack>
      )}

      {actions.submit && (
        <Box sx={{ mb: 3, p: 2, border: 1, borderColor: "divider", borderRadius: 1 }}>
          <Typography variant="h6" component="h2" gutterBottom>
            Submit a version
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
            Attach the actual content file. Accepted:{" "}
            {limits.map((t) => `${t.extension} (up to ${megabytes(t.maxBytes)})`).join(", ")}.
          </Typography>
          <TextField label="Note (optional)" value={note} onChange={(e) => setNote(e.target.value)} size="small" fullWidth sx={{ mb: 1 }} slotProps={{ htmlInput: { maxLength: 500 } }} />
          <input
            ref={fileInput}
            type="file"
            hidden
            aria-label="Content file"
            onChange={(e) => onPick(e.target.files?.[0])}
          />
          <Button variant="contained" disabled={busy} onClick={() => fileInput.current?.click()}>
            {busy ? "Uploading..." : "Choose file and submit"}
          </Button>
        </Box>
      )}

      <Typography variant="h6" component="h2" gutterBottom>
        Versions
      </Typography>
      {detail.versions.length === 0 ? (
        <Typography color="text.secondary">Nothing has been submitted yet.</Typography>
      ) : (
        <Stack spacing={1} sx={{ mb: 3 }}>
          {[...detail.versions].reverse().map((v) => (
            <Box key={v.number} sx={{ p: 1.5, border: 1, borderColor: "divider", borderRadius: 1 }}>
              <Stack direction="row" spacing={1} sx={{ alignItems: "center", flexWrap: "wrap" }}>
                <Typography sx={{ fontWeight: 600 }}>Version {v.number}</Typography>
                {v.number === detail.currentVersion && <Chip size="small" label="Current" />}
                {v.lastApproved && <StatusChip kind="approved" size="small" label="Approved" />}
                {v.removed && <StatusChip kind="rejected" size="small" label="File removed" />}
              </Stack>
              <Typography variant="body2">
                {v.fileName} ({fileSize(v.sizeBytes)}) - {when(v.submittedAt)}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Author {v.authorName}
                {v.submittedByUserId !== v.authorUserId ? `, submitted by ${v.submittedByName}` : ""}
                {v.note ? ` - ${v.note}` : ""}
              </Typography>
              {v.removed && v.removalReason && <Typography variant="body2">Removed: {v.removalReason}</Typography>}
              {!v.removed && (
                <Stack direction="row" spacing={1} sx={{ mt: 1 }}>
                  <Button
                    size="small"
                    onClick={async () => {
                      const result = await downloadVersion(authFetch, id, v.number, v.fileName);
                      if (!result.ok) setError(result.reason);
                    }}
                  >
                    Download
                  </Button>
                  {actions.removeFile && (
                    <Button size="small" color="error" onClick={() => setRemoving(v.number)}>
                      Remove file
                    </Button>
                  )}
                </Stack>
              )}
            </Box>
          ))}
        </Stack>
      )}

      <Typography variant="h6" component="h2" gutterBottom>
        Reviews
      </Typography>
      {detail.reviews.length === 0 ? (
        <Typography color="text.secondary" sx={{ mb: 3 }}>
          No decisions yet.
        </Typography>
      ) : (
        <Stack spacing={1} sx={{ mb: 3 }}>
          {detail.reviews.map((r, i) => (
            <Typography key={i} variant="body2">
              Version {r.versionNumber}: {r.decision === "APPROVED" ? "approved" : "returned"} by {r.reviewerName} on {when(r.decidedAt)}
              {r.comment ? ` - ${r.comment}` : ""}
            </Typography>
          ))}
        </Stack>
      )}

      <Divider sx={{ mb: 2 }} />
      <Typography variant="h6" component="h2" gutterBottom>
        History
      </Typography>
      <Stack spacing={0.5}>
        {detail.history.map((h, i) => (
          <Typography key={i} variant="body2">
            {when(h.at)} - {h.actorName}: {h.event.toLowerCase().replace(/_/g, " ")}
            {h.before || h.after ? ` (${h.before ?? ""} to ${h.after ?? ""})` : ""}
            {h.note ? ` - ${h.note}` : ""}
          </Typography>
        ))}
      </Stack>

      {returning && (
        <ReasonDialog
          title="Return for revision"
          label="Comment for the author"
          confirm="Return"
          onClose={() => setReturning(false)}
          onConfirm={async (text) => {
            setReturning(false);
            await run(returnContent(authFetch, id, text));
          }}
        />
      )}
      {removing !== null && (
        <ReasonDialog
          title={`Remove the file of version ${removing}`}
          label="Reason"
          confirm="Remove"
          onClose={() => setRemoving(null)}
          onConfirm={async (text) => {
            const number = removing;
            setRemoving(null);
            await run(removeFile(authFetch, id, number, text));
          }}
        />
      )}
    </Box>
  );
}

function ReasonDialog({
  title,
  label,
  confirm,
  onClose,
  onConfirm,
}: {
  title: string;
  label: string;
  confirm: string;
  onClose: () => void;
  onConfirm: (text: string) => void | Promise<void>;
}) {
  const [text, setText] = useState("");
  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm" aria-labelledby="reason-title">
      <DialogTitle id="reason-title">{title}</DialogTitle>
      <DialogContent>
        <TextField label={label} value={text} onChange={(e) => setText(e.target.value)} multiline minRows={3} fullWidth required sx={{ mt: 1 }} slotProps={{ htmlInput: { maxLength: 1000 } }} />
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" disabled={text.trim().length < 3} onClick={() => void onConfirm(text.trim())}>
          {confirm}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
