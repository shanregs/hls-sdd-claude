import { useRef, useState } from "react";
import {
  Alert,
  Button,
  List,
  ListItem,
  ListItemText,
  Stack,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { ReasonDialog } from "../recruitment/ReasonDialog";
import {
  downloadAttachment,
  removeAttachment,
  uploadAttachment,
  type Activity,
  type AttachmentRef,
} from "./marketingApi";

interface AttachmentListProps {
  activity: Activity;
  canAdd: boolean;
  /** Only Admin and Director may remove a file. */
  canRemove: boolean;
  onChanged: () => void;
}

function sizeText(bytes: number) {
  return bytes < 1024 * 1024
    ? `${Math.max(1, Math.round(bytes / 1024))} KB`
    : `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/** The photos and documents of a visit: add (jpg, png, pdf, docx, xlsx, txt; 10 MB), save and, for Admin or Director, remove. */
export function AttachmentList({
  activity,
  canAdd,
  canRemove,
  onChanged,
}: AttachmentListProps) {
  const { authFetch } = useAuth();
  const input = useRef<HTMLInputElement>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [removing, setRemoving] = useState<AttachmentRef | null>(null);

  const upload = async (file: File | undefined) => {
    if (!file) return;
    setBusy(true);
    setError(null);
    const result = await uploadAttachment(authFetch, activity.id, file);
    setBusy(false);
    if (input.current) input.current.value = "";
    if (!result.ok) setError(result.reason);
    else onChanged();
  };

  const download = async (file: AttachmentRef) => {
    const result = await downloadAttachment(authFetch, file.fileId, file.name);
    if (!result.ok) setError(result.reason);
  };

  return (
    <Stack spacing={1}>
      {error && (
        <Alert severity="error" role="alert">
          {error}
        </Alert>
      )}
      {activity.attachments.length === 0 ? (
        <span>No files attached.</span>
      ) : (
        <List dense aria-label={`Files of the ${activity.date} visit`}>
          {activity.attachments.map((f) => (
            <ListItem
              key={f.fileId}
              disableGutters
              secondaryAction={
                <Stack direction="row" spacing={0.5}>
                  <Button
                    size="small"
                    onClick={() => void download(f)}
                    aria-label={`Download ${f.name}`}
                  >
                    Download
                  </Button>
                  {canRemove && (
                    <Button
                      size="small"
                      color="error"
                      onClick={() => setRemoving(f)}
                      aria-label={`Remove ${f.name}`}
                    >
                      Remove
                    </Button>
                  )}
                </Stack>
              }
            >
              <ListItemText
                primary={f.name}
                secondary={sizeText(f.sizeBytes)}
              />
            </ListItem>
          ))}
        </List>
      )}
      {canAdd && activity.attachments.length < 10 && (
        <div>
          <input
            ref={input}
            type="file"
            accept=".jpg,.jpeg,.png,.pdf,.docx,.xlsx,.txt"
            aria-label={`Attach a file to the ${activity.date} visit`}
            disabled={busy}
            onChange={(e) => void upload(e.target.files?.[0])}
          />
        </div>
      )}
      {removing && (
        <ReasonDialog
          title={`Remove ${removing.name}?`}
          message="The file is hidden and the removal is recorded; the file itself is kept."
          label="Reason"
          confirmLabel="Remove file"
          destructive
          onConfirm={async (reason) => {
            const result = await removeAttachment(
              authFetch,
              activity.id,
              removing.fileId,
              reason,
            );
            if (!result.ok) return result.reason;
            setRemoving(null);
            onChanged();
            return null;
          }}
          onCancel={() => setRemoving(null)}
        />
      )}
    </Stack>
  );
}
