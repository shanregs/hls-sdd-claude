import { useRef, useState } from "react";
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import {
  removeMouDocument,
  uploadMouDocument,
  type Approval,
} from "./mouApprovalApi";
import { ALLOWED_EXTENSIONS, MAX_DOCUMENT_BYTES } from "./mouFormat";

/** Choosing a file uploads it as the next version. The allowed types and size are shown next to the button. */
export function UploadMouDocument({
  contractId,
  replacing,
  warnsReset,
  onUploaded,
}: {
  contractId: string;
  /** A document already exists, so this adds a new version. */
  replacing: boolean;
  /** The MoU is approved, so a new version sends it back for approval. */
  warnsReset: boolean;
  onUploaded: (approval: Approval) => void;
}) {
  const { authFetch } = useAuth();
  const input = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const choose = (file: File | undefined) => {
    if (!file) {
      return;
    }
    const extension = file.name.split(".").pop()?.toLowerCase() ?? "";
    if (!ALLOWED_EXTENSIONS.includes(extension)) {
      setError("Only PDF, JPG or PNG files can be uploaded.");
      return;
    }
    if (file.size === 0) {
      setError("The file is empty.");
      return;
    }
    if (file.size > MAX_DOCUMENT_BYTES) {
      setError("A file can be at most 10 MB.");
      return;
    }
    setError(null);
    setBusy(true);
    void (async () => {
      const result = await uploadMouDocument(authFetch, contractId, file);
      setBusy(false);
      if (input.current) {
        input.current.value = "";
      }
      if (result.ok) {
        onUploaded(result.data);
      } else {
        setError(result.reason);
      }
    })();
  };

  return (
    <Stack spacing={1}>
      <Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
        <Button
          variant="outlined"
          component="label"
          disabled={busy}
          sx={{ minHeight: 44 }}
        >
          {replacing ? "Upload a new version" : "Upload signed document"}
          <input
            ref={input}
            hidden
            type="file"
            accept=".pdf,.jpg,.jpeg,.png"
            aria-label="Signed MoU document"
            onChange={(e) => choose(e.target.files?.[0])}
          />
        </Button>
        {busy && <Typography role="status">Uploading...</Typography>}
      </Stack>
      <Typography variant="caption" color="text.secondary">
        PDF, JPG or PNG, up to 10 MB.
        {warnsReset &&
          " This MoU is approved: a new version sends it back for approval."}
      </Typography>
      {error && (
        <Alert severity="error" role="alert">
          {error}
        </Alert>
      )}
    </Stack>
  );
}

/** Removing a version needs a reason of at most 300 characters; the file and the history stay. */
export function RemoveDocumentDialog({
  contractId,
  documentId,
  onClose,
  onRemoved,
}: {
  contractId: string;
  documentId: string;
  onClose: () => void;
  onRemoved: (approval: Approval) => void;
}) {
  const { authFetch } = useAuth();
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const trimmed = reason.trim();

  const submit = () => {
    if (!trimmed) {
      setError("Give a reason.");
      return;
    }
    setBusy(true);
    void (async () => {
      const result = await removeMouDocument(
        authFetch,
        contractId,
        documentId,
        trimmed,
      );
      setBusy(false);
      if (result.ok) {
        onRemoved(result.data);
      } else {
        setError(result.reason);
      }
    })();
  };

  return (
    <Dialog open onClose={onClose} aria-labelledby="remove-doc-title" fullWidth>
      <DialogTitle id="remove-doc-title">Remove this version?</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>
          The version is hidden from download. The file and this record are
          kept, and the approval status does not change.
        </DialogContentText>
        <TextField
          autoFocus
          fullWidth
          multiline
          minRows={2}
          label="Reason"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          slotProps={{ htmlInput: { maxLength: 300 } }}
          error={error !== null}
          helperText={error ?? `${reason.length}/300`}
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          color="error"
          onClick={submit}
          disabled={busy}
        >
          Remove version
        </Button>
      </DialogActions>
    </Dialog>
  );
}
