import { useState } from "react";
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  TextField,
} from "@mui/material";

/** Asks for a written reason or note before something that must be explained; the server's refusal is shown inline. */
export function ReasonDialog({
  title,
  label,
  min,
  button,
  destructive = false,
  onClose,
  onSubmit,
}: {
  title: string;
  label: string;
  min: number;
  button: string;
  destructive?: boolean;
  onClose: () => void;
  /** Returns the problem to show, or null when it worked. */
  onSubmit: (text: string) => Promise<string | null>;
}) {
  const [text, setText] = useState("");
  const [problem, setProblem] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit() {
    if (text.trim().length < min) {
      setProblem(`Write at least ${min} characters.`);
      return;
    }
    setProblem(null);
    setBusy(true);
    const failure = await onSubmit(text.trim());
    setBusy(false);
    if (failure) setProblem(failure);
  }

  return (
    <Dialog
      open
      onClose={onClose}
      fullWidth
      maxWidth="sm"
      aria-labelledby="reason-title"
    >
      <DialogTitle id="reason-title">{title}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {problem && <Alert severity="error">{problem}</Alert>}
          <TextField
            label={label}
            value={text}
            onChange={(e) => setText(e.target.value)}
            multiline
            minRows={3}
            required
            slotProps={{ htmlInput: { maxLength: 2000 } }}
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
        <Button
          variant="contained"
          color={destructive ? "error" : "primary"}
          disabled={busy}
          onClick={() => void submit()}
        >
          {button}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
